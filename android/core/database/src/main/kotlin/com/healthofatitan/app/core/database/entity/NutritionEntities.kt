package com.healthofatitan.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * The profile of specifics (A41 §6, shared with training A40), one row per category so that
 * consent, export and deletion go by category (Agent 7). Class S4 (A25 §6): only in this
 * SQLCipher database, never in logs, analytics, pushes or widgets. [codes] are closed codes of
 * `content/profile/constraints.yaml` joined by `;` (allergens as `code:severity`). The row
 * `category = level` holds the chosen nutrition level (not health data).
 */
@Entity(tableName = "nutrition_profile", primaryKeys = ["user_id", "category"])
data class NutritionProfileEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    val category: String,
    val codes: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * The evening one-tap mark about food (A41 §10, A25 FUEL ①): `plate` «по тарелке», `partly`
 * «частично», `ate` «просто поел». Skipping is the normal state, so no row means skipped.
 */
@Entity(tableName = "nutrition_evening", primaryKeys = ["user_id", "day_key"])
data class NutritionEveningEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "day_key") val dayKey: String,
    val mark: String,
    @ColumnInfo(name = "answered_at") val answeredAt: Long,
)
