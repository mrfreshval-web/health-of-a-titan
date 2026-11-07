package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.domain.today.CalendarWeek
import com.healthofatitan.app.domain.today.Corridor
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
    /** Today's WHOOP day strain (the latest cycle), 0–21; memory only. */
    val dayStrain: StrainGlance? = null,
    /** The latest cycle: strain, kJ and steps (A46 «усердие», «движение»); memory only. */
    val cycle: CycleGlance? = null,
    /** Personal corridors over the fetched window (~30 days, A46 §3); memory only. */
    val corridors: BodyCorridors? = null,
    /** Minutes in heart-rate zones 2–5 of this calendar week's workouts (A52 «минуты в зонах»). */
    val weekZoneMinutes: Int? = null,
    /** Every day of the fetched window for «Неделя» (A52 §2.3, §2.4, §2.8); memory only. */
    val days: List<WhoopDay> = emptyList(),
)

/**
 * One local day of the window as «Неделя» reads it: the cycle that started that day, the recovery
 * and the main sleep that ended that morning, and the workouts that started that day with their
 * `zone_durations`. **Raw WHOOP values, memory only** — never written anywhere (R-11, D-009).
 */
data class WhoopDay(
    val day: LocalDate,
    val strain: Double? = null,
    val steps: Int? = null,
    val restingHr: Double? = null,
    val hrvMs: Double? = null,
    val respiratoryRate: Double? = null,
    val spo2: Double? = null,
    val skinTempC: Double? = null,
    val sleepHours: Double? = null,
    val needHours: Double? = null,
    val consistency: Double? = null,
    val workouts: List<WorkoutZones> = emptyList(),
)

/** A workout of the day: its minutes and its minutes in each heart-rate zone 0–5 (WHOOP `zone_durations`). */
data class WorkoutZones(val start: Instant, val minutes: Int, val zoneMinutes: List<Int>) {
    /** Zones 2–5: «минуты в зонах» of A52 (WHO 150–300 min of moderate-to-vigorous activity). */
    val activeMinutes: Int get() = zoneMinutes.drop(2).sum()

    /** Zones 4–5 (hard). */
    val hardMinutes: Int get() = zoneMinutes.drop(4).sum()

    val zonedMinutes: Int get() = zoneMinutes.sum()
}

/** The latest WHOOP cycle as «Сегодня» v3 reads it; every value may be missing. */
data class CycleGlance(val start: Instant, val strain: Double?, val kilojoule: Double?, val steps: Int?)

/**
 * Mean ± SD of the window for «Почему так» (A46 §3: the corridor of 30 days, the trend of 7).
 * Memory only, like the rest of the glance.
 */
data class BodyCorridors(
    val restingHr: Corridor?,
    val hrv: Corridor?,
    val respiratoryRate: Corridor?,
    val spo2: Corridor?,
    val skinTemp: Corridor?,
    /** Mean resting heart rate of the last 7 scored recoveries. */
    val restingHr7: Double?,
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
    /** A46 §3 «Сон» and «Дыхание», memory only. */
    val efficiency: Double? = null,
    val consistency: Double? = null,
    val disturbances: Int? = null,
    val respiratoryRate: Double? = null,
)

/** Minutes of light, deep (slow wave) and REM sleep and of waking in bed (WHOOP stage summary). */
data class SleepStages(val lightMin: Int, val deepMin: Int, val remMin: Int, val awakeMin: Int)

/** Recovery inputs of that night: HRV (rMSSD, ms) and resting heart rate. No Recovery % (A25 P7). */
data class RecoveryGlance(val hrvMs: Double?, val restingHr: Double?, val spo2: Double? = null, val skinTempC: Double? = null)

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
            SleepGlance(
                end.atZone(zoneOf(s.timezoneOffset, fallbackZone)).toLocalDate(), end, hours, need, stages,
                efficiency = s.score?.efficiency, consistency = s.score?.consistency,
                disturbances = st.disturbances, respiratoryRate = s.score?.respiratoryRate,
            )
        }
        val recovery = sleep?.let { (s, _) ->
            window.recoveries.firstOrNull { it.sleepId == s.id && it.scoreState == SCORED }?.score?.let {
                RecoveryGlance(
                    it.hrvRmssdMilli?.takeIf { v -> v > 0 },
                    it.restingHeartRate?.takeIf { v -> v > 0 },
                    spo2 = it.spo2Percentage?.takeIf { v -> v > 0 },
                    skinTempC = it.skinTempCelsius?.takeIf { v -> v > 0 },
                )
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
        val cycle = window.cycles
            .mapNotNull { c -> parse(c.start)?.let { c to it } }
            .maxByOrNull { it.second }
            ?.let { (c, start) ->
                CycleGlance(
                    start = start,
                    strain = c.score?.strain?.takeIf { it >= 0.0 },
                    kilojoule = c.score?.kilojoule?.takeIf { it >= 0.0 },
                    steps = (c.score?.stepCount ?: c.stepCount)?.takeIf { it >= 0 },
                )
            }
        return WhoopGlance(
            fetchedAt, sleepGlance, recovery, workout, strain,
            cycle = cycle,
            corridors = corridors(window),
            weekZoneMinutes = weekZones(window, fallbackZone, fetchedAt),
            days = days(window, fallbackZone),
        )
    }

    /** The window day by day (A52): cycles by their start, recoveries and sleeps by the morning, workouts by their start. */
    fun days(window: WhoopWindow, zone: ZoneId): List<WhoopDay> {
        val byDay = sortedMapOf<LocalDate, WhoopDay>()
        fun at(d: LocalDate): WhoopDay = byDay.getOrPut(d) { WhoopDay(d) }
        window.cycles.forEach { c ->
            val d = parse(c.start)?.atZone(zoneOf(c.timezoneOffset, zone))?.toLocalDate() ?: return@forEach
            val cur = at(d)
            byDay[d] = cur.copy(
                strain = c.score?.strain?.takeIf { it >= 0.0 } ?: cur.strain,
                steps = (c.score?.stepCount ?: c.stepCount)?.takeIf { it >= 0 } ?: cur.steps,
            )
        }
        val mainSleeps = window.sleeps.filter { !it.nap && it.scoreState == SCORED }
        mainSleeps.forEach { s ->
            val d = parse(s.end)?.atZone(zoneOf(s.timezoneOffset, zone))?.toLocalDate() ?: return@forEach
            val st = s.score?.stageSummary
            val cur = at(d)
            byDay[d] = cur.copy(
                sleepHours = st?.let { (it.light + it.slowWave + it.rem) / 3_600_000.0 } ?: cur.sleepHours,
                needHours = s.score?.sleepNeeded?.totalMilli?.takeIf { it > 0 }?.let { it / 3_600_000.0 } ?: cur.needHours,
                consistency = s.score?.consistency ?: cur.consistency,
                respiratoryRate = s.score?.respiratoryRate?.takeIf { it > 0 } ?: cur.respiratoryRate,
            )
            window.recoveries.firstOrNull { it.sleepId == s.id && it.scoreState == SCORED }?.score?.let { r ->
                val now = byDay.getValue(d)
                byDay[d] = now.copy(
                    restingHr = r.restingHeartRate?.takeIf { it > 0 },
                    hrvMs = r.hrvRmssdMilli?.takeIf { it > 0 },
                    spo2 = r.spo2Percentage?.takeIf { it > 0 },
                    skinTempC = r.skinTempCelsius?.takeIf { it > 0 },
                )
            }
        }
        window.workouts.filter { it.scoreState == SCORED }.forEach { w ->
            val start = parse(w.start) ?: return@forEach
            val d = start.atZone(zoneOf(w.timezoneOffset, zone)).toLocalDate()
            val minutes = parse(w.end)?.let { Duration.between(start, it).toMinutes().toInt() }?.coerceAtLeast(0) ?: 0
            val z = w.score?.zones
            val zones = if (z == null) emptyList() else listOf(z.zero, z.one, z.two, z.three, z.four, z.five).map(::minutes)
            val cur = at(d)
            byDay[d] = cur.copy(workouts = cur.workouts + WorkoutZones(start, minutes, zones))
        }
        return byDay.values.toList()
    }

    /** Mean ± SD of the scored recoveries and main sleeps of the window (A46 §3, memory only). */
    fun corridors(window: WhoopWindow): BodyCorridors {
        val rec = window.recoveries.filter { it.scoreState == SCORED }.mapNotNull { it.score }
        val sleeps = window.sleeps.filter { !it.nap && it.scoreState == SCORED }
            .mapNotNull { s -> parse(s.end)?.let { it to s } }
            .sortedBy { it.first }
        val rhrByNight = window.recoveries.filter { it.scoreState == SCORED }
            .mapNotNull { r -> sleeps.firstOrNull { it.second.id == r.sleepId }?.first?.let { end -> end to r.score?.restingHeartRate } }
            .filter { (it.second ?: 0.0) > 0.0 }
            .sortedByDescending { it.first }
        return BodyCorridors(
            restingHr = Corridor.of(rec.mapNotNull { it.restingHeartRate?.takeIf { v -> v > 0 } }),
            hrv = Corridor.of(rec.mapNotNull { it.hrvRmssdMilli?.takeIf { v -> v > 0 } }),
            respiratoryRate = Corridor.of(sleeps.mapNotNull { it.second.score?.respiratoryRate?.takeIf { v -> v > 0 } }),
            spo2 = Corridor.of(rec.mapNotNull { it.spo2Percentage?.takeIf { v -> v > 0 } }),
            skinTemp = Corridor.of(rec.mapNotNull { it.skinTempCelsius?.takeIf { v -> v > 0 } }),
            restingHr7 = rhrByNight.take(7).mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    /** Zones 2–5 of this calendar week's scored workouts, in minutes (A52 «минуты в зонах»). */
    fun weekZones(window: WhoopWindow, zone: ZoneId, now: Long): Int? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val monday = CalendarWeek.start(today)
        val zones = window.workouts
            .filter { it.scoreState == SCORED }
            .filter { w -> parse(w.start)?.atZone(zoneOf(w.timezoneOffset, zone))?.toLocalDate()?.let { CalendarWeek.contains(monday, it) } == true }
            .mapNotNull { it.score?.zones }
        if (zones.isEmpty()) return null
        return zones.sumOf { (it.two + it.three + it.four + it.five) / 60_000L }.toInt()
    }

    private fun parse(text: String): Instant? =
        runCatching { Instant.parse(text) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()

    private fun zoneOf(offset: String?, fallback: ZoneId): ZoneId =
        offset?.let { runCatching { ZoneOffset.of(it) }.getOrNull() } ?: fallback
}
