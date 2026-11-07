package com.healthofatitan.app.domain.checkin

import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** И2.21: n ≥ 8 «с» and ≥ 8 «без», an interval that does not cross 0, ≤ 1 a week, S5 under the lock. */
class DiscoveriesTest {

    private val today = LocalDate.of(2026, 12, 20)
    private val now = Instant.parse("2026-12-20T08:00:00Z")

    /** [n] mornings: on even days a cold shower alone and Заряд +2 after it, else +0…+1. */
    private fun showerMornings(n: Int): List<DayRecord> = (0 until n).map { i ->
        val shower = i % 2 == 0
        DayRecord(
            day = today.minusDays(i.toLong()),
            answers = mapOf(
                CardId.CHARGE to Answer(CardId.CHARGE, level = 2),
                CardId.CHARGE_AFTER to Answer(CardId.CHARGE_AFTER, level = if (shower) 4 else 2 + (i % 3 % 2)),
                CardId.RITUAL to Answer(CardId.RITUAL, choices = if (shower) setOf("COLD_SHOWER", "COFFEE") else setOf("COFFEE")),
                CardId.HEAD to Answer(CardId.HEAD, level = 2),
            ),
        )
    }

    @Test
    fun `shower to charge delta is found with 8 and 8 mornings`() {
        val d = assertNotNull(Discoveries.compute(Link.SHOWER_CHARGE, showerMornings(16), today))
        assertEquals(8, d.n1)
        assertEquals(8, d.n0)
        assertTrue(d.effect >= 1.0)
        assertTrue(d.ciLow > 0.0)
        assertTrue(d.positive)
    }

    @Test
    fun `fewer than 8 without is not a discovery yet, the tile counts what is left`() {
        val records = showerMornings(14)
        assertNull(Discoveries.compute(Link.SHOWER_CHARGE, records, today))
        val tile = Discoveries.collection(records, today, emptyMap(), links = false).first { it.pair == Link.SHOWER_CHARGE }
        tile as Tile.Progress
        assertEquals(2, tile.left)
        assertTrue(Discoveries.collection(records, today, emptyMap(), links = false).filter { it.pair.s5 }.all { it is Tile.Locked })
    }

    @Test
    fun `no difference gives no discovery`() {
        val flat = showerMornings(20).map { r -> r.copy(answers = r.answers + (CardId.CHARGE_AFTER to Answer(CardId.CHARGE_AFTER, level = 3))) }
        assertNull(Discoveries.compute(Link.SHOWER_CHARGE, flat, today))
    }

    @Test
    fun `at most one a week, none in the quiet days, gentle shows only the good ones`() {
        val records = showerMornings(16)
        assertEquals(Link.SHOWER_CHARGE, Discoveries.next(records, today, now, emptyMap(), links = false, gentle = false, quiet = false)?.pair)
        assertNull(Discoveries.next(records, today, now, mapOf(Link.LIGHT_CHARGE to now.minusSeconds(3 * 86_400)), false, false, false))
        assertNull(Discoveries.next(records, today, now, emptyMap(), false, false, quiet = true))
        // the reverse: a shower with a lower delta is a negative link, hidden in Бережно
        val worse = records.map { r ->
            val s = r.choices(CardId.RITUAL)!!.contains("COLD_SHOWER")
            r.copy(answers = r.answers + (CardId.CHARGE_AFTER to Answer(CardId.CHARGE_AFTER, level = if (s) 1 else 4)))
        }
        assertNull(Discoveries.next(worse, today, now, emptyMap(), false, gentle = true, quiet = false))
    }

    @Test
    fun `exposure reads skips as unknown and alcohol from the evening first`() {
        val r = DayRecord(today, answers = mapOf(CardId.YESTERDAY to Answer.skip(CardId.YESTERDAY)))
        assertNull(Discoveries.exposure(Link.SCREENS_SLEEP, r, null))
        assertEquals(true, Discoveries.exposure(Link.ALCOHOL_PULSE, r.copy(eveningAlcohol = 2), null))
        assertEquals(false, Discoveries.exposure(Link.ALCOHOL_PULSE, r.copy(eveningAlcohol = 0), null))
        assertEquals(true, Discoveries.exposure(Link.CLOSENESS_SLEEP, r.copy(only = PrivateAnswer(Intimacy.PARTNER)), null))
        assertNull(Discoveries.exposure(Link.CLOSENESS_SLEEP, r.copy(only = PrivateAnswer(Intimacy.NO_MARK)), null))
        assertEquals(2.0, Discoveries.median(listOf(1.0, 3.0)))
    }
}
