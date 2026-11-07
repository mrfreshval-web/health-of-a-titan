package com.healthofatitan.app.feature.today

import com.healthofatitan.app.domain.engine.FiredRule
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.RuleRef
import com.healthofatitan.app.integration.whoop.day.DayItemView
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A39 §7: the order and the limits of «Почему так», and the item of «Сейчас». */
class DayLayoutTest {

    private fun r(id: String) = FiredRule(RuleRef(id, 1), id)

    @Test
    fun `why keeps data before regime before context, hides plan rules and safety, at most 4`() {
        val fired = listOf(r("CTX-03"), r("SLP-01"), r("REG-02"), r("SAF-03"), r("NUT-01"), r("REG-05"), r("WRK-01"), r("EVE-01"), r("REG-06"))
        assertEquals(listOf("REG-05", "REG-02", "REG-06", "CTX-03"), whyLines(fired).map { it.ref.id })
    }

    @Test
    fun `why kinds`() {
        assertEquals(WhyKind.SAFETY, whyKind("SAF-04"))
        assertEquals(WhyKind.DATA, whyKind("REG-05"))
        assertEquals(WhyKind.REGIME, whyKind("REG-01"))
        assertEquals(WhyKind.CONTEXT, whyKind("CTX-01"))
        assertNull(whyKind("SLP-02"))
    }

    @Test
    fun `now is the open item in its slot, else the next one`() {
        fun item(id: String, start: Int?, end: Int?, status: ItemStatus = ItemStatus.OPEN) =
            DayItemView(id, ItemKind.WORK, id, null, start, end, status)
        val items = listOf(item("a", 9 * 60, 10 * 60, ItemStatus.DONE_SELF), item("b", 11 * 60, 12 * 60), item("c", 18 * 60, null))
        assertEquals("b", nowItem(items, 11 * 60 + 30)?.id)
        assertEquals("c", nowItem(items, 13 * 60)?.id)
        assertEquals("b", nowItem(items, 9 * 60 + 30)?.id)
    }
}
