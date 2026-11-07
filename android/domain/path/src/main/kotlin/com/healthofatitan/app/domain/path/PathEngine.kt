package com.healthofatitan.app.domain.path

import java.time.LocalDate

/** Builds what the tab «Путь» and the step of the day show, from stored goals, marks and data. */
object PathEngine {

    /**
     * @param goals every goal ever taken (active and history)
     * @param marks the user's tapped steps per goal id
     * @param regime today's mode, null while the day has no plan yet
     */
    fun state(
        today: LocalDate,
        goals: List<GoalRecord>,
        marks: Map<Long, Set<LocalDate>>,
        ev: PathEvidence,
        regime: PathRegime?,
    ): PathState {
        fun counted(goal: GoalRecord): Set<LocalDate> {
            val end = goal.endedDay ?: today
            val tapped = marks[goal.id].orEmpty()
            val fromData = generateSequence(goal.startDay) { it.plusDays(1) }
                .takeWhile { it <= end }
                .filter { ev.met(goal.plan.sphere, it) }
            return (fromData + tapped.asSequence().filter { it >= goal.startDay && it <= end }).toSet()
        }

        val active = goals.filter { it.status == GoalStatus.ACTIVE }.maxByOrNull { it.id }
        val activeCounted = active?.let(::counted).orEmpty()
        return PathState(
            today = today,
            active = active?.let { PathRules.progress(it, today, activeCounted) },
            proposals = PathRules.proposals(ev),
            spheres = spheres(ev, regime, active, active?.let { marks[it.id] }.orEmpty()),
            past = goals.filter { it.id != active?.id }.sortedByDescending { it.id }.map { PathRules.progress(it, today, counted(it)) },
            stepVersion = PathRules.stepVersion(regime),
            stepDone = today in activeCounted,
            stepMarked = active != null && today in marks[active.id].orEmpty(),
        )
    }

    /**
     * «Твой Титан» (A37 §8): share of the last 7 days with a step in each sphere. A sphere HoT
     * cannot see yet has no share and stays a contour; it never goes dark (A42 §3.4).
     */
    fun spheres(ev: PathEvidence, regime: PathRegime?, active: GoalRecord?, tapped: Set<LocalDate>): List<SphereLight> {
        val threshold = if (regime == PathRegime.GENTLE) PathRules.LIGHT_GENTLE else PathRules.LIGHT
        val days = (0L until 7L).map { ev.today.minusDays(it) }
        return Sphere.entries.map { sphere ->
            val isGoal = active?.plan?.sphere == sphere
            if (!ev.measured(sphere) && !isGoal) {
                SphereLight(sphere, null, false)
            } else {
                val n = days.count { ev.met(sphere, it) || (isGoal && it in tapped) }
                val share = n / 7f
                SphereLight(sphere, share, share + 1e-6f >= threshold)
            }
        }
    }
}
