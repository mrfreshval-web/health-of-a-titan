package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.domain.metrics.DayInputs
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.ln

/**
 * WHOOP window → [DayInputs] per HoT day (A14 §3.2: day = WHOOP cycle, wake → wake; `dayKey` =
 * local date of waking up). Pure, so it is tested on JVM fixtures.
 *
 * - **Sleep:** main sleeps only (no naps), SCORED; the day is the local date of `end` in the
 *   sleep's own `timezone_offset` (travel-safe). Two main sleeps ending the same day → the longer.
 * - **Recovery:** joined to its sleep by `sleep_id`; HRV → ln(rMSSD), resting HR.
 * - **Workouts:** belong to the day of the latest wake-up before their start; Edwards TRIMP from
 *   the minutes in HR zones 1–5. A worn day without workouts has load 0, an unknown day null.
 * - **Cycles:** a SCORED cycle marks a day as worn (the cycle starts at sleep onset, so the
 *   day is the date of the following wake-up when it starts in the evening).
 */
internal object WhoopWindowMapper {

    fun map(window: WhoopWindow, fallbackZone: ZoneId): List<DayInputs> {
        data class Night(val dayKey: LocalDate, val wake: Instant, val sleep: WhoopSleep, val hours: Double, val midMin: Double)

        val nights = window.sleeps
            .filter { !it.nap && it.scoreState == SCORED && it.score?.stageSummary != null }
            .mapNotNull { s ->
                val start = parseInstant(s.start) ?: return@mapNotNull null
                val end = parseInstant(s.end) ?: return@mapNotNull null
                if (!end.isAfter(start)) return@mapNotNull null
                val zone = zoneOf(s.timezoneOffset, fallbackZone)
                val st = s.score!!.stageSummary!!
                val hours = (st.light + st.slowWave + st.rem) / 3_600_000.0
                val mid = start.plus(Duration.between(start, end).dividedBy(2)).atZone(zone).toLocalTime()
                var midMin = mid.toSecondOfDay() / 60.0
                if (midMin > 720) midMin -= 1440 // 23:30 → −30, so the norm does not jump at midnight
                Night(end.atZone(zone).toLocalDate(), end, s, hours, midMin)
            }
            .groupBy { it.dayKey }
            .mapValues { (_, list) -> list.maxBy { it.hours } }

        val recoveryBySleep = window.recoveries
            .filter { it.scoreState == SCORED && it.sleepId != null }
            .associateBy { it.sleepId!! }

        val wakes = nights.values.sortedBy { it.wake }
        fun dayOf(instant: Instant, zone: ZoneId): LocalDate {
            wakes.lastOrNull { !it.wake.isAfter(instant) }?.let { return it.dayKey }
            // Before the first known wake-up: if that day's wake is still ahead, it is yesterday.
            val local = instant.atZone(zone).toLocalDate()
            return if (nights[local]?.wake?.isAfter(instant) == true) local.minusDays(1) else local
        }

        val trimpByDay = mutableMapOf<LocalDate, Double>()
        window.workouts.filter { it.scoreState == SCORED }.forEach { w ->
            val start = parseInstant(w.start) ?: return@forEach
            val z = w.score?.zones ?: return@forEach
            val trimp = (z.one * 1 + z.two * 2 + z.three * 3 + z.four * 4 + z.five * 5) / 60_000.0
            val day = dayOf(start, zoneOf(w.timezoneOffset, fallbackZone))
            trimpByDay[day] = (trimpByDay[day] ?: 0.0) + trimp
        }

        val wornByCycle = window.cycles.filter { it.scoreState == SCORED }.mapNotNull { c ->
            val start = parseInstant(c.start) ?: return@mapNotNull null
            val local = start.atZone(zoneOf(c.timezoneOffset, fallbackZone))
            if (local.hour >= EVENING_HOUR) local.toLocalDate().plusDays(1) else local.toLocalDate()
        }.toSet()

        val allDays = (nights.keys + trimpByDay.keys + wornByCycle).toSortedSet()
        return allDays.map { day ->
            val night = nights[day]
            val recovery = night?.let { recoveryBySleep[it.sleep.id] }?.score
            val hrv = recovery?.hrvRmssdMilli?.takeIf { it > 0 }
            DayInputs(
                dayKey = day,
                lnRmssd = hrv?.let(::ln),
                rhr = recovery?.restingHeartRate?.takeIf { it > 0 },
                rr = night?.sleep?.score?.respiratoryRate?.takeIf { it > 0 },
                sleepHours = night?.hours,
                sleepMidMin = night?.midMin,
                trimp = trimpByDay[day] ?: 0.0, // every day here was worn (sleep, cycle or workout)
            )
        }
    }

    private const val EVENING_HOUR = 15

    private fun parseInstant(text: String): Instant? =
        runCatching { Instant.parse(text) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()

    private fun zoneOf(offset: String?, fallback: ZoneId): ZoneId =
        offset?.let { runCatching { ZoneOffset.of(it) }.getOrNull() } ?: fallback
}
