package com.healthofatitan.app.domain.training

/**
 * Model of the training library (content/training/SCHEMA.md, A40 §4–§6). Pure data: the engine
 * never invents an exercise, it only picks, lightens and replaces what the experts wrote.
 */

/** A40 §2: three levels by the cost of effort, not by sport. [order] is the ladder. */
enum class Tier(val id: String, val ru: String, val order: Int) {
    CLASSIC("classic", "Классика", 0),
    ADVANCED("advanced", "Продвинутый", 1),
    EXTREME("extreme", "Экстрим", 2),
    ;

    companion object {
        fun of(id: String?): Tier? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The day regime of A09 §2 as the training layer sees it (`rules.vocab.regime`). The domain
 * modules do not depend on each other (A14 §7), so `:domain:engine.Regime` is mapped to this in
 * the feature layer.
 */
enum class DayMode(val ru: String) {
    BOOST("Разгон"),
    STEADY("Ровно"),
    GENTLE("Бережно"),
    ;

    val order: Int get() = when (this) {
        GENTLE -> 0
        STEADY -> 1
        BOOST -> 2
    }

    fun atMost(cap: DayMode): DayMode = if (order > cap.order) cap else this
}

enum class Measure { REPS, REPS_PER_SIDE, TIME, TIME_PER_SIDE, DISTANCE;

    val perSide: Boolean get() = this == REPS_PER_SIDE || this == TIME_PER_SIDE
    val timed: Boolean get() = this == TIME || this == TIME_PER_SIDE

    companion object {
        fun of(id: String?): Measure = when (id) {
            "reps_per_side" -> REPS_PER_SIDE
            "time" -> TIME
            "time_per_side" -> TIME_PER_SIDE
            "distance" -> DISTANCE
            else -> REPS
        }
    }
}

/** `vocab.category`; mobility, stretch and breathing carry the rule M-ALL. */
object Category {
    const val MOBILITY = "mobility"
    const val STRETCH = "stretch"
    const val BREATHING = "breathing"
    const val STRENGTH = "strength"
    const val CORE = "core"
    const val CARDIO = "cardio"
    const val POWER = "power"
    const val CONDITIONING = "conditioning"
    const val SKILL = "skill"

    /** Categories that ask RPE after the last set (A40 §7.3); mobility and stretching do not. */
    val EFFORT = setOf(STRENGTH, CORE, POWER, CONDITIONING, SKILL)
}

enum class BlockType(val id: String, val ru: String) {
    WARMUP("warmup", "Разминка"),
    MAIN("main", "Основная часть"),
    FINISHER("finisher", "Финишер"),
    COOLDOWN("cooldown", "Заминка"),
    ;

    companion object {
        fun of(id: String?): BlockType? = entries.firstOrNull { it.id == id }
    }
}

/** Parameters of one item: from the template, else from the exercise `default` (A40 §4.2 step 7). */
data class Dose(
    val sets: Int? = null,
    /** "8", "12-15", "6 каждой буквы" — the text the player shows. */
    val reps: String? = null,
    val timeS: Int? = null,
    val distanceM: Int? = null,
    val restS: Int? = null,
    /** eccentric-pause-concentric-pause, seconds: "3-0-1-0". */
    val tempo: String? = null,
    val rpe: String? = null,
    val zone: String? = null,
) {
    /** Template value wins over the exercise default, field by field. */
    fun over(default: Dose): Dose = Dose(
        sets = sets ?: default.sets,
        reps = reps ?: if (timeS != null || distanceM != null) null else default.reps,
        timeS = timeS ?: if (reps != null || distanceM != null) null else default.timeS,
        distanceM = distanceM ?: if (reps != null || timeS != null) null else default.distanceM,
        restS = restS ?: default.restS,
        tempo = tempo ?: default.tempo,
        rpe = rpe ?: default.rpe,
        zone = zone ?: default.zone,
    )

    companion object {
        fun of(m: Map<String, Any?>?): Dose = if (m == null) Dose() else Dose(
            sets = m.int("sets"),
            reps = m.str("reps"),
            timeS = m.int("time_s"),
            distanceM = m.int("distance_m"),
            restS = m.int("rest_s"),
            tempo = m.str("tempo"),
            rpe = m.str("rpe"),
            zone = m.str("zone"),
        )
    }
}

/** A key pose in words (`animation.poses[]`), the frames of [TitanPose] are keyed by [key]. */
data class PoseKey(val key: String, val ru: String)

data class ExerciseAnimation(val view: String, val loop: Boolean, val props: List<String>, val poses: List<PoseKey>)

data class Exercise(
    val id: String,
    val nameRu: String,
    val level: Tier,
    val category: String,
    val pattern: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val equipment: Set<String>,
    val place: Set<String>,
    val measure: Measure,
    val impact: String,
    val riskTags: Set<String>,
    val default: Dose,
    val techniqueRu: List<String>,
    val mistakesRu: List<String>,
    val cuesRu: List<String>,
    val breathingRu: String?,
    val contraindications: Set<String>,
    val regressions: List<String>,
    val progressions: List<String>,
    val swaps: List<String>,
    val animation: ExerciseAnimation,
    val retired: Boolean = false,
) {
    val isMobility: Boolean get() = category == Category.MOBILITY
    val isStretch: Boolean get() = category == Category.STRETCH
    val isBreathing: Boolean get() = category == Category.BREATHING
}

data class TemplateItem(
    val ex: String,
    val dose: Dose,
    val perSide: Boolean,
    val pair: String?,
    val alt: List<String>,
    val noteRu: String?,
)

data class TemplateBlock(
    val type: BlockType,
    val minutes: Int,
    val format: String,
    val rounds: Int?,
    val restBetweenRoundsS: Int?,
    val items: List<TemplateItem>,
)

/** What a template becomes in a regime (`modes`): as is, the lighter version, or another template. */
sealed interface ModeUse {
    data object Full : ModeUse
    data object Lighter : ModeUse
    data class Swap(val templateId: String) : ModeUse
}

/** `lighter` of a template: only less, never more; warm-up and cool-down are never dropped. */
data class Lighter(
    val setsDelta: Int = 0,
    val roundsDelta: Int = 0,
    val durationPct: Int = 0,
    val volumePct: Int = 0,
    val rpeMax: Int? = null,
    val zoneMax: String? = null,
    val dropBlocks: Set<BlockType> = emptySet(),
    val dropExercises: Set<String> = emptySet(),
)

data class Intensity(val rpe: String?, val zone: String?, val noteRu: String?)

data class Template(
    val id: String,
    val version: Int,
    val nameRu: String,
    /** classic · advanced · extreme · all (all = mobility, open to every level). */
    val level: String,
    val method: String,
    val goal: List<String>,
    val place: Set<String>,
    val equipment: Set<String>,
    val durationMin: Int,
    val intensity: Intensity,
    val modes: Map<DayMode, ModeUse>,
    val lighter: Lighter,
    val blocks: List<TemplateBlock>,
    val whyRu: String?,
    val contraindications: Set<String>,
) {
    val ref: String get() = "$id@$version"
    val tier: Tier? get() = Tier.of(level)
}

/** One `rules.profile_codes` entry: what a code forbids (A40 §8). */
data class ProfileCodeRule(
    val id: String,
    val forbidTags: Set<String> = emptySet(),
    val forbidPatterns: Set<String> = emptySet(),
    val forbidMethods: Set<String> = emptySet(),
    val rpeMax: Int? = null,
    val hideTraining: Boolean = false,
    val noProgram: Boolean = false,
    val maxLevel: Tier? = null,
    val route: String? = null,
    /** Where the code comes from in the shared profile (constraints.yaml), as written. */
    val from: Map<String, Any?> = emptyMap(),
)

/** `rules.regime_caps[mode]`: the ceiling the day sets (A40 §2.3). */
data class RegimeCap(
    /** null = all methods of the level. */
    val allowMethods: Set<String>?,
    val rpeMax: Map<Tier, Int>,
    val intervals: Boolean,
    val forbidTags: Set<String>,
    val zoneMax: String?,
)

/** `rules.player` defaults (A40 §7.2). */
data class PlayerDefaults(
    val restByCategory: Map<String, Int>,
    val restHeavyS: Int,
    val perSideSwitchS: Int,
    val countdownS: Int,
    val extendRestStepS: Int,
)

data class TrainingRules(
    val version: Int,
    val methods: Map<String, String>,
    val regimeCaps: Map<DayMode, RegimeCap>,
    val profileCodes: Map<String, ProfileCodeRule>,
    val player: PlayerDefaults,
    val mobilityTemplates: List<String>,
    val stopwords: List<String>,
)
