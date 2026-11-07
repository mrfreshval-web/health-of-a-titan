package com.healthofatitan.app.domain.nutrition

/** One swap the filter made: [from] is not allowed by the profile, [to] keeps the plate role. */
data class Swap(val from: Component, val to: Component)

/**
 * A meal that fits the profile, after swaps (A41 §6.1). Allergens are derived from [components],
 * never from the declared text of meals.yaml.
 *
 * @property mayContainNotes «может содержать» of allergens marked «избегаю»: shown, not excluded
 */
data class ResolvedMeal(
    val meal: Meal,
    val components: List<Pair<Component, Double>>,
    val swaps: List<Swap>,
    val mayContainNotes: Set<String>,
) {
    val allergens: Set<String> get() = components.flatMap { it.first.allergens }.toSet()
    val mayContain: Set<String> get() = components.flatMap { it.first.mayContain }.toSet() - allergens
}

/**
 * The hard filter (A41 §6.1, `content/profile/SCHEMA.md` §4): a forbidden component is replaced
 * by a swap of the same meal if one fits; otherwise the meal is not shown. The filter is never
 * relaxed for variety. Mirrors `resolve_meal()` of `scripts/check_nutrition.py`.
 */
object MealFilter {

    private val MEAT = setOf("meat_pork", "meat_beef", "meat_poultry", "meat_other")

    /** Why [c] cannot be on the plate of [p]; empty = allowed. */
    fun violations(c: Component, p: FoldedProfile): List<String> = buildList {
        (c.allergens intersect p.excludeAllergens).takeIf { it.isNotEmpty() }?.let { add("allergen $it") }
        (c.mayContain intersect p.strictMayContain).takeIf { it.isNotEmpty() }?.let { add("may contain $it") }
        (c.origin intersect p.excludeOrigins).takeIf { it.isNotEmpty() }?.let { add("origin $it") }
        if (c.lactose > p.lactoseMax) add("lactose")
        (c.tags intersect p.excludeTags).takeIf { it.isNotEmpty() }?.let { add("tag $it") }
    }

    fun allowed(c: Component, p: FoldedProfile): Boolean = violations(c, p).isEmpty()

    /** Kosher: no meat together with dairy in one meal. */
    fun mealRuleViolated(components: List<Component>, p: FoldedProfile): Boolean {
        if (ProfileFold.NO_MEAT_WITH_DAIRY !in p.mealRules) return false
        val origins = components.flatMap { it.origin }.toSet()
        return origins.any { it in MEAT } && "dairy" in origins
    }

    /** The meal for [p] after swaps, or null if it cannot be made safe. */
    fun resolve(meal: Meal, p: FoldedProfile, content: NutritionContent, useSwaps: Boolean = true): ResolvedMeal? {
        val out = mutableListOf<Pair<Component, Double>>()
        val swaps = mutableListOf<Swap>()
        for (part in meal.parts) {
            val c = content.component(part.component) ?: return null
            if (allowed(c, p)) {
                out += c to part.hands
                continue
            }
            if (!useSwaps) return null
            val alt = meal.swaps[c.id].orEmpty().firstNotNullOfOrNull { id -> content.component(id)?.takeIf { allowed(it, p) } }
                ?: return null
            out += alt to part.hands
            swaps += Swap(c, alt)
        }
        if (mealRuleViolated(out.map { it.first }, p)) return null
        val may = out.flatMap { it.first.mayContain }.toSet()
        return ResolvedMeal(meal, out, swaps, may intersect p.noteMayContain)
    }
}
