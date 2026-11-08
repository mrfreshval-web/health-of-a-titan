package com.healthofatitan.app.integration.whoop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * WHOOP API v2 records, **in memory only** (D-009, A14 §3.2). Only the fields HoT needs are
 * declared; the JSON parser ignores everything else (Recovery % is never even deserialised — A25
 * P7, data minimisation). These classes are never stored.
 * «Сегодня» v3 (A46 §2.3, §3, D-042) adds, still memory only: the cycle's kilojoules and step
 * count, SpO₂ and skin temperature of the recovery, sleep efficiency, consistency and
 * disturbances — shown on «Почему так» and the supports, never written anywhere (R-11). The
 * change of A25 P7 for SpO₂, skin temperature and kJ waits for Agent 7's review.
 * The cycle's day strain is read for the header ring «нагрузка против потолка режима» (D-029):
 * memory only, like the glance, never written to the database, a file or a log (R-11).
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
    val score: Score? = null,
    /** Steps may come on the cycle itself rather than in its score: both are read. [проверить] */
    @SerialName("step_count") val stepCount: Int? = null,
) {
    @Serializable
    internal data class Score(
        /** Day strain 0–21 (WHOOP scale), memory only. */
        val strain: Double? = null,
        /** Energy of the day, kJ (A46 «усердие»: active calories), memory only. */
        val kilojoule: Double? = null,
        /** Steps of the cycle (WHOOP, since 23.09.2026; A46 «движение»), memory only. [проверить на первом входе] */
        @SerialName("step_count") val stepCount: Int? = null,
    )
}

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
        /** A46 §3 «Дыхание», memory only. */
        @SerialName("spo2_percentage") val spo2Percentage: Double? = null,
        @SerialName("skin_temp_celsius") val skinTempCelsius: Double? = null,
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
        /** A46 §3 «Сон», memory only. */
        @SerialName("sleep_efficiency_percentage") val efficiency: Double? = null,
        @SerialName("sleep_consistency_percentage") val consistency: Double? = null,
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
        /** Awake in bed: only for the composition of the night on «Сегодня» (Y2), memory only. */
        @SerialName("total_awake_time_milli") val awake: Long = 0,
        /** Wake-ups of the night (A46 §3 «пробуждения»), memory only. */
        @SerialName("disturbance_count") val disturbances: Int? = null,
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
        /** 0–100: share of the workout with heart-rate data (A35 §4 rule 3, «без браслета»). */
        @SerialName("percent_recorded") val percentRecorded: Double? = null,
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

/** `user/profile/basic`: only the first name is read (A65 §2.4); e-mail and last name are never parsed. */
@Serializable
internal data class WhoopBasicProfile(@SerialName("first_name") val firstName: String? = null)

/** `user/measurement/body`: height and max heart rate; the weight is never parsed (D-043). */
@Serializable
internal data class WhoopBody(
    @SerialName("height_meter") val heightMeter: Double? = null,
    @SerialName("max_heart_rate") val maxHeartRate: Int? = null,
)

/** What the profile takes from WHOOP; held in memory only until [WhoopProfileImport] stores it. */
internal data class WhoopProfileFacts(val firstName: String?, val heightCm: Int?, val maxPulse: Int?)
