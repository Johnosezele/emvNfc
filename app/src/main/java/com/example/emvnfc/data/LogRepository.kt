package com.example.emvnfc.data

import com.example.emvnfc.model.EmvLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface LogRepository {
    val logs: StateFlow<List<EmvLogEntry>>
    suspend fun append(entry: EmvLogEntry)
    suspend fun clear()
}

class InMemoryLogRepository : LogRepository {
    private val mutex = Mutex()
    private val _logs = MutableStateFlow<List<EmvLogEntry>>(emptyList())
    override val logs: StateFlow<List<EmvLogEntry>> = _logs.asStateFlow()

    override suspend fun append(entry: EmvLogEntry) {
        mutex.withLock {
            _logs.value = _logs.value + entry
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            _logs.value = emptyList()
        }
    }
}
