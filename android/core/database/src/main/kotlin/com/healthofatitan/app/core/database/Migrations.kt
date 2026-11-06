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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
