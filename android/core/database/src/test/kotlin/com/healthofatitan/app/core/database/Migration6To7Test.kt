package com.healthofatitan.app.core.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.entity.NutritionEveningEntity
import com.healthofatitan.app.core.model.profile.AllergySeverity
import com.healthofatitan.app.core.model.profile.ProfileCategory
import com.healthofatitan.app.core.model.profile.UserConstraints
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «Титаниум» → stream C «Еда» on the founder's phone: a version-4 database (schema 1–4 frozen as
 * raw SQL, with a plan and a dawn) opens with schema 7 through MIGRATION_4_5 … MIGRATION_6_7. Room validates
 * `nutrition_profile` and `nutrition_evening`; the plan survives; the S4 profile needs consent,
 * is deleted by category and entirely when the consent is revoked.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class Migration6To7Test {

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
                "INSERT INTO day_plan VALUES ('local', '2026-11-10', 'STEADY', 'STEADY', 1, 'SG-SLEEP', '', '', 'day@2', " +
                    "'Ровно · день по плану', 'Сон 6 ч 40 мин.', 1410, 840, 1350, 10, NULL)",
                "INSERT INTO dawn_log VALUES ('local', '2026-11-10', 'hk-ravno-001', NULL, 'dawn', 'STEADY', 1)",
            ).forEach(db::execSQL)
            db.version = 4
        }
    }

    @Test
    fun `version 4 upgrades to 7, keeps the plan and stores the S4 profile by category`() = runTest {
        val name = "migration-4-7.db"
        createVersion4(name)
        val db = Room.databaseBuilder(context, HotDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            db.openHelper.writableDatabase // runs and validates MIGRATION_4_5 … MIGRATION_6_7
            assertEquals("STEADY", db.dayDao().plan("local", "2026-11-10")?.regime)
            assertEquals("hk-ravno-001", db.dawnDao().day("local", "2026-11-10")?.haikuId)

            var t = 1_000L
            val store = ProfileStore(db.nutritionDao(), db.consentDao()) { t++ }
            val profile = UserConstraints(
                allergies = mapOf("peanut" to AllergySeverity.ANAPHYLAXIS),
                diets = setOf("vegan"),
                injuries = setOf("knee_injury"),
            )

            // Without consent nothing is read, even if rows exist.
            store.save(profile)
            assertEquals(UserConstraints.NONE, store.profile.first().constraints)

            store.setConsent(true)
            assertTrue(store.profile.first().consented)
            assertEquals(profile, store.profile.first().constraints)

            store.setNutritionLevel("structured")
            store.delete(ProfileCategory.DIETS)
            val afterDelete = store.profile.first()
            assertEquals(emptySet(), afterDelete.constraints.diets)
            assertEquals(profile.allergies, afterDelete.constraints.allergies)
            assertEquals("structured", afterDelete.nutritionLevel)

            db.nutritionDao().upsertEvening(NutritionEveningEntity("local", "2026-11-10", "plate", 5L))
            assertEquals("plate", db.nutritionDao().observeEvening("local", "2026-11-10").first()?.mark)

            // Revoking consent deletes every category; the level is not health data and stays.
            store.setConsent(false)
            val revoked = store.profile.first()
            assertFalse(revoked.consented)
            assertTrue(db.nutritionDao().profile("local").all { it.category == ProfileStore.LEVEL_KEY })
            assertEquals("structured", revoked.nutritionLevel)

            store.deleteAll()
            assertNull(db.nutritionDao().observeEvening("local", "2026-11-10").first())
        } finally {
            db.close()
        }
    }
}
