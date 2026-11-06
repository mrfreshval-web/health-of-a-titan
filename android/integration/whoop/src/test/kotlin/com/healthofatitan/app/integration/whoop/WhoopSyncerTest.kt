package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.core.database.dao.MetricDao
import com.healthofatitan.app.core.database.entity.BaselineSnapshotEntity
import com.healthofatitan.app.core.database.entity.MetricDailyEntity
import com.healthofatitan.app.core.database.entity.SyncStateEntity
import com.healthofatitan.app.core.network.EdgeConfig
import com.healthofatitan.app.core.network.HotJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.exp
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** In-memory MetricDao: records exactly what would reach the database. */
class FakeMetricDao : MetricDao() {
    val daily = mutableListOf<MetricDailyEntity>()
    val baselines = mutableListOf<BaselineSnapshotEntity>()
    val sync = MutableStateFlow<SyncStateEntity?>(null)

    override suspend fun upsertDaily(rows: List<MetricDailyEntity>) {
        daily.removeAll { old -> rows.any { it.dayKey == old.dayKey && it.metricId == old.metricId && it.metricVersion == old.metricVersion } }
        daily += rows
    }

    override suspend fun upsertBaselines(rows: List<BaselineSnapshotEntity>) {
        baselines += rows
    }

    override suspend fun upsertSyncState(state: SyncStateEntity) {
        sync.value = state
    }

    override fun observeLatestDay(userId: String, anchorMetric: String): Flow<List<MetricDailyEntity>> =
        sync.map { val max = daily.maxOfOrNull { it.dayKey }; daily.filter { it.dayKey == max } }

    override suspend fun series(userId: String, metricId: String, version: Int, fromDay: String) =
        daily.filter { it.metricId == metricId && it.metricVersion == version && it.dayKey >= fromDay }.sortedBy { it.dayKey }

    override fun observeSyncState(source: String): Flow<SyncStateEntity?> = sync

    override suspend fun syncState(source: String): SyncStateEntity? = sync.value

    override suspend fun clearSyncState(source: String) {
        sync.value = null
    }
}

/**
 * A18 И1.2: the 35-day window goes WHOOP → memory → HoT metrics; only MetricDaily,
 * BaselineSnapshot and the sync status reach the database; offline is a retry, not a failure.
 */
class WhoopSyncerTest {

    private lateinit var server: MockWebServer
    private val paths = CopyOnWriteArrayList<String>()
    private val now = Instant.parse("2026-11-06T13:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private var unauthorizedOnce = false

    /** 30 nights, 07:00 UTC wake-ups, HRV around 60 ms; the last night is short and low. */
    private fun sleepsJson(): String = (0 until 30).joinToString(",", "[", "]") { i ->
        val wake = LocalDate.of(2026, 11, 6).minusDays(i.toLong())
        val light = if (i == 0) 9_000_000 else 14_400_000 + (i % 3) * 600_000
        """{"id":"s$i","start":"${wake.minusDays(1)}T23:00:00Z","end":"${wake}T07:00:00Z","timezone_offset":"+00:00","nap":false,
           "score_state":"SCORED","score":{"stage_summary":{"total_light_sleep_time_milli":$light,"total_slow_wave_sleep_time_milli":5400000,
           "total_rem_sleep_time_milli":5400000},"respiratory_rate":${14.0 + (i % 2) * 0.4},"sleep_performance_percentage":90}}"""
    }

    private fun recoveriesJson(range: IntRange): String = range.joinToString(",", "[", "]") { i ->
        val hrv = if (i == 0) 38.0 else 60.0 + (i % 4)
        """{"cycle_id":$i,"sleep_id":"s$i","score_state":"SCORED","score":{"recovery_score":70,"resting_heart_rate":${if (i == 0) 58 else 50 + i % 2},
           "hrv_rmssd_milli":$hrv,"spo2_percentage":96.0,"skin_temp_celsius":33.1}}"""
    }

    private fun json(body: String, code: Int = 200) = MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                paths += url.encodedPath + (url.queryParameter("nextToken")?.let { "?next=$it" } ?: "")
                if (unauthorizedOnce && request.headers["Authorization"] == "Bearer at-old") return json("{}", 401)
                return when (url.encodedPath) {
                    "/whoop/access" -> json("""{"access_token":"at-new","expires_at":${now.toEpochMilli() + 3_600_000}}""")
                    "/developer/v2/activity/sleep" -> json("""{"records":${sleepsJson()},"next_token":null}""")
                    // Two pages: WHOOP returns at most 25 records.
                    "/developer/v2/recovery" -> if (url.queryParameter("nextToken") == null) {
                        json("""{"records":${recoveriesJson(0..24)},"next_token":"p2"}""")
                    } else {
                        json("""{"records":${recoveriesJson(25..29)}}""")
                    }
                    "/developer/v2/cycle", "/developer/v2/activity/workout" -> json("""{"records":[]}""")
                    else -> json("{}", 404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun syncer(dao: FakeMetricDao, store: WhoopCredentialStore = InMemoryWhoopCredentialStore(WhoopCredentials("hs1.4242.s", "4242"))): WhoopSyncer {
        val http = OkHttpClient()
        val edge = EdgeClient(EdgeConfig(server.url("/").toString(), requireHttps = false), http, HotJson)
        val tokens = WhoopAccessTokens(edge, store, clock).apply { prime("at-old", now.toEpochMilli() + 3_600_000) }
        return WhoopSyncer(WhoopApi(http, HotJson, tokens, server.url("/")), dao, store, clock)
    }

    @Test
    fun `sync stores only derived metrics with versions and a personal norm`() = runBlocking {
        val dao = FakeMetricDao()
        val outcome = syncer(dao).sync()

        assertIs<SyncOutcome.Done>(outcome)
        assertTrue(paths.contains("/developer/v2/recovery?next=p2"), "second page fetched")
        val today = dao.daily.filter { it.dayKey == "2026-11-06" }.associateBy { "${it.metricId}@${it.metricVersion}" }
        assertEquals(setOf("ORS@1", "SLP@1", "SLPDEBT@1", "TRL@1", "ECAP@1", "LRB@1"), today.keys)
        // A short night with low HRV and high RHR: Заряд below the norm.
        assertTrue(today.getValue("ECAP@1").value!! < 50.0)
        assertTrue(today.getValue("ORS@1").level!! < 0)
        val norm = dao.baselines.single { it.dayKey == "2026-11-06" && it.inputId == "lnRMSSD" }
        assertEquals(28, norm.n) // 28 nights before today
        assertTrue(exp(norm.median28!!) in 60.0..63.0)
        val state = dao.sync.value!!
        assertEquals("OK", state.status)
        assertEquals(28, state.baselineNights)
        assertEquals(now.toEpochMilli(), state.lastSuccessAt)
    }

    @Test
    fun `nothing raw reaches the database`() = runBlocking {
        val dao = FakeMetricDao()
        syncer(dao).sync()
        val dump = (dao.daily + dao.baselines + listOfNotNull(dao.sync.value)).joinToString("\n")
        listOf("recovery_score", "spo2", "skin_temp", "hrv_rmssd", "resting_heart_rate", "96.0", "33.1", "s0", "at-new", "hs1.")
            .forEach { assertTrue(it !in dump, "found '$it' in stored rows") }
    }

    @Test
    fun `an expired token is replaced through the vault and the sync goes on`() = runBlocking {
        unauthorizedOnce = true
        val dao = FakeMetricDao()
        assertIs<SyncOutcome.Done>(syncer(dao).sync())
        assertEquals(1, paths.count { it == "/whoop/access" })
    }

    @Test
    fun `no network is a retry and the status says offline`() = runBlocking {
        val dao = FakeMetricDao()
        val s = syncer(dao)
        server.close()
        val outcome = s.sync()
        assertEquals(SyncOutcome.Retry(SyncStatus.OFFLINE), outcome)
        assertEquals("OFFLINE", dao.sync.value?.status)
        assertTrue(dao.daily.isEmpty())
    }

    @Test
    fun `without a connection there is nothing to sync`() = runBlocking {
        val dao = FakeMetricDao()
        assertEquals(SyncOutcome.Stop(null), syncer(dao, InMemoryWhoopCredentialStore()).sync())
        assertTrue(paths.isEmpty())
    }
}
