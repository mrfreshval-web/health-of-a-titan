package com.healthofatitan.app.integration.whoop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One sync attempt in the diagnostic log («Я → WHOOP → Журнал синхронизации»). Technical
 * facts only (class S1): when, who started it, how it ended, HTTP status, the step it stopped
 * at, how long it took and how many records came in. **No health values, no tokens, no IDs.**
 *
 * @property result `OK` or a [SyncFailure] name
 * @property detail short technical hint: exception class or WHOOP path, never a response body
 */
@Serializable
data class SyncLogEntry(
    val at: Long,
    val trigger: String,
    val result: String,
    val httpCode: Int? = null,
    val step: String? = null,
    val durationMs: Long = 0,
    val days: Int? = null,
    val nights: Int? = null,
    val workouts: Int? = null,
    val detail: String? = null,
) {
    val ok: Boolean get() = result == OK

    companion object {
        const val OK = "OK"
    }
}

/** The last [SyncLog.MAX] sync attempts, newest first. */
interface SyncLogStore {
    val entries: StateFlow<List<SyncLogEntry>>

    suspend fun append(entry: SyncLogEntry)

    suspend fun clear()
}

object SyncLog {
    const val MAX = 20

    /** Newest first, at most [max] entries. */
    fun appended(list: List<SyncLogEntry>, entry: SyncLogEntry, max: Int = MAX): List<SyncLogEntry> =
        (listOf(entry) + list).take(max)

    /** Plain text for «Скопировать»: to paste into a chat with the developers. */
    fun asText(entries: List<SyncLogEntry>, format: (Long) -> String): String = entries.joinToString("\n") { e ->
        buildString {
            append(format(e.at)).append(' ').append(e.trigger).append(' ').append(e.result)
            e.httpCode?.let { append(" http=").append(it) }
            e.step?.let { append(" step=").append(it) }
            append(" ").append(e.durationMs).append("ms")
            if (e.days != null) append(" days=").append(e.days)
            if (e.nights != null) append(" nights=").append(e.nights)
            if (e.workouts != null) append(" workouts=").append(e.workouts)
            e.detail?.let { append(" (").append(it).append(')') }
        }
    }
}

class InMemorySyncLogStore(initial: List<SyncLogEntry> = emptyList()) : SyncLogStore {
    private val state = MutableStateFlow(initial)
    override val entries: StateFlow<List<SyncLogEntry>> = state.asStateFlow()

    override suspend fun append(entry: SyncLogEntry) {
        state.value = SyncLog.appended(state.value, entry)
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}

/**
 * The log in a small JSON file in the app's no-backup directory: survives restarts, never leaves
 * the phone, never goes into a backup. A broken file is treated as empty.
 */
class FileSyncLogStore(private val file: File, private val json: Json) : SyncLogStore {
    private val serializer = ListSerializer(SyncLogEntry.serializer())
    private val mutex = Mutex()
    private val state = MutableStateFlow(load())
    override val entries: StateFlow<List<SyncLogEntry>> = state.asStateFlow()

    private fun load(): List<SyncLogEntry> =
        runCatching { if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyList() }
            .getOrDefault(emptyList())

    override suspend fun append(entry: SyncLogEntry) = mutex.withLock {
        val next = SyncLog.appended(state.value, entry)
        state.value = next
        write(next)
    }

    override suspend fun clear() = mutex.withLock {
        state.value = emptyList()
        write(emptyList())
    }

    private suspend fun write(list: List<SyncLogEntry>) = withContext(Dispatchers.IO) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(serializer, list))
            tmp.renameTo(file)
        }
        Unit
    }
}
