package com.healthofatitan.app.core.model.nav

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HotRouteTest {
    @Test
    fun `four modules of D-038, the mentor and sos are outside tabs`() {
        assertEquals(listOf("today", "week", "path", "behind"), HotRoute.tabs)
        assertFalse(HotRoute.MENTOR in HotRoute.tabs)
        assertFalse(HotRoute.SOS in HotRoute.tabs)
        assertFalse(HotRoute.ME in HotRoute.tabs)
    }

    @Test
    fun `support routes carry the support id`() {
        assertEquals("support/water", HotRoute.support("water"))
        assertEquals("support/{id}", HotRoute.SUPPORT)
    }

    @Test
    fun `sos deep link`() {
        assertEquals("hot://sos", HotRoute.SOS_DEEP_LINK)
    }
}
