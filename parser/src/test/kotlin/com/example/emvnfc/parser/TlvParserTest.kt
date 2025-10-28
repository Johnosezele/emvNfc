package com.example.emvnfc.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TlvParserTest {

    @Test
    fun `parse valid tlv sequence`() {
        val input = "9F02060000000012349F2608A1B2C3D4E5F678909F1007061122334455668407A00000000310105A085476541234567890"

        val tlvs = TlvParser.parse(input)

        assertEquals(5, tlvs.size)
        assertEquals("9F02", tlvs[0].tag)
        assertEquals(6, tlvs[0].length)
        assertEquals("000000001234", tlvs[0].hexValue)

        assertEquals("5A", tlvs.last().tag)
        assertEquals(8, tlvs.last().length)
        assertEquals("5476541234567890", tlvs.last().hexValue)
    }

    @Test
    fun `interpretation formats amount and masks pan`() {
        val input = "9F02060000000012345A085476541234567890"

        val parsed = TlvParser.parse(input)
        val interpreted = TagInterpreter.interpretAll(parsed)

        val amount = interpreted.first { it.tlv.tag == "9F02" }
        val pan = interpreted.first { it.tlv.tag == "5A" }

        assertEquals("12.34", amount.interpretation)
        assertEquals("547654\u2022\u2022\u2022\u2022\u2022\u20227890", pan.interpretation)
    }

    @Test
    fun `throws when declared length exceeds bytes`() {
        val malformed = "9F02030001"

        assertFailsWith<MalformedTlvException> {
            TlvParser.parse(malformed)
        }
    }

    @Test
    fun `throws when hex input is invalid`() {
        assertFailsWith<InvalidHexInputException> {
            TlvParser.parse("ZZ")
        }
    }

    @Test
    fun `unknown tags fall back to hex interpretation`() {
        val input = "DF0102A1B2"

        val parsed = TlvParser.parse(input)
        val interpreted = TagInterpreter.interpretAll(parsed)

        assertEquals(1, interpreted.size)
        assertEquals("DF01", interpreted[0].tlv.tag)
        assertEquals("A1B2", interpreted[0].interpretation)
    }

    @Test
    fun `supports multi byte tag parsing`() {
        val input = "9F1A020123"

        val tlvs = TlvParser.parse(input)

        assertEquals(1, tlvs.size)
        assertEquals("9F1A", tlvs[0].tag)
        assertEquals(2, tlvs[0].length)
        assertTrue(tlvs[0].value.contentEquals(byteArrayOf(0x01, 0x23)))
    }
}
