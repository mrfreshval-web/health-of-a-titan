package com.healthofatitan.app.di

import com.healthofatitan.app.core.database.ProfileStore
import com.healthofatitan.app.core.model.safety.SafetyEventKinds
import com.healthofatitan.app.domain.safety.Restriction
import com.healthofatitan.app.domain.safety.SafetyConsumer
import com.healthofatitan.app.domain.today.Safety
import com.healthofatitan.app.integration.whoop.safety.SafetyBus
import com.healthofatitan.app.integration.whoop.week.WeekProfile
import com.healthofatitan.app.integration.whoop.week.WeekSignals
import com.healthofatitan.app.feature.checkin.CheckinRepository
import com.healthofatitan.app.integration.whoop.day.TodaySafetySource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * What overrides «Сегодня» (A46 §4.8), bound where both sources are known: the crisis from the
 * safety bus (CrisisGuard ≥ 2 in the last 72 h, A07 → `event_log` → `SafetyState`, A60 §3) and the
 * storm net «Как ты на самом деле?» opened today (A44 §6.1, `CARE`; it joins the bus as STORM in
 * a later slice). SAF-01 is not stored by the body map yet: it waits for its slice.
 */
@Module
@InstallIn(SingletonComponent::class)
object TodayModule {

    @Provides
    fun provideTodaySafety(bus: SafetyBus, checkin: CheckinRepository): TodaySafetySource = TodaySafetySource { day ->
        combine(bus.state, checkin.prefs) { state, prefs ->
            val severe = Restriction.HELP_FIRST in state.restrictions(SafetyConsumer.TODAY)
            val care = prefs.stormShownAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() == day } == true
            when {
                severe -> Safety.CRISIS
                care -> Safety.CARE
                else -> Safety.NONE
            }
        }
    }

    /**
     * «Неделя» (A52): the crisis of the safety bus silences the Sunday push; StormNet hides skews
     * for 7 days (W-04); `ED_CARE` of the bus and the profile of specifics close weight (W-07) and
     * the fitness age (pregnancy, heart medication as the flag of beta-blockers; A52 §2.8). After
     * the person leaves `ED_CARE` the bus keeps weight closed until it is taken back on step 3
     * (D-057): the strictest of the two wins.
     */
    @Provides
    fun provideWeekSignals(bus: SafetyBus, checkin: CheckinRepository, profile: ProfileStore): WeekSignals = object : WeekSignals {
        // An unreadable journal is not «no crisis»: the push stays silent (the strictest wins).
        override suspend fun crisis(): Boolean = runCatching { Restriction.HELP_FIRST in bus.current().restrictions(SafetyConsumer.WEEK) }.getOrDefault(true)

        override suspend fun stormShownAt(): Instant? = checkin.prefs.first().stormShownAt?.let(Instant::ofEpochMilli)

        override suspend fun profile(): WeekProfile {
            val c = profile.profile.first().constraints
            val weightClosed = runCatching { Restriction.HIDE_WEIGHT in bus.current().restrictions(SafetyConsumer.WEEK) }.getOrDefault(true)
            return WeekProfile(
                edCare = weightClosed || c.medical.any { it in SafetyEventKinds.ED_PROFILE_CODES },
                pregnancy = "pregnancy" in c.lifeStage,
                betaBlockers = "heart_bp_meds" in c.medical,
            )
        }
    }
}
