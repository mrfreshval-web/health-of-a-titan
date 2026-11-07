package com.healthofatitan.app.domain.checkin

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** «Только ты» (§6.4): three independent switches, all off by default (D-036 п. 4). */
data class PrivateLayer(
    val intimacy: Boolean = false,
    val pulls: Boolean = false,
    /** «Замечать связи»: S5 discoveries (близость → сон, «что тянуло» → Тяга) only with this on. */
    val links: Boolean = false,
) {
    val on: Boolean get() = intimacy || pulls
}

/**
 * «Что спрашивать» (A44 §8, sc7). A card that is off disappears from the deck (И2.17′).
 *
 * @param off cards the person switched off (or removed after the 14-day offer)
 * @param drinks false = «не пью»: no alcohol anywhere (§6.5)
 * @param stack the person's own stack items for Р1, as typed (no doses)
 */
data class DeckSettings(
    val off: Set<CardId> = emptySet(),
    val quick: Boolean = false,
    val privateLayer: PrivateLayer = PrivateLayer(),
    val ritual: Boolean = true,
    /** «Напомни через 45 мин» — off by default (§4.2); no new push is ever added by this module. */
    val ritualReminder: Boolean = false,
    val evening: Boolean = true,
    val discoveries: Boolean = true,
    val haptics: Boolean = true,
    val drinks: Boolean = true,
    val stack: List<String> = emptyList(),
) {
    fun isOn(card: CardId): Boolean = card !in off
}

/** What the morning knows before it asks (A44 §1.2, §6.5). */
data class DeckContext(
    val day: LocalDate,
    /** WHOOP has the night: П8 «Как спалось» only when it has not (manual mode С8). */
    val whoopSawNight: Boolean = true,
    /** Alcohol was already marked last evening in «Закрыть день»: «Вчера» does not ask it again. */
    val eveningAlcoholMarked: Boolean = false,
)

/**
 * The deck engine (И2.1′): which cards, in which order, for each phase. The order is fixed: the
 * body and the state come before behaviour, the delicate card is last (A44 §1.2).
 */
object DeckEngine {

    /** П1–П7 (+ П8 only without a WHOOP night). D-036: 7 cards by default, «Быстро» = 2. */
    val WAKE_ORDER = listOf(CardId.CHARGE, CardId.WEATHER, CardId.BODY, CardId.HEAD, CardId.DRIVE, CardId.SLEPT, CardId.YESTERDAY, CardId.ONLY_YOU)

    val QUICK = listOf(CardId.CHARGE, CardId.WEATHER)

    /** ≈ 10 s a card (A44 §3 budget: 60–90 s for the wake deck). */
    const val SECONDS_PER_CARD = 10

    fun wake(settings: DeckSettings, ctx: DeckContext): List<CardId> {
        if (settings.quick) return QUICK
        return WAKE_ORDER.filter { card ->
            when (card) {
                CardId.CHARGE, CardId.WEATHER -> true
                CardId.SLEPT -> !ctx.whoopSawNight && settings.isOn(card)
                CardId.ONLY_YOU -> settings.privateLayer.on
                else -> settings.isOn(card)
            }
        }
    }

    /** Р1 + Р2 (§1.3), only with the ritual on. */
    fun ritual(settings: DeckSettings): List<CardId> =
        if (settings.ritual) listOf(CardId.RITUAL, CardId.CHARGE_AFTER) else emptyList()

    /**
     * What the evening adds to «Закрыть день» (§1.4): the evening weather, and on Sunday the week
     * («Неделя с людьми»; «Хватает ли тебе близости?» only with closeness on).
     */
    fun evening(settings: DeckSettings, day: LocalDate): List<CardId> = buildList {
        if (!settings.evening) return@buildList
        if (settings.isOn(CardId.EVENING_WEATHER)) add(CardId.EVENING_WEATHER)
        if (day.dayOfWeek == DayOfWeek.SUNDAY) {
            add(CardId.WEEK_PEOPLE)
            if (settings.privateLayer.intimacy) add(CardId.WEEK_CLOSENESS)
        }
    }

    /** «Вчера вечером» options: alcohol is not asked with «не пью» or when the evening marked it. */
    fun yesterdayOptions(settings: DeckSettings, ctx: DeckContext): List<Yesterday> =
        Yesterday.entries.filter { it != Yesterday.ALCOHOL || (settings.drinks && !ctx.eveningAlcoholMarked) }

    /** «Как вчера» (§1.3): yesterday's stack is put in; one tap keeps it. */
    fun prefillRitual(yesterday: Answer?): Set<String> =
        yesterday?.takeIf { !it.skipped && it.card == CardId.RITUAL }?.choices.orEmpty()

    /** The wake deck is offered from 05:00 until noon (§4.1 «любое открытие до 12:00»). */
    fun wakeWindow(now: LocalDateTime): Boolean = now.hour in WAKE_FROM_HOUR until NOON

    /**
     * «После ритуала» (§4.2): the first open ≥ 30 min after the wake deck and before 12:00; at
     * noon the card goes away by itself.
     */
    fun ritualDue(wakeDoneAt: LocalDateTime?, ritualDone: Boolean, now: LocalDateTime): Boolean =
        wakeDoneAt != null && !ritualDone && now.toLocalDate() == wakeDoneAt.toLocalDate() &&
            now.hour < NOON && !now.isBefore(wakeDoneAt.plusMinutes(RITUAL_AFTER_MIN))

    const val WAKE_FROM_HOUR = 5
    const val NOON = 12
    const val RITUAL_AFTER_MIN = 30L
}

/**
 * One pass through a deck: answer, skip, back, partial save (И2.1′). Immutable; every step gives a
 * new state, so the UI and the tests see the same thing.
 */
data class DeckState(
    val cards: List<CardId>,
    val index: Int = 0,
    val answers: Map<CardId, Answer> = emptyMap(),
) {
    val current: CardId? get() = cards.getOrNull(index)
    val done: Boolean get() = index >= cards.size
    val remainingSeconds: Int get() = (cards.size - index).coerceAtLeast(0) * DeckEngine.SECONDS_PER_CARD

    /** «Дальше: Тяга · Вчера · Только ты». */
    val next: List<CardId> get() = cards.drop(index + 1)

    fun answer(answer: Answer): DeckState =
        copy(answers = answers + (answer.card to answer), index = (index + 1).coerceAtMost(cards.size))

    /** A skip is stored as a skip, never a zero. */
    fun skip(atMs: Long = 0L): DeckState {
        val card = current ?: return this
        return copy(answers = answers + (card to Answer.skip(card, atMs)), index = index + 1)
    }

    /** «Назад» on one step: the old answer stays visible (and can be changed). */
    fun back(): DeckState = copy(index = (index - 1).coerceAtLeast(0))

    /** «Быстро» in the middle of the deck: what is answered stays, only П1 + П2 are left to ask. */
    fun quick(): DeckState {
        val left = DeckEngine.QUICK.filter { it !in answers || answers[it]?.skipped == true }
        val kept = cards.take(index).filter { it in answers }
        return DeckState(kept + left, kept.size, answers)
    }

    /** What is saved when the deck is closed half-way (partial save). */
    val answered: List<Answer> get() = cards.mapNotNull { answers[it] }
}
