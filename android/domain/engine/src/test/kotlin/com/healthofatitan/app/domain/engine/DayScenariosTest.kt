package com.healthofatitan.app.domain.engine

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A09 §8 golden days (`content/rules/_tests/day_scenarios.yaml`) as JVM tests. Defaults of the
 * file: wake 07:00, bedtime 23:00, a full norm, yesterday Ровно. Scenarios that need parts not in
 * slice 2 are adapted or left out, see the comment at each one:
 *  - GRW (goals) — no goals yet, so GOAL_STEP / LEARNING_25 are not expected;
 *  - D08 (manual mode, 3 questions) and D12 (return after silence) — next slices.
 */
class DayScenariosTest {

    /** Wednesday: the A19 queue then asks for strength (none logged this week). */
    private val day = LocalDate.of(2026, 11, 4)

    private fun input(
        night: NightFacts,
        self: SelfReport = SelfReport(),
        data: DataFacts = DataFacts(baselineNights = 28, historyDays = 60),
        yesterday: YesterdayFacts = YesterdayFacts(regime = Regime.STEADY),
        evening: EveningFacts? = null,
        context: ContextFacts = ContextFacts(),
    ) = DayInput(day, night, data, yesterday, evening, self, context, ScheduleFacts())

    private fun DayPlan.firedIds() = fired.map { it.ref.id }.toSet()

    private fun DayPlan.itemIds() = items.map { it.id }.toSet()

    @Test
    fun `D01 good morning gives BOOST with intervals allowed`() {
        val p = DayEngine.plan(
            input(
                NightFacts(orsZ = 0.8, orsEwma7 = 0.3, sleepHours = 7.6, prevSleepHours = 7.2, sleepDebt7h = 1.5, lrb = 0.2),
                SelfReport(energy = 8, sleepQuality = 4),
            ),
        )
        assertEquals(Regime.BOOST, p.regime)
        assertEquals(0, p.points)
        assertFalse(p.forbidIntervals)
        assertEquals("14:00", DayTexts.time(p.caffeineCutoffMin))
        assertTrue(p.itemIds().containsAll(listOf(DayEngine.Ids.LIGHT, DayEngine.Ids.FOCUS, DayEngine.Ids.BEDTIME)))
        assertTrue(p.firedIds().containsAll(listOf("REG-02", "SLP-01", "SLP-02", "SLP-03", "WRK-01")))
        assertEquals(TrainingLevel.L0, p.training.level)
    }

    @Test
    fun `D01 without a morning card still gives BOOST when ORS is clearly above the norm`() {
        val p = DayEngine.plan(input(NightFacts(orsZ = 0.8, orsEwma7 = 0.3, sleepHours = 7.6, prevSleepHours = 7.2)))
        assertEquals(Regime.BOOST, p.regime)
        val calm = DayEngine.plan(input(NightFacts(orsZ = 0.3, orsEwma7 = 0.3, sleepHours = 7.6, prevSleepHours = 7.2)))
        assertEquals(Regime.STEADY, calm.regime, "ORS < +0.5 without an energy answer is not enough for BOOST")
    }

    @Test
    fun `D02 a night under 5 h is GENTLE even with high energy`() {
        val p = DayEngine.plan(
            input(NightFacts(orsZ = 0.2, orsEwma7 = 0.1, sleepHours = 4.7, prevSleepHours = 7.1), SelfReport(energy = 7, sleepQuality = 3)),
        )
        assertEquals(Regime.GENTLE, p.regime)
        assertEquals("22:30", DayTexts.time(p.bedtimeMin))
        assertEquals("13:30", DayTexts.time(p.caffeineCutoffMin))
        assertTrue(p.itemIds().containsAll(listOf(DayEngine.Ids.NAP, DayEngine.Ids.BREAKFAST)))
        assertEquals(TrainingType.WALK, p.training.type)
        assertEquals("T15", p.training.templateId)
        assertTrue(p.firedIds().containsAll(listOf("REG-01", "SLP-05", "SLP-01", "WRK-02", "ACT-01")))
        assertFalse("REG-03" in p.firedIds())
    }

    @Test
    fun `D03 three servings and low energy give GENTLE with neutral words`() {
        val p = DayEngine.plan(
            input(
                NightFacts(orsZ = -1.6, orsEwma7 = -0.2, sleepHours = 6.5, prevSleepHours = 7.4),
                SelfReport(energy = 4, energyZ = -1.2),
                evening = EveningFacts(alcohol = 3),
            ),
        )
        assertEquals(Regime.GENTLE, p.regime)
        assertEquals(2, p.points)
        assertTrue(p.forbidIntervals)
        assertTrue(p.itemIds().containsAll(listOf(DayEngine.Ids.WATER, DayEngine.Ids.BREAKFAST)))
        assertTrue(p.firedIds().containsAll(listOf("REG-02", "CTX-01")))
        val words = listOf("вред", "нельзя", "срыв", "плохо", "зависим")
        p.fired.forEach { f -> words.forEach { w -> assertFalse(f.why.lowercase().contains(w), "${f.ref}: «$w»") } }
    }

    @Test
    fun `D04 red data and a great morning give STEADY without intervals`() {
        val p = DayEngine.plan(
            input(NightFacts(orsZ = -2.2, orsEwma7 = -0.6, sleepHours = 7.1, prevSleepHours = 7.3), SelfReport(energy = 8, sleepQuality = 4)),
        )
        assertEquals(Regime.STEADY, p.regime)
        assertEquals(3, p.points)
        assertTrue(p.forbidIntervals)
        assertTrue(p.firedIds().containsAll(listOf("REG-02", "REG-03")))
        assertFalse(p.training.type == TrainingType.INTERVALS)
    }

    @Test
    fun `D05 green data and energy 3 give STEADY`() {
        val p = DayEngine.plan(
            input(NightFacts(orsZ = 0.7, orsEwma7 = 0.4, sleepHours = 7.5, prevSleepHours = 7.4), SelfReport(energy = 3, sleepQuality = 3)),
        )
        assertEquals(Regime.STEADY, p.regime)
        assertEquals(1, p.points)
        assertTrue(p.forbidIntervals)
    }

    @Test
    fun `D06 a cold from the evening deck locks GENTLE and rest`() {
        val p = DayEngine.plan(
            input(NightFacts(orsZ = -1.9, sleepHours = 7.0), SelfReport(energy = 4), evening = EveningFacts(body = Body.SICK)),
        )
        assertEquals(Regime.GENTLE, p.regime)
        assertTrue(DayFlag.SICK in p.flags)
        assertEquals(TrainingType.REST, p.training.type)
        assertEquals("22:30", DayTexts.time(p.bedtimeMin))
        assertTrue(p.firedIds().containsAll(listOf("SAF-03", "REG-01")))
        assertFalse("ACT-01" in p.firedIds())
        assertFalse(DayEngine.Ids.NAP in p.itemIds(), "no nap advice while sick")
    }

    @Test
    fun `D07 calibration uses Recovery in memory and says it is learning`() {
        val p = DayEngine.plan(
            input(
                NightFacts(sleepHours = 7.2, prevSleepHours = 7.0, recoveryPct = 72.0),
                SelfReport(energy = 7, sleepQuality = 4),
                data = DataFacts(baselineNights = 5, historyDays = 5),
                yesterday = YesterdayFacts(regime = null),
            ),
        )
        assertEquals(Regime.BOOST, p.regime)
        assertTrue(p.flags.containsAll(listOf(DayFlag.CALIBRATION, DayFlag.DATA_LIMITED)))
        assertTrue(p.fired.any { it.ref.id == "REG-05" && it.why.lowercase().contains("изучаю твою норму") })
    }

    @Test
    fun `D07 without Recovery (slice 2 does not read it) calibration stays STEADY`() {
        val p = DayEngine.plan(
            input(NightFacts(sleepHours = 7.2, prevSleepHours = 7.0), data = DataFacts(baselineNights = 5, historyDays = 5), yesterday = YesterdayFacts()),
        )
        assertEquals(Regime.STEADY, p.regime)
    }

    @Test
    fun `D09 after GENTLE a great day is only STEADY`() {
        val p = DayEngine.plan(
            input(
                NightFacts(orsZ = 0.6, orsEwma7 = 0.0, sleepHours = 7.8, prevSleepHours = 6.8),
                SelfReport(energy = 8, sleepQuality = 5),
                yesterday = YesterdayFacts(regime = Regime.GENTLE, gentleStreak = 1),
            ),
        )
        assertEquals(Regime.STEADY, p.regime)
        assertEquals(Regime.BOOST, p.regimeComputed)
        assertTrue("REG-04" in p.firedIds())
    }

    @Test
    fun `D10 the exit threshold keeps the ORS signal on`() {
        val p = DayEngine.plan(
            input(
                NightFacts(orsZ = -0.7, orsEwma7 = -0.6, sleepHours = 5.5, prevSleepHours = 6.9),
                SelfReport(energy = 5),
                yesterday = YesterdayFacts(regime = Regime.STEADY, signals = setOf(Signals.ORS_DAY)),
            ),
        )
        assertEquals(Regime.GENTLE, p.regime)
        assertEquals(3, p.points)
        assertTrue(p.firedIds().containsAll(listOf("REG-02", "REG-04", "SLP-05")))

        val noHysteresis = DayEngine.plan(
            input(NightFacts(orsZ = -0.7, orsEwma7 = -0.6, sleepHours = 5.5, prevSleepHours = 6.9), SelfReport(energy = 5)),
        )
        assertEquals(Regime.STEADY, noHysteresis.regime)
    }

    @Test
    fun `D11 three hours east is STEADY with local light and no intervals`() {
        val p = DayEngine.plan(
            input(
                NightFacts(orsZ = -0.8, orsEwma7 = 0.0, sleepHours = 6.2, prevSleepHours = 7.5, sleepDebt7h = 2.0),
                SelfReport(energy = 6),
                context = ContextFacts(tzShiftHours = 3.0),
            ),
        )
        assertEquals(Regime.STEADY, p.regime)
        assertTrue(DayFlag.TRAVEL in p.flags)
        assertTrue(p.forbidIntervals)
        val light = p.items.single { it.id == DayEngine.Ids.LIGHT }
        assertEquals(RuleSet.CTX_04, light.rule)
        assertEquals(7 * 60, light.startMin)
        assertEquals("14:00", DayTexts.time(p.caffeineCutoffMin))
        assertTrue("CTX-04" in p.firedIds())
        assertFalse("SLP-03" in p.firedIds())
    }

    @Test
    fun `REG-06 a third GENTLE day starts a light week`() {
        val p = DayEngine.plan(
            input(
                NightFacts(orsZ = -2.1, orsEwma7 = -0.7, sleepHours = 6.5, prevSleepHours = 6.5),
                yesterday = YesterdayFacts(regime = Regime.GENTLE, gentleStreak = 2),
            ),
        )
        assertEquals(Regime.GENTLE, p.regime)
        assertTrue(DayFlag.LIGHT_WEEK in p.flags)
        val next = DayEngine.plan(
            input(
                NightFacts(orsZ = 0.9, orsEwma7 = 0.2, sleepHours = 7.5, prevSleepHours = 7.5),
                yesterday = YesterdayFacts(regime = Regime.STEADY, lightWeekDay = 2),
            ),
        )
        assertEquals(Regime.STEADY, next.regime, "light week caps at Ровно")
        assertTrue(next.fired.any { it.ref.id == "REG-06" && it.why.contains("день 3 из 7") })
    }

    @Test
    fun `the plan keeps the budget of seven items and each item has a rule`() {
        val p = DayEngine.plan(
            input(NightFacts(orsZ = 0.1, orsEwma7 = 0.0, sleepHours = 5.6, prevSleepHours = 7.5), evening = EveningFacts(alcohol = 2)),
        )
        assertTrue(p.items.size <= DayEngine.BUDGET)
        assertEquals(p.items.size, p.items.map { it.id }.toSet().size)
        assertTrue(DayEngine.Ids.TRAINING in p.itemIds() && DayEngine.Ids.BEDTIME in p.itemIds())
        val times = p.items.mapNotNull { it.startMin }
        assertEquals(times.sorted(), times, "items are in time order")
    }

    @Test
    fun `the push has no alcohol and fits A11 lengths`() {
        listOf(Regime.BOOST to 0.9, Regime.STEADY to 0.1, Regime.GENTLE to -2.5).forEach { (_, ors) ->
            val p = DayEngine.plan(
                input(NightFacts(orsZ = ors, orsEwma7 = 0.0, sleepHours = 7.2, prevSleepHours = 7.2), evening = EveningFacts(alcohol = 4)),
            )
            assertTrue(p.headline.length <= 40, p.headline)
            assertTrue(p.summary.length <= 110, p.summary)
            listOf("порц", "алкогол", "вино", "пив").forEach { assertFalse(p.summary.lowercase().contains(it), p.summary) }
        }
    }

    @Test
    fun `every fired rule has a one-line explanation`() {
        val p = DayEngine.plan(
            input(NightFacts(orsZ = -1.2, orsEwma7 = -0.6, sleepHours = 5.8, prevSleepHours = 6.0, lrb = 1.2), evening = EveningFacts(alcohol = 3, body = Body.SORE)),
        )
        p.fired.forEach { f ->
            assertTrue(f.why.isNotBlank(), f.ref.toString())
            assertFalse(f.why.contains('\n'), f.ref.toString())
            assertNull(Regex("\\{[a-z_]+}").find(f.why), "unfilled placeholder in ${f.ref}")
        }
        assertEquals(RuleSet.VERSION, p.ruleSet)
    }
}
