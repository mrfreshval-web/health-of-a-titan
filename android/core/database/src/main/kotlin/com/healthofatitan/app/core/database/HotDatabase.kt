package com.healthofatitan.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.healthofatitan.app.core.database.dao.ConsentDao
import com.healthofatitan.app.core.database.dao.DailyLoopDao
import com.healthofatitan.app.core.database.dao.DawnDao
import com.healthofatitan.app.core.database.dao.DayDao
import com.healthofatitan.app.core.database.dao.MetricDao
import com.healthofatitan.app.core.database.entity.BaselineSnapshotEntity
import com.healthofatitan.app.core.database.entity.ConsentRecordEntity
import com.healthofatitan.app.core.database.entity.DawnLogEntity
import com.healthofatitan.app.core.database.entity.DayPlanEntity
import com.healthofatitan.app.core.database.entity.EveningCheckEntity
import com.healthofatitan.app.core.database.entity.MetricDailyEntity
import com.healthofatitan.app.core.database.entity.PlanItemEntity
import com.healthofatitan.app.core.database.entity.ReturnLogEntity
import com.healthofatitan.app.core.database.entity.SafetyEventEntity
import com.healthofatitan.app.core.database.entity.RoutineAnchorEntity
import com.healthofatitan.app.core.database.entity.SyncStateEntity
import com.healthofatitan.app.core.database.entity.TrainingSessionEntity
import kotlin.reflect.KClass

/**
 * Local encrypted store (A14 §3, ADR-02/13). Only HoT-derived data; raw WHOOP data is never
 * persisted (checked by NoRawWhoopColumnsTest). Each slice adds its tables with a migration.
 */
@Database(
    entities = [
        ConsentRecordEntity::class,
        MetricDailyEntity::class,
        BaselineSnapshotEntity::class,
        SyncStateEntity::class,
        DayPlanEntity::class,
        PlanItemEntity::class,
        TrainingSessionEntity::class,
        RoutineAnchorEntity::class,
        EveningCheckEntity::class,
        DawnLogEntity::class,
        ReturnLogEntity::class,
        SafetyEventEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class HotDatabase : RoomDatabase() {
    abstract fun consentDao(): ConsentDao

    abstract fun metricDao(): MetricDao

    abstract fun dayDao(): DayDao

    abstract fun dawnDao(): DawnDao

    abstract fun dailyLoopDao(): DailyLoopDao

    companion object {
        const val FILE_NAME = "hot.db"

        /** Single local user until 5–20 users (A14 §3.2 `userId`, D-022). */
        const val LOCAL_USER = "local"

        /** Keep in sync with @Database.entities (used by schema tests; annotation is not runtime-visible). */
        val ENTITIES: List<KClass<*>> = listOf(
            ConsentRecordEntity::class,
            MetricDailyEntity::class,
            BaselineSnapshotEntity::class,
            SyncStateEntity::class,
            DayPlanEntity::class,
            PlanItemEntity::class,
            TrainingSessionEntity::class,
            RoutineAnchorEntity::class,
            EveningCheckEntity::class,
            DawnLogEntity::class,
            ReturnLogEntity::class,
            SafetyEventEntity::class,
        )
    }
}
