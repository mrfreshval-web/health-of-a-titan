package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.domain.engine.Intensity
import com.healthofatitan.app.domain.engine.SessionKind
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

/** WHOOP sleeps and workouts → routine nights and HoT sessions (slice 2, A35 §4). */
class WhoopRoutineMapperTest {

    private val min = 60_000L

    private fun workout(id: String, start: String, end: String, sport: String? = "Running", sportId: Int? = 0, zones: WhoopWorkout.Zones? = null) =
        WhoopWorkout(id, start, end, "-05:00", sport, sportId, SCORED, WhoopWorkout.Score(zones))

    private fun night(id: String, start: String, end: String, baselineMin: Long = 450) = WhoopSleep(
        id = id,
        start = start,
        end = end,
        timezoneOffset = "-05:00",
        scoreState = SCORED,
        score = WhoopSleep.Score(
            stageSummary = WhoopSleep.StageSummary(light = 240 * min, slowWave = 90 * min, rem = 100 * min),
            sleepNeeded = WhoopSleep.SleepNeeded(baseline = baselineMin * min),
        ),
    )

    @Test
    fun `sport decides strength or cardio`() {
        val t = "2026-11-05T22:00:00Z"
        assertEquals(SessionKind.STRENGTH, WhoopRoutineMapper.kindOf(workout("a", t, t, "Weightlifting")))
        assertEquals(SessionKind.STRENGTH, WhoopRoutineMapper.kindOf(workout("b", t, t, "Functional Fitness")))
        assertEquals(SessionKind.STRENGTH, WhoopRoutineMapper.kindOf(workout("c", t, t, null, sportId = 123)))
        assertEquals(SessionKind.CARDIO, WhoopRoutineMapper.kindOf(workout("d", t, t, "Cycling")))
    }

    @Test
    fun `zones give the intensity class`() {
        fun z(zero: Long = 0, one: Long = 0, two: Long = 0, three: Long = 0, four: Long = 0, five: Long = 0) =
            WhoopWorkout.Zones(zero * min, one * min, two * min, three * min, four * min, five * min)
        assertEquals(Intensity.HIGH, WhoopRoutineMapper.intensityOf(z(two = 20, three = 10, four = 6, five = 4)))
        assertEquals(Intensity.ZONE2, WhoopRoutineMapper.intensityOf(z(one = 10, two = 30, three = 5)))
        assertEquals(Intensity.EASY, WhoopRoutineMapper.intensityOf(z(zero = 30, one = 20, two = 5)))
        assertEquals(Intensity.MIXED, WhoopRoutineMapper.intensityOf(z(two = 10, three = 20, four = 5)))
        assertEquals(Intensity.MIXED, WhoopRoutineMapper.intensityOf(null))
        assertEquals(Intensity.MIXED, WhoopRoutineMapper.intensityOf(z()))
    }

    @Test
    fun `nights and sessions land on the local HoT day`() {
        val window = WhoopWindow(
            cycles = emptyList(),
            recoveries = emptyList(),
            // 23:30 → 07:10 local (-05:00), woke on 2026-11-05.
            sleeps = listOf(night("n1", "2026-11-05T04:30:00Z", "2026-11-05T12:10:00Z")),
            // 18:00–18:50 local on 2026-11-05.
            workouts = listOf(workout("w1", "2026-11-05T23:00:00Z", "2026-11-05T23:50:00Z", "Weightlifting")),
        )
        val r = WhoopRoutineMapper.map(window, ZoneId.of("UTC"))
        val n = r.nights.single()
        assertEquals(LocalDate.parse("2026-11-05"), n.dayKey)
        assertEquals(7 * 60 + 10, n.wakeMin)
        assertEquals(23 * 60 + 30, n.onsetMin)
        assertEquals(450, n.needMin)
        assertEquals(-300, n.tzOffsetMin)
        val s = r.sessions.single()
        assertEquals(LocalDate.parse("2026-11-05"), s.dayKey)
        assertEquals(18 * 60, s.startMin)
        assertEquals(50, s.minutes)
        assertEquals(SessionKind.STRENGTH, s.kind)
    }
}
