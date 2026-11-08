package com.healthofatitan.app.feature.me.account

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthofatitan.app.core.account.AccountChange
import com.healthofatitan.app.core.account.AccountConfig
import com.healthofatitan.app.core.account.AccountCredentials
import com.healthofatitan.app.core.account.AccountNotConfiguredException
import com.healthofatitan.app.core.account.AccountSession
import com.healthofatitan.app.core.account.AccountView
import com.healthofatitan.app.core.account.AccountViewResult
import com.healthofatitan.app.core.account.GoogleIdResult
import com.healthofatitan.app.core.account.GoogleIdSource
import com.healthofatitan.app.core.account.GoogleProfileImport
import com.healthofatitan.app.core.account.LocalDataWiper
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

/** What a confirmation dialog of «Аккаунт» asks about. */
sealed interface AccountConfirm {
    data object SignOut : AccountConfirm

    data object SignOutAll : AccountConfirm

    /** «Выйти и стереть телефон» (review 2026-11-08 А-1). */
    data object Wipe : AccountConfirm

    data class Unlink(val provider: String) : AccountConfirm

    data class EndDevice(val id: String, val name: String) : AccountConfirm
}

/** An action that needs a fresh proof of the person (A65 §2.3, §6): it waits for the step-up. */
enum class FreshAction { LINK_GOOGLE, LINK_WHOOP, UNLINK_GOOGLE, UNLINK_WHOOP, DELETE }

data class AccountUiState(
    val view: AccountView? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val note: AccountNote? = null,
    /** `409 identity_in_use`: «Этот Google уже привязан к другому аккаунту HoT» (A65 §2.3). */
    val inUse: String? = null,
    val confirm: AccountConfirm? = null,
    /** «Удалить аккаунт»: the screen of consequences is open. */
    val deleting: Boolean = false,
    val googleAvailable: Boolean = true,
)

/**
 * «Настройки › Аккаунт» (A65 §2.3, §3.4, §6; slices А3 and А5): the ways in with link and unlink,
 * the devices with «Завершить», «Выйти», «Выйти везде» and «Удалить аккаунт».
 *
 * Linking, unlinking and deletion need a step-up: when this session's proof is older than
 * 10 minutes, the account's Google (sheet) or WHOOP (browser) is asked again first, then the
 * action runs. One account per install: leaving the account wipes the phone ([LocalDataWiper]).
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val account: AccountSession,
    private val whoop: WhoopConnection,
    private val google: GoogleIdSource,
    config: AccountConfig,
    private val googleProfile: GoogleProfileImport,
    private val wiper: LocalDataWiper,
    private val saved: SavedStateHandle,
) : ViewModel() {

    private val local = MutableStateFlow(AccountUiState(googleAvailable = config.googleConfigured, busy = saved.get<String>(KEY_VERIFIER) != null))
    val state: StateFlow<AccountUiState> = local.asStateFlow()

    private val launches = Channel<AuthorizeConfig>(Channel.BUFFERED)

    /** WHOOP authorization requests for the browser (the screen builds the AppAuth intent). */
    val authRequests: Flow<AuthorizeConfig> = launches.receiveAsFlow()

    /** Tests and previews pass a clock; the step-up window is checked against it. */
    internal var now: () -> Long = System::currentTimeMillis

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val r = account.account()) {
                is AccountViewResult.Ok -> local.update { it.copy(view = r.view, loading = false) }
                AccountViewResult.Offline -> local.update { it.copy(loading = false, note = AccountNote.OFFLINE) }
                AccountViewResult.SignedOut -> local.update { it.copy(loading = false) } // «Вход» comes back by itself
                is AccountViewResult.Failed -> local.update { it.copy(loading = false, note = AccountNote.FAILED) }
            }
        }
    }

    private fun note(note: AccountNote?) = local.update { it.copy(busy = false, note = note) }

    fun dismissNote() = local.update { it.copy(note = null) }

    fun ask(confirm: AccountConfirm?) = local.update { it.copy(confirm = confirm) }

    fun openDelete(open: Boolean) = local.update { it.copy(deleting = open, confirm = null) }

    fun closeInUse() = local.update { it.copy(inUse = null) }

    // -----------------------------------------------------------------------------------------
    // Actions with a step-up

    /** «Привязать Google» · «Привязать WHOOP» · «Отвязать» · «Удалить навсегда». */
    fun run(action: FreshAction, activityContext: Context) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, note = null, confirm = null) }
        viewModelScope.launch { fresh(action, activityContext) }
    }

    private suspend fun fresh(action: FreshAction, context: Context) {
        val view = local.value.view ?: return note(AccountNote.FAILED)
        if (view.stepUpUntil - now() > FRESH_MARGIN_MS) return perform(action, context)
        when {
            view.has(AccountCredentials.PROVIDER_GOOGLE) && local.value.googleAvailable -> {
                if (reauthGoogle(context)) perform(action, context)
            }
            view.has(AccountCredentials.PROVIDER_WHOOP) -> startWhoop(FLOW_REAUTH, action)
            else -> note(AccountNote.STEP_UP_FAILED)
        }
    }

    /** The Google sheet again for this account's Google; false (with a note) when it did not work. */
    private suspend fun reauthGoogle(context: Context): Boolean {
        val g = googleToken(context) ?: return false
        return when (val r = account.reauthGoogle(g.idToken)) {
            is AccountChange.Done -> {
                local.update { s -> s.copy(view = s.view?.copy(stepUpUntil = now() + STEP_UP_MS)) }
                true
            }
            else -> {
                onChange(r)
                false
            }
        }
    }

    /** Nonce + Credential Manager; null (with a note) when there is no ID token. */
    private suspend fun googleToken(context: Context): GoogleIdResult.Ok? {
        val nonce = try {
            account.googleNonce()
        } catch (_: AccountNotConfiguredException) {
            note(AccountNote.FAILED)
            return null
        } catch (_: IOException) {
            note(AccountNote.OFFLINE)
            return null
        }
        return when (val g = google.idToken(context, nonce)) {
            is GoogleIdResult.Ok -> g
            GoogleIdResult.Cancelled -> null.also { note(AccountNote.CANCELLED) }
            GoogleIdResult.NoGoogleAccount -> null.also { note(AccountNote.NO_GOOGLE_ACCOUNT) }
            GoogleIdResult.NotConfigured -> null.also { note(AccountNote.GOOGLE_OFF) }
            GoogleIdResult.Failed -> null.also { note(AccountNote.STEP_UP_FAILED) }
        }
    }

    private suspend fun perform(action: FreshAction, context: Context) {
        when (action) {
            FreshAction.LINK_GOOGLE -> {
                val g = googleToken(context) ?: return
                val r = account.linkGoogle(g.idToken)
                if (r is AccountChange.Done) runCatching { googleProfile.import(g) }
                onChange(r, AccountNote.LINKED)
            }
            FreshAction.LINK_WHOOP -> when (val start = whoop.beginLogin()) {
                is LoginStart.Ready -> {
                    saved[KEY_VERIFIER] = start.pkce.verifier
                    saved[KEY_FLOW] = FLOW_LINK
                    launches.send(start.config)
                }
                LoginStart.StepUpRequired -> note(AccountNote.STEP_UP_FAILED)
                LoginStart.Offline -> note(AccountNote.OFFLINE)
                LoginStart.NotConfigured, LoginStart.SignedOut -> note(AccountNote.FAILED)
            }
            FreshAction.UNLINK_GOOGLE -> onChange(account.unlink(AccountCredentials.PROVIDER_GOOGLE), AccountNote.UNLINKED)
            FreshAction.UNLINK_WHOOP -> {
                val r = account.unlink(AccountCredentials.PROVIDER_WHOOP)
                if (r is AccountChange.Done) runCatching { whoop.forgetAfterUnlink() }
                onChange(r, AccountNote.UNLINKED)
            }
            FreshAction.DELETE -> {
                val r = account.deleteAccount()
                if (r is AccountChange.Done) {
                    local.update { it.copy(busy = true, note = null) }
                    wiper.wipe()
                } else {
                    onChange(r)
                }
            }
        }
    }

    private fun onChange(change: AccountChange, done: AccountNote? = null) {
        when (change) {
            is AccountChange.Done -> {
                note(done)
                refresh()
            }
            AccountChange.StepUpRequired -> note(AccountNote.STEP_UP_FAILED)
            is AccountChange.IdentityInUse -> local.update { it.copy(busy = false, inUse = change.provider.ifBlank { AccountCredentials.PROVIDER_GOOGLE }) }
            AccountChange.ProviderLinked -> note(AccountNote.PROVIDER_LINKED)
            AccountChange.LastIdentity -> note(AccountNote.LAST_WAY)
            AccountChange.Mismatch -> note(AccountNote.MISMATCH)
            AccountChange.SignedOut -> note(null)
            AccountChange.Offline -> note(AccountNote.OFFLINE)
            is AccountChange.Failed -> note(AccountNote.FAILED)
        }
    }

    // -----------------------------------------------------------------------------------------
    // WHOOP in the browser: the step-up (reauth) or linking

    private suspend fun startWhoop(flow: String, after: FreshAction) {
        when (val start = whoop.beginReauth()) {
            is LoginStart.Ready -> {
                saved[KEY_VERIFIER] = start.pkce.verifier
                saved[KEY_FLOW] = flow
                saved[KEY_AFTER] = after.name
                launches.send(start.config)
            }
            LoginStart.Offline -> note(AccountNote.OFFLINE)
            else -> note(AccountNote.STEP_UP_FAILED)
        }
    }

    /** The browser came back from WHOOP; [activityContext] hosts a Google sheet if the next step needs it. */
    fun onWhoopRedirect(redirect: AuthRedirect, activityContext: Context) {
        val verifier = saved.get<String>(KEY_VERIFIER)
        val flow = saved.get<String>(KEY_FLOW)
        val after = saved.get<String>(KEY_AFTER)?.let { name -> FreshAction.entries.firstOrNull { it.name == name } }
        listOf(KEY_VERIFIER, KEY_FLOW, KEY_AFTER).forEach { saved.remove<String>(it) }
        when {
            redirect is AuthRedirect.Cancelled -> note(AccountNote.CANCELLED)
            redirect !is AuthRedirect.Code || verifier == null -> note(AccountNote.FAILED)
            flow == FLOW_REAUTH -> viewModelScope.launch {
                when (val r = whoop.completeReauth(redirect.code, redirect.state, verifier)) {
                    is AccountChange.Done -> {
                        local.update { s -> s.copy(view = s.view?.copy(stepUpUntil = now() + STEP_UP_MS)) }
                        if (after != null) perform(after, activityContext) else note(null)
                    }
                    else -> onChange(r)
                }
            }
            else -> viewModelScope.launch {
                when (whoop.completeLogin(redirect.code, redirect.state, verifier)) {
                    LoginOutcome.Connected -> onChange(AccountChange.Done(), AccountNote.LINKED)
                    LoginOutcome.OtherAccount -> local.update { it.copy(busy = false, inUse = AccountCredentials.PROVIDER_WHOOP) }
                    LoginOutcome.ProviderLinked -> note(AccountNote.PROVIDER_LINKED)
                    LoginOutcome.StepUpRequired -> note(AccountNote.STEP_UP_FAILED)
                    LoginOutcome.Offline -> note(AccountNote.OFFLINE)
                    else -> note(AccountNote.FAILED)
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Devices and leaving

    fun endDevice(id: String) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, confirm = null) }
        viewModelScope.launch { onChange(account.revokeDevice(id), AccountNote.DEVICE_ENDED) }
    }

    /**
     * «Выйти»: the server ends this session. Whether the phone is wiped is [SignOutPolicy]: by
     * default the data stays, sealed; signing in to another account asks first (A65 §2.3).
     */
    fun signOut() = leave(wipe = SignOutPolicy.WIPES)

    /** «Выйти и стереть телефон»: the session ends and the phone is wiped. */
    fun signOutAndWipe() = leave(wipe = true)

    private fun leave(wipe: Boolean) {
        local.update { it.copy(busy = true, confirm = null, inUse = null) }
        viewModelScope.launch {
            account.logout()
            if (wipe) wiper.wipe()
        }
    }

    /**
     * «Войти в тот аккаунт» after `identity_in_use`: a sign-out, then «Вход» asks about the data
     * of this account (the owner check). In variant 2 the wipe question comes first.
     */
    fun switchAccount() {
        if (SignOutPolicy.WIPES) local.update { it.copy(inUse = null, confirm = AccountConfirm.Wipe) } else signOut()
    }

    /** «Выйти везде»: needs the server; the phone is wiped only in variant 2 of [SignOutPolicy]. */
    fun signOutAll() {
        if (local.value.busy) return
        local.update { it.copy(busy = true, confirm = null) }
        viewModelScope.launch {
            if (account.logoutAll()) {
                if (SignOutPolicy.WIPES) wiper.wipe()
            } else {
                note(AccountNote.OFFLINE)
            }
        }
    }

    companion object {
        /** A step-up that ends within this margin is renewed first. */
        const val FRESH_MARGIN_MS = 30_000L
        const val STEP_UP_MS = 10 * 60_000L
        private const val KEY_VERIFIER = "account_whoop_verifier"
        private const val KEY_FLOW = "account_whoop_flow"
        private const val KEY_AFTER = "account_whoop_after"
        private const val FLOW_REAUTH = "reauth"
        private const val FLOW_LINK = "link"
    }
}
