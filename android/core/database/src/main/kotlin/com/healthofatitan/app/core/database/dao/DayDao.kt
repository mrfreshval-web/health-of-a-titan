package com.healthofatitan.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.healthofatitan.app.core.database.entity.DayPlanEntity
import com.healthofatitan.app.core.database.entity.EveningCheckEntity
import com.healthofatitan.app.core.database.entity.PlanItemEntity
import com.healthofatitan.app.core.database.entity.RoutineAnchorEntity
import com.healthofatitan.app.core.database.entity.TrainingSessionEntity
import kotlinx.coroutines.flow.Flow

/** Day plan, its items, the routine, WHOOP sessions and the evening deck (slice 2). */
@Dao
abstract class DayDao {

    @Upsert
    abstract suspend fun upsertPlan(plan: DayPlanEntity)

    @Upsert
    abstract suspend fun upsertItems(items: List<PlanItemEntity>)

    @Query("DELETE FROM plan_item WHERE user_id = :userId AND day_key = :dayKey")
    abstract suspend fun deleteItems(userId: String, dayKey: String)

    /** Today's plan is rebuilt as a whole (A09 rebuild); the caller carries the statuses over. */
    @Transaction
    open suspend fun savePlan(plan: DayPlanEntity, items: List<PlanItemEntity>) {
        deleteItems(plan.userId, plan.dayKey)
        upsertPlan(plan)
        upsertItems(items)
    }

    @Query("SELECT * FROM day_plan WHERE user_id = :userId AND day_key = :dayKey")
    abstract suspend fun plan(userId: String, dayKey: String): DayPlanEntity?

    /** Earlier days, newest first (REG-04 yesterday, REG-06 streaks). */
    @Query("SELECT * FROM day_plan WHERE user_id = :userId AND day_key < :dayKey ORDER BY day_key DESC LIMIT :limit")
    abstract suspend fun plansBefore(userId: String, dayKey: String, limit: Int): List<DayPlanEntity>

    @Query("SELECT * FROM day_plan WHERE user_id = :userId ORDER BY day_key DESC LIMIT 1")
    abstract fun observeLatestPlan(userId: String): Flow<DayPlanEntity?>

    @Query("SELECT * FROM plan_item WHERE user_id = :userId AND day_key = :dayKey ORDER BY sort_order")
    abstract suspend fun items(userId: String, dayKey: String): List<PlanItemEntity>

    @Query("SELECT * FROM plan_item WHERE user_id = :userId AND day_key = :dayKey ORDER BY sort_order")
    abstract fun observeItems(userId: String, dayKey: String): Flow<List<PlanItemEntity>>

    @Query("UPDATE plan_item SET status = :status, status_at = :at WHERE user_id = :userId AND day_key = :dayKey AND item_id = :itemId")
    abstract suspend fun setItemStatus(userId: String, dayKey: String, itemId: String, status: String, at: Long?)

    @Query("UPDATE day_plan SET notified_at = :at WHERE user_id = :userId AND day_key = :dayKey")
    abstract suspend fun markNotified(userId: String, dayKey: String, at: Long)

    @Query("SELECT * FROM routine_anchor WHERE user_id = :userId")
    abstract suspend fun anchors(userId: String): List<RoutineAnchorEntity>

    @Query("SELECT * FROM training_session WHERE user_id = :userId AND day_key >= :fromDay ORDER BY day_key, start_min")
    abstract suspend fun sessions(userId: String, fromDay: String): List<TrainingSessionEntity>

    @Upsert
    abstract suspend fun upsertEvening(check: EveningCheckEntity)

    @Query("SELECT * FROM evening_check WHERE user_id = :userId AND day_key = :dayKey")
    abstract suspend fun evening(userId: String, dayKey: String): EveningCheckEntity?

    @Query("SELECT * FROM evening_check WHERE user_id = :userId ORDER BY day_key DESC LIMIT 1")
    abstract fun observeLatestEvening(userId: String): Flow<EveningCheckEntity?>
}
