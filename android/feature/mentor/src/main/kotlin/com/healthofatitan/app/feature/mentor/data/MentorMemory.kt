package com.healthofatitan.app.feature.mentor.data

import android.content.Context
import com.healthofatitan.app.core.security.FieldCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Base64

/** One summary of a finished conversation (A62 §11 п. 2): 3–5 lines, no quotes. */
@Serializable
data class TalkSummary(val at: Long, val text: String)

/**
 * The chronicle of Винс on the phone (A62 §4 level Б, «выжимки разговоров», D-056): only here,
 * never on a server; visible and deletable in the chat menu «Что Винс помнит».
 */
interface MentorMemory {
    val summaries: StateFlow<List<TalkSummary>>

    fun add(summary: TalkSummary)

    /** «Забыть разговоры»: everything goes, at once. */
    fun clear()
}

class InMemoryMentorMemory(initial: List<TalkSummary> = emptyList()) : MentorMemory {
    private val state = MutableStateFlow(initial)
    override val summaries: StateFlow<List<TalkSummary>> = state.asStateFlow()

    override fun add(summary: TalkSummary) {
        state.value = (state.value + summary).takeLast(EncryptedMentorMemory.MAX_KEPT)
    }

    override fun clear() {
        state.value = emptyList()
    }
}

/**
 * Sealed with Tink AEAD (K_field, Android Keystore, A14 §3.1 class S4) in a private prefs file that
 * is not backed up. Not a Room table on purpose in this slice: the accounts work (A65 А1–А6) takes
 * the next Room migration; the summaries move to an SQLCipher table `mentor_summary` (with FTS5 for
 * `search_chronicle`, A62 §4) when the two branches have met.
 */
class EncryptedMentorMemory(
    context: Context,
    private val cipher: FieldCipher,
    private val json: Json,
) : MentorMemory {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val serializer = ListSerializer(TalkSummary.serializer())
    private val state = MutableStateFlow(load())
    override val summaries: StateFlow<List<TalkSummary>> = state.asStateFlow()

    private fun load(): List<TalkSummary> = runCatching {
        val sealed = prefs.getString(KEY, null) ?: return emptyList()
        val plain = String(cipher.decrypt(Base64.getDecoder().decode(sealed), AD), Charsets.UTF_8)
        json.decodeFromString(serializer, plain)
    }.getOrDefault(emptyList()) // a value that no longer decrypts (key lost) reads as «nothing remembered»

    private fun save(list: List<TalkSummary>) {
        val sealed = cipher.encrypt(json.encodeToString(serializer, list).toByteArray(Charsets.UTF_8), AD)
        prefs.edit().putString(KEY, Base64.getEncoder().encodeToString(sealed)).apply()
        state.value = list
    }

    @Synchronized
    override fun add(summary: TalkSummary) = save((state.value + summary).takeLast(MAX_KEPT))

    @Synchronized
    override fun clear() {
        prefs.edit().remove(KEY).apply()
        state.value = emptyList()
    }

    companion object {
        const val PREFS = "hot_mentor_memory"
        private const val KEY = "summaries"
        private val AD = "S4.mentor.summaries".toByteArray()
        const val MAX_KEPT = 30
    }
}
