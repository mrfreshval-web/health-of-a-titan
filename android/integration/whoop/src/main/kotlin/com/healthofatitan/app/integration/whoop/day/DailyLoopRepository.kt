package com.healthofatitan.app.integration.whoop.day

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.DailyLoopDao
import com.healthofatitan.app.core.database.entity.ReturnLogEntity
import com.healthofatitan.app.core.database.entity.SafetyEventEntity
import com.healthofatitan.app.domain.engine.CrisisAssessment
import com.healthofatitan.app.domain.engine.CrisisLevel
import com.healthofatitan.app.domain.engine.CrisisSource
import com.healthofatitan.app.domain.engine.DayTexts
import com.healthofatitan.app.domain.engine.ReturnPolicy
import com.healthofatitan.app.domain.engine.ReturnStage
import com.healthofatitan.app.integration.whoop.safety.SafetyBus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The warm local nudge after a pause (A18 И4.4 ②): implemented in :app, which owns the icon, the
 * channel and the activity. The only action is «Минимальная версия» (opens «Сегодня»).
 */
fun interface ReturnNotifier {
    /** @return true if the notification was posted (false: notifications are off). */
    fun showReturn(title: String, text: String): Boolean
}

/**
 * The daily loop without shame (A18 И4.4) and the crisis log (A07 §4.7):
 * - every app open is a date in `return_log`; silence = whole days without one ([ReturnPolicy]);
 * - silence ≥ 2 days → the day plan is built with RET-01 and one warm nudge is sent per pause;
 * - silence ≥ 7 days → «С возвращением» once, on the first open;
 * - a crisis level from CrisisGuard is logged as date + level + reason + source, **never text**;
 *   it also feeds [CrisisSignals] (no haiku on «Рассвет» for 72 h after level ≥ 2) and the event
 *   journal of the safety bus [SafetyBus] (A60 §3), which «Сегодня», «Неделя» and «Путь» read.
 */
class DailyLoopRepository(
    private val dao: DailyLoopDao,
    private val clock: Clock,
    private val bus: SafetyBus? = null,
) : CrisisSignals {

    private val user = HotDatabase.LOCAL_USER
    private val mutex = Mutex()

    @Volatile
    private var lastSevereAt: Long = 0L

    private fun today(): LocalDate = LocalDate.now(clock)

    /** The app is open today (first open of a day wins). */
    suspend fun recordOpen() {
        dao.log(ReturnLogEntity(user, today().toString(), ReturnLogEntity.KIND_OPEN, clock.millis()))
    }

    /** Whole days without HoT before [day] — the input of RET-01. */
    suspend fun silenceDays(day: LocalDate = today()): Int =
        ReturnPolicy.silenceDays(dao.lastBefore(user, ReturnLogEntity.KIND_OPEN, day.toString())?.let(LocalDate::parse), day)

    /** «С возвращением» is due today: silence ≥ 7 days and not shown yet today. */
    fun welcomeDue(day: LocalDate = today()): Flow<Boolean> =
        dao.observeCount(user, ReturnLogEntity.KIND_WELCOME, day.toString()).map { shown ->
            shown == 0 && ReturnPolicy.stage(silenceDays(day)) == ReturnStage.WELCOME_BACK
        }

    suspend fun welcomeShown() {
        dao.log(ReturnLogEntity(user, today().toString(), ReturnLogEntity.KIND_WELCOME, clock.millis()))
    }

    /**
     * The warm nudge (A11 §2 «Возвращение»): once per pause, 10:00–20:00. Returns true if it was
     * posted. Nothing is posted while the person keeps opening HoT.
     */
    suspend fun nudgeIfDue(notifier: ReturnNotifier): Boolean = mutex.withLock {
        val lastOpen = dao.last(user, ReturnLogEntity.KIND_OPEN)?.let(LocalDate::parse)
        val lastNudge = dao.last(user, ReturnLogEntity.KIND_NUDGE)?.let(LocalDate::parse)
        if (!ReturnPolicy.nudgeDue(lastOpen, lastNudge, LocalDateTime.now(clock))) return@withLock false
        val posted = notifier.showReturn(DayTexts.RETURN_HEADLINE, DayTexts.RETURN_SUMMARY)
        if (posted) dao.log(ReturnLogEntity(user, today().toString(), ReturnLogEntity.KIND_NUDGE, clock.millis()))
        posted
    }

    /** A07 §4.7: date, level, reason class and source — the words are never stored. */
    suspend fun logSafety(assessment: CrisisAssessment, source: CrisisSource) {
        if (assessment.level == CrisisLevel.NONE) return
        val at = clock.millis()
        dao.insertSafety(
            SafetyEventEntity(
                userId = user,
                dayKey = today().toString(),
                at = at,
                level = assessment.level.value,
                reason = assessment.reason?.name,
                source = source.name,
            ),
        )
        if (assessment.level.autoShowHelp) lastSevereAt = at
        bus?.crisis(assessment.level.value, source.name, at)
    }

    /** Loads the last severe event once per process (called at app start). */
    suspend fun warmUp() {
        dao.lastAt(user, CrisisLevel.PASSIVE.value)?.let { at -> if (at > lastSevereAt) lastSevereAt = at }
    }

    /** «Рассвет» stays without a haiku for 72 h after level ≥ 2 (A37 §4, STYLE.md §6.3). */
    override fun active(): Boolean = lastSevereAt > 0 && clock.millis() - lastSevereAt < CRISIS_WINDOW.toMillis()

    companion object {
        val CRISIS_WINDOW: Duration = Duration.ofHours(72)
    }
}

/**
 * Checks a few times a day whether a pause has begun and the warm nudge is due (A18 И4.4). No
 * network, no data read: only the dates of `return_log`. Dependencies through a Hilt entry point.
 */
class ReturnWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun dailyLoop(): DailyLoopRepository

        fun returnNotifier(): ReturnNotifier
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        runCatching { deps.dailyLoop().nudgeIfDue(deps.returnNotifier()) }
        return Result.success()
    }

    companion object {
        private const val WORK = "return-watch"

        /** Every 3 hours; the policy itself keeps the nudge inside 10:00–20:00 and once per pause. */
        fun ensure(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReturnWatchWorker>(Duration.ofHours(3)).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
