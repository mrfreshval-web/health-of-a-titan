package com.healthofatitan.app.domain.checkin

import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** И2.1′: the deck engine in ten scenarios (A44 §10). */
class DeckEngineTest {

    private val day = LocalDate.of(2026, 11, 9) // a Monday
    private val ctx = DeckContext(day)

    @Test
    fun `1 default wake deck is seven cards in the fixed order, private last`() {
        val cards = DeckEngine.wake(DeckSettings(privateLayer = PrivateLayer(intimacy = true)), ctx)
        assertEquals(
            listOf(CardId.CHARGE, CardId.WEATHER, CardId.BODY, CardId.HEAD, CardId.DRIVE, CardId.YESTERDAY, CardId.ONLY_YOU),
            cards,
        )
    }

    @Test
    fun `2 quick is always the two core cards`() {
        assertEquals(DeckEngine.QUICK, DeckEngine.wake(DeckSettings(quick = true, off = setOf(CardId.CHARGE)), ctx))
    }

    @Test
    fun `3 a card switched off disappears, the core cannot`() {
        val cards = DeckEngine.wake(DeckSettings(off = setOf(CardId.HEAD, CardId.WEATHER)), ctx)
        assertFalse(CardId.HEAD in cards)
        assertTrue(CardId.WEATHER in cards)
        assertFalse(CardId.ONLY_YOU in cards)
    }

    @Test
    fun `4 how you slept only when WHOOP did not see the night`() {
        assertFalse(CardId.SLEPT in DeckEngine.wake(DeckSettings(), ctx))
        val manual = DeckEngine.wake(DeckSettings(), ctx.copy(whoopSawNight = false))
        assertEquals(CardId.SLEPT, manual[manual.indexOf(CardId.YESTERDAY) - 1])
    }

    @Test
    fun `5 a skip is stored as a skip, never zero, and the deck moves on`() {
        val s = DeckState(listOf(CardId.CHARGE, CardId.WEATHER)).skip(5L)
        val a = s.answers.getValue(CardId.CHARGE)
        assertTrue(a.skipped)
        assertNull(a.level)
        assertEquals(CardId.WEATHER, s.current)
    }

    @Test
    fun `6 back keeps the answer and quick in the middle keeps what was answered`() {
        var s = DeckState(DeckEngine.wake(DeckSettings(), ctx))
        s = s.answer(Answer(CardId.CHARGE, level = 2)).answer(Answer(CardId.WEATHER, level = 3)).answer(Answer(CardId.BODY, level = 3))
        s = s.back()
        assertEquals(CardId.BODY, s.current)
        assertEquals(3, s.answers.getValue(CardId.BODY).level)
        val q = s.quick()
        assertTrue(q.done)
        assertEquals(listOf(CardId.CHARGE, CardId.WEATHER), q.answered.map { it.card })
    }

    @Test
    fun `7 partial save gives the answers so far`() {
        val s = DeckState(DeckEngine.wake(DeckSettings(), ctx)).answer(Answer(CardId.CHARGE, level = 4))
        assertEquals(listOf(CardId.CHARGE), s.answered.map { it.card })
        assertFalse(s.done)
        assertEquals(5 * DeckEngine.SECONDS_PER_CARD, s.remainingSeconds)
    }

    @Test
    fun `8 yesterday's stack is put in, alcohol is not asked twice`() {
        val y = Answer(CardId.RITUAL, choices = setOf("COFFEE", Stack.own("Alpha-GPC")))
        assertEquals(setOf("COFFEE", "stack:Alpha-GPC"), DeckEngine.prefillRitual(y))
        assertEquals(emptySet(), DeckEngine.prefillRitual(Answer.skip(CardId.RITUAL)))
        assertFalse(Yesterday.ALCOHOL in DeckEngine.yesterdayOptions(DeckSettings(), ctx.copy(eveningAlcoholMarked = true)))
        assertFalse(Yesterday.ALCOHOL in DeckEngine.yesterdayOptions(DeckSettings(drinks = false), ctx))
        assertTrue(Yesterday.ALCOHOL in DeckEngine.yesterdayOptions(DeckSettings(), ctx))
    }

    @Test
    fun `9 ritual card waits 30 min after the deck and leaves at noon`() {
        val wake = LocalDateTime.of(day, java.time.LocalTime.of(6, 41))
        assertFalse(DeckEngine.ritualDue(wake, false, wake.plusMinutes(20)))
        assertTrue(DeckEngine.ritualDue(wake, false, wake.plusMinutes(60)))
        assertFalse(DeckEngine.ritualDue(wake, true, wake.plusMinutes(60)))
        assertFalse(DeckEngine.ritualDue(wake, false, wake.withHour(12)))
        assertFalse(DeckEngine.ritualDue(null, false, wake.plusMinutes(60)))
        assertTrue(DeckEngine.wakeWindow(wake))
        assertFalse(DeckEngine.wakeWindow(wake.withHour(13)))
    }

    @Test
    fun `10 evening adds the weather, and on Sunday the week`() {
        assertEquals(listOf(CardId.EVENING_WEATHER), DeckEngine.evening(DeckSettings(), day))
        val sunday = day.plusDays(6)
        assertEquals(listOf(CardId.EVENING_WEATHER, CardId.WEEK_PEOPLE), DeckEngine.evening(DeckSettings(), sunday))
        assertEquals(
            listOf(CardId.EVENING_WEATHER, CardId.WEEK_PEOPLE, CardId.WEEK_CLOSENESS),
            DeckEngine.evening(DeckSettings(privateLayer = PrivateLayer(intimacy = true)), sunday),
        )
        assertEquals(emptyList(), DeckEngine.evening(DeckSettings(evening = false), sunday))
        assertEquals(listOf(CardId.RITUAL, CardId.CHARGE_AFTER), DeckEngine.ritual(DeckSettings()))
        assertEquals(emptyList(), DeckEngine.ritual(DeckSettings(ritual = false)))
    }

    @Test
    fun `codec keeps commas in own stack names and the private answer round-trips`() {
        val set = setOf("COFFEE", "stack:L-тирозин, 500")
        assertEquals(set, Codec.choices(Codec.choices(set)))
        val p = PrivateAnswer(Intimacy.SELF, setOf(Pull.FEED, Pull.ADULT), wantLess = true)
        assertEquals(p, Codec.decodePrivate(Codec.encodePrivate(p)))
        assertEquals(PrivateAnswer(), Codec.decodePrivate(Codec.encodePrivate(PrivateAnswer())))
        assertEquals(CardId.PULLS, CardId.fromRef("pulls@1"))
    }
}
