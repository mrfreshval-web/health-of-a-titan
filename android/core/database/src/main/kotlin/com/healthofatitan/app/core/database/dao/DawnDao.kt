package com.healthofatitan.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthofatitan.app.core.database.entity.DawnLogEntity
import kotlinx.coroutines.flow.Flow

/** «Рассвет»: which mornings were shown and with which haiku (A37 §4, STYLE.md §6.2). */
@Dao
interface DawnDao {

    /** The first dawn of a day wins: a second insert for the same day is ignored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: DawnLogEntity): Long

    @Query("SELECT * FROM dawn_log WHERE user_id = :userId AND day_key = :dayKey")
    suspend fun day(userId: String, dayKey: String): DawnLogEntity?

    @Query("SELECT * FROM dawn_log WHERE user_id = :userId AND day_key = :dayKey")
    fun observeDay(userId: String, dayKey: String): Flow<DawnLogEntity?>

    @Query("SELECT * FROM dawn_log WHERE user_id = :userId ORDER BY day_key DESC")
    suspend fun history(userId: String): List<DawnLogEntity>
}
