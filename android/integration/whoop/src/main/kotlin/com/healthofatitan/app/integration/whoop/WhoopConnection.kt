package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.core.account.AccountAccess
import com.healthofatitan.app.core.account.AccountChange
import com.healthofatitan.app.core.account.AccountGate
import com.healthofatitan.app.core.account.AccountSession
import com.healthofatitan.app.core.account.MigrateResult
import com.healthofatitan.app.core.database.dao.MetricDao
import com.healthofatitan.app.core.network.EdgeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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

    /** Linking a new WHOOP to the signed-in account needs a step-up first (A65 §2.3). */
    data object StepUpRequired : LoginStart

    /** The account session is gone (or, for a step-up, the account has no WHOOP). */
    data object SignedOut : LoginStart
}

sealed interface LoginOutcome {
    data object Connected : LoginOutcome

    /** No HoT account for this WHOOP user and no (valid) invite (A65 §2.5): nothing was kept. */
    data class NotAllowed(val whoopUserId: String, val inviteInvalid: Boolean = false) : LoginOutcome

    /**
     * This WHOOP belongs to another HoT account than the one on the phone (`409 identity_in_use`).
     * Nothing changed here: no automatic merge (A65 §2.3).
     */
    data object OtherAccount : LoginOutcome

    /** Signed out, this WHOOP's account is not the owner of the data on the phone (A65 §2.3). */
    data object OtherOwner : LoginOutcome

    /** The account already has another WHOOP: unlink it in «Аккаунт» first. */
    data object ProviderLinked : LoginOutcome

    /** The step-up ran out between the two steps: sign in again, then connect. */
    data object StepUpRequired : LoginOutcome

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
    /** Application scope: a sync the user started outlives the screen it was started from. */
    private val scope: CoroutineScope,
    /** The HoT account (A65, D-062): «Войти через WHOOP» signs in to it. */
    private val account: AccountSession,
    /** First name, height and max pulse into the profile after WHOOP connects (A65 §2.4). */
    private val profileImport: WhoopProfileImport? = null,
) {

    /** Live sync state for the sync mark and the status line (started → step → done / failed). */
    val progress: StateFlow<SyncProgress> get() = syncer.progress

    /** Last night and last workout from WHOOP, in memory only (R-11); null until a sync succeeds. */
    val glance: StateFlow<WhoopGlance?> get() = syncer.glance

    /** The last 20 sync attempts for «Журнал синхронизации» (no health values, no tokens). */
    val log: StateFlow<List<SyncLogEntry>> get() = syncer.logEntries

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

    /** Whether «Вход» must lie over the app (A65, D-062); `hs1` holders migrate without it. */
    val accountGate: Flow<AccountGate> = combine(account.credentials, store.credentials) { acc, creds ->
        AccountGate.of(edgeConfig.isConfigured, signedIn = acc != null, legacyWhoopSession = creds?.legacySession != null)
    }

    /**
     * Step 1: PKCE on the phone, signed state from the Worker. Signed out it is «Войти через
     * WHOOP» (`intent=login`); signed in it links WHOOP to the account (`intent=link`, A65 §2.3) —
     * a Google account connects WHOOP this way, and the account's own WHOOP just reconnects.
     */
    suspend fun beginLogin(): LoginStart = begin(if (account.isSignedIn) WhoopIntent.LINK else WhoopIntent.LOGIN)

    /** Step-up with the account's WHOOP (A65 §2.3): before linking Google, unlinking or deleting. */
    suspend fun beginReauth(): LoginStart = begin(WhoopIntent.REAUTH)

    private suspend fun begin(intent: WhoopIntent): LoginStart {
        if (!edgeConfig.isConfigured) return LoginStart.NotConfigured
        val pkce = Pkce.create()
        return try {
            if (intent == WhoopIntent.LOGIN) return LoginStart.Ready(edge.authorize(pkce.challenge), pkce)
            val bearer = bearer() ?: return LoginStart.SignedOut
            when (val r = edge.authorizeFor(pkce.challenge, intent, bearer)) {
                is IntentAuthorize.Ready -> LoginStart.Ready(r.config, pkce)
                IntentAuthorize.StepUpRequired -> LoginStart.StepUpRequired
                IntentAuthorize.NotLinked, IntentAuthorize.SignedOut -> LoginStart.SignedOut
            }
        } catch (_: EdgeNotConfiguredException) {
            LoginStart.NotConfigured
        } catch (_: IOException) {
            LoginStart.Offline
        }
    }

    /** The account access token, null when signed out; throws [IOException] when unreachable. */
    private suspend fun bearer(): String? = when (val a = account.accessToken()) {
        is AccountAccess.Ok -> a.token
        AccountAccess.SignedOut -> null
        is AccountAccess.Unavailable -> throw IOException("account ${a.code}")
    }

    /** WHOOP is connected: tokens in memory, sync and the profile import start. */
    private fun connected(whoopUserId: String, accessToken: String, expiresAt: Long) {
        store.save(WhoopCredentials(whoopUserId))
        tokens.prime(accessToken, expiresAt)
        scheduler.ensurePeriodic()
        scheduler.ensureMorningWatch()
        launchSync(SyncTrigger.LOGIN)
        profileImport?.let { import -> scope.launch { runCatching { import.import() } } }
    }

    /**
     * Step 2, after the redirect `com.healthofatitan.app://oauth/whoop`: the code goes to the Worker
     * with the device proof over the state; the answer is a session of the HoT account (A65 §2.2).
     * [invite] only matters for a WHOOP user without an account yet.
     */
    suspend fun completeLogin(code: String, state: String, verifier: String, invite: String? = null): LoginOutcome {
        if (account.isSignedIn) return completeLink(code, state, verifier)
        val result = try {
            edge.token(code, state, verifier, account.proof(state), invite)
        } catch (_: EdgeNotConfiguredException) {
            return LoginOutcome.NotConfigured
        } catch (_: IOException) {
            return LoginOutcome.Offline
        } catch (_: kotlinx.serialization.SerializationException) {
            return LoginOutcome.Rejected
        }
        return when (result) {
            is TokenResult.Connected -> {
                val current = account.credentials.value?.accountId
                if (current != null && current != result.account.accountId) {
                    // One account per install (A65 §2.3): the new session is ended at once.
                    account.discard(result.account)
                    return LoginOutcome.OtherAccount
                }
                // Another account owns the data on the phone: nothing is kept until «Стереть и войти» (A65 §2.3).
                if (!account.establish(result.account)) return LoginOutcome.OtherOwner
                connected(result.whoopUserId, result.accessToken, result.expiresAt)
                LoginOutcome.Connected
            }
            is TokenResult.NotAllowed -> LoginOutcome.NotAllowed(result.whoopUserId, result.inviteInvalid)
            TokenResult.Rejected -> LoginOutcome.Rejected
        }
    }

    /** `intent=link`: WHOOP joins the account on the phone; no new session (A65 §2.3). */
    private suspend fun completeLink(code: String, state: String, verifier: String): LoginOutcome {
        val result = try {
            val bearer = bearer() ?: return LoginOutcome.Rejected
            edge.tokenFor(code, state, verifier, WhoopIntent.LINK, bearer)
        } catch (_: EdgeNotConfiguredException) {
            return LoginOutcome.NotConfigured
        } catch (_: IOException) {
            return LoginOutcome.Offline
        } catch (_: kotlinx.serialization.SerializationException) {
            return LoginOutcome.Rejected
        }
        return when (result) {
            is IntentToken.Linked -> {
                if (result.identities.isNotEmpty()) account.applyIdentities(result.identities)
                connected(result.whoopUserId, result.accessToken, result.expiresAt)
                LoginOutcome.Connected
            }
            IntentToken.InUse -> LoginOutcome.OtherAccount
            IntentToken.ProviderLinked -> LoginOutcome.ProviderLinked
            IntentToken.StepUpRequired -> LoginOutcome.StepUpRequired
            is IntentToken.Reauthed, IntentToken.Mismatch, IntentToken.SignedOut, IntentToken.Rejected -> LoginOutcome.Rejected
        }
    }

    /** Step 2 of the WHOOP step-up: the session counts as fresh for 10 minutes. */
    suspend fun completeReauth(code: String, state: String, verifier: String): AccountChange {
        val result = try {
            val bearer = bearer() ?: return AccountChange.SignedOut
            edge.tokenFor(code, state, verifier, WhoopIntent.REAUTH, bearer)
        } catch (_: EdgeNotConfiguredException) {
            return AccountChange.Failed(503)
        } catch (_: IOException) {
            return AccountChange.Offline
        } catch (_: kotlinx.serialization.SerializationException) {
            return AccountChange.Failed(0)
        }
        return when (result) {
            is IntentToken.Reauthed -> {
                if (store.credentials.value != null) tokens.prime(result.accessToken, result.expiresAt)
                AccountChange.Done()
            }
            IntentToken.Mismatch -> AccountChange.Mismatch
            IntentToken.SignedOut -> AccountChange.SignedOut
            else -> AccountChange.Failed(400)
        }
    }

    /**
     * After «Отвязать WHOOP» (the server already revoked and wiped the tokens): the phone forgets
     * the WHOOP session too; the synced history stays.
     */
    suspend fun forgetAfterUnlink() {
        scheduler.cancelAll()
        syncer.reset()
        tokens.clear()
        store.clear()
        dao.clearSyncState(WhoopSyncer.SOURCE)
    }

    /**
     * A65 §3.5, on every start: the WHOOP session `hs1` of slice 1 becomes a HoT account with the
     * WHOOP identity, without any screen. Without network it waits for the next start; a session
     * the Worker no longer knows is dropped (the person signs in again).
     */
    suspend fun migrateLegacySession() {
        val creds = store.credentials.value ?: return
        val legacy = creds.legacySession ?: return
        val result = try {
            account.migrate(legacy)
        } catch (_: IOException) {
            return
        } catch (_: kotlinx.serialization.SerializationException) {
            return
        }
        when (result) {
            is MigrateResult.Ok -> store.save(WhoopCredentials(result.whoopUserId))
            MigrateResult.Rejected -> {
                tokens.clear()
                store.clear()
            }
            // Another account owns the data on the phone: «Вход» asks first (A65 §2.3).
            is MigrateResult.Unavailable, MigrateResult.OtherOwner -> Unit
        }
    }

    /**
     * «Обновить», pull-to-refresh and app start: the sync runs right away in the app scope, so the
     * user sees it start, progress and end. Without network it says so at once and leaves the
     * retry to WorkManager («Обновлю, когда будет связь»); a retryable failure is queued too.
     */
    suspend fun syncNow(trigger: SyncTrigger = SyncTrigger.MANUAL) {
        if (store.credentials.value == null) return
        if (!scheduler.isOnline()) {
            syncer.markOffline(trigger)
            scheduler.syncNow()
            return
        }
        launchSync(trigger)
    }

    private fun launchSync(trigger: SyncTrigger) {
        scope.launch {
            val outcome = runCatching { syncer.sync(trigger) }.getOrNull()
            if (outcome is SyncOutcome.Retry) scheduler.syncNow()
        }
    }

    /**
     * On every app start (A18 И1.2 ①): refresh the window unless it is fresh. After a restart the
     * in-memory glance is gone, so «Сегодня» would lose last night: then refresh as well.
     */
    suspend fun onAppOpen() {
        if (store.credentials.value == null) return
        scheduler.ensurePeriodic()
        scheduler.ensureMorningWatch()
        val last = dao.syncState(WhoopSyncer.SOURCE)?.lastSuccessAt ?: 0L
        if (clock.millis() - last > FRESH_MS || syncer.glance.value == null) syncNow(SyncTrigger.APP_OPEN)
        profileImport?.let { import -> scope.launch { runCatching { import.importIfMissing() } } }
    }

    suspend fun clearLog() = syncer.clearLog()

    /** «Отключить и удалить токены»: wipes the vault on the Worker, then the phone's session. */
    suspend fun disconnect(): DisconnectOutcome {
        val creds = store.credentials.value
        if (creds != null) {
            val wiped = try {
                // The legacy session before migration, else the account session (A65 §3).
                val bearer = creds.legacySession ?: when (val a = account.accessToken()) {
                    is AccountAccess.Ok -> a.token
                    AccountAccess.SignedOut -> null // no session: nothing on the server is ours to wipe
                    is AccountAccess.Unavailable -> return DisconnectOutcome.NeedsNetwork
                }
                bearer == null || edge.disconnect(bearer)
            } catch (_: EdgeNotConfiguredException) {
                true // no Worker means no server copy to wipe
            } catch (_: IOException) {
                return DisconnectOutcome.NeedsNetwork
            }
            if (!wiped) return DisconnectOutcome.NeedsNetwork
        }
        scheduler.cancelAll()
        syncer.reset()
        tokens.clear()
        store.clear()
        dao.clearSyncState(WhoopSyncer.SOURCE)
        return DisconnectOutcome.Done
    }

    private companion object {
        const val FRESH_MS = 15 * 60 * 1000L
    }
}
