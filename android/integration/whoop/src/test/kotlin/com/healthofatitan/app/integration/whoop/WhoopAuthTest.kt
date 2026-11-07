package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.core.network.EdgeConfig
import com.healthofatitan.app.core.network.HotJson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A18 И1.1 on the phone side: PKCE, the Worker contract, single-flight access tokens, session
 * loss, disconnect. The Worker is a MockWebServer that behaves like hot-edge.
 */
class WhoopAuthTest {

    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<String>()
    private var accessHandler: (RecordedRequest) -> MockResponse = { ok("""{"access_token":"at-1","expires_at":${NOW + 3_600_000}}""", delayMs = 150) }

    private val clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)

    private fun ok(body: String, code: Int = 200, delayMs: Long = 0) = MockResponse.Builder()
        .code(code)
        .body(body)
        .addHeader("Content-Type", "application/json")
        .headersDelay(delayMs, TimeUnit.MILLISECONDS)
        .build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                requests += "$path ${request.headers["Authorization"].orEmpty()}"
                return when (path) {
                    "/whoop/access" -> accessHandler(request)
                    "/whoop/authorize" -> ok(
                        """{"authorization_endpoint":"https://api.prod.whoop.com/oauth/oauth2/auth","client_id":"cid",
                           "redirect_uri":"com.healthofatitan.app://oauth/whoop","scope":"offline read:recovery","state":"signed.state","authorize_url":"x"}""",
                    )
                    "/whoop/token" -> ok("""{"session":"hs1.4242.s","access_token":"at-0","expires_at":${NOW + 3_600_000},"whoop_user_id":"4242"}""")
                    "/whoop/disconnect" -> MockResponse.Builder().code(204).build()
                    else -> ok("{}", code = 404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    // MockWebServer speaks plain HTTP; the app itself always requires HTTPS.
    private fun edgeHttp() = EdgeClient(EdgeConfig(server.url("/").toString(), requireHttps = false), OkHttpClient(), HotJson)

    @Test
    fun `PKCE matches RFC 7636 appendix B`() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challengeOf("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val p = Pkce.create()
        assertEquals(43, p.verifier.length)
        assertTrue(p.verifier.matches(Regex("[A-Za-z0-9_-]{43}")))
        assertEquals(Pkce.challengeOf(p.verifier), p.challenge)
    }

    @Test
    fun `an unconfigured build never calls the network`() = runBlocking {
        val client = EdgeClient(EdgeConfig(""), OkHttpClient(), HotJson)
        val result = runCatching { client.authorize("c".repeat(43)) }
        assertIs<EdgeNotConfiguredException>(result.exceptionOrNull())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `two parallel requests for an expired token cause one call to the vault (single-flight)`() = runBlocking {
        val store = InMemoryWhoopCredentialStore(WhoopCredentials("hs1.4242.s", "4242"))
        val tokens = WhoopAccessTokens(edgeHttp(), store, clock)
        tokens.prime("old", NOW + 10_000) // expires in 10 s: below the margin
        val results = (1..5).map { async(kotlinx.coroutines.Dispatchers.IO) { tokens.get() } }.awaitAll()
        assertEquals(1, requests.count { it.startsWith("/whoop/access") })
        results.forEach { assertEquals(TokenOutcome.Ok("at-1"), it) }
        assertEquals("/whoop/access Bearer hs1.4242.s", requests.single())
    }

    @Test
    fun `a token rejected by WHOOP is replaced once`() = runBlocking {
        val store = InMemoryWhoopCredentialStore(WhoopCredentials("hs1.4242.s", "4242"))
        val tokens = WhoopAccessTokens(edgeHttp(), store, clock)
        tokens.prime("at-0", NOW + 3_600_000)
        assertEquals(TokenOutcome.Ok("at-0"), tokens.get())
        assertEquals(TokenOutcome.Ok("at-1"), tokens.get(staleToken = "at-0"))
        assertEquals(1, requests.size)
    }

    @Test
    fun `a dead session wipes the local credential`() = runBlocking {
        accessHandler = { ok("""{"error":"reauth_required"}""", code = 401) }
        val store = InMemoryWhoopCredentialStore(WhoopCredentials("hs1.4242.s", "4242"))
        val tokens = WhoopAccessTokens(edgeHttp(), store, clock)
        assertEquals(TokenOutcome.ReauthRequired, tokens.get())
        assertNull(store.credentials.value)
        assertEquals(TokenOutcome.NotConnected, tokens.get())
    }

    @Test
    fun `a worker outage keeps the session`() = runBlocking {
        accessHandler = { ok("""{"error":"whoop_unavailable"}""", code = 502) }
        val store = InMemoryWhoopCredentialStore(WhoopCredentials("hs1.4242.s", "4242"))
        assertEquals(TokenOutcome.Unavailable(502),WhoopAccessTokens(edgeHttp(), store, clock).get())
        assertEquals("4242", store.credentials.value?.whoopUserId)
    }

    @Test
    fun `login exchanges the code at the Worker, never at WHOOP`() = runBlocking {
        val edge = edgeHttp()
        val config = edge.authorize(Pkce.create().challenge)
        assertEquals("signed.state", config.state)
        assertEquals("com.healthofatitan.app://oauth/whoop", config.redirectUri)
        val result = edge.token("code", config.state, Pkce.create().verifier)
        assertEquals(TokenResult.Connected("hs1.4242.s", "at-0", NOW + 3_600_000, "4242"), result)
        assertEquals(listOf("/whoop/authorize ", "/whoop/token "), requests.toList())
    }

    @Test
    fun `a user outside the allowlist learns only the WHOOP user id`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = ok("""{"error":"not_allowed","whoop_user_id":"987654"}""", code = 403)
        }
        assertEquals(TokenResult.NotAllowed("987654"), edgeHttp().token("code", "state", Pkce.create().verifier))
    }

    @Test
    fun `disconnect is confirmed by 204`() = runBlocking {
        assertTrue(edgeHttp().disconnect("hs1.4242.s"))
        assertEquals("/whoop/disconnect Bearer hs1.4242.s", requests.single())
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
