package com.healthofatitan.app.domain.path

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A broken rule of A56 §4: the part does not stand, a line says why (Э4). */
enum class Violation(val rule: String) {
    TOO_MANY_GOALS("P-01"),
    TOO_MANY_DRAFTS("P-01"),
    TOO_FEW_COMPONENTS("P-02"),
    TOO_MANY_COMPONENTS("P-02"),
    NO_LEAD("P-02"),
    TOO_MANY_LEADS("P-02"),
    LEVER_NOT_ONE("P-02"),
    NO_WATER("P-03"),
    MARK_WITHOUT_LADDER("P-04"),
    MARK_OUT_OF_RANGE("P-04"),
    MIRROR_AS_GOAL("P-04"),
    GIVEN_NOT_SAFE("P-05"),
    DEADLINE_TOO_SOON("P-06"),
    DEADLINE_TOO_FAR("P-06"),
    CRITERIA_COUNT("P-06"),
    CRITERION_NOT_RANGE("P-06"),
    WEIGHT_CLOSED("P-20"),
    WEIGHT_LATER("D-047"),
}

/**
 * The checkable rules P-01…P-06 and P-20 of A56 §4 over a goal as it would be. Every change is run
 * through [goal] before it is applied: a goal that breaks a rule is never stored.
 */
object PathValidator {
    /** P-01: active and paused goals ≤ 3, in the MVP ≤ 1. */
    const val MAX_GOALS_MVP = 1
    const val MAX_GOALS = 3
    const val MAX_DRAFTS = 1
    const val MAX_LEADS_TOTAL = 4
    const val MIN_WEEKS = 4
    const val MAX_MONTHS = 24L
    const val MAX_CRITERIA = 3

    private val LIVE = setOf(GoalStatus.ACTIVE, GoalStatus.PAUSED, GoalStatus.HELD, GoalStatus.REVIEW)

    /** P-01 for a new goal next to [goals]. */
    fun canStart(goals: List<PracticalGoal>, limit: Int = MAX_GOALS_MVP): List<Violation> = buildList {
        if (goals.count { it.status in LIVE } >= limit) add(Violation.TOO_MANY_GOALS)
    }

    fun canDraft(goals: List<PracticalGoal>): List<Violation> = buildList {
        if (goals.count { it.status == GoalStatus.DRAFT } >= MAX_DRAFTS) add(Violation.TOO_MANY_DRAFTS)
    }

    /** P-06: from 4 weeks to 24 months. */
    fun deadline(start: LocalDate, deadline: LocalDate): Violation? = when {
        ChronoUnit.DAYS.between(start, deadline) < MIN_WEEKS * 7L -> Violation.DEADLINE_TOO_SOON
        deadline.isAfter(start.plusMonths(MAX_MONTHS)) -> Violation.DEADLINE_TOO_FAR
        else -> null
    }

    /**
     * P-02…P-06, P-20 for one goal. [others] are the person's other live goals (≤ 4 leads in all,
     * one lever per person).
     */
    fun goal(
        goal: PracticalGoal,
        given: GivenLayer = GivenLayer.NONE,
        flags: PathFlags = PathFlags.DEFAULT,
        others: List<PracticalGoal> = emptyList(),
    ): List<Violation> = buildList {
        val parts = goal.components
        if (parts.size < PathDecomposer.MIN_COMPONENTS) add(Violation.TOO_FEW_COMPONENTS)
        if (parts.size > PathDecomposer.MAX_COMPONENTS) add(Violation.TOO_MANY_COMPONENTS)
        val leads = parts.count { it.role == Role.LEAD }
        if (leads == 0) add(Violation.NO_LEAD)
        if (leads > PathDecomposer.MAX_LEADS || leads + others.sumOf { o -> o.components.count { it.role == Role.LEAD } } > MAX_LEADS_TOTAL) {
            add(Violation.TOO_MANY_LEADS)
        }
        val levers = parts.count { it.isLever } + others.sumOf { o -> o.components.count { it.isLever } }
        if (levers != 1) add(Violation.LEVER_NOT_ONE)
        if (parts.none { it.key.element == Element.WATER }) add(Violation.NO_WATER)
        parts.forEach { c ->
            val target = c.targetMark
            if (target != null && !c.key.markable) add(Violation.MARK_WITHOUT_LADDER)
            if (target != null && target !in MarkRules.MIN..MarkRules.MAX) add(Violation.MARK_OUT_OF_RANGE)
            val g = given.constraintOf(c.key)
            if (g != null && (g.safeVersion == null || c.safeVersion != g.safeVersion)) add(Violation.GIVEN_NOT_SAFE)
            if (c.key.weightOrStrength && !flags.weightAndStrength) add(Violation.WEIGHT_LATER)
            if (c.key == ComponentKey.BODY && given.closesWeight) add(Violation.WEIGHT_CLOSED)
        }
        if (goal.criteria.isEmpty() || goal.criteria.size > MAX_CRITERIA) add(Violation.CRITERIA_COUNT)
        goal.criteria.forEach { cr ->
            if (!(cr.low < cr.high)) add(Violation.CRITERION_NOT_RANGE)
            if (cr.metric?.mirrorOnly == true) add(Violation.MIRROR_AS_GOAL)
            if (cr.metric?.component?.weightOrStrength == true && !flags.weightAndStrength) add(Violation.WEIGHT_LATER)
            if (cr.metric == PathMetric.WAIST && given.closesWeight) add(Violation.WEIGHT_CLOSED)
        }
        if (goal.status == GoalStatus.DRAFT || goal.version <= 1) deadline(goal.start, goal.deadline)?.let(::add)
    }.distinct()

    /** P-03: the last component of Water cannot be removed — only replaced. */
    fun removable(goal: PracticalGoal, key: ComponentKey): Boolean =
        key.element != Element.WATER || goal.components.count { it.key.element == Element.WATER } > 1
}

/** The events of the state model (A56 §5) and who may cause them. */
enum class GoalEvent(val by: Set<Initiator>) {
    TAKE(setOf(Initiator.PERSON)),
    FROM_QUEUE(setOf(Initiator.PERSON)),
    PAUSE(setOf(Initiator.PERSON)),
    RESUME(setOf(Initiator.PERSON)),
    HOLD(setOf(Initiator.HOT)),
    UNHOLD(setOf(Initiator.PERSON)),
    STAGE_END(setOf(Initiator.HOT)),

    /** The next stage: the person, or HoT after 7 days without an answer («Собрал сам»). */
    NEXT_STAGE(setOf(Initiator.PERSON, Initiator.HOT)),
    CLOSE(setOf(Initiator.PERSON)),
    RELEASE(setOf(Initiator.PERSON)),

    /** A released goal goes to «Несу» by the person's toggle (on by default). */
    TO_QUEUE(setOf(Initiator.PERSON, Initiator.HOT)),
    UNDO_RELEASE(setOf(Initiator.PERSON)),
}

/** A56 §5 as a table. A rebuild is a new version over ACTIVE, not a state. */
object GoalLifecycle {
    const val DRAFT_DAYS = 7L
    const val HOLD_DAYS = 7L

    fun next(from: GoalStatus, event: GoalEvent, by: Initiator): GoalStatus? {
        if (by !in event.by) return null
        return when (event) {
            GoalEvent.TAKE -> GoalStatus.ACTIVE.takeIf { from == GoalStatus.DRAFT }
            GoalEvent.FROM_QUEUE -> GoalStatus.DRAFT.takeIf { from == GoalStatus.QUEUED }
            GoalEvent.PAUSE -> GoalStatus.PAUSED.takeIf { from == GoalStatus.ACTIVE }
            GoalEvent.RESUME -> GoalStatus.ACTIVE.takeIf { from == GoalStatus.PAUSED }
            GoalEvent.HOLD -> GoalStatus.HELD.takeIf { from == GoalStatus.ACTIVE || from == GoalStatus.PAUSED }
            GoalEvent.UNHOLD -> GoalStatus.ACTIVE.takeIf { from == GoalStatus.HELD }
            GoalEvent.STAGE_END -> GoalStatus.REVIEW.takeIf { from == GoalStatus.ACTIVE }
            GoalEvent.NEXT_STAGE -> GoalStatus.ACTIVE.takeIf { from == GoalStatus.REVIEW }
            GoalEvent.CLOSE -> GoalStatus.TAKEN.takeIf { from == GoalStatus.REVIEW || from == GoalStatus.ACTIVE }
            GoalEvent.RELEASE -> GoalStatus.RELEASED.takeIf { from == GoalStatus.ACTIVE || from == GoalStatus.PAUSED || from == GoalStatus.REVIEW }
            GoalEvent.TO_QUEUE -> GoalStatus.QUEUED.takeIf { from == GoalStatus.RELEASED }
            GoalEvent.UNDO_RELEASE -> GoalStatus.ACTIVE.takeIf { from == GoalStatus.RELEASED }
        }
    }

    /** A draft without «Беру путь» lives 7 days, then it is quietly deleted (A56 Э7). */
    fun draftExpired(createdOn: LocalDate, today: LocalDate): Boolean = ChronoUnit.DAYS.between(createdOn, today) >= DRAFT_DAYS

    /** A hold (SAF-01, crisis, StormNet) ends by a tap after 7 days. */
    fun holdMayEnd(heldOn: LocalDate, today: LocalDate): Boolean = ChronoUnit.DAYS.between(heldOn, today) >= HOLD_DAYS

    /** P-22: one layer by status. */
    fun layer(status: GoalStatus): GoalLayer = when (status) {
        GoalStatus.QUEUED -> GoalLayer.CARRYING
        GoalStatus.ACTIVE, GoalStatus.PAUSED, GoalStatus.HELD, GoalStatus.REVIEW -> GoalLayer.PATH
        GoalStatus.TAKEN -> GoalLayer.TAKEN
        GoalStatus.DRAFT, GoalStatus.RELEASED -> GoalLayer.NONE
    }
}
