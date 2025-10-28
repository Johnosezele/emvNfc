package com.example.emvnfc.parser

/**
 * Parses BER-TLV encoded data into [Tlv] entries.
 */
object TlvParser {

    fun parse(hexInput: String): List<Tlv> {
        if (hexInput.isBlank()) return emptyList()
        val bytes = hexInput.toByteArrayOrThrow()
        return parse(bytes)
    }

    fun parse(bytes: ByteArray): List<Tlv> {
        val result = mutableListOf<Tlv>()
        var index = 0
        while (index < bytes.size) {
            val (tag, consumedTag) = readTag(bytes, index)
            val (length, consumedLength) = readLength(bytes, index + consumedTag)
            val valueStart = index + consumedTag + consumedLength
            val valueEnd = valueStart + length
            if (valueEnd > bytes.size) {
                throw MalformedTlvException("Declared length exceeds available bytes for tag $tag")
            }
            val value = bytes.copyOfRange(valueStart, valueEnd)
            result += Tlv(tag = tag, length = length, value = value)
            index = valueEnd
        }
        return result
    }

    private fun readTag(bytes: ByteArray, startIndex: Int): Pair<String, Int> {
        if (startIndex >= bytes.size) throw MalformedTlvException("Unexpected end of data when reading tag")
        val firstByte = bytes[startIndex]
        val isMultiByte = firstByte.toUnsignedInt() and 0x1F == 0x1F
        if (!isMultiByte) {
            return Pair(firstByte.toUnsignedInt().toString(16).padStart(2, '0').uppercase(), 1)
        }
        var index = startIndex + 1
        val tagBytes = mutableListOf(firstByte)
        while (index < bytes.size) {
            val current = bytes[index]
            tagBytes += current
            index++
            if (current.toUnsignedInt() and 0x80 == 0) break
        }
        if (tagBytes.last().toUnsignedInt() and 0x80 != 0) {
            throw MalformedTlvException("Multi-byte tag not terminated correctly")
        }
        return Pair(tagBytes.toByteArray().toHexString(), tagBytes.size)
    }

    private fun readLength(bytes: ByteArray, startIndex: Int): Pair<Int, Int> {
        if (startIndex >= bytes.size) throw MalformedTlvException("Unexpected end of data when reading length")
        val firstByte = bytes[startIndex].toUnsignedInt()
        if (firstByte and 0x80 == 0) {
            return Pair(firstByte, 1)
        }
        val numLengthBytes = firstByte and 0x7F
        if (numLengthBytes == 0) {
            throw MalformedTlvException("Indefinite length encoding not supported")
        }
        if (startIndex + numLengthBytes >= bytes.size) {
            throw MalformedTlvException("Insufficient bytes for long-form length")
        }
        var length = 0
        for (i in 1..numLengthBytes) {
            length = (length shl 8) or bytes[startIndex + i].toUnsignedInt()
        }
        return Pair(length, 1 + numLengthBytes)
    }

    private fun String.toByteArrayOrThrow(): ByteArray {
        val sanitized = replace("\\s".toRegex(), "")
        if (sanitized.length % 2 != 0) {
            throw InvalidHexInputException("Hex input must contain an even number of characters")
        }
        return sanitized.chunked(2).map { pair ->
            pair.toIntOrNull(16)?.toByte() ?: throw InvalidHexInputException("Invalid hex byte: $pair")
        }.toByteArray()
    }
}
