package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.MetricDao
import com.healthofatitan.app.core.database.entity.BaselineSnapshotEntity
import com.healthofatitan.app.core.database.entity.MetricDailyEntity
import com.healthofatitan.app.core.database.entity.SyncStateEntity
import com.healthofatitan.app.domain.engine.Anchors
import com.healthofatitan.app.domain.metrics.MetricEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration

/** Sync status as stored in `sync_state.status` (S1, no health values). */
enum class SyncStatus { OK, OFFLINE, UNAVAILABLE, REAUTH, NOT_ALLOWED, DENIED, RATE_LIMITED, ERROR }

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
 *
 * Besides that, every attempt:
 *  - publishes its live [progress] (step by step) for the sync mark and the status line;
 *  - appends one line to the diagnostic [SyncLogStore] (status, HTTP code, counts — no values);
 *  - on success keeps a [WhoopGlance] of the last night and workout **in memory only** ([glance]).
 *
 * Single-flight: a sync that starts while another one runs waits for it and returns its outcome.
 */
class WhoopSyncer(
    private val api: WhoopApi,
    private val dao: MetricDao,
    private val store: WhoopCredentialStore,
    private val clock: Clock,
    private val log: SyncLogStore = InMemorySyncLogStore(),
) {
    private val mutex = Mutex()

    @Volatile
    private var lastOutcome: SyncOutcome? = null

    private val progressState = MutableStateFlow<SyncProgress>(SyncProgress.Idle)
    val progress: StateFlow<SyncProgress> = progressState.asStateFlow()

    private val glanceState = MutableStateFlow<WhoopGlance?>(null)

    /** Raw WHOOP glance of the last successful sync — memory only, never persisted (R-11). */
    val glance: StateFlow<WhoopGlance?> = glanceState.asStateFlow()

    val logEntries: StateFlow<List<SyncLogEntry>> get() = log.entries

    suspend fun sync(trigger: SyncTrigger = SyncTrigger.BACKGROUND): SyncOutcome {
        if (!mutex.tryLock()) {
            mutex.withLock { } // another sync is running: share its result
            return lastOutcome ?: SyncOutcome.Stop(null)
        }
        try {
            return run(trigger).also { lastOutcome = it }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun run(trigger: SyncTrigger): SyncOutcome {
        if (store.credentials.value == null) return SyncOutcome.Stop(null)
        val now = clock.instant()
        val startedMs = clock.millis()
        var step = SyncStep.START
        fun report(next: SyncStep) {
            step = next
            progressState.value = SyncProgress.Running(next, now.toEpochMilli(), trigger)
        }
        report(SyncStep.START)
        val previous = dao.syncState(SOURCE)
        var counts: Triple<Int, Int, WhoopGlance>? = null // nights, workouts, glance
        var routine: WhoopRoutineMapper.Routine? = null
        val outcome = runCatching {
            val window = api.window(from = now.minus(WINDOW), to = now, onStep = ::report)
            report(SyncStep.COMPUTE)
            val today = now.atZone(clock.zone).toLocalDate()
            val days = WhoopWindowMapper.map(window, clock.zone).filter { it.dayKey <= today }
            routine = WhoopRoutineMapper.map(window, clock.zone)
            counts = Triple(
                window.sleeps.count { !it.nap && it.scoreState == SCORED },
                window.workouts.count { it.scoreState == SCORED },
                WhoopGlanceMapper.from(window, clock.zone, now.toEpochMilli()),
            )
            // `window` goes out of scope here: raw WHOOP data is never written anywhere.
            MetricEngine.compute(days)
        }
        val result = outcome.getOrElse { e ->
            val classified = SyncFailures.classify(e)
            if (classified == null) {
                progressState.value = SyncProgress.Idle
                return SyncOutcome.Stop(null)
            }
            val status = classified.failure.status
            dao.upsertSyncState(
                SyncStateEntity(SOURCE, status.name, now.toEpochMilli(), previous?.lastSuccessAt, previous?.baselineNights ?: 0),
            )
            log.append(
                SyncLogEntry(
                    at = now.toEpochMilli(),
                    trigger = trigger.name,
                    result = classified.failure.name,
                    httpCode = classified.httpCode,
                    step = step.name,
                    durationMs = clock.millis() - startedMs,
                    detail = classified.detail,
                ),
            )
            progressState.value = SyncProgress.Failed(clock.millis(), classified.failure, classified.httpCode, trigger)
            return if (classified.failure.retryable) SyncOutcome.Retry(status) else SyncOutcome.Stop(status)
        }

        val at = now.toEpochMilli()
        val user = HotDatabase.LOCAL_USER
        val today = now.atZone(clock.zone).toLocalDate()
        val fromDay = now.minus(WINDOW).atZone(clock.zone).toLocalDate()
        val sessions = routine!!.sessions.filter { it.dayKey >= fromDay && it.dayKey <= today }
        val anchors = Anchors.derive(routine!!.nights, sessions, today)
        dao.saveSyncWithRoutine(
            userId = user,
            daily = result.daily.map {
                MetricDailyEntity(user, it.dayKey.toString(), it.metric.id, it.metric.version, it.value, it.z, it.level, it.coverage, it.ewma7, at)
            },
            baselines = result.baselines.map {
                BaselineSnapshotEntity(user, it.dayKey.toString(), it.input.id, it.median28, it.mad28, it.n, at)
            },
            state = SyncStateEntity(SOURCE, SyncStatus.OK.name, at, at, result.baselineNights),
            sessionsFromDay = fromDay.toString(),
            sessions = sessions.map { RoutineStore.toRow(user, it, at) },
            anchors = RoutineStore.toRows(user, anchors, at),
        )
        val dayCount = result.daily.map { it.dayKey }.distinct().size
        val (nights, workouts, glance) = counts!!
        glanceState.value = glance
        log.append(
            SyncLogEntry(
                at = at,
                trigger = trigger.name,
                result = SyncLogEntry.OK,
                durationMs = clock.millis() - startedMs,
                days = dayCount,
                nights = nights,
                workouts = workouts,
            ),
        )
        progressState.value = SyncProgress.Succeeded(clock.millis(), SyncCounts(dayCount, nights, workouts, result.baselineNights), trigger)
        return SyncOutcome.Done(days = dayCount, baselineNights = result.baselineNights)
    }

    /**
     * The phone has no network right now: says so at once (status, log, progress) — the
     * background retry waits for the network.
     */
    suspend fun markOffline(trigger: SyncTrigger = SyncTrigger.MANUAL) {
        val previous = dao.syncState(SOURCE)
        val at = clock.millis()
        dao.upsertSyncState(
            SyncStateEntity(SOURCE, SyncStatus.OFFLINE.name, at, previous?.lastSuccessAt, previous?.baselineNights ?: 0),
        )
        log.append(SyncLogEntry(at = at, trigger = trigger.name, result = SyncFailure.OFFLINE.name, detail = "no network"))
        progressState.value = SyncProgress.Failed(at, SyncFailure.OFFLINE, null, trigger)
    }

    /** «Отключить»: forget the in-memory glance and the live state. The log stays (no personal data). */
    fun reset() {
        glanceState.value = null
        progressState.value = SyncProgress.Idle
    }

    suspend fun clearLog() = log.clear()

    companion object {
        const val SOURCE = "whoop"

        /** 28 days of personal norm (A25 P1) + 7 days so the EWMA and the 7-day debt are complete. */
        val WINDOW: Duration = Duration.ofDays(MetricEngine.BASELINE_DAYS + 7)
    }
}
