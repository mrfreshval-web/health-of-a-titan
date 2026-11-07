package com.healthofatitan.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A practical goal of «Путь» v1 (A56 §1 `PracticalGoal`, D-048). Since v11 (A56 §5) the status is
 * one of DRAFT · ACTIVE · PAUSED · HELD · REVIEW · TAKEN · RELEASED · QUEUED; the v10 goals of the
 * 12-week path (A42) were migrated: REPLACED → RELEASED, DONE → TAKEN, their sphere kept in
 * [legacySphere]. Only on the device (P-21): no network code reads this table.
 *
 * @property criteria the criteria «что будет правдой к сроку», `GoalCodec.encodeCriteria`
 * @property stage the stage of 12 weeks the goal is in, from 1
 * @property pausedDays closed pauses of the current stage (the milestones move by them, P-16)
 */
@Entity(
    tableName = "path_goal",
    indices = [Index(value = ["user_id", "status"])],
)
data class PathGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    val title: String,
    val why: String?,
    @ColumnInfo(name = "be_name") val beName: String?,
    val kind: String,
    val template: String?,
    @ColumnInfo(name = "start_day") val startDay: String,
    val deadline: String,
    val stage: Int,
    val mode: String,
    val status: String,
    val criteria: String,
    val obstacle: String?,
    @ColumnInfo(name = "if_then") val ifThen: String?,
    val version: Int,
    @ColumnInfo(name = "paused_days") val pausedDays: Int,
    @ColumnInfo(name = "pause_from") val pauseFrom: String?,
    @ColumnInfo(name = "pause_reason") val pauseReason: String?,
    @ColumnInfo(name = "pause_until") val pauseUntil: String?,
    @ColumnInfo(name = "taken_at") val takenAt: Long,
    @ColumnInfo(name = "release_to_queue") val releaseToQueue: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "ended_day") val endedDay: String?,
    @ColumnInfo(name = "legacy_sphere") val legacySphere: String?,
)

/** A component in a goal (A56 §1 `GoalComponent`): role, marks, the safe version, the lever. */
@Entity(
    tableName = "path_goal_component",
    primaryKeys = ["goal_id", "component"],
)
data class PathGoalComponentEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "goal_id") val goalId: Long,
    val component: String,
    val role: String,
    @ColumnInfo(name = "target_mark") val targetMark: Int?,
    @ColumnInfo(name = "start_mark") val startMark: Int?,
    @ColumnInfo(name = "safe_version") val safeVersion: String?,
    @ColumnInfo(name = "is_lever") val isLever: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

/** A version of a goal (A56 §1 `GoalVersion`, P-11): the snapshot (`GoalCodec`), who and why. */
@Entity(
    tableName = "path_goal_version",
    indices = [Index(value = ["goal_id", "v"], unique = true)],
)
data class PathGoalVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "goal_id") val goalId: Long,
    val v: Int,
    val snapshot: String,
    @ColumnInfo(name = "change_type") val changeType: String,
    val initiator: String,
    val reason: String?,
    val at: Long,
)

/**
 * A card «было → станет» (A56 §1 `ChangeProposal`): kept until applied, declined or expired, so a
 * card of the mentor or of HoT survives the screen. [state] OPEN · APPLIED · DECLINED · EXPIRED.
 */
@Entity(
    tableName = "path_change_proposal",
    indices = [Index(value = ["goal_id", "state"])],
)
data class PathChangeProposalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "goal_id") val goalId: Long,
    @ColumnInfo(name = "from_version") val fromVersion: Int,
    @ColumnInfo(name = "change_type") val changeType: String,
    val before: String,
    val after: String,
    val effects: String,
    val initiator: String,
    val soft: Boolean,
    @ColumnInfo(name = "suggest_talk") val suggestTalk: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    val state: String,
)

/**
 * The Titan of the horizon of a goal (A57, A56 §1 `HorizonTitan`). The step is never stored as
 * truth — it is the calendar; [shownStep] is only the last step the screen animated. [transferred]
 * are the fires that moved to the person, comma-separated component ids; they never return (P-14).
 */
@Entity(tableName = "path_horizon")
data class PathHorizonEntity(
    @PrimaryKey @ColumnInfo(name = "goal_id") val goalId: Long,
    @ColumnInfo(name = "user_id") val userId: String,
    val transferred: String,
    @ColumnInfo(name = "shown_step") val shownStep: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** «Голос с горизонта» (A57 §4.2): optional, ≤ 300 signs, opens at milestone 8 or the meeting. */
@Entity(tableName = "path_letter")
data class PathLetterEntity(
    @PrimaryKey @ColumnInfo(name = "goal_id") val goalId: Long,
    @ColumnInfo(name = "user_id") val userId: String,
    val text: String,
    @ColumnInfo(name = "open_at") val openAt: String,
    @ColumnInfo(name = "written_on") val writtenOn: String,
    @ColumnInfo(name = "opened_at") val openedAt: Long?,
)

/** A mark taken (A54 «рубеж»): never rolled back (P-14). [source] TEST · SELF · DATA. */
@Entity(
    tableName = "path_mark",
    primaryKeys = ["goal_id", "component", "mark"],
)
data class PathMarkEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "goal_id") val goalId: Long,
    val component: String,
    val mark: Int,
    @ColumnInfo(name = "day_key") val dayKey: String,
    val source: String,
    val at: Long,
)

/**
 * The user's tap «шаг дня сделан» for a goal of the 12-week path (A42 §3.3) — history of the v10
 * goals, kept as it was.
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
