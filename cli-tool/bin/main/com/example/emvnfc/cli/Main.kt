package com.example.emvnfc.cli

import com.example.emvnfc.parser.TagInterpreter
import com.example.emvnfc.parser.Tlv
import com.example.emvnfc.parser.TlvParser

fun main(args: Array<String>) {
    val input = when {
        args.isNotEmpty() -> args.joinToString(separator = "")
        else -> {
            println("Enter EMV TLV hex string:")
            generateSequence { readLine() }
                .takeWhile { true }
                .joinToString(separator = "") { it }
        }
    }.trim()

    if (input.isEmpty()) {
        println("No input provided.")
        return
    }

    try {
        val tlvs = TlvParser.parse(input)
        val fields = TagInterpreter.interpretAll(tlvs)
        printTable(fields)
    } catch (ex: Exception) {
        System.err.println("Error: ${ex.message}")
    }
}

private fun printTable(fields: List<com.example.emvnfc.parser.ParsedField>) {
    if (fields.isEmpty()) {
        println("No TLVs parsed.")
        return
    }
    val header = listOf("Tag", "Length", "Value", "Interpretation")
    val rows = fields.map { field ->
        val tlv = field.tlv
        listOf(tlv.tag, tlv.length.toString(), tlv.hexValue, field.interpretation)
    }
    val columnWidths = IntArray(header.size)
    header.forEachIndexed { index, value -> columnWidths[index] = maxOf(columnWidths[index], value.length) }
    rows.forEach { row ->
        row.forEachIndexed { index, value ->
            columnWidths[index] = maxOf(columnWidths[index], value.length)
        }
    }

    fun formatRow(values: List<String>): String = values.mapIndexed { index, value ->
        value.padEnd(columnWidths[index])
    }.joinToString(separator = " | ")

    println(formatRow(header))
    println(columnWidths.joinToString(separator = "-+-") { "".padEnd(it, '-') })
    rows.forEach { row -> println(formatRow(row)) }
}
