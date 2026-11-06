package com.healthofatitan.app.domain.metrics

import org.junit.Test
import java.time.LocalDate
import kotlin.math.ln
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsTest {

    @Test
    fun `median and MAD`() {
        assertEquals(2.0, Stats.median(listOf(3.0, 1.0, 2.0)))
        assertEquals(2.5, Stats.median(listOf(4.0, 1.0, 2.0, 3.0)))
        assertNull(Stats.median(emptyList()))
        assertEquals(1.0, Stats.mad(listOf(1.0, 2.0, 3.0, 4.0, 5.0)))
    }

    @Test
    fun `robust z follows A25 P1 and needs 14 values`() {
        val baseline = (1..14).map { it.toDouble() } // median 7.5, MAD 3.5
        assertEquals(4.5 / (1.4826 * 3.5), Stats.robustZ(12.0, baseline, 14)!!, 1e-9)
        assertNull(Stats.robustZ(12.0, baseline.drop(1), 14))
        assertEquals(3.0, Stats.robustZ(1000.0, baseline, 14))
        assertEquals(-3.0, Stats.robustZ(-1000.0, baseline, 14))
    }

    @Test
    fun `robust z survives a baseline without spread`() {
        val zeros = List(20) { 0.0 }
        assertEquals(0.0, Stats.robustZ(0.0, zeros, 14))
        assertEquals(3.0, Stats.robustZ(50.0, zeros, 14))
        // MAD = 0 but some spread: mean absolute deviation is used.
        val mostlyZero = List(16) { 0.0 } + listOf(60.0, 90.0, 120.0, 150.0)
        val z = Stats.robustZ(60.0, mostlyZero, 14)!!
        assertEquals(60.0 / (1.2533 * (420.0 / 20)), z, 1e-9)
    }

    @Test
    fun `EWMA over 7 days carries over gaps`() {
        assertEquals(listOf(null, 10.0, 10.0, 12.5), Stats.ewmaSeries(listOf(null, 10.0, null, 20.0), 7))
    }
}

class MetricEngineTest {

    private val start = LocalDate.of(2026, 10, 1)

    /** 28 alternating baseline days (A25 fixture: median and MAD easy to check by hand), then [last]. */
    private fun fixture(last: DayInputs.(LocalDate) -> DayInputs): List<DayInputs> {
        val base = (0 until 28).map { i ->
            val even = i % 2 == 0
            DayInputs(
                dayKey = start.plusDays(i.toLong()),
                lnRmssd = if (even) 4.0 else 4.2,
                rhr = if (even) 50.0 else 52.0,
                rr = if (even) 14.0 else 15.0,
                sleepHours = if (even) 7.0 else 8.0,
                sleepMidMin = if (even) 180.0 else 200.0,
                trimp = if (i % 4 == 0) 120.0 else 0.0,
            )
        }
        val day = start.plusDays(28)
        return base + DayInputs(day).last(day)
    }

    private fun MetricResult.value(metric: MetricRef, day: LocalDate) = daily.single { it.metric == metric && it.dayKey == day }

    @Test
    fun `reference day matches the hand calculation of A25 formulas`() {
        val days = fixture { d -> copy(lnRmssd = 3.9, rhr = 54.0, rr = 14.5, sleepHours = 6.0, sleepMidMin = 190.0, trimp = 0.0) }
        val day = days.last().dayKey
        val r = MetricEngine.compute(days)

        // z(lnRMSSD) = (3.9 − 4.1) / (1.4826 · 0.1); z(RHR) = (54 − 51) / 1.4826; z(RR) = 0.
        val zHrv = -0.2 / (1.4826 * 0.1)
        val zRhr = 3.0 / 1.4826
        val orsExpected = 0.5 * zHrv - 0.3 * zRhr
        val ors = r.value(Metrics.ORS, day)
        assertEquals(orsExpected, ors.value!!, 1e-6)
        assertEquals(-1.28153, ors.value!!, 1e-4)
        assertEquals(-1, ors.level)
        assertEquals(1.0, ors.coverage, 1e-9)

        val zSleep = -1.5 / (1.4826 * 0.5)
        val slp = r.value(Metrics.SLP, day)
        assertEquals(6.0, slp.value)
        assertEquals(zSleep, slp.z!!, 1e-6)
        assertEquals(-2, slp.level)

        // ECAP without cards: ORS 0.35 + SLP 0.25 = 60 % coverage, renormalised.
        val ecapZ = (0.35 * orsExpected + 0.25 * zSleep) / 0.6
        val ecap = r.value(Metrics.ECAP, day)
        assertEquals(ecapZ, ecap.z!!, 1e-6)
        assertEquals(50 + 15 * ecapZ, ecap.value!!, 1e-6)
        assertEquals(26.14, ecap.value!!, 0.01)
        assertEquals(0.6, ecap.coverage, 1e-9)
        assertEquals(-2, ecap.level)

        // Personal norm snapshot of the day.
        val hrvBase = r.baselines.single { it.dayKey == day && it.input == BaselineInput.LN_RMSSD }
        assertEquals(4.1, hrvBase.median28!!, 1e-9)
        assertEquals(0.1, hrvBase.mad28!!, 1e-9)
        assertEquals(28, hrvBase.n)
        assertEquals(28, r.baselineNights)
    }

    @Test
    fun `an ordinary day is at the norm`() {
        val days = fixture { copy(lnRmssd = 4.1, rhr = 51.0, rr = 14.5, sleepHours = 7.5, trimp = 0.0) }
        val r = MetricEngine.compute(days)
        val ecap = r.value(Metrics.ECAP, days.last().dayKey)
        assertEquals(50.0, ecap.value!!, 1e-9)
        assertEquals(0, ecap.level)
    }

    @Test
    fun `fewer than 14 nights means the norm is still being learned`() {
        val days = (0 until 10).map { DayInputs(start.plusDays(it.toLong()), lnRmssd = 4.0, rhr = 50.0, rr = 14.0, sleepHours = 7.0, trimp = 0.0) }
        val r = MetricEngine.compute(days)
        val last = days.last().dayKey
        assertNull(r.value(Metrics.ORS, last).value)
        assertNull(r.value(Metrics.ECAP, last).value)
        assertNull(r.value(Metrics.LRB, last).value)
        assertEquals(9, r.baselineNights)
        assertEquals(7.0, r.value(Metrics.SLP, last).value) // hours are known, the norm is not
    }

    @Test
    fun `missing HRV drops ORS below 60 percent coverage`() {
        val days = fixture { copy(lnRmssd = null, rhr = 51.0, rr = 14.5, sleepHours = 7.5, trimp = 0.0) }
        val ors = MetricEngine.compute(days).value(Metrics.ORS, days.last().dayKey)
        assertNull(ors.value)
        assertEquals(0.5, ors.coverage, 1e-9)
    }

    @Test
    fun `sleep debt over 7 days is capped at 10 hours`() {
        val short = (0 until 7).map { DayInputs(start.plusDays(it.toLong()), sleepHours = 6.0) }
        assertEquals(7.0, MetricEngine.compute(short).value(Metrics.SLP_DEBT, short.last().dayKey).value!!, 1e-9)
        val veryShort = (0 until 7).map { DayInputs(start.plusDays(it.toLong()), sleepHours = 4.0) }
        assertEquals(10.0, MetricEngine.compute(veryShort).value(Metrics.SLP_DEBT, veryShort.last().dayKey).value!!, 1e-9)
        val sparse = (0 until 7 step 3).map { DayInputs(start.plusDays(it.toLong()), sleepHours = 6.0) }
        assertNull(MetricEngine.compute(sparse).value(Metrics.SLP_DEBT, sparse.last().dayKey).value)
    }

    @Test
    fun `hard training with poor recovery tips the balance towards load`() {
        val base = fixture { copy(lnRmssd = 4.1, rhr = 51.0, rr = 14.5, sleepHours = 7.5, trimp = 0.0) }
        val hard = (1..6).map { i ->
            DayInputs(base.last().dayKey.plusDays(i.toLong()), lnRmssd = 3.8, rhr = 56.0, rr = 15.5, sleepHours = 6.0, trimp = 300.0)
        }
        val r = MetricEngine.compute(base + hard)
        val lrb = r.value(Metrics.LRB, hard.last().dayKey)
        assertNotNull(lrb.value)
        assertTrue(lrb.value!! > 1.0, "LRB=${lrb.value}")
        assertTrue(lrb.level!! >= 1)
        assertEquals(0.75, lrb.coverage, 1e-9) // WLD (self-report) arrives with slice 2
    }

    @Test
    fun `values carry their formula version`() {
        val r = MetricEngine.compute(fixture { copy(lnRmssd = ln(60.0)) })
        assertTrue(r.daily.all { it.metric.version == 1 })
        assertEquals(setOf("ORS@1", "SLP@1", "SLPDEBT@1", "TRL@1", "ECAP@1", "LRB@1"), r.daily.map { it.metric.toString() }.toSet())
    }

    @Test
    fun `levels and balance zones`() {
        assertEquals(listOf(-2, -1, 0, 0, 1, 2), listOf(-2.0, -1.0, -0.4, 0.4, 1.0, 1.5).map { MetricEngine.levelOf(it) })
        assertEquals(listOf(-2, -1, 0, 0, 1, 2), listOf(-2.5, -1.5, -1.0, 1.0, 1.5, 2.0).map { MetricEngine.balanceLevel(it) })
        assertEquals(95.0, MetricEngine.facetScale(10.0))
        assertEquals(5.0, MetricEngine.facetScale(-10.0))
    }
}
