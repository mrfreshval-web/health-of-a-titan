package com.healthofatitan.app.core.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.model.profile.AddressForm
import com.healthofatitan.app.core.model.profile.ProfileSettings
import com.healthofatitan.app.core.model.profile.SexForCalc
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Profile «Я» (A61 §2.1, D-055) on the founder's phone: a version-4 database opens through every
 * migration up to 14. Room validates `person_profile` against its entity; the age and sex the
 * fitness age kept in `body_input` seed the profile (the year of birth = the year of the entry
 * minus the age); without them the profile reads as defaults: neutral address, nothing given.
 * The store writes each field without touching the others.
 *
 * Review of Agent 7 (2026-11-07), MIGRATION_13_14: no year of birth from an age under 18, and the
 * legacy age and sex rows of `body_input` are gone after the seed; the waist stays.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class Migration12To13Test {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun createVersion4(name: String, inputs: List<String>) {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            (listOf(
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
                // the fitness age inputs of «Неделя» as v11 keeps them; MIGRATION_10_11 keeps the table (IF NOT EXISTS)
                "CREATE TABLE IF NOT EXISTS `body_input` (`user_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `day_key` TEXT NOT NULL, " +
                    "`value` REAL, `text` TEXT, `at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `kind`, `day_key`))",
            ) + inputs).forEach(db::execSQL)
            db.version = 4
        }
    }

    private fun open(name: String): HotDatabase =
        Room.databaseBuilder(context, HotDatabase::class.java, name).addMigrations(*Migrations.ALL).allowMainThreadQueries().build()

    @Test
    fun `the fitness age inputs seed the profile`() = runTest {
        val name = "migration-12-13-seed.db"
        createVersion4(
            name,
            listOf(
                "INSERT INTO body_input VALUES ('local', 'age_years', '2026-10-05', 37.0, NULL, 10)",
                "INSERT INTO body_input VALUES ('local', 'age_years', '2026-11-02', 38.0, NULL, 20)",
                "INSERT INTO body_input VALUES ('local', 'sex', '2026-11-02', NULL, 'male', 21)",
                "INSERT INTO body_input VALUES ('local', 'waist_cm', '2026-11-02', 88.0, NULL, 22)",
            ),
        )
        val db = open(name)
        try {
            db.openHelper.writableDatabase // runs and validates every migration
            assertEquals(15, db.openHelper.readableDatabase.version)
            val store = ProfileSettingsStore(db.personProfileDao()) { 99L }
            assertEquals(ProfileSettings(AddressForm.NEUTRAL, 1988, SexForCalc.MALE), store.current())
            assertEquals(21L, db.personProfileDao().get(HotDatabase.LOCAL_USER)?.updatedAt)
            // the profile alone keeps the age and the sex now (Agent 7): no copy left in body_input
            assertNull(db.weekDao().latestInput("local", "age_years"))
            assertNull(db.weekDao().latestInput("local", "sex"))
            // the waist stays where it was (Путь › Замеры)
            assertEquals(88.0, db.weekDao().latestInput("local", "waist_cm")?.value)
        } finally {
            db.close()
        }
    }

    @Test
    fun `an age under 18 seeds no year of birth`() = runTest {
        val name = "migration-12-13-minor.db"
        createVersion4(
            name,
            listOf(
                "INSERT INTO body_input VALUES ('local', 'age_years', '2026-11-02', 16.0, NULL, 20)",
                "INSERT INTO body_input VALUES ('local', 'sex', '2026-11-02', NULL, 'female', 21)",
            ),
        )
        val db = open(name)
        try {
            db.openHelper.writableDatabase
            val store = ProfileSettingsStore(db.personProfileDao()) { 99L }
            assertEquals(ProfileSettings(AddressForm.NEUTRAL, null, SexForCalc.FEMALE), store.current())
            assertNull(db.weekDao().latestInput("local", "age_years"))
            assertNull(db.weekDao().latestInput("local", "sex"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `without inputs the profile is empty and each field is written on its own`() = runTest {
        val name = "migration-12-13-empty.db"
        createVersion4(name, emptyList())
        val db = open(name)
        try {
            db.openHelper.writableDatabase
            val store = ProfileSettingsStore(db.personProfileDao()) { 5L }
            assertEquals(ProfileSettings(), store.current())
            assertNull(db.personProfileDao().get(HotDatabase.LOCAL_USER))

            store.setAddressForm(AddressForm.FEMININE)
            assertEquals(ProfileSettings(addressForm = AddressForm.FEMININE), store.current())
            assertEquals(AddressForm.FEMININE, store.addressForm.first())

            store.setSexForCalc(SexForCalc.MALE) // a separate field: the address stays feminine
            store.setBirthYear(1990)
            assertEquals(ProfileSettings(AddressForm.FEMININE, 1990, SexForCalc.MALE), store.settings.first())

            store.setAddressForm(AddressForm.NEUTRAL)
            store.setBirthYear(null)
            assertEquals(ProfileSettings(AddressForm.NEUTRAL, null, SexForCalc.MALE), store.current())
            assertEquals("n", db.personProfileDao().get(HotDatabase.LOCAL_USER)?.addressForm)
        } finally {
            db.close()
        }
    }
}
