package com.healthofatitan.app.core.model.nav

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HotRouteTest {
    @Test
    fun `four tabs in A11 order and sos is outside tabs`() {
        assertEquals(listOf("today", "mentor", "week", "me"), HotRoute.tabs)
        assertFalse(HotRoute.SOS in HotRoute.tabs)
    }

    @Test
    fun `sos deep link`() {
        assertEquals("hot://sos", HotRoute.SOS_DEEP_LINK)
    }
}
