package com.healthofatitan.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Consent journal (A14 §3.2 `ConsentRecord`, class S2). One row per grant/revoke so the
 * history is auditable. Consent texts and ids come from Agent 7 (slice 0 step 5).
 */
@Entity(
    tableName = "consent_record",
    indices = [Index(value = ["user_id", "consent_id"])],
)
data class ConsentRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "consent_id") val consentId: String,
    @ColumnInfo(name = "consent_version") val consentVersion: Int,
    val granted: Boolean,
    /** UTC epoch ms + offset (A14 §3.2). */
    @ColumnInfo(name = "ts_utc_ms") val tsUtcMs: Long,
    @ColumnInfo(name = "tz_offset_min") val tzOffsetMin: Int,
)
