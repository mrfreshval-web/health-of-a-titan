package com.healthofatitan.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * «Рассвет» (A37 §4, content/haiku/STYLE.md §6.2): one row per morning the dawn screen was shown.
 * It is both «already shown today» and the haiku history: a text never repeats, a key image not
 * more than once in 7 days. Only on the device; no health values.
 *
 * @property haikuId the library id, null when no haiku was shown (crisis flags, nothing fitting)
 * @property textKey the normalised text (lower case, ё → е, letters only) — also catches duplicates
 *   of generated haiku with another id
 * @property image the key image of the haiku (`images:` of library.yaml)
 */
@Entity(
    tableName = "dawn_log",
    primaryKeys = ["user_id", "day_key"],
    indices = [Index(value = ["user_id", "haiku_id"])],
)
data class DawnLogEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "haiku_id") val haikuId: String?,
    @ColumnInfo(name = "text_key") val textKey: String?,
    val image: String?,
    val mode: String,
    @ColumnInfo(name = "shown_at") val shownAt: Long,
)
