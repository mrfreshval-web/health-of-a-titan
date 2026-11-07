package com.healthofatitan.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthofatitan.app.core.database.entity.ReturnLogEntity
import com.healthofatitan.app.core.database.entity.SafetyEventEntity
import kotlinx.coroutines.flow.Flow

/** Return after a pause (A18 И4.4) and the crisis events (A07 §4.7). */
@Dao
interface DailyLoopDao {

    /** The first event of a kind on a day wins: later ones that day are ignored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun log(row: ReturnLogEntity): Long

    /** The latest day of [kind] strictly before [dayKey] (`yyyy-MM-dd` sorts as text). */
    @Query("SELECT MAX(day_key) FROM return_log WHERE user_id = :userId AND kind = :kind AND day_key < :dayKey")
    suspend fun lastBefore(userId: String, kind: String, dayKey: String): String?

    /** The latest day of [kind], today included. */
    @Query("SELECT MAX(day_key) FROM return_log WHERE user_id = :userId AND kind = :kind")
    suspend fun last(userId: String, kind: String): String?

    @Query("SELECT COUNT(*) FROM return_log WHERE user_id = :userId AND kind = :kind AND day_key = :dayKey")
    fun observeCount(userId: String, kind: String, dayKey: String): Flow<Int>

    @Insert
    suspend fun insertSafety(event: SafetyEventEntity): Long

    /** The strongest level since [fromMs] (0 when none): CrisisSignals for «Рассвет». */
    @Query("SELECT COALESCE(MAX(level), 0) FROM safety_event WHERE user_id = :userId AND at >= :fromMs")
    suspend fun maxLevelSince(userId: String, fromMs: Long): Int

    /** When the latest event of at least [minLevel] happened, null if never. */
    @Query("SELECT MAX(at) FROM safety_event WHERE user_id = :userId AND level >= :minLevel")
    suspend fun lastAt(userId: String, minLevel: Int): Long?

    @Query("SELECT * FROM safety_event WHERE user_id = :userId ORDER BY at DESC")
    suspend fun safetyEvents(userId: String): List<SafetyEventEntity>
}
