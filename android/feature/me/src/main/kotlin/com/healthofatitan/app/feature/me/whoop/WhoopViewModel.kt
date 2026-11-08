package com.healthofatitan.app.feature.me.whoop

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthofatitan.app.integration.whoop.AuthRedirect
import com.healthofatitan.app.integration.whoop.AuthorizeConfig
import com.healthofatitan.app.integration.whoop.DisconnectOutcome
import com.healthofatitan.app.integration.whoop.LoginOutcome
import com.healthofatitan.app.integration.whoop.LoginStart
import com.healthofatitan.app.integration.whoop.SyncLogEntry
import com.healthofatitan.app.integration.whoop.SyncProgress
import com.healthofatitan.app.integration.whoop.SyncTrigger
import com.healthofatitan.app.integration.whoop.WhoopConnection
import com.healthofatitan.app.integration.whoop.WhoopStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-off notes on the WHOOP screen. Texts live in [WhoopTexts]. */
enum class WhoopNote {
    CANCELLED,
    REJECTED,
    OFFLINE,
    SERVER_NOT_READY,
    NEEDS_NETWORK_TO_DISCONNECT,
    DISCONNECTED,
    OTHER_ACCOUNT,

    /** A65 §2.3: linking a new WHOOP to the account needs a fresh sign-in («Аккаунт» does it). */
    STEP_UP,

    /** The account already has another WHOOP. */
    PROVIDER_LINKED,

    /** The account session is gone: «Вход» comes back by itself. */
    SIGNED_OUT,
}

enum class WhoopPhase { IDLE, CONNECTING, DISCONNECTING }

data class WhoopUiState(
    val status: WhoopStatus = WhoopStatus(configured = true, connected = false),
    val phase: WhoopPhase = WhoopPhase.IDLE,
    /** Logged in at WHOOP, but not on the Worker's allowlist: show this id to the founder. */
    val notAllowedUserId: String? = null,
    val note: WhoopNote? = null,
    /** Live sync: drives the sync mark and the status line. */
    val progress: SyncProgress = SyncProgress.Idle,
    /** «Журнал синхронизации»: last 20 attempts, no health values. */
    val log: List<SyncLogEntry> = emptyList(),
)

@HiltViewModel
class WhoopViewModel @Inject constructor(
    private val connection: WhoopConnection,
    private val saved: SavedStateHandle,
) : ViewModel() {

    private data class Local(val phase: WhoopPhase = WhoopPhase.IDLE, val notAllowedUserId: String? = null, val note: WhoopNote? = null)

    private val local = MutableStateFlow(Local(phase = if (saved.get<String>(KEY_VERIFIER) != null) WhoopPhase.CONNECTING else WhoopPhase.IDLE))

    val state: StateFlow<WhoopUiState> = combine(connection.status, local, connection.progress, connection.log) { status, l, progress, log ->
        WhoopUiState(status, l.phase, if (status.connected) null else l.notAllowedUserId, l.note, progress, log)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhoopUiState())

    private val launches = Channel<AuthorizeConfig>(Channel.BUFFERED)

    /** Authorization requests for the browser (AppAuth intent built by the screen). */
    val authRequests: Flow<AuthorizeConfig> = launches.receiveAsFlow()

    fun connect() {
        if (local.value.phase != WhoopPhase.IDLE) return
        local.update { it.copy(phase = WhoopPhase.CONNECTING, note = null) }
        viewModelScope.launch {
            when (val start = connection.beginLogin()) {
                is LoginStart.Ready -> {
                    // Survives process death while the browser is open (SavedStateHandle).
                    saved[KEY_VERIFIER] = start.pkce.verifier
                    launches.send(start.config)
                }
                LoginStart.Offline -> local.update { it.copy(phase = WhoopPhase.IDLE, note = WhoopNote.OFFLINE) }
                LoginStart.NotConfigured -> local.update { it.copy(phase = WhoopPhase.IDLE, note = WhoopNote.SERVER_NOT_READY) }
                LoginStart.StepUpRequired -> local.update { it.copy(phase = WhoopPhase.IDLE, note = WhoopNote.STEP_UP) }
                LoginStart.SignedOut -> local.update { it.copy(phase = WhoopPhase.IDLE, note = WhoopNote.SIGNED_OUT) }
            }
        }
    }

    fun onRedirect(redirect: AuthRedirect) {
        val verifier = saved.get<String>(KEY_VERIFIER)
        saved.remove<String>(KEY_VERIFIER)
        when {
            redirect is AuthRedirect.Cancelled -> local.update { it.copy(phase = WhoopPhase.IDLE, note = WhoopNote.CANCELLED) }
            redirect !is AuthRedirect.Code || verifier == null -> local.update { it.copy(phase = WhoopPhase.IDLE, note = WhoopNote.REJECTED) }
            else -> viewModelScope.launch {
                val note: WhoopNote?
                var notAllowed: String? = null
                when (val outcome = connection.completeLogin(redirect.code, redirect.state, verifier)) {
                    LoginOutcome.Connected -> note = null
                    is LoginOutcome.NotAllowed -> {
                        note = null
                        notAllowed = outcome.whoopUserId
                    }
                    LoginOutcome.Rejected -> note = WhoopNote.REJECTED
                    LoginOutcome.OtherAccount -> note = WhoopNote.OTHER_ACCOUNT
                    LoginOutcome.Offline -> note = WhoopNote.OFFLINE
                    LoginOutcome.NotConfigured -> note = WhoopNote.SERVER_NOT_READY
                    LoginOutcome.ProviderLinked -> note = WhoopNote.PROVIDER_LINKED
                    LoginOutcome.StepUpRequired -> note = WhoopNote.STEP_UP
                }
                local.update { Local(phase = WhoopPhase.IDLE, notAllowedUserId = notAllowed, note = note) }
            }
        }
    }

    fun sync() {
        viewModelScope.launch { connection.syncNow(SyncTrigger.MANUAL) }
    }

    fun clearLog() {
        viewModelScope.launch { connection.clearLog() }
    }

    fun disconnect() {
        if (local.value.phase != WhoopPhase.IDLE) return
        local.update { it.copy(phase = WhoopPhase.DISCONNECTING, note = null) }
        viewModelScope.launch {
            val note = when (connection.disconnect()) {
                DisconnectOutcome.Done -> WhoopNote.DISCONNECTED
                DisconnectOutcome.NeedsNetwork -> WhoopNote.NEEDS_NETWORK_TO_DISCONNECT
            }
            local.update { Local(phase = WhoopPhase.IDLE, note = note) }
        }
    }

    fun dismissNote() = local.update { it.copy(note = null) }

    private companion object {
        const val KEY_VERIFIER = "whoop_pkce_verifier"
    }
}
