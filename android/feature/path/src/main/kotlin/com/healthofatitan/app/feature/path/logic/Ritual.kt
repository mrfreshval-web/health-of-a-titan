package com.healthofatitan.app.feature.path.logic

import com.healthofatitan.app.domain.path.ComponentKey
import com.healthofatitan.app.domain.path.Criterion
import com.healthofatitan.app.domain.path.Decomposition
import com.healthofatitan.app.domain.path.DecompositionInput
import com.healthofatitan.app.domain.path.Element
import com.healthofatitan.app.domain.path.GivenLayer
import com.healthofatitan.app.domain.path.GoalComponent
import com.healthofatitan.app.domain.path.GoalGate
import com.healthofatitan.app.domain.path.GoalGuard
import com.healthofatitan.app.domain.path.GoalKind
import com.healthofatitan.app.domain.path.GoalMode
import com.healthofatitan.app.domain.path.GoalStatus
import com.healthofatitan.app.domain.path.GoalTemplate
import com.healthofatitan.app.domain.path.HorizonRules
import com.healthofatitan.app.domain.path.Obstacle
import com.healthofatitan.app.domain.path.PathDecomposer
import com.healthofatitan.app.domain.path.PathFlags
import com.healthofatitan.app.domain.path.PathTemplates
import com.healthofatitan.app.domain.path.PathValidator
import com.healthofatitan.app.domain.path.PracticalGoal
import com.healthofatitan.app.domain.path.Realism
import com.healthofatitan.app.domain.path.RealismRules
import com.healthofatitan.app.domain.path.Role
import com.healthofatitan.app.domain.path.Sufficiency
import com.healthofatitan.app.domain.path.TakenItem
import com.healthofatitan.app.domain.path.Violation
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit

/** The seven screens of «Назови цель» (A56 §2, A58-VP §5.2). */
enum class RitualStep { GOAL, TRUTH, BE, PARTS, OBSTACLE, REAL, TAKE }

/** The deadline chips of Э2 (A56 Э2): «12 недель», «к лету», «к 31.12», «через год». */
enum class DeadlineChoice { WEEKS_12, SUMMER, YEAR_END, ONE_YEAR }

/**
 * What the person said in the ritual so far. Free text never leaves the phone and is read by
 * CrisisGuard before anything else (A07); numbers come from tables only (P-07).
 */
data class RitualDraft(
    val step: RitualStep = RitualStep.GOAL,
    val text: String = "",
    val templateId: String? = null,
    val deadline: DeadlineChoice = DeadlineChoice.WEEKS_12,
    val why: String = "",
    val beName: String = "",
    /** The parts as the person edits them on Э4; null = the decomposition as is. */
    val parts: List<GoalComponent>? = null,
    val obstacle: String = "",
    val ifThen: String = "",
    val mode: GoalMode = GoalMode.BY_DATE,
    val letter: String = "",
)

/** The deterministic logic of the ritual over the domain tables (no AI in numbers, P-07). */
object RitualLogic {
    fun template(d: RitualDraft, flags: PathFlags, given: GivenLayer): GoalTemplate? =
        PathTemplates.byId(d.templateId) ?: PathTemplates.match(d.text, flags, given)

    /**
     * The template whose parts and criteria a goal takes: the matched one, or for «Соберём вместе»
     * (no template) the template «Силы на вечер» as a start the person edits on Э4.
     */
    fun basis(d: RitualDraft, flags: PathFlags, given: GivenLayer): GoalTemplate? =
        template(d, flags, given) ?: PathTemplates.byId(PathTemplates.ENERGY)

    fun gate(d: RitualDraft, flags: PathFlags, given: GivenLayer): GoalGate =
        if (d.templateId != null) GoalGate.OK else GoalGuard.check(d.text, given, flags)

    fun title(d: RitualDraft, flags: PathFlags, given: GivenLayer): String =
        d.text.trim().takeIf { it.isNotEmpty() } ?: template(d, flags, given)?.title.orEmpty()

    /** The date of a chip; «к 31.12» closer than 4 weeks moves to the next year (P-12 minimum). */
    fun deadline(choice: DeadlineChoice, today: LocalDate): LocalDate = when (choice) {
        DeadlineChoice.WEEKS_12 -> today.plusWeeks(12)
        DeadlineChoice.SUMMER -> LocalDate.of(today.year, Month.JUNE, 1).let { if (ChronoUnit.WEEKS.between(today, it) < 4) it.plusYears(1) else it }
        DeadlineChoice.YEAR_END -> LocalDate.of(today.year, Month.DECEMBER, 31).let { if (ChronoUnit.WEEKS.between(today, it) < 4) it.plusYears(1) else it }
        DeadlineChoice.ONE_YEAR -> today.plusYears(1)
    }

    fun decomposition(
        d: RitualDraft,
        flags: PathFlags,
        given: GivenLayer,
        taken: List<TakenItem>,
        sufficiency: List<Sufficiency>,
    ): Decomposition {
        return PathDecomposer.decompose(
            DecompositionInput(
                template = basis(d, flags, given),
                given = given,
                taken = taken,
                sufficiency = sufficiency,
                flags = flags,
            ),
        )
    }

    fun parts(d: RitualDraft, dec: Decomposition): List<GoalComponent> = d.parts ?: dec.components

    /** Э4: remove a part — Water stays (P-03: it can be replaced, not removed). */
    fun remove(parts: List<GoalComponent>, key: ComponentKey): List<GoalComponent>? {
        val left = parts.filterNot { it.key == key }
        if (left.none { it.key.element == Element.WATER }) return null
        if (left.size < PathDecomposer.MIN_COMPONENTS) return null
        val relevered = if (left.none { it.isLever }) left.mapIndexed { i, c -> c.copy(isLever = i == left.indexOfFirst { it.role == Role.LEAD }) } else left
        return if (relevered.none { it.role == Role.LEAD }) relevered.mapIndexed { i, c -> if (i == 0) c.copy(role = Role.LEAD) else c } else relevered
    }

    /** Э4: a role in a tap cycles ведёт → подпирает → бережёт; never a third lead. */
    fun cycleRole(parts: List<GoalComponent>, key: ComponentKey): List<GoalComponent> {
        val leads = parts.count { it.role == Role.LEAD }
        return parts.map { c ->
            if (c.key != key) return@map c
            var next = Role.entries[(c.role.ordinal + 1) % Role.entries.size]
            if (next == Role.LEAD && leads >= PathDecomposer.MAX_LEADS) next = Role.PROP
            c.copy(role = next)
        }.let { list -> if (list.none { it.role == Role.LEAD }) parts else list }
    }

    /** Э4: «+ из стихии» — the first part of [e] that is not in the goal and not closed by «Дано». */
    fun add(parts: List<GoalComponent>, e: Element, flags: PathFlags, given: GivenLayer): List<GoalComponent> {
        if (parts.size >= PathDecomposer.MAX_COMPONENTS) return parts
        val key = ComponentKey.of(e).firstOrNull { k ->
            parts.none { it.key == k } && (flags.weightAndStrength || !k.weightOrStrength) &&
                !(given.closesWeight && (k == ComponentKey.BODY || k == ComponentKey.FUEL)) &&
                given.constraintOf(k)?.let { it.safeVersion != null } != false
        } ?: return parts
        return parts + GoalComponent(key, Role.PROP, safeVersion = given.constraintOf(key)?.safeVersion)
    }

    fun criteria(d: RitualDraft, flags: PathFlags, given: GivenLayer): List<Criterion> = basis(d, flags, given)?.criteria.orEmpty()

    /** Э6 «Как реально» (R7): without the safe pace tables only the personal pace of «Взято» counts. */
    fun realism(d: RitualDraft, today: LocalDate, flags: PathFlags, given: GivenLayer, personalPace: Double? = null): Realism {
        val c = criteria(d, flags, given).firstOrNull() ?: return Realism.NoPace
        val weeks = RealismRules.weeksBetween(today, deadline(d.deadline, today))
        return RealismRules.assess(c.low..c.high, weeks, today, safePacePerWeek = null, personalPacePerWeek = personalPace)
    }

    /** The goal of «Беру путь»: ACTIVE from today, version 1 (A56 §1). */
    fun build(
        d: RitualDraft,
        today: LocalDate,
        nowMs: Long,
        flags: PathFlags,
        given: GivenLayer,
        dec: Decomposition,
    ): PracticalGoal {
        val t = template(d, flags, given)
        return PracticalGoal(
            id = 0,
            title = title(d, flags, given).take(TITLE_MAX),
            why = d.why.trim().takeIf { it.isNotEmpty() },
            beName = d.beName.trim().takeIf { it.isNotEmpty() },
            kind = t?.kind ?: GoalKind.DO,
            templateId = t?.id ?: PathTemplates.CUSTOM,
            start = today,
            deadline = deadline(d.deadline, today),
            mode = d.mode,
            status = GoalStatus.ACTIVE,
            criteria = criteria(d, flags, given),
            obstacle = d.obstacle.trim().takeIf { it.isNotEmpty() }?.let { Obstacle(it, d.ifThen.trim()) },
            components = parts(d, dec),
            takenAt = nowMs,
        )
    }

    /** What still stops «Беру путь» (Э4 shows each as one line with its reason). */
    fun violations(goal: PracticalGoal, given: GivenLayer, flags: PathFlags, others: List<PracticalGoal>): List<Violation> =
        PathValidator.canStart(others) + PathValidator.goal(goal, given, flags, others)

    /** The fire that flies to the person on Э7 at once (wow 3): the first part with «Взято». */
    fun firstFire(goal: PracticalGoal, taken: List<TakenItem>): ComponentKey? = HorizonRules.firstFromTaken(goal, taken)

    const val TITLE_MAX = 60
}
