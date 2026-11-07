package com.healthofatitan.app.integration.whoop.week

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration

/**
 * The one push of «Неделя» (A52 §2.7): every hour WorkManager asks [WeekRepository.pushIfDue]; the
 * rule ([com.healthofatitan.app.domain.today.week.WeekPush]) lets it out once, on Sunday evening.
 */
class WeekPushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun week(): WeekRepository

        fun weekNotifier(): WeekNotifier
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        runCatching { deps.week().pushIfDue(deps.weekNotifier()) }
        return Result.success()
    }

    companion object {
        private const val WORK = "week-push"

        fun ensure(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeekPushWorker>(Duration.ofHours(1)).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
