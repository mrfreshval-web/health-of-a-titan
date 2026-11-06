package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.MetricDao
import com.healthofatitan.app.core.database.entity.BaselineSnapshotEntity
import com.healthofatitan.app.core.database.entity.MetricDailyEntity
import com.healthofatitan.app.core.database.entity.SyncStateEntity
import com.healthofatitan.app.domain.metrics.MetricEngine
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.time.Clock
import java.time.Duration

/** Sync status as stored in `sync_state.status` (S1, no health values). */
enum class SyncStatus { OK, OFFLINE, UNAVAILABLE, REAUTH, NOT_ALLOWED, RATE_LIMITED, ERROR }

sealed interface SyncOutcome {
    data class Done(val days: Int, val baselineNights: Int) : SyncOutcome

    /** Worth retrying later (no network, WHOOP or Worker down, rate limit). */
    data class Retry(val status: SyncStatus) : SyncOutcome

    /** Retrying will not help until the user acts (connect again, allowlist). */
    data class Stop(val status: SyncStatus?) : SyncOutcome
}

/**
 * One sync (A18 И1.2): the window (28 days of norm + 7 for the trend) is loaded into memory,
 * turned into HoT metrics and dropped. Only `MetricDaily`, `BaselineSnapshot` and the sync
 * status are written, in one transaction (D-009, D-023: each value with `metricId@ver`).
 */
class WhoopSyncer(
    private val api: WhoopApi,
    private val dao: MetricDao,
    private val store: WhoopCredentialStore,
    private val clock: Clock,
) {

    suspend fun sync(): SyncOutcome {
        if (store.credentials.value == null) return SyncOutcome.Stop(null)
        val now = clock.instant()
        val previous = dao.syncState(SOURCE)
        val outcome = runCatching {
            val window = api.window(from = now.minus(WINDOW), to = now)
            val today = now.atZone(clock.zone).toLocalDate()
            val days = WhoopWindowMapper.map(window, clock.zone).filter { it.dayKey <= today }
            // `window` goes out of scope here: raw WHOOP data is never written anywhere.
            MetricEngine.compute(days)
        }
        val result = outcome.getOrElse { e ->
            val (status, retry) = when (e) {
                is WhoopApiException.NotConnected -> null to false
                is WhoopApiException.ReauthRequired -> SyncStatus.REAUTH to false
                is WhoopApiException.NotAllowed -> SyncStatus.NOT_ALLOWED to false
                is WhoopApiException.RateLimited -> SyncStatus.RATE_LIMITED to true
                is WhoopApiException.Unavailable, is EdgeNotConfiguredException -> SyncStatus.UNAVAILABLE to true
                is SerializationException, is IllegalArgumentException -> SyncStatus.ERROR to false
                is IOException -> SyncStatus.OFFLINE to true
                else -> throw e
            }
            if (status != null) {
                dao.upsertSyncState(
                    SyncStateEntity(SOURCE, status.name, now.toEpochMilli(), previous?.lastSuccessAt, previous?.baselineNights ?: 0),
                )
            }
            return if (retry) SyncOutcome.Retry(status!!) else SyncOutcome.Stop(status)
        }

        val at = now.toEpochMilli()
        val user = HotDatabase.LOCAL_USER
        dao.saveSync(
            daily = result.daily.map {
                MetricDailyEntity(user, it.dayKey.toString(), it.metric.id, it.metric.version, it.value, it.z, it.level, it.coverage, it.ewma7, at)
            },
            baselines = result.baselines.map {
                BaselineSnapshotEntity(user, it.dayKey.toString(), it.input.id, it.median28, it.mad28, it.n, at)
            },
            state = SyncStateEntity(SOURCE, SyncStatus.OK.name, at, at, result.baselineNights),
        )
        return SyncOutcome.Done(days = result.daily.map { it.dayKey }.distinct().size, baselineNights = result.baselineNights)
    }

    /** Records that the phone has no network right now (the background retry waits for it). */
    suspend fun markOffline() {
        val previous = dao.syncState(SOURCE)
        dao.upsertSyncState(
            SyncStateEntity(SOURCE, SyncStatus.OFFLINE.name, clock.millis(), previous?.lastSuccessAt, previous?.baselineNights ?: 0),
        )
    }

    companion object {
        const val SOURCE = "whoop"

        /** 28 days of personal norm (A25 P1) + 7 days so the EWMA and the 7-day debt are complete. */
        val WINDOW: Duration = Duration.ofDays(MetricEngine.BASELINE_DAYS + 7)
    }
}
