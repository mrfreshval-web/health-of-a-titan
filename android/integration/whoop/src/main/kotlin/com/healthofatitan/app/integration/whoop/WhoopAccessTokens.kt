package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.core.account.AccountAccess
import com.healthofatitan.app.core.account.AccountSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

/** Outcome of asking for a WHOOP access token. */
sealed interface TokenOutcome {
    data class Ok(val accessToken: String) : TokenOutcome

    data object NotConnected : TokenOutcome

    data object ReauthRequired : TokenOutcome

    data object NotAllowed : TokenOutcome

    /** Worker or WHOOP is down; [code] is the Worker's HTTP status, for the sync log. */
    data class Unavailable(val code: Int? = null) : TokenOutcome
}

/**
 * The WHOOP access token on the phone (A18 И1.1 ②, И1.1a): kept **only in memory**, fetched
 * from the Worker's vault.
 *
 * Who asks (A65 §3, D-062): the account session — its access token comes from [account] — or,
 * until `/auth/migrate` ran, the legacy device session `hs1` stored with the WHOOP credentials.
 *
 * Single-flight: concurrent callers share one `/whoop/access` call (a mutex, then the cache).
 * The vault is single-flight too, so even two phones' worth of callers cause one WHOOP refresh.
 * A rejected WHOOP grant wipes the local WHOOP credential: the user has to connect again.
 */
class WhoopAccessTokens(
    private val edge: EdgeClient,
    private val store: WhoopCredentialStore,
    private val clock: Clock,
    private val account: AccountSession? = null,
) {
    private val mutex = Mutex()

    @Volatile
    private var cached: Pair<String, Long>? = null // token, expiresAt epoch ms

    /** Seeds the cache right after login so the first sync needs no extra call. */
    fun prime(accessToken: String, expiresAt: Long) {
        cached = accessToken to expiresAt
    }

    fun clear() {
        cached = null
    }

    /**
     * @param staleToken a token WHOOP just rejected with 401: it is dropped and a new one fetched.
     * @throws java.io.IOException when there is no connection
     */
    suspend fun get(staleToken: String? = null): TokenOutcome = mutex.withLock {
        val current = cached
        if (current != null && current.first == staleToken) cached = null
        cached?.let { (token, expiresAt) ->
            if (expiresAt - clock.millis() > MIN_TTL_MS) return@withLock TokenOutcome.Ok(token)
        }
        val creds = store.credentials.value ?: return@withLock TokenOutcome.NotConnected
        val legacy = creds.legacySession
        if (legacy != null) return@withLock fromVault(edge.access(legacy), legacyAuth = true)

        val session = account ?: return@withLock TokenOutcome.NotConnected
        var bearer = when (val a = session.accessToken()) {
            is AccountAccess.Ok -> a.token
            AccountAccess.SignedOut -> return@withLock TokenOutcome.NotConnected
            is AccountAccess.Unavailable -> return@withLock TokenOutcome.Unavailable(a.code)
        }
        var result = edge.access(bearer)
        if (result == AccessResult.Unauthorized) {
            // The access token was refused (expired on the way, rotated key): one fresh try.
            bearer = when (val a = session.accessToken(stale = bearer)) {
                is AccountAccess.Ok -> a.token
                AccountAccess.SignedOut -> return@withLock TokenOutcome.NotConnected
                is AccountAccess.Unavailable -> return@withLock TokenOutcome.Unavailable(a.code)
            }
            result = edge.access(bearer)
        }
        fromVault(result, legacyAuth = false)
    }

    private fun fromVault(r: AccessResult, legacyAuth: Boolean): TokenOutcome = when (r) {
        is AccessResult.Token -> {
            cached = r.accessToken to r.expiresAt
            TokenOutcome.Ok(r.accessToken)
        }
        AccessResult.ReauthRequired, AccessResult.NotLinked -> {
            cached = null
            store.clear()
            TokenOutcome.ReauthRequired
        }
        AccessResult.Unauthorized -> if (legacyAuth) {
            // The old session is unknown at the Worker: connect again.
            cached = null
            store.clear()
            TokenOutcome.ReauthRequired
        } else {
            TokenOutcome.Unavailable(401)
        }
        AccessResult.NotAllowed -> TokenOutcome.NotAllowed
        is AccessResult.Unavailable -> TokenOutcome.Unavailable(r.code)
    }

    private companion object {
        /** Ask the vault again when less than this is left (the vault refreshes at 5 min). */
        const val MIN_TTL_MS = 2 * 60 * 1000L
    }
}
