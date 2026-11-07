package com.healthofatitan.app.integration.whoop.day

import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.integration.whoop.SleepGlance
import com.healthofatitan.app.integration.whoop.StrainGlance
import com.healthofatitan.app.integration.whoop.WhoopGlance
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** D-029: the header rings come from data only — WHOOP strain, the plan's bedtime, no input. */
class HeaderRingsTest {

    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 11, 10)

    private fun plan(regime: Regime, bedtimeMin: Int = 23 * 60 + 30) = DayView(
        dayKey = day, regime = regime, flags = emptySet(), headline = "", summary = "", fired = emptyList(),
        items = emptyList(), bedtimeMin = bedtimeMin, caffeineCutoffMin = 14 * 60, windDownMin = 22 * 60 + 30,
    )

    private fun glance(strain: Double?, wake: LocalDateTime?) = WhoopGlance(
        fetchedAt = 0L,
        sleep = wake?.let { SleepGlance(day, it.toInstant(zone), 7.0, 8.0) },
        recovery = null,
        workout = null,
        dayStrain = strain?.let { StrainGlance(day.atTime(6, 0).toInstant(zone), it) },
    )

    @Test
    fun `load against the mode ceiling and time left until bedtime`() {
        val now = day.atTime(14, 17)
        val r = HeaderRingsMapper.of(plan(Regime.STEADY), glance(8.4, day.atTime(6, 30)), now, zone)
        assertEquals(8.4, r.strain)
        assertEquals(12.0, r.strainCeiling)
        assertEquals(9 * 60 + 13, r.untilBedMin)
        assertEquals(23 * 60 + 30, r.bedtimeMin)
        // 06:30 → 23:30 is 17 h; 14:17 is 7 h 47 min into it
        assertEquals((7 * 60 + 47) / (17f * 60), r.dayFraction!!, 0.001f)
        assertNull(r.steps) // no step source yet: the ring is dashed, never 0
    }

    @Test
    fun `ceilings follow the mode`() {
        val now = day.atTime(9, 0)
        assertEquals(16.0, HeaderRingsMapper.of(plan(Regime.BOOST), null, now, zone).strainCeiling)
        assertEquals(7.0, HeaderRingsMapper.of(plan(Regime.GENTLE), null, now, zone).strainCeiling)
    }

    @Test
    fun `without a plan only the strain is shown and without a night the day is 16 h`() {
        val noPlan = HeaderRingsMapper.of(null, glance(3.1, null), day.atTime(9, 0), zone)
        assertEquals(3.1, noPlan.strain)
        assertNull(noPlan.untilBedMin)
        assertNull(noPlan.strainCeiling)

        val noNight = HeaderRingsMapper.of(plan(Regime.STEADY, bedtimeMin = 23 * 60), null, day.atTime(15, 0), zone)
        assertEquals(0.5f, noNight.dayFraction!!, 0.001f) // 07:00 → 23:00, at 15:00
        assertEquals(0, HeaderRingsMapper.of(plan(Regime.STEADY, bedtimeMin = 23 * 60), null, day.atTime(23, 40), zone).untilBedMin)
    }
}
