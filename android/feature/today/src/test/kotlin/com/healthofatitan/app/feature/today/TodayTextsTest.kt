package com.healthofatitan.app.feature.today

import com.healthofatitan.app.domain.engine.RegimeEngine
import com.healthofatitan.app.integration.whoop.SyncFailure
import com.healthofatitan.app.integration.whoop.SyncStatus
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `sleep is a clock number with the need or the week's debt under it`() {
        assertEquals("7:30", TodayTexts.clock(7.5))
        assertEquals("6:05", TodayTexts.clock(6 + 5 / 60.0))
        assertEquals("нужно 8:05", TodayTexts.sleepCaption(8 + 5 / 60.0, 3.0))
        assertEquals("недосып 2 ч за неделю", TodayTexts.sleepCaption(null, 2.0))
        assertNull(TodayTexts.sleepCaption(null, 0.1))
    }

    @Test
    fun `norm is nights out of the window, never a fraction (UX review 119, R5)`() {
        assertEquals("Учусь тебя: 9 из 28 ночей", TodayTexts.nights(9))
        // The screen's numbers are the engine's: Заряд from 14 nights, the full window is 28.
        assertEquals(RegimeEngine.MIN_BASELINE, TodayTexts.NORM_NIGHTS)
        assertEquals(RegimeEngine.FULL_NORM_DAYS, TodayTexts.NORM_FULL_NIGHTS)
    }

    @Test
    fun `workout shows a Russian sport and when`() {
        val today = LocalDate.of(2026, 11, 6)
        val yesterday = Instant.parse("2026-11-05T18:00:00Z")
        assertEquals("Бег · вчера", TodayTexts.workoutCaption("running", yesterday, ZoneOffset.UTC, today))
        assertEquals("Функциональная · 07:10", TodayTexts.workoutCaption("Functional Fitness", Instant.parse("2026-11-06T07:10:00Z"), ZoneOffset.UTC, today))
        assertEquals("Тренировка", TodayTexts.sport(null))
        assertEquals("Тренировка", TodayTexts.sport("activity"))
        assertEquals("Padel", TodayTexts.sport("padel"))
    }

    @Test
    fun `sync lines are one short line with the code`() {
        assertEquals("Готово · 30 ночей · 5 тренировок", TodayTexts.done(30, 5))
        assertEquals("Готово · 21 ночь · 1 тренировка", TodayTexts.done(21, 1))
        assertEquals("Готово · 3 ночи · 2 тренировки", TodayTexts.done(3, 2))
        assertEquals("WHOOP не отвечает · 502", TodayTexts.failure(SyncFailure.WHOOP_DOWN, 502))
        assertEquals("WHOOP закрыл доступ · 403", TodayTexts.failure(SyncFailure.WHOOP_DENIED, 403))
        assertEquals("Нет сети — обновлю сам", TodayTexts.failure(SyncFailure.OFFLINE, null))
        SyncFailure.entries.forEach { assertTrue(TodayTexts.failure(it, null).split(' ').size <= 5, "short: $it") }
    }

    @Test
    fun `offline says it will update later`() {
        assertEquals("Нет сети — обновлю сам", TodayTexts.problem(SyncStatus.OFFLINE))
        assertNull(TodayTexts.problem(SyncStatus.OK))
    }
}
