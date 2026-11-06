package com.healthofatitan.app.navigation

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinksTest {
    @Test
    fun `known hot links map to routes`() {
        assertEquals("sos", DeepLinks.routeFor("hot", "sos"))
        assertEquals("today", DeepLinks.routeFor("hot", "today"))
        assertEquals("me", DeepLinks.routeFor("hot", "me"))
    }

    @Test
    fun `unknown or foreign links are ignored`() {
        assertNull(DeepLinks.routeFor("hot", "admin"))
        assertNull(DeepLinks.routeFor("https", "sos"))
        assertNull(DeepLinks.routeFor(null, null))
    }
}
