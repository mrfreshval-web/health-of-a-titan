package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.core.network.HotJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the UI shows for each sync state, why a sync failed, and the diagnostic log. */
class SyncStateMappingTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `failures are classified with the HTTP code`() {
        fun c(e: Throwable) = SyncFailures.classify(e)
        assertEquals(ClassifiedFailure(SyncFailure.REAUTH, 401), c(WhoopApiException.ReauthRequired()))
        assertEquals(SyncFailure.NOT_ALLOWED, c(WhoopApiException.NotAllowed())?.failure)
        assertEquals(ClassifiedFailure(SyncFailure.RATE_LIMITED, 429), c(WhoopApiException.RateLimited()))
        assertEquals(ClassifiedFailure(SyncFailure.SERVER, 502, "hot-edge"), c(WhoopApiException.Unavailable(502)))
        assertEquals(ClassifiedFailure(SyncFailure.WHOOP_DENIED, 403, "/v2/recovery"), c(WhoopApiException.Http(403, "/developer/v2/recovery")))
        assertEquals(SyncFailure.WHOOP_DOWN, c(WhoopApiException.Http(503, "/x"))?.failure)
        assertEquals(SyncFailure.WHOOP_REJECTED, c(WhoopApiException.Http(404, "/x"))?.failure)
        assertEquals(SyncFailure.REAUTH, c(WhoopApiException.Http(401, "/x"))?.failure)
        assertEquals(SyncFailure.SERVER, c(EdgeNotConfiguredException())?.failure)
        assertEquals(SyncFailure.PARSE, c(SerializationException("bad"))?.failure)
        assertEquals(ClassifiedFailure(SyncFailure.OFFLINE, null, "UnknownHostException"), c(UnknownHostException("x")))
        assertEquals(SyncFailure.OFFLINE, c(IOException("reset"))?.failure)
        assertEquals(ClassifiedFailure(SyncFailure.UNKNOWN, null, "IllegalStateException"), c(IllegalStateException("x")))
        assertNull(c(WhoopApiException.NotConnected()))
        assertFailsWith<CancellationException> { c(CancellationException("stop")) }
    }

    @Test
    fun `only failures that heal by themselves are retried`() {
        val retried = SyncFailure.entries.filter { it.retryable }.toSet()
        assertEquals(setOf(SyncFailure.OFFLINE, SyncFailure.RATE_LIMITED, SyncFailure.WHOOP_DOWN, SyncFailure.SERVER), retried)
    }

    @Test
    fun `indicator follows the sync from start to a fresh success and then rests`() {
        val counts = SyncCounts(days = 30, nights = 30, workouts = 5, baselineNights = 9)
        assertEquals(SyncIndicator(SyncLight.IDLE), SyncIndicators.of(SyncProgress.Idle, 0))

        val running = SyncIndicators.of(SyncProgress.Running(SyncStep.SLEEP, 1_000, SyncTrigger.MANUAL), 2_000)
        assertEquals(SyncLight.RUNNING, running.light)
        assertEquals(SyncStep.SLEEP.fraction, running.progress)
        assertEquals(1_000, running.key)

        val done = SyncProgress.Succeeded(10_000, counts, SyncTrigger.MANUAL)
        val fresh = SyncIndicators.of(done, 11_000)
        assertEquals(SyncLight.SUCCESS, fresh.light)
        assertTrue(fresh.celebrate)
        assertTrue(fresh.haptic)
        assertEquals(10_000 + SyncIndicators.FRESH_MS, SyncIndicators.nextChangeAt(done, 11_000))

        val stale = SyncIndicators.of(done, 10_000 + SyncIndicators.FRESH_MS)
        assertEquals(SyncLight.IDLE, stale.light)
        assertNull(SyncIndicators.nextChangeAt(done, 10_000 + SyncIndicators.FRESH_MS))
    }

    @Test
    fun `background syncs never buzz`() {
        val counts = SyncCounts(1, 1, 0, 1)
        assertFalse(SyncIndicators.of(SyncProgress.Succeeded(0, counts, SyncTrigger.BACKGROUND), 10).haptic)
        assertFalse(SyncIndicators.of(SyncProgress.Succeeded(0, counts, SyncTrigger.APP_OPEN), 10).haptic)
        assertTrue(SyncIndicators.of(SyncProgress.Succeeded(0, counts, SyncTrigger.LOGIN), 10).haptic)
    }

    @Test
    fun `errors stay on screens and fade in the header`() {
        val failed = SyncProgress.Failed(1_000, SyncFailure.WHOOP_DOWN, 502, SyncTrigger.MANUAL)
        assertTrue(failed.willRetry)
        assertEquals(SyncLight.ERROR, SyncIndicators.of(failed, 1_000_000).light)
        assertEquals(SyncLight.ERROR, SyncIndicators.of(failed, 5_000, holdErrorMs = 8_000).light)
        assertEquals(9_000, SyncIndicators.nextChangeAt(failed, 5_000, holdErrorMs = 8_000))
        assertEquals(SyncLight.IDLE, SyncIndicators.of(failed, 9_000, holdErrorMs = 8_000).light)
    }

    @Test
    fun `steps move the ring forward`() {
        val fractions = SyncStep.entries.map { it.fraction }
        assertEquals(fractions.sorted(), fractions)
        assertTrue(fractions.all { it > 0f && it < 1f })
    }

    @Test
    fun `log keeps the last 20 attempts, newest first`() = runBlocking {
        val store = InMemorySyncLogStore()
        repeat(25) { store.append(SyncLogEntry(at = it.toLong(), trigger = "MANUAL", result = SyncLogEntry.OK)) }
        val entries = store.entries.value
        assertEquals(SyncLog.MAX, entries.size)
        assertEquals(24L, entries.first().at)
        assertEquals(5L, entries.last().at)
    }

    @Test
    fun `log survives a restart in its file and a broken file reads as empty`() = runBlocking {
        val file = File(tmp.root, "log.json")
        val first = FileSyncLogStore(file, HotJson)
        first.append(SyncLogEntry(at = 1, trigger = "MANUAL", result = "WHOOP_DOWN", httpCode = 502, step = "SLEEP", durationMs = 900))
        first.append(SyncLogEntry(at = 2, trigger = "BACKGROUND", result = SyncLogEntry.OK, days = 30, nights = 30, workouts = 4))
        val second = FileSyncLogStore(file, HotJson)
        assertEquals(first.entries.value, second.entries.value)
        assertEquals(502, second.entries.value.last().httpCode)

        file.writeText("{not json")
        assertTrue(FileSyncLogStore(file, HotJson).entries.value.isEmpty())
    }

    @Test
    fun `log text has status, code and counts but nothing personal`() {
        val text = SyncLog.asText(
            listOf(
                SyncLogEntry(at = 2, trigger = "MANUAL", result = SyncLogEntry.OK, durationMs = 2100, days = 30, nights = 30, workouts = 4),
                SyncLogEntry(at = 1, trigger = "BACKGROUND", result = "WHOOP_DENIED", httpCode = 403, step = "RECOVERY", detail = "/v2/recovery"),
            ),
        ) { "t$it" }
        assertEquals(
            "t2 MANUAL OK 2100ms days=30 nights=30 workouts=4\n" +
                "t1 BACKGROUND WHOOP_DENIED http=403 step=RECOVERY 0ms (/v2/recovery)",
            text,
        )
    }
}
