package com.healthofatitan.app.feature.path.logic

import com.healthofatitan.app.domain.safety.ActiveFlag
import com.healthofatitan.app.domain.safety.SafetyFlag
import com.healthofatitan.app.domain.safety.SafetyState
import com.healthofatitan.app.domain.today.DayMode
import com.healthofatitan.app.domain.today.StopWords
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «За плечами» (A66 §6): the calendar, the period, the facts, the search and failures without shame. */
class BehindLogicTest {
    private val today = LocalDate.of(2026, 11, 5)
    private val since = LocalDate.of(2026, 10, 1)

    private val days = mapOf(
        LocalDate.of(2026, 10, 1) to DayRecord(LocalDate.of(2026, 10, 1), DayMode.ROVNO, training = true),
        LocalDate.of(2026, 10, 2) to DayRecord(LocalDate.of(2026, 10, 2), DayMode.RAZGON),
        LocalDate.of(2026, 10, 3) to DayRecord(LocalDate.of(2026, 10, 3), DayMode.BEREZHNO),
        LocalDate.of(2026, 10, 4) to DayRecord(LocalDate.of(2026, 10, 4), null, pause = true),
        LocalDate.of(2026, 11, 5) to DayRecord(LocalDate.of(2026, 11, 5), DayMode.ROVNO),
    )

    @Test
    fun `the year is 12 rows of 31 slots, short months end in empty slots`() {
        val rows = YearCalendar.year(2026, today, days, since)
        assertEquals(12, rows.size)
        assertTrue(rows.all { it.cells.size == YearCalendar.SLOTS })
        assertEquals("фев", rows[1].label)
        assertEquals(3, rows[1].cells.count { it.kind == CellKind.NONE }, "28 days in February 2026")
        assertEquals(0, rows[0].cells.count { it.kind == CellKind.NONE })
    }

    @Test
    fun `cells - mode, pause, no data inside the archive, quiet before it and after today`() {
        val oct = YearCalendar.month(YearMonth.of(2026, 10), today, days, since).cells
        assertEquals(CellKind.MODE, oct[0].kind)
        assertEquals(DayMode.ROVNO, oct[0].mode)
        assertTrue(oct[0].training)
        assertEquals(DayMode.RAZGON, oct[1].mode)
        assertEquals(CellKind.PAUSE, oct[3].kind)
        assertEquals(CellKind.NO_DATA, oct[10].kind, "inside the archive without data: dashed")
        val sep = YearCalendar.month(YearMonth.of(2026, 9), today, days, since).cells
        assertTrue(sep.filter { it.kind != CellKind.NONE }.all { it.kind == CellKind.QUIET }, "older than the archive: quiet, not «no data»")
        val nov = YearCalendar.month(YearMonth.of(2026, 11), today, days, since).cells
        assertTrue(nov[4].today)
        assertEquals(CellKind.QUIET, nov[5].kind, "no future cells are claimed")
        val empty = YearCalendar.month(YearMonth.of(2026, 10), today, emptyMap(), null).cells
        assertTrue(empty.filter { it.kind != CellKind.NONE }.all { it.kind == CellKind.QUIET })
    }

    @Test
    fun `a month is one talkback line`() {
        val row = YearCalendar.month(YearMonth.of(2026, 10), today, days, since)
        assertTrue(row.spoken.startsWith("Октябрь: "), row.spoken)
        assertTrue("Ровно 1" in row.spoken && "Разгон 1" in row.spoken && "Бережно 1" in row.spoken, row.spoken)
        assertTrue("тренировок 1" in row.spoken && "пауза 1 день" in row.spoken, row.spoken)
        assertTrue("нет данных 27" in row.spoken, row.spoken)
    }

    @Test
    fun `the period steps by month or year, never into the future or before the archive`() {
        val year = PeriodSel(BehindPeriod.YEAR, today)
        assertNull(year.step(1, today, since))
        assertNull(year.step(-1, today, since))
        assertEquals(2025, year.step(-1, today, LocalDate.of(2025, 3, 1))?.anchor?.year)
        val month = PeriodSel(BehindPeriod.MONTH, today)
        assertEquals(10, month.step(-1, today, since)?.anchor?.monthValue)
        assertNull(month.step(1, today, since))
        assertNull(PeriodSel(BehindPeriod.MONTH, since).step(-1, today, since))
        assertNull(PeriodSel(BehindPeriod.ALL, today).step(-1, today, since))
        assertTrue(month.contains(LocalDate.of(2026, 11, 1)) && !month.contains(LocalDate.of(2026, 10, 31)))
        assertEquals("2026", year.label)
        assertEquals("Ноябрь 2026", month.label)
    }

    private val records = listOf(
        BehindRecord(RecordKind.TRAINING, LocalDate.of(2026, 10, 1), BehindCopy.TRAINING_HOT, "40 мин", "classic"),
        BehindRecord(RecordKind.TRAINING, LocalDate.of(2026, 11, 2), BehindCopy.TRAINING_WHOOP, "35 мин", "running whoop"),
        BehindRecord(RecordKind.TRAINING, LocalDate.of(2025, 12, 2), BehindCopy.TRAINING_WHOOP, "35 мин"),
        BehindRecord(RecordKind.WEEK, LocalDate.of(2026, 11, 2), BehindCopy.week(45, LocalDate.of(2026, 11, 2)), "Тренировки · Сон по потребности"),
        BehindRecord(RecordKind.WEEK, LocalDate.of(2026, 10, 26), BehindCopy.week(44, LocalDate.of(2026, 10, 26)), "Фокус-блоки"),
        BehindRecord(RecordKind.CHAPTER, LocalDate.of(2026, 10, 21), BehindCopy.chapter("Подтянуться 10 раз"), BehindCopy.CHAPTER_STARTED),
        BehindRecord(RecordKind.MILESTONE, LocalDate.of(2026, 11, 17), BehindCopy.milestone(1, "Подтянуться 10 раз")),
        BehindRecord(RecordKind.NOTE, LocalDate.of(2026, 11, 1), BehindCopy.NOTE_PRIDE, "Вечерние созвоны съедают отбой"),
        BehindRecord(RecordKind.DAY, LocalDate.of(2026, 10, 3), "сб, 3 октября · Бережно", words = "Бережно"),
        BehindRecord(RecordKind.HARD, LocalDate.of(2026, 10, 26), BehindCopy.week(44, LocalDate.of(2026, 10, 26)), "неделя пошла иначе · Бережно 3 дня"),
    )

    @Test
    fun `the facts of the year are positive and cumulative, an empty one is left out`() {
        val year = PeriodSel(BehindPeriod.YEAR, today)
        val f = PeriodFacts.of(records, year, edCare = false)
        assertEquals(listOf(2, 2, 1), f.map { it.number })
        assertEquals(listOf("тренировки", "недели с целями", "глава начата"), f.map { it.words })
        assertEquals(listOf(BehindFilter.TRAININGS, BehindFilter.WEEKS, BehindFilter.MILESTONES), f.map { it.filter })
        assertEquals(3, PeriodFacts.of(records, PeriodSel(BehindPeriod.ALL, today), false).first().number)
        assertTrue(PeriodFacts.of(emptyList(), year, false).isEmpty(), "the first day: the starting point, not three zeros")
    }

    @Test
    fun `with ED_CARE the hero counts milestones, not workouts`() {
        val f = PeriodFacts.of(records, PeriodSel(BehindPeriod.YEAR, today), edCare = true)
        assertTrue(f.none { it.filter == BehindFilter.TRAININGS })
        assertEquals("веха", f.first().words)
    }

    @Test
    fun `the search finds by every word, by month names and modes, newest first, grouped`() {
        val all = PeriodSel(BehindPeriod.ALL, today)
        val tr = BehindSearch.search(records, "тренировка", all)
        assertEquals(listOf(RecordKind.TRAINING), tr.map { it.kind })
        assertEquals(LocalDate.of(2026, 11, 2), tr.first().items.first().day)
        assertEquals(3, tr.first().items.size)
        assertEquals(1, BehindSearch.search(records, "тренировка whoop декабрь", all).single().items.size)
        assertEquals(RecordKind.DAY, BehindSearch.search(records, "бережно", all).single().kind, "hard periods are never searched")
        assertEquals(RecordKind.NOTE, BehindSearch.search(records, "созвоны", all).single().kind)
        assertEquals(listOf(RecordKind.WEEK), BehindSearch.search(records, "неделя 45", all).map { it.kind })
        assertTrue(BehindSearch.search(records, "  ", all).isEmpty())
        assertTrue(BehindSearch.search(records, "тренировка", PeriodSel(BehindPeriod.MONTH, LocalDate.of(2026, 9, 1))).isEmpty())
        assertEquals("еще", BehindSearch.normalize("ЕЩЁ"))
    }

    @Test
    fun `more than three in a group says how many more`() {
        val many = (1..5).map { BehindRecord(RecordKind.TRAINING, today.minusDays(it.toLong()), BehindCopy.TRAINING_HOT) }
        val g = BehindSearch.search(many, "тренировка", PeriodSel(BehindPeriod.ALL, today)).single()
        assertEquals(3, g.items.size)
        assertEquals(2, g.more)
        assertEquals("ещё 2 ›", BehindCopy.more(g.more))
    }

    @Test
    fun `hard periods are not offered in hard states`() {
        val normal = SafetyState.NORMAL
        assertTrue(BehindSafety.hardAvailable(normal, DayMode.ROVNO))
        assertFalse(BehindSafety.hardAvailable(normal, DayMode.BEREZHNO), "Бережно")
        listOf(SafetyFlag.STORM, SafetyFlag.GRIEF, SafetyFlag.SICK, SafetyFlag.RETURN, SafetyFlag.LIGHT_WEEK, SafetyFlag.CRISIS_L2).forEach { f ->
            val s = SafetyState(flags = mapOf(f to ActiveFlag(f, since = 0L)))
            assertFalse(BehindSafety.hardAvailable(s, DayMode.ROVNO), f.name)
        }
        assertFalse(BehindSafety.hardAvailable(SafetyState(unknown = true), null), "an unknown flag forbids")
    }

    @Test
    fun `a week that went otherwise comes with its context and without words of shame`() {
        val monday = LocalDate.of(2026, 10, 26)
        val gentle = (0..2).map { DayRecord(monday.plusDays(it.toLong()), DayMode.BEREZHNO) } + DayRecord(monday.plusDays(4), null, pause = true)
        val r = assertNotNull(BehindSafety.hardWeek(monday, gentle))
        assertEquals(RecordKind.HARD, r.kind)
        assertEquals("Неделя 44 · 26 окт – 1 ноя", r.title)
        assertEquals("неделя пошла иначе · Бережно 3 дня, пауза 1 день", r.detail)
        assertNull(BehindSafety.hardWeek(monday, gentle.take(2)), "two days of Бережно are an ordinary week")
        val words = listOf("сорвал", "провал", "пропуск", "не выполнил", "неудач")
        (BehindCopy.all() + r.title + r.detail!!).forEach { t ->
            assertTrue(words.none { it in t.lowercase() }, t)
            assertEquals(emptyList(), StopWords.hits(t), t)
        }
    }

    @Test
    fun `released chapters count the effort, not the time since`() {
        assertEquals("отпущена · была в работе 5 недель", BehindCopy.released(5))
        assertEquals("отпущена · была в работе 1 неделю", BehindCopy.released(0))
        assertEquals("глава начата", BehindCopy.chapters(1, started = true))
        assertEquals("главы", BehindCopy.chapters(3, started = false))
    }
}
