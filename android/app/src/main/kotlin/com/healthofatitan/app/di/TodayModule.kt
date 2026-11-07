package com.healthofatitan.app.di

import com.healthofatitan.app.domain.today.Safety
import com.healthofatitan.app.feature.checkin.CheckinRepository
import com.healthofatitan.app.integration.whoop.day.DailyLoopRepository
import com.healthofatitan.app.integration.whoop.day.TodaySafetySource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.ZoneId

/**
 * What overrides «Сегодня» (A46 §4.8), bound where both sources are known: CrisisGuard ≥ 2 in the
 * last 72 h (A07, the daily loop) and the storm net «Как ты на самом деле?» opened today (A44 §6.1,
 * `CARE`). SAF-01 is not stored by the body map yet: it waits for its slice.
 */
@Module
@InstallIn(SingletonComponent::class)
object TodayModule {

    @Provides
    fun provideTodaySafety(loop: DailyLoopRepository, checkin: CheckinRepository): TodaySafetySource = TodaySafetySource { day ->
        val crisis = flow {
            while (true) {
                emit(runCatching { loop.active() }.getOrDefault(false))
                delay(60_000L)
            }
        }.distinctUntilChanged()
        combine(crisis, checkin.prefs) { severe, prefs ->
            val care = prefs.stormShownAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() == day } == true
            when {
                severe -> Safety.CRISIS
                care -> Safety.CARE
                else -> Safety.NONE
            }
        }
    }
}
