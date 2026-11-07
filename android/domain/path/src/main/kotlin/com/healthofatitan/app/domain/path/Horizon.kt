package com.healthofatitan.app.domain.path

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * The stage of 12 weeks (A56 §1 `Stage`): milestones at weeks 4, 8 and 12. A pause stops the clock:
 * it neither resets the stage nor burns weeks (P-16); the milestones move by the paused days.
 */
object StageRules {
    const val WEEKS = 12
    val MILESTONES = listOf(4, 8, 12)

    /** Paused days of the stage up to [today]: the closed pauses and the current one. */
    fun pausedDays(goal: PracticalGoal, today: LocalDate): Int {
        val open = goal.pauseFrom?.takeIf { goal.status == GoalStatus.PAUSED || goal.status == GoalStatus.HELD }
            ?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0).toInt() } ?: 0
        return goal.pausedDays + open
    }

    /** The day of the stage's own calendar: today minus every paused day. */
    fun effectiveDay(goal: PracticalGoal, today: LocalDate): LocalDate = today.minusDays(pausedDays(goal, today).toLong())

    /** Week 1–12 of the stage. */
    fun week(goal: PracticalGoal, today: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(goal.start, effectiveDay(goal, today)).coerceAtLeast(0)
        return (days / 7 + 1).toInt().coerceIn(1, WEEKS)
    }

    /** The date of the milestone at [week] (4, 8 or 12), moved by the pauses so far. */
    fun milestone(goal: PracticalGoal, week: Int, today: LocalDate): LocalDate =
        goal.start.plusDays(week * 7L - 1).plusDays(pausedDays(goal, today).toLong())

    /** The next milestone not reached yet and the days to it; null after week 12. */
    fun nextMilestone(goal: PracticalGoal, today: LocalDate): Pair<Int, Long>? = MILESTONES
        .map { it to milestone(goal, it, today) }
        .firstOrNull { (_, d) -> !d.isBefore(today) }
        ?.let { (w, d) -> w to ChronoUnit.DAYS.between(today, d) }

    /** The stage is over: the 12 weeks of its own calendar are behind (→ REVIEW, «Встреча»). */
    fun stageOver(goal: PracticalGoal, today: LocalDate): Boolean =
        ChronoUnit.DAYS.between(goal.start, effectiveDay(goal, today)) >= WEEKS * 7L

    /** Stages to the deadline, at least one (A56 Э2: a goal over 12 weeks is split into stages). */
    fun stagesTotal(goal: PracticalGoal): Int {
        val weeks = ChronoUnit.DAYS.between(goal.start, goal.deadline).coerceAtLeast(1) / 7.0
        return kotlin.math.ceil(weeks / WEEKS).toInt().coerceAtLeast(goal.stage)
    }

    /** Monday of the week of [day] (the Week of A52 runs Monday to Sunday). */
    fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}

/**
 * A fire on the Titan of the horizon (A57 §4.1). Fires of components without marks («Чувства»,
 * «Взгляд», practice) are [SHARED]: a soft halo on both figures at once, never moved, never counted.
 */
enum class HorizonLight { ON_HORIZON, WITH_PERSON, SHARED }

data class HorizonFire(val key: ComponentKey, val light: HorizonLight)

/**
 * «Титан горизонта» (A57 §4, A56 §1): the future you of light on the horizon of «Срез мира».
 *
 * @param step 0…12, the steps he made towards the person — by the calendar only
 * @param seated the path is on pause: he sits on a stone and waits (A56 §3)
 */
data class HorizonTitan(
    val goalId: Long,
    val name: String,
    val fires: List<HorizonFire>,
    val step: Int,
    val seated: Boolean,
) {
    /** Week 12: the figures meet whatever happened (A57 §4.1). There is no state «провал». */
    val meeting: Boolean get() = step >= HorizonRules.STEPS

    val withPerson: List<ComponentKey> get() = fires.filter { it.light == HorizonLight.WITH_PERSON }.map { it.key }
}

/**
 * The rules of the Titan of the horizon. **The step depends on the calendar only** (A57 резюме):
 * [step] takes no progress at all, so a lag can never be shown as a distance (P-19). Data decide
 * only which fires moved to the person, and a fire that moved never returns (P-14).
 */
object HorizonRules {
    const val STEPS = 12

    /** One step every Sunday of the stage's own calendar (pauses do not count: he waits, P-16). */
    fun step(stageStart: LocalDate, today: LocalDate, pausedDays: Int): Int {
        val end = today.minusDays(pausedDays.toLong())
        if (!end.isAfter(stageStart)) return 0
        val firstSunday = stageStart.plusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        if (firstSunday.isAfter(end)) return 0
        return (ChronoUnit.DAYS.between(firstSunday, end) / 7 + 1).toInt().coerceAtMost(STEPS)
    }

    /** The fires: the leading and propping components (A57 §4.1). */
    fun fires(goal: PracticalGoal, transferred: Set<ComponentKey>): List<HorizonFire> = goal.components
        .filter { it.role == Role.LEAD || it.role == Role.PROP }
        .map { c ->
            val light = when {
                c.key in transferred -> HorizonLight.WITH_PERSON
                !c.key.markable || c.targetMark == null -> HorizonLight.SHARED
                else -> HorizonLight.ON_HORIZON
            }
            HorizonFire(c.key, light)
        }

    /**
     * The fires that moved to the person: the ones before (never taken back, P-14) and every
     * markable component whose taken mark reached its target.
     */
    fun transfers(goal: PracticalGoal, marks: Map<ComponentKey, Int>, already: Set<ComponentKey>): Set<ComponentKey> =
        already + goal.components
            .filter { it.key.markable && it.targetMark != null && (marks[it.key] ?: 0) >= it.targetMark }
            .map { it.key }

    /** Э7: the first fire from «Взято» flies to the person at once (A56 Э7, wow 3). */
    fun firstFromTaken(goal: PracticalGoal, taken: List<TakenItem>): ComponentKey? = goal.components
        .filter { it.role == Role.LEAD || it.role == Role.PROP }
        .firstOrNull { c -> taken.any { it.key == c.key } }
        ?.key

    fun build(goal: PracticalGoal, today: LocalDate, transferred: Set<ComponentKey>): HorizonTitan = HorizonTitan(
        goalId = goal.id,
        name = PathCopy.horizonName(goal.beName, goal.deadline),
        fires = fires(goal, transferred),
        step = step(goal.start, today, StageRules.pausedDays(goal, today)),
        seated = goal.status == GoalStatus.PAUSED || goal.status == GoalStatus.HELD,
    )
}

/** When «голос с горизонта» opens (A57 §4.2). */
enum class LetterOpen { MILESTONE_8, MEETING }

/** A letter from the future you, ≤ 300 signs, optional (A56 §1 `Letter`). */
data class Letter(val goalId: Long, val text: String, val openAt: LetterOpen, val writtenOn: LocalDate)

object LetterRules {
    const val MAX = 300

    fun valid(text: String): Boolean = text.isNotBlank() && text.length <= MAX

    /**
     * The letter opens at milestone 8 or at the meeting. It never appears after gaps (P-18): on a
     * pause or a hold it waits.
     */
    fun opens(letter: Letter, goal: PracticalGoal, today: LocalDate): Boolean {
        if (goal.status != GoalStatus.ACTIVE && goal.status != GoalStatus.REVIEW) return false
        return when (letter.openAt) {
            LetterOpen.MILESTONE_8 -> !today.isBefore(StageRules.milestone(goal, 8, today))
            LetterOpen.MEETING -> goal.status == GoalStatus.REVIEW || StageRules.stageOver(goal, today)
        }
    }
}

/** The experiment of the week from the goal's lever (A55 §2 mechanic 4). */
data class Experiment(val weekStart: LocalDate, val key: ComponentKey, val ifThen: String?)

enum class ExperimentOutcome { LEARNED, PARTLY }

/**
 * P-15: the experiment does not change in the middle of a week. It is built from the goal **as it
 * was on Monday**; a change later in the week reaches the next one. No experiment in «Бережно» and
 * after `StormNet`.
 */
object ExperimentRules {
    fun forWeek(goalOnMonday: PracticalGoal?, weekStart: LocalDate, gentle: Boolean, stormNet: Boolean): Experiment? {
        if (gentle || stormNet || goalOnMonday == null || goalOnMonday.status != GoalStatus.ACTIVE) return null
        val lever = goalOnMonday.lever ?: return null
        return Experiment(weekStart, lever.key, goalOnMonday.obstacle?.ifThen)
    }

    /** The lever disappeared during the week: «узнали частично»; otherwise «узнали». */
    fun outcome(experiment: Experiment, now: PracticalGoal?): ExperimentOutcome =
        if (now?.has(experiment.key) == true) ExperimentOutcome.LEARNED else ExperimentOutcome.PARTLY
}

/** «На плаву» (A54 §2.3): not counted while the pause is illness, grief or vacation (P-16). */
object AfloatRules {
    fun counts(goal: PracticalGoal): Boolean {
        val reason = goal.pauseReason ?: return true
        val paused = goal.status == GoalStatus.PAUSED || goal.status == GoalStatus.HELD
        return !(paused && reason.freezesAfloat)
    }
}

/** Pushes of the path (P-18): only one — the return after a pause by its term. */
enum class PathPush { RETURN_AFTER_PAUSE }

object PathPushes {
    val ALLOWED: Set<PathPush> = setOf(PathPush.RETURN_AFTER_PAUSE)

    /** The return push is due on the pause's last day; never in grief (no term is asked). */
    fun due(goal: PracticalGoal, today: LocalDate): PathPush? {
        if (goal.status != GoalStatus.PAUSED) return null
        val reason = goal.pauseReason
        if (reason != null && !reason.asksTerm) return null
        val until = goal.pauseUntil ?: return null
        return if (!today.isBefore(until)) PathPush.RETURN_AFTER_PAUSE else null
    }

    /** The push text: never «Зачем», never the letter (P-18). */
    fun text(push: PathPush): String = when (push) {
        PathPush.RETURN_AFTER_PAUSE -> PathCopy.PUSH_RETURN
    }
}
