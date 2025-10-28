package com.example.emvnfc.model

import java.util.UUID

/** Records a masked EMV transaction snapshot for display and sharing. */
data class EmvLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestampMillis: Long,
    val source: LogSource,
    val fields: List<LogField>
)

/** Identifies where a log originated. */
enum class LogSource { LIVE, SAMPLE }

/** Contains the masked value for an EMV tag. */
data class LogField(
    val tag: String,
    val rawHex: String,
    val interpretation: String
)
