package com.healthofatitan.app.domain.checkin

import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MorningAndSafetyTest {

    private val day = LocalDate.of(2026, 11, 9)
    private val t0 = Instant.parse("2026-11-09T06:41:00Z")

    @Test
    fun `live titan follows every answer, low charge still glows`() {
        val look = TitanLook.of(
            listOf(
                Answer(CardId.CHARGE, level = 1),
                Answer(CardId.WEATHER, level = Sky.CLOUDY.level),
                Answer(CardId.BODY, level = BodyFeel.SORE.level, choices = setOf("HIPS", "KNEES")),
                Answer(CardId.HEAD, level = Head.FOG.level),
                Answer(CardId.DRIVE, level = Drive.EAGER.level),
                Answer.skip(CardId.YESTERDAY),
            ),
        )
        assertTrue(look.light > 0f)
        assertEquals(Sky.CLOUDY, look.sky)
        assertEquals(setOf(BodyArea.HIPS, BodyArea.KNEES), look.amber)
        assertEquals(1f, look.blur)
        assertEquals(1f, look.lift)
        assertEquals(1f, TitanLook.lightOf(5))
        assertTrue(TitanLook.of(listOf(Answer(CardId.BODY, level = BodyFeel.SICK.level))).sick)
    }

    @Test
    fun `reveal scissors and the count of matches`() {
        assertEquals(5, Reveal.dataLevel(2))
        assertEquals(1, Reveal.dataLevel(-2))
        assertNull(Reveal.dataLevel(null))
        assertEquals(Agreement.MATCH, Reveal.agreement(3, 4))
        assertEquals(Agreement.YOU_LOWER, Reveal.agreement(2, 5))
        assertEquals(Agreement.YOU_HIGHER, Reveal.agreement(5, 2))
        assertEquals(2 to 3, Reveal.matched(listOf(3 to 3, 2 to 5, 4 to 5)))
    }

    @Test
    fun `mornings with you count the last 30 days without streaks`() {
        val days = listOf(day, day.minusDays(1), day.minusDays(3), day.minusDays(29), day.minusDays(30))
        assertEquals(4, Mornings.withYou(days, day))
    }

    @Test
    fun `mentor line context, gentle first`() {
        assertEquals(LineContext.GENTLE, MentorLine.morning(2, 5, gentle = true, quick = false, whoopConnected = true))
        assertEquals(LineContext.WAITING, MentorLine.morning(2, null, false, false, true))
        assertEquals(LineContext.NO_WHOOP, MentorLine.morning(2, null, false, false, false))
        assertEquals(LineContext.YOU_LOWER, MentorLine.morning(2, 5, false, false, true))
        assertEquals(LineContext.MATCH, MentorLine.morning(4, 4, false, false, true))
        assertEquals(LineContext.RITUAL_UP, MentorLine.ritual(RitualDelta.of(2, 4)))
        assertEquals(LineContext.EVENING_HEAVY, MentorLine.evening(Sky.STORM))
        assertEquals(MentorLine.pick(3, day), MentorLine.pick(3, day))
    }

    @Test
    fun `storm net - two storms in a row, morning and evening count`() {
        val w = listOf(WeatherMark(t0.minusSeconds(86_400), Sky.CLEAR), WeatherMark(t0.minusSeconds(40_000), Sky.STORM), WeatherMark(t0, Sky.STORM))
        assertEquals(StormReason.TWO_STORMS, StormNet.check(w, emptyList(), t0, null))
        assertNull(StormNet.check(w.dropLast(1) + WeatherMark(t0, Sky.RAIN), emptyList(), t0, null))
    }

    @Test
    fun `storm net - storm with empty, low mornings, and 72 hours between checks`() {
        assertEquals(StormReason.STORM_EMPTY, StormNet.check(listOf(WeatherMark(t0, Sky.STORM, empty = true)), emptyList(), t0, null))
        val mornings = (0..4).map { MorningMark(day.minusDays(it.toLong()), if (it < 3) 1 else 4, if (it < 3) Sky.STORM else Sky.CLEAR) }
        assertEquals(StormReason.LOW_MORNINGS, StormNet.check(emptyList(), mornings, t0, null))
        assertNull(StormNet.check(listOf(WeatherMark(t0, Sky.STORM, empty = true)), emptyList(), t0, t0.minusSeconds(3600)))
        assertTrue(StormNet.quiet(t0.minusSeconds(86_400), t0))
        assertFalse(StormNet.quiet(null, t0))
    }

    @Test
    fun `specialist route - three times in 14 days, once a month`() {
        val days = listOf(day, day.minusDays(4), day.minusDays(10))
        assertTrue(SpecialistRoute.due(days, day, null))
        assertFalse(SpecialistRoute.due(days, day, day.minusDays(10)))
        assertTrue(SpecialistRoute.due(days, day, day.minusDays(30)))
        assertFalse(SpecialistRoute.due(days.take(2), day, null))
    }

    @Test
    fun `pain - chest is the emergency line, joint spares the zone`() {
        assertEquals(PainRoute.EMERGENCY, PainRules.route(Pain.CHEST))
        assertEquals(PainRoute.SPARE_ZONE, PainRules.route(Pain.JOINT))
        assertTrue(PainRules.sick(null, BodyFeel.SICK))
        assertFalse(PainRules.sick(BodyFeel.LIGHT, BodyFeel.SICK))
        assertTrue(PainRules.sore(BodyFeel.SORE, null))
        assertEquals(6, Charge.energy(3))
    }

    @Test
    fun `usage - median time and removal offers after 14 days`() {
        val events = buildList {
            repeat(6) { add(UsageEvent(day, Phase.WAKE, CardId.HEAD, UsageKind.SKIPPED)) }
            repeat(2) { add(UsageEvent(day, Phase.WAKE, CardId.HEAD, UsageKind.ANSWERED)) }
            repeat(6) { add(UsageEvent(day, Phase.WAKE, CardId.CHARGE, UsageKind.SKIPPED)) }
            add(UsageEvent(day, Phase.WAKE, null, UsageKind.DECK_DONE, 50_000))
            add(UsageEvent(day, Phase.WAKE, null, UsageKind.DECK_DONE, 70_000))
        }
        assertEquals(60, Usage.medianSeconds(events, Phase.WAKE))
        assertNull(Usage.medianSeconds(events, Phase.RITUAL))
        assertEquals(emptyList(), Usage.removalOffers(events, day.minusDays(3), day, emptySet(), emptySet()))
        assertEquals(listOf(CardId.HEAD), Usage.removalOffers(events, day.minusDays(14), day, emptySet(), emptySet()))
        assertEquals(emptyList(), Usage.removalOffers(events, day.minusDays(14), day, emptySet(), setOf(CardId.HEAD)))
    }
}
