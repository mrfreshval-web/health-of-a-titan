package com.healthofatitan.app.domain.path

import java.time.LocalDate

/**
 * «Путь» v1 (A54 v0.2 §10, A56 §1, D-046…D-048): one practical goal of life, decomposed into
 * components of three elements. Pure Kotlin; the app maps rows of `path_*` into these types.
 * Names shown to the person live in [PathLexicon] only.
 */
enum class Element { SKY, EARTH, WATER }

/**
 * The 14 components (D-047): Земля 5, Небо 4, Вода 5.
 *
 * @param hasMarks the component has a ladder of marks 1–5 (A54 §2); false for Топливо, Гибкость ума
 *   and Чувства (вето 10) — they are practice or a mirror, never a score (P-04)
 * @param marksInMvp the MVP shows marks only for Земля, Сон and Внимание (D-048 п. 3)
 * @param weightOrStrength a goal on it waits for the tables of safe pace (R7, D-047): behind
 *   [PathFlags.weightAndStrength]
 */
enum class ComponentKey(
    val id: String,
    val element: Element,
    val hasMarks: Boolean,
    val marksInMvp: Boolean,
    val weightOrStrength: Boolean = false,
) {
    CARDIO("cardio", Element.EARTH, hasMarks = true, marksInMvp = true),
    STRENGTH("strength", Element.EARTH, hasMarks = true, marksInMvp = true, weightOrStrength = true),
    MOBILITY("mobility", Element.EARTH, hasMarks = true, marksInMvp = true),
    BODY("body", Element.EARTH, hasMarks = true, marksInMvp = true, weightOrStrength = true),
    FUEL("fuel", Element.EARTH, hasMarks = false, marksInMvp = false),
    ATTENTION("attention", Element.SKY, hasMarks = true, marksInMvp = true),
    MASTERY("mastery", Element.SKY, hasMarks = true, marksInMvp = false),
    CLARITY("clarity", Element.SKY, hasMarks = true, marksInMvp = false),
    OUTLOOK("outlook", Element.SKY, hasMarks = false, marksInMvp = false),
    SLEEP("sleep", Element.WATER, hasMarks = true, marksInMvp = true),
    REST("rest", Element.WATER, hasMarks = true, marksInMvp = false),
    FEELINGS("feelings", Element.WATER, hasMarks = false, marksInMvp = false),
    PEOPLE("people", Element.WATER, hasMarks = true, marksInMvp = false),
    AFLOAT("afloat", Element.WATER, hasMarks = true, marksInMvp = false),
    ;

    /** A target mark can be set now (P-04 and the MVP scope). */
    val markable: Boolean get() = hasMarks && marksInMvp

    companion object {
        fun fromId(id: String?): ComponentKey? = entries.firstOrNull { it.id == id }

        fun of(element: Element): List<ComponentKey> = entries.filter { it.element == element }
    }
}

/**
 * What a criterion or a mark can be measured by (A54 §2, §10 `Metric`). [mirrorOnly] metrics —
 * physiology — are a mirror, never a goal or a mark (P-04, A54 §6 Агент 3).
 */
enum class PathMetric(val id: String, val component: ComponentKey, val mirrorOnly: Boolean = false) {
    SLEEP_NIGHTS("sleep_nights", ComponentKey.SLEEP),
    SLEEP_REGULARITY("sleep_regularity", ComponentKey.SLEEP),
    SLEEP_STAGES("sleep_stages", ComponentKey.SLEEP, mirrorOnly = true),
    HRV("hrv", ComponentKey.REST, mirrorOnly = true),
    RESTING_HR("resting_hr", ComponentKey.REST, mirrorOnly = true),
    SPO2("spo2", ComponentKey.REST, mirrorOnly = true),
    SKIN_TEMP("skin_temp", ComponentKey.REST, mirrorOnly = true),
    REST_EVENINGS("rest_evenings", ComponentKey.REST),
    BREATHERS("breathers", ComponentKey.REST),
    FOCUS_BLOCKS("focus_blocks", ComponentKey.ATTENTION),
    SKILL_SESSIONS("skill_sessions", ComponentKey.MASTERY),
    HARD_TALKS("hard_talks", ComponentKey.PEOPLE),
    WARM_CONTACTS("warm_contacts", ComponentKey.PEOPLE),
    ZONE_MINUTES("zone_minutes", ComponentKey.CARDIO),
    RUNS("runs", ComponentKey.CARDIO),
    STRENGTH_SESSIONS("strength_sessions", ComponentKey.STRENGTH),
    PULLUPS("pullups", ComponentKey.STRENGTH),
    BALANCE_SESSIONS("balance_sessions", ComponentKey.MOBILITY),
    WAIST("waist", ComponentKey.BODY),
    MILESTONES("milestones", ComponentKey.CLARITY),
    ;

    companion object {
        fun fromId(id: String?): PathMetric? = entries.firstOrNull { it.id == id }
    }
}

/** The role of a component in a goal (A56 §1): ведёт · подпирает · бережёт. Seniority is the order. */
enum class Role { LEAD, PROP, GUARD }

/**
 * The 8 states of a goal (A56 §5). Migration from v10: `REPLACED` → [RELEASED], `DONE` → [TAKEN].
 */
enum class GoalStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    HELD,
    REVIEW,
    TAKEN,
    RELEASED,
    QUEUED,
    ;

    companion object {
        /** Stored value → status; the v10 names still read (the migration rewrites them too). */
        fun parse(value: String?): GoalStatus? = when (value) {
            "REPLACED" -> RELEASED
            "DONE" -> TAKEN
            else -> entries.firstOrNull { it.name == value }
        }
    }
}

/** Where a goal is seen (P-22): exactly one layer by its status. */
enum class GoalLayer { PATH, CARRYING, TAKEN, NONE }

enum class GoalKind { BE, DO, HAVE }

/** «к сроку» keeps the date and lowers the bar; «целиком» keeps the bar, the date is computed. */
enum class GoalMode { BY_DATE, FULL }

/** Who made a change (A56 §1 `GoalVersion.initiator`). */
enum class Initiator { PERSON, HOT, MENTOR }

/**
 * A checkable criterion «что будет правдой к сроку», always a range (P-06): «талия −4…−6 см».
 * [low] and [high] are amounts of change or counts per week; [low] < [high].
 */
data class Criterion(
    val label: String,
    val low: Double,
    val high: Double,
    val unit: String,
    val metric: PathMetric? = null,
)

/** «Что встанет на пути» → «если — то» (WOOP, A56 Э5). */
data class Obstacle(val text: String, val ifThen: String)

/**
 * A component in a goal (A56 §1 `GoalComponent`).
 *
 * @param startMark the mark at the start (from «Взято» or a test); null = «пока не знаю»
 * @param safeVersion the safe version by «Дано» («без прыжков»), P-05
 * @param isLever the lever («рычаг», the weakest link): exactly one per person (P-02)
 */
data class GoalComponent(
    val key: ComponentKey,
    val role: Role,
    val targetMark: Int? = null,
    val startMark: Int? = null,
    val safeVersion: String? = null,
    val isLever: Boolean = false,
)

/** Why the person paused the path (A56 §3). Words in [PathCopy.pauseReason]. */
enum class PauseReason(val id: String) {
    UNWELL("unwell"),
    KIDS_SICK("kids_sick"),
    GRIEF("grief"),
    VACATION("vacation"),
    TOO_MUCH("too_much"),
    PRIVATE("private"),
    ;

    /** «На плаву» does not count in illness, grief and vacation (P-16, A54 §2.3). */
    val freezesAfloat: Boolean get() = this == UNWELL || this == KIDS_SICK || this == GRIEF || this == VACATION

    /** In grief HoT asks no term and shows no return card (A56 §3). */
    val asksTerm: Boolean get() = this != GRIEF

    companion object {
        fun fromId(id: String?): PauseReason? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The practical goal (A56 §1 `PracticalGoal`).
 *
 * @param start the first day of the current stage
 * @param pausedDays days of closed pauses in the current stage: milestones move by them (P-16)
 * @param pauseFrom the first day of the current pause or hold
 * @param takenAt when «Беру путь» was tapped (epoch ms): the 7 days of calibration start here (P-08)
 */
data class PracticalGoal(
    val id: Long,
    val title: String,
    val why: String? = null,
    val beName: String? = null,
    val kind: GoalKind = GoalKind.DO,
    val templateId: String? = null,
    val start: LocalDate,
    val deadline: LocalDate,
    /** The stage of 12 weeks the goal is in, from 1 (A56 §3 «Конец этапа»). */
    val stage: Int = 1,
    val mode: GoalMode = GoalMode.BY_DATE,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val criteria: List<Criterion> = emptyList(),
    val obstacle: Obstacle? = null,
    val components: List<GoalComponent> = emptyList(),
    val version: Int = 1,
    val pausedDays: Int = 0,
    val pauseFrom: LocalDate? = null,
    val pauseReason: PauseReason? = null,
    val pauseUntil: LocalDate? = null,
    val takenAt: Long = 0L,
    /** Released goals go to «Несу» unless the person switched it off (A56 §8 п. 3). */
    val releaseToQueue: Boolean = true,
) {
    val lever: GoalComponent? get() = components.firstOrNull { it.isLever }

    fun component(key: ComponentKey): GoalComponent? = components.firstOrNull { it.key == key }

    fun has(key: ComponentKey): Boolean = component(key) != null
}

/**
 * A line of «Дано» (A54 §2.4): a hard filter, not a component. A constrained component is excluded
 * or gets [safeVersion] (P-05); no change removes it.
 */
data class GivenConstraint(
    val id: String,
    val label: String,
    val affects: Set<ComponentKey>,
    val safeVersion: String?,
)

/** «Дано» of the person: constraints and the flags that close goals (A56 §2.2). */
data class GivenLayer(
    val constraints: List<GivenConstraint> = emptyList(),
    /** `ed_care` of the food profile (D-043): no goals on weight, «Форма» and «Топливо» hidden. */
    val edCare: Boolean = false,
    /** Pregnancy, breastfeeding, medical flags: no weight goals, load by ACOG. */
    val pregnancyOrMedical: Boolean = false,
) {
    fun constraintOf(key: ComponentKey): GivenConstraint? = constraints.firstOrNull { key in it.affects }

    val closesWeight: Boolean get() = edCare || pregnancyOrMedical

    companion object {
        val NONE = GivenLayer()
    }
}

/**
 * «Взято» of a component (A47, A54 §3): the start mark and the personal pace, with the source
 * the ritual names («стартуем с весны 2025»).
 */
data class TakenItem(val key: ComponentKey, val mark: Int?, val since: String, val pacePerWeek: Double? = null)

/** «Несу» (A47): a candidate for the ritual's first screen and its obstacle (A56 §2.1). */
data class CarryingItem(val text: String, val key: ComponentKey? = null)

/** Feature flags of the path. Weight and strength goals stay off until the safe pace tables (R7, D-047). */
data class PathFlags(val weightAndStrength: Boolean = false) {
    companion object {
        val DEFAULT = PathFlags()
    }
}

/**
 * How far a supporting component is from the threshold of sufficiency (A54 R5): «сон 6,1 ч при
 * пороге 7 ч». Only behaviour, never a mirror metric.
 */
data class Sufficiency(val key: ComponentKey, val now: Double, val threshold: Double) {
    /** Normalised deficit 0…1: 0 when the threshold is reached. */
    val deficit: Double get() = if (threshold <= 0.0) 0.0 else ((threshold - now) / threshold).coerceIn(0.0, 1.0)
}
