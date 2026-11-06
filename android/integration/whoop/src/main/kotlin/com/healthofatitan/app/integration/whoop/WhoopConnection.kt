package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.core.database.dao.MetricDao
import com.healthofatitan.app.core.network.EdgeConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.IOException
import java.time.Clock

/** What the WHOOP screen and «Сегодня» need to know about the connection. */
data class WhoopStatus(
    /** The build knows the Worker address (EDGE_BASE_URL). */
    val configured: Boolean,
    val connected: Boolean,
    val whoopUserId: String? = null,
    val lastSyncAt: Long? = null,
    val lastAttemptAt: Long? = null,
    val syncStatus: SyncStatus? = null,
    /** Nights in the personal norm (A25 P1 needs 14). */
    val baselineNights: Int = 0,
)

sealed interface LoginStart {
    data class Ready(val config: AuthorizeConfig, val pkce: Pkce) : LoginStart

    data object Offline : LoginStart

    data object NotConfigured : LoginStart
}

sealed interface LoginOutcome {
    data object Connected : LoginOutcome

    data class NotAllowed(val whoopUserId: String) : LoginOutcome

    data object Rejected : LoginOutcome

    data object Offline : LoginOutcome

    data object NotConfigured : LoginOutcome
}

sealed interface DisconnectOutcome {
    data object Done : DisconnectOutcome

    /** The server copy can only be wiped online; nothing was removed yet. */
    data object NeedsNetwork : DisconnectOutcome
}

/**
 * WHOOP connection for the UI (A18 И1.1): login through the Worker, status, sync, disconnect.
 * Secrets never pass through here except the device session (to the credential store) and the
 * access token (to the in-memory cache).
 */
class WhoopConnection(
    private val edgeConfig: EdgeConfig,
    private val edge: EdgeClient,
    private val store: WhoopCredentialStore,
    private val tokens: WhoopAccessTokens,
    private val syncer: WhoopSyncer,
    private val scheduler: WhoopSyncScheduler,
    private val dao: MetricDao,
    private val clock: Clock,
) {

    val status: Flow<WhoopStatus> = combine(store.credentials, dao.observeSyncState(WhoopSyncer.SOURCE)) { creds, sync ->
        WhoopStatus(
            configured = edgeConfig.isConfigured,
            connected = creds != null,
            whoopUserId = creds?.whoopUserId,
            lastSyncAt = sync?.lastSuccessAt,
            lastAttemptAt = sync?.lastAttemptAt,
            syncStatus = sync?.status?.let { s -> SyncStatus.entries.firstOrNull { it.name == s } },
            baselineNights = sync?.baselineNights ?: 0,
        )
    }

    /** Step 1: PKCE on the phone, signed state from the Worker. */
    suspend fun beginLogin(): LoginStart {
        if (!edgeConfig.isConfigured) return LoginStart.NotConfigured
        val pkce = Pkce.create()
        return try {
            LoginStart.Ready(edge.authorize(pkce.challenge), pkce)
        } catch (_: EdgeNotConfiguredException) {
            LoginStart.NotConfigured
        } catch (_: IOException) {
            LoginStart.Offline
        }
    }

    /** Step 2, after the redirect `com.healthofatitan.app://oauth/whoop`. */
    suspend fun completeLogin(code: String, state: String, verifier: String): LoginOutcome {
        val result = try {
            edge.token(code, state, verifier)
        } catch (_: EdgeNotConfiguredException) {
            return LoginOutcome.NotConfigured
        } catch (_: IOException) {
            return LoginOutcome.Offline
        } catch (_: kotlinx.serialization.SerializationException) {
            return LoginOutcome.Rejected
        }
        return when (result) {
            is TokenResult.Connected -> {
                store.save(WhoopCredentials(result.session, result.whoopUserId))
                tokens.prime(result.accessToken, result.expiresAt)
                scheduler.syncNow()
                scheduler.ensurePeriodic()
                LoginOutcome.Connected
            }
            is TokenResult.NotAllowed -> LoginOutcome.NotAllowed(result.whoopUserId)
            TokenResult.Rejected -> LoginOutcome.Rejected
        }
    }

    /** «Синхронизировать» and app start: queue a sync; without network say so right away. */
    suspend fun syncNow() {
        if (store.credentials.value == null) return
        if (!scheduler.isOnline()) syncer.markOffline()
        scheduler.syncNow()
    }

    /** On every app start (A18 И1.2 ①): refresh the window unless it is fresh. */
    suspend fun onAppOpen() {
        if (store.credentials.value == null) return
        scheduler.ensurePeriodic()
        val last = dao.syncState(WhoopSyncer.SOURCE)?.lastSuccessAt ?: 0L
        if (clock.millis() - last > FRESH_MS) syncNow()
    }

    /** «Отключить и удалить токены»: wipes the vault on the Worker, then the phone's session. */
    suspend fun disconnect(): DisconnectOutcome {
        val creds = store.credentials.value
        if (creds != null) {
            val wiped = try {
                edge.disconnect(creds.session)
            } catch (_: EdgeNotConfiguredException) {
                true // no Worker means no server copy to wipe
            } catch (_: IOException) {
                return DisconnectOutcome.NeedsNetwork
            }
            if (!wiped) return DisconnectOutcome.NeedsNetwork
        }
        scheduler.cancelAll()
        tokens.clear()
        store.clear()
        dao.clearSyncState(WhoopSyncer.SOURCE)
        return DisconnectOutcome.Done
    }

    private companion object {
        const val FRESH_MS = 15 * 60 * 1000L
    }
}
