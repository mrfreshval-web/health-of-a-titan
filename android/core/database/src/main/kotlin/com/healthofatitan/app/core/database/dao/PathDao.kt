package com.healthofatitan.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.healthofatitan.app.core.database.entity.MetricDailyEntity
import com.healthofatitan.app.core.database.entity.PathChangeProposalEntity
import com.healthofatitan.app.core.database.entity.PathGoalComponentEntity
import com.healthofatitan.app.core.database.entity.PathGoalEntity
import com.healthofatitan.app.core.database.entity.PathGoalVersionEntity
import com.healthofatitan.app.core.database.entity.PathHorizonEntity
import com.healthofatitan.app.core.database.entity.PathLetterEntity
import com.healthofatitan.app.core.database.entity.PathMarkEntity
import com.healthofatitan.app.core.database.entity.PathStepEntity
import kotlinx.coroutines.flow.Flow

/**
 * «Путь» v1 (A56 §1): goals, their components and versions, change cards, the Titan of the
 * horizon, the letter, the marks; and the derived data the path reads (sleep hours, workout days).
 * Everything stays on the device (P-21).
 */
@Dao
abstract class PathDao {

    @Query("SELECT * FROM path_goal WHERE user_id = :userId ORDER BY id DESC")
    abstract fun observeGoals(userId: String): Flow<List<PathGoalEntity>>

    @Query("SELECT * FROM path_goal WHERE user_id = :userId ORDER BY id DESC")
    abstract suspend fun goals(userId: String): List<PathGoalEntity>

    @Query("SELECT * FROM path_goal WHERE id = :id")
    abstract suspend fun goal(id: Long): PathGoalEntity?

    @Insert
    abstract suspend fun insertGoal(goal: PathGoalEntity): Long

    @Upsert
    abstract suspend fun upsertGoal(goal: PathGoalEntity)

    @Query("DELETE FROM path_goal WHERE id = :id")
    abstract suspend fun deleteGoal(id: Long)

    @Query("SELECT * FROM path_goal_component WHERE user_id = :userId ORDER BY goal_id, sort_order")
    abstract fun observeComponents(userId: String): Flow<List<PathGoalComponentEntity>>

    @Query("SELECT * FROM path_goal_component WHERE goal_id = :goalId ORDER BY sort_order")
    abstract suspend fun components(goalId: Long): List<PathGoalComponentEntity>

    @Query("DELETE FROM path_goal_component WHERE goal_id = :goalId")
    abstract suspend fun deleteComponents(goalId: Long)

    @Insert
    abstract suspend fun insertComponents(rows: List<PathGoalComponentEntity>)

    @Insert
    abstract suspend fun insertVersion(version: PathGoalVersionEntity): Long

    @Query("SELECT * FROM path_goal_version WHERE goal_id = :goalId ORDER BY v")
    abstract suspend fun versions(goalId: Long): List<PathGoalVersionEntity>

    @Query("SELECT * FROM path_goal_version WHERE user_id = :userId ORDER BY goal_id, v")
    abstract fun observeVersions(userId: String): Flow<List<PathGoalVersionEntity>>

    /** A goal and its components as one row of truth, with the version that explains the change (P-11). */
    @Transaction
    open suspend fun saveGoal(goal: PathGoalEntity, components: List<PathGoalComponentEntity>, version: PathGoalVersionEntity?): Long {
        val id = if (goal.id == 0L) insertGoal(goal) else goal.id.also { upsertGoal(goal) }
        deleteComponents(id)
        insertComponents(components.map { it.copy(goalId = id) })
        version?.let { insertVersion(it.copy(goalId = id)) }
        return id
    }

    /** A draft without «Беру путь» is quietly deleted after 7 days (A56 Э7). */
    @Transaction
    open suspend fun dropGoal(id: Long) {
        deleteComponents(id)
        deleteVersions(id)
        deleteGoal(id)
    }

    @Query("DELETE FROM path_goal_version WHERE goal_id = :goalId")
    abstract suspend fun deleteVersions(goalId: Long)

    @Insert
    abstract suspend fun insertProposal(p: PathChangeProposalEntity): Long

    @Query("UPDATE path_change_proposal SET state = :state WHERE id = :id")
    abstract suspend fun setProposalState(id: Long, state: String)

    @Query("SELECT * FROM path_change_proposal WHERE goal_id = :goalId AND state = 'OPEN' ORDER BY id DESC")
    abstract suspend fun openProposals(goalId: Long): List<PathChangeProposalEntity>

    @Upsert
    abstract suspend fun upsertHorizon(h: PathHorizonEntity)

    @Query("SELECT * FROM path_horizon WHERE user_id = :userId")
    abstract fun observeHorizons(userId: String): Flow<List<PathHorizonEntity>>

    @Query("SELECT * FROM path_horizon WHERE goal_id = :goalId")
    abstract suspend fun horizon(goalId: Long): PathHorizonEntity?

    @Upsert
    abstract suspend fun upsertLetter(l: PathLetterEntity)

    @Query("SELECT * FROM path_letter WHERE goal_id = :goalId")
    abstract suspend fun letter(goalId: Long): PathLetterEntity?

    @Query("DELETE FROM path_letter WHERE goal_id = :goalId")
    abstract suspend fun deleteLetter(goalId: Long)

    /** A mark is never rolled back (P-14): a second tap of the same mark changes nothing. */
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    abstract suspend fun insertMark(m: PathMarkEntity)

    @Query("SELECT * FROM path_mark WHERE user_id = :userId")
    abstract fun observeMarks(userId: String): Flow<List<PathMarkEntity>>

    // History of the v10 goals (A42): the user's taps «шаг дня сделан».
    @Upsert
    abstract suspend fun upsertStep(step: PathStepEntity)

    @Query("DELETE FROM path_step WHERE user_id = :userId AND goal_id = :goalId AND day_key = :dayKey")
    abstract suspend fun deleteStep(userId: String, goalId: Long, dayKey: String)

    @Query("SELECT * FROM path_step WHERE user_id = :userId AND goal_id = :goalId AND day_key = :dayKey")
    abstract suspend fun step(userId: String, goalId: Long, dayKey: String): PathStepEntity?

    @Query("SELECT * FROM path_step WHERE user_id = :userId")
    abstract fun observeSteps(userId: String): Flow<List<PathStepEntity>>

    /** Derived values of one metric version from [fromDay] (sleep hours SLP for the lever). */
    @Query("SELECT * FROM metric_daily WHERE user_id = :userId AND metric_id = :metricId AND metric_version = :version AND day_key >= :fromDay")
    abstract fun observeMetric(userId: String, metricId: String, version: Int, fromDay: String): Flow<List<MetricDailyEntity>>

    /** Days with a WHOOP workout from [fromDay] (class and duration only, A35 §4). */
    @Query("SELECT DISTINCT day_key FROM training_session WHERE user_id = :userId AND day_key >= :fromDay")
    abstract fun observeTrainingDays(userId: String, fromDay: String): Flow<List<String>>

    companion object {
        const val PROPOSAL_OPEN = "OPEN"
        const val PROPOSAL_APPLIED = "APPLIED"
        const val PROPOSAL_DECLINED = "DECLINED"
    }
}
