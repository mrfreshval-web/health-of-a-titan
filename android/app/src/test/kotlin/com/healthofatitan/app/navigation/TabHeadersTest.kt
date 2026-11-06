package com.healthofatitan.app.navigation

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class TabHeadersTest {

    @Test
    fun `today overline is weekday and day with the genitive month`() {
        assertEquals("вторник · 10 ноября", TabHeaders.today(LocalDate.of(2026, 11, 10)))
    }

    @Test
    fun `week overline spans monday to sunday`() {
        assertEquals("2–8 ноября", TabHeaders.week(LocalDate.of(2026, 11, 6)))
        assertEquals("30 ноября – 6 декабря", TabHeaders.week(LocalDate.of(2026, 12, 3)))
    }
}
