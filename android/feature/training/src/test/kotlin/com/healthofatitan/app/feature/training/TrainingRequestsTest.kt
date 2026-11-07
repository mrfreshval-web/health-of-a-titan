package com.healthofatitan.app.feature.training

import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.training.DayMode
import com.healthofatitan.app.domain.training.DayTrainingKind
import com.healthofatitan.app.domain.training.TrainingFlag
import com.healthofatitan.app.domain.training.TrainingPlace
import com.healthofatitan.app.integration.whoop.day.DayItemView
import com.healthofatitan.app.integration.whoop.day.DayView
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrainingRequestsTest {

    private val today = LocalDate.of(2026, 11, 10)

    private fun view(regime: Regime, title: String, start: Int? = 18 * 60, end: Int? = 18 * 60 + 45, flags: Set<DayFlag> = emptySet(), day: LocalDate = today) =
        DayView(
            dayKey = day,
            regime = regime,
            flags = flags,
            headline = "",
            summary = "",
            fired = emptyList(),
            items = listOf(DayItemView("training", ItemKind.TRAINING, title, null, start, end, ItemStatus.OPEN)),
            bedtimeMin = 23 * 60,
            caffeineCutoffMin = 14 * 60,
            windDownMin = 22 * 60,
        )

    @Test
    fun `regime kind minutes and flags of today's plan become the request`() {
        val r = TrainingRequests.request(
            view(Regime.BOOST, "Силовая · 45 мин", flags = setOf(DayFlag.TRAVEL, DayFlag.LIGHT_WEEK, DayFlag.CALIBRATION)),
            today,
            TrainingPlace.GYM,
        )
        assertEquals(DayMode.BOOST, r.mode)
        assertEquals(DayTrainingKind.STRENGTH, r.kind)
        assertEquals(45, r.minutes)
        assertEquals(TrainingPlace.GYM, r.place)
        assertEquals(setOf(TrainingFlag.TRAVEL, TrainingFlag.LIGHT_WEEK), r.flags)
    }

    @Test
    fun `minutes come from the title when the item has no slot`() {
        val r = TrainingRequests.request(view(Regime.GENTLE, "Прогулка · 30 мин", start = null, end = null, flags = setOf(DayFlag.SICK)), today, TrainingPlace.HOME)
        assertEquals(DayMode.GENTLE, r.mode)
        assertEquals(DayTrainingKind.WALK, r.kind)
        assertEquals(30, r.minutes)
        assertEquals(setOf(TrainingFlag.SICK), r.flags)
    }

    @Test
    fun `rest and zone 2 titles map to their kinds`() {
        assertEquals(DayTrainingKind.REST, TrainingRequests.kind(view(Regime.STEADY, "Отдых").items.first()))
        assertEquals(DayTrainingKind.ZONE2, TrainingRequests.kind(view(Regime.STEADY, "Zone 2 · 40 мин").items.first()))
        assertNull(TrainingRequests.kind(view(Regime.STEADY, "Что-то другое").items.first()))
    }

    @Test
    fun `no plan for today gives the steady default`() {
        val r = TrainingRequests.request(view(Regime.BOOST, "Силовая · 45 мин", day = today.minusDays(1)), today, TrainingPlace.HOME)
        assertEquals(DayMode.STEADY, r.mode)
        assertNull(r.kind)
        assertNull(r.minutes)
        assertEquals(TrainingRequests.request(null, today, TrainingPlace.HOME), r)
    }
}
