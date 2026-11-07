package com.healthofatitan.app.core.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.entity.PathGoalEntity
import com.healthofatitan.app.core.database.entity.PathStepEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * «Титаниум» → stream Г «Путь» on the founder's phone: a version-4 database (schema frozen as raw
 * SQL, with a dawn, two nights and two workouts) opens with schema 6 through MIGRATION_4_5 and MIGRATION_5_6.
 * Room validates `path_goal` and `path_step` against the entities (since v11 `path_goal` is the
 * practical goal of «Путь» v1); old data survives.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class Migration5To6Test {

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

    private fun goal(title: String, start: String) = PathGoalEntity(
        userId = "local", title = title, why = null, beName = null, kind = "DO", template = "race", startDay = start,
        deadline = "2027-02-01", stage = 1, mode = "BY_DATE", status = "ACTIVE", criteria = "", obstacle = null, ifThen = null,
        version = 1, pausedDays = 0, pauseFrom = null, pauseReason = null, pauseUntil = null, takenAt = 1L, releaseToQueue = true,
        createdAt = 1L, endedDay = null, legacySphere = null,
    )

    @Test
    fun `version 4 upgrades to 6, keeps the dawn and stores goals and steps`() = runTest {
        val name = "migration-4-6.db"
        createVersion4(name)
        val db = Room.databaseBuilder(context, HotDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            db.openHelper.writableDatabase // runs and validates MIGRATION_4_5 and MIGRATION_5_6
            assertEquals("hk-ravno-001", db.dawnDao().day("local", "2026-11-10")?.haikuId)

            val path = db.pathDao()
            assertEquals(0, path.goals("local").size)
            val first = path.saveGoal(goal("Забег", "2026-11-10"), emptyList(), null)
            path.upsertStep(PathStepEntity("local", first, "2026-11-11", "MINIMAL", 2L))
            assertEquals(listOf("Забег"), path.observeGoals("local").first().map { it.title })
            assertEquals("MINIMAL", path.step("local", first, "2026-11-11")?.version)
            path.deleteStep("local", first, "2026-11-11")
            assertEquals(0, path.observeSteps("local").first().size)

            val sleep = path.observeMetric("local", "SLP", 1, "2026-11-01").first().sortedBy { it.dayKey }
            assertEquals(listOf(7.2, 6.1), sleep.map { it.value })
            assertEquals(listOf("2026-11-10"), path.observeTrainingDays("local", "2026-11-01").first())
        } finally {
            db.close()
        }
    }
}
