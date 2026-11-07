package com.healthofatitan.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * A workout done in the HoT player (A40 §7, stream B). Separate from `training_session`, which
 * holds what WHOOP saw. Only what the user did and said: template, sets, RPE, «Мне плохо».
 * Nothing from WHOOP and no heart rate is stored here.
 *
 * @property templateRef `templateId@version` (ADR-05), so the workout can be reproduced
 * @property mode the day regime the session was built for (BOOST / STEADY / GENTLE)
 * @property activeS seconds from start to finish without pauses
 * @property rpeMedian median of the RPE answers, null if none was given
 * @property feltBad «Мне плохо» was pressed during the workout
 */
@Entity(
    tableName = "training_workout",
    primaryKeys = ["user_id", "workout_id"],
    indices = [Index(value = ["user_id", "day_key"])],
)
data class TrainingWorkoutEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "template_ref") val templateRef: String,
    val mode: String,
    val lighter: Boolean,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
    @ColumnInfo(name = "active_s") val activeS: Long,
    @ColumnInfo(name = "sets_done") val setsDone: Int,
    @ColumnInfo(name = "sets_skipped") val setsSkipped: Int,
    @ColumnInfo(name = "sets_planned") val setsPlanned: Int,
    @ColumnInfo(name = "rpe_median") val rpeMedian: Int?,
    @ColumnInfo(name = "felt_bad") val feltBad: Boolean,
    @ColumnInfo(name = "stretch_done") val stretchDone: Boolean,
)

/**
 * One set of a [TrainingWorkoutEntity] as it was done (A40 §7.3): the confirmed repetitions or
 * seconds, skipped or not, and the RPE answered after it (the last set of an exercise or round).
 */
@Entity(tableName = "training_set", primaryKeys = ["user_id", "workout_id", "step_index"])
data class TrainingSetEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "step_index") val stepIndex: Int,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "block_type") val blockType: String,
    @ColumnInfo(name = "set_no") val setNo: Int,
    val side: String?,
    val reps: String?,
    val seconds: Int,
    val skipped: Boolean,
    val rpe: Int?,
)
