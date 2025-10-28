package com.example.emvnfc.nfc

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
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
                val tlvData = readProcessingOptions(isoDep)
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

    private fun readProcessingOptions(isoDep: IsoDep): ByteArray {
        val selectPpse = byteArrayOf(
            0x00.toByte(),
            0xA4.toByte(),
            0x04.toByte(),
            0x00.toByte(),
            0x0E.toByte(),
            *"2PAY.SYS.DDF01".toByteArray(),
            0x00.toByte()
        )
        val ppseResponse = isoDep.transceive(selectPpse)
        return ppseResponse
    }
}

sealed interface NfcEvent {
    data object Idle : NfcEvent
    data object Reading : NfcEvent
    data class Payload(val tlvBytes: ByteArray) : NfcEvent
    data class Error(val message: String) : NfcEvent
}
