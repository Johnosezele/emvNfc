package com.example.emvnfc

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.emvnfc.parser.TagInterpreter
import com.example.emvnfc.parser.TlvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NfcMaskingInstrumentedTest {

    @Test
    fun pan_isMaskedInInterpreter() {
        val tlvHex = "5A0854765412345679"
        val tlvs = TlvParser.parse(tlvHex)
        val parsed = TagInterpreter.interpretAll(tlvs)
        val panField = parsed.first { it.tlv.tag.equals("5A", ignoreCase = true) }

        assertTrue("Expected masked PAN to contain bullet characters", panField.interpretation.contains('•'))
        assertEquals("547654••••5679", panField.interpretation)
    }
}
