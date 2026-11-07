package com.healthofatitan.app.domain.checkin

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** One measure of «Погода» (morning or evening) in time order. A skip is not a measure. */
data class WeatherMark(val at: Instant, val sky: Sky, val empty: Boolean = false)

/** One morning for the «на нуле + гроза» rule. */
data class MorningMark(val day: LocalDate, val charge: Int?, val sky: Sky?)

/** Why the storm net fired (stored as a class, never as words, like A07 §4.7). */
enum class StormReason { TWO_STORMS, STORM_EMPTY, LOW_MORNINGS }

/**
 * The crisis net on «Погода» (И2.8′, A44 §6.1). Local, offline, no LLM:
 *  - «гроза» in two measures in a row (morning and evening both count);
 *  - «гроза» + the chip «пусто»;
 *  - Заряд «на нуле» + «гроза» in 3 mornings of the last 5.
 * Then the gentle check «Как ты на самом деле?» (A07 §4.3, level 1), not more often than every
 * 72 hours. A skip is never a signal; «Только ты» never goes into it.
 */
object StormNet {
    val REPEAT: Duration = Duration.ofHours(72)

    /** After the net fires: haiku hidden, discoveries and the collection not shown for 7 days. */
    const val QUIET_DAYS = 7L

    fun check(weather: List<WeatherMark>, mornings: List<MorningMark>, now: Instant, lastShown: Instant?): StormReason? {
        if (lastShown != null && Duration.between(lastShown, now) < REPEAT) return null
        val marks = weather.filter { !it.at.isAfter(now) }.sortedBy { it.at }
        val last = marks.lastOrNull()
        if (last != null && last.sky == Sky.STORM && last.empty) return StormReason.STORM_EMPTY
        if (marks.size >= 2 && marks.takeLast(2).all { it.sky == Sky.STORM }) return StormReason.TWO_STORMS
        val five = mornings.sortedBy { it.day }.takeLast(5)
        if (five.count { it.charge == Charge.MIN && it.sky == Sky.STORM } >= 3) return StormReason.LOW_MORNINGS
        return null
    }

    /** True while the 7 quiet days after a fired net last. */
    fun quiet(lastShown: Instant?, now: Instant): Boolean =
        lastShown != null && Duration.between(lastShown, now) < Duration.ofDays(QUIET_DAYS)
}

/**
 * The way to a specialist (A44 §6.4, Agent 10): «хотелось меньше» ≥ 3 times in 14 days → one soft
 * offer; again not earlier than in 30 days. Pure count, no judgement of what was pulled.
 */
object SpecialistRoute {
    const val TIMES = 3
    const val WINDOW_DAYS = 14L
    const val REPEAT_DAYS = 30L

    fun due(wantLessDays: Collection<LocalDate>, today: LocalDate, lastOffered: LocalDate?): Boolean {
        if (lastOffered != null && lastOffered.plusDays(REPEAT_DAYS).isAfter(today)) return false
        val from = today.minusDays(WINDOW_DAYS - 1)
        return wantLessDays.toSet().count { !it.isBefore(from) && !it.isAfter(today) } >= TIMES
    }
}

/** Where «Это боль» leads (A44 §6.2, Agent 3). No diagnosis, no name of an injury. */
enum class PainRoute {
    /** SAF-01: the emergency line on screen, training paused. */
    EMERGENCY,

    /** TR-09 / F6: no load on the zone today, a doctor if it is sharp or swells. */
    SPARE_ZONE,
}

object PainRules {
    fun route(pain: Pain): PainRoute = when (pain) {
        Pain.CHEST -> PainRoute.EMERGENCY
        Pain.JOINT -> PainRoute.SPARE_ZONE
    }

    /** «Простуда» in the morning or the evening → SAF-03@2 (the evening answer wins when both exist). */
    fun sick(evening: BodyFeel?, morning: BodyFeel?): Boolean = (evening ?: morning) == BodyFeel.SICK

    /** «Забито» + zones → EVE-01 (L1, no load on the zone). */
    fun sore(evening: BodyFeel?, morning: BodyFeel?): Boolean = (evening ?: morning) == BodyFeel.SORE
}
