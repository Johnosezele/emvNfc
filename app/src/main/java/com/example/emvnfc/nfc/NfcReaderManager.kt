package com.example.emvnfc.nfc

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import com.example.emvnfc.parser.Tlv
import com.example.emvnfc.parser.TlvParser
import com.example.emvnfc.util.toHexString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class NfcReaderManager(
    private val scope: CoroutineScope,
    private val adapterProvider: () -> NfcAdapter?
) : NfcAdapter.ReaderCallback {

    private val _events = MutableSharedFlow<NfcEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<NfcEvent> = _events

    fun enable(activity: android.app.Activity) {
        adapterProvider()?.enableReaderMode(
            activity,
            this,
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            Bundle().apply {
                putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1000)
            }
        )
    }

    fun disable(activity: android.app.Activity) {
        adapterProvider()?.disableReaderMode(activity)
    }

    override fun onTagDiscovered(tag: Tag) {
        scope.launch {
            emit(NfcEvent.Reading)
            val isoDep = IsoDep.get(tag)
            if (isoDep == null) {
                emit(NfcEvent.Error("Unsupported tag technology"))
                return@launch
            }
            runCatching {
                isoDep.connect()
                val tlvData = readApplicationData(isoDep)
                emit(NfcEvent.Payload(tlvData))
            }.onFailure { throwable ->
                emit(NfcEvent.Error("Tag lost or read error: ${throwable.message}"))
            }.also {
                runCatching { isoDep.close() }
            }
        }
    }

    private suspend fun emit(event: NfcEvent) {
        _events.emit(event)
    }

    private fun readApplicationData(isoDep: IsoDep): ByteArray {
        val aids = selectPpseForAids(isoDep)
        if (aids.isEmpty()) {
            throw IllegalStateException("No EMV applications found on card")
        }
        val combined = mutableListOf<Byte>()
        for (aid in aids) {
            val records = readRecordsForAid(isoDep, aid)
            if (records.isNotEmpty()) {
                combined += records
                break
            }
        }
        if (combined.isEmpty()) {
            throw IllegalStateException("No application records readable")
        }
        return combined.toByteArray()
    }

    private fun selectPpseForAids(isoDep: IsoDep): List<ByteArray> {
        val command = byteArrayOf(
            0x00,
            0xA4.toByte(),
            0x04,
            0x00,
            0x0E,
            *"2PAY.SYS.DDF01".toByteArray(),
            0x00
        )
        val response = isoDep.transceiveSuccess(command)
        val tlvs = flattenTlvs(TlvParser.parse(response.toHexString()))
        return tlvs.filter { it.tag.equals("4F", ignoreCase = true) }
            .map { it.value }
    }

    private fun readRecordsForAid(isoDep: IsoDep, aid: ByteArray): List<Byte> {
        selectApplication(isoDep, aid)
        val pdol = extractDol(selectApplicationFci)
        val gpoResponse = getProcessingOptions(isoDep, pdol)
        val aflEntries = extractAflEntries(gpoResponse)
        return aflEntries.flatMap { entry ->
            readRecords(isoDep, entry)
        }
    }

    private fun selectApplication(isoDep: IsoDep, aid: ByteArray) {
        val lc = aid.size.toByte()
        val command = byteArrayOf(
            0x00,
            0xA4.toByte(),
            0x04,
            0x00,
            lc,
            *aid,
            0x00
        )
        selectApplicationFci = isoDep.transceiveSuccess(command)
    }

    private fun extractDol(fci: ByteArray): ByteArray? {
        val tlvs = flattenTlvs(TlvParser.parse(fci.toHexString()))
        return tlvs.firstOrNull { it.tag.equals("9F38", ignoreCase = true) }?.value
    }

    private fun getProcessingOptions(isoDep: IsoDep, pdol: ByteArray?): ByteArray {
        val dolData = buildDolData(pdol)
        val lc = (dolData.size + 2).toByte()
        val body = byteArrayOf(0x83.toByte(), dolData.size.toByte(), *dolData)
        val command = byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, lc, *body, 0x00)
        return isoDep.transceiveSuccess(command)
    }

    private fun extractAflEntries(gpoResponse: ByteArray): List<AflEntry> {
        val tlvs = flattenTlvs(TlvParser.parse(gpoResponse.toHexString()))
        val afl = tlvs.firstOrNull { it.tag.equals("94", ignoreCase = true) }?.value
            ?: throw IllegalStateException("No AFL present in GPO response")
        if (afl.size % 4 != 0) {
            throw IllegalStateException("AFL length is invalid")
        }
        return afl.asIterable().chunked(4).map { chunk ->
            val sfi = (chunk[0].toInt() and 0xFF) shr 3
            val firstRecord = chunk[1].toInt() and 0xFF
            val lastRecord = chunk[2].toInt() and 0xFF
            AflEntry(sfi, firstRecord, lastRecord)
        }
    }

    private fun readRecords(isoDep: IsoDep, entry: AflEntry): List<Byte> {
        val results = mutableListOf<Byte>()
        for (record in entry.firstRecord..entry.lastRecord) {
            val p2 = ((entry.sfi shl 3) or 0x04).toByte()
            val command = byteArrayOf(0x00, 0xB2.toByte(), record.toByte(), p2, 0x00)
            try {
                val response = isoDep.transceiveSuccess(command)
                results += response.toList()
            } catch (_: Exception) {
                continue
            }
        }
        return results
    }

    private fun buildDolData(pdol: ByteArray?): ByteArray {
        if (pdol == null || pdol.isEmpty()) return byteArrayOf()
        val buffer = mutableListOf<Byte>()
        var index = 0
        while (index < pdol.size) {
            val tagByte = pdol[index].toInt() and 0xFF
            index++
            val tag = if ((tagByte and 0x1F) == 0x1F) {
                val second = pdol[index].toInt() and 0xFF
                index++
                byteArrayOf(tagByte.toByte(), second.toByte())
            } else {
                byteArrayOf(tagByte.toByte())
            }
            val length = pdol[index].toInt() and 0xFF
            index++
            repeat(length) { buffer.add(0x00) }
        }
        return buffer.toByteArray()
    }

    private fun IsoDep.transceiveSuccess(command: ByteArray): ByteArray {
        val response = transceive(command)
        if (response.size < 2) {
            throw IllegalStateException("APDU response too short")
        }
        val sw1 = response[response.size - 2]
        val sw2 = response[response.size - 1]
        if (sw1 != 0x90.toByte() || sw2 != 0x00.toByte()) {
            throw IllegalStateException("APDU failure: %02X%02X".format(sw1.toInt() and 0xFF, sw2.toInt() and 0xFF))
        }
        return response.copyOf(response.size - 2)
    }

    private fun flattenTlvs(tlvs: List<Tlv>): List<Tlv> {
        val result = mutableListOf<Tlv>()
        for (tlv in tlvs) {
            result += tlv
            if (isConstructed(tlv)) {
                val nested = TlvParser.parse(tlv.value.toHexString())
                result += flattenTlvs(nested)
            }
        }
        return result
    }

    private fun isConstructed(tlv: Tlv): Boolean {
        if (tlv.tag.length < 2) return false
        val firstByte = tlv.tag.substring(0, 2).toInt(16)
        return (firstByte and 0x20) != 0
    }

    private data class AflEntry(
        val sfi: Int,
        val firstRecord: Int,
        val lastRecord: Int
    )

    private var selectApplicationFci: ByteArray = byteArrayOf()
}

sealed interface NfcEvent {
    data object Idle : NfcEvent
    data object Reading : NfcEvent
    data class Payload(val tlvBytes: ByteArray) : NfcEvent
    data class Error(val message: String) : NfcEvent
}
