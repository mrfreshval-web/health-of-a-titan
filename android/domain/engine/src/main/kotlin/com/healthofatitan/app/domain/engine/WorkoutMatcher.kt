package com.healthofatitan.app.domain.engine

import kotlin.math.abs

/**
 * Status of a plan item (A35 §4 rule 1). All three «done» statuses count as done in EXE: the load
 * is measured by the strap, the execution is what the user says — no control, no counters.
 */
enum class ItemStatus { OPEN, DONE_WHOOP, DONE_SELF, MODIFIED }

data class Verification(val status: ItemStatus, val sessionId: String)

/**
 * A35 §4: a WHOOP workout confirms the training item if it falls into the slot ±3 h (else any
 * session of the day). Thresholds are [Оценка], reviewed on day 30 (Agent 12):
 *  - strength: a strength session ≥ 60 % of the planned time;
 *  - Zone 2: zones 1–2 dominate and ≥ 70 % of the time;
 *  - intervals: the work part in zones 4–5 (class HIGH); otherwise «изменено» (recorded as Zone 2);
 *  - walk / mobility: an easy or Zone 2 session ≥ 70 % of the time.
 * Anything else that WHOOP saw that day is «⇄ изменено» — it still counts as done.
 */
object WorkoutMatcher {

    const val SLOT_WINDOW_MIN = 180
    const val MIN_SESSION_MIN = 10

    fun verify(training: TrainingChoice, sessions: List<Session>): Verification? {
        val day = sessions.filter { it.minutes >= MIN_SESSION_MIN }
        if (day.isEmpty() || training.type == TrainingType.REST) return null
        val slot = training.startMin
        val inSlot = if (slot == null) emptyList() else day.filter { abs(it.startMin - slot) <= SLOT_WINDOW_MIN }
        val pool = inSlot.ifEmpty { day }
        val preferred = pool.filter { matchesKind(training.type, it) }
        val best = preferred.ifEmpty { pool }.maxBy { it.minutes }
        val confirmed = when (training.type) {
            TrainingType.STRENGTH -> best.kind == SessionKind.STRENGTH && best.minutes >= 0.6 * training.minutes
            TrainingType.ZONE2 -> best.kind == SessionKind.CARDIO && best.intensity == Intensity.ZONE2 && best.minutes >= 0.7 * training.minutes
            TrainingType.INTERVALS -> best.kind == SessionKind.CARDIO && best.intensity == Intensity.HIGH
            TrainingType.WALK, TrainingType.MOBILITY ->
                (best.intensity == Intensity.EASY || best.intensity == Intensity.ZONE2) && best.minutes >= 0.7 * training.minutes
            TrainingType.REST -> false
        }
        return Verification(if (confirmed) ItemStatus.DONE_WHOOP else ItemStatus.MODIFIED, best.id)
    }

    private fun matchesKind(type: TrainingType, s: Session): Boolean = when (type) {
        TrainingType.STRENGTH -> s.kind == SessionKind.STRENGTH
        else -> s.kind == SessionKind.CARDIO
    }

    /**
     * Merges a stored status with what WHOOP says now. Data upgrades «отмечено тобой» to «WHOOP» or
     * «изменено»; without data the user's own mark stays.
     */
    fun merge(stored: ItemStatus, fromData: Verification?): ItemStatus = fromData?.status ?: stored
}
