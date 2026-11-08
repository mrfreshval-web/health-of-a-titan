package com.healthofatitan.app.domain.mentor

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MentorRulesTest {

    // --- ContextPack (A62 §4): what goes to the model and what never does --------------------

    @Test
    fun `the pack has no field for photos, WHOOP numbers or the profile of specifics`() {
        val names = ContextPack::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name.lowercase() }
        assertEquals(setOf("addressform", "restrictions", "flags", "summaries", "about"), names.toSet())
        listOf("photo", "image", "whoop", "recovery", "hrv", "weight", "calorie", "reason", "crisis").forEach { banned ->
            assertTrue(names.none { banned in it }, banned)
        }
    }

    @Test
    fun `about me goes only with the consent about_mentor`() {
        val inputs = PackInputs(about = "Работаю ночами, двое детей.")
        assertNull(ContextPackBuilder.build(inputs).about)
        assertEquals("Работаю ночами, двое детей.", ContextPackBuilder.build(inputs.copy(consents = MentorConsents(aboutMentor = true))).about)
    }

    @Test
    fun `under ED_CARE no line about calories or weight reaches the model`() {
        val inputs = PackInputs(
            edCare = true,
            restrictions = setOf("HIDE_CALORIES", "HIDE_WEIGHT", "HIDE_FOOD_TALK"),
            summaries = listOf("Обсудили сон, договорились ложиться до 23:30.", "Говорили о калориях на ужин.", "Хочет сбросить 5 кг к лету."),
            about = "Мой вес 92 кг, хочу похудеть.",
            consents = MentorConsents(aboutMentor = true),
        )
        val pack = ContextPackBuilder.build(inputs)
        assertEquals(listOf("Обсудили сон, договорились ложиться до 23:30."), pack.summaries)
        assertNull(pack.about)
        assertEquals(listOf("food_gentle"), pack.flags)
        assertEquals(listOf("HIDE_CALORIES", "HIDE_FOOD_TALK", "HIDE_WEIGHT"), pack.restrictions)
    }

    @Test
    fun `the crisis goes as a flag only and the pack keeps at most three summaries`() {
        val pack = ContextPackBuilder.build(PackInputs(recentCrisis = true, summaries = (1..5).map { "Разговор $it." }, addressForm = "x"))
        assertEquals(listOf("gentle_plus"), pack.flags)
        assertEquals(listOf("Разговор 3.", "Разговор 4.", "Разговор 5."), pack.summaries)
        assertEquals("n", pack.addressForm)
    }

    // --- The gate before the model ---------------------------------------------------------------

    @Test
    fun `level 2 or 3 never reaches the model, an active crisis window stops coaching`() {
        assertEquals(InputDecision.Sos(3), MentorGate.before("…", { 3 }, crisisActive = false))
        assertEquals(InputDecision.Sos(2), MentorGate.before("…", { 2 }, crisisActive = false))
        assertEquals(InputDecision.Window, MentorGate.before("привет", { 0 }, crisisActive = true, mode = CrisisWindowMode.CARD_ONCE))
        assertEquals(InputDecision.Send(1), MentorGate.before("сил нет", { 1 }, crisisActive = false))
        assertEquals(InputDecision.Send(0), MentorGate.before("привет", { 0 }, crisisActive = false))
    }

    @Test
    fun `the crisis window is gentle talk by the founder's choice (D-063), level 2 still never goes out`() {
        assertEquals(CrisisWindowMode.GENTLE_TALK, MentorPolicy.CRISIS_WINDOW_MODE)
        val b = CrisisWindowMode.GENTLE_TALK
        assertEquals(InputDecision.Send(1, care = true), MentorGate.before("привет", { 0 }, crisisActive = true, mode = b))
        assertEquals(InputDecision.Sos(2), MentorGate.before("…", { 2 }, crisisActive = true, mode = b))
    }

    @Test
    fun `a yes or a doubt to the safety question is consent, a clear no or a new topic is not`() {
        listOf(
            "да", "Да.", "бывают", "да, но не часто", "не часто", "нет, но иногда бывает", "не знаю", "наверное",
            "может быть", "не уверена", "ну есть немного", "иногда да", "да бывают иногда когда ночью не сплю и думаю", "yes",
        ).forEach { assertTrue(MentorGate.consent(it), it) }
        listOf(
            "нет", "нет, просто устал", "не бывает", "нет, никогда", "да нет, всё нормально", "не, всё ок", "давай про сон",
            "нет, просто начальник достал и я не высыпаюсь уже неделю", "",
        ).forEach { assertFalse(MentorGate.consent(it), it) }
    }

    @Test
    fun `a no to the safety question is checked, a bare no stays on the phone, a refusal is a decline`() {
        listOf("нет", "Нет.", "нет, никогда", "да нет, всё нормально", "нет, просто устал на работе").forEach { assertTrue(MentorGate.saysNo(it), it) }
        listOf("давай про сон", "да", "не знаю").forEach { assertFalse(MentorGate.saysNo(it), it) }
        listOf("нет", "нет, никогда", "не, всё ок").forEach { assertTrue(MentorGate.denial(it), it) }
        listOf("нет, просто устал на работе", "давай про сон").forEach { assertFalse(MentorGate.denial(it), it) }
        listOf("не хочу отвечать", "Не твоё дело").forEach { assertTrue(MentorGate.declines(it), it) }
        assertFalse(MentorGate.declines("нет"))
        assertFalse(MentorGate.saysNo("не хочу отвечать"))
    }

    @Test
    fun `checked goes to the pack as a neutral flag`() {
        assertEquals(listOf("gentle", "checked"), ContextPackBuilder.build(PackInputs(gentle = true, checked = true)).flags)
    }

    @Test
    fun `the model may only raise the level`() {
        assertEquals(2, MentorGate.merge(0, 2))
        assertEquals(3, MentorGate.merge(3, 2))
        assertEquals(1, MentorGate.merge(1, null))
    }

    // --- ResponseGuard -------------------------------------------------------------------------

    @Test
    fun `ED_CARE replaces an answer about calories or weight`() {
        val r = ResponseGuard.check("На ужин держи 600 ккал.", setOf("HIDE_CALORIES"), edCare = true)
        assertEquals(GuardResult.Replaced(GuardReason.ED_CARE), r)
        assertEquals(GuardResult.Replaced(GuardReason.ED_CARE), ResponseGuard.check("Чтобы похудеть, ешь меньше.", emptySet(), edCare = true))
        assertIs<GuardResult.Pass>(ResponseGuard.check("На ужин держи 600 ккал.", emptySet(), edCare = false))
        assertIs<GuardResult.Pass>(ResponseGuard.check("Сон — главное сегодня. Ложись до 23:30.", setOf("HIDE_CALORIES"), edCare = true))
    }

    @Test
    fun `a hiding of the bus without ED_CARE is FOOD_HIDDEN, not the care mode`() {
        assertEquals(GuardResult.Replaced(GuardReason.FOOD_HIDDEN), ResponseGuard.check("Твой вес 92 кг — это норм.", setOf("HIDE_WEIGHT"), edCare = false))
    }

    @Test
    fun `doses in units, pills, and ml or grams of a supplement are replaced, grams of food are not`() {
        listOf(
            "Инсулин 10 ЕД перед ужином.",
            "Коли 8 ед. утром.",
            "Выпей 2 таблетки.",
            "По одной капсуле на ночь.",
            "Полтаблетки перед сном.",
            "Креатин 5 г в день.",
            "Омега-3: 2 г в день.",
            "Сироп от кашля 10 мл три раза.",
            "Мелатонин 3 мг.",
            "10 единиц инсулина перед едой",
            "по 5 капель на ночь",
            "20 капель валерьянки",
            "1 ампулу",
            "Креатин моногидрат: по 3–5 г в день",
            "Омега-3 — 2 г в сутки",
            "5 г креатина",
            "10 мл сиропа",
            "две таблетки ибупрофена",
            "2 столовые ложки сиропа",
            "пять таблеток",
        ).forEach { assertEquals(GuardResult.Replaced(GuardReason.DOSE), ResponseGuard.check(it, emptySet(), false), it) }
        listOf(
            "150 г курицы на обед и рис.",
            "Выпей 500 мл воды после тренировки.",
            "Две еды до тренировки, отдых 90 с.",
            "Присед 3×8 по 60 кг.",
            "Съешь 150 г творога — там есть и белок, и магний.",
            "100 г лосося дают омега-3 и витамин D.",
            "500 мл воды без добавок, можно пару капель лимона.",
            "1,5 л воды в день.",
            "Один пакетик чая с мятой перед сном.",
            "3 единицы алкоголя в неделю — много",
            "30 г протеина после тренировки",
            "Две капсулы кофе до обеда — и хватит.",
        ).forEach { assertIs<GuardResult.Pass>(ResponseGuard.check(it, emptySet(), false), it) }
    }

    @Test
    fun `no doses and no diagnoses`() {
        assertEquals(GuardResult.Replaced(GuardReason.DOSE), ResponseGuard.check("Магний 400 мг перед сном.", emptySet(), false))
        assertEquals(GuardResult.Replaced(GuardReason.DOSE), ResponseGuard.check("Vitamin D 2000 IU", emptySet(), false))
        assertEquals(GuardResult.Replaced(GuardReason.DIAGNOSIS), ResponseGuard.check("Похоже, у тебя депрессия.", emptySet(), false))
        assertIs<GuardResult.Pass>(ResponseGuard.check("Присед 4×6, 80 кг, отдых 150 с.", emptySet(), false))
        assertIs<GuardResult.Pass>(ResponseGuard.check("Это стоит показать врачу на этой неделе.", emptySet(), false))
    }

    // --- Level 1: the session ----------------------------------------------------------------

    @Test
    fun `the window starts with the person, merges repeats and drops local lines`() {
        val s = MentorSession(maxMessages = 3)
        s.add(ChatLine(Speaker.MENTOR, "Привет! Я Винс.", 0, local = true))
        s.add(ChatLine(Speaker.PERSON, "а", 1))
        s.add(ChatLine(Speaker.MENTOR, "б", 2))
        s.add(ChatLine(Speaker.PERSON, "в", 3))
        s.add(ChatLine(Speaker.PERSON, "г", 4))
        val w = s.window()
        assertEquals(Speaker.PERSON, w.first().speaker)
        assertEquals(listOf("а", "б", "в\nг"), w.map { it.text })
        val short = MentorSession(maxMessages = 2).apply {
            add(ChatLine(Speaker.PERSON, "а", 1)); add(ChatLine(Speaker.MENTOR, "б", 2)); add(ChatLine(Speaker.PERSON, "в", 3))
        }
        assertEquals(listOf("в"), short.window().map { it.text })
    }

    @Test
    fun `the tier floor goes only up and a crisis conversation is never summarised`() {
        val s = MentorSession()
        s.lift(MentorTier.HIGH)
        s.lift(MentorTier.LIGHT)
        assertEquals(MentorTier.HIGH, s.floor)
        repeat(3) {
            s.add(ChatLine(Speaker.PERSON, "q$it", it.toLong()))
            s.add(ChatLine(Speaker.MENTOR, "a$it", it.toLong()))
        }
        assertTrue(s.shouldSummarize())
        s.markCrisis()
        assertFalse(s.shouldSummarize())
    }

    @Test
    fun `after the model's crisis marker the whole last turn of the person never goes out again`() {
        val s = MentorSession()
        s.add(ChatLine(Speaker.PERSON, "а", 1))
        s.add(ChatLine(Speaker.MENTOR, "б", 2))
        s.add(ChatLine(Speaker.PERSON, "X", 3))
        s.add(ChatLine(Speaker.MENTOR, "Нет связи", 4, local = true))
        s.add(ChatLine(Speaker.PERSON, "Y", 5))
        s.localizeLastPerson()
        assertEquals(listOf("а", "б"), s.window().map { it.text })
        assertEquals(5, s.lines.size)
    }
}
