package com.healthofatitan.app

import android.app.Application
import android.util.Log
import com.healthofatitan.app.core.database.HotDatabase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HotApplication : Application() {

    @Inject
    lateinit var database: dagger.Lazy<HotDatabase>

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Create/open the encrypted database off the main thread (slice 0 acceptance:
        // the file exists and is not readable by plain sqlite3). No data is logged.
        appScope.launch {
            runCatching { database.get().openHelper.writableDatabase }
                .onFailure { Log.e(TAG, "Encrypted database could not be opened: ${it.javaClass.simpleName}") }
        }
    }

    private companion object {
        const val TAG = "HotApplication"
    }
}
