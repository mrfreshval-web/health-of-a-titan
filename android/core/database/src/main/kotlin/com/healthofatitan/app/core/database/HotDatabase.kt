package com.healthofatitan.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.healthofatitan.app.core.database.dao.ConsentDao
import com.healthofatitan.app.core.database.entity.ConsentRecordEntity
import kotlin.reflect.KClass

/**
 * Local encrypted store (A14 §3, ADR-02/13). Only HoT-derived data; raw WHOOP data is never
 * persisted (checked by NoRawWhoopColumnsTest). Each slice adds its tables with a migration.
 */
@Database(
    entities = [ConsentRecordEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class HotDatabase : RoomDatabase() {
    abstract fun consentDao(): ConsentDao

    companion object {
        const val FILE_NAME = "hot.db"

        /** Keep in sync with @Database.entities (used by schema tests; annotation is not runtime-visible). */
        val ENTITIES: List<KClass<*>> = listOf(ConsentRecordEntity::class)
    }
}
