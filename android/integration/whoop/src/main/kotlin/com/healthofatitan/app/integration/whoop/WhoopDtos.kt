package com.healthofatitan.app.integration.whoop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * WHOOP API v2 records, **in memory only** (D-009, A14 §3.2). Only the fields HoT needs are
 * declared; the JSON parser ignores everything else (SpO₂, skin temperature, Recovery %, strain,
 * kJ are never even deserialised — A25 P7, data minimisation). These classes are never stored.
 * Sleep need and the sport name are read for «Сегодня» ([WhoopGlance], memory only) and for the
 * routine of slice 2 ([WhoopRoutineMapper]): only the 28-night median of the baseline need and a
 * STRENGTH/CARDIO class are stored, never the raw values (R-11, D-009).
 * [Факт: developer.whoop.com/api] field names; v2 paths — see WhoopApi.
 */

@Serializable
internal data class WhoopPage<T>(
    val records: List<T> = emptyList(),
    @SerialName("next_token") val nextToken: String? = null,
)

internal const val SCORED = "SCORED"

@Serializable
internal data class WhoopCycle(
    val id: Long,
    val start: String,
    val end: String? = null,
    @SerialName("timezone_offset") val timezoneOffset: String? = null,
    @SerialName("score_state") val scoreState: String? = null,
)

@Serializable
internal data class WhoopRecovery(
    @SerialName("cycle_id") val cycleId: Long? = null,
    @SerialName("sleep_id") val sleepId: String? = null,
    @SerialName("score_state") val scoreState: String? = null,
    val score: Score? = null,
) {
    @Serializable
    internal data class Score(
        @SerialName("resting_heart_rate") val restingHeartRate: Double? = null,
        @SerialName("hrv_rmssd_milli") val hrvRmssdMilli: Double? = null,
    )
}

@Serializable
internal data class WhoopSleep(
    val id: String,
    @SerialName("cycle_id") val cycleId: Long? = null,
    val start: String,
    val end: String,
    @SerialName("timezone_offset") val timezoneOffset: String? = null,
    val nap: Boolean = false,
    @SerialName("score_state") val scoreState: String? = null,
    val score: Score? = null,
) {
    @Serializable
    internal data class Score(
        @SerialName("stage_summary") val stageSummary: StageSummary? = null,
        @SerialName("respiratory_rate") val respiratoryRate: Double? = null,
        @SerialName("sleep_needed") val sleepNeeded: SleepNeeded? = null,
    )

    @Serializable
    internal data class SleepNeeded(
        @SerialName("baseline_milli") val baseline: Long = 0,
        @SerialName("need_from_sleep_debt_milli") val debt: Long = 0,
        @SerialName("need_from_recent_strain_milli") val strain: Long = 0,
        @SerialName("need_from_recent_nap_milli") val nap: Long = 0,
    ) {
        val totalMilli: Long get() = baseline + debt + strain + nap
    }

    @Serializable
    internal data class StageSummary(
        @SerialName("total_light_sleep_time_milli") val light: Long = 0,
        @SerialName("total_slow_wave_sleep_time_milli") val slowWave: Long = 0,
        @SerialName("total_rem_sleep_time_milli") val rem: Long = 0,
    )
}

@Serializable
internal data class WhoopWorkout(
    val id: String,
    val start: String,
    val end: String,
    @SerialName("timezone_offset") val timezoneOffset: String? = null,
    @SerialName("sport_name") val sportName: String? = null,
    @SerialName("sport_id") val sportId: Int? = null,
    @SerialName("score_state") val scoreState: String? = null,
    val score: Score? = null,
) {
    @Serializable
    internal data class Score(
        @SerialName("zone_durations") val zones: Zones? = null,
    )

    @Serializable
    internal data class Zones(
        @SerialName("zone_zero_milli") val zero: Long = 0,
        @SerialName("zone_one_milli") val one: Long = 0,
        @SerialName("zone_two_milli") val two: Long = 0,
        @SerialName("zone_three_milli") val three: Long = 0,
        @SerialName("zone_four_milli") val four: Long = 0,
        @SerialName("zone_five_milli") val five: Long = 0,
    )
}

/** The 28-day window as fetched — lives for the duration of one sync and is then dropped. */
internal data class WhoopWindow(
    val cycles: List<WhoopCycle>,
    val recoveries: List<WhoopRecovery>,
    val sleeps: List<WhoopSleep>,
    val workouts: List<WhoopWorkout>,
)
