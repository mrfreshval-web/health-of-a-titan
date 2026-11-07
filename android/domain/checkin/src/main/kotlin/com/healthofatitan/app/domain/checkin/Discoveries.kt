package com.healthofatitan.app.domain.checkin

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random

/**
 * One day for the discoveries (A44 §5): the answers of that date and the HoT-derived WHOOP values
 * of the night that ended that morning. Only z-values and levels, never raw WHOOP data.
 *
 * @param answers the cards answered on [day] (wake, ritual, evening)
 * @param only «Только ты» of [day]; read only when «Замечать связи» is on
 * @param sleepZ SLP@1 z of the night before [day]
 * @param restingZ RHRZ@1 of the night before [day]
 * @param trainedBefore WHOOP saw a workout the day before [day]
 * @param eveningAlcohol servings marked in «Закрыть день» of the day before [day]
 */
data class DayRecord(
    val day: LocalDate,
    val answers: Map<CardId, Answer> = emptyMap(),
    val only: PrivateAnswer? = null,
    val sleepZ: Double? = null,
    val restingZ: Double? = null,
    val trainedBefore: Boolean? = null,
    val eveningAlcohol: Int? = null,
) {
    fun level(card: CardId): Int? = answers[card]?.takeIf { !it.skipped }?.level
    fun choices(card: CardId): Set<String>? = answers[card]?.takeIf { !it.skipped }?.choices
}

/** The unit the outcome is measured in: answer levels (≥ 0.5 level) or WHOOP z (≥ SWC 0.5). */
enum class OutcomeUnit { LEVEL, Z }

/**
 * The pre-registered list of v1 (12 pairs, A44 §5; A25 §5). Nothing outside this list is ever
 * searched for: no fishing in the data.
 *
 * @param better +1 when a higher outcome is the better one (Заряд, Голова, сон), −1 for pulse
 * @param s5 a «Только ты» pair: only with «Замечать связи», shown under the lock
 * @param single a single practice of the ritual: needs ≥ 8 mornings with it alone (§5 method)
 */
enum class Link(val better: Int, val unit: OutcomeUnit, val s5: Boolean = false, val single: RitualItem? = null) {
    SHOWER_CHARGE(+1, OutcomeUnit.LEVEL, single = RitualItem.COLD_SHOWER),
    LIGHT_CHARGE(+1, OutcomeUnit.LEVEL, single = RitualItem.LIGHT),
    BREATH_CHARGE(+1, OutcomeUnit.LEVEL, single = RitualItem.BREATH),
    STACK_HEAD(+1, OutcomeUnit.LEVEL),
    SCREENS_SLEEP(+1, OutcomeUnit.Z),
    SCREENS_CHARGE(+1, OutcomeUnit.LEVEL),
    ALCOHOL_PULSE(-1, OutcomeUnit.Z),
    ALCOHOL_HEAD(+1, OutcomeUnit.LEVEL),
    SWEETS_WEATHER(+1, OutcomeUnit.LEVEL),
    TRAINING_HEAD(+1, OutcomeUnit.LEVEL),
    CLOSENESS_SLEEP(+1, OutcomeUnit.Z, s5 = true),
    PULLS_DRIVE(+1, OutcomeUnit.LEVEL, s5 = true),
}

/** A computed link. [effect] is median(with) − median(without), in the pair's unit. */
data class Discovery(
    val pair: Link,
    val n1: Int,
    val n0: Int,
    val effect: Double,
    val ciLow: Double,
    val ciHigh: Double,
) {
    /** The practice goes with a better outcome (a «good» link); otherwise it is a gentle one. */
    val positive: Boolean get() = effect * pair.better > 0
}

/** A tile of the collection (A44 §3): open, «ещё N утр», or under the lock. */
sealed interface Tile {
    val pair: Link

    data class Open(override val pair: Link, val discovery: Discovery, val shownAt: Instant?) : Tile
    data class Progress(override val pair: Link, val n1: Int, val n0: Int, val left: Int) : Tile {
        /** 0…1 for the progress ring. */
        val fraction: Float get() = ((minOf(n1, Discoveries.MIN_N) + minOf(n0, Discoveries.MIN_N)).toFloat() / (2 * Discoveries.MIN_N))
    }
    data class Locked(override val pair: Link) : Tile
}

/**
 * Discoveries (И2.21, A44 §5 method, Agent 9): within one person, medians «с» and «без», n ≥ 8 and
 * ≥ 8 in 90 days, a bootstrap interval of the difference of medians. A link is shown when the
 * interval does not cross 0 and the effect is ≥ 0.5 level (or ≥ 0.5 z, the SWC). Always «связь,
 * не причина». No AI takes part; the seed is fixed so the same data give the same answer.
 */
object Discoveries {
    const val METHOD = "median-diff-bootstrap@1"
    const val MIN_N = 8
    const val WINDOW_DAYS = 90L
    const val MIN_EFFECT = 0.5
    const val RESAMPLES = 1000
    val EVERY: Duration = Duration.ofDays(7)

    /** Exposure of [r] for [pair]: true «с», false «без», null «не знаем» (skipped, no data). */
    fun exposure(pair: Link, r: DayRecord, prev: DayRecord?): Boolean? {
        val ritual = r.choices(CardId.RITUAL)
        val yesterday = r.choices(CardId.YESTERDAY)
        return when (pair) {
            Link.SHOWER_CHARGE, Link.LIGHT_CHARGE, Link.BREATH_CHARGE -> ritual?.contains(pair.single!!.name)
            // the stack as a set: the morning had its stack (any item), D → Голова next morning
            Link.STACK_HEAD -> prev?.choices(CardId.RITUAL)?.isNotEmpty()
            Link.SCREENS_SLEEP, Link.SCREENS_CHARGE -> yesterday?.contains(Yesterday.SCREENS_LATE.name)
            Link.ALCOHOL_PULSE, Link.ALCOHOL_HEAD -> r.eveningAlcohol?.let { it > 0 } ?: yesterday?.contains(Yesterday.ALCOHOL.name)
            Link.SWEETS_WEATHER -> yesterday?.contains(Yesterday.SWEETS.name)
            Link.TRAINING_HEAD -> r.trainedBefore
            Link.CLOSENESS_SLEEP -> when (r.only?.intimacy) {
                Intimacy.PARTNER, Intimacy.SELF -> true
                Intimacy.NONE -> false
                else -> null
            }
            Link.PULLS_DRIVE -> r.only?.pulls?.takeIf { it.isNotEmpty() }?.let { p -> p.any { it != Pull.NOTHING } }
        }
    }

    fun outcome(pair: Link, r: DayRecord): Double? = when (pair) {
        Link.SHOWER_CHARGE, Link.LIGHT_CHARGE, Link.BREATH_CHARGE ->
            RitualDelta.of(r.level(CardId.CHARGE), r.level(CardId.CHARGE_AFTER))?.toDouble()
        Link.STACK_HEAD, Link.ALCOHOL_HEAD, Link.TRAINING_HEAD -> r.level(CardId.HEAD)?.toDouble()
        Link.SCREENS_SLEEP, Link.CLOSENESS_SLEEP -> r.sleepZ
        Link.SCREENS_CHARGE -> r.level(CardId.CHARGE)?.toDouble()
        Link.ALCOHOL_PULSE -> r.restingZ
        Link.SWEETS_WEATHER -> r.level(CardId.WEATHER)?.toDouble()
        Link.PULLS_DRIVE -> r.level(CardId.DRIVE)?.toDouble()
    }

    /** The «с» and «без» outcomes of [pair] in the last 90 days. */
    fun samples(pair: Link, records: List<DayRecord>, today: LocalDate): Pair<List<Double>, List<Double>> {
        val from = today.minusDays(WINDOW_DAYS - 1)
        val byDay = records.associateBy { it.day }
        val with = mutableListOf<Double>()
        val without = mutableListOf<Double>()
        for (r in records.sortedBy { it.day }) {
            if (r.day.isBefore(from) || r.day.isAfter(today)) continue
            val e = exposure(pair, r, byDay[r.day.minusDays(1)]) ?: continue
            val y = outcome(pair, r) ?: continue
            if (e) with += y else without += y
        }
        return with to without
    }

    /** Mornings with this single practice and none of the other two (§5: no credit to one of several). */
    fun aloneCount(item: RitualItem, records: List<DayRecord>): Int {
        val others = listOf(RitualItem.COLD_SHOWER, RitualItem.LIGHT, RitualItem.BREATH).filter { it != item }.map { it.name }
        return records.count { r ->
            val c = r.choices(CardId.RITUAL) ?: return@count false
            item.name in c && others.none { it in c }
        }
    }

    fun compute(pair: Link, records: List<DayRecord>, today: LocalDate): Discovery? {
        val (with, without) = samples(pair, records, today)
        if (with.size < MIN_N || without.size < MIN_N) return null
        if (pair.single != null && aloneCount(pair.single, records) < MIN_N) return null
        val effect = median(with) - median(without)
        val (lo, hi) = bootstrap(with, without, seed = pair.ordinal * 7919L + with.size * 31L + without.size)
        val significant = lo > 0.0 || hi < 0.0
        if (!significant || abs(effect) < MIN_EFFECT) return null
        return Discovery(pair, with.size, without.size, effect, lo, hi)
    }

    /** All twelve tiles of the collection; S5 pairs under the lock without «Замечать связи». */
    fun collection(records: List<DayRecord>, today: LocalDate, shown: Map<Link, Instant>, links: Boolean): List<Tile> =
        Link.entries.map { pair ->
            if (pair.s5 && !links) return@map Tile.Locked(pair)
            val found = compute(pair, records, today)
            if (found != null && pair in shown) {
                Tile.Open(pair, found, shown[pair])
            } else {
                val (w, wo) = samples(pair, records, today)
                Tile.Progress(pair, w.size, wo.size, (MIN_N - w.size).coerceAtLeast(0) + (MIN_N - wo.size).coerceAtLeast(0))
            }
        }

    /**
     * The next discovery to reveal, or null: not more often than once a week, never during the 7
     * quiet days after the storm net, in Бережно only the good links (A25 §5), S5 only with links.
     */
    fun next(
        records: List<DayRecord>,
        today: LocalDate,
        now: Instant,
        shown: Map<Link, Instant>,
        links: Boolean,
        gentle: Boolean,
        quiet: Boolean,
    ): Discovery? {
        if (quiet) return null
        val last = shown.values.maxOrNull()
        if (last != null && Duration.between(last, now) < EVERY) return null
        return Link.entries.asSequence()
            .filter { it !in shown && (!it.s5 || links) }
            .mapNotNull { compute(it, records, today) }
            .filter { !gentle || it.positive }
            .maxByOrNull { abs(it.effect) }
    }

    fun median(xs: List<Double>): Double {
        val s = xs.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2.0
    }

    /** 95 % percentile interval of median(with*) − median(without*) over [RESAMPLES] resamples. */
    fun bootstrap(with: List<Double>, without: List<Double>, seed: Long): Pair<Double, Double> {
        val rnd = Random(seed)
        val diffs = DoubleArray(RESAMPLES) {
            median(List(with.size) { with[rnd.nextInt(with.size)] }) - median(List(without.size) { without[rnd.nextInt(without.size)] })
        }
        diffs.sort()
        return diffs[(RESAMPLES * 0.025).toInt()] to diffs[(RESAMPLES * 0.975).toInt() - 1]
    }
}
