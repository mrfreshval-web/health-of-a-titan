package com.healthofatitan.app.domain.engine

import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A09 §7a, D-027: the routine is derived from WHOOP and the phone alarm, never asked. */
class AnchorsTest {

    /** Friday. */
    private val today = LocalDate.of(2026, 11, 6)

    /** 28 nights: weekdays wake 06:45 / asleep 23:20, weekends wake 08:30 / asleep 00:40; need 8 h. */
    private val nights = (0L until 28L).map { i ->
        val d = today.minusDays(i)
        val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
        NightPoint(
            dayKey = d,
            wakeMin = if (weekend) 8 * 60 + 30 else 6 * 60 + 45,
            onsetMin = if (weekend) 40 else 23 * 60 + 20,
            needMin = 480,
            tzOffsetMin = -300,
        )
    }

    private val sessions = (1L..8L).map { i ->
        Session("w$i", today.minusDays(i * 3), 18 * 60 + 10, 50, SessionKind.STRENGTH, Intensity.MIXED)
    }

    @Test
    fun `medians split weekdays and weekends, onset wraps around midnight`() {
        val a = Anchors.derive(nights, sessions, today)
        assertEquals(6 * 60 + 45, a.wakeWeekdayMin)
        assertEquals(8 * 60 + 30, a.wakeWeekendMin)
        assertEquals(23 * 60 + 20, a.onsetWeekdayMin)
        assertEquals(40, a.onsetWeekendMin)
        assertEquals(480, a.sleepNeedMin)
        assertEquals(18 * 60 + 10, a.trainingStartWeekdayMin)
        assertEquals(50, a.trainingMinutes)
        assertEquals(-300, a.homeTzOffsetMin)
        assertEquals(today, a.lastWakeDay)
    }

    @Test
    fun `fewer than four workouts fix no training slot`() {
        val a = Anchors.derive(nights, sessions.take(3), today)
        assertNull(a.trainingStartWeekdayMin)
        assertNull(a.trainingMinutes)
    }

    @Test
    fun `tomorrow's alarm sets tonight's bedtime, otherwise the weekend median`() {
        val a = Anchors.derive(nights, sessions, today)
        val withAlarm = Anchors.resolve(a, today, today.plusDays(1).atTime(7, 15))
        assertEquals(AnchorSource.ALARM, withAlarm.wakeTomorrowSource)
        // 07:15 − 8 h − 15 min = 23:00
        assertEquals(23 * 60, withAlarm.bedtimeMin)
        assertEquals(6 * 60 + 45, withAlarm.wakeMin, "today's wake is the real one from WHOOP")

        val noAlarm = Anchors.resolve(a, today, null)
        assertEquals(AnchorSource.WHOOP, noAlarm.wakeTomorrowSource)
        assertEquals(8 * 60 + 30, noAlarm.wakeTomorrowMin, "Saturday")
        assertEquals(24 * 60 + 15, noAlarm.bedtimeMin, "00:15 after midnight")

        val farAlarm = Anchors.resolve(a, today, today.plusDays(3).atTime(6, 0))
        assertEquals(AnchorSource.WHOOP, farAlarm.wakeTomorrowSource, "an alarm for another day is ignored")
    }

    @Test
    fun `without history the defaults are 07_00 and minus 8 hours`() {
        val s = Anchors.resolve(RoutineAnchors(), today, null)
        assertEquals(7 * 60, s.wakeMin)
        assertEquals(AnchorSource.DEFAULT, s.wakeTomorrowSource)
        assertEquals(23 * 60, s.bedtimeMin)
        assertNull(s.trainingStartMin)
    }
}
