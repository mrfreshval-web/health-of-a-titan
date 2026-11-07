package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.core.network.HotJson
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «Сегодня» v3 (A46 §2.3, §3): the cycle's step count and kilojoules, SpO₂, skin temperature,
 * sleep efficiency, consistency and wake-ups are read into the in-memory glance, with the
 * personal corridors of the window. Nothing of it is stored (NoRawWhoopColumnsTest).
 */
class WhoopGlanceV3Test {

    private val json = HotJson

    private fun cycle(start: String, strain: Double?, kj: Double?, steps: Int?) =
        """{"id":1,"start":"$start","score_state":"SCORED","score":{"strain":${strain ?: "null"},"kilojoule":${kj ?: "null"},"step_count":${steps ?: "null"}}}"""

    @Test
    fun `cycle steps and kilojoules are read, from the score or the cycle itself`() {
        val inScore = json.decodeFromString(WhoopCycle.serializer(), cycle("2026-11-07T06:00:00Z", 6.2, 4184.0, 5200))
        assertEquals(5200, inScore.score?.stepCount)
        assertEquals(4184.0, inScore.score?.kilojoule)
        val onCycle = json.decodeFromString(WhoopCycle.serializer(), """{"id":2,"start":"2026-11-07T06:00:00Z","step_count":900}""")
        assertEquals(900, onCycle.stepCount)
        val glance = WhoopGlanceMapper.from(WhoopWindow(listOf(onCycle), emptyList(), emptyList(), emptyList()), ZoneOffset.UTC, 0L)
        assertEquals(900, glance.cycle?.steps)
        assertNull(glance.cycle?.strain)
    }

    @Test
    fun `sleep and recovery fields of section 3 reach the glance and the corridors`() {
        val sleeps = (1..10).map { d ->
            val day = d.toString().padStart(2, '0')
            """{"id":"s$d","start":"2026-10-${day}T23:00:00Z","end":"2026-10-${(d + 1).toString().padStart(2, '0')}T06:30:00Z","nap":false,"score_state":"SCORED",
              "score":{"respiratory_rate":${14.0 + d * 0.1},"sleep_efficiency_percentage":91.0,"sleep_consistency_percentage":77.0,
              "stage_summary":{"total_light_sleep_time_milli":12000000,"total_slow_wave_sleep_time_milli":5000000,"total_rem_sleep_time_milli":6000000,"total_awake_time_milli":1000000,"disturbance_count":7}}}"""
        }.map { json.decodeFromString(WhoopSleep.serializer(), it) }
        val recoveries = (1..10).map { d ->
            json.decodeFromString(
                WhoopRecovery.serializer(),
                """{"sleep_id":"s$d","score_state":"SCORED","score":{"resting_heart_rate":${50 + d},"hrv_rmssd_milli":${40 + d},"spo2_percentage":96.5,"skin_temp_celsius":${33.0 + d * 0.05}}}""",
            )
        }
        val g = WhoopGlanceMapper.from(WhoopWindow(emptyList(), recoveries, sleeps, emptyList()), ZoneOffset.UTC, Instant.parse("2026-10-12T08:00:00Z").toEpochMilli())
        val sleep = assertNotNull(g.sleep)
        assertEquals(91.0, sleep.efficiency)
        assertEquals(77.0, sleep.consistency)
        assertEquals(7, sleep.disturbances)
        assertNotNull(sleep.respiratoryRate)
        assertEquals(96.5, g.recovery?.spo2)
        assertNotNull(g.recovery?.skinTempC)
        val c = assertNotNull(g.corridors)
        assertEquals(55.5, c.restingHr!!.mean, 1e-9)
        assertEquals(10, c.hrv!!.n)
        assertTrue(c.respiratoryRate!!.sd > 0)
        assertEquals(57.0, c.restingHr7!!, 1e-9) // nights 4–10: 54…60
    }
}
