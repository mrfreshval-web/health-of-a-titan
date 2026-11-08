package com.healthofatitan.app.feature.mentor

import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.network.EdgeConfig
import com.healthofatitan.app.core.network.HotJson
import com.healthofatitan.app.domain.engine.CrisisAssessment
import com.healthofatitan.app.domain.engine.CrisisLevel
import com.healthofatitan.app.domain.mentor.MentorTier
import com.healthofatitan.app.domain.mentor.Speaker
import com.healthofatitan.app.feature.mentor.data.InMemoryMentorMemory
import com.healthofatitan.app.feature.mentor.data.MentorApi
import com.healthofatitan.app.feature.mentor.data.MentorCall
import com.healthofatitan.app.feature.mentor.data.MentorReply
import com.healthofatitan.app.feature.mentor.data.TalkSummary
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Винс v1 on the phone (A62, A07 §4): the crisis leads to «Помощь» and never to the model, the
 * answer is checked before the person sees it, the ContextPack carries only what is allowed.
 */
class MentorConversationTest {

    private val calls = mutableListOf<MentorCall>()
    private val logged = mutableListOf<CrisisAssessment>()
    private var reply: MentorReply = MentorReply.Text("Присед 4×6, отдых 150 с.", MentorTier.LIGHT, softLimit = false)
    private var bus = MentorSafety.NORMAL
    private val memory = InMemoryMentorMemory()

    private fun conversation(setup: MentorSetup = MentorSetup(persona = MentorPersona.BRO)) = MentorConversation(
        send = { calls += it; reply },
        safety = { bus },
        setup = { setup },
        memory = memory,
        logCrisis = { logged += it },
        clock = { 1L },
    )

    @Test
    fun `words of level 2 open help, are logged by level only and never reach the model`() = runBlocking {
        val c = conversation().apply { start() }
        c.send("Не хочу жить")
        assertTrue(calls.isEmpty(), "the model is never asked")
        assertEquals(CrisisLevel.PASSIVE, logged.single().level)
        val ui = c.ui.value
        assertTrue(ui.crisis)
        assertTrue(ui.openHelp, "«Помощь» opens by itself")
        assertEquals(LineKind.CRISIS, ui.lines.last().kind)
        assertTrue("112" in ui.lines.last().text && "911" in ui.lines.last().text)
        // next words of the same conversation do not carry the crisis to the model either
        c.helpOpened()
        bus = bus.copy(crisis = true)
        c.send("ладно, а что с тренировкой?")
        assertTrue(calls.isEmpty(), "an open crisis window stops coaching")
    }

    @Test
    fun `the model's crisis marker opens help`() = runBlocking {
        reply = MentorReply.Crisis(2)
        val c = conversation().apply { start() }
        c.send("мне как-то странно")
        assertEquals(1, calls.size)
        assertTrue(c.ui.value.openHelp)
        assertEquals(CrisisLevel.PASSIVE, logged.last().level)
    }

    @Test
    fun `level 1 goes to the model with the level and no summary is kept`() = runBlocking {
        val c = conversation().apply { start() }
        c.send("сил больше нет, всё валится")
        assertEquals(1, calls.single().crisisLevel)
        repeat(3) { c.send("ещё вопрос $it") }
        c.close()
        assertTrue(memory.summaries.value.isEmpty())
    }

    @Test
    fun `under ED_CARE calories are replaced, and the pack carries codes, not reasons`() = runBlocking {
        bus = MentorSafety(restrictions = setOf("HIDE_CALORIES", "HIDE_WEIGHT", "HIDE_FOOD_TALK"), edCare = true)
        memory.add(TalkSummary(0, "Говорили о калориях на ужин."))
        memory.add(TalkSummary(0, "Договорились ложиться до 23:30."))
        reply = MentorReply.Text("Держи 1800 ккал в день.", MentorTier.MID, softLimit = false)
        val c = conversation().apply { start() }
        c.send("что на ужин?")
        assertEquals(MentorTexts.ED_CARE_REPLACED, c.ui.value.lines.last().text)
        val pack = calls.single().pack
        assertEquals(listOf("ed_care"), pack.flags)
        assertEquals(listOf("Договорились ложиться до 23:30."), pack.summaries)
        assertEquals(null, pack.about)
    }

    @Test
    fun `doses from the model never reach the person`() = runBlocking {
        reply = MentorReply.Text("Магний 400 мг на ночь.", MentorTier.HIGH, softLimit = false)
        val c = conversation().apply { start() }
        c.send("что пить для сна?")
        assertEquals(MentorTexts.DOSE_REPLACED, c.ui.value.lines.last().text)
    }

    @Test
    fun `a missing key is said plainly`() = runBlocking {
        reply = MentorReply.NotConfigured("ANTHROPIC_API_KEY")
        val c = conversation().apply { start() }
        c.send("привет")
        assertEquals(MentorTexts.NOT_CONFIGURED, c.ui.value.lines.last().text)
        assertEquals(LineKind.NOTICE, c.ui.value.lines.last().kind)
    }

    @Test
    fun `the tier lifts and is sent as the floor of the next turn, a good talk is summarised`() = runBlocking {
        val c = conversation().apply { start() }
        reply = MentorReply.Text("Покажи колено врачу на этой неделе.", MentorTier.HIGH, softLimit = false)
        c.send("болит колено")
        reply = MentorReply.Text("Ок, держим план.", MentorTier.LIGHT, softLimit = false)
        c.send("спасибо")
        c.send("и ещё")
        assertEquals(MentorTier.HIGH, calls.last().floor)
        assertEquals(Speaker.PERSON, calls.last().window.first().speaker)
        assertTrue(calls.last().window.none { it.text == MentorTexts.greeting(MentorPersona.BRO) }, "the app's greeting is not Винс's words")
        reply = MentorReply.Text("Обсудили колено. Договорились показать врачу.", null, softLimit = false)
        c.close()
        assertTrue(calls.last().summary)
        assertEquals(1, memory.summaries.value.size)
    }

    @Test
    fun `the request body carries the pack and nothing else`() {
        val server = MockWebServer()
        server.enqueue(MockResponse.Builder().code(200).body("""{"type":"crisis","level":2,"source":"model"}""").build())
        server.start()
        try {
            val api = MentorApi(EdgeConfig(server.url("/").toString(), requireHttps = false), OkHttpClient(), HotJson)
            var sent: MentorCall? = null
            val c = MentorConversation(
                send = { call -> sent = call; api.send(call, "hs1.4242.s") },
                safety = { MentorSafety(restrictions = setOf("HIDE_CALORIES"), edCare = true) },
                setup = { MentorSetup(persona = MentorPersona.BUTLER, addressForm = "f") },
                memory = memory,
                logCrisis = { logged += it },
            )
            val result = runBlocking {
                c.start()
                c.send("как спала — не знаю")
                c.ui.value
            }
            assertTrue(result.openHelp)
            val request = server.takeRequest()
            assertEquals("/llm/mentor", request.url.encodedPath)
            assertEquals("Bearer hs1.4242.s", request.headers["Authorization"])
            val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
            assertEquals(setOf("v", "task", "persona", "tier_floor", "crisis_level", "expert_topics", "context", "messages"), body.keys)
            assertEquals("butler", body["persona"]!!.jsonPrimitive.content)
            val context = body["context"]!!.jsonObject
            assertEquals(setOf("address_form", "restrictions", "flags", "summaries"), context.keys)
            assertEquals("f", context["address_form"]!!.jsonPrimitive.content)
            assertEquals(1, body["messages"]!!.jsonArray.size)
            assertFalse(sent!!.summary)
        } finally {
            server.close()
        }
    }
}
