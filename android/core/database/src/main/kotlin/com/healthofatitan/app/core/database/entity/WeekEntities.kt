package com.healthofatitan.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * «Неделя» (A52, D-044, D-045), Room 11. Only HoT-derived values and what the person typed; never
 * a raw WHOOP value (NoRawWhoopColumnsTest). All of it stays on the device.
 */

/**
 * The plan of a calendar week (Monday [weekStart]): its status, the ritual «Итог и сборка» that
 * closes it and the one Sunday push.
 *
 * @property status `PROPOSED | APPROVED | AUTO` (W-06)
 * @property pride «Чем гордишься на этой неделе?» — the person's words (A52 §2.6, Agent 8)
 * @property carry «Что тянется?» — the person's words
 */
@Entity(tableName = "week_plan", primaryKeys = ["user_id", "week_start"])
data class WeekPlanEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "week_start") val weekStart: String,
    val status: String,
    @ColumnInfo(name = "assembled_at") val assembledAt: Long,
    @ColumnInfo(name = "ritual_done_at") val ritualDoneAt: Long? = null,
    @ColumnInfo(name = "pushed_at") val pushedAt: Long? = null,
    val pride: String? = null,
    val carry: String? = null,
)

/** One goal of a week (A52 §2.2, §4 `WeekGoal`): its target as set; the progress is counted live. */
@Entity(tableName = "week_goal", primaryKeys = ["user_id", "week_start", "goal_id"])
data class WeekGoalEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "week_start") val weekStart: String,
    @ColumnInfo(name = "goal_id") val goalId: String,
    val sphere: String,
    val kind: String,
    val measure: String,
    val title: String,
    val target: Int,
    val unit: String,
    val source: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

/**
 * The score of a day as the week counts it at 04:00 the next day (A52 §4 `DayScore`). [fills] are
 * the shares of the five supports' norms (comma-separated, HoT-derived), for the columns of the
 * Titan; [mode] is `RAZGON | ROVNO | BEREZHNO`.
 */
@Entity(tableName = "week_day_score", primaryKeys = ["user_id", "day_key"])
data class WeekDayScoreEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "in_support") val inSupport: Boolean,
    @ColumnInfo(name = "supports_filled") val supportsFilled: Int,
    @ColumnInfo(name = "tasks_done") val tasksDone: Int,
    val mode: String?,
    val sick: Boolean,
    val fills: String,
    @ColumnInfo(name = "computed_at") val computedAt: Long,
)

/**
 * One frame of the year ribbon (A52 §2.7): the look of the Titan of a week as the rules made it,
 * and the fitness age of that week when it was estimated (D-045, behind its flag).
 */
@Entity(tableName = "week_frame", primaryKeys = ["user_id", "week_start"])
data class WeekFrameEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "week_start") val weekStart: String,
    val pose: String,
    val mode: String,
    @ColumnInfo(name = "light_level") val lightLevel: Float,
    val columns: String,
    val sky: String,
    val detail: String?,
    @ColumnInfo(name = "days_in_support") val daysInSupport: Int,
    val seed: Long,
    @ColumnInfo(name = "revealed_at") val revealedAt: Long?,
    @ColumnInfo(name = "fitness_age") val fitnessAge: Double?,
    @ColumnInfo(name = "computed_at") val computedAt: Long,
)

/**
 * What the person enters for the fitness age (D-045): the waist every 4 weeks («Замеры» of the
 * path, A52 §2.8), the age and the sex. A temporary home until the profile has these fields.
 *
 * @property kind [KIND_WAIST] (value cm), [KIND_AGE] (value years), [KIND_SEX] (text `male | female`)
 */
@Entity(tableName = "body_input", primaryKeys = ["user_id", "kind", "day_key"])
data class BodyInputEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    val kind: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    val value: Double?,
    val text: String?,
    val at: Long,
) {
    companion object {
        const val KIND_WAIST = "waist_cm"
        const val KIND_AGE = "age_years"
        const val KIND_SEX = "sex"
    }
}
