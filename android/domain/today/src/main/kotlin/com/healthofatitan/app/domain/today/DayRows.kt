package com.healthofatitan.app.domain.today

import java.util.Locale
import kotlin.math.roundToInt

/**
 * The words of the block «День» (A66 §3.5). DRAFT: wording waits for Agents 16 and 10; every line
 * about load, food, drink and rest waits for Agent 3 (A66 §3.5 «Условия Агента 3»). The texts never
 * say that a pause or a breather «restores» the body (condition 4) and never name a diagnosis.
 */
object DayCopy {
    const val TITLE = "День"

    /** Always in the header (A66 §3.5): the direct answer to «оно не обнуляется?». */
    const val RESETS = "обнулится после сна"

    const val LOAD = "Нагрузка"
    const val FOOD = "Питание"
    const val REST = "Отдых"

    const val GLYPH_LOAD = "▲"
    const val GLYPH_FOOD = "◒"
    const val GLYPH_REST = "☾"

    /** Condition 3 of Agent 3: Бережно, sickness, after STORM, red flags — no «сколько осталось». */
    const val BY_FEEL = "по самочувствию"
    const val IN_CORRIDOR = "в коридоре"
    const val ABOVE_CORRIDOR = "выше коридора"
    const val NO_VALUE = "—"
    const val ESTIMATE = "оценка"
    const val FOOD_ADD = "+ Еда"
    const val BREATHER_DONE = "передышка ✓"
    const val DAY_CLOSED = "день закрыт ✓"

    /** Sickness (A66 §3.8): rest is the plan, it is counted. */
    const val REST_COUNTED = "засчитан"
    const val DRINK_BY_THIRST = "питьё — по жажде"

    fun ofCorridor(value: String, corridor: String): String = "$value из $corridor"

    /** Condition 1 of Agent 3: without ≥ 5 past walks only the Strain to the corridor. */
    fun toCorridor(rest: String): String = "до коридора ≈ $rest"

    fun toCorridorWalk(minutes: Int): String = "до коридора ≈ $minutes мин быстрой ходьбы"

    /** Condition 2 of Agent 3: without WHOOP only minutes of activity, marked as an estimate. */
    fun activity(minutes: Int): String = "$minutes мин активности"

    fun meals(done: Int, planned: Int): String = "$done из $planned ${mealsGenitive(planned)}"

    /** Return after 7+ days (A8): no denominators. */
    fun mealsNoTotal(done: Int): String = "$done ${mealsCount(done)}"

    fun drink(glasses: Int): String = "питьё: $glasses ${glassesWord(glasses)}"

    fun evening(screensFrom: String?, bedtime: String?): String? =
        listOfNotNull(screensFrom?.let { "экраны с $it" }, bedtime?.let { "отбой $it" }).joinToString(" · ").ifEmpty { null }

    /** «из 1 приёма», «из 4 приёмов». */
    fun mealsGenitive(n: Int): String = if (n % 10 == 1 && n % 100 != 11) "приёма" else "приёмов"

    /** «1 приём», «2 приёма», «5 приёмов». */
    fun mealsCount(n: Int): String = plural(n, "приём", "приёма", "приёмов")

    fun glassesWord(n: Int): String = plural(n, "стакан", "стакана", "стаканов")

    private fun plural(n: Int, one: String, few: String, many: String): String {
        val mod100 = n % 100
        val mod10 = n % 10
        return when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }

    /** Every user-facing string, for the stop-dictionary test (T-13). */
    fun all(): List<String> = listOf(
        TITLE, RESETS, LOAD, FOOD, REST, BY_FEEL, IN_CORRIDOR, ABOVE_CORRIDOR, ESTIMATE, FOOD_ADD, BREATHER_DONE,
        DAY_CLOSED, REST_COUNTED, DRINK_BY_THIRST, ofCorridor("9,8", "10–14"), toCorridor("0,2"), toCorridorWalk(20),
        activity(40), meals(2, 4), meals(1, 1), mealsNoTotal(2), drink(1), drink(3), drink(5), evening("22:00", "23:05")!!,
    )
}

enum class DayRowId(val key: String) {
    LOAD("load"),
    FOOD("food"),
    REST("rest"),
}

/** Where a tap on a row leads (A66 §3.5); the screen maps it to a route. */
enum class DayTap {
    /** Today's training (its screen holds the workouts of the day). */
    TRAINING,

    /** «Почему так»: why the corridor is such. */
    WHY,

    /** The existing food screen of the day (A41). */
    NUTRITION,
    BREATHER,

    /** «Закрыть день» unfolds in «Ритм дня». */
    CLOSE_DAY,
}

/** One segment of a row: done, still open, counted in the morning, or not recorded at all. */
enum class DaySeg { DONE, OPEN, LATER, UNKNOWN }

/** The Strain bar of «Нагрузка»: the value and the corridor ends as shares of the 0–21 scale. */
data class LoadBar(val value: Float, val from: Float?, val to: Float?, val above: Boolean)

data class DayRowView(
    val id: DayRowId,
    val label: String,
    val glyph: String,
    /** The number on the right, one line; null skips it (nothing to say). */
    val value: String?,
    /** The second line: what to do, the drink hint, the evening windows; null skips it. */
    val hint: String?,
    /** 0…1, how much of the row is done. */
    val fill: Float,
    /** The row counts in the share of the day (it has a norm to count against). */
    val counted: Boolean,
    val estimate: Boolean = false,
    val bar: LoadBar? = null,
    val segments: List<DaySeg> = emptyList(),
    val tap: DayTap,
    val spoken: String,
)

data class DayBlockView(
    /** False on the sober screen (STORM, SAF-01, crisis) and on a pause: the block is hidden (A66 §3.8). */
    val shown: Boolean,
    val rows: List<DayRowView>,
    /** The drink hint (A66 §3.5: not counted); under the food row, or alone when there is none. */
    val drink: String?,
    /** «+ Еда» marks this meal slot; null hides the button. */
    val nextMeal: String? = null,
) {
    val share: Float get() = DayRules.share(rows)

    companion object {
        val HIDDEN = DayBlockView(shown = false, rows = emptyList(), drink = null)
    }
}

data class DayInput(
    val scenario: Scenario,
    val mode: DayMode?,
    val whoopConnected: Boolean,
    /** WHOOP `cycle.strain` of today, memory only. */
    val strain: Double? = null,
    /** Without WHOOP: minutes of today's trainings marked done, by their planned slot (an estimate). */
    val activityMinutes: Int? = null,
    /**
     * Strain per minute of the person's past walks (condition 1 of Agent 3). Nothing stores it yet,
     * so «Сегодня» passes an empty list and the minutes are never shown.
     */
    val walkStrainPerMinute: List<Double> = emptyList(),
    /** The day is SICK (A60): only rest and the drink hint. */
    val sick: Boolean = false,
    /** A safety rule fired today (SAF-03, SAF-04): no «сколько осталось». */
    val redFlag: Boolean = false,
    val safety: Safety = Safety.NONE,
    val mealsMarked: Int = 0,
    val mealsPlanned: Int = SupportNorms.MEALS_DEFAULT,
    val food: TodayFood? = null,
    /** The slot «+ Еда» marks; null when every meal is marked. */
    val nextMeal: String? = null,
    val water: Int = 0,
    /** «Закрыть день» is answered for today. */
    val dayClosed: Boolean = false,
    /** «Закрыть день» is due now (its evening, not answered): a tap on «Отдых» opens it. */
    val closeDue: Boolean = false,
    /** A breather of today; null = not recorded (nothing stores a breather yet). */
    val breatherDone: Boolean? = null,
    val screensFromMin: Int? = null,
    val bedtimeMin: Int? = null,
    /** Today's plan has a training. */
    val trainingToday: Boolean = false,
)

/**
 * The block «День» (A66 §3.5, §3.8): Нагрузка · Питание · Отдых. Pure, numbers only from what the
 * input holds; a missing number is «—» or no caption at all.
 *
 * The corridor of the load is [SupportNorms.effortCorridor] — the draft table of the mode, **ждёт
 * Агентов 12 и 3** — and applies to WHOOP Strain only (condition 2 of Agent 3).
 */
object DayRules {
    /** WHOOP Strain scale. */
    const val STRAIN_MAX = 21.0

    /** Condition 1 of Agent 3: minutes of walking only from ≥ 5 past walks of the person. */
    const val MIN_WALKS = 5

    /** «≈ 20 мин»: the minutes are rounded to 5. */
    const val WALK_ROUND = 5

    fun gentle(i: DayInput): Boolean = i.scenario == Scenario.GENTLE || i.mode == DayMode.BEREZHNO

    /** Condition 3 of Agent 3: no «сколько осталось» — only «по самочувствию». */
    fun byFeel(i: DayInput): Boolean = gentle(i) || i.sick || i.redFlag || i.safety != Safety.NONE

    /** ED_CARE (A60 §3.4): no food row, the share by two rows. */
    fun foodHidden(food: TodayFood?): Boolean = food?.hideCounts == true || food?.quiet == true

    fun build(i: DayInput): DayBlockView {
        if (i.scenario == Scenario.SAFETY || i.safety.sober) return DayBlockView.HIDDEN
        val drink = if (i.water > 0) DayCopy.drink(i.water) else DayCopy.DRINK_BY_THIRST
        val rest = rest(i)
        if (i.sick) return DayBlockView(shown = true, rows = listOf(rest), drink = drink)
        val load = load(i)
        val food = if (foodHidden(i.food)) null else food(i)
        val rows = if (gentle(i)) listOfNotNull(rest, load, food) else listOfNotNull(load, food, rest)
        return DayBlockView(shown = true, rows = rows, drink = drink, nextMeal = i.nextMeal.takeIf { food != null })
    }

    /** The share of the closed day (A66 §1.1): the mean of the counted rows, each at most 1. */
    fun share(rows: List<DayRowView>): Float {
        val counted = rows.filter { it.counted }
        if (counted.isEmpty()) return 0f
        return counted.sumOf { it.fill.coerceIn(0f, 1f).toDouble() }.toFloat() / counted.size
    }

    /**
     * «≈ N мин быстрой ходьбы» from the person's own walks: the mean Strain of a minute of their
     * walking. Strain is logarithmic, so there is no general formula (Agent 3); null with < 5 walks.
     */
    fun walkMinutes(rest: Double, walks: List<Double>): Int? {
        val own = walks.filter { it > 0.0 }
        if (own.size < MIN_WALKS || rest <= 0.0) return null
        val raw = rest / own.average()
        return (((raw / WALK_ROUND).roundToInt()).coerceAtLeast(1) * WALK_ROUND)
    }

    fun load(i: DayInput): DayRowView {
        val tap = if (i.trainingToday) DayTap.TRAINING else DayTap.WHY
        val feel = byFeel(i)
        val strain = i.strain?.takeIf { i.whoopConnected }
        if (strain == null) {
            if (!i.whoopConnected) {
                // condition 2: no Strain corridor for the watch or sRPE — minutes of activity, an estimate
                val value = i.activityMinutes?.takeIf { it > 0 }?.let(DayCopy::activity) ?: DayCopy.NO_VALUE
                val hint = if (feel) DayCopy.BY_FEEL else null
                return DayRowView(
                    id = DayRowId.LOAD, label = DayCopy.LOAD, glyph = DayCopy.GLYPH_LOAD, value = value, hint = hint,
                    fill = 0f, counted = false, estimate = true, tap = tap,
                    spoken = spoken(DayCopy.LOAD, value, hint, estimate = true),
                )
            }
            val hint = if (feel) DayCopy.BY_FEEL else null
            return DayRowView(
                id = DayRowId.LOAD, label = DayCopy.LOAD, glyph = DayCopy.GLYPH_LOAD, value = DayCopy.NO_VALUE, hint = hint,
                fill = 0f, counted = !feel, tap = tap, spoken = spoken(DayCopy.LOAD, DayCopy.NO_VALUE, hint, estimate = false),
            )
        }
        val corridor = SupportNorms.effortCorridor(i.mode)
        // Бережно keeps its lowered corridor (A66 §3.8); a red flag, sickness or a storm has none at all
        val withCorridor = !i.redFlag && i.safety == Safety.NONE && i.scenario != Scenario.RETURN
        val value = if (withCorridor) DayCopy.ofCorridor(one(strain), range(corridor)) else one(strain)
        val hint = when {
            feel -> DayCopy.BY_FEEL
            i.scenario == Scenario.RETURN -> null
            strain < corridor.start -> {
                val left = corridor.start - strain
                walkMinutes(left, i.walkStrainPerMinute)?.let(DayCopy::toCorridorWalk) ?: DayCopy.toCorridor(one(left))
            }
            strain <= corridor.endInclusive -> DayCopy.IN_CORRIDOR
            else -> DayCopy.ABOVE_CORRIDOR
        }
        val bar = LoadBar(
            value = (strain / STRAIN_MAX).toFloat().coerceIn(0f, 1f),
            from = if (withCorridor) (corridor.start / STRAIN_MAX).toFloat() else null,
            to = if (withCorridor) (corridor.endInclusive / STRAIN_MAX).toFloat() else null,
            above = withCorridor && strain > corridor.endInclusive,
        )
        return DayRowView(
            id = DayRowId.LOAD, label = DayCopy.LOAD, glyph = DayCopy.GLYPH_LOAD, value = value, hint = hint,
            fill = if (withCorridor) (strain / corridor.start).toFloat().coerceIn(0f, 1f) else 0f,
            counted = withCorridor,
            bar = bar, tap = tap, spoken = spoken(DayCopy.LOAD, value, hint, estimate = false),
        )
    }

    fun food(i: DayInput): DayRowView {
        val planned = (i.food?.mealsPlanned ?: i.mealsPlanned).coerceAtLeast(1)
        val done = i.mealsMarked.coerceIn(0, planned)
        val value = if (i.scenario == Scenario.RETURN) DayCopy.mealsNoTotal(done) else DayCopy.meals(done, planned)
        return DayRowView(
            id = DayRowId.FOOD, label = DayCopy.FOOD, glyph = DayCopy.GLYPH_FOOD, value = value, hint = null,
            fill = done.toFloat() / planned, counted = true,
            segments = List(planned) { if (it < done) DaySeg.DONE else DaySeg.OPEN },
            tap = DayTap.NUTRITION, spoken = spoken(DayCopy.FOOD, value, null, estimate = false),
        )
    }

    /**
     * Three segments (A66 §3.5): a breather by day · «Закрыть день» · sleep in the bedtime window
     * (counted in the morning). A behavioural goal, not a measure of recovery (condition 4).
     * A segment that is not recorded or not due yet does not count against the row.
     */
    fun rest(i: DayInput): DayRowView {
        val breather = when (i.breatherDone) {
            true -> DaySeg.DONE
            false -> DaySeg.OPEN
            null -> DaySeg.UNKNOWN
        }
        val close = if (i.dayClosed) DaySeg.DONE else DaySeg.OPEN
        val segments = listOf(breather, close, DaySeg.LATER)
        val tap = if (i.closeDue && !i.dayClosed) DayTap.CLOSE_DAY else DayTap.BREATHER
        val hint = DayCopy.evening(i.screensFromMin?.let(Supports::clock), i.bedtimeMin?.let(Supports::clock))
        if (i.sick) {
            return DayRowView(
                id = DayRowId.REST, label = DayCopy.REST, glyph = DayCopy.GLYPH_REST, value = DayCopy.REST_COUNTED, hint = hint,
                fill = 1f, counted = true, segments = segments.map { DaySeg.DONE }, tap = DayTap.BREATHER,
                spoken = spoken(DayCopy.REST, DayCopy.REST_COUNTED, hint, estimate = false),
            )
        }
        val known = segments.filter { it == DaySeg.DONE || it == DaySeg.OPEN }
        val done = known.count { it == DaySeg.DONE }
        val doneWords = listOfNotNull(
            DayCopy.BREATHER_DONE.takeIf { breather == DaySeg.DONE },
            DayCopy.DAY_CLOSED.takeIf { close == DaySeg.DONE },
        )
        // one line at 412 dp (T-10): the latest step on screen, every step in the spoken text
        val value = doneWords.lastOrNull()
        return DayRowView(
            id = DayRowId.REST, label = DayCopy.REST, glyph = DayCopy.GLYPH_REST, value = value, hint = hint,
            fill = if (known.isEmpty()) 0f else done.toFloat() / known.size, counted = known.isNotEmpty(),
            segments = segments, tap = tap,
            spoken = spoken(DayCopy.REST, doneWords.joinToString(", ").ifEmpty { null }, hint, estimate = false),
        )
    }

    private fun spoken(label: String, value: String?, hint: String?, estimate: Boolean): String =
        listOfNotNull(label, value?.replace(DayCopy.NO_VALUE, TodayCopy.NO_DATA), hint, DayCopy.ESTIMATE.takeIf { estimate })
            .joinToString(", ")
            .replace(" ✓", "")

    fun one(d: Double): String = String.format(Locale.ROOT, "%.1f", d).replace('.', ',')

    fun range(r: ClosedFloatingPointRange<Double>): String = "${r.start.roundToInt()}–${r.endInclusive.roundToInt()}"
}
