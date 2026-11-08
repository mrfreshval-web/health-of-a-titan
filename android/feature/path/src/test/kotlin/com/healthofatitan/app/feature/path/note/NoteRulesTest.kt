package com.healthofatitan.app.feature.path.note

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NoteRulesTest {
    @Test
    fun `a blank note is not saved`() {
        assertNull(NoteRules.clean("   \n "))
    }

    @Test
    fun `a note is trimmed and cut to the limit`() {
        assertEquals("Колено ныло", NoteRules.clean("  Колено ныло \n"))
        assertEquals(NoteCopy.MAX, NoteRules.clean("а".repeat(NoteCopy.MAX + 20))!!.length)
    }
}
