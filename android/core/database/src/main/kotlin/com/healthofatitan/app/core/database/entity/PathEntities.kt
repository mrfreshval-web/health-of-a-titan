package com.healthofatitan.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A goal of the 12-week cycle (A42 §3.2, D-033): one ACTIVE at a time; a changed goal stays as
 * REPLACED so its weeks are kept (A42 §3.2 п. 4). Process only: «N days of 7» in one sphere —
 * no weight, no measurements, no raw WHOOP values.
 *
 * @property sphere `sleep`, `body`, … (domain `Sphere.id`)
 * @property baseline days of 7 «now» when the goal was taken; null = default plan, no data yet
 * @property stage1 / stage2 / stage3 the week goal of each 4-week stage, days of 7
 * @property status ACTIVE / REPLACED / DONE
 */
@Entity(
    tableName = "path_goal",
    indices = [Index(value = ["user_id", "status"])],
)
data class PathGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    val sphere: String,
    @ColumnInfo(name = "start_day") val startDay: String,
    val baseline: Int?,
    val stage1: Int,
    val stage2: Int,
    val stage3: Int,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "ended_day") val endedDay: String?,
)

/**
 * The user's tap «шаг дня сделан» for a goal (A42 §3.3). [version] is FULL / NORMAL / MINIMAL:
 * in «Бережно» the minimal version counts as a step all the same.
 */
@Entity(
    tableName = "path_step",
    primaryKeys = ["user_id", "goal_id", "day_key"],
)
data class PathStepEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "goal_id") val goalId: Long,
    @ColumnInfo(name = "day_key") val dayKey: String,
    val version: String,
    @ColumnInfo(name = "done_at") val doneAt: Long,
)
