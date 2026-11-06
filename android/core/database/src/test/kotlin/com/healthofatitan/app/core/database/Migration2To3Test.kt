package com.healthofatitan.app.core.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.entity.DayPlanEntity
import com.healthofatitan.app.core.database.entity.EveningCheckEntity
import com.healthofatitan.app.core.database.entity.PlanItemEntity
import com.healthofatitan.app.core.database.entity.RoutineAnchorEntity
import com.healthofatitan.app.core.database.entity.SyncStateEntity
import com.healthofatitan.app.core.database.entity.TrainingSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Slice 1 → slice 2 on the founder's phone: a version-2 database (the slice 1 schema frozen as raw
 * SQL, with a consent row and a metric) opens with schema 3 through MIGRATION_2_3 only. Room
 * validates the new tables against the entities; then each slice 2 table takes a row.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class Migration2To3Test {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun createVersion2(name: String) {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `consent_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`user_id` TEXT NOT NULL, `consent_id` TEXT NOT NULL, `consent_version` INTEGER NOT NULL, " +
                    "`granted` INTEGER NOT NULL, `ts_utc_ms` INTEGER NOT NULL, `tz_offset_min` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_consent_record_user_id_consent_id` ON `consent_record` (`user_id`, `consent_id`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `metric_daily` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, " +
                    "`metric_id` TEXT NOT NULL, `metric_version` INTEGER NOT NULL, `value` REAL, `z` REAL, `level` INTEGER, " +
                    "`coverage` REAL NOT NULL, `ewma7` REAL, `computed_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`user_id`, `day_key`, `metric_id`, `metric_version`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `baseline_snapshot` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, " +
                    "`input_id` TEXT NOT NULL, `median28` REAL, `mad28` REAL, `n` INTEGER NOT NULL, `computed_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`user_id`, `day_key`, `input_id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_state` (`source` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                    "`last_attempt_at` INTEGER, `last_success_at` INTEGER, `baseline_nights` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`source`))",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'slice-1')")
            db.execSQL(
                "INSERT INTO consent_record (user_id, consent_id, consent_version, granted, ts_utc_ms, tz_offset_min) " +
                    "VALUES ('local', 'disclaimer', 1, 1, 1762400000000, -300)",
            )
            db.execSQL(
                "INSERT INTO metric_daily (user_id, day_key, metric_id, metric_version, value, z, level, coverage, ewma7, computed_at) " +
                    "VALUES ('local', '2026-11-06', 'SLP', 1, 7.2, NULL, NULL, 1.0, NULL, 1)",
            )
            db.execSQL("INSERT INTO sync_state (source, status, last_attempt_at, last_success_at, baseline_nights) VALUES ('whoop', 'OK', 1, 1, 21)")
            db.version = 2
        }
    }

    @Test
    fun `version 2 upgrades to 3, keeps slice 1 data and accepts the day plan`() = runTest {
        val name = "migration-2-3.db"
        createVersion2(name)
        val db = Room.databaseBuilder(context, HotDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            db.openHelper.writableDatabase // runs and validates MIGRATION_2_3
            assertEquals(1, db.consentDao().observeCurrent("local").first().size)
            assertEquals(7.2, db.metricDao().observeLatestDay("local", "SLP").first().single().value)
            assertEquals(21, db.metricDao().syncState("whoop")?.baselineNights)

            val day = db.dayDao()
            day.savePlan(
                DayPlanEntity(
                    "local", "2026-11-07", "STEADY", "STEADY", 1, "SG-SLEEP", "", "REG-02@1\tСон короче нормы.", "day@1",
                    "Ровно · день по плану", "Сон 6 ч 40 мин.", 1380, 840, 1320, 10L, null,
                ),
                listOf(PlanItemEntity("local", "2026-11-07", "training", "A19-Q@1", "TRAINING", 1080, 1125, "Силовая", null, 0, "OPEN", null, null)),
            )
            day.markNotified("local", "2026-11-07", 20L)
            day.setItemStatus("local", "2026-11-07", "training", "DONE_WHOOP", 30L)
            assertEquals(20L, day.plan("local", "2026-11-07")?.notifiedAt)
            assertEquals("DONE_WHOOP", day.items("local", "2026-11-07").single().status)
            assertEquals("2026-11-07", day.observeLatestPlan("local").first()?.dayKey)

            db.metricDao().saveSyncWithRoutine(
                userId = "local",
                daily = emptyList(),
                baselines = emptyList(),
                state = SyncStateEntity("whoop", "OK", 2L, 2L, 22),
                sessionsFromDay = "2026-10-10",
                sessions = listOf(TrainingSessionEntity("local", "w1", "2026-11-07", 1090, 50, "STRENGTH", "MIXED", 2L)),
                anchors = listOf(RoutineAnchorEntity("local", "wake_weekday", 420, null, "whoop", 2L)),
            )
            assertEquals("w1", day.sessions("local", "2026-11-01").single().sourceId)
            assertEquals(420, day.anchors("local").single().valueMin)

            day.upsertEvening(EveningCheckEntity("local", "2026-11-07", null, "SORE", 40L))
            val evening = assertNotNull(day.evening("local", "2026-11-07"))
            assertEquals(null, evening.alcohol)
            assertEquals("SORE", day.observeLatestEvening("local").first()?.body)
        } finally {
            db.close()
        }
    }
}
