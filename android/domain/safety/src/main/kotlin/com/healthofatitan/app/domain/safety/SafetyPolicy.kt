package com.healthofatitan.app.domain.safety

/**
 * The modules that read the bus (A60 §3.2). This slice wires the three tabs; the rest of A60
 * (rituals, pushes and widgets, ContextPack of Винс, supplements, food photo, SOS practices) join
 * with their slices — each one gets its column in [SafetyPolicy] the day it reads the bus.
 */
enum class SafetyConsumer { TODAY, WEEK, PATH }

/**
 * What a flag forbids a consumer (A58 §2 «коды запретов»). Codes, never the names of states: the
 * same codes go to Винс later (A60 §3.5, rule L-07).
 */
enum class Restriction {
    /** No celebration, no haiku, no playful tone: the sober version of the screen. */
    SOBER,

    /** «Помощь» (112 · 911) stands on the screen itself (A07 §4.3). */
    HELP_FIRST,

    /** Coaching and «Работа» stop (CrisisGuard ≥ 2, A07 §4.3). */
    NO_COACHING,

    /** No proactive push (the Sunday push of the week, A52 §2.7). */
    NO_PUSH,

    /** No skews of the week (W-04). */
    HIDE_SKEWS,

    /** The load of the day is taken off. */
    NO_LOAD,

    /** Intensity up to RPE 6 (`intensity>RPE6` in A60 §3.5). */
    INTENSITY_CAP,

    HIDE_CALORIES,
    HIDE_NUTRIENTS,

    /** Food chips, the screen and the talk about food, «сладкое» in cards (A60 §3.4). */
    HIDE_FOOD_TALK,
    HIDE_WEIGHT,
    HIDE_FOOD_WINDOWS,
    HIDE_BODY_PHOTO,
    NO_FITNESS_AGE,

    /** The Path holds the vault with one hand: Water «держим», no lever, no «Зачем» on top. */
    PATH_GENTLE,

    /** The calendar of the stage stands; the Titan of the horizon waits (P-9). */
    PATH_STANDS,
}

/**
 * The matrix «flag × consumer» (A60 §3.2). The rules of the bus:
 * 1. the strictest wins — restrictions of all active flags are united, never subtracted;
 * 2. a person can make it stricter, not softer (the journal accepts no «clear» of a flag set by the bus);
 * 3. an unknown flag means «forbid»: [strictest];
 * 4. every consumer has a policy for every flag: the `when` below is exhaustive, so a new flag
 *    without a policy does not compile and CI fails (L-05).
 *
 * **Черновик, ждёт ревью Агентов 3, 10 и 12:** the rows of the flags without a producer in this
 * slice. The rows of the crisis (A07 §4.3) and of `ED_CARE` (A60 §3.4) follow the artifacts.
 */
object SafetyPolicy {
    private val STOP = setOf(Restriction.SOBER, Restriction.HELP_FIRST, Restriction.NO_COACHING, Restriction.NO_PUSH)

    /** What `ED_CARE` hides everywhere (A60 §3.4, L-08). */
    val ED_HIDDEN: Set<Restriction> = setOf(
        Restriction.HIDE_CALORIES,
        Restriction.HIDE_NUTRIENTS,
        Restriction.HIDE_FOOD_TALK,
        Restriction.HIDE_WEIGHT,
        Restriction.HIDE_FOOD_WINDOWS,
        Restriction.HIDE_BODY_PHOTO,
    )

    fun of(consumer: SafetyConsumer, flag: SafetyFlag): Set<Restriction> = when (consumer) {
        SafetyConsumer.TODAY -> today(flag)
        SafetyConsumer.WEEK -> week(flag)
        SafetyConsumer.PATH -> path(flag)
    }

    /** Everything a consumer can forbid: what an unknown flag means (rule 3). */
    fun strictest(consumer: SafetyConsumer): Set<Restriction> = SafetyFlag.entries.flatMapTo(mutableSetOf()) { of(consumer, it) }

    private fun today(flag: SafetyFlag): Set<Restriction> = when (flag) {
        SafetyFlag.EMERGENCY, SafetyFlag.CRISIS_L2, SafetyFlag.CRISIS_L3 -> STOP
        SafetyFlag.SICK -> setOf(Restriction.NO_LOAD)
        SafetyFlag.PREGNANT, SafetyFlag.POSTPARTUM_12M -> setOf(Restriction.INTENSITY_CAP, Restriction.HIDE_WEIGHT)
        SafetyFlag.LACTATING -> setOf(Restriction.HIDE_WEIGHT)
        SafetyFlag.ED_CARE -> ED_HIDDEN
        SafetyFlag.MEDS_HR, SafetyFlag.CHRONIC, SafetyFlag.INJURY, SafetyFlag.SCREEN_NOT_CLEARED -> setOf(Restriction.INTENSITY_CAP)
        SafetyFlag.STORM -> setOf(Restriction.SOBER)
        SafetyFlag.GRIEF -> setOf(Restriction.SOBER, Restriction.NO_PUSH)
        SafetyFlag.MENOPAUSE, SafetyFlag.MEDS_PSY, SafetyFlag.AGE_65P, SafetyFlag.RETURN, SafetyFlag.LIGHT_WEEK,
        SafetyFlag.HEALTH_ANXIETY, SafetyFlag.CALIBRATION, SafetyFlag.MANUAL, SafetyFlag.ATYPICAL_PHYSIO, SafetyFlag.CYCLE,
        -> emptySet()
    }

    private fun week(flag: SafetyFlag): Set<Restriction> = when (flag) {
        SafetyFlag.EMERGENCY, SafetyFlag.CRISIS_L2, SafetyFlag.CRISIS_L3 -> STOP + Restriction.HIDE_SKEWS + Restriction.NO_FITNESS_AGE
        SafetyFlag.SICK, SafetyFlag.MEDS_HR, SafetyFlag.HEALTH_ANXIETY -> setOf(Restriction.NO_FITNESS_AGE)
        SafetyFlag.PREGNANT, SafetyFlag.POSTPARTUM_12M -> setOf(Restriction.NO_FITNESS_AGE, Restriction.HIDE_WEIGHT)
        SafetyFlag.LACTATING -> setOf(Restriction.HIDE_WEIGHT)
        SafetyFlag.ED_CARE -> ED_HIDDEN + Restriction.NO_FITNESS_AGE
        SafetyFlag.STORM -> setOf(Restriction.HIDE_SKEWS, Restriction.NO_FITNESS_AGE)
        SafetyFlag.GRIEF -> setOf(Restriction.SOBER, Restriction.NO_PUSH, Restriction.HIDE_SKEWS)
        SafetyFlag.MENOPAUSE, SafetyFlag.MEDS_PSY, SafetyFlag.CHRONIC, SafetyFlag.INJURY, SafetyFlag.SCREEN_NOT_CLEARED,
        SafetyFlag.AGE_65P, SafetyFlag.RETURN, SafetyFlag.LIGHT_WEEK, SafetyFlag.CALIBRATION, SafetyFlag.MANUAL,
        SafetyFlag.ATYPICAL_PHYSIO, SafetyFlag.CYCLE,
        -> emptySet()
    }

    private fun path(flag: SafetyFlag): Set<Restriction> = when (flag) {
        SafetyFlag.EMERGENCY, SafetyFlag.CRISIS_L2, SafetyFlag.CRISIS_L3 -> setOf(Restriction.SOBER, Restriction.PATH_GENTLE)
        SafetyFlag.SICK -> setOf(Restriction.PATH_STANDS)
        SafetyFlag.GRIEF -> setOf(Restriction.SOBER, Restriction.PATH_STANDS)
        SafetyFlag.STORM, SafetyFlag.LIGHT_WEEK -> setOf(Restriction.PATH_GENTLE)
        SafetyFlag.PREGNANT, SafetyFlag.POSTPARTUM_12M, SafetyFlag.LACTATING -> setOf(Restriction.HIDE_WEIGHT)
        SafetyFlag.ED_CARE -> setOf(Restriction.HIDE_WEIGHT, Restriction.HIDE_BODY_PHOTO, Restriction.HIDE_CALORIES)
        SafetyFlag.MENOPAUSE, SafetyFlag.MEDS_HR, SafetyFlag.MEDS_PSY, SafetyFlag.CHRONIC, SafetyFlag.INJURY,
        SafetyFlag.SCREEN_NOT_CLEARED, SafetyFlag.AGE_65P, SafetyFlag.RETURN, SafetyFlag.HEALTH_ANXIETY,
        SafetyFlag.CALIBRATION, SafetyFlag.MANUAL, SafetyFlag.ATYPICAL_PHYSIO, SafetyFlag.CYCLE,
        -> emptySet()
    }
}
