package com.healthofatitan.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.healthofatitan.app.core.database.entity.MetricDailyEntity
import com.healthofatitan.app.core.database.entity.PathGoalEntity
import com.healthofatitan.app.core.database.entity.PathStepEntity
import kotlinx.coroutines.flow.Flow

/** The path (A42 §3, D-033): goals, the user's steps, and the derived data the goal reads. */
@Dao
abstract class PathDao {

    @Query("SELECT * FROM path_goal WHERE user_id = :userId ORDER BY id DESC")
    abstract fun observeGoals(userId: String): Flow<List<PathGoalEntity>>

    @Query("SELECT * FROM path_goal WHERE user_id = :userId AND status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    abstract suspend fun active(userId: String): PathGoalEntity?

    @Insert
    abstract suspend fun insertGoal(goal: PathGoalEntity): Long

    @Query("UPDATE path_goal SET status = :status, ended_day = :endedDay WHERE user_id = :userId AND status = 'ACTIVE'")
    abstract suspend fun closeActive(userId: String, status: String, endedDay: String): Int

    /** A new goal replaces the active one; the old row and its steps stay as history. */
    @Transaction
    open suspend fun replaceActive(goal: PathGoalEntity, endedDay: String): Long {
        closeActive(goal.userId, STATUS_REPLACED, endedDay)
        return insertGoal(goal)
    }

    @Upsert
    abstract suspend fun upsertStep(step: PathStepEntity)

    @Query("DELETE FROM path_step WHERE user_id = :userId AND goal_id = :goalId AND day_key = :dayKey")
    abstract suspend fun deleteStep(userId: String, goalId: Long, dayKey: String)

    @Query("SELECT * FROM path_step WHERE user_id = :userId AND goal_id = :goalId AND day_key = :dayKey")
    abstract suspend fun step(userId: String, goalId: Long, dayKey: String): PathStepEntity?

    @Query("SELECT * FROM path_step WHERE user_id = :userId")
    abstract fun observeSteps(userId: String): Flow<List<PathStepEntity>>

    /** Derived values of one metric version from [fromDay] (sleep hours SLP for the sleep goal). */
    @Query("SELECT * FROM metric_daily WHERE user_id = :userId AND metric_id = :metricId AND metric_version = :version AND day_key >= :fromDay")
    abstract fun observeMetric(userId: String, metricId: String, version: Int, fromDay: String): Flow<List<MetricDailyEntity>>

    /** Days with a WHOOP workout from [fromDay] (class and duration only, A35 §4). */
    @Query("SELECT DISTINCT day_key FROM training_session WHERE user_id = :userId AND day_key >= :fromDay")
    abstract fun observeTrainingDays(userId: String, fromDay: String): Flow<List<String>>

    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_REPLACED = "REPLACED"
    }
}
