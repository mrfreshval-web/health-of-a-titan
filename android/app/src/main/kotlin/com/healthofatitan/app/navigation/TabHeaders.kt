package com.healthofatitan.app.navigation

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Overlines of the tab headers (A36 §10: «ВТОРНИК · 10 НОЯБРЯ», «2–8 НОЯБРЯ»). Pure functions of
 * the date, so they are unit-testable; HotOverline sets them in capitals.
 */
object TabHeaders {
    private val ru: Locale = Locale.forLanguageTag("ru")
    private val dayAndMonth = DateTimeFormatter.ofPattern("d MMMM", ru)
    private val weekday = DateTimeFormatter.ofPattern("EEEE", ru)

    /** «вторник · 10 ноября». */
    fun today(date: LocalDate): String = "${weekday.format(date)} · ${dayAndMonth.format(date)}"

    /** Monday–Sunday of the date's week: «2–8 ноября» or «30 ноября – 6 декабря». */
    fun week(date: LocalDate): String {
        val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = start.plusDays(6)
        return if (start.month == end.month) {
            "${start.dayOfMonth}–${dayAndMonth.format(end)}"
        } else {
            "${dayAndMonth.format(start)} – ${dayAndMonth.format(end)}"
        }
    }

    /** The middle of the «Неделя» header (A66 §2.2, mockup 06): «Неделя 45 · 2–8 ноября», ISO week. */
    fun weekCenter(date: LocalDate): String = "Неделя ${date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} · ${week(date)}"

    fun overline(tab: HotTab, date: LocalDate): String? = when (tab) {
        HotTab.TODAY -> null // the date stands in the middle of the header (A46 §2.1)
        HotTab.WEEK -> null // the week and its dates stand in the middle (A66 §2.2)
        HotTab.PATH -> "Путь"
        HotTab.BEHIND -> null
    }
}
