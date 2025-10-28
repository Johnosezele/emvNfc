package com.example.emvnfc.util

fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
    (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
}.uppercase()

fun String.hexToByteArray(): ByteArray {
    require(length % 2 == 0) { "Hex string must have an even length." }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
