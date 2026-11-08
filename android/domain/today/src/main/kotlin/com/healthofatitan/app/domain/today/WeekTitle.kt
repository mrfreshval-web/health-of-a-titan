package com.healthofatitan.app.domain.today

import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * The title of the app bar on «Неделя» (A66 §4.2, mockup 06): «Неделя 45 · 2–8 ноября». The
 * number is the ISO week (Monday-based, like the calendar of the screen); the dates run Monday to
 * Sunday. A week across two months names both: «Неделя 44 · 26 октября – 1 ноября»; across two
 * years the same way (the ISO number belongs to the week-based year).
 */
object WeekTitle {
    private val MONTHS = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )

    /** The ISO number of the week of [day]. */
    fun number(day: LocalDate): Int = day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    /** «2–8 ноября» or «26 октября – 1 ноября» for the week of [day]. */
    fun range(day: LocalDate): String {
        val monday = CalendarWeek.start(day)
        val sunday = CalendarWeek.end(day)
        return if (monday.month == sunday.month) {
            "${monday.dayOfMonth}–${sunday.dayOfMonth} ${month(sunday)}"
        } else {
            "${monday.dayOfMonth} ${month(monday)} – ${sunday.dayOfMonth} ${month(sunday)}"
        }
    }

    /** «Неделя 45 · 2–8 ноября». */
    fun of(day: LocalDate): String = "Неделя ${number(day)} · ${range(day)}"

    /** The month of [day] in the genitive: «ноября». */
    fun month(day: LocalDate): String = MONTHS[day.monthValue - 1]
}
