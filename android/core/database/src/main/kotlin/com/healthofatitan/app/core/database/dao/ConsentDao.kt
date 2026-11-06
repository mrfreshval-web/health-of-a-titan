package com.healthofatitan.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.healthofatitan.app.core.database.entity.ConsentRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsentDao {
    @Insert
    suspend fun insert(record: ConsentRecordEntity): Long

    /** Latest record per consent id = current state. */
    @Query(
        """
        SELECT * FROM consent_record c
        WHERE c.user_id = :userId AND c.id = (
            SELECT MAX(id) FROM consent_record WHERE user_id = :userId AND consent_id = c.consent_id
        )
        """,
    )
    fun observeCurrent(userId: String): Flow<List<ConsentRecordEntity>>
}
