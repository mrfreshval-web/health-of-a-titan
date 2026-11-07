package com.healthofatitan.app.core.datastore

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.healthofatitan.app.core.model.mentor.MentorPersona
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MentorKeysTest {

    @Test
    fun `nothing stored means not chosen yet and sensei in effect`() {
        val prefs = MentorKeys.decode(mutablePreferencesOf())
        assertNull(prefs.persona)
        assertEquals(MentorPersona.SENSEI, prefs.effective)
    }

    @Test
    fun `the stored character is read back`() {
        val prefs = MentorKeys.decode(mutablePreferencesOf(MentorKeys.PERSONA to "butler"))
        assertEquals(MentorPersona.BUTLER, prefs.persona)
    }

    @Test
    fun `strong words stay off even if a flag is stored (D-030 p 3)`() {
        val prefs = MentorKeys.decode(mutablePreferencesOf(MentorKeys.PERSONA to "bro", MentorKeys.STRONG_WORDS to true))
        assertEquals(MentorPersona.BRO, prefs.persona)
        assertFalse(prefs.strongWords)
    }
}
