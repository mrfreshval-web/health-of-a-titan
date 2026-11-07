package com.healthofatitan.app.domain.checkin

import java.time.LocalDate
import kotlin.math.abs

/**
 * The live Titan (И2.14′, A44 §3): every answer changes it at once. Low answers give less light,
 * never a «bad» look and never red (A44 §3, A11 §12.1).
 *
 * @param light 0…1 how far the light fills the figure from below (Заряд)
 * @param sky the weather over the head (Погода), null before the answer
 * @param amber body zones lit amber («забито → где»)
 * @param sick «простуда»: a soft halo instead of zones
 * @param blur 0…1 how soft the figure is (Голова: туман → ясно)
 * @param lift −1…1 lower or higher (Тяга к делу)
 */
data class TitanLook(
    val light: Float = 0.5f,
    val sky: Sky? = null,
    val amber: Set<BodyArea> = emptySet(),
    val sick: Boolean = false,
    val blur: Float = 0f,
    val lift: Float = 0f,
) {
    companion object {
        /** Folds the answers so far; a skip leaves the neutral look of that card. */
        fun of(answers: Collection<Answer>): TitanLook {
            var look = TitanLook()
            for (a in answers) {
                if (a.skipped) continue
                look = when (a.card) {
                    CardId.CHARGE, CardId.CHARGE_AFTER -> a.level?.let { look.copy(light = lightOf(it)) } ?: look
                    CardId.WEATHER, CardId.EVENING_WEATHER -> look.copy(sky = Sky.of(a.level))
                    CardId.BODY -> when (BodyFeel.of(a.level)) {
                        BodyFeel.SORE -> look.copy(amber = a.choices.mapNotNull { c -> BodyArea.entries.firstOrNull { it.name == c } }.toSet().ifEmpty { setOf(BodyArea.HIPS) }, sick = false)
                        BodyFeel.SICK -> look.copy(sick = true, amber = emptySet())
                        else -> look.copy(sick = false, amber = emptySet())
                    }
                    CardId.HEAD -> when (Head.of(a.level)) {
                        Head.FOG -> look.copy(blur = 1f)
                        Head.MID -> look.copy(blur = 0.4f)
                        else -> look.copy(blur = 0f)
                    }
                    CardId.DRIVE -> when (Drive.of(a.level)) {
                        Drive.NONE -> look.copy(lift = -1f)
                        Drive.EAGER -> look.copy(lift = 1f)
                        else -> look.copy(lift = 0f)
                    }
                    else -> look
                }
            }
            return look
        }

        /** Level 1 still glows a little: the floor of the light, as for a closed day. */
        fun lightOf(level: Int): Float = 0.2f + 0.8f * (level.coerceIn(Charge.MIN, Charge.MAX) - 1) / 4f
    }
}

/** «Сошлись» or «разошлись» (A44 §3 «ножницы»). The feeling is never «wrong». */
enum class Agreement { MATCH, YOU_LOWER, YOU_HIGHER }

/**
 * Раскрытие «ты ↔ WHOOP» (И2.13′): the person's Заряд (1–5) next to WHOOP's (the HoT Заряд ECAP@1
 * level −2…+2 → 1…5). One level apart still counts as «сошлись».
 */
object Reveal {
    fun dataLevel(ecapLevel: Int?): Int? = ecapLevel?.let { (it + 3).coerceIn(Charge.MIN, Charge.MAX) }

    fun agreement(you: Int, data: Int): Agreement = when {
        abs(you - data) <= 1 -> Agreement.MATCH
        you < data -> Agreement.YOU_LOWER
        else -> Agreement.YOU_HIGHER
    }

    /** «Совпали с WHOOP 16 из 23»: mornings with both values, and how many matched. */
    fun matched(pairs: List<Pair<Int, Int>>): Pair<Int, Int> =
        pairs.count { (y, d) -> agreement(y, d) == Agreement.MATCH } to pairs.size
}

/** «Утра с тобой — 23 из 30» (A44 §3): a count, never a streak that breaks (A42 §6). */
object Mornings {
    const val WINDOW = 30

    fun withYou(days: Collection<LocalDate>, today: LocalDate): Int {
        val from = today.minusDays(WINDOW - 1L)
        return days.toSet().count { !it.isBefore(from) && !it.isAfter(today) }
    }
}

/**
 * «После ритуала» (И2.19): the delta of Заряд between the two measures. Honest: alertness after
 * waking rises by itself (sleep inertia, §6.3), so the delta alone is not a discovery.
 */
object RitualDelta {
    fun of(wake: Int?, after: Int?): Int? = if (wake == null || after == null) null else after - wake
}

/** What the mentor's one line says after the reveal (И2.16). The words live in CheckinTexts. */
enum class LineContext {
    MATCH, YOU_LOWER, YOU_HIGHER, WAITING, NO_WHOOP, QUICK, GENTLE,

    /** The answers of the «Только ты» card and alcohol: only a neutral «Принято», no character. */
    NEUTRAL,
    RITUAL_UP, RITUAL_SAME, EVENING_LIGHT, EVENING_HEAVY,
}

object MentorLine {
    /**
     * The context of the morning line. Бережно (GENTLE mode) or a crisis check: only supportive
     * lines (A44 §6.6). Without a reveal yet: the waiting line.
     */
    fun morning(you: Int?, data: Int?, gentle: Boolean, quick: Boolean, whoopConnected: Boolean): LineContext = when {
        gentle -> LineContext.GENTLE
        !whoopConnected -> LineContext.NO_WHOOP
        you == null -> LineContext.QUICK
        data == null -> LineContext.WAITING
        else -> when (Reveal.agreement(you, data)) {
            Agreement.MATCH -> if (quick) LineContext.QUICK else LineContext.MATCH
            Agreement.YOU_LOWER -> LineContext.YOU_LOWER
            Agreement.YOU_HIGHER -> LineContext.YOU_HIGHER
        }
    }

    fun ritual(delta: Int?): LineContext = if ((delta ?: 0) > 0) LineContext.RITUAL_UP else LineContext.RITUAL_SAME

    fun evening(sky: Sky?): LineContext = if (sky != null && sky.level <= Sky.RAIN.level) LineContext.EVENING_HEAVY else LineContext.EVENING_LIGHT

    /** The same day always gives the same line of a list (no flicker on recomposition). */
    fun pick(size: Int, day: LocalDate): Int = if (size <= 0) 0 else Math.floorMod(day.toEpochDay(), size.toLong()).toInt()
}
