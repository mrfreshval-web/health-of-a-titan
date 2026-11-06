package com.healthofatitan.app.feature.today

import com.healthofatitan.app.integration.whoop.SyncStatus
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TodayTextsTest {

    @Test
    fun `charge has five levels relative to the own norm`() {
        assertEquals("Заметно ниже нормы", TodayTexts.chargeWord(-2))
        assertEquals("Около твоей нормы", TodayTexts.chargeWord(0))
        assertEquals("Заметно выше нормы", TodayTexts.chargeWord(2))
        assertEquals("Нет данных", TodayTexts.chargeWord(null))
    }

    @Test
    fun `balance has three zones`() {
        assertEquals("Недогрузка", TodayTexts.balanceWord(-2))
        assertEquals("Недогрузка", TodayTexts.balanceWord(-1))
        assertEquals("Баланс", TodayTexts.balanceWord(0))
        assertEquals("Нагрузка копится", TodayTexts.balanceWord(1))
        assertEquals("Нагрузка копится", TodayTexts.balanceWord(2))
    }

    @Test
    fun `sleep line shows the debt only when it matters`() {
        assertEquals("Сон 7 ч 30 мин", TodayTexts.sleepLine(7.5, 0.1))
        assertEquals("Сон 6 ч · недосып 2 ч за неделю", TodayTexts.sleepLine(6.0, 2.0))
        assertEquals("Сон 45 мин", TodayTexts.sleepLine(0.75, null))
        assertNull(TodayTexts.sleepLine(null, 3.0))
    }

    @Test
    fun `offline says it will update later`() {
        assertEquals("Обновлю, когда будет связь", TodayTexts.problem(SyncStatus.OFFLINE))
        assertNull(TodayTexts.problem(SyncStatus.OK))
    }
}
