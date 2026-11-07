package com.healthofatitan.app.integration.whoop

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * What «Сегодня» shows from WHOOP before the personal norm is ready (and next to it later):
 * last night, its recovery inputs and the last workout. **Raw WHOOP values, memory only**
 * (R-11, D-009): built from the window during a sync, kept in a StateFlow for the process
 * lifetime, never written to the database, a file or a log. After a restart it is rebuilt by
 * the next sync; until then «Сегодня» falls back to stored derived metrics.
 */
data class WhoopGlance(
    val fetchedAt: Long,
    val sleep: SleepGlance?,
    val recovery: RecoveryGlance?,
    val workout: WorkoutGlance?,
    /** Today's WHOOP day strain (the latest cycle), 0–21; memory only (D-029 header ring). */
    val dayStrain: StrainGlance? = null,
)

/** The day strain of the latest WHOOP cycle and the cycle's start. */
data class StrainGlance(val cycleStart: Instant, val strain: Double)

/**
 * Last main sleep: [hours] asleep (light + deep + REM), [needHours] = WHOOP's sleep need, and the
 * composition of the night in minutes (UX review 119, Y2: the sleep chip opens it). Shown only,
 * never used by the metrics (A25 P7: no stages in the norm); memory only like the rest.
 */
data class SleepGlance(
    val wakeDay: LocalDate,
    val end: Instant,
    val hours: Double,
    val needHours: Double?,
    val stages: SleepStages? = null,
)

/** Minutes of light, deep (slow wave) and REM sleep and of waking in bed (WHOOP stage summary). */
data class SleepStages(val lightMin: Int, val deepMin: Int, val remMin: Int, val awakeMin: Int)

/** Recovery inputs of that night: HRV (rMSSD, ms) and resting heart rate. No Recovery % (A25 P7). */
data class RecoveryGlance(val hrvMs: Double?, val restingHr: Double?)

/** Latest scored workout: when, how long, which sport (WHOOP's name), and its load (TRIMP). */
data class WorkoutGlance(val start: Instant, val minutes: Int, val sport: String?, val trimp: Double?)

internal object WhoopGlanceMapper {

    private fun minutes(milli: Long): Int = (milli / 60_000L).toInt()

    fun from(window: WhoopWindow, fallbackZone: ZoneId, fetchedAt: Long): WhoopGlance {
        val sleep = window.sleeps
            .filter { !it.nap && it.scoreState == SCORED && it.score?.stageSummary != null }
            .mapNotNull { s -> parse(s.end)?.let { end -> s to end } }
            .maxByOrNull { it.second }
        val sleepGlance = sleep?.let { (s, end) ->
            val st = s.score!!.stageSummary!!
            val hours = (st.light + st.slowWave + st.rem) / 3_600_000.0
            val need = s.score?.sleepNeeded?.totalMilli?.takeIf { it > 0 }?.let { it / 3_600_000.0 }
            val stages = SleepStages(minutes(st.light), minutes(st.slowWave), minutes(st.rem), minutes(st.awake))
            SleepGlance(end.atZone(zoneOf(s.timezoneOffset, fallbackZone)).toLocalDate(), end, hours, need, stages)
        }
        val recovery = sleep?.let { (s, _) ->
            window.recoveries.firstOrNull { it.sleepId == s.id && it.scoreState == SCORED }?.score?.let {
                RecoveryGlance(it.hrvRmssdMilli?.takeIf { v -> v > 0 }, it.restingHeartRate?.takeIf { v -> v > 0 })
            }
        }
        val workout = window.workouts
            .filter { it.scoreState == SCORED }
            .mapNotNull { w -> parse(w.start)?.let { start -> Triple(w, start, parse(w.end)) } }
            .maxByOrNull { it.second }
            ?.let { (w, start, end) ->
                val minutes = end?.let { Duration.between(start, it).toMinutes().toInt() }?.coerceAtLeast(0) ?: 0
                val z = w.score?.zones
                val trimp = z?.let { (it.one * 1 + it.two * 2 + it.three * 3 + it.four * 4 + it.five * 5) / 60_000.0 }
                WorkoutGlance(start, minutes, w.sportName?.takeIf { it.isNotBlank() }, trimp)
            }
        val strain = window.cycles
            .mapNotNull { c -> c.score?.strain?.takeIf { it >= 0.0 }?.let { v -> parse(c.start)?.let { StrainGlance(it, v) } } }
            .maxByOrNull { it.cycleStart }
        return WhoopGlance(fetchedAt, sleepGlance, recovery, workout, strain)
    }

    private fun parse(text: String): Instant? =
        runCatching { Instant.parse(text) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()

    private fun zoneOf(offset: String?, fallback: ZoneId): ZoneId =
        offset?.let { runCatching { ZoneOffset.of(it) }.getOrNull() } ?: fallback
}
