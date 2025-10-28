package com.example.emvnfc.parser

import java.math.BigDecimal

object TagInterpreter {

    private val interpreters: Map<String, (ByteArray) -> String> = mapOf(
        "9F02" to ::formatAmount,
        "5A" to ::maskPan,
        "57" to ::maskTrack2Equivalent,
        "9F26" to { value -> value.toHexString() },
        "9F10" to { value -> value.toHexString() },
        "84" to { value -> value.toHexString() }
    )

    fun interpret(tlv: Tlv): ParsedField {
        val interpreter = interpreters[tlv.tag.uppercase()] ?: { value: ByteArray -> value.toHexString() }
        return ParsedField(tlv, interpreter(tlv.value))
    }

    fun interpretAll(tlvs: List<Tlv>): List<ParsedField> = tlvs.map(::interpret)

    private fun formatAmount(bytes: ByteArray): String {
        val digits = bytes.toHexString().trimStart('0').ifBlank { "0" }
        val value = BigDecimal(digits).movePointLeft(2)
        return value.setScale(2).toPlainString()
    }

    private fun maskPan(bytes: ByteArray): String {
        val pan = bytes.toHexString().trimEnd('F')
        return maskPanString(pan)
    }

    private fun maskTrack2Equivalent(bytes: ByteArray): String {
        val raw = bytes.toHexString()
        val track = buildString {
            for (char in raw) {
                append(if (char == 'D') '=' else char)
            }
        }.trimEnd('F')
        val separatorIndex = track.indexOf('=')
        return if (separatorIndex > 0) {
            val pan = track.substring(0, separatorIndex)
            val remainder = track.substring(separatorIndex)
            maskPanString(pan) + remainder
        } else {
            maskPanString(track)
        }
    }

    private fun maskPanString(pan: String): String {
        if (pan.length <= 6) return pan
        val firstSix = pan.take(6)
        val lastFour = pan.takeLast(4.coerceAtMost(pan.length - 6))
        val maskLength = (pan.length - firstSix.length - lastFour.length).coerceAtLeast(0)
        return buildString {
            append(firstSix)
            repeat(maskLength) { append('•') }
            append(lastFour)
        }
    }
}
