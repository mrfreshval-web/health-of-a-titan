package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.core.network.HotJson
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ln
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** WHOOP v2 JSON (shape of developer.whoop.com/api examples) → HoT days, on the JVM. */
class WhoopWindowMapperTest {

    private val sleeps = """
        {"records":[
          {"id":"s-main","cycle_id":93845,"user_id":4242,"created_at":"2026-11-05T11:25:44.774Z","updated_at":"2026-11-05T14:25:44.774Z",
           "start":"2026-11-05T04:30:00.000Z","end":"2026-11-05T12:00:00.000Z","timezone_offset":"-05:00","nap":false,"score_state":"SCORED",
           "score":{"stage_summary":{"total_in_bed_time_milli":27000000,"total_awake_time_milli":1800000,"total_no_data_time_milli":0,
             "total_light_sleep_time_milli":12600000,"total_slow_wave_sleep_time_milli":5400000,"total_rem_sleep_time_milli":7200000,
             "sleep_cycle_count":4,"disturbance_count":9},
             "sleep_needed":{"baseline_milli":27395716,"need_from_sleep_debt_milli":352230,"need_from_recent_strain_milli":208595,"need_from_recent_nap_milli":-12312},
             "respiratory_rate":14.6,"sleep_performance_percentage":98,"sleep_consistency_percentage":90,"sleep_efficiency_percentage":91.7}},
          {"id":"s-nap","cycle_id":93845,"start":"2026-11-05T19:00:00.000Z","end":"2026-11-05T19:40:00.000Z","timezone_offset":"-05:00","nap":true,
           "score_state":"SCORED","score":{"stage_summary":{"total_light_sleep_time_milli":2400000},"respiratory_rate":15.0}},
          {"id":"s-short","cycle_id":93845,"start":"2026-11-05T09:00:00.000Z","end":"2026-11-05T10:00:00.000Z","timezone_offset":"-05:00","nap":false,
           "score_state":"SCORED","score":{"stage_summary":{"total_light_sleep_time_milli":3000000},"respiratory_rate":13.0}},
          {"id":"s-pending","cycle_id":93846,"start":"2026-11-06T04:00:00.000Z","end":"2026-11-06T11:00:00.000Z","timezone_offset":"-05:00","nap":false,
           "score_state":"PENDING_SCORE"}
        ],"next_token":null}
    """.trimIndent()

    private val recoveries = """
        {"records":[{"cycle_id":93845,"sleep_id":"s-main","user_id":4242,"created_at":"2026-11-05T12:25:44.774Z","updated_at":"2026-11-05T12:25:44.774Z",
          "score_state":"SCORED","score":{"user_calibrating":false,"recovery_score":44,"resting_heart_rate":64,"hrv_rmssd_milli":31.813562,
          "spo2_percentage":95.6875,"skin_temp_celsius":33.7}}]}
    """.trimIndent()

    private val workouts = """
        {"records":[
          {"id":"w-1","user_id":4242,"start":"2026-11-05T22:00:00.000Z","end":"2026-11-05T22:45:00.000Z","timezone_offset":"-05:00",
           "sport_name":"running","score_state":"SCORED","score":{"strain":8.25,"average_heart_rate":123,"max_heart_rate":146,"kilojoule":1569.34,
           "percent_recorded":100,"distance_meter":1772.77,"altitude_gain_meter":46.64,"altitude_change_meter":-0.78,
           "zone_durations":{"zone_zero_milli":300000,"zone_one_milli":600000,"zone_two_milli":900000,"zone_three_milli":600000,
             "zone_four_milli":300000,"zone_five_milli":0}}},
          {"id":"w-early","user_id":4242,"start":"2026-11-05T10:30:00.000Z","end":"2026-11-05T10:50:00.000Z","timezone_offset":"-05:00",
           "score_state":"SCORED","score":{"zone_durations":{"zone_one_milli":1200000}}}
        ]}
    """.trimIndent()

    private val cycles = """
        {"records":[
          {"id":93845,"user_id":4242,"start":"2026-11-04T23:30:00.000-05:00","end":"2026-11-05T23:30:00.000-05:00","timezone_offset":"-05:00",
           "score_state":"SCORED","score":{"strain":5.29,"kilojoule":8288.3,"average_heart_rate":68,"max_heart_rate":141}},
          {"id":93800,"user_id":4242,"start":"2026-11-02T23:00:00.000Z","end":"2026-11-03T23:00:00.000Z","timezone_offset":"+00:00","score_state":"SCORED"}
        ]}
    """.trimIndent()

    private fun window() = WhoopWindow(
        cycles = HotJson.decodeFromString(WhoopPage.serializer(WhoopCycle.serializer()), cycles).records,
        recoveries = HotJson.decodeFromString(WhoopPage.serializer(WhoopRecovery.serializer()), recoveries).records,
        sleeps = HotJson.decodeFromString(WhoopPage.serializer(WhoopSleep.serializer()), sleeps).records,
        workouts = HotJson.decodeFromString(WhoopPage.serializer(WhoopWorkout.serializer()), workouts).records,
    )

    private val days by lazy { WhoopWindowMapper.map(window(), ZoneId.of("UTC")) }

    @Test
    fun `the day is the local date of waking up, in the sleep's own offset`() {
        // Wake 12:00Z = 07:00 at −05:00 on 5 Nov.
        val day = days.single { it.dayKey == LocalDate.of(2026, 11, 5) }
        assertEquals((12_600_000 + 5_400_000 + 7_200_000) / 3_600_000.0, day.sleepHours!!, 1e-9) // 7 h, awake excluded
        assertEquals(14.6, day.rr)
        // Mid-sleep 08:15Z = 03:15 local.
        assertEquals(195.0, day.sleepMidMin!!, 1e-9)
    }

    @Test
    fun `recovery joins its sleep and HRV becomes ln rMSSD`() {
        val day = days.single { it.dayKey == LocalDate.of(2026, 11, 5) }
        assertEquals(ln(31.813562), day.lnRmssd!!, 1e-12)
        assertEquals(64.0, day.rhr)
    }

    @Test
    fun `naps, shorter duplicates and unscored sleeps are ignored`() {
        val day = days.single { it.dayKey == LocalDate.of(2026, 11, 5) }
        assertEquals(7.0, day.sleepHours!!, 1e-9) // not the 1-hour «s-short», not the nap
        assertNull(days.firstOrNull { it.dayKey == LocalDate.of(2026, 11, 6) }?.sleepHours)
    }

    @Test
    fun `workouts give Edwards TRIMP to the day of the wake-up before them`() {
        val nov5 = days.single { it.dayKey == LocalDate.of(2026, 11, 5) }
        // w-1: 10·1 + 15·2 + 10·3 + 5·4 = 90; w-early (05:30 local, before the 07:00 wake) → 4 Nov.
        assertEquals(90.0, nov5.trimp!!, 1e-9)
        val nov4 = days.single { it.dayKey == LocalDate.of(2026, 11, 4) }
        assertEquals(20.0, nov4.trimp!!, 1e-9)
    }

    @Test
    fun `a scored cycle marks a worn day with zero load`() {
        // Cycle 93800 starts 23:00 at +00:00 → wake day 3 Nov; no sleep or workout data that day.
        val nov3 = days.single { it.dayKey == LocalDate.of(2026, 11, 3) }
        assertEquals(0.0, nov3.trimp)
        assertNull(nov3.sleepHours)
        assertNull(nov3.lnRmssd)
    }
}
