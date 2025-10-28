package com.example.emvnfc.data

/** Provides recorded EMV TLV blobs for use when NFC hardware is unavailable. */
object SampleTlvProvider {
    /** Single transaction sample containing minimal EMV tags required by the assessment. */
    val fallbackTransaction: String =
        "6F1A8407A0000000031010A511500B5649534120435245444954"

    /** Sample TLV containing amount, ARQC, and PAN for parser verification. */
    val purchaseRecord: String =
        "9F02060000000012349F2608A1B2C3D4E5F6789F1007061122334455668407A00000000310105A0854765412345679"
}
