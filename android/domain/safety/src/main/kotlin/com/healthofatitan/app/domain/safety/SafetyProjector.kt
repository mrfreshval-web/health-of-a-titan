package com.healthofatitan.app.domain.safety

import com.healthofatitan.app.core.model.safety.SafetyEventKinds
import java.time.Duration

/**
 * A row of the event journal (A58 §8 «журнал и проекторы») as the projector reads it. The
 * journal keeps codes, never words (A07 §4.7).
 */
data class JournalEvent(val id: Long, val kind: String, val payload: String?, val at: Long, val source: String? = null)

/** The kinds of safety events in the journal. Everything the bus knows starts with [PREFIX]. */
object SafetyEvents {
    const val PREFIX = SafetyEventKinds.PREFIX

    /** CrisisGuard set a level (payload: 1–3). Written by the crisis log next to `safety_event`. */
    const val CRISIS = SafetyEventKinds.CRISIS

    /** `ED_CARE` began (payload: [EdCareReason.code]). */
    const val ED_CARE_ENTER = SafetyEventKinds.ED_CARE_ENTER

    /** The person chose to leave `ED_CARE` (no payload). Before the cooldown ends it changes nothing. */
    const val ED_CARE_EXIT = SafetyEventKinds.ED_CARE_EXIT

    /** The person chose a step of the return (payload: 1–3). */
    const val ED_CARE_STEP = SafetyEventKinds.ED_CARE_STEP

    /** The person took one thing back on step 3 (payload: [EdCareReturn.code]). */
    const val ED_CARE_RETURN = SafetyEventKinds.ED_CARE_RETURN

    val KNOWN: Set<String> = setOf(CRISIS, ED_CARE_ENTER, ED_CARE_EXIT, ED_CARE_STEP, ED_CARE_RETURN)
}

/**
 * The projector of the bus: the journal in, [SafetyState] out. Pure and replayable — the same
 * events at the same moment give the same state, so the state is never stored, only the events.
 */
object SafetyProjector {
    /** The crisis window: «Помощь» first and no Sunday push for 72 h after level ≥ 2 (A07, `CRISIS_WINDOW`). */
    val CRISIS_WINDOW: Duration = Duration.ofHours(72)

    fun project(events: List<JournalEvent>, nowMs: Long): SafetyState {
        var unknown = false
        var crisis: ActiveFlag? = null
        var ed = EdCareState.NONE
        events.filter { it.kind.startsWith(SafetyEvents.PREFIX) && it.at <= nowMs }
            .sortedWith(compareBy({ it.at }, { it.id }))
            .forEach { e ->
                when (e.kind) {
                    SafetyEvents.CRISIS -> crisis = strictest(crisis, crisisFlag(e, nowMs))
                    SafetyEvents.ED_CARE_ENTER -> {
                        val reason = EdCareReason.parse(e.payload)
                        // an entry this build cannot read: the strictest reason, and «forbid» everywhere
                        if (reason == null) unknown = true
                        ed = enter(ed, reason ?: EdCareReason.BODY, e.at)
                    }
                    SafetyEvents.ED_CARE_EXIT -> ed = exit(ed, e.at)
                    SafetyEvents.ED_CARE_STEP -> ed = step(ed, e.payload?.toIntOrNull())
                    SafetyEvents.ED_CARE_RETURN -> ed = takeBack(ed, EdCareReturn.parse(e.payload))
                    else -> unknown = true
                }
            }
        val flags = buildMap<SafetyFlag, ActiveFlag> {
            crisis?.let { put(it.flag, it) }
            if (ed.active) put(SafetyFlag.ED_CARE, ActiveFlag(SafetyFlag.ED_CARE, ed.since ?: 0L, null, ed.reason?.code))
        }
        return SafetyState(flags, ed, unknown)
    }

    private fun crisisFlag(e: JournalEvent, nowMs: Long): ActiveFlag? {
        // an unreadable level counts as the highest one
        val level = e.payload?.toIntOrNull() ?: 3
        if (level < 2) return null
        val until = e.at + CRISIS_WINDOW.toMillis()
        if (until <= nowMs) return null
        return ActiveFlag(if (level >= 3) SafetyFlag.CRISIS_L3 else SafetyFlag.CRISIS_L2, e.at, until, e.source)
    }

    /** L3 beats L2; of two equal levels the later one keeps the window open longer. */
    private fun strictest(a: ActiveFlag?, b: ActiveFlag?): ActiveFlag? = when {
        a == null -> b
        b == null -> a
        a.flag != b.flag -> if (a.flag == SafetyFlag.CRISIS_L3) a else b
        else -> if ((b.until ?: 0) >= (a.until ?: 0)) b else a
    }

    /**
     * A new entry while the mode is on keeps the longer cooldown (the strictest wins); an entry
     * after an exit starts over from step 0.
     */
    private fun enter(ed: EdCareState, reason: EdCareReason, at: Long): EdCareState {
        val ends = at + reason.cooldown.toMillis()
        return if (ed.active) {
            val longer = ends > (ed.cooldownEndsAt ?: 0)
            ed.copy(reason = if (longer) reason else ed.reason, cooldownEndsAt = maxOf(ends, ed.cooldownEndsAt ?: 0))
        } else {
            EdCareState(active = true, reason = reason, since = at, cooldownEndsAt = ends, history = true)
        }
    }

    /** Only the person's choice ends the mode, and only after the cooldown (D-057 п. 2). */
    private fun exit(ed: EdCareState, at: Long): EdCareState =
        if (ed.exitOpen(at)) ed.copy(active = false, step = EdCareRules.STEP_FOOD_NO_NUMBERS, returned = emptySet()) else ed

    private fun step(ed: EdCareState, n: Int?): EdCareState {
        if (ed.active || !ed.history || n == null) return ed
        val step = n.coerceIn(EdCareRules.STEP_FOOD_NO_NUMBERS, EdCareRules.STEP_LAST)
        // a step down closes again what step 3 gave back
        return ed.copy(step = step, returned = if (step < EdCareRules.STEP_LAST) emptySet() else ed.returned)
    }

    private fun takeBack(ed: EdCareState, item: EdCareReturn?): EdCareState =
        if (!ed.active && ed.step == EdCareRules.STEP_LAST && item != null) ed.copy(returned = ed.returned + item) else ed
}
