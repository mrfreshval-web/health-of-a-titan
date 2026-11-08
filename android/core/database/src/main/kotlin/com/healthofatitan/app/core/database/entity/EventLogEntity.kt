package com.healthofatitan.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The event journal (A58 §8 «журнал и проекторы», Room 15): append-only facts from which the
 * projectors build state. This slice writes only the safety events the bus `SafetyState` needs
 * (A60 §3); the kinds are closed codes like `safety.crisis`, the payload is a code or a number —
 * never words (A07 §4.7). Only in this SQLCipher database.
 *
 * @property kind `<module>.<event>`, e.g. `safety.ed_care.enter`
 * @property payload a code or a number (crisis level, reason of entry), never free text
 * @property source who wrote it (`EVENING`, `MENTOR`, `profile`, `person`, `migration`…)
 */
@Entity(tableName = "event_log", indices = [Index(value = ["user_id", "kind", "at"])])
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    val kind: String,
    val payload: String?,
    val source: String?,
    val at: Long,
    @ColumnInfo(name = "day_key") val dayKey: String,
)
