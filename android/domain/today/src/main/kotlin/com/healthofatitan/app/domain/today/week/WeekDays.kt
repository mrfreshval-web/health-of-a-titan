package com.healthofatitan.app.domain.today.week

import com.healthofatitan.app.domain.today.CalendarWeek
import com.healthofatitan.app.domain.today.DayMode
import com.healthofatitan.app.domain.today.DayScore
import com.healthofatitan.app.domain.today.DayScores
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One day as the week sees it (A52 §2.1, §4). [fills] are the five supports of the day as shares
 * of their norm (effort, movement, nutrition, drink, sleep preparation), null when the day has no
 * data. A stored [score] (counted at 04:00 the next day) wins over a recount.
 */
data class DayFact(
    val day: LocalDate,
    val mode: DayMode? = null,
    val fills: List<Float>? = null,
    val tasksDone: Int = 0,
    val sick: Boolean = false,
    val score: DayScore? = null,
)

/** One of the seven columns of «Неделя одним взглядом» (A52 §2.1). */
data class DayColumn(
    val day: LocalDate,
    val mode: DayMode?,
    /** 0…1: the share of filled supports (supportsFilled / 5). */
    val fill: Float,
    /** The dot: ≥ 1 task done that day. */
    val taskDone: Boolean,
    val inSupport: Boolean,
    val today: Boolean,
    /** A future day: an outline only. */
    val future: Boolean,
    val sick: Boolean,
    val supportsFilled: Int,
    val tasksDone: Int,
    /** The past day had no data: nothing is subtracted, the column is quiet. */
    val noData: Boolean,
)

/** What kind of week it is for the mark (A52 §2.1, §3.1, §3.6). */
enum class WeekKind { NORMAL, FIRST, RETURN }

object WeekScoring {
    const val DAYS = 7
    const val SUPPORTS = 5

    /** The mark on the scale of «Дни в опоре» (A52 §2.1): 4; the first week 3; a week of return 2. */
    fun mark(kind: WeekKind): Int = when (kind) {
        WeekKind.NORMAL -> 4
        WeekKind.FIRST -> 3
        WeekKind.RETURN -> 2
    }

    /** A52 §2.1 «День после 04:00 следующего дня считается окончательно». */
    const val CLOSE_HOUR = 4

    /**
     * W-01: a day is in support when ≥ 1 task of the day is done (a mini version counts) and ≥ 3 of
     * the 5 supports are at ≥ 70 % of their norm (≥ 50 % in Бережно). A sick day counts as rest.
     */
    fun score(f: DayFact): DayScore? {
        f.score?.let { return it }
        val fills = f.fills
        return when {
            fills != null -> DayScores.of(fills, f.tasksDone, gentle = f.mode == DayMode.BEREZHNO, sick = f.sick)
            f.sick -> DayScore(dayInSupport = true, supportsFilled = 0, tasksDone = f.tasksDone)
            f.tasksDone > 0 -> DayScores.of(emptyList(), f.tasksDone, gentle = f.mode == DayMode.BEREZHNO)
            else -> null
        }
    }

    /** The day whose score is final at [now]: the previous day after 04:00, every day before it. */
    fun closed(day: LocalDate, now: LocalDateTime): Boolean =
        day.isBefore(now.toLocalDate().minusDays(1)) ||
            (day == now.toLocalDate().minusDays(1) && now.hour >= CLOSE_HOUR)

    /** W-08: seven columns Monday…Sunday of the local calendar week of [today]. */
    fun columns(today: LocalDate, facts: List<DayFact>): List<DayColumn> {
        val monday = CalendarWeek.start(today)
        val byDay = facts.associateBy { it.day }
        return (0 until DAYS).map { i ->
            val d = monday.plusDays(i.toLong())
            val f = byDay[d]
            val future = d.isAfter(today)
            val s = if (future || f == null) null else score(f)
            DayColumn(
                day = d,
                mode = f?.mode?.takeIf { !future },
                fill = (s?.supportsFilled ?: 0).toFloat() / SUPPORTS,
                taskDone = (s?.tasksDone ?: 0) > 0,
                inSupport = s?.dayInSupport == true,
                today = d == today,
                future = future,
                sick = f?.sick == true && !future,
                supportsFilled = s?.supportsFilled ?: 0,
                tasksDone = s?.tasksDone ?: 0,
                noData = !future && s == null,
            )
        }
    }

    fun daysInSupport(columns: List<DayColumn>): Int = columns.count { it.inSupport }

    /** North Star (A52 §2.1): «Неделя в опоре» = ≥ 4 days in support and ≥ 1 step of the path. */
    fun weekInSupport(daysInSupport: Int, pathSteps: Int): Boolean = daysInSupport >= 4 && pathSteps >= 1

    /** A52 §3.4: ≥ 3 days of Бережно make a week of recovery. */
    fun gentleWeek(columns: List<DayColumn>): Boolean = columns.count { it.mode == DayMode.BEREZHNO } >= 3

    /** The mode most of the week's days had; Ровно when nothing is known. */
    fun dominantMode(columns: List<DayColumn>): DayMode =
        columns.mapNotNull { it.mode }.groupingBy { it }.eachCount().maxWithOrNull(compareBy({ it.value }, { it.key.ordinal }))?.key
            ?: DayMode.ROVNO

    private val SHORT = mapOf(
        DayOfWeek.MONDAY to "пн", DayOfWeek.TUESDAY to "вт", DayOfWeek.WEDNESDAY to "ср", DayOfWeek.THURSDAY to "чт",
        DayOfWeek.FRIDAY to "пт", DayOfWeek.SATURDAY to "сб", DayOfWeek.SUNDAY to "вс",
    )

    fun short(day: LocalDate): String = SHORT.getValue(day.dayOfWeek)

    /** ISO number of the week (A52 §1 «неделя 45»): Monday-based, like the calendar of the screen. */
    fun number(day: LocalDate): Int = day.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
}
