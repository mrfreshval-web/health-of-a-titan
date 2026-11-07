package com.healthofatitan.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.healthofatitan.app.core.database.entity.TodayMarkEntity
import kotlinx.coroutines.flow.Flow

/** «Сегодня» v3: water, meals and the tasks of a day as the person marked them (A46 §2.3, §2.4). */
@Dao
interface TodayDao {

    @Query("SELECT * FROM today_mark WHERE user_id = :userId AND day_key = :dayKey")
    fun observeDay(userId: String, dayKey: String): Flow<List<TodayMarkEntity>>

    @Query("SELECT * FROM today_mark WHERE user_id = :userId AND day_key = :dayKey")
    suspend fun day(userId: String, dayKey: String): List<TodayMarkEntity>

    @Query("SELECT * FROM today_mark WHERE user_id = :userId AND day_key >= :fromDay AND kind = :kind")
    suspend fun since(userId: String, fromDay: String, kind: String): List<TodayMarkEntity>

    @Upsert
    suspend fun upsert(row: TodayMarkEntity)

    @Query("DELETE FROM today_mark WHERE user_id = :userId AND day_key = :dayKey AND kind = :kind AND ref = :ref")
    suspend fun delete(userId: String, dayKey: String, kind: String, ref: String)
}
