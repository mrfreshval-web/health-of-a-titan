package com.healthofatitan.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The daily loop's own trace (A18 И4.4): on which days HoT was opened, when the warm nudge after a
 * pause was sent and when «С возвращением» was shown. Dates only — no health values, no counts of
 * missed days are ever derived for display.
 *
 * @property kind [KIND_OPEN], [KIND_NUDGE] or [KIND_WELCOME]
 * @property at epoch ms of the first event of that kind on that day
 */
@Entity(tableName = "return_log", primaryKeys = ["user_id", "day_key", "kind"])
data class ReturnLogEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    val kind: String,
    val at: Long,
) {
    companion object {
        const val KIND_OPEN = "OPEN"
        const val KIND_NUDGE = "NUDGE"
        const val KIND_WELCOME = "WELCOME"
    }
}

/**
 * `SafetyEvent` (A07 §4.7, A18 И4.3 ④): the crisis protocol fired. Only the date, the level, the
 * reason class and where it happened — **never the text** the person wrote or said. Local and
 * encrypted (SQLCipher), never sent to any backend or analytics (A25 §5).
 *
 * @property level CrisisGuard level 1–3 (A07 §4.3)
 * @property reason the trigger class (DIRECT, INDIRECT, MEANS, …), not the words
 * @property source EVENING, MENTOR, VOICE, SOS, JOURNAL
 */
@Entity(tableName = "safety_event", indices = [Index(value = ["user_id", "at"])])
data class SafetyEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    val at: Long,
    val level: Int,
    val reason: String?,
    val source: String,
)
