package com.healthofatitan.app.feature.mentor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthofatitan.app.core.database.ProfileSettingsStore
import com.healthofatitan.app.core.datastore.MentorSettings
import com.healthofatitan.app.core.network.EdgeConfig
import com.healthofatitan.app.core.security.FieldCipher
import com.healthofatitan.app.domain.engine.CrisisSource
import com.healthofatitan.app.domain.safety.SafetyConsumer
import com.healthofatitan.app.domain.safety.SafetyFlag
import com.healthofatitan.app.feature.mentor.data.EncryptedMentorMemory
import com.healthofatitan.app.feature.mentor.data.MentorApi
import com.healthofatitan.app.feature.mentor.data.MentorMemory
import com.healthofatitan.app.feature.mentor.data.TalkSummary
import com.healthofatitan.app.core.account.AccountAccess
import com.healthofatitan.app.core.account.AccountSession
import com.healthofatitan.app.integration.whoop.day.DailyLoopRepository
import com.healthofatitan.app.integration.whoop.safety.SafetyBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@HiltViewModel
class MentorViewModel @Inject constructor(
    api: MentorApi,
    private val memory: MentorMemory,
    account: AccountSession,
    bus: SafetyBus,
    loop: DailyLoopRepository,
    settings: MentorSettings,
    profile: ProfileSettingsStore,
) : ViewModel() {

    private val conversation = MentorConversation(
        send = { call -> sendWithAccess(api, account, call) },
        safety = {
            val s = bus.current()
            MentorSafety(
                restrictions = s.restrictions(SafetyConsumer.MENTOR).map { it.name }.toSet(),
                edCare = SafetyFlag.ED_CARE in s || s.edCare.active,
                crisis = s.crisis,
                gentle = SafetyFlag.STORM in s || SafetyFlag.GRIEF in s,
            )
        },
        setup = {
            MentorSetup(
                persona = settings.prefs.first().effective,
                addressForm = runCatching { profile.current().addressForm.code }.getOrDefault("n"),
            )
        },
        memory = memory,
        // A07 §4.7: the level, the reason class and the source MENTOR — never the words.
        logCrisis = { a -> loop.logSafety(a, CrisisSource.MENTOR) },
    )

    val ui: StateFlow<MentorUi> = conversation.ui
    val summaries: StateFlow<List<TalkSummary>> = memory.summaries

    init {
        viewModelScope.launch { conversation.start() }
    }

    fun send(text: String) {
        viewModelScope.launch { conversation.send(text) }
    }

    fun helpOpened() = conversation.helpOpened()

    fun newConversation() {
        viewModelScope.launch {
            withContext(NonCancellable) { conversation.close() }
            conversation.start()
        }
    }

    fun forgetAll() = memory.clear()

    override fun onCleared() {
        // The summary outlives the screen: a short call after the person leaves the chat.
        closer.launch { conversation.close() }
    }

    private companion object {
        val closer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object MentorModule {
    @Provides
    @Singleton
    fun provideMentorApi(config: EdgeConfig, http: OkHttpClient, json: Json): MentorApi = MentorApi(config, http, json)

    @Provides
    @Singleton
    fun provideMentorMemory(@ApplicationContext context: Context, cipher: FieldCipher, json: Json): MentorMemory =
        EncryptedMentorMemory(context, cipher, json)
}

/**
 * A65 §3.4: `/llm/mentor` takes the account access token. A token refused on the way (expired,
 * rotated key) gets one fresh try, as in WhoopAccessTokens; no account means «войти».
 */
internal suspend fun sendWithAccess(api: MentorApi, account: AccountSession, call: MentorCall): MentorReply {
    suspend fun token(stale: String? = null): String? = try {
        when (val a = account.accessToken(stale)) {
            is AccountAccess.Ok -> a.token
            else -> null
        }
    } catch (e: java.io.IOException) {
        null
    }
    val first = token() ?: return if (account.isSignedIn) MentorReply.Offline else MentorReply.NeedsLogin
    val reply = api.send(call, first)
    if (reply != MentorReply.NeedsLogin) return reply
    val second = token(stale = first) ?: return MentorReply.NeedsLogin
    return api.send(call, second)
}
