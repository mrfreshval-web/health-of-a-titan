package com.healthofatitan.app.feature.path.logic

import com.healthofatitan.app.domain.safety.FlagGroup
import com.healthofatitan.app.domain.safety.Restriction
import com.healthofatitan.app.domain.safety.SafetyConsumer
import com.healthofatitan.app.domain.safety.SafetyFlag
import com.healthofatitan.app.domain.safety.SafetyState
import com.healthofatitan.app.domain.today.DayMode
import java.time.LocalDate
import java.time.YearMonth

/** The period of «За плечами» (A66 §6.2): the segment «Месяц · Год · Всё время». */
enum class BehindPeriod(val label: String) {
    MONTH(BehindCopy.MONTH),
    YEAR(BehindCopy.YEAR),
    ALL(BehindCopy.ALL_TIME),
}

/** A lived day as HoT keeps it: the mode of the day, a pause (illness, the stand of the path), a workout. */
data class DayRecord(val day: LocalDate, val mode: DayMode?, val pause: Boolean = false, val training: Boolean = false)

/** What a record of the archive is (A66 §6.3: Дни · Недели · Тренировки · Цели · Заметки). */
enum class RecordKind { DAY, WEEK, TRAINING, MILESTONE, CHAPTER, NOTE, HARD }

/**
 * One record of the archive. [words] are extra words a search may find it by (the mode, the source,
 * the goal titles of the week); the date words are added by [BehindSearch].
 */
data class BehindRecord(
    val kind: RecordKind,
    val day: LocalDate,
    val title: String,
    val detail: String? = null,
    val words: String = "",
)

/** The filter chips (A66 §6.4, mockup 08): one row, «Трудные периоды» off by default (§6.5 п. 1). */
enum class BehindFilter(val label: String, val kinds: Set<RecordKind>) {
    TRAININGS(BehindCopy.F_TRAININGS, setOf(RecordKind.TRAINING)),
    WEEKS(BehindCopy.F_WEEKS, setOf(RecordKind.WEEK)),
    MILESTONES(BehindCopy.F_MILESTONES, setOf(RecordKind.MILESTONE, RecordKind.CHAPTER)),
    NOTES(BehindCopy.F_NOTES, setOf(RecordKind.NOTE)),
    HARD(BehindCopy.F_HARD, setOf(RecordKind.HARD)),
}

// ── The year calendar ──────────────────────────────────────────────────────────────────────────

enum class CellKind {
    /** The month has no such day (30 февраля): an empty slot, nothing drawn. */
    NONE,

    /** Before the archive begins or after today: a faint slot, no claim either way. */
    QUIET,

    /** A day inside the archive without data: the dashed «нет данных» (A66 §1.1). */
    NO_DATA,

    /** The light of the mode of the day. */
    MODE,

    /** Pause, illness, vacation: hatched (A66 §6.2). */
    PAUSE,
}

data class CalendarCell(val day: LocalDate?, val kind: CellKind, val mode: DayMode? = null, val training: Boolean = false, val today: Boolean = false)

/** One month row of 31 slots; [spoken] is its one TalkBack node (A66 §6.9). */
data class MonthRow(val month: YearMonth, val label: String, val cells: List<CalendarCell>, val spoken: String)

object YearCalendar {
    const val SLOTS = 31

    /**
     * The 12 × 31 calendar of [year] (A66 §6.2, mockup 08). [since] is the first day the archive
     * holds (the oldest record; D-057 п. 5 keeps only 28–35 days of WHOOP, so older days are quiet,
     * not «no data»); days after [today] are quiet — no future cells are claimed (§6.7).
     */
    fun year(year: Int, today: LocalDate, days: Map<LocalDate, DayRecord>, since: LocalDate?): List<MonthRow> =
        (1..12).map { month(YearMonth.of(year, it), today, days, since) }

    fun month(ym: YearMonth, today: LocalDate, days: Map<LocalDate, DayRecord>, since: LocalDate?): MonthRow {
        val cells = (1..SLOTS).map { d ->
            if (d > ym.lengthOfMonth()) return@map CalendarCell(null, CellKind.NONE)
            val day = ym.atDay(d)
            val r = days[day]
            val kind = when {
                day.isAfter(today) -> CellKind.QUIET
                r?.pause == true -> CellKind.PAUSE
                r?.mode != null -> CellKind.MODE
                since == null || day.isBefore(since) -> CellKind.QUIET
                else -> CellKind.NO_DATA
            }
            CalendarCell(day, kind, r?.mode.takeIf { kind == CellKind.MODE }, r?.training == true && !day.isAfter(today), day == today)
        }
        return MonthRow(ym, RuWords.monthShort(ym.monthValue), cells, spoken(ym, cells))
    }

    /** «Октябрь: Ровно 18, Бережно 9, тренировок 12, пауза 2 дн., нет данных 3». */
    fun spoken(ym: YearMonth, cells: List<CalendarCell>): String {
        val parts = buildList {
            DayMode.entries.forEach { m -> cells.count { it.mode == m }.takeIf { it > 0 }?.let { add("${m.word} $it") } }
            cells.count { it.training }.takeIf { it > 0 }?.let { add("${BehindCopy.A11Y_TRAININGS} $it") }
            cells.count { it.kind == CellKind.PAUSE }.takeIf { it > 0 }?.let { add("${BehindCopy.A11Y_PAUSE} ${RuWords.days(it)}") }
            cells.count { it.kind == CellKind.NO_DATA }.takeIf { it > 0 }?.let { add("${BehindCopy.NO_DATA} $it") }
        }
        return RuWords.month(ym.monthValue) + ": " + (parts.joinToString(", ").ifEmpty { BehindCopy.A11Y_EMPTY_MONTH })
    }

    /** The years the archive spans, oldest first; at least the year of [today]. */
    fun years(today: LocalDate, since: LocalDate?): List<Int> = ((since?.year ?: today.year).coerceAtMost(today.year)..today.year).toList()
}

// ── The period ─────────────────────────────────────────────────────────────────────────────────

/** The selected period: [anchor] is any day of the month or year shown. */
data class PeriodSel(val period: BehindPeriod, val anchor: LocalDate) {
    fun contains(d: LocalDate): Boolean = when (period) {
        BehindPeriod.MONTH -> d.year == anchor.year && d.month == anchor.month
        BehindPeriod.YEAR -> d.year == anchor.year
        BehindPeriod.ALL -> true
    }

    /** «2026», «Ноябрь 2026», «Всё время». */
    val label: String
        get() = when (period) {
            BehindPeriod.MONTH -> "${RuWords.month(anchor.monthValue)} ${anchor.year}"
            BehindPeriod.YEAR -> anchor.year.toString()
            BehindPeriod.ALL -> BehindCopy.ALL_TIME
        }

    /** ‹ ›: a month or a year back or forward, never past [today] nor before [since]. */
    fun step(by: Int, today: LocalDate, since: LocalDate?): PeriodSel? {
        val next = when (period) {
            BehindPeriod.MONTH -> anchor.plusMonths(by.toLong())
            BehindPeriod.YEAR -> anchor.plusYears(by.toLong())
            BehindPeriod.ALL -> return null
        }
        val first = since ?: today
        val ok = when (period) {
            BehindPeriod.MONTH -> !YearMonth.from(next).isAfter(YearMonth.from(today)) && !YearMonth.from(next).isBefore(YearMonth.from(first))
            else -> next.year in first.year..today.year
        }
        return if (ok) copy(anchor = next) else null
    }
}

// ── The facts of the hero ──────────────────────────────────────────────────────────────────────

/** A fact of the period (A66 §6.2): a big number, its words, and the filter its tap opens. */
data class FactCard(val number: Int, val words: String, val filter: BehindFilter)

object PeriodFacts {
    /**
     * Three facts of the period, only positive and cumulative — they never go down and count no
     * defeat (§6.5 п. 4, Z-05). A fact with nothing to count yet is left out, not shown as «0».
     * With `ED_CARE` the count of workouts gives way to the milestones (§6.5 п. 7).
     */
    fun of(records: List<BehindRecord>, sel: PeriodSel, edCare: Boolean): List<FactCard> {
        val r = records.filter { sel.contains(it.day) }
        val trainings = r.count { it.kind == RecordKind.TRAINING }
        val weeks = r.count { it.kind == RecordKind.WEEK }
        val milestones = r.count { it.kind == RecordKind.MILESTONE }
        val chapters = r.filter { it.kind == RecordKind.CHAPTER }
        val first = if (edCare) FactCard(milestones, BehindCopy.milestones(milestones), BehindFilter.MILESTONES)
        else FactCard(trainings, BehindCopy.trainings(trainings), BehindFilter.TRAININGS)
        return listOf(
            first,
            FactCard(weeks, BehindCopy.weeksWithGoals(weeks), BehindFilter.WEEKS),
            FactCard(chapters.size, BehindCopy.chapters(chapters.size, started = chapters.all { it.detail == BehindCopy.CHAPTER_STARTED }), BehindFilter.MILESTONES),
        ).filter { it.number > 0 }
    }
}

// ── The search ─────────────────────────────────────────────────────────────────────────────────

data class SearchGroup(val kind: RecordKind, val title: String, val items: List<BehindRecord>, val more: Int)

object BehindSearch {
    const val PER_GROUP = 3

    /** The order of the groups (A66 §6.3). */
    val ORDER = listOf(RecordKind.DAY, RecordKind.WEEK, RecordKind.TRAINING, RecordKind.CHAPTER, RecordKind.MILESTONE, RecordKind.NOTE)

    private val SPLIT = Regex("[^\\p{L}\\p{Nd}-]+")

    fun normalize(s: String): String = s.lowercase().replace('ё', 'е')

    fun tokens(query: String): List<String> = normalize(query).split(SPLIT).map { it.trim('-') }.filter { it.isNotEmpty() }

    fun haystack(r: BehindRecord): String = normalize(listOfNotNull(r.title, r.detail, r.words, RuWords.dateWords(r.day)).joinToString(" "))

    /**
     * Search by words over what the archive holds (A66 §6.3; Room FTS comes later): every word of
     * [query] must be found in a record — its title, detail, words or date words («ноябрь», «вс»,
     * «2026»). Трудные периоды are never searched: they live behind their filter only (§6.5 п. 1, 7).
     * Newest first, [PER_GROUP] per group and «ещё N».
     */
    fun search(records: List<BehindRecord>, query: String, sel: PeriodSel): List<SearchGroup> {
        val words = tokens(query).takeIf { it.isNotEmpty() } ?: return emptyList()
        val found = records.asSequence()
            .filter { it.kind != RecordKind.HARD && sel.contains(it.day) }
            .filter { r -> haystack(r).let { h -> words.all { it in h } } }
            .sortedByDescending { it.day }
            .toList()
        return ORDER.mapNotNull { k ->
            val all = found.filter { it.kind == k }
            if (all.isEmpty()) null else SearchGroup(k, BehindCopy.group(k), all.take(PER_GROUP), all.size - minOf(all.size, PER_GROUP))
        }
    }

    /** The records of a filter in the period, newest first. */
    fun filtered(records: List<BehindRecord>, filter: BehindFilter, sel: PeriodSel): List<BehindRecord> =
        records.filter { it.kind in filter.kinds && sel.contains(it.day) }.sortedByDescending { it.day }
}

// ── Failures without shame ─────────────────────────────────────────────────────────────────────

object BehindSafety {
    /**
     * «Трудные периоды» is not offered in hard states (A66 §6.5 п. 7): a crisis, STORM, grief,
     * illness, the return and the light week — every flag of the gentle group and the holds of the
     * Path on the bus — and on a day of Бережно. Read from the consumer PATH of the one bus.
     */
    fun hardAvailable(state: SafetyState, todayMode: DayMode?): Boolean {
        if (todayMode == DayMode.BEREZHNO) return false
        if (state.crisis) return false
        if (state.flags.keys.any { it.group == FlagGroup.GENTLE || it == SafetyFlag.SICK }) return false
        val r = state.restrictions(SafetyConsumer.PATH)
        return Restriction.PATH_GENTLE !in r && Restriction.PATH_STANDS !in r && Restriction.SOBER !in r
    }

    /** `ED_CARE` (and after it, until weight is taken back) hides the count of workouts in the hero. */
    fun edCare(state: SafetyState): Boolean = Restriction.HIDE_WEIGHT in state.restrictions(SafetyConsumer.PATH)

    /**
     * A week that went otherwise (§6.5 п. 2, 5): only with its context from the data — days of
     * Бережно, days on pause. No count of defeats, no «не выполнил(а)».
     */
    fun hardWeek(monday: LocalDate, days: List<DayRecord>): BehindRecord? {
        val week = days.filter { !it.day.isBefore(monday) && !it.day.isAfter(monday.plusDays(6)) }
        val gentle = week.count { it.mode == DayMode.BEREZHNO }
        val pause = week.count { it.pause }
        if (gentle < HARD_GENTLE_DAYS && pause == 0) return null
        val context = buildList {
            if (gentle > 0) add("${DayMode.BEREZHNO.word} ${RuWords.days(gentle)}")
            if (pause > 0) add("${BehindCopy.A11Y_PAUSE} ${RuWords.days(pause)}")
        }.joinToString(", ")
        return BehindRecord(
            RecordKind.HARD, monday,
            BehindCopy.week(weekNumber(monday), monday),
            "${BehindCopy.WENT_OTHERWISE} · $context",
        )
    }

    const val HARD_GENTLE_DAYS = 3

    fun weekNumber(monday: LocalDate): Int = monday.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
}

/** The draft texts of «За плечами» (A66 §6.8): on review of Agents 16 and 10. */
object BehindCopy {
    const val TITLE = "За плечами"
    const val SEARCH = "Найти день, тренировку, неделю…"
    const val MONTH = "Месяц"
    const val YEAR = "Год"
    const val ALL_TIME = "Всё время"
    const val F_TRAININGS = "Тренировки"
    const val F_WEEKS = "Недели"
    const val F_MILESTONES = "Вехи"
    const val F_NOTES = "Заметки"
    const val F_HARD = "Трудные периоды"
    const val HARD_LATER = "Трудные периоды откроются, когда станет легче."
    const val NO_DATA = "нет данных"
    const val A11Y_TRAININGS = "тренировок"
    const val A11Y_PAUSE = "пауза"
    const val A11Y_EMPTY_MONTH = "записей нет"
    const val EMPTY = "Здесь соберётся всё прожитое с HoT. Отправная точка — сегодня."
    const val NOTHING_IN_FILTER = "За этот период здесь пока пусто."
    const val SEARCH_ALL = "Искать за всё время"
    const val WENT_OTHERWISE = "неделя пошла иначе"
    const val CHAPTER_STARTED = "начата"
    const val CHAPTER_TAKEN = "взята"
    const val NOTE_PRIDE = "Чем горжусь"
    const val NOTE_CARRY = "Что несу дальше"
    const val TRAINING = "Тренировка"
    const val TRAINING_WHOOP = "Тренировка · WHOOP"
    const val TRAINING_HOT = "Тренировка · HoT"
    const val DATA_WINDOW = "Данные WHOOP хранятся 28–35 дней: что старше — пусто, а не потеряно."
    const val LEGEND_NO_DATA = "нет данных"
    const val LEGEND_PAUSE = "пауза"
    const val PERIOD_PREV = "Назад по времени"
    const val PERIOD_NEXT = "Вперёд по времени"
    const val CLEAR = "Очистить поиск"

    fun nothingFound(period: String) = "Ничего не нашлось за $period."

    fun more(n: Int) = "ещё $n ›"

    fun trainings(n: Int) = RuWords.plural(n, "тренировка", "тренировки", "тренировок")

    fun weeksWithGoals(n: Int) = RuWords.plural(n, "неделя", "недели", "недель") + " с целями"

    fun milestones(n: Int) = RuWords.plural(n, "веха", "вехи", "вех")

    /** «1 глава начата» while the only chapter is under way; «3 главы» otherwise. */
    fun chapters(n: Int, started: Boolean) = if (n == 1 && started) "глава начата" else RuWords.plural(n, "глава", "главы", "глав")

    fun released(weeks: Int) = "отпущена · была в работе ${weeks.coerceAtLeast(1)} ${RuWords.plural(weeks.coerceAtLeast(1), "неделю", "недели", "недель")}"

    fun week(number: Int, monday: LocalDate) = "Неделя $number · ${RuWords.range(monday, monday.plusDays(6))}"

    fun milestone(n: Int, goal: String) = "Веха $n · $goal"

    fun chapter(goal: String) = "Глава · $goal"

    fun group(k: RecordKind): String = when (k) {
        RecordKind.DAY -> "Дни"
        RecordKind.WEEK -> "Недели"
        RecordKind.TRAINING -> "Тренировки"
        RecordKind.CHAPTER -> "Цели"
        RecordKind.MILESTONE -> "Вехи"
        RecordKind.NOTE -> "Заметки"
        RecordKind.HARD -> F_HARD
    }

    fun all(): List<String> = listOf(
        TITLE, SEARCH, MONTH, YEAR, ALL_TIME, F_TRAININGS, F_WEEKS, F_MILESTONES, F_NOTES, F_HARD, HARD_LATER, NO_DATA,
        A11Y_TRAININGS, A11Y_PAUSE, A11Y_EMPTY_MONTH, EMPTY, NOTHING_IN_FILTER, SEARCH_ALL, WENT_OTHERWISE, CHAPTER_STARTED,
        CHAPTER_TAKEN, NOTE_PRIDE, NOTE_CARRY, TRAINING, TRAINING_WHOOP, TRAINING_HOT, DATA_WINDOW, LEGEND_NO_DATA, LEGEND_PAUSE,
        PERIOD_PREV, PERIOD_NEXT, CLEAR, nothingFound("2026"), more(4), trainings(38), weeksWithGoals(3), milestones(2), chapters(1, true),
        chapters(3, false), released(5), week(45, LocalDate.of(2026, 11, 2)), milestone(1, "Силы на вечер"), chapter("Силы на вечер"),
    ) + RecordKind.entries.map(::group)
}
