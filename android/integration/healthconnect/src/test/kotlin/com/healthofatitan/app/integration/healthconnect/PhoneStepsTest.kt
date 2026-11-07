package com.healthofatitan.app.integration.healthconnect

import com.healthofatitan.app.domain.engine.Steps
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A35 §2 / A18 И3b.5: phone steps — one source, windows, the personal goal and the states. */
class PhoneStepsTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 11, 12)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day): Instant = d.atTime(h, m).atZone(zone).toInstant()

    @Test
    fun `one source wins so a sensor read by two apps is not doubled`() {
        val chunks = listOf(
            StepChunk("android", at(8), at(9), 3000),
            StepChunk("com.google.android.apps.fitness", at(8), at(9), 2900),
            StepChunk("android", at(12), at(13), 1000),
        )
        assertEquals("android", StepMath.pickOrigin(chunks))
        assertEquals(4000, StepMath.onlyOrigin(chunks).sumOf { it.count }.toInt())
        assertNull(StepMath.pickOrigin(emptyList()))
        assertEquals(emptyList(), StepMath.onlyOrigin(emptyList()))
    }

    @Test
    fun `a window takes the overlapping share of a record`() {
        val chunks = listOf(
            StepChunk("android", at(18), at(18, 20), 2000),
            StepChunk("android", at(18, 20), at(18, 40), 1000),
            StepChunk("android", at(18, 30), at(18, 30), 50),
            StepChunk("android", at(20), at(21), 500),
        )
        assertEquals(2000 + 500, StepMath.within(chunks, at(18), at(18, 30)))
        assertEquals(50, StepMath.within(listOf(chunks[2]), at(18), at(18, 31)), "an instant record counts once the window includes it")
        assertEquals(0, StepMath.within(chunks, at(19), at(19, 30)))
        assertEquals(0, StepMath.within(chunks, at(19), at(18)))
        // the best 20-minute stretch between 17:00 and 21:00 is the walk at 18:00
        assertEquals(2000, StepMath.bestWindow(chunks, at(17), at(21), 20))
        // a window shorter than the walk is taken whole
        assertEquals(2000 + 500, StepMath.bestWindow(chunks, at(18), at(18, 30), 45))
    }

    @Test
    fun `days are split by the local date of the record`() {
        val chunks = listOf(
            StepChunk("android", at(10, d = day.minusDays(1)), at(11, d = day.minusDays(1)), 7000),
            StepChunk("android", at(10), at(11), 4000),
            StepChunk("android", at(15), at(16), 1000),
        )
        assertEquals(mapOf(day.minusDays(1) to 7000, day to 5000), StepMath.daily(chunks, zone))
    }

    private class FakeSource(
        var available: Boolean = true,
        var granted: Boolean = true,
        val chunks: List<StepChunk> = emptyList(),
    ) : PhoneStepsSource {
        var reads = 0
        override fun available() = available
        override suspend fun granted() = granted
        override suspend fun read(from: Instant, to: Instant): List<StepChunk> {
            reads++
            return chunks.filter { !it.start.isBefore(from) && it.start.isBefore(to) }
        }
    }

    private val clock = Clock.fixed(at(19), zone)

    @Test
    fun `states follow availability and the permission`() = runTest {
        val source = FakeSource(available = false)
        val repo = StepsRepository(source, clock)
        repo.refresh()
        assertEquals(PhoneSteps.Unavailable, repo.state.value)
        source.available = true
        source.granted = false
        repo.refresh()
        assertEquals(PhoneSteps.NeedsPermission, repo.state.value)
    }

    @Test
    fun `today, the goal from history and the walk slot`() = runTest {
        val history = (1..5).map { i -> StepChunk("android", at(12, d = day.minusDays(i.toLong())), at(13, d = day.minusDays(i.toLong())), 6000L + i * 1000) }
        val today = listOf(
            StepChunk("android", at(8), at(9), 2500),
            StepChunk("android", at(18), at(18, 20), 1800),
        )
        val source = FakeSource(chunks = history + today)
        val repo = StepsRepository(source, clock)
        repo.refresh(WalkWindow(at(17), at(21), 20))
        val ready = repo.state.value as PhoneSteps.Ready
        assertEquals(4300, ready.today)
        assertEquals(9000, ready.goal)
        assertEquals(1800, ready.inSlot)
        assertEquals(Steps.fraction(4300, 9000), ready.fraction)
        val reads = source.reads
        repo.refresh()
        assertNull((repo.state.value as PhoneSteps.Ready).inSlot)
        assertEquals(reads + 1, source.reads, "the goal is computed once a day")
    }

    @Test
    fun `hours with movement are split by the hour of the day`() {
        val start = java.time.Instant.parse("2026-11-07T00:00:00Z")
        val chunks = listOf(
            StepChunk("android", start.plusSeconds(8 * 3_600L), start.plusSeconds(9 * 3_600L), 2_500),
            StepChunk("android", start.plusSeconds(13 * 3_600L + 1_800), start.plusSeconds(14 * 3_600L + 1_800), 600),
        )
        val hourly = StepMath.hourly(chunks, start)
        assertEquals(24, hourly.size)
        assertEquals(2_500, hourly[8])
        assertEquals(300, hourly[13])
        assertEquals(300, hourly[14])
        assertEquals(0, hourly[20])
    }

    @Test
    fun `a failing read degrades to unavailable, never crashes`() = runTest {
        val broken = object : PhoneStepsSource {
            override fun available() = true
            override suspend fun granted() = true
            override suspend fun read(from: Instant, to: Instant): List<StepChunk> = error("binder died")
        }
        val repo = StepsRepository(broken, clock)
        repo.refresh()
        assertEquals(PhoneSteps.Unavailable, repo.state.value)
    }
}
