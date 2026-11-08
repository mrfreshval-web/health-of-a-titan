package com.healthofatitan.app.core.account

import com.healthofatitan.app.core.network.EdgeConfig
import com.healthofatitan.app.core.network.HotJson
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * «Я › Аккаунт» on the phone (A65 §2.3, §6; slices А3 and А5): the account view, link and unlink
 * with their refusals, the device list, and `account_deleted`. The Worker is a MockWebServer.
 */
class AccountChangesTest {

    private lateinit var server: MockWebServer
    private val calls = CopyOnWriteArrayList<String>()
    private val clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
    private val key = SoftwareDeviceKey()
    private val answers = mutableMapOf<String, () -> MockResponse>()
    private var refresh: () -> MockResponse = {
        ok(
            """{"access":"acc-1","access_expires_at":${NOW + 900_000},"refresh":"hr1.r2","account_id":"a-1",
               "identities":[{"provider":"whoop","subject":"4242","linked_at":1}]}""",
        )
    }

    private fun ok(body: String, code: Int = 200) = MockResponse.Builder()
        .code(code)
        .body(body)
        .addHeader("Content-Type", "application/json")
        .build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                calls += "${request.method} $path ${request.headers["Authorization"].orEmpty()}"
                if (path == "/auth/refresh") return refresh()
                return answers[path]?.invoke() ?: ok("""{"error":"not_found"}""", 404)
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private val signedIn = AccountCredentials(refresh = "hr1.r1", accountId = "a-1", providers = listOf("whoop"), whoopUserId = "4242")

    private fun session(store: AccountCredentialStore = InMemoryAccountCredentialStore(signedIn)) =
        AccountSession(AuthApi(EdgeConfig(server.url("/").toString(), requireHttps = false), OkHttpClient(), HotJson), store, key, clock, "Pixel 9 Pro XL")

    @Test
    fun `the account view lists identities, devices and the step-up window`() = runBlocking {
        answers["/auth/account"] = {
            ok(
                """{"account_id":"a-1","step_up_until":${NOW + 600_000},
                   "identities":[{"provider":"whoop","subject":"4242","linked_at":1,"last_login_at":2},
                                 {"provider":"google","subject":"g-1","linked_at":3,"linked_device":"Pixel 9 Pro XL"}],
                   "devices":[{"id":"d1","name":"Pixel 9 Pro XL","created_at":1,"last_used_at":2,"current":true},
                              {"id":"d2","name":"Pixel 8","created_at":1,"last_used_at":2}]}""",
            )
        }
        val r = session().account()
        assertIs<AccountViewResult.Ok>(r)
        assertEquals(NOW + 600_000, r.view.stepUpUntil)
        assertTrue(r.view.has(AccountCredentials.PROVIDER_GOOGLE))
        assertEquals("Pixel 9 Pro XL", r.view.identities.last().linkedDevice)
        assertEquals(listOf(true, false), r.view.devices.map { it.current })
        assertTrue(calls.any { it == "GET /auth/account Bearer acc-1" })
    }

    @Test
    fun `a link updates the stored ways in`() = runBlocking {
        answers["/auth/link/google"] = {
            ok("""{"identities":[{"provider":"whoop","subject":"4242"},{"provider":"google","subject":"g-1"}]}""")
        }
        val store = InMemoryAccountCredentialStore(signedIn)
        val r = session(store).linkGoogle("id-token")
        assertIs<AccountChange.Done>(r)
        assertEquals(2, r.identities?.size)
        assertEquals(setOf("whoop", "google"), store.credentials.value?.providers?.toSet())
    }

    @Test
    fun `the refusals of link and unlink map to their own answers`() = runBlocking {
        val s = session()
        answers["/auth/link/google"] = { ok("""{"error":"step_up_required"}""", 403) }
        assertEquals(AccountChange.StepUpRequired, s.linkGoogle("t"))
        answers["/auth/link/google"] = { ok("""{"error":"identity_in_use","provider":"google"}""", 409) }
        assertEquals(AccountChange.IdentityInUse("google"), s.linkGoogle("t"))
        answers["/auth/link/google"] = { ok("""{"error":"provider_linked"}""", 409) }
        assertEquals(AccountChange.ProviderLinked, s.linkGoogle("t"))
        answers["/auth/unlink"] = { ok("""{"error":"last_identity"}""", 409) }
        assertEquals(AccountChange.LastIdentity, s.unlink("whoop"))
        answers["/auth/reauth/google"] = { ok("""{"error":"identity_mismatch"}""", 403) }
        assertEquals(AccountChange.Mismatch, s.reauthGoogle("t"))
        answers["/auth/devices/revoke"] = { ok("""{"error":"not_found"}""", 404) }
        assertEquals(AccountChange.Failed(404), s.revokeDevice("d9"))
    }

    @Test
    fun `a refused access token is refreshed once and the call repeated`() = runBlocking {
        var first = true
        answers["/auth/devices/revoke"] = {
            if (first) {
                first = false
                ok("""{"error":"unauthorized"}""", 401)
            } else {
                MockResponse.Builder().code(204).build()
            }
        }
        assertIs<AccountChange.Done>(session().revokeDevice("d2"))
        assertEquals(2, calls.count { it.startsWith("POST /auth/devices/revoke") })
        assertEquals(2, calls.count { it.startsWith("POST /auth/refresh") })
    }

    @Test
    fun `account_deleted raises the flag and keeps the session until the wipe`() = runBlocking {
        refresh = { ok("""{"error":"account_deleted"}""", 401) }
        val store = InMemoryAccountCredentialStore(signedIn)
        val s = session(store)
        assertFalse(s.deleted.value)
        s.checkAlive()
        assertTrue(s.deleted.value)
        assertNotNull(store.credentials.value, "the phone is wiped by the app, not here")
        assertEquals(AccountViewResult.SignedOut, s.account())
    }

    @Test
    fun `delete is a POST with the access token`() = runBlocking {
        answers["/auth/delete"] = { ok("""{"deleted":true}""") }
        assertIs<AccountChange.Done>(session().deleteAccount())
        assertTrue(calls.any { it == "POST /auth/delete Bearer acc-1" })
    }

    @Test
    fun `without a connection a change is Offline`() = runBlocking {
        val s = session()
        server.close()
        assertEquals(AccountChange.Offline, s.unlink("google"))
    }

    private companion object {
        const val NOW = 1_762_600_000_000L
    }
}
