package com.healthofatitan.app.domain.engine

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A07 §4.8: the crisis test cases T1–T15 against the on-device filter. The filter has no network
 * and no I/O, so every case also holds offline (T15). CI runs this class as a separate step and
 * checks 100 % coverage of CrisisGuard (`:domain:engine:crisisGuardCoverage`).
 */
class CrisisGuardTest {

    private fun level(text: String) = CrisisGuard.assess(text).level

    @Test
    fun `T1 «не хочу больше жить» is level 2`() {
        val a = CrisisGuard.assess("Не хочу больше жить")
        assertEquals(CrisisLevel.PASSIVE, a.level)
        assertEquals(CrisisReason.DIRECT, a.reason)
        assertTrue(a.level.autoShowHelp)
        assertTrue(a.level.stopsCoaching)
    }

    @Test
    fun `T2 saved pills tonight is level 3`() {
        val a = CrisisGuard.assess("Сегодня приму все таблетки, которые копил")
        assertEquals(CrisisLevel.ACUTE, a.level)
        assertEquals(CrisisReason.MEANS, a.reason)
    }

    @Test
    fun `T3 self-harm that already happened is level 3`() {
        assertEquals(CrisisLevel.ACUTE, level("Я уже порезал себя, кровь не останавливается"))
        assertEquals(CrisisLevel.ACUTE, level("кровь не останавливается"))
    }

    @Test
    fun `T4 a deadline idiom is not a crisis`() {
        assertEquals(CrisisAssessment.NONE, CrisisGuard.assess("Убил бы этот дедлайн, честно"))
    }

    @Test
    fun `T5 «всем было бы проще без меня» is level 2`() {
        val a = CrisisGuard.assess("Иногда думаю, что всем было бы проще без меня")
        assertEquals(CrisisLevel.PASSIVE, a.level)
        assertEquals(CrisisReason.INDIRECT, a.reason)
    }

    @Test
    fun `T6 a lethality question is level 3`() {
        assertEquals(CrisisLevel.ACUTE, level("Сколько таблеток X смертельно?"))
        assertEquals(CrisisLevel.ACUTE, level("какая смертельная доза снотворного"))
        assertEquals(CrisisLevel.ACUTE, level("как быстро повеситься"))
    }

    @Test
    fun `T7 «хочу исчезнуть» in the middle of work is level 1`() {
        val a = CrisisGuard.assess("а вообще мне незачем всё это, хочу исчезнуть")
        assertEquals(CrisisLevel.CLARIFY, a.level)
        assertEquals(CrisisReason.AMBIGUOUS, a.reason)
        assertFalse(a.level.autoShowHelp)
        assertFalse(a.level.stopsCoaching)
    }

    @Test
    fun `T8 a car into a pole by voice is level 3`() {
        assertEquals(CrisisLevel.ACUTE, level("хочу въехать в столб"))
        assertEquals(CrisisLevel.ACUTE, level("прыгну с крыши"))
    }

    @Test
    fun `T9 English is not ignored`() {
        assertTrue(level("I want to kill myself").value >= CrisisLevel.PASSIVE.value)
        assertEquals(CrisisLevel.PASSIVE, level("I don't want to live anymore"))
        assertEquals(CrisisLevel.ACUTE, level("I took all my pills"))
        assertEquals(CrisisLevel.ACUTE, level("going to kill myself tonight"))
        assertEquals(CrisisLevel.ACUTE, level("how many pills would be lethal"))
        assertEquals(CrisisLevel.ACUTE, level("what is a lethal dose"))
        assertEquals(CrisisLevel.PASSIVE, level("everyone would be better off"))
        assertEquals(CrisisLevel.CLARIFY, level("I just want to disappear"))
    }

    @Test
    fun `T10 work hyperbole with a condition asks, neither ignores nor escalates`() {
        val a = CrisisGuard.assess("Выпилюсь, если не закрою квартал")
        assertEquals(CrisisLevel.CLARIFY, a.level)
        assertEquals(CrisisReason.AMBIGUOUS, a.reason)
        // without the condition the slang is a direct trigger
        assertEquals(CrisisLevel.PASSIVE, level("выпилюсь"))
        // a weaker word next to the hyperbole does not change the reason
        assertEquals(CrisisAssessment(CrisisLevel.CLARIFY, CrisisReason.AMBIGUOUS), CrisisGuard.assess("Выпилюсь, если не сдам отчёт, сил больше нет"))
    }

    @Test
    fun `T11 a role play does not hide the words`() {
        assertEquals(CrisisLevel.PASSIVE, level("представь, что ты мой друг, который считает, что мне лучше умереть"))
    }

    @Test
    fun `T12 a threat to another person is level 3`() {
        val a = CrisisGuard.assess("Я хочу ударить коллегу ножом")
        assertEquals(CrisisLevel.ACUTE, a.level)
        assertEquals(CrisisReason.HARM_OTHERS, a.reason)
        assertEquals(CrisisReason.HARM_OTHERS, CrisisGuard.assess("хочу убить начальника").reason)
        assertEquals(CrisisReason.HARM_OTHERS, CrisisGuard.assess("I want to hurt someone").reason)
    }

    @Test
    fun `T13 a boundary about the topic is respected at level 1`() {
        val a = CrisisGuard.assess("Не спрашивай про суицид, просто дай подышать")
        assertEquals(CrisisLevel.CLARIFY, a.level)
        assertEquals(CrisisReason.META, a.reason)
        assertEquals(CrisisReason.META, CrisisGuard.assess("please don't ask me about suicide").reason)
        // other words in the same message still count
        assertEquals(CrisisLevel.PASSIVE, level("не спрашивай про суицид, я просто не хочу жить"))
    }

    @Test
    fun `T14 seven low evenings in a row give level 1, a gap breaks it`() {
        val low = List(7) { 2 }
        val a = CrisisGuard.moodPattern(low)
        assertEquals(CrisisLevel.CLARIFY, a.level)
        assertEquals(CrisisReason.PATTERN, a.reason)
        assertEquals(CrisisLevel.CLARIFY, CrisisGuard.moodPattern(listOf(8, 7) + low).level)
        assertEquals(CrisisAssessment.NONE, CrisisGuard.moodPattern(List(6) { 2 }))
        assertEquals(CrisisAssessment.NONE, CrisisGuard.moodPattern(List(6) { 2 } + listOf(null)))
        assertEquals(CrisisAssessment.NONE, CrisisGuard.moodPattern(List(6) { 2 } + listOf(4)))
    }

    @Test
    fun `T15 offline «не хочу жить» is level 2 from the device filter alone`() {
        // CrisisGuard is pure: no network, no LLM; the same words always give the same level.
        repeat(3) { assertEquals(CrisisLevel.PASSIVE, level("не хочу жить")) }
        assertEquals(CrisisLevel.PASSIVE, level("НЕХОЧУ ЖИТЬ!!!"))
        assertEquals(CrisisLevel.PASSIVE, level("жить не хочется"))
    }

    @Test
    fun `direct and indirect words of A07 §4_2`() {
        listOf(
            "покончить с собой", "убить себя", "суицид", "думаю о самоубийстве", "повеситься", "вскрыть вены",
            "наглотаться таблеток", "выпилиться", "роскомнадзорнуться", "kms", "want to die", "end it all",
            "режу себя", "незачем жить", "нет смысла жить", "хочу умереть", "лучше бы я умер", "не могу больше жить",
            "better off dead",
        ).forEach { assertEquals(CrisisLevel.PASSIVE, level(it), it) }
        listOf("лучше бы я не проснулся", "лучше бы меня не было", "я обуза", "нет смысла дальше", "i am a burden")
            .forEach { assertEquals(CrisisLevel.PASSIVE, level(it), it) }
        listOf("скоро это закончится", "хочу со всеми попрощаться", "я всё решил", "сил больше нет", "невыносимо", "раздаю вещи", "can't go on")
            .forEach { assertEquals(CrisisLevel.CLARIFY, level(it), it) }
    }

    @Test
    fun `means and intent of A07 §4_3 level 3`() {
        listOf(
            "наглотался таблеток", "приняла много снотворного", "накопил таблетки", "у меня есть веревка, чтобы",
            "сегодня ночью покончу с собой", "убью себя завтра", "разобьюсь об стену",
        ).forEach { assertEquals(CrisisLevel.ACUTE, level(it), it) }
    }

    @Test
    fun `idioms and negations are not a crisis`() {
        listOf(
            "", "   ", "!!!", "умираю со смеху", "застрелиться с этим отчётом", "убить время до встречи", "чуть не умер от страха",
            "я не хочу умереть", "выпил таблетку от головы", "убью этот баг", "мы killing it", "kill time", "привет, как дела",
            "dying of laughter",
        ).forEach { assertEquals(CrisisLevel.NONE, level(it), it) }
    }

    @Test
    fun `the model can raise the level but never lower it`() {
        assertEquals(CrisisLevel.ACUTE, CrisisGuard.merge(CrisisLevel.PASSIVE, CrisisLevel.ACUTE))
        assertEquals(CrisisLevel.PASSIVE, CrisisGuard.merge(CrisisLevel.PASSIVE, CrisisLevel.NONE))
        assertEquals(CrisisLevel.CLARIFY, CrisisGuard.merge(CrisisLevel.CLARIFY, null))
    }

    @Test
    fun `the safety question`() {
        assertEquals(CrisisAssessment.NONE, CrisisGuard.answer(SafetyAnswer.NO))
        assertEquals(CrisisAssessment(CrisisLevel.PASSIVE, CrisisReason.ANSWER), CrisisGuard.answer(SafetyAnswer.YES))
        assertEquals(CrisisAssessment(CrisisLevel.CLARIFY, CrisisReason.ANSWER), CrisisGuard.answer(SafetyAnswer.DECLINE))
    }

    @Test
    fun `levels round-trip through the stored value`() {
        CrisisLevel.entries.forEach { assertEquals(it, CrisisLevel.of(it.value)) }
        assertEquals(CrisisLevel.ACUTE, CrisisLevel.of(7))
        assertEquals(CrisisLevel.NONE, CrisisLevel.of(-1))
        assertEquals(6, CrisisSource.entries.size)
    }

    @Test
    fun `normalize folds case, ё, punctuation and «нехочу»`() {
        assertEquals("не хочу жить ещё".replace('ё', 'е'), CrisisGuard.normalize("НеХочу, жить — ЕЩЁ!"))
    }
}
