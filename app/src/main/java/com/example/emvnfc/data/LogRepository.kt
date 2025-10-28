package com.example.emvnfc.data

import android.content.Context
import com.example.emvnfc.model.EmvLogEntry
import com.example.emvnfc.model.LogField
import com.example.emvnfc.model.LogSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

interface LogRepository {
    val logs: StateFlow<List<EmvLogEntry>>
    suspend fun append(entry: EmvLogEntry)
    suspend fun clear()
    fun logFile(): File?
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

    override fun logFile(): File? = null
}

class FileLogRepository(context: Context) : LogRepository {
    private val mutex = Mutex()
    private val logsDir: File = File(context.filesDir, "logs")
    private val file: File = File(logsDir, "emv_logs.json")
    private val _logs = MutableStateFlow(loadFromDisk())
    override val logs: StateFlow<List<EmvLogEntry>> = _logs.asStateFlow()

    override suspend fun append(entry: EmvLogEntry) {
        mutex.withLock {
            val updated = _logs.value + entry
            persist(updated)
            _logs.value = updated
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            persist(emptyList())
            _logs.value = emptyList()
        }
    }

    override fun logFile(): File? = if (file.exists()) file else null

    private fun loadFromDisk(): List<EmvLogEntry> = runCatching {
        if (!file.exists()) return emptyList()
        val text = file.readText()
        if (text.isBlank()) return emptyList()
        val array = JSONArray(text)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val fieldsArray = obj.getJSONArray("fields")
                val fields = buildList {
                    for (j in 0 until fieldsArray.length()) {
                        val fieldObj = fieldsArray.getJSONObject(j)
                        add(
                            LogField(
                                tag = fieldObj.getString("tag"),
                                rawHex = fieldObj.getString("rawHex"),
                                interpretation = fieldObj.getString("interpretation")
                            )
                        )
                    }
                }
                add(
                    EmvLogEntry(
                        id = obj.optString("id"),
                        timestampMillis = obj.getLong("timestamp"),
                        source = LogSource.valueOf(obj.getString("source")),
                        fields = fields
                    )
                )
            }
        }
    }.getOrElse { emptyList() }

    private fun persist(entries: List<EmvLogEntry>) {
        runCatching {
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }
            val array = JSONArray()
            entries.forEach { entry ->
                val fieldsArray = JSONArray()
                entry.fields.forEach { field ->
                    fieldsArray.put(
                        JSONObject().apply {
                            put("tag", field.tag)
                            put("rawHex", field.rawHex)
                            put("interpretation", field.interpretation)
                        }
                    )
                }
                array.put(
                    JSONObject().apply {
                        put("id", entry.id)
                        put("timestamp", entry.timestampMillis)
                        put("source", entry.source.name)
                        put("fields", fieldsArray)
                    }
                )
            }
            file.writeText(array.toString())
        }
    }
}
