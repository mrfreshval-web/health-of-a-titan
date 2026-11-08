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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One HoT account per install (A65 §2.3, review 2026-11-08 А-2): the owner of the data on the
 * phone survives a sign-out; a sign-in into another account keeps nothing until «Стереть и войти».
 */
class DataOwnerTest {

    private lateinit var server: MockWebServer
    private val calls = CopyOnWriteArrayList<String>()
    private var accountId = "a-2"
    private val clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                calls += "$path ${request.headers["Authorization"].orEmpty()}"
                return when (path) {
                    "/auth/nonce" -> ok("""{"nonce":"n1.abc.def","expires_at":1}""")
                    "/auth/google" -> ok(
                        """{"access":"acc-$accountId","access_expires_at":${NOW + 900_000},"refresh":"hr1.$accountId",
                           "account_id":"$accountId","identities":[{"provider":"google","subject":"g"}]}""",
                    )
                    "/auth/logout" -> MockResponse.Builder().code(204).build()
                    else -> ok("{}", 404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun ok(body: String, code: Int = 200) = MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build()

    private fun session(store: AccountCredentialStore, owner: DataOwnerStore) = AccountSession(
        AuthApi(EdgeConfig(server.url("/").toString(), requireHttps = false), OkHttpClient(), HotJson),
        store,
        SoftwareDeviceKey(),
        clock,
        "Pixel 9 Pro XL",
        owner = owner,
    )

    @Test
    fun `a sign-in into another account keeps nothing until the choice, and Cancel ends that session`() = runBlocking {
        val store = InMemoryAccountCredentialStore()
        val owner = InMemoryDataOwnerStore("a-1")
        val s = session(store, owner)
        assertEquals(SignInOutcome.OtherOwner, s.completeGoogle("id", "n1.abc.def"))
        assertNull(store.credentials.value, "the session of a-2 is not kept")
        assertEquals("a-1", owner.get(), "the data still belongs to a-1")
        assertTrue(s.otherOwner.value)
        s.cancelOtherOwner()
        assertFalse(s.otherOwner.value)
        assertTrue(calls.any { it == "/auth/logout Bearer acc-a-2" }, "the held session is ended on the server")
        assertNull(store.credentials.value)
    }

    @Test
    fun `the same account signs in again after a sign-out with its data in place`() = runBlocking {
        val store = InMemoryAccountCredentialStore()
        val owner = InMemoryDataOwnerStore()
        accountId = "a-1"
        val s = session(store, owner)
        assertEquals(SignInOutcome.SignedIn, s.completeGoogle("id", "n1.abc.def"))
        assertEquals("a-1", owner.get(), "the first sign-in makes the owner")
        s.logout()
        assertNull(store.credentials.value)
        assertEquals("a-1", owner.get(), "a sign-out keeps the owner")
        assertEquals(SignInOutcome.SignedIn, s.completeGoogle("id", "n1.abc.def"))
        assertFalse(s.otherOwner.value)
    }

    @Test
    fun `an install of slices A1-A2 takes the signed-in account as the owner`() {
        val owner = InMemoryDataOwnerStore()
        session(InMemoryAccountCredentialStore(AccountCredentials(refresh = "hr1.r", accountId = "a-7", providers = listOf("whoop"))), owner)
        assertEquals("a-7", owner.get())
    }

    private companion object {
        const val NOW = 1_762_600_000_000L
    }
}
