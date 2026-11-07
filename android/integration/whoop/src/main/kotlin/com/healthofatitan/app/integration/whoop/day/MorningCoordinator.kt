package com.healthofatitan.app.integration.whoop.day

import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.MorningPolicy
import com.healthofatitan.app.integration.whoop.SyncProgress
import com.healthofatitan.app.integration.whoop.WhoopSyncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/** Shows the morning notification (implemented in :app, which owns the icon and the activity). */
fun interface DayNotifier {
    /** @return true if the notification was posted (false: notifications are off). */
    fun showMorning(title: String, text: String): Boolean
}

/**
 * The local morning (interim path until the server leads the morning, D-025 / A34): after every
 * successful WHOOP sync the day plan is rebuilt; once the night is scored and it is 06:00–12:00,
 * one push per day shows the regime. Opening «Сегодня» with the plan counts as «seen» — then no
 * push follows. Single-flight, so the background sync, the morning watch and the app never post
 * twice.
 */
class MorningCoordinator(
    private val syncer: WhoopSyncer,
    private val plans: DayPlanRepository,
    private val notifier: DayNotifier,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** Rebuild after each successful sync, wherever it ran (app, WorkManager, morning watch). */
    fun start(scope: CoroutineScope) {
        scope.launch {
            syncer.progress.filterIsInstance<SyncProgress.Succeeded>().collect {
                runCatching { onDataChanged() }
            }
        }
    }

    /** Rebuilds today's plan and posts the morning push if it is due. Returns when to check again. */
    suspend fun onDataChanged(): MorningPolicy.Step = mutex.withLock {
        plans.refresh()
        val today = LocalDate.now(clock)
        // A MANUAL default day (no scored night yet) is not pushed: the push waits for the data.
        val plan = plans.planFor(today)?.takeUnless { DayFlag.MANUAL.name in it.flags.split(',') }
        val step = MorningPolicy.next(LocalDateTime.now(clock), plan != null, plan?.notifiedAt != null)
        if (step is MorningPolicy.Step.NotifyNow && plan != null) {
            notifier.showMorning(plan.headline, plan.summary)
            plans.markNotified(today)
            return@withLock MorningPolicy.next(LocalDateTime.now(clock), planReadyToday = true, notifiedToday = true)
        }
        step
    }

    /** «Сегодня» is open: rebuild (alarm, new workouts) and count today's plan as seen. */
    suspend fun onTodayOpened() = mutex.withLock {
        plans.refresh()
        val today = LocalDate.now(clock)
        val plan = plans.planFor(today)
        if (plan != null && plan.notifiedAt == null) plans.markNotified(today)
    }
}
