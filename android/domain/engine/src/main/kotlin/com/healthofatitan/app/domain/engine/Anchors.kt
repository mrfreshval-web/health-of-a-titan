package com.healthofatitan.app.domain.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * One main sleep as HoT keeps it for the routine (A09 §7a): local times only, no stages or scores.
 *
 * @param dayKey local date of waking up
 * @param wakeMin local wake time, minutes from 00:00
 * @param onsetMin local sleep start, minutes from 00:00 of the evening (0…1439)
 * @param needMin WHOOP baseline sleep need of that night, minutes (in memory; only the median is kept)
 * @param tzOffsetMin UTC offset of that night, minutes
 */
data class NightPoint(
    val dayKey: LocalDate,
    val wakeMin: Int,
    val onsetMin: Int,
    val needMin: Int?,
    val tzOffsetMin: Int,
)

/**
 * The routine derived from WHOOP, never asked (D-027, A09 §7a). All times are local minutes from
 * 00:00. Weekdays and weekends are separate; values need ≥ 3 nights (training: ≥ 4 sessions).
 */
data class RoutineAnchors(
    val wakeWeekdayMin: Int? = null,
    val wakeWeekendMin: Int? = null,
    val onsetWeekdayMin: Int? = null,
    val onsetWeekendMin: Int? = null,
    val sleepNeedMin: Int? = null,
    val trainingStartWeekdayMin: Int? = null,
    val trainingStartWeekendMin: Int? = null,
    val trainingMinutes: Int? = null,
    val workouts28: Int = 0,
    val nights28: Int = 0,
    val homeTzOffsetMin: Int? = null,
    val lastWakeDay: LocalDate? = null,
    val lastWakeMin: Int? = null,
)

object Anchors {

    const val WINDOW_DAYS = 28L
    const val MIN_NIGHTS = 3
    const val MIN_WORKOUTS = 4
    const val DEFAULT_WAKE = 7 * 60
    const val DEFAULT_SLEEP_MIN = 8 * 60
    const val FALL_ASLEEP_MIN = 15

    fun isWeekend(day: LocalDate) = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY

    /** Median rounded to 5 minutes, null below [minN] values. */
    fun median5(values: List<Int>, minN: Int = 1): Int? {
        if (values.size < minN || values.isEmpty()) return null
        val s = values.sorted()
        val mid = s.size / 2
        val m = if (s.size % 2 == 1) s[mid].toDouble() else (s[mid - 1] + s[mid]) / 2.0
        return (m / 5.0).roundToInt() * 5
    }

    fun derive(nights: List<NightPoint>, sessions: List<Session>, today: LocalDate): RoutineAnchors {
        val from = today.minusDays(WINDOW_DAYS)
        val ns = nights.filter { it.dayKey > from && it.dayKey <= today }
        val (weekend, weekday) = ns.partition { isWeekend(it.dayKey) }
        // Onset around midnight: 23:30 and 00:30 are an hour apart, not 23 hours.
        fun unwrap(m: Int) = if (m < 12 * 60) m + 24 * 60 else m
        fun onset(list: List<NightPoint>) = median5(list.map { unwrap(it.onsetMin) }, MIN_NIGHTS)?.let { Math.floorMod(it, 24 * 60) }

        val ss = sessions.filter { it.dayKey > from && it.dayKey <= today && it.minutes >= WorkoutMatcher.MIN_SESSION_MIN }
        val enough = ss.size >= MIN_WORKOUTS
        val (ssWeekend, ssWeekday) = ss.partition { isWeekend(it.dayKey) }
        fun start(list: List<Session>) = if (!enough) null else median5(list.map { it.startMin }, 2) ?: median5(ss.map { it.startMin })

        val home = ns.filter { it.dayKey <= today.minusDays(2) && it.dayKey > today.minusDays(16) }.ifEmpty { ns }
        val last = ns.maxByOrNull { it.dayKey }
        return RoutineAnchors(
            wakeWeekdayMin = median5(weekday.map { it.wakeMin }, MIN_NIGHTS),
            wakeWeekendMin = median5(weekend.map { it.wakeMin }, MIN_NIGHTS),
            onsetWeekdayMin = onset(weekday),
            onsetWeekendMin = onset(weekend),
            sleepNeedMin = median5(ns.mapNotNull { it.needMin }, MIN_NIGHTS)?.coerceIn(390, 600),
            trainingStartWeekdayMin = start(ssWeekday),
            trainingStartWeekendMin = start(ssWeekend),
            trainingMinutes = if (enough) median5(ss.map { it.minutes }) else null,
            workouts28 = ss.size,
            nights28 = ns.size,
            homeTzOffsetMin = median5(home.map { it.tzOffsetMin }),
            lastWakeDay = last?.dayKey,
            lastWakeMin = last?.wakeMin,
        )
    }

    /**
     * Anchors for [dayKey] (A09 §7a): today's wake is the real one when WHOOP has it; tomorrow's
     * wake is the phone alarm if one is set for tomorrow morning, else the median for that kind of
     * day. Target bedtime = tomorrow's wake − sleep need − 15 min to fall asleep (default − 8 h).
     */
    fun resolve(a: RoutineAnchors, dayKey: LocalDate, nextAlarm: LocalDateTime?): ScheduleFacts {
        fun usualWake(day: LocalDate): Int? =
            if (isWeekend(day)) a.wakeWeekendMin ?: a.wakeWeekdayMin else a.wakeWeekdayMin ?: a.wakeWeekendMin

        val wake = (if (a.lastWakeDay == dayKey) a.lastWakeMin else null) ?: usualWake(dayKey) ?: DEFAULT_WAKE
        val tomorrow = dayKey.plusDays(1)
        val alarm = nextAlarm?.takeIf { it.toLocalDate() == tomorrow && it.hour in 3..11 }
        val usualTomorrow = usualWake(tomorrow)
        val (wakeTomorrow, source) = when {
            alarm != null -> (alarm.hour * 60 + alarm.minute) to AnchorSource.ALARM
            usualTomorrow != null -> usualTomorrow to AnchorSource.WHOOP
            else -> DEFAULT_WAKE to AnchorSource.DEFAULT
        }
        val need = a.sleepNeedMin
        val bedtime = (24 * 60 + wakeTomorrow - (need?.plus(FALL_ASLEEP_MIN) ?: DEFAULT_SLEEP_MIN))
            .coerceIn(wake + 12 * 60, wake + 20 * 60)
        val trainingStart = if (isWeekend(dayKey)) a.trainingStartWeekendMin else a.trainingStartWeekdayMin
        return ScheduleFacts(
            wakeMin = wake,
            wakeTomorrowMin = wakeTomorrow,
            wakeTomorrowSource = source,
            sleepNeedMin = need,
            bedtimeMin = bedtime,
            trainingStartMin = trainingStart,
            trainingMinutes = a.trainingMinutes,
        )
    }
}
