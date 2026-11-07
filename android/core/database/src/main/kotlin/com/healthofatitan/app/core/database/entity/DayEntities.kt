package com.healthofatitan.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * `DayState` (A14 §3.2, S3): the regime of one day as the user saw it (A09 §6: history is never
 * recomputed). No raw WHOOP values: only the regime, flags and fired rules with their texts.
 *
 * @property signals ids of the A09 signals that fired (REG-04 exit thresholds tomorrow), comma-separated
 * @property flags A09 flags (SICK, TRAVEL, LIGHT_WEEK…), comma-separated
 * @property firedRules one rule per line: `ruleId@ver<TAB>why`
 * @property ruleSet manifest version of `content/rules` (A09 §6), e.g. `day@1`
 * @property notifiedAt when the morning push was shown (one per day), null if not yet
 */
@Entity(tableName = "day_plan", primaryKeys = ["user_id", "day_key"])
data class DayPlanEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    val regime: String,
    @ColumnInfo(name = "regime_computed") val regimeComputed: String,
    val points: Int,
    val signals: String,
    val flags: String,
    @ColumnInfo(name = "fired_rules") val firedRules: String,
    @ColumnInfo(name = "rule_set") val ruleSet: String,
    val headline: String,
    val summary: String,
    @ColumnInfo(name = "bedtime_min") val bedtimeMin: Int,
    @ColumnInfo(name = "caffeine_cutoff_min") val caffeineCutoffMin: Int,
    @ColumnInfo(name = "wind_down_min") val windDownMin: Int,
    @ColumnInfo(name = "built_at") val builtAt: Long,
    @ColumnInfo(name = "notified_at") val notifiedAt: Long?,
)

/**
 * `PlanItem` (A14 §3.2, S2): one item of the day plan with its rule `ruleId@ver` and status
 * (A35 §4: OPEN, DONE_WHOOP, DONE_SELF, MODIFIED).
 *
 * @property slotStart minutes from 00:00 of the day (≥ 1440 after midnight), null = any time
 * @property sessionRef the HoT training session that confirmed it (`training_session.source_id`)
 */
@Entity(tableName = "plan_item", primaryKeys = ["user_id", "day_key", "item_id"])
data class PlanItemEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "rule_ref") val ruleRef: String,
    val kind: String,
    @ColumnInfo(name = "slot_start") val slotStart: Int?,
    @ColumnInfo(name = "slot_end") val slotEnd: Int?,
    val title: String,
    val detail: String?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val status: String,
    @ColumnInfo(name = "status_at") val statusAt: Long?,
    @ColumnInfo(name = "session_ref") val sessionRef: String?,
)

/**
 * `TrainingSession` (A14 §3.2, S3) as seen by WHOOP, classified by HoT in memory during the sync
 * (A35 §4). No heart-rate zones, strain or kJ are stored — only the class, start and duration.
 *
 * @property sourceId the WHOOP workout UUID (an identifier, not a health value)
 * @property startMin local start, minutes from 00:00 of [dayKey]
 * @property kind STRENGTH / CARDIO; [intensity] HIGH / ZONE2 / EASY / MIXED
 */
@Entity(tableName = "training_session", primaryKeys = ["user_id", "source_id"])
data class TrainingSessionEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "start_min") val startMin: Int,
    @ColumnInfo(name = "duration_min") val durationMin: Int,
    val kind: String,
    val intensity: String,
    @ColumnInfo(name = "computed_at") val computedAt: Long,
)

/**
 * The routine derived from WHOOP (A09 §7a, D-027): medians over 28 nights, local minutes.
 * Aggregates only, like `baseline_snapshot` (R-11 grey zone, Agent 7).
 *
 * @property anchorId e.g. `wake_weekday`, `onset_weekend`, `sleep_need`, `training_start_weekday`
 * @property dayKey for `last_wake`: the day of that wake-up
 */
@Entity(tableName = "routine_anchor", primaryKeys = ["user_id", "anchor_id"])
data class RoutineAnchorEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "anchor_id") val anchorId: String,
    @ColumnInfo(name = "value_min") val valueMin: Int?,
    @ColumnInfo(name = "day_key") val dayKey: String?,
    val source: String,
    @ColumnInfo(name = "computed_at") val computedAt: Long,
)

/**
 * The evening deck (A25 §2.3, D-027: ≤ 3 taps, only what is not in the data), S4. A skipped card
 * is null, never 0.
 *
 * @property dayKey the HoT day being closed; the next morning reads it
 * @property alcohol В4 servings 0…4 (4 = 4+)
 * @property body FINE / SORE / SICK
 */
@Entity(tableName = "evening_check", primaryKeys = ["user_id", "day_key"])
data class EveningCheckEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    val alcohol: Int?,
    val body: String?,
    @ColumnInfo(name = "answered_at") val answeredAt: Long,
)
