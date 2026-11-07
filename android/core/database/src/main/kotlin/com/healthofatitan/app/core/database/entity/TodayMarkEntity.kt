package com.healthofatitan.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * «Сегодня» v3 (A46 v0.3, D-042): what the person marks by hand on a day — glasses of water, meals
 * of the plan, a task of the day done or sent back to its week. One row per (day, kind, ref); only
 * on the device, no WHOOP values (NoRawWhoopColumnsTest).
 *
 * @property kind [KIND_WATER] (ref empty, [count] = glasses), [KIND_MEAL] (ref = meal slot),
 *   [KIND_TASK_DONE] or [KIND_TASK_TO_WEEK] (ref = task id, [goal] = the week goal it returned to)
 */
@Entity(tableName = "today_mark", primaryKeys = ["user_id", "day_key", "kind", "ref"])
data class TodayMarkEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    val kind: String,
    val ref: String,
    val count: Int,
    val goal: String? = null,
    val at: Long,
) {
    companion object {
        const val KIND_WATER = "water"
        const val KIND_MEAL = "meal"
        const val KIND_TASK_DONE = "task_done"
        const val KIND_TASK_TO_WEEK = "task_to_week"

        /** «Эксперимент недели» (A53 §3.2): the action was done that day (ref = experiment id). */
        const val KIND_EXPERIMENT = "experiment"
    }
}
