package com.healthofatitan.app.domain.path

/**
 * What holds the Path right now: a pause the person set, a goal held by HoT (P-16) or a heavy week.
 * Holds never burn weeks and never put the light out (P-14).
 */
data class SafetyHold(val paused: Boolean = false, val held: Boolean = false, val heavy: Boolean = false) {
    val any: Boolean get() = paused || held || heavy

    companion object {
        val NONE = SafetyHold()
    }
}

/**
 * The seam for the single SafetyState of D-051 (A58 audit: one safety state instead of a pause per
 * module). The Path does not decide on its own whether it is held: it asks this interface. Until
 * the event bus and its projectors arrive, [LOCAL] reads the goal's own status and the regime of
 * the week; the bus will replace it without touching the rules.
 */
fun interface PathSafety {
    fun hold(goal: PracticalGoal?, heavyWeek: Boolean): SafetyHold

    companion object {
        val LOCAL = PathSafety { goal, heavyWeek ->
            SafetyHold(
                paused = goal?.status == GoalStatus.PAUSED,
                held = goal?.status == GoalStatus.HELD,
                heavy = heavyWeek,
            )
        }
    }
}
