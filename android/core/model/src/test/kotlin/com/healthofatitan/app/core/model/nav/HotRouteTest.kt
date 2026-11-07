package com.healthofatitan.app.core.model.nav

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HotRouteTest {
    @Test
    fun `four tabs in A11 order with path instead of me and sos is outside tabs`() {
        assertEquals(listOf("today", "mentor", "week", "path"), HotRoute.tabs)
        assertFalse(HotRoute.SOS in HotRoute.tabs)
        assertFalse(HotRoute.ME in HotRoute.tabs)
    }

    @Test
    fun `sos deep link`() {
        assertEquals("hot://sos", HotRoute.SOS_DEEP_LINK)
    }
}
