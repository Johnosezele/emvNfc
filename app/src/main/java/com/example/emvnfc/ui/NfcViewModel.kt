package com.example.emvnfc.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.emvnfc.data.LogRepository
import com.example.emvnfc.data.InMemoryLogRepository
import com.example.emvnfc.model.EmvLogEntry
import com.example.emvnfc.model.LogField
import com.example.emvnfc.model.LogSource
import com.example.emvnfc.nfc.NfcEvent
import com.example.emvnfc.parser.TagInterpreter
import com.example.emvnfc.parser.TlvParser
import com.example.emvnfc.util.toHexString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val allowedTags = setOf("84", "50", "9F02", "5F2A")

sealed interface ReaderStatus {
    data object Idle : ReaderStatus
    data object WaitingForCard : ReaderStatus
    data object Reading : ReaderStatus
    data object Completed : ReaderStatus
    data object Error : ReaderStatus
}

data class ReaderUiState(
    val status: ReaderStatus = ReaderStatus.Idle,
    val logs: List<EmvLogEntry> = emptyList(),
    val message: String? = null,
    val nfcAvailable: Boolean = true
)

class NfcViewModel(
    private val repository: LogRepository = InMemoryLogRepository(),
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.logs.collect { entries ->
                _uiState.update { it.copy(logs = entries) }
            }
        }
    }

    fun onNfcAvailabilityChanged(available: Boolean) {
        _uiState.update {
            it.copy(
                nfcAvailable = available,
                status = if (available) ReaderStatus.WaitingForCard else ReaderStatus.Idle,
                message = null
            )
        }
    }

    fun onReaderModeEnabled() {
        _uiState.update {
            it.copy(status = ReaderStatus.WaitingForCard, message = null)
        }
    }

    fun onReaderModeDisabled() {
        _uiState.update {
            it.copy(status = ReaderStatus.Idle)
        }
    }

    fun onNfcEvent(event: NfcEvent) {
        when (event) {
            NfcEvent.Idle -> {
                _uiState.update { it.copy(status = ReaderStatus.WaitingForCard, message = null) }
            }
            NfcEvent.Reading -> {
                _uiState.update { it.copy(status = ReaderStatus.Reading, message = null) }
            }
            is NfcEvent.Payload -> {
                viewModelScope.launch {
                    persistLogFromHex(event.tlvBytes.toHexString(), LogSource.LIVE)
                }
            }
            is NfcEvent.Error -> {
                _uiState.update {
                    it.copy(status = ReaderStatus.Error, message = event.message)
                }
            }
        }
    }

    fun ingestSample(hex: String) {
        viewModelScope.launch {
            persistLogFromHex(hex, LogSource.SAMPLE)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clear()
            _uiState.update {
                it.copy(status = ReaderStatus.WaitingForCard, message = null)
            }
        }
    }

    fun buildShareText(): String? {
        val entries = _uiState.value.logs
        if (entries.isEmpty()) return null
        val builder = StringBuilder()
        entries.forEach { entry ->
            builder.appendLine("Timestamp: ${entry.timestampMillis}")
            builder.appendLine("Source: ${entry.source}")
            entry.fields.forEach { field ->
                builder.appendLine("${field.tag}: ${field.interpretation} (hex=${field.rawHex})")
            }
            builder.appendLine()
        }
        return builder.toString().trimEnd()
    }

    private suspend fun persistLogFromHex(hex: String, source: LogSource) {
        runCatching {
            val entry = buildLogEntry(hex, source)
            repository.append(entry)
            entry
        }.onSuccess {
            _uiState.update { state ->
                state.copy(status = ReaderStatus.Completed, message = null)
            }
        }.onFailure { throwable ->
            _uiState.update { state ->
                state.copy(status = ReaderStatus.Error, message = throwable.message ?: "Failed to parse TLV data")
            }
        }
    }

    private fun buildLogEntry(hex: String, source: LogSource): EmvLogEntry {
        val tlvs = TlvParser.parse(hex)
        val interpreted = TagInterpreter.interpretAll(tlvs)
        val fields = interpreted
            .filter { allowedTags.contains(it.tlv.tag.uppercase()) }
            .map { field ->
                LogField(
                    tag = field.tlv.tag.uppercase(),
                    rawHex = field.tlv.hexValue,
                    interpretation = field.interpretation
                )
            }
        if (fields.isEmpty()) {
            throw IllegalStateException("No supported tags found in TLV response")
        }
        return EmvLogEntry(
            timestampMillis = timeProvider(),
            source = source,
            fields = fields
        )
    }
}
