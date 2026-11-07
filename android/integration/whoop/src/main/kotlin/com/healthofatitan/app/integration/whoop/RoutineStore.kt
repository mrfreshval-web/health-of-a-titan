package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.core.database.entity.RoutineAnchorEntity
import com.healthofatitan.app.core.database.entity.TrainingSessionEntity
import com.healthofatitan.app.domain.engine.Intensity
import com.healthofatitan.app.domain.engine.RoutineAnchors
import com.healthofatitan.app.domain.engine.Session
import com.healthofatitan.app.domain.engine.SessionKind
import java.time.LocalDate

/** [RoutineAnchors] and [Session] ⇄ database rows (A09 §7a anchors with their source, D-027). */
internal object RoutineStore {

    const val SOURCE_WHOOP = "whoop"

    private const val WAKE_WEEKDAY = "wake_weekday"
    private const val WAKE_WEEKEND = "wake_weekend"
    private const val ONSET_WEEKDAY = "onset_weekday"
    private const val ONSET_WEEKEND = "onset_weekend"
    private const val SLEEP_NEED = "sleep_need"
    private const val TRAINING_WEEKDAY = "training_start_weekday"
    private const val TRAINING_WEEKEND = "training_start_weekend"
    private const val TRAINING_MINUTES = "training_minutes"
    private const val WORKOUTS_28 = "workouts_28"
    private const val NIGHTS_28 = "nights_28"
    private const val HOME_TZ = "home_tz_offset"
    private const val LAST_WAKE = "last_wake"
    private const val LAST_TZ = "last_tz_offset"
    private const val LAST_TZ_RUN = "last_tz_run_nights"

    fun toRows(user: String, a: RoutineAnchors, at: Long): List<RoutineAnchorEntity> {
        fun row(id: String, value: Int?, day: LocalDate? = null) = RoutineAnchorEntity(user, id, value, day?.toString(), SOURCE_WHOOP, at)
        return listOf(
            row(WAKE_WEEKDAY, a.wakeWeekdayMin),
            row(WAKE_WEEKEND, a.wakeWeekendMin),
            row(ONSET_WEEKDAY, a.onsetWeekdayMin),
            row(ONSET_WEEKEND, a.onsetWeekendMin),
            row(SLEEP_NEED, a.sleepNeedMin),
            row(TRAINING_WEEKDAY, a.trainingStartWeekdayMin),
            row(TRAINING_WEEKEND, a.trainingStartWeekendMin),
            row(TRAINING_MINUTES, a.trainingMinutes),
            row(WORKOUTS_28, a.workouts28),
            row(NIGHTS_28, a.nights28),
            row(HOME_TZ, a.homeTzOffsetMin),
            row(LAST_WAKE, a.lastWakeMin, a.lastWakeDay),
            row(LAST_TZ, a.lastTzOffsetMin),
            row(LAST_TZ_RUN, a.lastTzRunNights),
        )
    }

    fun fromRows(rows: List<RoutineAnchorEntity>): RoutineAnchors {
        val byId = rows.associateBy { it.anchorId }
        fun v(id: String) = byId[id]?.valueMin
        return RoutineAnchors(
            wakeWeekdayMin = v(WAKE_WEEKDAY),
            wakeWeekendMin = v(WAKE_WEEKEND),
            onsetWeekdayMin = v(ONSET_WEEKDAY),
            onsetWeekendMin = v(ONSET_WEEKEND),
            sleepNeedMin = v(SLEEP_NEED),
            trainingStartWeekdayMin = v(TRAINING_WEEKDAY),
            trainingStartWeekendMin = v(TRAINING_WEEKEND),
            trainingMinutes = v(TRAINING_MINUTES),
            workouts28 = v(WORKOUTS_28) ?: 0,
            nights28 = v(NIGHTS_28) ?: 0,
            homeTzOffsetMin = v(HOME_TZ),
            lastWakeDay = byId[LAST_WAKE]?.dayKey?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            lastWakeMin = v(LAST_WAKE),
            lastTzOffsetMin = v(LAST_TZ),
            lastTzRunNights = v(LAST_TZ_RUN) ?: 0,
        )
    }

    fun toRow(user: String, s: Session, at: Long) =
        TrainingSessionEntity(user, s.id, s.dayKey.toString(), s.startMin, s.minutes, s.kind.name, s.intensity.name, at)

    fun fromRow(r: TrainingSessionEntity): Session? {
        val day = runCatching { LocalDate.parse(r.dayKey) }.getOrNull() ?: return null
        val kind = SessionKind.entries.firstOrNull { it.name == r.kind } ?: return null
        val intensity = Intensity.entries.firstOrNull { it.name == r.intensity } ?: Intensity.MIXED
        return Session(r.sourceId, day, r.startMin, r.durationMin, kind, intensity)
    }
}
