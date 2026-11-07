package com.healthofatitan.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.healthofatitan.app.core.database.entity.NutritionEveningEntity
import com.healthofatitan.app.core.database.entity.NutritionProfileEntity
import kotlinx.coroutines.flow.Flow

/** Profile of specifics (S4) and the evening food mark (A41 §6.5, §10). */
@Dao
interface NutritionDao {

    @Query("SELECT * FROM nutrition_profile WHERE user_id = :userId")
    fun observeProfile(userId: String): Flow<List<NutritionProfileEntity>>

    @Query("SELECT * FROM nutrition_profile WHERE user_id = :userId")
    suspend fun profile(userId: String): List<NutritionProfileEntity>

    @Upsert
    suspend fun upsert(rows: List<NutritionProfileEntity>)

    /** «Удалить по категории» (Agent 7). */
    @Query("DELETE FROM nutrition_profile WHERE user_id = :userId AND category = :category")
    suspend fun deleteCategory(userId: String, category: String)

    @Query("DELETE FROM nutrition_profile WHERE user_id = :userId AND category != :keep")
    suspend fun deleteAllExcept(userId: String, keep: String)

    /** Writes the given categories and removes the emptied ones in one transaction. */
    @Transaction
    suspend fun replace(userId: String, rows: List<NutritionProfileEntity>, emptied: List<String>) {
        emptied.forEach { deleteCategory(userId, it) }
        if (rows.isNotEmpty()) upsert(rows)
    }

    @Upsert
    suspend fun upsertEvening(row: NutritionEveningEntity)

    @Query("SELECT * FROM nutrition_evening WHERE user_id = :userId AND day_key = :dayKey")
    fun observeEvening(userId: String, dayKey: String): Flow<NutritionEveningEntity?>

    @Query("SELECT * FROM nutrition_evening WHERE user_id = :userId AND day_key >= :fromDay ORDER BY day_key")
    suspend fun eveningsSince(userId: String, fromDay: String): List<NutritionEveningEntity>

    @Query("DELETE FROM nutrition_evening WHERE user_id = :userId")
    suspend fun deleteEvenings(userId: String)
}
