package com.healthofatitan.app.core.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.entity.EveningCheckEntity
import com.healthofatitan.app.core.database.entity.ReturnLogEntity
import com.healthofatitan.app.core.database.entity.SafetyEventEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * «Титаниум» → stream A on the founder's phone: a version-4 database (schema frozen as raw SQL,
 * with an evening answered in slice 2) opens with schema 5 through MIGRATION_4_5 only. Room
 * validates the new `evening_check` columns, `return_log` and `safety_event`; the old evening stays.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class Migration4To5Test {

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
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
                "CREATE TABLE IF NOT EXISTS `dawn_log` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, `haiku_id` TEXT, " +
                    "`text_key` TEXT, `image` TEXT, `mode` TEXT NOT NULL, `shown_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`user_id`, `day_key`))",
                "CREATE INDEX IF NOT EXISTS `index_dawn_log_user_id_haiku_id` ON `dawn_log` (`user_id`, `haiku_id`)",
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'titanium')",
                "INSERT INTO evening_check VALUES ('local', '2026-11-10', 1, 'SORE', 20)",
                "INSERT INTO day_plan VALUES ('local', '2026-11-10', 'STEADY', 'STEADY', 1, 'SG-SLEEP', '', '', 'day@1', " +
                    "'Ровно · день по плану', 'Сон 6 ч 40 мин.', 1410, 840, 1350, 10, NULL)",
            ).forEach(db::execSQL)
            db.version = 4
        }
    }

    @Test
    fun `version 4 upgrades to 5, keeps the evening and accepts the daily loop`() = runTest {
        val name = "migration-4-5.db"
        createVersion4(name)
        val db = Room.databaseBuilder(context, HotDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            db.openHelper.writableDatabase // runs and validates MIGRATION_4_5
            val day = db.dayDao()
            val old = day.evening("local", "2026-11-10")
            assertEquals("SORE", old?.body)
            assertNull(old?.training)
            assertNull(old?.planHelped)
            day.upsertEvening(EveningCheckEntity("local", "2026-11-11", 0, "FINE", 30L, "DONE", "PLATE", true))
            assertEquals(true, day.evening("local", "2026-11-11")?.planHelped)
            assertEquals("PLATE", day.observeLatestEvening("local").first()?.food)

            val loop = db.dailyLoopDao()
            loop.log(ReturnLogEntity("local", "2026-11-08", ReturnLogEntity.KIND_OPEN, 1L))
            loop.log(ReturnLogEntity("local", "2026-11-11", ReturnLogEntity.KIND_OPEN, 2L))
            loop.log(ReturnLogEntity("local", "2026-11-11", ReturnLogEntity.KIND_OPEN, 3L)) // ignored
            assertEquals("2026-11-08", loop.lastBefore("local", ReturnLogEntity.KIND_OPEN, "2026-11-11"))
            assertEquals("2026-11-11", loop.last("local", ReturnLogEntity.KIND_OPEN))
            assertNull(loop.last("local", ReturnLogEntity.KIND_NUDGE))
            assertEquals(1, loop.observeCount("local", ReturnLogEntity.KIND_OPEN, "2026-11-11").first())

            assertEquals(0, loop.maxLevelSince("local", 0L))
            loop.insertSafety(SafetyEventEntity(userId = "local", dayKey = "2026-11-11", at = 100L, level = 2, reason = "DIRECT", source = "EVENING"))
            loop.insertSafety(SafetyEventEntity(userId = "local", dayKey = "2026-11-11", at = 200L, level = 1, reason = null, source = "MENTOR"))
            assertEquals(2, loop.maxLevelSince("local", 50L))
            assertEquals(1, loop.maxLevelSince("local", 150L))
            assertEquals(100L, loop.lastAt("local", 2))
            assertNull(loop.lastAt("local", 3))
            assertEquals(listOf(200L, 100L), loop.safetyEvents("local").map { it.at })
        } finally {
            db.close()
        }
    }
}
