package com.healthofatitan.app.di

import android.app.ActivityManager
import android.content.Context
import com.healthofatitan.app.core.account.AccountProfileStore
import com.healthofatitan.app.core.account.DeviceKey
import com.healthofatitan.app.core.account.LocalDataWiper
import com.healthofatitan.app.core.security.DatabaseKeyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Singleton

/**
 * «Выйти», «Выйти везде» and «Удалить аккаунт» wipe the phone (A65 §2.3, §6: one HoT account per
 * install). The keys go first, so nothing left on disk can be opened again even if the process
 * dies half-way; then the system clears all app data and closes the app.
 */
@Module
@InstallIn(SingletonComponent::class)
object AccountWipeModule {
    @Provides
    @Singleton
    fun provideLocalDataWiper(
        @ApplicationContext context: Context,
        databaseKey: DatabaseKeyProvider,
        deviceKey: DeviceKey,
        profile: AccountProfileStore,
    ): LocalDataWiper = LocalDataWiper {
        withContext(Dispatchers.IO) {
            runCatching { profile.clear() }
            runCatching { deviceKey.reset() }
            runCatching { databaseKey.destroy() }
            // Kills the process: the database, Tink keysets, prefs, files and caches are gone.
            context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
        }
    }
}
