package com.healthofatitan.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.healthofatitan.app.core.database.entity.BodyInputEntity
import com.healthofatitan.app.core.database.entity.WeekDayScoreEntity
import com.healthofatitan.app.core.database.entity.WeekFrameEntity
import com.healthofatitan.app.core.database.entity.WeekGoalEntity
import com.healthofatitan.app.core.database.entity.WeekPlanEntity
import kotlinx.coroutines.flow.Flow

/** «Неделя» (A52): plans, goals, scores of days, frames of the Titan and body inputs. */
@Dao
abstract class WeekDao {

    @Query("SELECT * FROM week_plan WHERE user_id = :userId AND week_start = :weekStart")
    abstract suspend fun plan(userId: String, weekStart: String): WeekPlanEntity?

    @Query("SELECT * FROM week_plan WHERE user_id = :userId AND week_start = :weekStart")
    abstract fun observePlan(userId: String, weekStart: String): Flow<WeekPlanEntity?>

    @Query("SELECT * FROM week_plan WHERE user_id = :userId ORDER BY week_start LIMIT 1")
    abstract suspend fun firstPlan(userId: String): WeekPlanEntity?

    @Upsert
    abstract suspend fun upsertPlan(plan: WeekPlanEntity)

    @Query("SELECT * FROM week_goal WHERE user_id = :userId AND week_start = :weekStart ORDER BY sort_order")
    abstract suspend fun goals(userId: String, weekStart: String): List<WeekGoalEntity>

    @Query("SELECT * FROM week_goal WHERE user_id = :userId AND week_start = :weekStart ORDER BY sort_order")
    abstract fun observeGoals(userId: String, weekStart: String): Flow<List<WeekGoalEntity>>

    @Upsert
    abstract suspend fun upsertGoals(goals: List<WeekGoalEntity>)

    @Query("DELETE FROM week_goal WHERE user_id = :userId AND week_start = :weekStart")
    abstract suspend fun deleteGoals(userId: String, weekStart: String)

    @Query("DELETE FROM week_goal WHERE user_id = :userId AND week_start = :weekStart AND goal_id = :goalId")
    abstract suspend fun deleteGoal(userId: String, weekStart: String, goalId: String)

    /** The plan and its goals replace what was stored for that week, in one transaction. */
    @Transaction
    open suspend fun savePlan(plan: WeekPlanEntity, goals: List<WeekGoalEntity>) {
        upsertPlan(plan)
        deleteGoals(plan.userId, plan.weekStart)
        upsertGoals(goals)
    }

    @Query("SELECT * FROM week_day_score WHERE user_id = :userId AND day_key >= :fromDay AND day_key <= :toDay ORDER BY day_key")
    abstract suspend fun scores(userId: String, fromDay: String, toDay: String): List<WeekDayScoreEntity>

    @Upsert
    abstract suspend fun upsertScores(rows: List<WeekDayScoreEntity>)

    @Query("SELECT * FROM week_frame WHERE user_id = :userId ORDER BY week_start")
    abstract fun observeFrames(userId: String): Flow<List<WeekFrameEntity>>

    @Query("SELECT * FROM week_frame WHERE user_id = :userId AND week_start = :weekStart")
    abstract suspend fun frame(userId: String, weekStart: String): WeekFrameEntity?

    @Upsert
    abstract suspend fun upsertFrame(frame: WeekFrameEntity)

    @Query("SELECT * FROM body_input WHERE user_id = :userId AND kind = :kind ORDER BY day_key DESC LIMIT 1")
    abstract suspend fun latestInput(userId: String, kind: String): BodyInputEntity?

    @Query("SELECT * FROM body_input WHERE user_id = :userId ORDER BY day_key DESC")
    abstract fun observeInputs(userId: String): Flow<List<BodyInputEntity>>

    @Upsert
    abstract suspend fun upsertInput(row: BodyInputEntity)
}
