package com.healthofatitan.app.domain.path

import java.time.LocalDate

/** The six states of a part of «Срез мира» (A58-VP §1.4): the form tells the state, colour repeats it. */
enum class PartState { OFF, MARKS, PRACTICE, EXCLUDED }

/**
 * One part of the slice. [lever] and [safeVersion] are marks over the state; a pause is a flag of
 * the whole slice (the light never goes out, P-14).
 *
 * @param mark the filled marks 0…5 (MARKS only); [target] the goal tick
 */
data class SlicePart(
    val key: ComponentKey,
    val state: PartState,
    val role: Role? = null,
    val mark: Int = 0,
    val target: Int? = null,
    val lever: Boolean = false,
    val safeVersion: String? = null,
)

/** The balance label and the one step (A55 §1 п. 5): a word, never a number (A54 §5). */
enum class BalanceKind { EVEN, LEVER, HEAVY, PAUSED, HELD, NO_GOAL }

data class Balance(val kind: BalanceKind, val title: String, val sub: String, val lever: ComponentKey?)

data class ElementView(val element: Element, val status: String, val parts: List<SlicePart>, val outside: Int)

/**
 * Everything the screen «Путь» shows (A55 §1, A58-VP §5.1). Built by [PathOverviewBuilder];
 * no sum, no average, no score anywhere (A54 §5, P-19).
 */
data class PathOverview(
    val today: LocalDate,
    val goal: PracticalGoal?,
    val parts: List<SlicePart>,
    val balance: Balance,
    val elements: List<ElementView>,
    val horizon: HorizonTitan?,
    val week: Int,
    val stagesTotal: Int,
    val nextMilestone: Pair<Int, Long>?,
    val given: List<String>,
    /** «Зачем» is hidden on a heavy week and shown by a tap on the star (A55 §2 mechanic 1). */
    val whyVisible: Boolean,
    /** The Titan of the horizon is a pillar of light: ed_care or «Форма» in the goal (A57 §4.1, A58-VP §2.5). */
    val lightOnly: Boolean,
    val paused: Boolean,
) {
    companion object {
        fun empty(today: LocalDate): PathOverview = PathOverviewBuilder.build(today, null, emptyMap(), emptySet(), GivenLayer.NONE)
    }
}

object PathOverviewBuilder {
    /**
     * @param marks the marks taken so far per component (from `path_mark`)
     * @param transferred fires that moved to the person (never taken back, P-14)
     * @param heavy a hard week («Бережно» or StormNet): one hand, Water «держим»
     * @param safety where the holds come from (D-051: one SafetyState later; the goal and the week now)
     */
    fun build(
        today: LocalDate,
        goal: PracticalGoal?,
        marks: Map<ComponentKey, Int>,
        transferred: Set<ComponentKey>,
        given: GivenLayer,
        sufficiency: List<Sufficiency> = emptyList(),
        heavy: Boolean = false,
        safety: PathSafety = PathSafety.LOCAL,
    ): PathOverview {
        val live = goal?.takeIf { GoalLifecycle.layer(it.status) == GoalLayer.PATH }
        val parts = ComponentKey.entries.map { key ->
            val c = live?.component(key)
            val constraint = given.constraintOf(key)
            when {
                c == null && constraint != null && constraint.safeVersion == null -> SlicePart(key, PartState.EXCLUDED)
                c == null -> SlicePart(key, PartState.OFF, safeVersion = constraint?.safeVersion)
                key.markable -> SlicePart(
                    key, PartState.MARKS, c.role,
                    mark = maxOf(marks[key] ?: 0, c.startMark ?: 0), target = c.targetMark, lever = c.isLever, safeVersion = c.safeVersion,
                )
                else -> SlicePart(key, PartState.PRACTICE, c.role, lever = c.isLever, safeVersion = c.safeVersion)
            }
        }
        val hold = safety.hold(live, heavy)
        val paused = hold.paused
        val held = hold.held
        val lever = live?.lever
        val leverDeficit = lever?.let { l -> sufficiency.firstOrNull { it.key == l.key }?.deficit } ?: 0.0
        val balance = when {
            live == null -> Balance(BalanceKind.NO_GOAL, PathCopy.EMPTY_TITLE, PathCopy.EMPTY_LINE, null)
            held -> Balance(BalanceKind.HELD, PathCopy.HELD_TITLE, PathCopy.HELD_SUB, null)
            paused -> Balance(BalanceKind.PAUSED, PathCopy.PAUSED_TITLE, PathCopy.PAUSED_SUB, null)
            hold.heavy -> Balance(BalanceKind.HEAVY, PathCopy.HEAVY_TITLE, PathCopy.HEAVY_SUB, null)
            lever != null && leverDeficit > 0.0 -> PathLexicon.name(lever.key).let {
                Balance(BalanceKind.LEVER, PathCopy.lever(it), PathCopy.leverSub(it), lever.key)
            }
            else -> Balance(BalanceKind.EVEN, PathCopy.BALANCE_EVEN, PathCopy.BALANCE_EVEN_SUB, lever?.key)
        }
        val elements = Element.entries.map { e ->
            val mine = parts.filter { it.key.element == e && it.role != null }
            val status = when {
                live == null -> PathLexicon.motto(e)
                hold.any -> if (e == Element.WATER) PathCopy.STATUS_HOLDS else PathCopy.STATUS_KEPT
                balance.kind == BalanceKind.LEVER && lever?.key?.element == e -> PathCopy.leverStatus(PathLexicon.name(lever.key))
                mine.any { it.role == Role.LEAD } -> PathCopy.STATUS_LEADS
                mine.isNotEmpty() && e != Element.WATER -> PathCopy.STATUS_PROPS
                else -> PathCopy.STATUS_HOLDS
            }
            ElementView(e, status, mine, ComponentKey.of(e).size - mine.size)
        }
        return PathOverview(
            today = today,
            goal = live,
            parts = parts,
            balance = balance,
            elements = elements,
            horizon = live?.let { HorizonRules.build(it, today, transferred) },
            week = live?.let { StageRules.week(it, today) } ?: 0,
            stagesTotal = live?.let { StageRules.stagesTotal(it) } ?: 0,
            nextMilestone = live?.let { StageRules.nextMilestone(it, today) },
            given = given.constraints.map { it.label },
            whyVisible = !hold.any,
            lightOnly = given.edCare || live?.has(ComponentKey.BODY) == true,
            paused = paused || held,
        )
    }
}
