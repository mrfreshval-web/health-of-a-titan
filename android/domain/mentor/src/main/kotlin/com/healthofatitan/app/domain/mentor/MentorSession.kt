package com.healthofatitan.app.domain.mentor

/**
 * The tiers of the topic router (A62 §2, block 2 §9). The Worker decides the tier of each turn;
 * the phone only keeps the highest one of the conversation, so the lift goes only up (block 5 §4).
 */
enum class MentorTier(val code: String) {
    LIGHT("light"),
    MID("mid"),
    MID_PLUS("midplus"),
    HIGH("high"),
    ;

    fun lift(other: MentorTier?): MentorTier = if (other != null && other.ordinal > ordinal) other else this

    companion object {
        fun fromCode(code: String?): MentorTier? = entries.firstOrNull { it.code == code }
    }
}

enum class Speaker { PERSON, MENTOR }

/**
 * One line of the chat. [local] lines are the app's own words (greeting, crisis card, errors):
 * shown, never sent to the model as if Винс had said them.
 */
data class ChatLine(val speaker: Speaker, val text: String, val at: Long, val local: Boolean = false)

/**
 * Level 1 of the memory (A62 §4 «Сейчас»): the conversation itself, in the process only. Nothing
 * of it is written to the disk; when the conversation ends, a 3–5 line summary may go to the
 * chronicle ([shouldSummarize], A62 §11 п. 2) and the words are gone.
 *
 * The window sent to the model is the tail that fits [maxMessages] and [maxChars], starts with the
 * person and alternates speakers (the Messages API wants that); local lines never go out.
 */
class MentorSession(
    private val maxMessages: Int = 24,
    private val maxChars: Int = 16_000,
) {
    private val _lines = mutableListOf<ChatLine>()
    val lines: List<ChatLine> get() = _lines.toList()

    /** The highest tier this conversation reached: the floor of the next turn. */
    var floor: MentorTier = MentorTier.LIGHT
        private set

    /** CrisisGuard ≥ 1 happened in this conversation: no summary is kept of it (A62 §4 §6). */
    var crisisSeen: Boolean = false
        private set

    fun add(line: ChatLine) {
        _lines += line
    }

    fun lift(tier: MentorTier?) {
        floor = floor.lift(tier)
    }

    fun markCrisis() {
        crisisSeen = true
    }

    /**
     * The model saw a risk in the last turn of the person (`[[SOS]]`), or the person said «да» to
     * Text 1: the last turn of the person — every non-local line after the last answer of the
     * model, since [window] merges them — stays on the screen but never goes to the model again
     * (A62 §4 §6), like a crisis caught on the phone.
     */
    fun localizeLastPerson() {
        for (i in _lines.indices.reversed()) {
            val l = _lines[i]
            if (l.local) continue
            if (l.speaker == Speaker.MENTOR) break
            _lines[i] = l.copy(local = true)
        }
    }

    val personTurns: Int get() = _lines.count { it.speaker == Speaker.PERSON && !it.local }

    /** The exchange the model sees: non-local lines, merged per speaker, trimmed from the start. */
    fun window(): List<ChatLine> {
        val merged = mutableListOf<ChatLine>()
        for (l in _lines.filter { !it.local }) {
            val last = merged.lastOrNull()
            if (last != null && last.speaker == l.speaker) merged[merged.size - 1] = last.copy(text = last.text + "\n" + l.text) else merged += l
        }
        var tail = merged.takeLast(maxMessages)
        while (tail.isNotEmpty() && (tail.first().speaker != Speaker.PERSON || tail.sumOf { it.text.length } > maxChars)) {
            tail = tail.drop(1)
        }
        return tail
    }

    /** A62 §11 п. 2: a summary for a real conversation (≥ [MIN_TURNS] turns) without a crisis. */
    fun shouldSummarize(): Boolean = !crisisSeen && personTurns >= MIN_TURNS && window().lastOrNull()?.speaker == Speaker.MENTOR

    companion object {
        const val MIN_TURNS = 3
    }
}
