package com.healthofatitan.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * `MetricDaily` (A14 §3.2, class S3): one HoT-derived value per day and formula version
 * (`metricId@ver`, D-023). Raw WHOOP values are never stored (D-009, NoRawWhoopColumnsTest).
 *
 * @property dayKey local date of waking up, ISO `yyyy-MM-dd` (day = WHOOP cycle, wake → wake)
 * @property z personal z (or the composite in z units for ORS, LRB); null while the norm is learned
 * @property level −2…+2; null when there is no value
 * @property coverage share of the input weight available that day (A25 P5)
 * @property ewma7 7-day EWMA of [value] (A25 P2)
 */
@Entity(
    tableName = "metric_daily",
    primaryKeys = ["user_id", "day_key", "metric_id", "metric_version"],
)
data class MetricDailyEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "metric_id") val metricId: String,
    @ColumnInfo(name = "metric_version") val metricVersion: Int,
    val value: Double?,
    val z: Double?,
    val level: Int?,
    val coverage: Double,
    val ewma7: Double?,
    @ColumnInfo(name = "computed_at") val computedAt: Long,
)

/**
 * `BaselineSnapshot` (A14 §3.2, S3): the personal norm of one input on one day — 28-day median,
 * MAD and count. An aggregate in physiological units (R-11 grey zone, documented in A14).
 */
@Entity(
    tableName = "baseline_snapshot",
    primaryKeys = ["user_id", "day_key", "input_id"],
)
data class BaselineSnapshotEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "input_id") val inputId: String,
    val median28: Double?,
    val mad28: Double?,
    val n: Int,
    @ColumnInfo(name = "computed_at") val computedAt: Long,
)

/**
 * `SyncState` (A14 §3.2, S1): technical sync status of a source, no health values.
 *
 * @property status one of OK, OFFLINE, UNAVAILABLE, REAUTH, NOT_ALLOWED, RATE_LIMITED, ERROR
 * @property baselineNights nights in the 28-day personal norm (A09 `data.baseline_n`)
 */
@Entity(tableName = "sync_state", primaryKeys = ["source"])
data class SyncStateEntity(
    val source: String,
    val status: String,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long?,
    @ColumnInfo(name = "last_success_at") val lastSuccessAt: Long?,
    @ColumnInfo(name = "baseline_nights") val baselineNights: Int,
)
