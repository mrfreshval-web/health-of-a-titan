package com.healthofatitan.app.core.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.entity.TrainingSetEntity
import com.healthofatitan.app.core.database.entity.TrainingWorkoutEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * «Титаниум» → stream B «Тренировка»: a version-4 database (schema frozen as raw SQL, with a plan
 * of the day) opens with schema 8 through MIGRATION_4_5 … MIGRATION_7_8. Room validates `training_workout`
 * and `training_set`; the plan survives; a workout and its sets are saved and read back.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class Migration7To8Test {

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
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'slice-3')",
                "INSERT INTO day_plan VALUES ('local', '2026-11-10', 'STEADY', 'STEADY', 1, 'SG-SLEEP', '', '', 'day@1', " +
                    "'Ровно · день по плану', 'Сон 6 ч 40 мин.', 1410, 840, 1350, 10, NULL)",
            ).forEach(db::execSQL)
            db.version = 4
        }
    }

    @Test
    fun `version 4 upgrades to 8, keeps the plan and accepts the training log`() = runTest {
        val name = "migration-4-8.db"
        createVersion4(name)
        val db = Room.databaseBuilder(context, HotDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            db.openHelper.writableDatabase // runs and validates MIGRATION_4_5 … MIGRATION_7_8
            assertEquals("STEADY", db.dayDao().plan("local", "2026-11-10")?.regime)

            val log = db.trainingLogDao()
            assertTrue(log.observeDay("local", "2026-11-10").first().isEmpty())
            val workout = TrainingWorkoutEntity(
                userId = "local", workoutId = "w1", dayKey = "2026-11-10", templateRef = "tpl-functional-home@1",
                mode = "STEADY", lighter = false, startedAt = 1_000L, finishedAt = 2_000_000L, activeS = 1_800L,
                setsDone = 2, setsSkipped = 1, setsPlanned = 3, rpeMedian = 7, feltBad = false, stretchDone = true,
            )
            val sets = listOf(
                TrainingSetEntity("local", "w1", 0, "ex-squat-bw", "warmup", 1, null, "10", 40, false, null),
                TrainingSetEntity("local", "w1", 2, "ex-pushup", "main", 1, null, "8", 35, false, 7),
                TrainingSetEntity("local", "w1", 1, "ex-reverse-lunge", "main", 1, "left", null, 0, true, null),
            )
            log.save(workout, sets)
            assertEquals(listOf("w1"), log.observeDay("local", "2026-11-10").first().map { it.workoutId })
            assertEquals(listOf(0, 1, 2), log.sets("local", "w1").map { it.stepIndex })
            assertEquals(7, log.recent("local", 5).single().rpeMedian)
            // saving again replaces, never duplicates
            log.save(workout.copy(feltBad = true), sets)
            assertTrue(log.recent("local", 5).single().feltBad)
            assertEquals(3, log.sets("local", "w1").size)
        } finally {
            db.close()
        }
    }
}
