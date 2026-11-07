package com.healthofatitan.app.core.ui.component

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** UX review 119, R1 and R5: numbers never carry a fraction or a range that can break. */
class HotNumbersTest {

    @Test
    fun `clock and span work over midnight`() {
        assertEquals("02:50", HotNumbers.clock(2 * 60 + 50))
        assertEquals("23:59", HotNumbers.clock(23 * 60 + 59))
        assertEquals("00:10", HotNumbers.clock(24 * 60 + 10))
        assertEquals(75, HotNumbers.span(23 * 60 + 5, 20))
        assertEquals("60 мин", HotNumbers.duration(HotNumbers.span(10 * 60 + 52, 11 * 60 + 52)))
    }

    @Test
    fun `the norm bar says nights out of the full window, never a fraction`() {
        assertEquals("Учусь тебя: 9 из 28 ночей", HotNorm.label(9))
        assertEquals("Норма есть: 14 из 28 ночей", HotNorm.label(14))
        assertEquals("Норма полная: 28 ночей", HotNorm.label(35))
        listOf(0, 9, 14, 21, 28).forEach { assertFalse(HotNorm.label(it).contains('/'), "no fraction: $it") }
        assertTrue(HotNorm.explain(9).contains("Ещё 5 ночей"))
        assertTrue(HotNorm.explain(13).contains("Ещё 1 ночь"))
    }
}
