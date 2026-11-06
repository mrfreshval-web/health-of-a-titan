package com.healthofatitan.app.integration.whoop

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

/** Outcome of asking for a WHOOP access token. */
sealed interface TokenOutcome {
    data class Ok(val accessToken: String) : TokenOutcome

    data object NotConnected : TokenOutcome

    data object ReauthRequired : TokenOutcome

    data object NotAllowed : TokenOutcome

    data object Unavailable : TokenOutcome
}

/**
 * The WHOOP access token on the phone (A18 И1.1 ②, И1.1a): kept **only in memory**, fetched
 * from the Worker's vault with the device session.
 *
 * Single-flight: concurrent callers share one `/whoop/access` call (a mutex, then the cache).
 * The vault is single-flight too, so even two phones' worth of callers cause one WHOOP refresh.
 * A rejected session wipes the local credential: the user has to connect again.
 */
class WhoopAccessTokens(
    private val edge: EdgeClient,
    private val store: WhoopCredentialStore,
    private val clock: Clock,
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
        when (val r = edge.access(creds.session)) {
            is AccessResult.Token -> {
                cached = r.accessToken to r.expiresAt
                TokenOutcome.Ok(r.accessToken)
            }
            AccessResult.ReauthRequired -> {
                cached = null
                store.clear()
                TokenOutcome.ReauthRequired
            }
            AccessResult.NotAllowed -> TokenOutcome.NotAllowed
            AccessResult.Unavailable -> TokenOutcome.Unavailable
        }
    }

    private companion object {
        /** Ask the vault again when less than this is left (the vault refreshes at 5 min). */
        const val MIN_TTL_MS = 2 * 60 * 1000L
    }
}
