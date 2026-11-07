package com.healthofatitan.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.healthofatitan.app.core.database.dao.ConsentDao
import com.healthofatitan.app.core.database.dao.DailyLoopDao
import com.healthofatitan.app.core.database.dao.DawnDao
import com.healthofatitan.app.core.database.dao.DayDao
import com.healthofatitan.app.core.database.dao.MetricDao
import com.healthofatitan.app.core.database.dao.NutritionDao
import com.healthofatitan.app.core.database.dao.PathDao
import com.healthofatitan.app.core.database.dao.TrainingLogDao
import com.healthofatitan.app.core.database.entity.BaselineSnapshotEntity
import com.healthofatitan.app.core.database.entity.ConsentRecordEntity
import com.healthofatitan.app.core.database.entity.DawnLogEntity
import com.healthofatitan.app.core.database.entity.DayPlanEntity
import com.healthofatitan.app.core.database.entity.EveningCheckEntity
import com.healthofatitan.app.core.database.entity.MetricDailyEntity
import com.healthofatitan.app.core.database.entity.NutritionEveningEntity
import com.healthofatitan.app.core.database.entity.NutritionProfileEntity
import com.healthofatitan.app.core.database.entity.PathChangeProposalEntity
import com.healthofatitan.app.core.database.entity.PathGoalComponentEntity
import com.healthofatitan.app.core.database.entity.PathGoalEntity
import com.healthofatitan.app.core.database.entity.PathGoalVersionEntity
import com.healthofatitan.app.core.database.entity.PathHorizonEntity
import com.healthofatitan.app.core.database.entity.PathLetterEntity
import com.healthofatitan.app.core.database.entity.PathMarkEntity
import com.healthofatitan.app.core.database.entity.PathStepEntity
import com.healthofatitan.app.core.database.entity.PlanItemEntity
import com.healthofatitan.app.core.database.entity.ReturnLogEntity
import com.healthofatitan.app.core.database.entity.SafetyEventEntity
import com.healthofatitan.app.core.database.entity.RoutineAnchorEntity
import com.healthofatitan.app.core.database.entity.SyncStateEntity
import com.healthofatitan.app.core.database.entity.TrainingSessionEntity
import com.healthofatitan.app.core.database.entity.TrainingSetEntity
import com.healthofatitan.app.core.database.entity.TrainingWorkoutEntity
import com.healthofatitan.app.core.database.entity.CheckinAnswerEntity
import com.healthofatitan.app.core.database.entity.CheckinPrivateEntity
import com.healthofatitan.app.core.database.entity.CheckinDayEntity
import com.healthofatitan.app.core.database.entity.CheckinDiscoveryEntity
import com.healthofatitan.app.core.database.entity.CheckinEventEntity
import com.healthofatitan.app.core.database.dao.CheckinDao
import com.healthofatitan.app.core.database.dao.TodayDao
import com.healthofatitan.app.core.database.entity.TodayMarkEntity
import com.healthofatitan.app.core.database.dao.WeekDao
import com.healthofatitan.app.core.database.entity.BodyInputEntity
import com.healthofatitan.app.core.database.entity.WeekDayScoreEntity
import com.healthofatitan.app.core.database.entity.WeekExperimentEntity
import com.healthofatitan.app.core.database.entity.WeekFrameEntity
import com.healthofatitan.app.core.database.entity.WeekGoalEntity
import com.healthofatitan.app.core.database.entity.WeekPlanEntity
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
        PathGoalEntity::class,
        PathStepEntity::class,
        NutritionProfileEntity::class,
        NutritionEveningEntity::class,

        TrainingWorkoutEntity::class,
        TrainingSetEntity::class,
        CheckinAnswerEntity::class,
        CheckinPrivateEntity::class,
        CheckinDayEntity::class,
        CheckinDiscoveryEntity::class,
        CheckinEventEntity::class,
        TodayMarkEntity::class,
        WeekPlanEntity::class,
        WeekGoalEntity::class,
        WeekDayScoreEntity::class,
        WeekFrameEntity::class,
        BodyInputEntity::class,
        WeekExperimentEntity::class,
        PathGoalComponentEntity::class,
        PathGoalVersionEntity::class,
        PathChangeProposalEntity::class,
        PathHorizonEntity::class,
        PathLetterEntity::class,
        PathMarkEntity::class,
    ],
    version = 12,
    exportSchema = true,
)
abstract class HotDatabase : RoomDatabase() {
    abstract fun consentDao(): ConsentDao

    abstract fun metricDao(): MetricDao

    abstract fun dayDao(): DayDao

    abstract fun dawnDao(): DawnDao

    abstract fun dailyLoopDao(): DailyLoopDao

    abstract fun pathDao(): PathDao

    abstract fun nutritionDao(): NutritionDao

    abstract fun trainingLogDao(): TrainingLogDao
    abstract fun checkinDao(): CheckinDao

    abstract fun todayDao(): TodayDao

    abstract fun weekDao(): WeekDao

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
            PathGoalEntity::class,
            PathStepEntity::class,
            NutritionProfileEntity::class,
            NutritionEveningEntity::class,
            TrainingWorkoutEntity::class,
            TrainingSetEntity::class,
            CheckinAnswerEntity::class,
            CheckinPrivateEntity::class,
            CheckinDayEntity::class,
            CheckinDiscoveryEntity::class,
            CheckinEventEntity::class,
            TodayMarkEntity::class,
            WeekPlanEntity::class,
            WeekGoalEntity::class,
            WeekDayScoreEntity::class,
            WeekFrameEntity::class,
            BodyInputEntity::class,
            WeekExperimentEntity::class,
            PathGoalComponentEntity::class,
            PathGoalVersionEntity::class,
            PathChangeProposalEntity::class,
            PathHorizonEntity::class,
            PathLetterEntity::class,
            PathMarkEntity::class,
        )
    }
}
