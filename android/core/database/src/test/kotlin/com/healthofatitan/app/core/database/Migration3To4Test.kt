package com.healthofatitan.app.core.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.entity.DawnLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Slice 2 → «Титаниум» on the founder's phone: a version-3 database (slice 1 + slice 2 schema
 * frozen as raw SQL, with a plan of the day) opens with schema 4 through MIGRATION_3_4 only.
 * Room validates `dawn_log` against [DawnLogEntity]; the plan survives; the first dawn of a day wins.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class Migration3To4Test {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun createVersion3(name: String) {
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
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'slice-2')",
                "INSERT INTO day_plan VALUES ('local', '2026-11-10', 'STEADY', 'STEADY', 1, 'SG-SLEEP', '', '', 'day@1', " +
                    "'Ровно · день по плану', 'Сон 6 ч 40 мин.', 1410, 840, 1350, 10, NULL)",
            ).forEach(db::execSQL)
            db.version = 3
        }
    }

    @Test
    fun `version 3 upgrades to 4, keeps the plan and accepts the dawn log`() = runTest {
        val name = "migration-3-4.db"
        createVersion3(name)
        val db = Room.databaseBuilder(context, HotDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            db.openHelper.writableDatabase // runs and validates MIGRATION_3_4
            assertEquals("STEADY", db.dayDao().plan("local", "2026-11-10")?.regime)

            val dawn = db.dawnDao()
            assertNull(dawn.day("local", "2026-11-10"))
            dawn.insert(DawnLogEntity("local", "2026-11-10", "hk-ravno-001", "ночь чуть короче", "dawn", "STEADY", 1L))
            // a second dawn the same day does not replace the first
            dawn.insert(DawnLogEntity("local", "2026-11-10", "hk-ravno-002", "другое", "sea", "STEADY", 2L))
            dawn.insert(DawnLogEntity("local", "2026-11-11", null, null, null, "GENTLE", 3L))
            assertEquals("hk-ravno-001", dawn.day("local", "2026-11-10")?.haikuId)
            assertEquals(listOf("2026-11-11", "2026-11-10"), dawn.history("local").map { it.dayKey })
            assertEquals("hk-ravno-001", dawn.observeDay("local", "2026-11-10").first()?.haikuId)
        } finally {
            db.close()
        }
    }
}
