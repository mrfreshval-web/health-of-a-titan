package com.healthofatitan.app.core.ui.component

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The «Титан приходит» storyboard (A37 §5) as pure functions. The header rings left with A46 v0.3. */
class TitaniumLogicTest {

    @Test
    fun `the titan arrives in 1,5 s and ends at rest`() {
        val t = TitanArrivalTimeline
        assertEquals(1500f, t.END)
        assertEquals(0f, t.titanAlpha(t.FOG))
        assertEquals(1f, t.titanAlpha(t.END))
        assertEquals(1f, t.titanScale(t.END))
        assertEquals(0f, t.titanBlur(t.END))
        assertEquals(1f, t.ringReveal(t.END))
        assertEquals(0f, t.fog(t.END))
        // the columns overshoot on the push and settle
        assertTrue(t.spread(t.PUSH) > 1f)
        assertEquals(1f, t.spread(t.END))
        // a finger pulling to refresh walks the titan from the step to the palms
        assertEquals(t.PALMS, t.tracking(1f))
    }
}
