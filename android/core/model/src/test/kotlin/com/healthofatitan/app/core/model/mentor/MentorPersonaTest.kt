package com.healthofatitan.app.core.model.mentor

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MentorPersonaTest {
    @Test
    fun `ids round trip and unknown ids are ignored`() {
        MentorPersona.entries.forEach { assertEquals(it, MentorPersona.fromId(it.id)) }
        assertNull(MentorPersona.fromId("bro_strong"))
        assertNull(MentorPersona.fromId(null))
    }

    @Test
    fun `skip means sensei`() {
        assertEquals(MentorPersona.SENSEI, MentorPersona.DEFAULT)
    }
}
