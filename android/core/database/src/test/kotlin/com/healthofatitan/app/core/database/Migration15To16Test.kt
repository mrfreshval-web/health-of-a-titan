package com.healthofatitan.app.core.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.entity.NoteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Room 16 (A66 §2.4): 15 → 16 adds `note` and touches nothing else. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class Migration15To16Test {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun open(name: String): HotDatabase =
        Room.databaseBuilder(context, HotDatabase::class.java, name).addMigrations(*Migrations.ALL).allowMainThreadQueries().build()

    @Test
    fun `version 15 gets the notes table and keeps its rows`() = runTest {
        val name = "migration-15-16.db"
        context.getDatabasePath(name).delete()
        open(name).apply { openHelper.writableDatabase }.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("DROP INDEX IF EXISTS `index_note_user_id_day_key`")
            db.execSQL("DROP TABLE IF EXISTS `note`")
            db.execSQL("INSERT INTO event_log (user_id, kind, payload, source, at, day_key) VALUES ('local', 'safety.crisis', '1', 'EVENING', 1, '2026-11-08')")
            db.version = 15
        }
        val db = open(name)
        try {
            db.openHelper.writableDatabase // runs 15 → 16 and validates the schema
            assertEquals(16, db.openHelper.readableDatabase.version)
            assertEquals(1, db.eventLogDao().list(HotDatabase.LOCAL_USER, "").size)
            db.noteDao().insert(NoteEntity(userId = HotDatabase.LOCAL_USER, dayKey = "2026-11-08", text = "Колено ныло после бега", at = 5))
            assertEquals(listOf("Колено ныло после бега"), db.noteDao().observe(HotDatabase.LOCAL_USER).first().map { it.text })
        } finally {
            db.close()
        }
    }
}
