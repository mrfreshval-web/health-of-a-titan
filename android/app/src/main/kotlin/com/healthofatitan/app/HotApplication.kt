package com.healthofatitan.app

import android.app.Application
import android.util.Log
import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.integration.whoop.day.DailyLoopRepository
import com.healthofatitan.app.integration.whoop.day.ReturnWatchWorker
import com.healthofatitan.app.integration.whoop.day.MorningCoordinator
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

    @Inject
    lateinit var morning: dagger.Lazy<MorningCoordinator>

    @Inject
    lateinit var dailyLoop: dagger.Lazy<DailyLoopRepository>

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Create/open the encrypted database off the main thread (slice 0 acceptance:
        // the file exists and is not readable by plain sqlite3). No data is logged.
        appScope.launch {
            runCatching { database.get().openHelper.writableDatabase }
                .onFailure { Log.e(TAG, "Encrypted database could not be opened: ${it.javaClass.simpleName}") }
        }
        // Slice 2: every successful WHOOP sync (app, WorkManager, morning watch) rebuilds the day
        // plan and, in the morning window, posts the push once (A09, A11 §2).
        appScope.launch { runCatching { morning.get().start(appScope) } }
        // Stream A: a crisis level ≥ 2 of the last 72 h keeps «Рассвет» without a haiku; the warm
        // nudge after a pause is checked a few times a day (A18 И4.3, И4.4).
        appScope.launch { runCatching { dailyLoop.get().warmUp() } }
        runCatching { ReturnWatchWorker.ensure(this) }
    }

    private companion object {
        const val TAG = "HotApplication"
    }
}
