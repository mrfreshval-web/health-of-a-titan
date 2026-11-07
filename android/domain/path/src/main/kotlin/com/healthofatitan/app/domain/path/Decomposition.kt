package com.healthofatitan.app.domain.path

/**
 * What the ritual knows when it decomposes a goal (A54 R1–R8, A56 Э4).
 *
 * @param sufficiency distances to the thresholds of sufficiency (R5): only supporting components
 * @param noSleepNumbers the mode «без цифр сна»: sleep is never the lever (A54 §6, A55 §4)
 * @param sleepLeverStages stages in a row where sleep was the lever: after 2 it steps down (A55 §4)
 */
data class DecompositionInput(
    val template: GoalTemplate?,
    val chosen: List<Pair<ComponentKey, Role>> = emptyList(),
    val given: GivenLayer = GivenLayer.NONE,
    val taken: List<TakenItem> = emptyList(),
    val sufficiency: List<Sufficiency> = emptyList(),
    val flags: PathFlags = PathFlags.DEFAULT,
    val noSleepNumbers: Boolean = false,
    val sleepLeverStages: Int = 0,
)

/** The Water check (A54 §4 «правило равновесия»): improve Water only below its threshold. */
enum class WaterCheck { IMPROVE, HOLD }

/**
 * The decomposition the person approves on Э4.
 *
 * @param givenNotes «Колено бережём: без прыжков» — what «Дано» changed
 * @param startFrom the «Взято» source the ritual names («стартуем с весны 2025»), null without one
 * @param leverDeficit the lever's distance to its threshold, 0 when the lever is just the first lead
 */
data class Decomposition(
    val templateId: String,
    val components: List<GoalComponent>,
    val waterCheck: WaterCheck,
    val givenNotes: List<String>,
    val startFrom: TakenItem?,
    val leverDeficit: Double,
) {
    val lever: GoalComponent? get() = components.firstOrNull { it.isLever }
}

/**
 * R2–R6 of A54 §4, deterministic (P-07): the same input gives the same decomposition. No AI in
 * numbers: tables and fixed orders only.
 */
object PathDecomposer {
    const val MIN_COMPONENTS = 2
    const val MAX_COMPONENTS = 6
    const val MAX_LEADS = 2

    /** The water component HoT adds when a goal has none (P-03). */
    val WATER_GUARD = ComponentKey.SLEEP

    fun decompose(input: DecompositionInput): Decomposition {
        val base = input.template?.components ?: input.chosen
        val notes = mutableListOf<String>()
        // R2: the template or the person's choice; weight and strength stay behind the flag
        var parts = base
            .distinctBy { it.first }
            .filter { (k, _) -> input.flags.weightAndStrength || !k.weightOrStrength }
            .filter { (k, _) -> !(input.given.closesWeight && k == ComponentKey.BODY) }
            .map { (k, r) -> GoalComponent(k, r) }
        // R3: «Дано» — exclude or the safe version (P-05)
        parts = parts.mapNotNull { c ->
            val g = input.given.constraintOf(c.key) ?: return@mapNotNull c
            notes += g.label
            if (g.safeVersion != null) c.copy(safeVersion = g.safeVersion) else null
        }
        // P-03: Water in every goal; HoT adds the water guard itself
        if (parts.none { it.key.element == Element.WATER }) {
            val guard = Element.WATER.let { ComponentKey.of(it) }.first { input.given.constraintOf(it)?.let { g -> g.safeVersion != null } ?: true }
            parts = parts + GoalComponent(guard, Role.GUARD, safeVersion = input.given.constraintOf(guard)?.safeVersion)
        }
        // P-02: at most 2 leads; a goal without a lead takes its first component as the lead
        var leads = 0
        parts = parts.map { c ->
            if (c.role == Role.LEAD) {
                leads++
                if (leads > MAX_LEADS) c.copy(role = Role.PROP) else c
            } else {
                c
            }
        }
        if (parts.none { it.role == Role.LEAD } && parts.isNotEmpty()) {
            val first = parts.indexOfFirst { it.key.element != Element.WATER }.takeIf { it >= 0 } ?: 0
            parts = parts.mapIndexed { i, c -> if (i == first) c.copy(role = Role.LEAD) else c }
        }
        parts = parts.take(MAX_COMPONENTS)
        // R4: «Взято» gives the start mark; the target is one mark up (a step ≤ 30 %, P-06)
        val takenByKey = input.taken.groupBy { it.key }.mapValues { (_, v) -> v.maxByOrNull { it.mark ?: 0 } }
        parts = parts.map { c ->
            val start = takenByKey[c.key]?.mark?.takeIf { c.key.markable }
            c.copy(startMark = start, targetMark = start?.let { MarkRules.nextTarget(it) })
        }
        // R5: the lever
        val (lever, deficit) = lever(parts, input)
        parts = parts.map { it.copy(isLever = it.key == lever) }
        val water = if (input.sufficiency.any { it.key.element == Element.WATER && it.deficit > 0.0 && parts.any { p -> p.key == it.key } }) {
            WaterCheck.IMPROVE
        } else {
            WaterCheck.HOLD
        }
        val startFrom = parts.firstNotNullOfOrNull { c -> takenByKey[c.key] }
        return Decomposition(
            templateId = input.template?.id ?: PathTemplates.CUSTOM,
            components = parts,
            waterCheck = water,
            givenNotes = notes.distinct(),
            startFrom = startFrom,
            leverDeficit = deficit,
        )
    }

    /**
     * R5: the lever is the supporting component with the largest normalised deficit; a tie goes
     * to the earlier component. Without deficits it is the first lead. Sleep steps down in «без
     * цифр сна» and after 2 stages as the lever.
     */
    fun lever(parts: List<GoalComponent>, input: DecompositionInput): Pair<ComponentKey?, Double> {
        val sleepAllowed = !input.noSleepNumbers && input.sleepLeverStages < MAX_SLEEP_LEVER_STAGES
        val candidates = parts.filter { it.key != ComponentKey.SLEEP || sleepAllowed }
        val bySuff = input.sufficiency.associateBy { it.key }
        val best = candidates
            .filter { it.role != Role.LEAD }
            .mapNotNull { c -> bySuff[c.key]?.deficit?.takeIf { it > 0.0 }?.let { c to it } }
            .maxWithOrNull(compareBy<Pair<GoalComponent, Double>> { it.second }.thenByDescending { parts.indexOf(it.first) })
        if (best != null) return best.first.key to best.second
        val lead = candidates.firstOrNull { it.role == Role.LEAD } ?: candidates.firstOrNull()
        return lead?.key to 0.0
    }

    /** R6 (after the MVP, up to 3 goals): a shared component takes the senior of its roles. */
    fun sharedRole(roles: Collection<Role>): Role? = roles.minByOrNull { it.ordinal }

    const val MAX_SLEEP_LEVER_STAGES = 2
}

/** Marks (A54 §2): the step between targets is one mark (≤ 30 %), never above 5. */
object MarkRules {
    const val MIN = 1
    const val MAX = 5

    fun nextTarget(start: Int): Int = (start + 1).coerceIn(MIN, MAX)

    /**
     * Sleep mark from the nights of the last week that reached 7 hours (A54 §2.3, D-048 MVP).
     * [Оценка, ⚑ Agent 3]: thresholds from the personal start come after the MVP.
     */
    fun sleepMark(nightsOf7: Int): Int = when {
        nightsOf7 <= 1 -> 1
        nightsOf7 <= 3 -> 2
        nightsOf7 == 4 -> 3
        nightsOf7 <= 6 -> 4
        else -> 5
    }
}
