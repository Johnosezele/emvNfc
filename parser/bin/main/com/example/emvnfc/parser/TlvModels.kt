package com.example.emvnfc.parser

/** Represents a single BER-TLV structure. */
data class Tlv(
    val tag: String,
    val length: Int,
    val value: ByteArray
) {
    val hexValue: String = value.toHexString()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Tlv) return false

        return tag == other.tag &&
            length == other.length &&
            value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = tag.hashCode()
        result = 31 * result + length
        result = 31 * result + value.contentHashCode()
        return result
    }
}

/** High-level view of a TLV value with human readable interpretation. */
data class ParsedField(
    val tlv: Tlv,
    val interpretation: String
)

sealed class TlvParseException(message: String) : IllegalArgumentException(message)

class InvalidHexInputException(message: String) : TlvParseException(message)

class MalformedTlvException(message: String) : TlvParseException(message)

internal fun Byte.toUnsignedInt(): Int = toInt() and 0xFF

internal fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
    byte.toUnsignedInt().toString(16).padStart(2, '0')
}.uppercase()
