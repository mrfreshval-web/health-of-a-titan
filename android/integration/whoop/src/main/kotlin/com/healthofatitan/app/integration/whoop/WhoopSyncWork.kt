package com.healthofatitan.app.integration.whoop

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.healthofatitan.app.domain.engine.MorningPolicy
import com.healthofatitan.app.integration.whoop.day.MorningCoordinator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.LocalDateTime

/**
 * Background sync (A18 И1.2 ③). Runs only with a network; without one WorkManager waits and
 * starts it as soon as the phone is online again — «Обновлю, когда будет связь».
 * Dependencies come from the Hilt graph through an entry point (no custom WorkerFactory).
 */
class WhoopSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun whoopSyncer(): WhoopSyncer
    }

    override suspend fun doWork(): Result {
        val syncer = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).whoopSyncer()
        return when (syncer.sync(SyncTrigger.BACKGROUND)) {
            is SyncOutcome.Done, is SyncOutcome.Stop -> Result.success()
            is SyncOutcome.Retry -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 8
    }
}

/**
 * The local morning watch (A34 interim path, until the server leads the morning, D-025): from
 * 04:30 it syncs WHOOP, rebuilds the day and posts the morning push once the night is scored
 * (not before 06:00, not after 12:00); otherwise it checks again in 15 minutes. Then it waits
 * for the next morning. Doze may delay it — the fallback is the plan on app open.
 */
class MorningWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun whoopSyncer(): WhoopSyncer

        fun morningCoordinator(): MorningCoordinator

        fun whoopSyncScheduler(): WhoopSyncScheduler
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val step = runCatching {
            if (MorningPolicy.wantsData(LocalDateTime.now())) deps.whoopSyncer().sync(SyncTrigger.BACKGROUND)
            deps.morningCoordinator().onDataChanged()
        }.getOrNull()
        val delay = (step as? MorningPolicy.Step.Wait)?.delay ?: Duration.ofMinutes(MorningPolicy.RETRY_MIN)
        deps.whoopSyncScheduler().scheduleMorningWatch(delay, fromWorker = true)
        return Result.success()
    }
}

/** Schedules WHOOP syncs: now (on open, after login, «Синхронизировать») and every 3 hours. */
class WhoopSyncScheduler(private val context: Context) {

    private val workManager get() = WorkManager.getInstance(context)

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<WhoopSyncWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<WhoopSyncWorker>(Duration.ofHours(3))
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(5))
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** The morning watch chain; [fromWorker] appends to the running link instead of keeping the old one. */
    fun scheduleMorningWatch(delay: Duration, fromWorker: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<MorningWatchWorker>()
            .setInitialDelay(delay)
            .setConstraints(online)
            .addTag(TAG)
            .build()
        val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
        workManager.enqueueUniqueWork(MORNING, policy, request)
    }

    /** On app open and after login: make sure tomorrow's (or this) morning is watched. */
    fun ensureMorningWatch() {
        val step = MorningPolicy.next(LocalDateTime.now(), planReadyToday = false, notifiedToday = false)
        scheduleMorningWatch((step as? MorningPolicy.Step.Wait)?.delay ?: Duration.ZERO)
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG)
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val TAG = "whoop-sync"
        const val NOW = "whoop-sync-now"
        const val PERIODIC = "whoop-sync-periodic"
        const val MORNING = "whoop-morning-watch"
    }
}
