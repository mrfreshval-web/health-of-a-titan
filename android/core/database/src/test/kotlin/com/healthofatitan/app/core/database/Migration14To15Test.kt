package com.healthofatitan.app.core.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.model.profile.ProfileCategory
import com.healthofatitan.app.core.model.profile.UserConstraints
import com.healthofatitan.app.core.model.safety.SafetyEventKinds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * «Единый Титан, часть 1» (A58 §8, A60 §3): version 14 → 15 adds the event journal `event_log`
 * and fills it with what the safety bus must know on the first open: every crisis level of
 * `safety_event` and `ED_CARE` for a consented profile that marks it. Nothing else is touched.
 * The gate 18+ is a row of the consent journal (`age18`).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class Migration14To15Test {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun open(name: String): HotDatabase =
        Room.databaseBuilder(context, HotDatabase::class.java, name).addMigrations(*Migrations.ALL).allowMainThreadQueries().build()

    /** A database with the schema of 14 (15 only adds `event_log`) and the rows a v14 build left. */
    private fun createVersion14(name: String, rows: List<String>) {
        context.getDatabasePath(name).delete()
        open(name).apply { openHelper.writableDatabase }.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("DROP INDEX IF EXISTS `index_event_log_user_id_kind_at`")
            db.execSQL("DROP TABLE IF EXISTS `event_log`")
            rows.forEach(db::execSQL)
            db.version = 14
        }
    }

    private fun consent(user: String, granted: Int, id: Int) =
        "INSERT INTO consent_record (id, user_id, consent_id, consent_version, granted, ts_utc_ms, tz_offset_min) " +
            "VALUES ($id, '$user', 'profile_s4', 1, $granted, 1, 0)"

    private fun medical(user: String, codes: String, at: Long) =
        "INSERT INTO nutrition_profile (user_id, category, codes, updated_at) VALUES ('$user', 'medical', '$codes', $at)"

    @Test
    fun `version 14 gets the journal with past crises and ED_CARE of a consented profile`() = runTest {
        val name = "migration-14-15.db"
        createVersion14(
            name,
            listOf(
                "INSERT INTO safety_event (user_id, day_key, at, level, reason, source) VALUES ('local', '2026-11-06', 1000, 2, 'INDIRECT', 'EVENING')",
                "INSERT INTO safety_event (user_id, day_key, at, level, reason, source) VALUES ('local', '2026-11-07', 2000, 1, 'AMBIGUOUS', 'MENTOR')",
                // the profile marks ed_history and underweight: the longer cooldown («body») wins
                consent("local", 1, 1),
                medical("local", "diabetes;ed_history;underweight_reported", 1_762_500_000_000),
                // a profile with ed_history only
                consent("self", 1, 2),
                medical("self", "ed_history", 1_762_500_000_000),
                // the consent was revoked: nothing is read from the profile
                consent("revoked", 1, 3),
                consent("revoked", 0, 4),
                medical("revoked", "ed_signals", 5),
                // no ED code: nothing
                consent("plain", 1, 5),
                medical("plain", "diabetes;ed_historyx", 5),
            ),
        )
        val db = open(name)
        try {
            db.openHelper.writableDatabase // runs 14 → 15 and validates the schema
            assertEquals(16, db.openHelper.readableDatabase.version)
            val dao = db.eventLogDao()

            val local = dao.list("local", SafetyEventKinds.PREFIX)
            val crises = local.filter { it.kind == SafetyEventKinds.CRISIS }
            assertEquals(listOf("2", "1"), crises.map { it.payload })
            assertEquals(listOf(1000L, 2000L), crises.map { it.at })
            assertEquals(listOf("EVENING", "MENTOR"), crises.map { it.source })
            assertEquals(listOf(SafetyEventKinds.REASON_BODY), local.filter { it.kind == SafetyEventKinds.ED_CARE_ENTER }.map { it.payload })

            val self = dao.list("self", SafetyEventKinds.PREFIX)
            assertEquals(listOf(SafetyEventKinds.REASON_SELF_REPORT), self.map { it.payload })
            assertEquals("2025-11-07", self.single().dayKey)
            assertTrue(dao.list("revoked", SafetyEventKinds.PREFIX).isEmpty())
            assertTrue(dao.list("plain", SafetyEventKinds.PREFIX).isEmpty())
            assertEquals(2, dao.count("local", SafetyEventKinds.CRISIS), "safety_event is copied, not moved")
        } finally {
            db.close()
        }
    }

    @Test
    fun `marking ED_CARE in the profile writes one entry, unmarking writes no exit`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, HotDatabase::class.java).allowMainThreadQueries().build()
        try {
            val store = ProfileStore(db.nutritionDao(), db.consentDao(), db.eventLogDao()) { 10L }
            store.setConsent(true)
            val marked = UserConstraints.fromCodes(mapOf(ProfileCategory.MEDICAL to listOf("ed_history")))
            store.save(marked)
            store.save(marked) // saved again unchanged: no second entry
            store.save(UserConstraints.NONE) // unmarked: the person leaves ED_CARE only by choice
            val events = db.eventLogDao().list(HotDatabase.LOCAL_USER, SafetyEventKinds.PREFIX)
            assertEquals(listOf(SafetyEventKinds.ED_CARE_ENTER to SafetyEventKinds.REASON_SELF_REPORT), events.map { it.kind to it.payload })
            assertEquals(ProfileStore.SOURCE_PROFILE, events.single().source)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the gate 18+ is asked until age18 is granted`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, HotDatabase::class.java).allowMainThreadQueries().build()
        try {
            val gate = AgeGateStore(db.consentDao()) { 7L }
            assertEquals(AgeGate.ASK, gate.gate.first())
            ProfileStore(db.nutritionDao(), db.consentDao()).setConsent(true) // another consent does not open it
            assertEquals(AgeGate.ASK, gate.gate.first())
            gate.confirmAdult()
            assertEquals(AgeGate.CONFIRMED, gate.gate.first())
            val row = db.consentDao().observeCurrent(HotDatabase.LOCAL_USER).first().single { it.consentId == AgeGateStore.CONSENT_ID }
            assertEquals(AgeGateStore.CONSENT_VERSION, row.consentVersion)
            assertEquals(7L, row.tsUtcMs)
        } finally {
            db.close()
        }
    }
}
