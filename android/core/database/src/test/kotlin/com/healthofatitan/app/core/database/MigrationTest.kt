package com.healthofatitan.app.core.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.entity.BaselineSnapshotEntity
import com.healthofatitan.app.core.database.entity.MetricDailyEntity
import com.healthofatitan.app.core.database.entity.SyncStateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Slice 0 → slice 1 upgrade on the founder's phone: a version-1 database (as Room created it in
 * slice 0, with a consent row) opens with schema 2 through MIGRATION_1_2. Room validates every
 * table against the entities after migrating, so a wrong column type or key fails here, not on
 * the Pixel. Plain framework SQLite: SQLCipher only wraps the same SQL.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun createVersion1(name: String) {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `consent_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`user_id` TEXT NOT NULL, `consent_id` TEXT NOT NULL, `consent_version` INTEGER NOT NULL, " +
                    "`granted` INTEGER NOT NULL, `ts_utc_ms` INTEGER NOT NULL, `tz_offset_min` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_consent_record_user_id_consent_id` ON `consent_record` (`user_id`, `consent_id`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'slice-0')")
            db.execSQL(
                "INSERT INTO consent_record (user_id, consent_id, consent_version, granted, ts_utc_ms, tz_offset_min) " +
                    "VALUES ('local', 'disclaimer', 1, 1, 1762400000000, -300)",
            )
            db.version = 1
        }
    }

    @Test
    fun `version 1 upgrades to 2, keeps consent and accepts metrics`() = runTest {
        val name = "migration-test.db"
        createVersion1(name)
        val db = Room.databaseBuilder(context, HotDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            db.openHelper.writableDatabase // runs and validates the migration
            assertEquals(1, db.consentDao().observeCurrent("local").first().size)

            val dao = db.metricDao()
            dao.saveSync(
                daily = listOf(MetricDailyEntity("local", "2026-11-06", "ECAP", 1, 44.0, -0.4, 0, 0.6, 46.0, 1L)),
                baselines = listOf(BaselineSnapshotEntity("local", "2026-11-06", "lnRMSSD", 4.1, 0.1, 21, 1L)),
                state = SyncStateEntity("whoop", "OK", 1L, 1L, 21),
            )
            assertEquals(44.0, dao.observeLatestDay("local", "ECAP").first().single().value)
            assertEquals(21, dao.syncState("whoop")?.baselineNights)
        } finally {
            db.close()
        }
    }

    @Test
    fun `a fresh install creates schema 2`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, HotDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.metricDao().upsertSyncState(SyncStateEntity("whoop", "OFFLINE", 2L, null, 0))
            assertEquals("OFFLINE", db.metricDao().observeSyncState("whoop").first()?.status)
        } finally {
            db.close()
        }
    }
}
