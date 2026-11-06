package com.healthofatitan.app.integration.whoop

/** The four looks of a sync indicator (the UI maps them onto its sync mark). */
enum class SyncLight { IDLE, RUNNING, SUCCESS, ERROR }

/**
 * What a sync indicator shows at a moment: pure, so the mapping is unit-tested.
 *
 * @param key one sync attempt (its start or end time): the finale plays once per key
 * @param celebrate a fresh success: play the finale
 * @param haptic the user asked for this sync (button, pull, login): tick on success / failure
 */
data class SyncIndicator(
    val light: SyncLight,
    val progress: Float? = null,
    val key: Long = 0L,
    val celebrate: Boolean = false,
    val haptic: Boolean = false,
)

object SyncIndicators {
    /** How long a success stays «fresh» (finale, «Готово · …» line) before the mark rests. */
    const val FRESH_MS = 6_000L

    /**
     * @param holdErrorMs how long an error stays visible; null = until the next attempt (screens),
     *   a number = it fades back to idle (the top bar must not alarm forever)
     */
    fun of(progress: SyncProgress, now: Long, holdErrorMs: Long? = null): SyncIndicator = when (progress) {
        SyncProgress.Idle -> SyncIndicator(SyncLight.IDLE)
        is SyncProgress.Running -> SyncIndicator(SyncLight.RUNNING, progress.step.fraction, progress.startedAt)
        is SyncProgress.Succeeded ->
            if (now - progress.at < FRESH_MS) {
                SyncIndicator(SyncLight.SUCCESS, 1f, progress.at, celebrate = true, haptic = progress.trigger.byUser)
            } else {
                SyncIndicator(SyncLight.IDLE, key = progress.at)
            }
        is SyncProgress.Failed ->
            if (holdErrorMs == null || now - progress.at < holdErrorMs) {
                SyncIndicator(SyncLight.ERROR, key = progress.at, haptic = progress.trigger.byUser)
            } else {
                SyncIndicator(SyncLight.IDLE, key = progress.at)
            }
    }

    /** When [of] will change by itself (a success going stale, an error fading), or null. */
    fun nextChangeAt(progress: SyncProgress, now: Long, holdErrorMs: Long? = null): Long? = when (progress) {
        is SyncProgress.Succeeded -> (progress.at + FRESH_MS).takeIf { it > now }
        is SyncProgress.Failed -> holdErrorMs?.let { progress.at + it }?.takeIf { it > now }
        else -> null
    }

    private val SyncTrigger.byUser: Boolean get() = this == SyncTrigger.MANUAL || this == SyncTrigger.LOGIN
}
