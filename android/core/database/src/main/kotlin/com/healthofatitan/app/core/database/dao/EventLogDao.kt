package com.healthofatitan.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.healthofatitan.app.core.database.entity.EventLogEntity
import kotlinx.coroutines.flow.Flow

/** The event journal (A58 §8): append and read by the prefix of the kind; rows are never updated. */
@Dao
interface EventLogDao {
    @Insert
    suspend fun append(event: EventLogEntity): Long

    @Query("SELECT * FROM event_log WHERE user_id = :userId AND substr(kind, 1, length(:prefix)) = :prefix ORDER BY at, id")
    fun observe(userId: String, prefix: String): Flow<List<EventLogEntity>>

    @Query("SELECT * FROM event_log WHERE user_id = :userId AND substr(kind, 1, length(:prefix)) = :prefix ORDER BY at, id")
    suspend fun list(userId: String, prefix: String): List<EventLogEntity>

    @Query("SELECT COUNT(*) FROM event_log WHERE user_id = :userId AND kind = :kind")
    suspend fun count(userId: String, kind: String): Int
}
