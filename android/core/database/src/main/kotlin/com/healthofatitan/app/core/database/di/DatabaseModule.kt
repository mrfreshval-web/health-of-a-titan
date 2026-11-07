package com.healthofatitan.app.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.Migrations
import com.healthofatitan.app.core.database.ProfileSettingsStore
import com.healthofatitan.app.core.database.ProfileStore
import com.healthofatitan.app.core.database.dao.ConsentDao
import com.healthofatitan.app.core.database.dao.DailyLoopDao
import com.healthofatitan.app.core.database.dao.DawnDao
import com.healthofatitan.app.core.database.dao.DayDao
import com.healthofatitan.app.core.database.dao.MetricDao
import com.healthofatitan.app.core.database.dao.NutritionDao
import com.healthofatitan.app.core.database.dao.CheckinDao
import com.healthofatitan.app.core.database.dao.PathDao
import com.healthofatitan.app.core.database.dao.PersonProfileDao
import com.healthofatitan.app.core.database.dao.TrainingLogDao
import com.healthofatitan.app.core.database.dao.TodayDao
import com.healthofatitan.app.core.database.dao.WeekDao
import com.healthofatitan.app.core.security.DatabaseKeyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * SQLCipher-encrypted Room database (A14 §3.1): key K_db from Android Keystore,
     * `PRAGMA secure_delete = ON` on every open. Opening the file with plain `sqlite3`
     * fails with "file is not a database".
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyProvider: DatabaseKeyProvider,
    ): HotDatabase {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(keyProvider.getOrCreateKey())
        return Room.databaseBuilder(context, HotDatabase::class.java, HotDatabase.FILE_NAME)
            .openHelperFactory(factory)
            .addMigrations(*Migrations.ALL)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.query("PRAGMA secure_delete = ON").use { it.moveToFirst() }
                    }
                },
            )
            .build()
    }

    @Provides
    fun provideConsentDao(db: HotDatabase): ConsentDao = db.consentDao()

    @Provides
    fun provideMetricDao(db: HotDatabase): MetricDao = db.metricDao()

    @Provides
    fun provideDayDao(db: HotDatabase): DayDao = db.dayDao()

    @Provides
    fun provideDawnDao(db: HotDatabase): DawnDao = db.dawnDao()

    @Provides
    fun provideDailyLoopDao(db: HotDatabase): DailyLoopDao = db.dailyLoopDao()

    @Provides
    fun providePathDao(db: HotDatabase): PathDao = db.pathDao()

    @Provides
    fun provideNutritionDao(db: HotDatabase): NutritionDao = db.nutritionDao()

    @Provides
    fun provideCheckinDao(db: HotDatabase): CheckinDao = db.checkinDao()

    /** The shared profile of specifics (S4): nutrition and training read the same store. */
    @Provides
    @Singleton
    fun provideProfileStore(db: HotDatabase): ProfileStore = ProfileStore(db.nutritionDao(), db.consentDao())

    @Provides
    fun provideTrainingLogDao(db: HotDatabase): TrainingLogDao = db.trainingLogDao()

    @Provides
    fun provideTodayDao(db: HotDatabase): TodayDao = db.todayDao()

    @Provides
    fun provideWeekDao(db: HotDatabase): WeekDao = db.weekDao()

    @Provides
    fun providePersonProfileDao(db: HotDatabase): PersonProfileDao = db.personProfileDao()

    /** The profile «Я» (A61, D-055): address form, year of birth, sex for calculations. */
    @Provides
    @Singleton
    fun provideProfileSettings(db: HotDatabase): ProfileSettingsStore = ProfileSettingsStore(db.personProfileDao())
}
