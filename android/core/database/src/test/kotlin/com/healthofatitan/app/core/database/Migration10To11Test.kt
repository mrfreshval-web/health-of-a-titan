package com.healthofatitan.app.core.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.entity.BodyInputEntity
import com.healthofatitan.app.core.database.entity.TodayMarkEntity
import com.healthofatitan.app.core.database.entity.WeekDayScoreEntity
import com.healthofatitan.app.core.database.entity.WeekExperimentEntity
import com.healthofatitan.app.core.database.entity.WeekFrameEntity
import com.healthofatitan.app.core.database.entity.WeekGoalEntity
import com.healthofatitan.app.core.database.entity.WeekPlanEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * «Неделя» (A52, D-044, D-045) on the founder's phone: a version-4 database (schema 1–4 frozen as
 * raw SQL) opens through every migration up to the week tables (10 → 11). Room validates each new
 * table against its entity; old data survive; a plan with its goals replaces itself in one
 * transaction; scores, frames and body inputs are kept.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class Migration10To11Test {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun createVersion4(name: String) {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            listOf(
                "CREATE TABLE IF NOT EXISTS `consent_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`user_id` TEXT NOT NULL, `consent_id` TEXT NOT NULL, `consent_version` INTEGER NOT NULL, " +
                    "`granted` INTEGER NOT NULL, `ts_utc_ms` INTEGER NOT NULL, `tz_offset_min` INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS `index_consent_record_user_id_consent_id` ON `consent_record` (`user_id`, `consent_id`)",
                "CREATE TABLE IF NOT EXISTS `metric_daily` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, " +
                    "`metric_id` TEXT NOT NULL, `metric_version` INTEGER NOT NULL, `value` REAL, `z` REAL, `level` INTEGER, " +
                    "`coverage` REAL NOT NULL, `ewma7` REAL, `computed_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`user_id`, `day_key`, `metric_id`, `metric_version`))",
                "CREATE TABLE IF NOT EXISTS `baseline_snapshot` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, " +
                    "`input_id` TEXT NOT NULL, `median28` REAL, `mad28` REAL, `n` INTEGER NOT NULL, `computed_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`user_id`, `day_key`, `input_id`))",
                "CREATE TABLE IF NOT EXISTS `sync_state` (`source` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                    "`last_attempt_at` INTEGER, `last_success_at` INTEGER, `baseline_nights` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`source`))",
                "CREATE TABLE IF NOT EXISTS `day_plan` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, `regime` TEXT NOT NULL, " +
                    "`regime_computed` TEXT NOT NULL, `points` INTEGER NOT NULL, `signals` TEXT NOT NULL, `flags` TEXT NOT NULL, " +
                    "`fired_rules` TEXT NOT NULL, `rule_set` TEXT NOT NULL, `headline` TEXT NOT NULL, `summary` TEXT NOT NULL, " +
                    "`bedtime_min` INTEGER NOT NULL, `caffeine_cutoff_min` INTEGER NOT NULL, `wind_down_min` INTEGER NOT NULL, " +
                    "`built_at` INTEGER NOT NULL, `notified_at` INTEGER, PRIMARY KEY(`user_id`, `day_key`))",
                "CREATE TABLE IF NOT EXISTS `plan_item` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, `item_id` TEXT NOT NULL, " +
                    "`rule_ref` TEXT NOT NULL, `kind` TEXT NOT NULL, `slot_start` INTEGER, `slot_end` INTEGER, `title` TEXT NOT NULL, " +
                    "`detail` TEXT, `sort_order` INTEGER NOT NULL, `status` TEXT NOT NULL, `status_at` INTEGER, `session_ref` TEXT, " +
                    "PRIMARY KEY(`user_id`, `day_key`, `item_id`))",
                "CREATE TABLE IF NOT EXISTS `training_session` (`user_id` TEXT NOT NULL, `source_id` TEXT NOT NULL, " +
                    "`day_key` TEXT NOT NULL, `start_min` INTEGER NOT NULL, `duration_min` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                    "`intensity` TEXT NOT NULL, `computed_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `source_id`))",
                "CREATE TABLE IF NOT EXISTS `routine_anchor` (`user_id` TEXT NOT NULL, `anchor_id` TEXT NOT NULL, " +
                    "`value_min` INTEGER, `day_key` TEXT, `source` TEXT NOT NULL, `computed_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`user_id`, `anchor_id`))",
                "CREATE TABLE IF NOT EXISTS `evening_check` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, " +
                    "`alcohol` INTEGER, `body` TEXT, `answered_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `day_key`))",
                "CREATE TABLE IF NOT EXISTS `dawn_log` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, `haiku_id` TEXT, " +
                    "`text_key` TEXT, `image` TEXT, `mode` TEXT NOT NULL, `shown_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`user_id`, `day_key`))",
                "CREATE INDEX IF NOT EXISTS `index_dawn_log_user_id_haiku_id` ON `dawn_log` (`user_id`, `haiku_id`)",
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'titanium')",
                "INSERT INTO dawn_log VALUES ('local', '2026-11-10', 'hk-ravno-001', 'весло', 'oar', 'STEADY', 1)",
                "INSERT INTO metric_daily VALUES ('local', '2026-11-10', 'SLP', 1, 7.2, 0.3, 0, 1.0, 7.0, 1)",
                "INSERT INTO metric_daily VALUES ('local', '2026-11-11', 'SLP', 1, 6.1, -0.8, -1, 1.0, 6.9, 1)",
                "INSERT INTO training_session VALUES ('local', 'w-1', '2026-11-10', 1080, 45, 'CARDIO', 'ZONE2', 1)",
                "INSERT INTO training_session VALUES ('local', 'w-2', '2026-11-10', 1200, 20, 'STRENGTH', 'MIXED', 1)",
            ).forEach(db::execSQL)
            db.version = 4
        }
    }

    @Test
    fun `version 4 upgrades to the week tables and keeps today and the dawn`() = runTest {
        val name = "migration-4-week.db"
        createVersion4(name)
        val db = Room.databaseBuilder(context, HotDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            db.openHelper.writableDatabase // runs and validates every migration
            // «Путь» v1 takes 11 → 12 on top of the week tables, the profile «Я» 12 → 13, its cleanup 13 → 14
            assertEquals(14, db.openHelper.readableDatabase.version)
            assertEquals("hk-ravno-001", db.dawnDao().day("local", "2026-11-10")?.haikuId)
            db.todayDao().upsert(TodayMarkEntity("local", "2026-11-11", TodayMarkEntity.KIND_TASK_TO_WEEK, "wg:body-training", 1, goal = "body-training", at = 1L))

            val dao = db.weekDao()
            fun goal(id: String, target: Int, order: Int) =
                WeekGoalEntity("local", "2026-11-09", id, "BODY", "TRAINING", "TIMES", "Тренировки", target, "раз", "PATH", order)
            dao.savePlan(WeekPlanEntity("local", "2026-11-09", "PROPOSED", assembledAt = 1L), listOf(goal("body-training", 3, 0), goal("work-focus", 4, 1)))
            dao.savePlan(
                WeekPlanEntity("local", "2026-11-09", "APPROVED", assembledAt = 2L, ritualDoneAt = 3L, pride = "первый подход", carry = null),
                listOf(goal("body-training", 2, 0)),
            )
            assertEquals("APPROVED", dao.plan("local", "2026-11-09")?.status)
            assertEquals(listOf(2), dao.goals("local", "2026-11-09").map { it.target })
            assertEquals("первый подход", dao.observePlan("local", "2026-11-09").first()?.pride)
            assertEquals("2026-11-09", dao.firstPlan("local")?.weekStart)

            dao.upsertScores(listOf(WeekDayScoreEntity("local", "2026-11-09", true, 3, 1, "ROVNO", false, "0.8,0.7,0.9,0.2,0.1", 5L)))
            assertEquals(true, dao.scores("local", "2026-11-09", "2026-11-15").single().inSupport)
            assertEquals(0, dao.scores("local", "2026-11-16", "2026-11-22").size)

            dao.upsertFrame(WeekFrameEntity("local", "2026-11-09", "HOLD", "ROVNO", 0.7f, "0.8,0.6,0.7,0.5,0.9", "CLEAR", null, 4, 42L, null, null, 6L))
            assertEquals(4, dao.observeFrames("local").first().single().daysInSupport)
            assertNull(dao.frame("local", "2026-11-09")?.fitnessAge)

            dao.upsertInput(BodyInputEntity("local", BodyInputEntity.KIND_WAIST, "2026-11-01", 88.0, null, 7L))
            dao.upsertInput(BodyInputEntity("local", BodyInputEntity.KIND_WAIST, "2026-11-09", 87.0, null, 8L))
            assertEquals(87.0, dao.latestInput("local", BodyInputEntity.KIND_WAIST)?.value)
            assertEquals(2, dao.observeInputs("local").first().size)
            dao.upsertFrame(
                WeekFrameEntity("local", "2026-11-30", "RISE", "ROVNO", 0.9f, "0.8,0.6,0.7,0.5,0.9", "CLEAR", null, 5, 43L, null, 41.0, 9L, 1, 44.2, 12.5, 87.0),
            )
            assertEquals(listOf(1), dao.lastMilestones("local").map { it.fitnessMilestone })
            dao.upsertExperiment(
                WeekExperimentEntity("local", "2026-11-16", "exp-screens_sleep", "Экран убран → сон лучше?", "Экран убран", "SLEEP", "PATTERN", false, "TAKEN", decidedAt = 10L),
            )
            dao.upsertExperiment(dao.experiment("local", "2026-11-16")!!.copy(outcome = "YES", resultText = "Похоже, да", points = 5))
            assertEquals("YES", dao.experiment("local", "2026-11-16")?.outcome)
            dao.deleteGoal("local", "2026-11-09", "body-training")
            assertEquals(0, dao.goals("local", "2026-11-09").size)
        } finally {
            db.close()
        }
    }
}
