package com.healthofatitan.app.core.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Clock

/** An access token for the Worker, or why there is none. */
sealed interface AccountAccess {
    data class Ok(val token: String) : AccountAccess

    /** No account on the phone, or the server ended the session: «Вход» again. */
    data object SignedOut : AccountAccess

    /** The Worker answered with an error; keep everything and retry later. */
    data class Unavailable(val code: Int) : AccountAccess
}

/** The end of a sign-in attempt, for the «Вход» screen. */
sealed interface SignInOutcome {
    data object SignedIn : SignInOutcome

    /**
     * The account is fine, but the phone keeps the data of another HoT account (A65 §2.3). Nothing
     * was kept yet: «Стереть и войти» or «Отмена» ([AccountSession.otherOwner]).
     */
    data object OtherOwner : SignInOutcome

    /** Google is fine, but there is no HoT account: invite or «Войти через WHOOP». */
    data class NeedsInvite(val signupTicket: String) : SignInOutcome

    data class InviteInvalid(val signupTicket: String?) : SignInOutcome

    data object Rejected : SignInOutcome

    data object Offline : SignInOutcome

    data object NotConfigured : SignInOutcome
}

/** «Я › Аккаунт» as the Worker sees it now. */
sealed interface AccountViewResult {
    data class Ok(val view: AccountView) : AccountViewResult

    data object SignedOut : AccountViewResult

    data object Offline : AccountViewResult

    data class Failed(val code: Int) : AccountViewResult
}

/** The end of a change in «Я › Аккаунт» (A65 §2.3, §3.4, §6). */
sealed interface AccountChange {
    /** Done; [identities] when the Worker answered with the new list of ways in. */
    data class Done(val identities: List<IdentityDto>? = null) : AccountChange

    /** Sign in again with an identity of the account first (step-up, ≤ 10 min). */
    data object StepUpRequired : AccountChange

    /** This Google or WHOOP belongs to another HoT account: no merge (A65 §2.3). */
    data class IdentityInUse(val provider: String) : AccountChange

    /** The account already has a Google (or a WHOOP): unlink it first. */
    data object ProviderLinked : AccountChange

    /** The last way in cannot be unlinked. */
    data object LastIdentity : AccountChange

    /** The step-up came with another Google or WHOOP than the account's own. */
    data object Mismatch : AccountChange

    data object SignedOut : AccountChange

    data object Offline : AccountChange

    data class Failed(val code: Int) : AccountChange
}

/**
 * The HoT account on the phone (A65 §3, D-062): one place for the session.
 *
 *  - The refresh token is in [AccountCredentialStore] (Tink + Keystore); the access token (15 min)
 *    lives only in memory here.
 *  - [accessToken] is single-flight: concurrent callers share one `/auth/refresh`. Each refresh is
 *    signed by the [DeviceKey], and the rotated refresh token is stored before anyone sees the new
 *    access token. A `401` from refresh (logout elsewhere, reuse detected, expiry) signs out here.
 *  - Sign-in endpoints get the device proof over the server nonce, the OAuth state or the ticket.
 */
class AccountSession(
    private val api: AuthApi,
    private val store: AccountCredentialStore,
    private val key: DeviceKey,
    private val clock: Clock,
    private val deviceName: String,
    /** Whose data is on the phone: survives a sign-out, only the wipe removes it. */
    private val owner: DataOwnerStore = InMemoryDataOwnerStore(),
) {
    private val mutex = Mutex()

    init {
        // Installs of slices А1–А2 had no owner yet: the account signed in now is the owner.
        if (owner.get() == null) store.credentials.value?.let { owner.set(it.accountId) }
    }

    private val otherOwnerState = MutableStateFlow<AccountTokens?>(null)

    /**
     * A sign-in into another account than the owner of the data on the phone: held here, not
     * kept, until the person chooses «Стереть и войти» or «Отмена» (A65 §2.3).
     */
    private val otherOwnerFlag = MutableStateFlow(false)
    val otherOwner: StateFlow<Boolean> = otherOwnerFlag.asStateFlow()

    @Volatile
    private var cached: Pair<String, Long>? = null // access token, expiresAt epoch ms

    val credentials: StateFlow<AccountCredentials?> get() = store.credentials

    private val deletedFlag = MutableStateFlow(false)

    /**
     * The Worker said `account_deleted` (A65 §6: deleted on another phone or on the web page). The
     * app wipes the phone when this turns true; until then the session stays as it is, so a wipe
     * interrupted by a kill happens again on the next start.
     */
    val deleted: StateFlow<Boolean> = deletedFlag.asStateFlow()

    val isSignedIn: Boolean get() = store.credentials.value != null

    /** Proof of possession of the device key over [binding] (nonce, OAuth state or ticket). */
    fun proof(binding: String): DeviceProof = DeviceProof(key.publicKey(), key.sign(loginMessage(binding)), deviceName)

    /**
     * Keeps a fresh session from a sign-in route (Google, WHOOP, migration) — only when the data on
     * the phone is this account's (or nobody's). Another owner: nothing is kept, the session waits
     * in [otherOwner], and the result is false.
     */
    fun establish(tokens: AccountTokens): Boolean {
        val current = owner.get()
        if (current != null && current != tokens.accountId) {
            otherOwnerState.value = tokens
            otherOwnerFlag.value = true
            return false
        }
        if (current == null) owner.set(tokens.accountId)
        keep(tokens)
        return true
    }

    private fun keep(tokens: AccountTokens) {
        store.save(AccountCredentials.of(tokens))
        cached = tokens.access to tokens.accessExpiresAt
    }

    /** «Отмена» on «На телефоне данные другого аккаунта»: the new session is ended, the phone stays as it was. */
    suspend fun cancelOtherOwner() {
        otherOwnerState.value?.let { discard(it) }
        otherOwnerState.value = null
        otherOwnerFlag.value = false
    }

    /**
     * «Стереть и войти», before the wipe: the held session is ended on the server too (the wipe
     * takes its refresh token with everything else); after the restart the person signs in again.
     */
    suspend fun endOtherOwnerBeforeWipe() = cancelOtherOwner()

    /**
     * A valid access token. [stale] is a token the Worker just refused: it is dropped first.
     * @throws IOException when there is no connection
     */
    suspend fun accessToken(stale: String? = null): AccountAccess = mutex.withLock {
        if (stale != null && cached?.first == stale) cached = null
        cached?.let { (token, exp) -> if (exp - clock.millis() > MIN_TTL_MS) return@withLock AccountAccess.Ok(token) }
        val creds = store.credentials.value ?: return@withLock AccountAccess.SignedOut
        val ts = clock.millis() / 1000
        when (val r = api.refresh(creds.refresh, ts, key.sign(refreshMessage(creds.refresh, ts)))) {
            is RefreshResult.Ok -> {
                keep(r.tokens)
                AccountAccess.Ok(r.tokens.access)
            }
            RefreshResult.SignedOut -> {
                signOutLocally()
                AccountAccess.SignedOut
            }
            RefreshResult.Deleted -> {
                cached = null
                deletedFlag.value = true
                AccountAccess.SignedOut
            }
            is RefreshResult.Unavailable -> AccountAccess.Unavailable(r.code)
        }
    }

    /** «Войти через Google», step 1: the server nonce for Credential Manager. */
    suspend fun googleNonce(): String = api.nonce()

    /** «Войти через Google», step 2: the ID token Credential Manager returned for [nonce]. */
    suspend fun completeGoogle(idToken: String, nonce: String, invite: String? = null): SignInOutcome = guarded {
        when (val r = api.google(idToken, proof(nonce), invite)) {
            is GoogleLoginResult.Ok -> if (establish(r.tokens)) SignInOutcome.SignedIn else SignInOutcome.OtherOwner
            is GoogleLoginResult.NoAccount -> SignInOutcome.NeedsInvite(r.signupTicket)
            is GoogleLoginResult.InviteInvalid -> SignInOutcome.InviteInvalid(r.signupTicket)
            GoogleLoginResult.Rejected -> SignInOutcome.Rejected
        }
    }

    /** A new account for the Google identity of [signupTicket] with an invite code. */
    suspend fun signup(signupTicket: String, invite: String): SignInOutcome = guarded {
        when (val r = api.signup(signupTicket, invite, proof(signupTicket))) {
            is SignupResult.Ok -> if (establish(r.tokens)) SignInOutcome.SignedIn else SignInOutcome.OtherOwner
            SignupResult.InviteInvalid -> SignInOutcome.InviteInvalid(signupTicket)
            SignupResult.AccountExists, SignupResult.Rejected -> SignInOutcome.Rejected
        }
    }

    /**
     * A65 §3.5: the WHOOP session `hs1` of slice 1 becomes an account session.
     * @throws IOException when there is no connection (retry on the next start)
     */
    suspend fun migrate(legacySession: String): MigrateResult {
        val nonce = api.nonce()
        val r = api.migrate(legacySession, nonce, proof(nonce))
        if (r is MigrateResult.Ok) establish(r.tokens)
        return r
    }

    /** A session the phone does not keep (e.g. it belongs to another account): ended at once. */
    suspend fun discard(tokens: AccountTokens) {
        runCatching { api.logout(tokens.access) }
    }

    /** «Выйти»: the server ends this session (best effort), the phone forgets the account. */
    suspend fun logout() {
        val access = runCatching { accessToken() }.getOrNull()
        if (access is AccountAccess.Ok) runCatching { api.logout(access.token) }
        signOutLocally()
    }

    /**
     * «Выйти везде»: every session of the account ends, this one too. Needs the network.
     * @return false when the server could not be reached; nothing changed then.
     */
    suspend fun logoutAll(): Boolean {
        val access = runCatching { accessToken() }.getOrNull() as? AccountAccess.Ok ?: return false
        val ok = runCatching { api.logoutAll(access.token) }.getOrDefault(false)
        if (ok) signOutLocally()
        return ok
    }

    // -----------------------------------------------------------------------------------------
    // «Я › Аккаунт» (slices А3 and А5)

    /**
     * One account call with the access token; a token the Worker refuses as stale is refreshed
     * once. Null when there is no session. Throws [IOException] without a connection.
     */
    private suspend fun authorized(call: suspend (String) -> AuthApi.Answer): AuthApi.Answer? {
        var stale: String? = null
        repeat(2) {
            val token = when (val a = accessToken(stale)) {
                is AccountAccess.Ok -> a.token
                AccountAccess.SignedOut -> return null
                is AccountAccess.Unavailable -> return AuthApi.Answer(a.code, null, "")
            }
            val answer = call(token)
            if (answer.code != 401 || answer.error != "unauthorized") return answer
            stale = token
        }
        return null
    }

    /** «Я › Аккаунт»: identities, devices, the step-up window. */
    suspend fun account(): AccountViewResult = try {
        val answer = authorized { api.call("/auth/account", it, get = true) }
        when {
            answer == null || answer.code == 401 -> AccountViewResult.SignedOut
            answer.ok -> AccountViewResult.Ok(api.accountView(answer.text))
            else -> AccountViewResult.Failed(answer.code)
        }
    } catch (_: IOException) {
        AccountViewResult.Offline
    } catch (_: kotlinx.serialization.SerializationException) {
        AccountViewResult.Failed(0)
    }

    private suspend fun change(path: String, body: JsonObject? = null): AccountChange = try {
        val answer = authorized { api.call(path, it, body) }
        when {
            answer == null -> AccountChange.SignedOut
            answer.ok -> {
                val identities = runCatching { api.identities(answer.text) }.getOrNull()?.takeIf { it.isNotEmpty() }
                identities?.let(::applyIdentities)
                AccountChange.Done(identities)
            }
            answer.error == "step_up_required" -> AccountChange.StepUpRequired
            answer.error == "identity_in_use" -> AccountChange.IdentityInUse(api.error(answer.text).provider.orEmpty())
            answer.error == "provider_linked" -> AccountChange.ProviderLinked
            answer.error == "last_identity" -> AccountChange.LastIdentity
            answer.error == "identity_mismatch" -> AccountChange.Mismatch
            answer.code == 401 && answer.error == "unauthorized" -> AccountChange.SignedOut
            else -> AccountChange.Failed(answer.code)
        }
    } catch (_: IOException) {
        AccountChange.Offline
    }

    /** The identities the Worker answered after a link or an unlink: the stored session follows. */
    fun applyIdentities(identities: List<IdentityDto>) {
        store.credentials.value?.let { store.save(it.withIdentities(identities)) }
    }

    /** Step-up with Google (A65 §2.3): an ID token from Credential Manager for a fresh [googleNonce]. */
    suspend fun reauthGoogle(idToken: String): AccountChange = change("/auth/reauth/google", buildJsonObject { put("id_token", idToken) })

    /** «Привязать Google»: a fresh Google sign-in; needs a step-up ≤ 10 min. */
    suspend fun linkGoogle(idToken: String): AccountChange = change("/auth/link/google", buildJsonObject { put("id_token", idToken) })

    /** «Отвязать»: never the last way in; WHOOP → its tokens are revoked on the server. */
    suspend fun unlink(provider: String): AccountChange = change("/auth/unlink", buildJsonObject { put("provider", provider) })

    /** «Завершить» on one device of the list. */
    suspend fun revokeDevice(id: String): AccountChange = change("/auth/devices/revoke", buildJsonObject { put("id", id) })

    /**
     * «Удалить аккаунт» (A65 §6): the server part. After [AccountChange.Done] the caller wipes the
     * phone; this session no longer exists anywhere.
     */
    suspend fun deleteAccount(): AccountChange = change("/auth/delete")

    /** A refresh at app start, so a deleted account is noticed (and wiped) without any screen. */
    suspend fun checkAlive() {
        if (store.credentials.value != null) runCatching { accessToken() }
    }

    fun signOutLocally() {
        cached = null
        store.clear()
        key.reset()
    }

    private suspend fun guarded(block: suspend () -> SignInOutcome): SignInOutcome = try {
        block()
    } catch (_: AccountNotConfiguredException) {
        SignInOutcome.NotConfigured
    } catch (_: IOException) {
        SignInOutcome.Offline
    } catch (_: kotlinx.serialization.SerializationException) {
        SignInOutcome.Rejected
    }

    private companion object {
        /** Refresh when less than this is left of the 15 minutes. */
        const val MIN_TTL_MS = 60 * 1000L
    }
}
