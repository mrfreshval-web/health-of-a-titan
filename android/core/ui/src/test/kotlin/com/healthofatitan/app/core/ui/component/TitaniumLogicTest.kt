package com.healthofatitan.app.core.ui.component

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D-029 header ring texts and the «Титан приходит» storyboard (A37 §5) as pure functions. */
class TitaniumLogicTest {

    @Test
    fun `ring numbers are short and russian`() {
        assertEquals("8,4", StatusRingsTexts.strain(8.4))
        assertEquals("6,8k", StatusRingsTexts.steps(6_800))
        assertEquals("940", StatusRingsTexts.steps(940))
        assertEquals("9:13", StatusRingsTexts.until(553))
        assertEquals("0:00", StatusRingsTexts.until(-20))
        assertEquals("23:30", StatusRingsTexts.clock(23 * 60 + 30))
    }

    @Test
    fun `talkback reads every ring, missing data is said, never zero`() {
        val r = StatusRings(strain = 8.4, strainCeiling = 12.0, untilBedMin = 553, bedtimeMin = 1410, mode = HotMode.ROVNO)
        assertEquals("Нагрузка 8,4 из 12, шаги: нет данных, до отбоя 9 часов 13 минут", StatusRingsTexts.spoken(r))
        assertEquals("Нагрузка: нет данных, шаги: нет данных, до отбоя: нет плана", StatusRingsTexts.spoken(StatusRings.Empty))
        assertTrue(StatusRingsTexts.why(r).startsWith("потолок нагрузки «Ровно» — 12"))
        assertEquals(0.7f, r.strainFraction!!, 0.001f)
    }

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
