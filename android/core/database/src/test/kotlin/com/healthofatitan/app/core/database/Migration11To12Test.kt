package com.healthofatitan.app.core.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.entity.PathGoalComponentEntity
import com.healthofatitan.app.core.database.entity.PathGoalVersionEntity
import com.healthofatitan.app.core.database.entity.PathHorizonEntity
import com.healthofatitan.app.core.database.entity.PathLetterEntity
import com.healthofatitan.app.core.database.entity.PathMarkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * «Путь» v1 (A54–A57, D-048) on the founder's phone: a version-4 database with the goals of the
 * 12-week path (A42) opens through every migration up to v12 (Week takes 10 → 11). The statuses follow the state model
 * of A56 §5 (REPLACED → RELEASED, DONE → TAKEN, ACTIVE stays), each old goal gets its sphere as a
 * leading component with a Water guard and a version 1 «MIGRATED»; the steps survive; the new
 * tables take components, versions, cards, the horizon, the letter and marks.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class Migration11To12Test {

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
                // the v6 path tables with goals of the 12-week path (A42): MIGRATION_5_6 keeps them (IF NOT EXISTS)
                "CREATE TABLE IF NOT EXISTS `path_goal` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` TEXT NOT NULL, " +
                    "`sphere` TEXT NOT NULL, `start_day` TEXT NOT NULL, `baseline` INTEGER, `stage1` INTEGER NOT NULL, " +
                    "`stage2` INTEGER NOT NULL, `stage3` INTEGER NOT NULL, `status` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                    "`ended_day` TEXT)",
                "CREATE INDEX IF NOT EXISTS `index_path_goal_user_id_status` ON `path_goal` (`user_id`, `status`)",
                "CREATE TABLE IF NOT EXISTS `path_step` (`user_id` TEXT NOT NULL, `goal_id` INTEGER NOT NULL, `day_key` TEXT NOT NULL, " +
                    "`version` TEXT NOT NULL, `done_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `goal_id`, `day_key`))",
                "INSERT INTO path_goal VALUES (1, 'local', 'body', '2026-09-01', 1, 2, 3, 3, 'REPLACED', 10, '2026-10-01')",
                "INSERT INTO path_goal VALUES (2, 'local', 'sleep', '2026-08-01', 3, 4, 5, 5, 'DONE', 20, '2026-10-24')",
                "INSERT INTO path_goal VALUES (3, 'local', 'sleep', '2026-10-02', 3, 4, 5, 5, 'ACTIVE', 30, NULL)",
                "INSERT INTO path_step VALUES ('local', 3, '2026-10-05', 'NORMAL', 31)",
                "INSERT INTO dawn_log VALUES ('local', '2026-11-10', 'hk-ravno-001', 'весло', 'oar', 'STEADY', 1)",
                "INSERT INTO metric_daily VALUES ('local', '2026-11-10', 'SLP', 1, 7.2, 0.3, 0, 1.0, 7.0, 1)",
                "INSERT INTO metric_daily VALUES ('local', '2026-11-11', 'SLP', 1, 6.1, -0.8, -1, 1.0, 6.9, 1)",
                "INSERT INTO training_session VALUES ('local', 'w-1', '2026-11-10', 1080, 45, 'CARDIO', 'ZONE2', 1)",
                "INSERT INTO training_session VALUES ('local', 'w-2', '2026-11-10', 1200, 20, 'STRENGTH', 'MIXED', 1)",
            ).forEach(db::execSQL)
            db.version = 4
        }
    }

    @Test
    fun `the goals of the 12-week path become practical goals with the new statuses`() = runTest {
        val name = "migration-4-path-v1.db"
        createVersion4(name)
        val db = Room.databaseBuilder(context, HotDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            db.openHelper.writableDatabase // runs and validates every migration up to 11 → 12
            assertEquals("hk-ravno-001", db.dawnDao().day("local", "2026-11-10")?.haikuId)
            val path = db.pathDao()
            val goals = path.goals("local").associateBy { it.id }
            assertEquals("RELEASED", goals.getValue(1).status)
            assertEquals("TAKEN", goals.getValue(2).status)
            assertEquals("ACTIVE", goals.getValue(3).status)
            assertEquals("Сон 7 часов", goals.getValue(3).title)
            assertEquals("Тренировки", goals.getValue(1).title)
            assertEquals("2026-12-25", goals.getValue(3).deadline)
            assertEquals("sleep", goals.getValue(3).legacySphere)
            assertEquals("legacy", goals.getValue(3).template)
            assertEquals("ночи от 7 ч из 7|4.0|6.0||sleep_nights", goals.getValue(3).criteria)
            assertEquals(false, goals.getValue(1).releaseToQueue)
            assertEquals(listOf("sleep" to "LEAD", "rest" to "GUARD"), path.components(3).map { it.component to it.role })
            assertEquals(listOf("cardio" to "LEAD", "sleep" to "GUARD"), path.components(1).map { it.component to it.role })
            assertEquals(listOf(true, false), path.components(3).map { it.isLever })
            assertEquals(listOf("MIGRATED"), path.versions(3).map { it.changeType })
            assertEquals("NORMAL", path.step("local", 3, "2026-10-05")?.version)

            // the new tables
            val id = path.saveGoal(
                goals.getValue(3).copy(id = 0, title = "Силы на вечер", status = "DRAFT", legacySphere = null),
                listOf(
                    PathGoalComponentEntity("local", 0, "rest", "LEAD", null, null, null, false, 0),
                    PathGoalComponentEntity("local", 0, "sleep", "GUARD", 3, 2, null, true, 1),
                ),
                PathGoalVersionEntity(userId = "local", goalId = 0, v = 1, snapshot = "title=Силы", changeType = "CREATE", initiator = "PERSON", reason = null, at = 1L),
            )
            assertEquals(listOf("rest", "sleep"), path.components(id).map { it.component })
            assertEquals(1, path.versions(id).size)
            path.upsertHorizon(PathHorizonEntity(id, "local", "sleep", 0, 1L))
            assertEquals("sleep", path.horizon(id)?.transferred)
            path.upsertLetter(PathLetterEntity(id, "local", "Ты дошёл", "MILESTONE_8", "2026-11-11", null))
            assertEquals("Ты дошёл", path.letter(id)?.text)
            path.insertMark(PathMarkEntity("local", id, "sleep", 3, "2026-11-11", "SELF", 1L))
            path.insertMark(PathMarkEntity("local", id, "sleep", 3, "2026-11-12", "SELF", 2L))
            assertEquals(listOf("2026-11-11"), path.observeMarks("local").first().map { it.dayKey })
            path.dropGoal(id)
            assertNull(path.goal(id))
            assertEquals(0, path.components(id).size)
        } finally {
            db.close()
        }
    }
}
