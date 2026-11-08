package com.healthofatitan.app.core.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.entity.BodyInputEntity
import com.healthofatitan.app.core.database.entity.PersonProfileEntity
import com.healthofatitan.app.core.model.profile.AddressForm
import com.healthofatitan.app.core.model.profile.ProfileSettings
import com.healthofatitan.app.core.model.profile.SexForCalc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Review of A61 by Agent 7 (2026-11-07). MIGRATION_12_13 went out in a build, so a phone may hold
 * a version-13 database with what it left: a year of birth seeded from an age under 18 and the
 * legacy age and sex rows in `body_input`. MIGRATION_13_14 cleans both and keeps what the person
 * typed in the profile and the waist. The store drops a legacy row when its field is cleared, and
 * the wipe of the whole database takes the profile too.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class Migration13To14Test {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun open(name: String): HotDatabase =
        Room.databaseBuilder(context, HotDatabase::class.java, name).addMigrations(*Migrations.ALL).allowMainThreadQueries().build()

    /** A database with the schema of 13 (13 → 14 changes no table) and the rows a v13 build left. */
    private fun createVersion13(name: String, rows: List<String>) {
        context.getDatabasePath(name).delete()
        open(name).apply { openHelper.writableDatabase }.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            rows.forEach(db::execSQL)
            db.version = 13
        }
    }

    private fun profileRow(user: String, birthYear: Int?, sex: String) =
        "INSERT INTO person_profile (user_id, address_form, birth_year, sex_for_calc, updated_at) " +
            "VALUES ('$user', 'n', ${birthYear ?: "NULL"}, '$sex', 1)"

    @Test
    fun `version 13 loses a minor's seeded year and the legacy rows, keeps typed years and the waist`() = runTest {
        val name = "migration-13-14.db"
        createVersion13(
            name,
            listOf(
                // seeded by 12 → 13 from an age of 16: 2026 − 16
                profileRow("local", 2010, "female"),
                "INSERT INTO body_input VALUES ('local', 'age_years', '2026-11-02', 16.0, NULL, 20)",
                "INSERT INTO body_input VALUES ('local', 'sex', '2026-11-02', NULL, 'female', 21)",
                "INSERT INTO body_input VALUES ('local', 'waist_cm', '2026-11-02', 70.0, NULL, 22)",
                // the person typed 1990 in the profile after an old row of 16: the typed year stays
                profileRow("typed", 1990, "unspecified"),
                "INSERT INTO body_input VALUES ('typed', 'age_years', '2026-11-02', 16.0, NULL, 20)",
                // an adult: the seeded year stays
                profileRow("adult", 1988, "male"),
                "INSERT INTO body_input VALUES ('adult', 'age_years', '2026-11-02', 38.0, NULL, 20)",
                "INSERT INTO body_input VALUES ('adult', 'sex', '2026-11-02', NULL, 'male', 21)",
            ),
        )
        val db = open(name)
        try {
            db.openHelper.writableDatabase // runs 13 → 14 and validates the schema
            assertEquals(16, db.openHelper.readableDatabase.version)
            val store = ProfileSettingsStore(db.personProfileDao()) { 99L }
            assertEquals(ProfileSettings(AddressForm.NEUTRAL, null, SexForCalc.FEMALE), store.current())
            assertEquals(1990, db.personProfileDao().get("typed")?.birthYear)
            assertEquals(1988, db.personProfileDao().get("adult")?.birthYear)
            listOf("local", "typed", "adult").forEach { user ->
                assertNull(db.weekDao().latestInput(user, BodyInputEntity.KIND_AGE), user)
                assertNull(db.weekDao().latestInput(user, BodyInputEntity.KIND_SEX), user)
            }
            assertEquals(70.0, db.weekDao().latestInput("local", BodyInputEntity.KIND_WAIST)?.value)
        } finally {
            db.close()
        }
    }

    @Test
    fun `clearing a field of the profile deletes its legacy row`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, HotDatabase::class.java).allowMainThreadQueries().build()
        try {
            val user = HotDatabase.LOCAL_USER
            val week = db.weekDao()
            week.upsertInput(BodyInputEntity(user, BodyInputEntity.KIND_AGE, "2026-11-02", 38.0, null, 1))
            week.upsertInput(BodyInputEntity(user, BodyInputEntity.KIND_SEX, "2026-11-02", null, "male", 2))
            week.upsertInput(BodyInputEntity(user, BodyInputEntity.KIND_WAIST, "2026-11-02", 88.0, null, 3))
            val store = ProfileSettingsStore(db.personProfileDao()) { 5L }

            store.setSexForCalc(SexForCalc.MALE) // a chosen sex deletes nothing
            assertNotNull(week.latestInput(user, BodyInputEntity.KIND_SEX))
            store.setSexForCalc(SexForCalc.UNSPECIFIED)
            assertNull(week.latestInput(user, BodyInputEntity.KIND_SEX), "«не указывать» means «не хранится»")
            assertNotNull(week.latestInput(user, BodyInputEntity.KIND_AGE))

            store.setBirthYear(1988)
            assertNotNull(week.latestInput(user, BodyInputEntity.KIND_AGE))
            store.setBirthYear(null)
            assertNull(week.latestInput(user, BodyInputEntity.KIND_AGE))
            assertEquals(88.0, week.latestInput(user, BodyInputEntity.KIND_WAIST)?.value, "the waist stays")
            assertEquals(ProfileSettings(), store.current())
        } finally {
            db.close()
        }
    }

    @Test
    fun `wiping the database takes the profile too`() = runTest {
        // There is no app-wide «удалить всё» yet; when it comes it wipes HotDatabase as a whole,
        // and the profile is one of its entities (Agent 7, П-12).
        assertTrue(PersonProfileEntity::class in HotDatabase.ENTITIES)
        val db = Room.inMemoryDatabaseBuilder(context, HotDatabase::class.java).allowMainThreadQueries().build()
        try {
            val store = ProfileSettingsStore(db.personProfileDao()) { 5L }
            store.setAddressForm(AddressForm.FEMININE)
            store.setBirthYear(1990)
            store.setSexForCalc(SexForCalc.FEMALE)
            assertNotNull(db.personProfileDao().get(HotDatabase.LOCAL_USER))

            withContext(Dispatchers.IO) { db.clearAllTables() }
            assertNull(db.personProfileDao().get(HotDatabase.LOCAL_USER))
            assertEquals(ProfileSettings(), store.current())
        } finally {
            db.close()
        }
    }
}
