package com.healthofatitan.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.healthofatitan.app.core.database.entity.BaselineSnapshotEntity
import com.healthofatitan.app.core.database.entity.MetricDailyEntity
import com.healthofatitan.app.core.database.entity.SyncStateEntity
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
