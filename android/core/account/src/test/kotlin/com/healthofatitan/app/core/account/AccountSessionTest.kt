package com.healthofatitan.app.core.account

import com.healthofatitan.app.core.network.EdgeConfig
import com.healthofatitan.app.core.network.HotJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The account session on the phone (A65 §3, D-062): rotation, single-flight refresh, the device
 * key, sign-out on a revoked session, Google sign-in and the migration of `hs1`. The Worker is a
 * MockWebServer that answers like hot-edge.
 */
class AccountSessionTest {

    private lateinit var server: MockWebServer
    private val calls = CopyOnWriteArrayList<String>()
    private val bodies = CopyOnWriteArrayList<JsonObject>()
    private val clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
    private val key = SoftwareDeviceKey()
    private var refreshCode = 200
    private var googleAnswer: () -> MockResponse = { tokens("acc-g", "hr1.g") }

    private fun ok(body: String, code: Int = 200, delayMs: Long = 0) = MockResponse.Builder()
        .code(code)
        .body(body)
        .addHeader("Content-Type", "application/json")
        .headersDelay(delayMs, TimeUnit.MILLISECONDS)
        .build()

    private fun tokens(access: String, refresh: String, delayMs: Long = 0) = ok(
        """{"access":"$access","access_expires_at":${NOW + 900_000},"refresh":"$refresh","account_id":"a-1",
           "identities":[{"provider":"whoop","subject":"4242","linked_at":1}]}""",
        delayMs = delayMs,
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                calls += "$path ${request.headers["Authorization"].orEmpty()}"
                request.body?.utf8()?.takeIf { it.isNotBlank() && it != "{}" }?.let { bodies += HotJson.parseToJsonElement(it) as JsonObject }
                return when (path) {
                    "/auth/nonce" -> ok("""{"nonce":"n1.abc.def","expires_at":1}""")
                    "/auth/refresh" -> if (refreshCode == 200) tokens("acc-2", "hr1.r2", delayMs = 100) else ok("""{"error":"session_revoked"}""", refreshCode)
                    "/auth/google" -> googleAnswer()
                    "/auth/signup" -> tokens("acc-s", "hr1.s")
                    "/auth/migrate" -> ok(
                        """{"access":"acc-m","access_expires_at":${NOW + 900_000},"refresh":"hr1.m","account_id":"a-1",
                           "identities":[{"provider":"whoop","subject":"4242"}],"whoop_user_id":"4242"}""",
                    )
                    "/auth/logout", "/auth/logout-all" -> MockResponse.Builder().code(204).build()
                    else -> ok("{}", 404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun session(initial: AccountCredentials? = null, store: AccountCredentialStore = InMemoryAccountCredentialStore(initial)) =
        AccountSession(AuthApi(EdgeConfig(server.url("/").toString(), requireHttps = false), OkHttpClient(), HotJson), store, key, clock, "Pixel 9 Pro XL")

    private val signedIn = AccountCredentials(refresh = "hr1.r1", accountId = "a-1", providers = listOf("whoop"), whoopUserId = "4242")

    @Test
    fun `refresh is single-flight, signed by the device key and the rotated token is kept`() = runBlocking {
        val s = session(signedIn)
        val results = (1..5).map { async(Dispatchers.IO) { s.accessToken() } }.awaitAll()
        results.forEach { assertEquals(AccountAccess.Ok("acc-2"), it) }
        assertEquals(1, calls.count { it.startsWith("/auth/refresh") })
        assertEquals("hr1.r2", s.credentials.value?.refresh)
        val body = bodies.single()
        val ts = body["ts"]!!.jsonPrimitive.long
        assertEquals(NOW / 1000, ts)
        assertEquals("hr1.r1", body["refresh"]!!.jsonPrimitive.content)
        assertTrue(key.verify(refreshMessage("hr1.r1", ts), body["sig"]!!.jsonPrimitive.content))
    }

    @Test
    fun `a refused access token is dropped and fetched again`() = runBlocking {
        val s = session(signedIn)
        assertEquals(AccountAccess.Ok("acc-2"), s.accessToken())
        assertEquals(AccountAccess.Ok("acc-2"), s.accessToken()) // cached
        assertEquals(1, calls.size)
        s.accessToken(stale = "acc-2")
        assertEquals(2, calls.size)
    }

    @Test
    fun `a revoked session signs out on the phone and resets the device key`() = runBlocking {
        refreshCode = 401
        val s = session(signedIn)
        val before = key.publicKey()
        assertEquals(AccountAccess.SignedOut, s.accessToken())
        assertNull(s.credentials.value)
        assertNotEquals(before, key.publicKey())
        assertEquals(AccountAccess.SignedOut, s.accessToken())
        assertEquals(1, calls.size)
    }

    @Test
    fun `a server outage keeps the account`() = runBlocking {
        refreshCode = 503
        val s = session(signedIn)
        assertEquals(AccountAccess.Unavailable(503), s.accessToken())
        assertEquals("hr1.r1", s.credentials.value?.refresh)
    }

    @Test
    fun `Google sign-in sends the ID token with the device proof over the nonce, never an e-mail`() = runBlocking {
        val s = session()
        val nonce = s.googleNonce()
        assertEquals(SignInOutcome.SignedIn, s.completeGoogle("id.token.x", nonce))
        assertEquals("a-1", s.credentials.value?.accountId)
        assertEquals("4242", s.credentials.value?.whoopUserId)
        val body = bodies.single()
        assertEquals(setOf("id_token", "device_pub", "device_sig", "device_name"), body.keys)
        assertEquals(key.publicKey(), body["device_pub"]!!.jsonPrimitive.content)
        assertTrue(key.verify(loginMessage("n1.abc.def"), body["device_sig"]!!.jsonPrimitive.content))
        // the access token is in memory: no refresh needed now
        assertEquals(AccountAccess.Ok("acc-g"), s.accessToken())
        assertEquals(0, calls.count { it.startsWith("/auth/refresh") })
    }

    @Test
    fun `no account for this Google gives a ticket, the invite then creates the account`() = runBlocking {
        googleAnswer = { ok("""{"error":"no_account","signup_ticket":"t1.p.m"}""", 404) }
        val s = session()
        val outcome = assertIs<SignInOutcome.NeedsInvite>(s.completeGoogle("id", s.googleNonce()))
        assertNull(s.credentials.value)
        assertEquals(SignInOutcome.SignedIn, s.signup(outcome.signupTicket, "ABCD"))
        val body = bodies.last()
        assertEquals("t1.p.m", body["signup_ticket"]!!.jsonPrimitive.content)
        assertTrue(key.verify(loginMessage("t1.p.m"), body["device_sig"]!!.jsonPrimitive.content))
    }

    @Test
    fun `offline and unconfigured builds say so`() = runBlocking {
        val unconfigured = AccountSession(AuthApi(EdgeConfig(""), OkHttpClient(), HotJson), InMemoryAccountCredentialStore(), key, clock, "x")
        assertEquals(SignInOutcome.NotConfigured, unconfigured.completeGoogle("id", "n"))
        val offline = session()
        server.close()
        assertEquals(SignInOutcome.Offline, offline.completeGoogle("id", "n"))
    }

    @Test
    fun `migration turns hs1 into an account session`() = runBlocking {
        val s = session()
        val r = assertIs<MigrateResult.Ok>(s.migrate("hs1.4242.secret"))
        assertEquals("4242", r.whoopUserId)
        assertEquals("hr1.m", s.credentials.value?.refresh)
        assertEquals(listOf("/auth/nonce ", "/auth/migrate Bearer hs1.4242.secret"), calls.toList())
    }

    @Test
    fun `logout ends the session on the server and forgets it here`() = runBlocking {
        val s = session(signedIn)
        s.logout()
        assertNull(s.credentials.value)
        assertEquals(listOf("/auth/refresh ", "/auth/logout Bearer acc-2"), calls.toList())
    }

    @Test
    fun `logout everywhere needs the server`() = runBlocking {
        val s = session(signedIn)
        assertTrue(s.logoutAll())
        assertNull(s.credentials.value)
        refreshCode = 503
        val t = session(signedIn)
        assertEquals(false, t.logoutAll())
        assertEquals("hr1.r1", t.credentials.value?.refresh)
    }

    @Test
    fun `stored credentials carry no name, e-mail or access token`() {
        val c = AccountCredentials.of(
            AccountTokens("acc", 1, "hr1.x", "a-1", listOf(IdentityDto("google", "109"), IdentityDto("whoop", "4242"))),
        )
        val text = HotJson.encodeToString(AccountCredentials.serializer(), c)
        assertTrue(!text.contains("acc\""))
        assertEquals(listOf("google", "whoop"), c.providers)
        assertEquals("4242", c.whoopUserId)
    }

    @Test
    fun `refresh message matches the Worker`() {
        // sha256("hr1.x") in base64url, as backend/edge/src/auth-tokens.ts refreshMessage builds it
        val m = refreshMessage("hr1.x", 1_800_000_000)
        assertTrue(m.matches(Regex("""hot-refresh\|v1\|[A-Za-z0-9_-]{43}\|1800000000""")))
        assertEquals("hot-login|v1|n1.a.b", loginMessage("n1.a.b"))
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
