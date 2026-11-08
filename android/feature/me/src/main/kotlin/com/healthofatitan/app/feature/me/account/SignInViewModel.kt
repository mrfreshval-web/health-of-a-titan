package com.healthofatitan.app.feature.me.account

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthofatitan.app.core.account.AccountConfig
import com.healthofatitan.app.core.account.AccountNotConfiguredException
import com.healthofatitan.app.core.account.AccountSession
import com.healthofatitan.app.core.account.GoogleIdResult
import com.healthofatitan.app.core.account.GoogleIdSource
import com.healthofatitan.app.core.account.SignInOutcome
import com.healthofatitan.app.integration.whoop.AuthRedirect
import com.healthofatitan.app.integration.whoop.AuthorizeConfig
import com.healthofatitan.app.integration.whoop.LoginOutcome
import com.healthofatitan.app.integration.whoop.LoginStart
import com.healthofatitan.app.integration.whoop.WhoopConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

enum class SignInPhase { IDLE, GOOGLE, WHOOP, SIGNUP }

/** One-off notes on «Вход». Texts live in [SignInTexts]. */
enum class SignInNote { CANCELLED, REJECTED, OFFLINE, NOT_CONFIGURED, GOOGLE_NOT_CONFIGURED, NO_GOOGLE_ACCOUNT, INVITE_INVALID, OTHER_ACCOUNT }

data class SignInUiState(
    val phase: SignInPhase = SignInPhase.IDLE,
    /** No HoT account for this Google or WHOOP: «У тебя уже есть HoT?» with the invite field. */
    val needsInvite: Boolean = false,
    /** The Google identity is proven and waits for an invite (A65 §2.1 step 5). */
    val signupTicket: String? = null,
    /** A WHOOP user without an account: this id goes to the person who invites. */
    val whoopUserId: String? = null,
    val note: SignInNote? = null,
    val googleAvailable: Boolean = true,
)

/**
 * «Вход» (A65 §2.1–2.2, D-062): two buttons, Google and WHOOP, one account. The screen closes by
 * itself when the account appears on the phone (the gate in MainActivity watches it).
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val account: AccountSession,
    private val whoop: WhoopConnection,
    private val google: GoogleIdSource,
    config: AccountConfig,
    private val saved: SavedStateHandle,
) : ViewModel() {

    private val local = MutableStateFlow(
        SignInUiState(
            phase = if (saved.get<String>(KEY_VERIFIER) != null) SignInPhase.WHOOP else SignInPhase.IDLE,
            googleAvailable = config.googleConfigured,
            signupTicket = saved[KEY_TICKET],
            whoopUserId = saved[KEY_WHOOP_ID],
            needsInvite = saved.get<String>(KEY_TICKET) != null || saved.get<String>(KEY_WHOOP_ID) != null,
        ),
    )
    val state: StateFlow<SignInUiState> = local.asStateFlow()

    private val launches = Channel<AuthorizeConfig>(Channel.BUFFERED)

    /** WHOOP authorization requests for the browser (AppAuth intent built by the screen). */
    val authRequests: Flow<AuthorizeConfig> = launches.receiveAsFlow()

    private fun idle(note: SignInNote?) = local.update { it.copy(phase = SignInPhase.IDLE, note = note) }

    /** «Войти через Google»: [activityContext] hosts the Credential Manager sheet. */
    fun signInWithGoogle(activityContext: Context) {
        if (local.value.phase != SignInPhase.IDLE) return
        local.update { it.copy(phase = SignInPhase.GOOGLE, note = null) }
        viewModelScope.launch {
            val nonce = try {
                account.googleNonce()
            } catch (_: AccountNotConfiguredException) {
                return@launch idle(SignInNote.NOT_CONFIGURED)
            } catch (_: IOException) {
                return@launch idle(SignInNote.OFFLINE)
            }
            when (val g = google.idToken(activityContext, nonce)) {
                is GoogleIdResult.Ok -> onOutcome(account.completeGoogle(g.idToken, nonce))
                GoogleIdResult.Cancelled -> idle(SignInNote.CANCELLED)
                GoogleIdResult.NoGoogleAccount -> idle(SignInNote.NO_GOOGLE_ACCOUNT)
                GoogleIdResult.NotConfigured -> idle(SignInNote.GOOGLE_NOT_CONFIGURED)
                GoogleIdResult.Failed -> idle(SignInNote.REJECTED)
            }
        }
    }

    private fun onOutcome(outcome: SignInOutcome) {
        when (outcome) {
            SignInOutcome.SignedIn -> {
                clearInvite()
                idle(null)
            }
            is SignInOutcome.NeedsInvite -> {
                saved[KEY_TICKET] = outcome.signupTicket
                local.update { it.copy(phase = SignInPhase.IDLE, needsInvite = true, signupTicket = outcome.signupTicket, whoopUserId = null, note = null) }
            }
            is SignInOutcome.InviteInvalid -> {
                outcome.signupTicket?.let { saved[KEY_TICKET] = it }
                local.update {
                    it.copy(phase = SignInPhase.IDLE, needsInvite = true, signupTicket = outcome.signupTicket ?: it.signupTicket, note = SignInNote.INVITE_INVALID)
                }
            }
            SignInOutcome.Rejected -> idle(SignInNote.REJECTED)
            SignInOutcome.Offline -> idle(SignInNote.OFFLINE)
            SignInOutcome.NotConfigured -> idle(SignInNote.NOT_CONFIGURED)
        }
    }

    /** «Войти через WHOOP»: the PKCE + state flow of slice 1; [invite] only for a new account. */
    fun signInWithWhoop(invite: String? = null) {
        if (local.value.phase != SignInPhase.IDLE) return
        local.update { it.copy(phase = SignInPhase.WHOOP, note = null) }
        if (invite != null) saved[KEY_INVITE] = invite.trim() else saved.remove<String>(KEY_INVITE)
        viewModelScope.launch {
            when (val start = whoop.beginLogin()) {
                is LoginStart.Ready -> {
                    saved[KEY_VERIFIER] = start.pkce.verifier
                    launches.send(start.config)
                }
                LoginStart.Offline -> idle(SignInNote.OFFLINE)
                LoginStart.NotConfigured -> idle(SignInNote.NOT_CONFIGURED)
            }
        }
    }

    fun onWhoopRedirect(redirect: AuthRedirect) {
        val verifier = saved.get<String>(KEY_VERIFIER)
        val invite = saved.get<String>(KEY_INVITE)
        saved.remove<String>(KEY_VERIFIER)
        saved.remove<String>(KEY_INVITE)
        when {
            redirect is AuthRedirect.Cancelled -> idle(SignInNote.CANCELLED)
            redirect !is AuthRedirect.Code || verifier == null -> idle(SignInNote.REJECTED)
            else -> viewModelScope.launch {
                when (val outcome = whoop.completeLogin(redirect.code, redirect.state, verifier, invite)) {
                    LoginOutcome.Connected -> {
                        clearInvite()
                        idle(null)
                    }
                    is LoginOutcome.NotAllowed -> {
                        saved[KEY_WHOOP_ID] = outcome.whoopUserId
                        saved.remove<String>(KEY_TICKET)
                        local.update {
                            it.copy(
                                phase = SignInPhase.IDLE,
                                needsInvite = true,
                                signupTicket = null,
                                whoopUserId = outcome.whoopUserId,
                                note = if (outcome.inviteInvalid) SignInNote.INVITE_INVALID else null,
                            )
                        }
                    }
                    LoginOutcome.OtherAccount -> idle(SignInNote.OTHER_ACCOUNT)
                    LoginOutcome.Rejected -> idle(SignInNote.REJECTED)
                    LoginOutcome.Offline -> idle(SignInNote.OFFLINE)
                    LoginOutcome.NotConfigured -> idle(SignInNote.NOT_CONFIGURED)
                }
            }
        }
    }

    /** «Создать аккаунт» with the invite code: Google through its ticket, WHOOP through a new login. */
    fun submitInvite(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty() || local.value.phase != SignInPhase.IDLE) return
        val ticket = local.value.signupTicket
        if (ticket == null) {
            signInWithWhoop(invite = trimmed)
            return
        }
        local.update { it.copy(phase = SignInPhase.SIGNUP, note = null) }
        viewModelScope.launch { onOutcome(account.signup(ticket, trimmed)) }
    }

    /** «Назад» from the invite step. */
    fun backToChoice() {
        clearInvite()
        local.update { it.copy(needsInvite = false, signupTicket = null, whoopUserId = null, note = null) }
    }

    fun dismissNote() = local.update { it.copy(note = null) }

    private fun clearInvite() {
        saved.remove<String>(KEY_TICKET)
        saved.remove<String>(KEY_WHOOP_ID)
        saved.remove<String>(KEY_INVITE)
    }

    private companion object {
        const val KEY_VERIFIER = "signin_whoop_verifier"
        const val KEY_INVITE = "signin_invite"
        const val KEY_TICKET = "signin_ticket"
        const val KEY_WHOOP_ID = "signin_whoop_id"
    }
}
