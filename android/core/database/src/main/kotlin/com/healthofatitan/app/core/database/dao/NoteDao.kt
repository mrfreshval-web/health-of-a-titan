package com.healthofatitan.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.healthofatitan.app.core.database.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

/** Notes of «+» (A66 §2.4) for «За плечами» and its search (A66 §6.3). */
@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Query("SELECT * FROM note WHERE user_id = :userId ORDER BY at DESC")
    fun observe(userId: String): Flow<List<NoteEntity>>

    @Query("DELETE FROM note WHERE user_id = :userId AND id = :id")
    suspend fun delete(userId: String, id: Long)
}
