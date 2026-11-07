package com.healthofatitan.app.domain.path

import java.time.LocalDate

/**
 * Six spheres of «Твой Титан» (A37 §8), from the head down: разум — голова, работа — руки,
 * связи — грудь, еда — корпус, тело — ноги, сон — сияние-кольцо вокруг фигуры.
 */
enum class Sphere(val id: String) {
    MIND("mind"),
    WORK("work"),
    BONDS("bonds"),
    FOOD("food"),
    BODY("body"),
    SLEEP("sleep"),
    ;

    companion object {
        fun fromId(id: String?): Sphere? = entries.firstOrNull { it.id == id }
    }
}

/** The day's mode as the path sees it (A09: Разгон / Ровно / Бережно), without the engine. */
enum class PathRegime { BOOST, STEADY, GENTLE }

/**
 * A goal for one cycle (A42 §3.2): «N days of 7» in one sphere, process only — never weight or
 * looks (A42 §3.2 п. 2). Three stages of four weeks; the last stage holds the target.
 *
 * @param baseline days of 7 now, from data; null when HoT has no week of data yet (default plan)
 */
data class GoalPlan(val sphere: Sphere, val baseline: Int?, val stages: List<Int>) {
    init {
        require(stages.size == PathRules.STAGES) { "a cycle has ${PathRules.STAGES} stages" }
    }

    val fromData: Boolean get() = baseline != null
    val target: Int get() = stages.last()
}

enum class GoalStatus { ACTIVE, REPLACED, DONE }

/** A goal as stored: one ACTIVE at a time (D-033); a changed goal stays as REPLACED history. */
data class GoalRecord(
    val id: Long,
    val plan: GoalPlan,
    val startDay: LocalDate,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val endedDay: LocalDate? = null,
)

/**
 * What HoT knows from stored, derived data (no raw WHOOP values): the sleep hours of each night
 * (metric SLP) and the days with a WHOOP workout (training_session), for the last weeks.
 */
data class PathEvidence(
    val today: LocalDate,
    val sleepHours: Map<LocalDate, Double> = emptyMap(),
    val trainingDays: Set<LocalDate> = emptySet(),
) {
    /** HoT can see this sphere from data (in the MVP: sleep and body, once WHOOP has nights). */
    fun measured(sphere: Sphere): Boolean = (sphere == Sphere.SLEEP || sphere == Sphere.BODY) && sleepHours.isNotEmpty()

    /** The data says the sphere's step was done that day. */
    fun met(sphere: Sphere, day: LocalDate): Boolean = when (sphere) {
        Sphere.SLEEP -> (sleepHours[day] ?: 0.0) >= PathRules.SLEEP_HOURS - EPS
        Sphere.BODY -> day in trainingDays
        else -> false
    }

    private companion object {
        const val EPS = 1e-9
    }
}

/** A week of the 12-week ring: lit, a contour («мимо», never red or empty), the current one, ahead. */
enum class WeekState { LIT, MISSED, CURRENT, FUTURE }

data class WeekMark(val week: Int, val state: WeekState, val done: Int, val target: Int)

/**
 * Where a goal stands: week 1–12, stage 1–3, the week goal and how much of it is done.
 *
 * @param finished the 12 weeks are behind (the ritual of A42 §3.5 comes in a later slice)
 */
data class GoalProgress(
    val goal: GoalRecord,
    val week: Int,
    val stage: Int,
    val weekTarget: Int,
    val weekDone: Int,
    val weeks: List<WeekMark>,
    val finished: Boolean,
) {
    val litWeeks: Int get() = weeks.count { it.state == WeekState.LIT }
}

/** How a step of the day is sized by the mode (A42 §3.3): the path never argues with the day. */
enum class StepVersion { FULL, NORMAL, MINIMAL }

/** A sphere of «Твой Титан» this week: share of days with a step, null when there is no data. */
data class SphereLight(val sphere: Sphere, val share: Float?, val lit: Boolean)

data class PathState(
    val today: LocalDate,
    val active: GoalProgress?,
    /** One proposal per measured sphere, the shortest lever first (A42 §3.2). */
    val proposals: List<GoalPlan>,
    val spheres: List<SphereLight>,
    /** Earlier goals, newest first: changing the goal keeps their weeks (A42 §3.2 п. 4). */
    val past: List<GoalProgress>,
    val stepVersion: StepVersion,
    /** Today counts for the active goal (from data or the user's tap). */
    val stepDone: Boolean,
    /** The user tapped today's step (can be undone; data marks cannot). */
    val stepMarked: Boolean,
) {
    val litSpheres: Int get() = spheres.count { it.lit }

    companion object {
        fun empty(today: LocalDate) = PathState(
            today = today,
            active = null,
            proposals = PathRules.proposals(PathEvidence(today)),
            spheres = Sphere.entries.map { SphereLight(it, null, false) },
            past = emptyList(),
            stepVersion = StepVersion.NORMAL,
            stepDone = false,
            stepMarked = false,
        )
    }
}
