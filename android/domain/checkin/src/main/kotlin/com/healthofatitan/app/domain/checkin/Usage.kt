package com.healthofatitan.app.domain.checkin

import java.time.LocalDate
import kotlin.math.roundToLong

/** What happened to a card or a deck (И2.18). Never the answer itself: no health values. */
enum class UsageKind { SHOWN, ANSWERED, SKIPPED, DECK_DONE, DECK_QUICK, DECK_LEFT }

data class UsageEvent(
    val day: LocalDate,
    val phase: Phase,
    val card: CardId?,
    val kind: UsageKind,
    /** Time on the card (or the whole deck for DECK_*), ms. */
    val durationMs: Long = 0L,
)

/**
 * Local usage metrics of the decks (И2.18, A44 §3 budget): the median time per phase and the share
 * of skips per card. After 14 days a card skipped in more than half of its shows is offered for
 * removal (A25 §2.1, D-036 п. 3) — the core П1 + П2 never are.
 */
object Usage {
    const val REVIEW_AFTER_DAYS = 14L
    const val SKIP_SHARE = 0.5
    const val MIN_SHOWS = 5

    fun medianSeconds(events: List<UsageEvent>, phase: Phase): Long? {
        val xs = events.filter { it.phase == phase && it.kind == UsageKind.DECK_DONE }.map { it.durationMs }.sorted()
        if (xs.isEmpty()) return null
        val m = xs.size / 2
        val ms = if (xs.size % 2 == 1) xs[m].toDouble() else (xs[m - 1] + xs[m]) / 2.0
        return (ms / 1000.0).roundToLong()
    }

    fun skipShare(events: List<UsageEvent>, card: CardId): Double? {
        val answered = events.count { it.card == card && it.kind == UsageKind.ANSWERED }
        val skipped = events.count { it.card == card && it.kind == UsageKind.SKIPPED }
        val total = answered + skipped
        return if (total == 0) null else skipped.toDouble() / total
    }

    /** Cards to offer for removal; empty before the 14th day of use. */
    fun removalOffers(events: List<UsageEvent>, firstDay: LocalDate?, today: LocalDate, off: Set<CardId>, dismissed: Set<CardId>): List<CardId> {
        if (firstDay == null || firstDay.plusDays(REVIEW_AFTER_DAYS).isAfter(today)) return emptyList()
        return CardId.entries.filter { card ->
            if (card.core || card in off || card in dismissed) return@filter false
            val shows = events.count { it.card == card && (it.kind == UsageKind.ANSWERED || it.kind == UsageKind.SKIPPED) }
            shows >= MIN_SHOWS && (skipShare(events, card) ?: 0.0) > SKIP_SHARE
        }
    }
}
