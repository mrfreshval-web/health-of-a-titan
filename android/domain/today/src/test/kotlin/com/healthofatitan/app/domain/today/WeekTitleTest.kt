package com.healthofatitan.app.domain.today

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

/** A66 §4.2: the title of «Неделя» by the ISO week. */
class WeekTitleTest {

    @Test
    fun `week 45 of 2026 is 2 to 8 November, from any of its days`() {
        (2..8).forEach { d ->
            assertEquals("Неделя 45 · 2–8 ноября", WeekTitle.of(LocalDate.of(2026, 11, d)))
        }
    }

    @Test
    fun `a week across two months names both`() {
        assertEquals("Неделя 44 · 26 октября – 1 ноября", WeekTitle.of(LocalDate.of(2026, 10, 28)))
    }

    @Test
    fun `the ISO number belongs to the week-based year`() {
        assertEquals("Неделя 53 · 28 декабря – 3 января", WeekTitle.of(LocalDate.of(2027, 1, 2)))
        assertEquals(1, WeekTitle.number(LocalDate.of(2025, 12, 29)))
    }
}
