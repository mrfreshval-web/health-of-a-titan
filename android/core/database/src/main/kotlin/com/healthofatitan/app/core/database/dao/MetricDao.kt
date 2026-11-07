package com.healthofatitan.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.healthofatitan.app.core.database.entity.BaselineSnapshotEntity
import com.healthofatitan.app.core.database.entity.MetricDailyEntity
import com.healthofatitan.app.core.database.entity.RoutineAnchorEntity
import com.healthofatitan.app.core.database.entity.SyncStateEntity
import com.healthofatitan.app.core.database.entity.TrainingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MetricDao {

    @Upsert
    abstract suspend fun upsertDaily(rows: List<MetricDailyEntity>)

    @Upsert
    abstract suspend fun upsertBaselines(rows: List<BaselineSnapshotEntity>)

    @Upsert
    abstract suspend fun upsertSyncState(state: SyncStateEntity)

    /** One sync writes all derived values atomically: either the whole window or nothing. */
    @Transaction
    open suspend fun saveSync(daily: List<MetricDailyEntity>, baselines: List<BaselineSnapshotEntity>, state: SyncStateEntity) {
        upsertDaily(daily)
        upsertBaselines(baselines)
        upsertSyncState(state)
    }

    @Upsert
    abstract suspend fun upsertSessions(rows: List<TrainingSessionEntity>)

    /** WHOOP may delete a workout: the window is replaced, not merged. */
    @Query("DELETE FROM training_session WHERE user_id = :userId AND day_key >= :fromDay")
    abstract suspend fun deleteSessionsFrom(userId: String, fromDay: String)

    @Upsert
    abstract suspend fun upsertAnchors(rows: List<RoutineAnchorEntity>)

    /**
     * Slice 2: the derived values plus the training sessions of the window and the routine
     * (A09 §7a), in one transaction.
     */
    @Transaction
    open suspend fun saveSyncWithRoutine(
        userId: String,
        daily: List<MetricDailyEntity>,
        baselines: List<BaselineSnapshotEntity>,
        state: SyncStateEntity,
        sessionsFromDay: String,
        sessions: List<TrainingSessionEntity>,
        anchors: List<RoutineAnchorEntity>,
    ) {
        upsertDaily(daily)
        upsertBaselines(baselines)
        deleteSessionsFrom(userId, sessionsFromDay)
        upsertSessions(sessions)
        upsertAnchors(anchors)
        upsertSyncState(state)
    }

    /** All metric rows of the given days (the day engine reads today and yesterday). */
    @Query("SELECT * FROM metric_daily WHERE user_id = :userId AND day_key IN (:days)")
    abstract suspend fun metricsForDays(userId: String, days: List<String>): List<MetricDailyEntity>

    /** The first day HoT has a value of [metricId] for (age of the personal norm, A09 REG-05). */
    @Query("SELECT MIN(day_key) FROM metric_daily WHERE user_id = :userId AND metric_id = :metricId AND value IS NOT NULL")
    abstract suspend fun firstDay(userId: String, metricId: String): String?

    /** Values of the latest day that has the given metric version, for «Сегодня». */
    @Query(
        """
        SELECT * FROM metric_daily
        WHERE user_id = :userId AND day_key = (
            SELECT MAX(day_key) FROM metric_daily WHERE user_id = :userId AND metric_id = :anchorMetric
        )
        """,
    )
    abstract fun observeLatestDay(userId: String, anchorMetric: String): Flow<List<MetricDailyEntity>>

    @Query("SELECT * FROM metric_daily WHERE user_id = :userId AND metric_id = :metricId AND metric_version = :version AND day_key >= :fromDay ORDER BY day_key")
    abstract suspend fun series(userId: String, metricId: String, version: Int, fromDay: String): List<MetricDailyEntity>

    @Query("SELECT * FROM sync_state WHERE source = :source")
    abstract fun observeSyncState(source: String): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE source = :source")
    abstract suspend fun syncState(source: String): SyncStateEntity?

    @Query("DELETE FROM sync_state WHERE source = :source")
    abstract suspend fun clearSyncState(source: String)
}
