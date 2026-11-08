package com.healthofatitan.app.feature.mentor

import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.domain.engine.CrisisAssessment
import com.healthofatitan.app.domain.engine.CrisisGuard
import com.healthofatitan.app.domain.engine.CrisisLevel
import com.healthofatitan.app.domain.engine.CrisisReason
import com.healthofatitan.app.domain.engine.SafetyAnswer
import com.healthofatitan.app.domain.mentor.ChatLine
import com.healthofatitan.app.domain.mentor.ContextPack
import com.healthofatitan.app.domain.mentor.ContextPackBuilder
import com.healthofatitan.app.domain.mentor.CrisisWindowMode
import com.healthofatitan.app.domain.mentor.EdCareText
import com.healthofatitan.app.domain.mentor.GuardReason
import com.healthofatitan.app.domain.mentor.GuardResult
import com.healthofatitan.app.domain.mentor.InputDecision
import com.healthofatitan.app.domain.mentor.MentorConsents
import com.healthofatitan.app.domain.mentor.MentorGate
import com.healthofatitan.app.domain.mentor.MentorPolicy
import com.healthofatitan.app.domain.mentor.MentorSession
import com.healthofatitan.app.domain.mentor.PackInputs
import com.healthofatitan.app.domain.mentor.ResponseGuard
import com.healthofatitan.app.domain.mentor.Speaker
import com.healthofatitan.app.feature.mentor.data.MentorCall
import com.healthofatitan.app.feature.mentor.data.MentorMemory
import com.healthofatitan.app.feature.mentor.data.MentorReply
import com.healthofatitan.app.feature.mentor.data.TalkSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the safety bus says to Винс right now (consumer MENTOR, A60 §3.2). */
data class MentorSafety(
    val restrictions: Set<String> = emptySet(),
    val edCare: Boolean = false,
    /** CrisisGuard ≥ 2 within its window: no coaching (A07 §4.3). */
    val crisis: Boolean = false,
    val gentle: Boolean = false,
) {
    companion object {
        val NORMAL = MentorSafety()
    }
}

/** What the conversation reads from the settings on each turn. */
data class MentorSetup(
    val persona: MentorPersona = MentorPersona.DEFAULT,
    val addressForm: String = "n",
    /**
     * «Уровень эксперта по темам» (A62 §9.1): training on the high tier. On by default in this
     * build — the founder's setting (D-056 п. 3); the switch in settings comes with the tests.
     */
    val expertTraining: Boolean = true,
    val consents: MentorConsents = MentorConsents(),
)

/** [EMERGENCY] — the medical line of `[[SOS_MED]]` with the «Помощь» button (М1). */
enum class LineKind { TALK, CRISIS, EMERGENCY, NOTICE }

data class UiLine(val speaker: Speaker, val text: String, val kind: LineKind = LineKind.TALK)

data class MentorUi(
    val persona: MentorPersona = MentorPersona.DEFAULT,
    val lines: List<UiLine> = emptyList(),
    val sending: Boolean = false,
    /** The crisis card is in the chat: «Помощь» stays one tap away. */
    val crisis: Boolean = false,
    /** Open «Помощь» now (CrisisGuard ≥ 2 or the model's marker); the screen calls [MentorConversation.helpOpened]. */
    val openHelp: Boolean = false,
    /**
     * «Помощь» as a chip above the input: after CrisisGuard ≥ 1 (also T13, where no question is
     * asked), inside the crisis window and after an emergency (М2, М10).
     */
    val helpVisible: Boolean = false,
)

/**
 * One conversation with Винс (A62): every message of the person passes CrisisGuard on the phone
 * (A07 §4.1 layer 1) and is logged to the safety bus by level only.
 *
 * - Level ≥ 2 → no model, the crisis card with 112 and 911 and «Помощь».
 * - Level 1 → Text 1 of A07 §4.3 once per conversation (a local line, not the model); a short «да»
 *   to it → `SafetyAnswer.YES` → level 2. With T13 (META) no question, but «Помощь» is in sight.
 * - Inside the crisis window: [MentorPolicy.CRISIS_WINDOW_MODE] (the card once, then a short line;
 *   or variant Б — gentle talk on Opus).
 * - The answer passes the model's markers (`[[SOS]]` may only raise, `[[SOS_MED]]` → emergency),
 *   CrisisGuard on the model's words (М4) and [ResponseGuard] (no food under hiding, no doses,
 *   no diagnoses) before the person sees it.
 *
 * Pure of Android: the ViewModel passes the network, the bus, the journal and the settings in.
 */
class MentorConversation(
    private val send: suspend (MentorCall) -> MentorReply,
    private val safety: suspend () -> MentorSafety,
    private val setup: suspend () -> MentorSetup,
    private val memory: MentorMemory,
    private val logCrisis: suspend (CrisisAssessment) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val assess: (String) -> CrisisAssessment = CrisisGuard::assess,
    private val windowMode: CrisisWindowMode = MentorPolicy.CRISIS_WINDOW_MODE,
) {
    private val mutex = Mutex()
    private var session = MentorSession()
    private var persona = MentorPersona.DEFAULT
    private var softNoticeShown = false

    /** Text 1 was asked in this conversation (once per conversation, М2). */
    private var safetyAsked = false

    /** The next message is the answer to Text 1. */
    private var awaitingAnswer = false

    /** CrisisGuard 1 happened in this conversation: the gentle flag goes with every turn. */
    private var sessionGentle = false

    /** The highest CrisisGuard level (0–1) sent in this conversation: Opus and no day limit stay. */
    private var sessionLevel = 0

    /** The full crisis card was shown in this conversation (М10, П6): the window then says less. */
    private var windowCardShown = false

    /** Text 1 was answered «нет»: the pack carries `checked` to the end of the conversation (П4). */
    private var checked = false

    private val state = MutableStateFlow(MentorUi())
    val ui: StateFlow<MentorUi> = state.asStateFlow()

    /** A new conversation: a greeting in the character's manner, said by the app (no model). */
    suspend fun start() = mutex.withLock {
        persona = setup().persona
        session = MentorSession()
        softNoticeShown = false
        safetyAsked = false
        awaitingAnswer = false
        sessionGentle = false
        sessionLevel = 0
        windowCardShown = false
        checked = false
        local(Speaker.MENTOR, MentorTexts.greeting(persona))
        state.value = MentorUi(persona = persona, lines = listOf(UiLine(Speaker.MENTOR, MentorTexts.greeting(persona))))
    }

    fun helpOpened() = state.update { it.copy(openHelp = false) }

    suspend fun send(raw: String) {
        val text = raw.trim().take(MAX_INPUT)
        if (text.isEmpty() || state.value.sending) return
        mutex.withLock { turn(text) }
    }

    private suspend fun turn(text: String) {
        val assessment = assess(text)
        val s = setup()
        persona = s.persona
        // A bare «нет» to Text 1: shown, not sent; the model answers the line of level 1 (П4).
        var bareNo = false
        if (awaitingAnswer) {
            awaitingAnswer = false
            if (assessment.level < CrisisLevel.PASSIVE && MentorGate.consent(text)) {
                // «Да» (or doubt) to Text 1: level 2 (A07 §4.3). Neither the answer nor the line of
                // level 1 before it goes to the model again — together they are a disclosure.
                val yes = CrisisGuard.answer(SafetyAnswer.YES)
                logCrisis(yes)
                session.markCrisis()
                session.localizeLastPerson()
                local(Speaker.PERSON, text)
                show(UiLine(Speaker.PERSON, text))
                crisisCard(open = yes.level.autoShowHelp)
                return
            }
            if (MentorGate.declines(text)) {
                // «Не хочу отвечать»: level 1 for the metrics of block 9; the talk goes on, gentle.
                logCrisis(CrisisGuard.answer(SafetyAnswer.DECLINE))
            } else if (MentorGate.saysNo(text)) {
                checked = true
                bareNo = MentorGate.denial(text)
            }
            // «Нет» or another topic: the talk goes on, gentle (sessionGentle is already on).
        }
        if (assessment.level != CrisisLevel.NONE) logCrisis(assessment)
        val bus = safety()
        when (val decision = MentorGate.before(text, { assessment.level.value }, bus.crisis, windowMode)) {
            is InputDecision.Sos -> {
                // The words of a crisis never go to the model, now or later (A62 §4 §6).
                session.markCrisis()
                local(Speaker.PERSON, text)
                show(UiLine(Speaker.PERSON, text))
                crisisCard(open = assessment.level.autoShowHelp)
            }
            InputDecision.Window -> {
                // М10 (temporary, variant А): the window of a recent crisis — no model, no advice.
                session.markCrisis()
                local(Speaker.PERSON, text)
                show(UiLine(Speaker.PERSON, text))
                if (!windowCardShown) {
                    crisisCard(open = false)
                } else {
                    notice(MentorTexts.window(persona), talk = true)
                }
                state.update { it.copy(helpVisible = true) }
            }
            is InputDecision.Send -> {
                if (decision.crisisLevel > 0) session.markCrisis()
                if (decision.care) state.update { it.copy(helpVisible = true) }
                sessionLevel = maxOf(sessionLevel, decision.crisisLevel)
                if (assessment.level == CrisisLevel.CLARIFY) {
                    sessionGentle = true
                    state.update { it.copy(helpVisible = true) }
                    if (assessment.reason != CrisisReason.META && !safetyAsked && !decision.care) {
                        // М2: Text 1, said by the app; this turn does not go to the model.
                        safetyAsked = true
                        awaitingAnswer = true
                        session.add(ChatLine(Speaker.PERSON, text, clock()))
                        show(UiLine(Speaker.PERSON, text))
                        notice(MentorTexts.safetyQuestion(persona), talk = true)
                        return
                    }
                }
                session.add(ChatLine(Speaker.PERSON, text, clock(), local = bareNo))
                show(UiLine(Speaker.PERSON, text))
                state.update { it.copy(sending = true) }
                val pack = pack(s, bus, decision.care)
                val reply = try {
                    send(MentorCall(s.persona.id, session.window(), pack, session.floor, sessionLevel, s.expertTraining))
                } finally {
                    state.update { it.copy(sending = false) }
                }
                answer(reply, bus, sessionLevel)
            }
        }
    }

    private suspend fun answer(reply: MentorReply, bus: MentorSafety, deviceLevel: Int) {
        when (reply) {
            is MentorReply.Text -> {
                session.lift(reply.tier)
                when (val g = ResponseGuard.check(reply.text, bus.restrictions, bus.edCare)) {
                    is GuardResult.Pass -> {
                        // М4: the model's own words pass CrisisGuard too; ≥ 2 → the card instead.
                        if (assess(g.text).level >= CrisisLevel.PASSIVE) {
                            session.markCrisis()
                            crisisCard(open = false)
                        } else {
                            session.add(ChatLine(Speaker.MENTOR, g.text, clock()))
                            show(UiLine(Speaker.MENTOR, g.text))
                        }
                    }
                    is GuardResult.Replaced -> notice(
                        when (g.reason) {
                            GuardReason.ED_CARE -> MentorTexts.edCareReplaced(persona)
                            GuardReason.FOOD_HIDDEN -> MentorTexts.foodReplaced(persona)
                            GuardReason.DOSE -> MentorTexts.DOSE_REPLACED
                            GuardReason.DIAGNOSIS -> MentorTexts.DIAGNOSIS_REPLACED
                        },
                        talk = true,
                    )
                }
                if (reply.softLimit && !softNoticeShown) {
                    softNoticeShown = true
                    notice(MentorTexts.SOFT_LIMIT)
                }
            }
            is MentorReply.Crisis -> {
                val level = MentorGate.merge(deviceLevel, reply.level)
                logCrisis(CrisisAssessment(CrisisLevel.of(level)))
                session.markCrisis()
                session.localizeLastPerson()
                crisisCard(open = CrisisLevel.of(level).autoShowHelp)
            }
            MentorReply.Emergency -> {
                // М1: the body, not the mood — no crisis journal, no 72 h window; no summary.
                session.markCrisis()
                local(Speaker.MENTOR, MentorTexts.emergency(persona))
                show(UiLine(Speaker.MENTOR, MentorTexts.emergency(persona), LineKind.EMERGENCY))
                state.update { it.copy(helpVisible = true) }
            }
            MentorReply.Refused -> notice(MentorTexts.refused(persona), talk = true)
            is MentorReply.NotConfigured -> notice(MentorTexts.notConfigured(persona))
            MentorReply.NeedsLogin -> notice(MentorTexts.needsLogin(persona))
            is MentorReply.Limit -> notice(if (reply.month) MentorTexts.MONTH_LIMIT else MentorTexts.DAY_LIMIT)
            MentorReply.Busy -> notice(MentorTexts.busy(persona))
            MentorReply.Offline -> notice(MentorTexts.offline(persona))
            is MentorReply.Failed -> notice(MentorTexts.failed(persona))
        }
    }

    /**
     * The end of a conversation: a 3–5 line summary for the chronicle on the phone (A62 §11 п. 2),
     * never after a crisis, never with calories or weight while they are hidden.
     */
    suspend fun close() = mutex.withLock {
        if (!session.shouldSummarize()) return@withLock
        val s = setup()
        val bus = safety()
        val reply = send(MentorCall(s.persona.id, session.window(), pack(s, bus, care = false), session.floor, 0, s.expertTraining, summary = true))
        val text = (reply as? MentorReply.Text)?.text?.trim().orEmpty()
        val hidden = (bus.edCare || EdCareText.HIDING.any { it in bus.restrictions }) && EdCareText.mentionsHidden(text)
        if (text.isNotEmpty() && !hidden && ResponseGuard.check(text, emptySet(), edCare = false) is GuardResult.Pass) {
            memory.add(TalkSummary(clock(), text.take(ContextPack.MAX_SUMMARY_CHARS)))
        }
        session = MentorSession()
    }

    private fun pack(s: MentorSetup, bus: MentorSafety, care: Boolean): ContextPack = ContextPackBuilder.build(
        PackInputs(
            addressForm = s.addressForm,
            restrictions = bus.restrictions,
            edCare = bus.edCare,
            // gentle_plus → NO_COACHING + SOBER: only variant Б sends messages of the window out.
            recentCrisis = care || bus.crisis,
            gentle = bus.gentle || sessionGentle,
            checked = checked,
            summaries = memory.summaries.value.map { it.text },
            consents = s.consents,
        ),
    )

    private fun crisisCard(open: Boolean) {
        windowCardShown = true
        show(UiLine(Speaker.MENTOR, MentorTexts.CRISIS, LineKind.CRISIS))
        state.update { it.copy(crisis = true, helpVisible = true, openHelp = it.openHelp || open) }
    }

    /** A line of the app; [talk] lines read as Винс's answer, the others as a quiet notice. */
    private fun notice(text: String, talk: Boolean = false) {
        local(Speaker.MENTOR, text)
        show(UiLine(Speaker.MENTOR, text, if (talk) LineKind.TALK else LineKind.NOTICE))
    }

    private fun local(speaker: Speaker, text: String) = session.add(ChatLine(speaker, text, clock(), local = true))

    private fun show(line: UiLine) = state.update { it.copy(lines = it.lines + line) }

    companion object {
        const val MAX_INPUT = 2000
    }
}
