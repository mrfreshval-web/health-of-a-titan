package com.healthofatitan.app.feature.mentor

import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.network.EdgeConfig
import com.healthofatitan.app.core.network.HotJson
import com.healthofatitan.app.domain.engine.CrisisAssessment
import com.healthofatitan.app.domain.engine.CrisisLevel
import com.healthofatitan.app.domain.engine.CrisisReason
import com.healthofatitan.app.domain.engine.CrisisGuard
import com.healthofatitan.app.domain.mentor.CrisisWindowMode
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

    private fun conversation(
        setup: MentorSetup = MentorSetup(persona = MentorPersona.BRO),
        assess: (String) -> CrisisAssessment = CrisisGuard::assess,
        windowMode: CrisisWindowMode = CrisisWindowMode.CARD_ONCE,
    ) = MentorConversation(
        send = { calls += it; reply },
        safety = { bus },
        setup = { setup },
        memory = memory,
        logCrisis = { logged += it },
        clock = { 1L },
        assess = assess,
        windowMode = windowMode,
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
    fun `M10 the crisis window shows the full card once, then the short line with help`() = runBlocking {
        bus = MentorSafety(crisis = true)
        val c = conversation(MentorSetup(persona = MentorPersona.BUTLER)).apply { start() }
        c.send("привет")
        assertEquals(LineKind.CRISIS, c.ui.value.lines.last().kind)
        assertFalse(c.ui.value.openHelp, "the window does not push «Помощь» open by itself")
        c.send("что по плану?")
        assertEquals(MentorTexts.window(MentorPersona.BUTLER), c.ui.value.lines.last().text)
        assertTrue("как вы" in c.ui.value.lines.last().text)
        assertEquals(1, c.ui.value.lines.count { it.kind == LineKind.CRISIS })
        assertTrue(c.ui.value.helpVisible)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `M1 the model's medical marker shows the emergency line, not the crisis journal`() = runBlocking {
        reply = MentorReply.Emergency
        val c = conversation().apply { start() }
        repeat(3) { c.send("давит в груди при нагрузке") }
        val ui = c.ui.value
        assertEquals(LineKind.EMERGENCY, ui.lines.last().kind)
        assertEquals(MentorTexts.emergency(MentorPersona.BRO), ui.lines.last().text)
        assertTrue("112" in ui.lines.last().text && "911" in ui.lines.last().text)
        assertTrue(ui.helpVisible)
        assertFalse(ui.crisis, "no crisis card, no crisis state")
        assertTrue(logged.isEmpty(), "no entry in the crisis journal, no 72 h window")
        c.close()
        assertTrue(memory.summaries.value.isEmpty(), "no summary of an emergency talk")
    }

    @Test
    fun `M2 level 1 asks Text 1 once, without the model, and a yes leads to level 2`() = runBlocking {
        val c = conversation().apply { start() }
        c.send("сил больше нет, всё валится")
        assertTrue(calls.isEmpty(), "Text 1 is the app's line")
        assertEquals(MentorTexts.safetyQuestion(MentorPersona.BRO), c.ui.value.lines.last().text)
        assertTrue(c.ui.value.helpVisible)
        c.send("да, бывают")
        assertTrue(calls.isEmpty())
        assertEquals(CrisisLevel.PASSIVE, logged.last().level)
        assertEquals(CrisisReason.ANSWER, logged.last().reason)
        assertTrue(c.ui.value.crisis)
        assertTrue(c.ui.value.openHelp)
        assertEquals(LineKind.CRISIS, c.ui.value.lines.last().kind)
    }

    @Test
    fun `M2 a bare no stays on the phone, the model answers the line of level 1 with checked`() = runBlocking {
        val c = conversation().apply { start() }
        c.send("сил больше нет, всё валится")
        c.send("нет")
        val call = calls.single()
        assertEquals(listOf("сил больше нет, всё валится"), call.window.map { it.text })
        assertEquals(1, call.crisisLevel, "Opus and no day limit for the rest of the talk")
        assertTrue("gentle" in call.pack.flags && "checked" in call.pack.flags)
        c.send("сил больше нет, всё валится")
        assertEquals(2, calls.size, "Text 1 is not asked twice")
        assertTrue("checked" in calls.last().pack.flags, "checked stays to the end of the talk")
        assertEquals(1, c.ui.value.lines.count { it.text == MentorTexts.safetyQuestion(MentorPersona.BRO) })
    }

    @Test
    fun `M2 a longer no goes to the model with checked, another topic does not set it`() = runBlocking {
        val c = conversation().apply { start() }
        c.send("сил больше нет, всё валится")
        c.send("нет, просто устал на работе")
        assertTrue(calls.single().window.last().text.endsWith("нет, просто устал на работе"))
        assertTrue("checked" in calls.single().pack.flags)
        val d = conversation().apply { start() }
        d.send("сил больше нет, всё валится")
        d.send("давай про сон")
        assertFalse("checked" in calls.last().pack.flags)
    }

    @Test
    fun `M2 a refusal to answer is logged as a decline`() = runBlocking {
        val c = conversation().apply { start() }
        c.send("сил больше нет, всё валится")
        c.send("не хочу отвечать")
        assertTrue(logged.any { it.level == CrisisLevel.CLARIFY && it.reason == CrisisReason.ANSWER })
        assertFalse(c.ui.value.crisis)
    }

    @Test
    fun `M2 a yes makes the line of level 1 local too`() = runBlocking {
        val c = conversation(windowMode = CrisisWindowMode.GENTLE_TALK).apply { start() }
        c.send("сил больше нет, всё валится")
        c.send("не знаю")
        assertTrue(c.ui.value.crisis)
        bus = MentorSafety(crisis = true)
        c.send("ладно, что по сну?")
        assertTrue(calls.single().window.none { "валится" in it.text || "не знаю" in it.text })
    }

    @Test
    fun `P5 after the model's marker neither line of the last turn goes out again`() = runBlocking {
        val c = conversation(windowMode = CrisisWindowMode.GENTLE_TALK).apply { start() }
        reply = MentorReply.Offline
        c.send("строка икс")
        reply = MentorReply.Crisis(2)
        c.send("строка игрек")
        assertTrue(calls.last().window.any { "икс" in it.text } && calls.last().window.any { "игрек" in it.text })
        reply = MentorReply.Text("Я рядом.", MentorTier.HIGH, softLimit = false)
        bus = MentorSafety(crisis = true)
        c.send("а что по сну?")
        assertTrue(calls.last().window.none { "икс" in it.text || "игрек" in it.text })
        assertEquals(1, calls.last().crisisLevel)
    }

    @Test
    fun `P6 the full card is shown once, also after Sos`() = runBlocking {
        val c = conversation().apply { start() }
        c.send("Не хочу жить")
        bus = MentorSafety(crisis = true)
        c.send("ладно")
        assertEquals(MentorTexts.window(MentorPersona.BRO), c.ui.value.lines.last().text)
        assertEquals(1, c.ui.value.lines.count { it.kind == LineKind.CRISIS })
    }

    @Test
    fun `variant B, level 2 in the window never reaches the model, the next words go without it`() = runBlocking {
        bus = MentorSafety(crisis = true)
        val c = conversation(windowMode = CrisisWindowMode.GENTLE_TALK).apply { start() }
        c.send("Не хочу жить")
        assertTrue(calls.isEmpty())
        c.send("расскажи про сон")
        val call = calls.single()
        assertEquals(1, call.crisisLevel)
        assertTrue("gentle_plus" in call.pack.flags)
        assertTrue(call.window.none { "жить" in it.text })
    }

    @Test
    fun `M2 with T13 no question is asked, but help stays in sight`() = runBlocking {
        val c = conversation(assess = { CrisisAssessment(CrisisLevel.CLARIFY, CrisisReason.META) }).apply { start() }
        c.send("не спрашивай меня про суицид, давай про сон")
        assertEquals(1, calls.single().crisisLevel)
        assertTrue(c.ui.value.helpVisible)
        assertTrue(c.ui.value.lines.none { it.text == MentorTexts.safetyQuestion(MentorPersona.BRO) })
    }

    @Test
    fun `M4 an answer of the model with crisis words is replaced by the card`() = runBlocking {
        reply = MentorReply.Text("Иногда и правда не хочется жить, это нормально.", MentorTier.MID, softLimit = false)
        val c = conversation().apply { start() }
        c.send("как пережить плохой день?")
        assertEquals(LineKind.CRISIS, c.ui.value.lines.last().kind)
        assertTrue(c.ui.value.lines.none { "не хочется жить" in it.text })
    }

    @Test
    fun `the model's crisis marker makes the person's words local`() = runBlocking {
        reply = MentorReply.Crisis(2)
        val c = conversation().apply { start() }
        c.send("мне как-то странно")
        reply = MentorReply.Text("Я рядом.", MentorTier.HIGH, softLimit = false)
        bus = MentorSafety.NORMAL
        c.send("ну ладно, а что по сну?")
        assertTrue(calls.last().window.none { it.text.contains("странно") })
    }

    @Test
    fun `M5 a hidden weight without ED_CARE gets the neutral line, refusal and month limit point to help`() = runBlocking {
        bus = MentorSafety(restrictions = setOf("HIDE_WEIGHT"))
        reply = MentorReply.Text("Твой вес 92 кг — держи курс.", MentorTier.LIGHT, softLimit = false)
        val c = conversation().apply { start() }
        c.send("как я?")
        assertEquals(MentorTexts.foodReplaced(MentorPersona.BRO), c.ui.value.lines.last().text)
        assertFalse("бережный" in c.ui.value.lines.last().text)
        reply = MentorReply.Refused
        c.send("а если так?")
        assertTrue("112" in c.ui.value.lines.last().text)
        reply = MentorReply.Limit(month = true)
        c.send("ещё")
        assertTrue("112" in c.ui.value.lines.last().text && "911" in c.ui.value.lines.last().text)
    }

    @Test
    fun `Vincent speaks on vy`() {
        assertEquals("Здравствуйте. Чем могу быть полезен?", MentorTexts.greeting(MentorPersona.BUTLER))
        assertEquals("Напишите Винсенту", MentorTexts.inputHint(MentorPersona.BUTLER))
        assertEquals("Напиши Сенсею", MentorTexts.inputHint(MentorPersona.SENSEI))
        listOf(
            MentorTexts.emergency(MentorPersona.BUTLER),
            MentorTexts.safetyQuestion(MentorPersona.BUTLER),
            MentorTexts.window(MentorPersona.BUTLER),
            MentorTexts.refused(MentorPersona.BUTLER),
            MentorTexts.offline(MentorPersona.BUTLER),
            MentorTexts.failed(MentorPersona.BUTLER),
        ).forEach { line ->
            assertTrue(Regex("(?iU)(?<!\\p{L})(тебе|тебя|ты|звони|напиши|попробуй|давай)(?!\\p{L})").find(line) == null, line)
        }
        assertTrue("Не врач и не психотерапевт" in MentorTexts.DISCLOSURE)
        assertTrue(MentorTexts.disclosure(MentorPersona.BUTLER).startsWith("Винсент — ИИ-наставник"))
        assertTrue(MentorTexts.notConfigured(MentorPersona.SENSEI).startsWith("Сенсей "))
        assertTrue("попросите кого-нибудь побыть рядом" in MentorTexts.emergency(MentorPersona.BUTLER))
        assertTrue("попроси кого-нибудь побыть рядом" in MentorTexts.emergency(MentorPersona.BRO))
        listOf(MentorPersona.BRO, MentorPersona.BUTLER).forEach { assertFalse("на паузе" in MentorTexts.emergency(it)) }
        assertEquals("ИИ-наставник", MentorTexts.SUBTITLE)
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
        c.send("не, просто тяжёлая неделя")
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
        assertEquals(MentorTexts.edCareReplaced(MentorPersona.BRO), c.ui.value.lines.last().text)
        val pack = calls.single().pack
        assertEquals(listOf("food_gentle"), pack.flags)
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
        assertEquals(MentorTexts.notConfigured(MentorPersona.BRO), c.ui.value.lines.last().text)
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
