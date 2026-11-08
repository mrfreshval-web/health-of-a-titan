package com.healthofatitan.app.feature.mentor

import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.domain.engine.CrisisAssessment
import com.healthofatitan.app.domain.engine.CrisisGuard
import com.healthofatitan.app.domain.engine.CrisisLevel
import com.healthofatitan.app.domain.mentor.ChatLine
import com.healthofatitan.app.domain.mentor.ContextPack
import com.healthofatitan.app.domain.mentor.ContextPackBuilder
import com.healthofatitan.app.domain.mentor.EdCareText
import com.healthofatitan.app.domain.mentor.GuardReason
import com.healthofatitan.app.domain.mentor.GuardResult
import com.healthofatitan.app.domain.mentor.InputDecision
import com.healthofatitan.app.domain.mentor.MentorConsents
import com.healthofatitan.app.domain.mentor.MentorGate
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

enum class LineKind { TALK, CRISIS, NOTICE }

data class UiLine(val speaker: Speaker, val text: String, val kind: LineKind = LineKind.TALK)

data class MentorUi(
    val persona: MentorPersona = MentorPersona.DEFAULT,
    val lines: List<UiLine> = emptyList(),
    val sending: Boolean = false,
    /** The crisis card is in the chat: «Помощь» stays one tap away. */
    val crisis: Boolean = false,
    /** Open «Помощь» now (CrisisGuard ≥ 2 or the model's marker); the screen calls [MentorConversation.helpOpened]. */
    val openHelp: Boolean = false,
)

/**
 * One conversation with Винс (A62): every message of the person passes CrisisGuard on the phone
 * (A07 §4.1 layer 1) and is logged to the safety bus by level only; level ≥ 2 or an open crisis
 * window → no model, the crisis line with 112 and 911 and «Помощь». The answer passes the model's
 * crisis marker (layer 2, may only raise) and [ResponseGuard] (no calories under `ED_CARE`, no
 * doses, no diagnoses) before the person sees it.
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
) {
    private val mutex = Mutex()
    private var session = MentorSession()
    private var softNoticeShown = false
    private val state = MutableStateFlow(MentorUi())
    val ui: StateFlow<MentorUi> = state.asStateFlow()

    /** A new conversation: a greeting in the character's manner, said by the app (no model). */
    suspend fun start() = mutex.withLock {
        val persona = setup().persona
        session = MentorSession()
        softNoticeShown = false
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
        if (assessment.level != CrisisLevel.NONE) logCrisis(assessment)
        val bus = safety()
        when (val decision = MentorGate.before(text, { assessment.level.value }, bus.crisis)) {
            is InputDecision.Sos -> {
                // The words of a crisis never go to the model, now or later (A62 §4 §6).
                session.markCrisis()
                local(Speaker.PERSON, text)
                show(UiLine(Speaker.PERSON, text))
                crisisCard(open = assessment.level.autoShowHelp)
            }
            is InputDecision.Send -> {
                if (decision.crisisLevel > 0) session.markCrisis()
                session.add(ChatLine(Speaker.PERSON, text, clock()))
                show(UiLine(Speaker.PERSON, text))
                state.update { it.copy(sending = true) }
                val s = setup()
                val pack = pack(s, bus)
                val reply = try {
                    send(MentorCall(s.persona.id, session.window(), pack, session.floor, decision.crisisLevel, s.expertTraining))
                } finally {
                    state.update { it.copy(sending = false) }
                }
                answer(reply, bus, decision.crisisLevel)
            }
        }
    }

    private suspend fun answer(reply: MentorReply, bus: MentorSafety, deviceLevel: Int) {
        when (reply) {
            is MentorReply.Text -> {
                session.lift(reply.tier)
                when (val g = ResponseGuard.check(reply.text, bus.restrictions, bus.edCare)) {
                    is GuardResult.Pass -> {
                        session.add(ChatLine(Speaker.MENTOR, g.text, clock()))
                        show(UiLine(Speaker.MENTOR, g.text))
                    }
                    is GuardResult.Replaced -> notice(
                        when (g.reason) {
                            GuardReason.ED_CARE -> MentorTexts.ED_CARE_REPLACED
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
                crisisCard(open = CrisisLevel.of(level).autoShowHelp)
            }
            MentorReply.Refused -> notice(MentorTexts.REFUSED, talk = true)
            is MentorReply.NotConfigured -> notice(MentorTexts.NOT_CONFIGURED)
            MentorReply.NeedsLogin -> notice(MentorTexts.NEEDS_LOGIN)
            is MentorReply.Limit -> notice(if (reply.month) MentorTexts.MONTH_LIMIT else MentorTexts.DAY_LIMIT)
            MentorReply.Busy -> notice(MentorTexts.BUSY)
            MentorReply.Offline -> notice(MentorTexts.OFFLINE)
            is MentorReply.Failed -> notice(MentorTexts.FAILED)
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
        val reply = send(MentorCall(s.persona.id, session.window(), pack(s, bus), session.floor, 0, s.expertTraining, summary = true))
        val text = (reply as? MentorReply.Text)?.text?.trim().orEmpty()
        val hidden = (bus.edCare || EdCareText.HIDING.any { it in bus.restrictions }) && EdCareText.mentionsHidden(text)
        if (text.isNotEmpty() && !hidden && ResponseGuard.check(text, emptySet(), edCare = false) is GuardResult.Pass) {
            memory.add(TalkSummary(clock(), text.take(ContextPack.MAX_SUMMARY_CHARS)))
        }
        session = MentorSession()
    }

    private fun pack(s: MentorSetup, bus: MentorSafety): ContextPack = ContextPackBuilder.build(
        PackInputs(
            addressForm = s.addressForm,
            restrictions = bus.restrictions,
            edCare = bus.edCare,
            recentCrisis = bus.crisis,
            gentle = bus.gentle,
            summaries = memory.summaries.value.map { it.text },
            consents = s.consents,
        ),
    )

    private fun crisisCard(open: Boolean) {
        show(UiLine(Speaker.MENTOR, MentorTexts.CRISIS, LineKind.CRISIS))
        state.update { it.copy(crisis = true, openHelp = it.openHelp || open) }
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
