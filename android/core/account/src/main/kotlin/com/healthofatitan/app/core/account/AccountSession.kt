package com.healthofatitan.app.core.account

import kotlinx.coroutines.flow.StateFlow
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

    /** Google is fine, but there is no HoT account: invite or «Войти через WHOOP». */
    data class NeedsInvite(val signupTicket: String) : SignInOutcome

    data class InviteInvalid(val signupTicket: String?) : SignInOutcome

    data object Rejected : SignInOutcome

    data object Offline : SignInOutcome

    data object NotConfigured : SignInOutcome
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
) {
    private val mutex = Mutex()

    @Volatile
    private var cached: Pair<String, Long>? = null // access token, expiresAt epoch ms

    val credentials: StateFlow<AccountCredentials?> get() = store.credentials

    val isSignedIn: Boolean get() = store.credentials.value != null

    /** Proof of possession of the device key over [binding] (nonce, OAuth state or ticket). */
    fun proof(binding: String): DeviceProof = DeviceProof(key.publicKey(), key.sign(loginMessage(binding)), deviceName)

    /** Keeps a fresh session from any sign-in route (Google, WHOOP, migration). */
    fun establish(tokens: AccountTokens) {
        store.save(AccountCredentials.of(tokens))
        cached = tokens.access to tokens.accessExpiresAt
    }

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
                establish(r.tokens)
                AccountAccess.Ok(r.tokens.access)
            }
            RefreshResult.SignedOut -> {
                signOutLocally()
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
            is GoogleLoginResult.Ok -> {
                establish(r.tokens)
                SignInOutcome.SignedIn
            }
            is GoogleLoginResult.NoAccount -> SignInOutcome.NeedsInvite(r.signupTicket)
            is GoogleLoginResult.InviteInvalid -> SignInOutcome.InviteInvalid(r.signupTicket)
            GoogleLoginResult.Rejected -> SignInOutcome.Rejected
        }
    }

    /** A new account for the Google identity of [signupTicket] with an invite code. */
    suspend fun signup(signupTicket: String, invite: String): SignInOutcome = guarded {
        when (val r = api.signup(signupTicket, invite, proof(signupTicket))) {
            is SignupResult.Ok -> {
                establish(r.tokens)
                SignInOutcome.SignedIn
            }
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
