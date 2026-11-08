package com.healthofatitan.app.feature.mentor.data

import com.healthofatitan.app.core.network.EdgeConfig
import com.healthofatitan.app.core.network.await
import com.healthofatitan.app.domain.mentor.ChatLine
import com.healthofatitan.app.domain.mentor.ContextPack
import com.healthofatitan.app.domain.mentor.MentorTier
import com.healthofatitan.app.domain.mentor.Speaker
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One turn sent to `POST /llm/mentor` (backend/edge `src/mentor/mentor.ts`). */
data class MentorCall(
    val persona: String,
    val window: List<ChatLine>,
    val pack: ContextPack,
    val floor: MentorTier,
    val crisisLevel: Int,
    val expertTraining: Boolean,
    val summary: Boolean = false,
)

sealed interface MentorReply {
    data class Text(val text: String, val tier: MentorTier?, val softLimit: Boolean) : MentorReply

    /** The model saw a risk to life (`[[SOS]]`) or the phone's level was ≥ 2: open «Помощь». */
    data class Crisis(val level: Int) : MentorReply

    /** The model declined (stop_reason refusal). */
    data object Refused : MentorReply

    /** No ANTHROPIC_API_KEY (or no EDGE_BASE_URL): a founder action, said plainly. */
    data class NotConfigured(val missing: String?) : MentorReply

    /** The device session is missing or gone: connect WHOOP again (A65 replaces this later). */
    data object NeedsLogin : MentorReply

    /** D-056: the day or the month is spent. */
    data class Limit(val month: Boolean) : MentorReply

    data object Busy : MentorReply

    data object Offline : MentorReply

    data class Failed(val code: Int) : MentorReply
}

/**
 * Client of `/llm/mentor` (A62, A14 §5). It sends only the window of the conversation and the
 * [ContextPack]; it logs nothing. The session token is the device session of slice 1 (`hs1.…`) —
 * the accounts of A65 replace it behind the same `Authorization: Bearer` header.
 */
class MentorApi(
    private val config: EdgeConfig,
    http: OkHttpClient,
    private val json: Json,
) {
    // Opus with thinking may take longer than the WHOOP calls: a longer read and call timeout.
    private val http = http.newBuilder().readTimeout(100, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    @Serializable
    internal data class Turn(val role: String, val content: String)

    @Serializable
    internal data class Pack(
        @SerialName("address_form") val addressForm: String,
        val restrictions: List<String>,
        val flags: List<String>,
        val summaries: List<String>,
        val about: String? = null,
    )

    @Serializable
    internal data class Body(
        // No defaults here: HotJson does not encode default values, and the Worker requires `v`.
        val v: Int,
        val task: String,
        val persona: String,
        @SerialName("tier_floor") val tierFloor: String,
        @SerialName("crisis_level") val crisisLevel: Int,
        @SerialName("expert_topics") val expertTopics: List<String>,
        val context: Pack,
        val messages: List<Turn>,
    )

    @Serializable
    internal data class Answer(
        val type: String? = null,
        val text: String? = null,
        val tier: String? = null,
        val level: Int? = null,
        val degraded: String? = null,
        val error: String? = null,
        val missing: String? = null,
        val scope: String? = null,
    )

    internal fun body(call: MentorCall): String = json.encodeToString(
        Body.serializer(),
        Body(
            v = 1,
            task = if (call.summary) "summary" else "reply",
            persona = call.persona,
            tierFloor = call.floor.code,
            crisisLevel = call.crisisLevel,
            expertTopics = if (call.expertTraining) listOf("training") else emptyList(),
            context = Pack(call.pack.addressForm, call.pack.restrictions, call.pack.flags, call.pack.summaries, call.pack.about),
            messages = call.window.map { Turn(if (it.speaker == Speaker.PERSON) "user" else "assistant", it.text) },
        ),
    )

    suspend fun send(call: MentorCall, session: String?): MentorReply {
        if (!config.isConfigured) return MentorReply.NotConfigured("EDGE_BASE_URL")
        if (session.isNullOrBlank()) return MentorReply.NeedsLogin
        val request = Request.Builder()
            .url(config.url("/llm/mentor"))
            .header("Authorization", "Bearer $session")
            .post(body(call).toRequestBody(jsonType))
            .build()
        return try {
            http.newCall(request).await().use { res ->
                val a = runCatching { json.decodeFromString(Answer.serializer(), res.body.string()) }.getOrNull() ?: Answer()
                when {
                    res.code == 200 -> when (a.type) {
                        "reply", "summary" -> a.text?.takeIf { it.isNotBlank() }
                            ?.let { MentorReply.Text(it, MentorTier.fromCode(a.tier), a.degraded == "soft") }
                            ?: MentorReply.Failed(res.code)
                        "crisis" -> MentorReply.Crisis(a.level ?: 2)
                        "refused" -> MentorReply.Refused
                        else -> MentorReply.Failed(res.code)
                    }
                    res.code == 401 -> MentorReply.NeedsLogin
                    res.code == 403 -> MentorReply.NeedsLogin
                    res.code == 429 && a.error == "budget_exhausted" -> MentorReply.Limit(month = a.scope == "month")
                    res.code == 429 -> MentorReply.Busy
                    res.code == 503 && a.error == "not_configured" -> MentorReply.NotConfigured(a.missing)
                    else -> MentorReply.Failed(res.code)
                }
            }
        } catch (_: IOException) {
            MentorReply.Offline
        }
    }
}
