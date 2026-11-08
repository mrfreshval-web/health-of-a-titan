package com.healthofatitan.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * «Заметка» from «+» (A66 §2.4, Room 16): the person's own words about a day, found again in «За
 * плечами» (A66 §6.3). Free text lives only in this SQLCipher database and never leaves the phone;
 * CrisisGuard reads it before it is saved (A07).
 */
@Entity(tableName = "note", indices = [Index(value = ["user_id", "day_key"])])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    val text: String,
    val at: Long,
)
