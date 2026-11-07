package com.healthofatitan.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A44 §7 `CardResponse`: one answer of a state card (wake, ritual, evening, week). Levels and
 * icon codes only, a skip is `skipped = 1` with no value (never 0). S5 «Только ты» never lands
 * here: it has its own table [CheckinPrivateEntity].
 *
 * @property card `cardId@ver`, e.g. `charge@1`
 * @property choices multiselect codes joined by U+001F (`SCREENS_LATE`, `COLD_SHOWER`, `stack:…`)
 * @property count alcohol servings of «Вчера» (1…4)
 * @property answeredAt epoch ms: the ritual delta needs the time
 */
@Entity(tableName = "checkin_answer", primaryKeys = ["user_id", "day_key", "card"])
data class CheckinAnswerEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    val card: String,
    val phase: String,
    val level: Int?,
    val choices: String,
    val count: Int?,
    val skipped: Boolean,
    @ColumnInfo(name = "answered_at") val answeredAt: Long,
)

/**
 * S5 «Только ты» (A44 §6.4, A25 §2.4/§6): closeness, pulls, the week question. A separate table,
 * the payload is AEAD-encrypted with K_field on top of SQLCipher and bound to its row (associated
 * data `checkin_private:<day>:<card>`). Never sent to a server, an LLM, the mentor, a push or a
 * widget; «Выключить и удалить всё» deletes every row.
 */
@Entity(tableName = "checkin_private", primaryKeys = ["user_id", "day_key", "card"])
data class CheckinPrivateEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    val card: String,
    val payload: ByteArray,
    @ColumnInfo(name = "answered_at") val answeredAt: Long,
) {
    override fun equals(other: Any?): Boolean =
        other is CheckinPrivateEntity && other.userId == userId && other.dayKey == dayKey && other.card == card &&
            other.answeredAt == answeredAt && other.payload.contentEquals(payload)

    override fun hashCode(): Int = (userId + dayKey + card).hashCode() * 31 + payload.contentHashCode()
}

/**
 * The morning as a whole (A44 §4): when the wake deck was done, «Быстро», when the reveal was
 * seen and both Заряд levels for «совпали N из M». `data_level` is the HoT Заряд (ECAP@1) level
 * 1…5, never a raw WHOOP value.
 */
@Entity(tableName = "checkin_day", primaryKeys = ["user_id", "day_key"])
data class CheckinDayEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "wake_done_at") val wakeDoneAt: Long? = null,
    val quick: Boolean = false,
    @ColumnInfo(name = "you_level") val youLevel: Int? = null,
    @ColumnInfo(name = "data_level") val dataLevel: Int? = null,
    @ColumnInfo(name = "reveal_seen_at") val revealSeenAt: Long? = null,
    @ColumnInfo(name = "ritual_done_at") val ritualDoneAt: Long? = null,
)

/**
 * A44 §7 `DiscoveryRecord` (extends `InsightRecord`): a link shown as a discovery. Numbers of the
 * method only; [sensitivity] `S5` marks the «Только ты» pairs kept under the lock.
 */
@Entity(tableName = "checkin_discovery", primaryKeys = ["user_id", "pair_id"])
data class CheckinDiscoveryEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "pair_id") val pairId: String,
    val n1: Int,
    val n0: Int,
    val effect: Double,
    @ColumnInfo(name = "ci_low") val ciLow: Double,
    @ColumnInfo(name = "ci_high") val ciHigh: Double,
    val method: String,
    @ColumnInfo(name = "shown_at") val shownAt: Long,
    val sensitivity: String,
)

/**
 * Local usage metrics of the decks (И2.18): what happened to a card and how long it took — never
 * the answer, no health values, never sent anywhere.
 */
@Entity(tableName = "checkin_event", indices = [Index(value = ["user_id", "day_key"])])
data class CheckinEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    val phase: String,
    val card: String?,
    val kind: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val at: Long,
)
