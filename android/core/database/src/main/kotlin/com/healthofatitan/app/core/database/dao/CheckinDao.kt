package com.healthofatitan.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.healthofatitan.app.core.database.entity.CheckinAnswerEntity
import com.healthofatitan.app.core.database.entity.CheckinDayEntity
import com.healthofatitan.app.core.database.entity.CheckinDiscoveryEntity
import com.healthofatitan.app.core.database.entity.CheckinEventEntity
import com.healthofatitan.app.core.database.entity.CheckinPrivateEntity
import kotlinx.coroutines.flow.Flow

/** State cards (A44): answers, the S5 layer, the morning, discoveries and usage metrics. */
@Dao
interface CheckinDao {

    @Upsert
    suspend fun upsertAnswers(rows: List<CheckinAnswerEntity>)

    @Query("SELECT * FROM checkin_answer WHERE user_id = :userId AND day_key >= :fromDay ORDER BY day_key, answered_at")
    suspend fun answersFrom(userId: String, fromDay: String): List<CheckinAnswerEntity>

    @Query("SELECT * FROM checkin_answer WHERE user_id = :userId AND day_key >= :fromDay ORDER BY day_key, answered_at")
    fun observeAnswersFrom(userId: String, fromDay: String): Flow<List<CheckinAnswerEntity>>

    @Query("SELECT * FROM checkin_answer WHERE user_id = :userId AND day_key = :dayKey")
    suspend fun answers(userId: String, dayKey: String): List<CheckinAnswerEntity>

    @Upsert
    suspend fun upsertPrivate(rows: List<CheckinPrivateEntity>)

    @Query("SELECT * FROM checkin_private WHERE user_id = :userId AND day_key >= :fromDay ORDER BY day_key")
    suspend fun privateFrom(userId: String, fromDay: String): List<CheckinPrivateEntity>

    /** «Выключить и удалить всё» (A44 §6.4): every S5 row and every S5 discovery. */
    @Query("DELETE FROM checkin_private WHERE user_id = :userId")
    suspend fun deletePrivate(userId: String)

    @Query("DELETE FROM checkin_discovery WHERE user_id = :userId AND sensitivity = 'S5'")
    suspend fun deletePrivateDiscoveries(userId: String)

    @Upsert
    suspend fun upsertDay(row: CheckinDayEntity)

    @Query("SELECT * FROM checkin_day WHERE user_id = :userId AND day_key = :dayKey")
    suspend fun day(userId: String, dayKey: String): CheckinDayEntity?

    @Query("SELECT * FROM checkin_day WHERE user_id = :userId AND day_key = :dayKey")
    fun observeDay(userId: String, dayKey: String): Flow<CheckinDayEntity?>

    @Query("SELECT * FROM checkin_day WHERE user_id = :userId AND day_key >= :fromDay ORDER BY day_key")
    fun observeDaysFrom(userId: String, fromDay: String): Flow<List<CheckinDayEntity>>

    @Query("SELECT MIN(day_key) FROM checkin_day WHERE user_id = :userId AND wake_done_at IS NOT NULL")
    suspend fun firstDay(userId: String): String?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDiscovery(row: CheckinDiscoveryEntity): Long

    @Query("SELECT * FROM checkin_discovery WHERE user_id = :userId ORDER BY shown_at DESC")
    fun observeDiscoveries(userId: String): Flow<List<CheckinDiscoveryEntity>>

    @Query("SELECT * FROM checkin_discovery WHERE user_id = :userId ORDER BY shown_at DESC")
    suspend fun discoveries(userId: String): List<CheckinDiscoveryEntity>

    @Insert
    suspend fun insertEvents(rows: List<CheckinEventEntity>)

    @Query("SELECT * FROM checkin_event WHERE user_id = :userId AND day_key >= :fromDay ORDER BY at")
    suspend fun eventsFrom(userId: String, fromDay: String): List<CheckinEventEntity>
}
