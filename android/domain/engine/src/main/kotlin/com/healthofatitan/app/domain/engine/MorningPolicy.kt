package com.healthofatitan.app.domain.engine

import java.time.Duration
import java.time.LocalDateTime

/**
 * When the phone shows the morning push (A11 §2, A34) — the interim local path until the server
 * leads the morning (D-025). One push per day, not before 06:00, not after 12:00 (later the user
 * is already in the app); while the night is not scored yet, check again every 15 minutes.
 */
object MorningPolicy {

    const val WATCH_FROM_MIN = 4 * 60 + 30
    const val QUIET_UNTIL_MIN = 6 * 60
    const val LATEST_MIN = 12 * 60
    const val RETRY_MIN = 15L

    sealed interface Step {
        data object NotifyNow : Step

        /** Check again after [delay]. */
        data class Wait(val delay: Duration) : Step
    }

    /** Inside the morning window a sync is worth it (the night may just have been scored). */
    fun wantsData(now: LocalDateTime): Boolean = (now.hour * 60 + now.minute) in WATCH_FROM_MIN until LATEST_MIN

    fun next(now: LocalDateTime, planReadyToday: Boolean, notifiedToday: Boolean): Step {
        val m = now.hour * 60 + now.minute
        val tomorrow = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(WATCH_FROM_MIN.toLong()))
        fun inMinutes(target: Int) = Duration.ofMinutes((target - m).toLong().coerceAtLeast(1))
        return when {
            notifiedToday -> Step.Wait(tomorrow)
            m < WATCH_FROM_MIN -> Step.Wait(inMinutes(WATCH_FROM_MIN))
            planReadyToday && m < QUIET_UNTIL_MIN -> Step.Wait(inMinutes(QUIET_UNTIL_MIN))
            planReadyToday && m < LATEST_MIN -> Step.NotifyNow
            m < LATEST_MIN -> Step.Wait(Duration.ofMinutes(RETRY_MIN))
            else -> Step.Wait(tomorrow)
        }
    }
}
