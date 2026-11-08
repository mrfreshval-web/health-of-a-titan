package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.core.network.EdgeConfig
import com.healthofatitan.app.core.network.HotJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * `intent=link` and `intent=reauth` of the WHOOP round trip (A65 §2.3, slice А3): the account
 * bearer goes along, the body names the intent, and the refusals map to their own answers.
 */
class WhoopIntentTest {

    private lateinit var server: MockWebServer
    private val seen = CopyOnWriteArrayList<Pair<String, JsonObject>>()
    private var authorize: () -> MockResponse = {
        ok(
            """{"authorization_endpoint":"https://api.prod.whoop.com/oauth/oauth2/auth","client_id":"cid",
               "redirect_uri":"com.healthofatitan.app://oauth/whoop","scope":"offline","state":"s.link"}""",
        )
    }
    private var token: () -> MockResponse = {
        ok(
            """{"whoop_access_token":"wat","whoop_expires_at":5,"whoop_user_id":"4242","step_up_until":9,
               "identities":[{"provider":"google","subject":"g-1"},{"provider":"whoop","subject":"4242"}]}""",
        )
    }

    private fun ok(body: String, code: Int = 200) = MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = HotJson.parseToJsonElement(request.body?.utf8().orEmpty().ifBlank { "{}" }) as JsonObject
                seen += "${request.url.encodedPath} ${request.headers["Authorization"].orEmpty()}" to body
                return when (request.url.encodedPath) {
                    "/whoop/authorize" -> authorize()
                    "/whoop/token" -> token()
                    else -> ok("{}", 404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun edge() = EdgeClient(EdgeConfig(server.url("/").toString(), requireHttps = false), OkHttpClient(), HotJson)

    @Test
    fun `authorize for a link sends the bearer and the intent`() = runBlocking {
        val r = edge().authorizeFor("chal", WhoopIntent.LINK, "acc-1")
        assertIs<IntentAuthorize.Ready>(r)
        assertEquals("s.link", r.config.state)
        val (line, body) = seen.single()
        assertEquals("/whoop/authorize Bearer acc-1", line)
        assertEquals("link", body["intent"]!!.jsonPrimitive.content)
        assertEquals("chal", body["code_challenge"]!!.jsonPrimitive.content)
    }

    @Test
    fun `authorize refusals`() = runBlocking {
        authorize = { ok("""{"error":"step_up_required"}""", 403) }
        assertEquals(IntentAuthorize.StepUpRequired, edge().authorizeFor("c", WhoopIntent.LINK, "a"))
        authorize = { ok("""{"error":"not_linked"}""", 409) }
        assertEquals(IntentAuthorize.NotLinked, edge().authorizeFor("c", WhoopIntent.REAUTH, "a"))
        authorize = { ok("""{"error":"unauthorized"}""", 401) }
        assertEquals(IntentAuthorize.SignedOut, edge().authorizeFor("c", WhoopIntent.REAUTH, "a"))
    }

    @Test
    fun `token for a link returns the identities and for a reauth the window`() = runBlocking {
        val linked = edge().tokenFor("code", "s.link", "ver", WhoopIntent.LINK, "acc-1")
        assertIs<IntentToken.Linked>(linked)
        assertEquals(listOf("google", "whoop"), linked.identities.map { it.provider })
        assertEquals("4242", linked.whoopUserId)
        val body = seen.single().second
        assertEquals("link", body["intent"]!!.jsonPrimitive.content)
        assertEquals("ver", body["code_verifier"]!!.jsonPrimitive.content)

        val reauthed = edge().tokenFor("code", "s", "ver", WhoopIntent.REAUTH, "acc-1")
        assertIs<IntentToken.Reauthed>(reauthed)
        assertEquals(9L, reauthed.stepUpUntil)
    }

    @Test
    fun `token refusals`() = runBlocking {
        val cases = mapOf(
            ok("""{"error":"identity_in_use","provider":"whoop"}""", 409) to IntentToken.InUse,
            ok("""{"error":"provider_linked"}""", 409) to IntentToken.ProviderLinked,
            ok("""{"error":"step_up_required"}""", 403) to IntentToken.StepUpRequired,
            ok("""{"error":"identity_mismatch"}""", 403) to IntentToken.Mismatch,
            ok("""{"error":"unauthorized"}""", 401) to IntentToken.SignedOut,
            ok("""{"error":"invalid_grant"}""", 400) to IntentToken.Rejected,
        )
        cases.forEach { (answer, expected) ->
            token = { answer }
            assertEquals(expected, edge().tokenFor("c", "s", "v", WhoopIntent.LINK, "a"))
        }
    }
}
