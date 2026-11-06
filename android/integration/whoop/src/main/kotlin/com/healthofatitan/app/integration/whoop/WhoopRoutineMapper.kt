package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.domain.engine.Intensity
import com.healthofatitan.app.domain.engine.NightPoint
import com.healthofatitan.app.domain.engine.Session
import com.healthofatitan.app.domain.engine.SessionKind
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * WHOOP window → what the day engine keeps (slice 2, A09 §7a, A35 §4). Pure, tested on JVM.
 *
 * - **Nights:** local wake and sleep-start times, WHOOP's baseline sleep need (only its 28-night
 *   median is stored, as a routine anchor) and the UTC offset of the night (home time zone).
 * - **Sessions:** start (local minutes of the HoT day), duration, STRENGTH/CARDIO by the sport
 *   name and an intensity class from the share of time in heart-rate zones. Zones, heart rate,
 *   strain and kJ stay in memory (R-11, D-009).
 */
internal object WhoopRoutineMapper {

    data class Routine(val nights: List<NightPoint>, val sessions: List<Session>)

    private val strengthWords = listOf("weight", "strength", "powerlift", "functional", "crossfit", "bodybuild", "kettlebell")

    /** WHOOP's Strength Trainer sport (A35 §1). */
    private const val STRENGTH_TRAINER_ID = 123

    fun map(window: WhoopWindow, fallbackZone: ZoneId): Routine {
        data class Night(val point: NightPoint, val wake: Instant, val hours: Double)

        val nights = window.sleeps
            .filter { !it.nap && it.scoreState == SCORED && it.score?.stageSummary != null }
            .mapNotNull { s ->
                val start = parse(s.start) ?: return@mapNotNull null
                val end = parse(s.end) ?: return@mapNotNull null
                if (!end.isAfter(start)) return@mapNotNull null
                val zone = zoneOf(s.timezoneOffset, fallbackZone)
                val st = s.score!!.stageSummary!!
                val endLocal = end.atZone(zone)
                val startLocal = start.atZone(zone)
                val need = s.score?.sleepNeeded?.baseline?.takeIf { it > 0 }?.let { (it / 60_000).toInt() }
                Night(
                    NightPoint(
                        dayKey = endLocal.toLocalDate(),
                        wakeMin = endLocal.toLocalTime().toSecondOfDay() / 60,
                        onsetMin = startLocal.toLocalTime().toSecondOfDay() / 60,
                        needMin = need,
                        tzOffsetMin = endLocal.offset.totalSeconds / 60,
                    ),
                    end,
                    (st.light + st.slowWave + st.rem) / 3_600_000.0,
                )
            }
            .groupBy { it.point.dayKey }
            .map { (_, list) -> list.maxBy { it.hours } }
            .sortedBy { it.wake }

        fun dayOf(instant: Instant, zone: ZoneId): LocalDate {
            nights.lastOrNull { !it.wake.isAfter(instant) }?.let { return it.point.dayKey }
            val local = instant.atZone(zone).toLocalDate()
            return if (nights.any { it.point.dayKey == local && it.wake.isAfter(instant) }) local.minusDays(1) else local
        }

        val sessions = window.workouts.filter { it.scoreState == SCORED }.mapNotNull { w ->
            val start = parse(w.start) ?: return@mapNotNull null
            val end = parse(w.end) ?: return@mapNotNull null
            val minutes = Duration.between(start, end).toMinutes().toInt()
            if (minutes <= 0) return@mapNotNull null
            val zone = zoneOf(w.timezoneOffset, fallbackZone)
            val day = dayOf(start, zone)
            val startMin = Duration.between(day.atStartOfDay(zone).toInstant(), start).toMinutes().toInt()
            Session(w.id, day, startMin, minutes, kindOf(w), intensityOf(w.score?.zones))
        }
        return Routine(nights.map { it.point }, sessions)
    }

    fun kindOf(w: WhoopWorkout): SessionKind {
        val name = w.sportName?.lowercase().orEmpty()
        val strength = w.sportId == STRENGTH_TRAINER_ID || strengthWords.any { name.contains(it) }
        return if (strength) SessionKind.STRENGTH else SessionKind.CARDIO
    }

    /** A35 §4 classes, see [Intensity]. Thresholds [Оценка], reviewed on day 30 (Agent 12). */
    fun intensityOf(z: WhoopWorkout.Zones?): Intensity {
        if (z == null) return Intensity.MIXED
        val total = (z.zero + z.one + z.two + z.three + z.four + z.five).toDouble()
        if (total <= 0.0) return Intensity.MIXED
        return when {
            (z.four + z.five) / total >= 0.2 -> Intensity.HIGH
            (z.one + z.two) / total >= 0.6 -> Intensity.ZONE2
            (z.zero + z.one) / total >= 0.6 -> Intensity.EASY
            else -> Intensity.MIXED
        }
    }

    private fun parse(text: String): Instant? =
        runCatching { Instant.parse(text) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()

    private fun zoneOf(offset: String?, fallback: ZoneId): ZoneId =
        offset?.let { runCatching { ZoneOffset.of(it) }.getOrNull() } ?: fallback
}
