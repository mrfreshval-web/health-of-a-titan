package com.healthofatitan.app.di

import com.healthofatitan.app.core.database.ProfileStore
import com.healthofatitan.app.domain.today.Safety
import com.healthofatitan.app.integration.whoop.week.WeekProfile
import com.healthofatitan.app.integration.whoop.week.WeekSignals
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
import kotlinx.coroutines.flow.first
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

    /**
     * «Неделя» (A52): the crisis silences the Sunday push; StormNet hides skews for 7 days (W-04);
     * the profile of specifics closes weight (`ed_care`, W-07) and the fitness age (pregnancy, heart
     * medication as the flag of beta-blockers, `ed_care`; A52 §2.8).
     */
    @Provides
    fun provideWeekSignals(loop: DailyLoopRepository, checkin: CheckinRepository, profile: ProfileStore): WeekSignals = object : WeekSignals {
        override fun crisis(): Boolean = runCatching { loop.active() }.getOrDefault(false)

        override suspend fun stormShownAt(): Instant? = checkin.prefs.first().stormShownAt?.let(Instant::ofEpochMilli)

        override suspend fun profile(): WeekProfile {
            val c = profile.profile.first().constraints
            return WeekProfile(
                edCare = "ed_history" in c.medical || "underweight_reported" in c.medical,
                pregnancy = "pregnancy" in c.lifeStage,
                betaBlockers = "heart_bp_meds" in c.medical,
            )
        }
    }
}
