package com.healthofatitan.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations. Every released schema must upgrade without data loss: the founder installs
 * each build over the previous one (A14 ADR-07). MigrationTest validates each step against the
 * schema Room expects.
 */
object Migrations {

    /** Slice 1: derived metrics, personal norm, sync status (A14 §3.2). No raw WHOOP columns. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
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
        }
    }

    /**
     * Slice 2: the day plan (`DayState`, `PlanItem`), WHOOP training sessions classified by HoT,
     * the routine derived from WHOOP (A09 §7a) and the evening deck. No raw WHOOP columns.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `day_plan` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, `regime` TEXT NOT NULL, " +
                    "`regime_computed` TEXT NOT NULL, `points` INTEGER NOT NULL, `signals` TEXT NOT NULL, `flags` TEXT NOT NULL, " +
                    "`fired_rules` TEXT NOT NULL, `rule_set` TEXT NOT NULL, `headline` TEXT NOT NULL, `summary` TEXT NOT NULL, " +
                    "`bedtime_min` INTEGER NOT NULL, `caffeine_cutoff_min` INTEGER NOT NULL, `wind_down_min` INTEGER NOT NULL, " +
                    "`built_at` INTEGER NOT NULL, `notified_at` INTEGER, PRIMARY KEY(`user_id`, `day_key`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `plan_item` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, `item_id` TEXT NOT NULL, " +
                    "`rule_ref` TEXT NOT NULL, `kind` TEXT NOT NULL, `slot_start` INTEGER, `slot_end` INTEGER, `title` TEXT NOT NULL, " +
                    "`detail` TEXT, `sort_order` INTEGER NOT NULL, `status` TEXT NOT NULL, `status_at` INTEGER, `session_ref` TEXT, " +
                    "PRIMARY KEY(`user_id`, `day_key`, `item_id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `training_session` (`user_id` TEXT NOT NULL, `source_id` TEXT NOT NULL, " +
                    "`day_key` TEXT NOT NULL, `start_min` INTEGER NOT NULL, `duration_min` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                    "`intensity` TEXT NOT NULL, `computed_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `source_id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `routine_anchor` (`user_id` TEXT NOT NULL, `anchor_id` TEXT NOT NULL, " +
                    "`value_min` INTEGER, `day_key` TEXT, `source` TEXT NOT NULL, `computed_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`user_id`, `anchor_id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `evening_check` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, " +
                    "`alcohol` INTEGER, `body` TEXT, `answered_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `day_key`))",
            )
        }
    }

    /** «Титаниум»: the dawn log — mornings shown and the haiku history (A37 §4). No health values. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `dawn_log` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, `haiku_id` TEXT, " +
                    "`text_key` TEXT, `image` TEXT, `mode` TEXT NOT NULL, `shown_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`user_id`, `day_key`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dawn_log_user_id_haiku_id` ON `dawn_log` (`user_id`, `haiku_id`)")
        }
    }

    /**
     * Stream A «Каждый день»: «Закрыть день» answers on `evening_check` (A18 И4.1), the return log
     * (И4.4) and safety events without any text (A07 §4.7, И4.3). No health values, no raw WHOOP.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `evening_check` ADD COLUMN `training` TEXT")
            db.execSQL("ALTER TABLE `evening_check` ADD COLUMN `food` TEXT")
            db.execSQL("ALTER TABLE `evening_check` ADD COLUMN `plan_helped` INTEGER")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `return_log` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, `at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `day_key`, `kind`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `safety_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, `at` INTEGER NOT NULL, `level` INTEGER NOT NULL, " +
                    "`reason` TEXT, `source` TEXT NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_safety_event_user_id_at` ON `safety_event` (`user_id`, `at`)")
        }
    }

    /**
     * Stream Г «Путь» (D-033, A42 §3): the 12-week goal and the user's steps of the day. Process
     * goals only («N days of 7»), no raw WHOOP values. Tables are prefixed `path_`.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `path_goal` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` TEXT NOT NULL, " +
                    "`sphere` TEXT NOT NULL, `start_day` TEXT NOT NULL, `baseline` INTEGER, `stage1` INTEGER NOT NULL, " +
                    "`stage2` INTEGER NOT NULL, `stage3` INTEGER NOT NULL, `status` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                    "`ended_day` TEXT)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_path_goal_user_id_status` ON `path_goal` (`user_id`, `status`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `path_step` (`user_id` TEXT NOT NULL, `goal_id` INTEGER NOT NULL, `day_key` TEXT NOT NULL, " +
                    "`version` TEXT NOT NULL, `done_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `goal_id`, `day_key`))",
            )
        }
    }

    /**
     * Stream C «Еда»: the profile of specifics (S4, per category, shared with training) and the
     * evening one-tap food mark (A41 §6.5, §10). Encrypted like everything else (SQLCipher).
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `nutrition_profile` (`user_id` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                    "`codes` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `category`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `nutrition_evening` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, " +
                    "`mark` TEXT NOT NULL, `answered_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `day_key`))",
            )
        }
    }

    /**
     * Stream B «Тренировка» (A40 §7): the log of workouts done in the HoT player and their sets.
     * New tables only, prefixed `training_`; nothing existing changes.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `training_workout` (`user_id` TEXT NOT NULL, `workout_id` TEXT NOT NULL, " +
                    "`day_key` TEXT NOT NULL, `template_ref` TEXT NOT NULL, `mode` TEXT NOT NULL, `lighter` INTEGER NOT NULL, " +
                    "`started_at` INTEGER NOT NULL, `finished_at` INTEGER NOT NULL, `active_s` INTEGER NOT NULL, " +
                    "`sets_done` INTEGER NOT NULL, `sets_skipped` INTEGER NOT NULL, `sets_planned` INTEGER NOT NULL, " +
                    "`rpe_median` INTEGER, `felt_bad` INTEGER NOT NULL, `stretch_done` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`user_id`, `workout_id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_workout_user_id_day_key` ON `training_workout` (`user_id`, `day_key`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `training_set` (`user_id` TEXT NOT NULL, `workout_id` TEXT NOT NULL, " +
                    "`step_index` INTEGER NOT NULL, `exercise_id` TEXT NOT NULL, `block_type` TEXT NOT NULL, " +
                    "`set_no` INTEGER NOT NULL, `side` TEXT, `reps` TEXT, `seconds` INTEGER NOT NULL, " +
                    "`skipped` INTEGER NOT NULL, `rpe` INTEGER, PRIMARY KEY(`user_id`, `workout_id`, `step_index`))",
            )
        }
    }

    /**
     * State cards (A44 v0.2, D-036): answers of the decks, the S5 «Только ты» layer in its own
     * table (payload encrypted with K_field), the morning (wake, reveal, ritual), the discoveries
     * and local usage metrics. New tables only, prefixed `checkin_`; no raw WHOOP values.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `checkin_answer` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, `card` TEXT NOT NULL, " +
                    "`phase` TEXT NOT NULL, `level` INTEGER, `choices` TEXT NOT NULL, `count` INTEGER, `skipped` INTEGER NOT NULL, " +
                    "`answered_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `day_key`, `card`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `checkin_private` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, `card` TEXT NOT NULL, " +
                    "`payload` BLOB NOT NULL, `answered_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `day_key`, `card`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `checkin_day` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, `wake_done_at` INTEGER, " +
                    "`quick` INTEGER NOT NULL, `you_level` INTEGER, `data_level` INTEGER, `reveal_seen_at` INTEGER, " +
                    "`ritual_done_at` INTEGER, PRIMARY KEY(`user_id`, `day_key`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `checkin_discovery` (`user_id` TEXT NOT NULL, `pair_id` TEXT NOT NULL, `n1` INTEGER NOT NULL, " +
                    "`n0` INTEGER NOT NULL, `effect` REAL NOT NULL, `ci_low` REAL NOT NULL, `ci_high` REAL NOT NULL, `method` TEXT NOT NULL, " +
                    "`shown_at` INTEGER NOT NULL, `sensitivity` TEXT NOT NULL, PRIMARY KEY(`user_id`, `pair_id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `checkin_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` TEXT NOT NULL, " +
                    "`day_key` TEXT NOT NULL, `phase` TEXT NOT NULL, `card` TEXT, `kind` TEXT NOT NULL, `duration_ms` INTEGER NOT NULL, " +
                    "`at` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_checkin_event_user_id_day_key` ON `checkin_event` (`user_id`, `day_key`)")
        }
    }

    /**
     * «Сегодня» v3 (A46 v0.3, D-042): water, meals and the tasks of the day as marked by hand.
     * One new table; nothing existing changes; no WHOOP values.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `today_mark` (`user_id` TEXT NOT NULL, `day_key` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`ref` TEXT NOT NULL, `count` INTEGER NOT NULL, `goal` TEXT, `at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`user_id`, `day_key`, `kind`, `ref`))",
            )
        }
    }

    /**
     * «Путь» v1 (A54–A57, D-046…D-048): the practical goal with its components, versions, change
     * cards, the Titan of the horizon, the letter and the marks. `path_goal` is rebuilt: the v10
     * goals of the 12-week path keep their id, start and history; their status follows the state
     * model of A56 §5 (REPLACED → RELEASED, DONE → TAKEN), the sphere stays in `legacy_sphere` and
     * becomes two components (the sphere leads, Water guards), with version 1 «MIGRATED».
     * Note: the module «Неделя» may take 10 → 11 too — whoever merges second renumbers.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `path_goal_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `why` TEXT, `be_name` TEXT, `kind` TEXT NOT NULL, `template` TEXT, `start_day` TEXT NOT NULL, " +
                    "`deadline` TEXT NOT NULL, `stage` INTEGER NOT NULL, `mode` TEXT NOT NULL, `status` TEXT NOT NULL, `criteria` TEXT NOT NULL, " +
                    "`obstacle` TEXT, `if_then` TEXT, `version` INTEGER NOT NULL, `paused_days` INTEGER NOT NULL, `pause_from` TEXT, " +
                    "`pause_reason` TEXT, `pause_until` TEXT, `taken_at` INTEGER NOT NULL, `release_to_queue` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, `ended_day` TEXT, `legacy_sphere` TEXT)",
            )
            db.execSQL(
                "INSERT INTO `path_goal_new` (`id`, `user_id`, `title`, `why`, `be_name`, `kind`, `template`, `start_day`, `deadline`, " +
                    "`stage`, `mode`, `status`, `criteria`, `obstacle`, `if_then`, `version`, `paused_days`, `pause_from`, `pause_reason`, " +
                    "`pause_until`, `taken_at`, `release_to_queue`, `created_at`, `ended_day`, `legacy_sphere`) " +
                    "SELECT `id`, `user_id`, " +
                    "CASE `sphere` WHEN 'sleep' THEN 'Сон 7 часов' WHEN 'body' THEN 'Тренировки' ELSE 'Цель на 12 недель' END, " +
                    "NULL, NULL, 'DO', 'legacy', `start_day`, date(`start_day`, '+84 days'), 1, 'BY_DATE', " +
                    "CASE `status` WHEN 'REPLACED' THEN 'RELEASED' WHEN 'DONE' THEN 'TAKEN' ELSE `status` END, " +
                    "CASE `sphere` WHEN 'sleep' THEN 'ночи от 7 ч из 7' ELSE 'дни с тренировкой из 7' END || '|' || " +
                    "`stage1` || '.0|' || min(`stage3` + 1, 7) || '.0||' || CASE `sphere` WHEN 'sleep' THEN 'sleep_nights' ELSE 'runs' END, " +
                    "NULL, NULL, 1, 0, NULL, NULL, NULL, `created_at`, 0, `created_at`, `ended_day`, `sphere` FROM `path_goal`",
            )
            db.execSQL("DROP TABLE `path_goal`")
            db.execSQL("ALTER TABLE `path_goal_new` RENAME TO `path_goal`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_path_goal_user_id_status` ON `path_goal` (`user_id`, `status`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `path_goal_component` (`user_id` TEXT NOT NULL, `goal_id` INTEGER NOT NULL, " +
                    "`component` TEXT NOT NULL, `role` TEXT NOT NULL, `target_mark` INTEGER, `start_mark` INTEGER, `safe_version` TEXT, " +
                    "`is_lever` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`goal_id`, `component`))",
            )
            db.execSQL(
                "INSERT INTO `path_goal_component` SELECT `user_id`, `id`, CASE `legacy_sphere` WHEN 'sleep' THEN 'sleep' ELSE 'cardio' END, " +
                    "'LEAD', NULL, NULL, NULL, 1, 0 FROM `path_goal`",
            )
            db.execSQL(
                "INSERT INTO `path_goal_component` SELECT `user_id`, `id`, CASE `legacy_sphere` WHEN 'sleep' THEN 'rest' ELSE 'sleep' END, " +
                    "'GUARD', NULL, NULL, NULL, 0, 1 FROM `path_goal`",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `path_goal_version` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` TEXT NOT NULL, " +
                    "`goal_id` INTEGER NOT NULL, `v` INTEGER NOT NULL, `snapshot` TEXT NOT NULL, `change_type` TEXT NOT NULL, " +
                    "`initiator` TEXT NOT NULL, `reason` TEXT, `at` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_path_goal_version_goal_id_v` ON `path_goal_version` (`goal_id`, `v`)")
            db.execSQL(
                "INSERT INTO `path_goal_version` (`user_id`, `goal_id`, `v`, `snapshot`, `change_type`, `initiator`, `reason`, `at`) " +
                    "SELECT `user_id`, `id`, 1, '', 'MIGRATED', 'PERSON', NULL, `created_at` FROM `path_goal`",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `path_change_proposal` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` TEXT NOT NULL, " +
                    "`goal_id` INTEGER NOT NULL, `from_version` INTEGER NOT NULL, `change_type` TEXT NOT NULL, `before` TEXT NOT NULL, " +
                    "`after` TEXT NOT NULL, `effects` TEXT NOT NULL, `initiator` TEXT NOT NULL, `soft` INTEGER NOT NULL, " +
                    "`suggest_talk` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `expires_at` INTEGER NOT NULL, `state` TEXT NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_path_change_proposal_goal_id_state` ON `path_change_proposal` (`goal_id`, `state`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `path_horizon` (`goal_id` INTEGER NOT NULL, `user_id` TEXT NOT NULL, `transferred` TEXT NOT NULL, " +
                    "`shown_step` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`goal_id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `path_letter` (`goal_id` INTEGER NOT NULL, `user_id` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                    "`open_at` TEXT NOT NULL, `written_on` TEXT NOT NULL, `opened_at` INTEGER, PRIMARY KEY(`goal_id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `path_mark` (`user_id` TEXT NOT NULL, `goal_id` INTEGER NOT NULL, `component` TEXT NOT NULL, " +
                    "`mark` INTEGER NOT NULL, `day_key` TEXT NOT NULL, `source` TEXT NOT NULL, `at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`goal_id`, `component`, `mark`))",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
}
