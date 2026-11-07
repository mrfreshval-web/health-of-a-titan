package com.healthofatitan.app.core.model.profile

import com.healthofatitan.app.core.model.content.Tree
import com.healthofatitan.app.core.model.content.bool
import com.healthofatitan.app.core.model.content.obj
import com.healthofatitan.app.core.model.content.str
import com.healthofatitan.app.core.model.content.strings

/**
 * The closed lists of `content/profile/constraints.yaml` (compiled to
 * `assets/content/profile/constraints.json`): what each code of [UserConstraints] means. Shared by
 * nutrition and training; labels (`ru`) come from content only (Agent 10 reviews them).
 */
data class ProfileDictionary(
    val allergens: List<Allergen>,
    val intolerances: List<Intolerance>,
    val diets: List<Diet>,
    val lifeStages: List<SafetyFlag>,
    val medicalFlags: List<SafetyFlag>,
    val injuryPresets: List<InjuryPreset>,
    val movementLimits: Map<String, String>,
    /** `storage.disclaimer_ru`: shown under the allergen filter, always. */
    val disclaimer: String,
    /** True while `status: draft` (texts not yet reviewed by Agents 3, 10, 7). */
    val draft: Boolean,
) {
    fun allergen(id: String): Allergen? = allergens.firstOrNull { it.id == id }
    fun diet(id: String): Diet? = diets.firstOrNull { it.id == id }
    fun intolerance(id: String): Intolerance? = intolerances.firstOrNull { it.id == id }
    fun lifeStage(id: String): SafetyFlag? = lifeStages.firstOrNull { it.id == id }
    fun medical(id: String): SafetyFlag? = medicalFlags.firstOrNull { it.id == id }
    fun injury(id: String): InjuryPreset? = injuryPresets.firstOrNull { it.id == id }

    /**
     * For training (A40): every movement limit the profile implies — injury presets, plus
     * presets bound to a ticked life stage or medical flag (pregnancy, hypertension…).
     */
    fun movementLimits(profile: UserConstraints): Set<String> = injuryPresets
        .filter { p ->
            p.id in profile.injuries ||
                (p.lifeStage != null && p.lifeStage in profile.lifeStage) ||
                (p.medicalFlag != null && p.medicalFlag in profile.medical)
        }
        .flatMap { it.limits }
        .toSet()

    data class Allergen(val id: String, val ru: String, val implies: List<String>, val includesRu: String?)

    /** Filter of an intolerance; [lactoseMax] is `none | low | high`. */
    data class Intolerance(
        val id: String,
        val ru: String,
        val lactoseMax: String?,
        val excludeAllergens: List<String>,
        val excludeTags: List<String>,
        val mayContainAllowed: Boolean,
        val caffeineShiftH: Int,
        val medicalRoute: Boolean,
    )

    data class Diet(
        val id: String,
        val ru: String,
        val excludeOrigins: List<String>,
        val excludeAllergens: List<String>,
        val mayContainExcluded: Boolean,
        val mealRule: String?,
    )

    /**
     * A life stage or a medical flag: switches the product into a safe mode (`mode`: safe,
     * ed_care, ed_care_light, filter_only) with the nutrition gate below. [training] is kept as
     * the raw map of the YAML for the training engine.
     */
    data class SafetyFlag(
        val id: String,
        val ru: String,
        val mode: String,
        val maxLevel: String?,
        val forbidMethods: List<String>,
        val addHints: List<String>,
        val hideNumbers: Boolean,
        val mayContainGlutenExcluded: Boolean,
        val photoFeedbackOff: Boolean,
        val impliesDiets: List<String>,
        val training: Map<String, Tree>,
        val route: String?,
    )

    data class InjuryPreset(
        val id: String,
        val ru: String,
        val region: String?,
        val status: String?,
        val limits: List<String>,
        val lifeStage: String?,
        val medicalFlag: String?,
        val route: String?,
    )

    companion object {
        /** Reads the tree of constraints.yaml / constraints.json. */
        fun parse(root: Tree): ProfileDictionary {
            val r = root.obj()
            fun items(key: String) = r[key].let { v -> (v as? List<*>).orEmpty().map { it.obj() } }
            return ProfileDictionary(
                allergens = items("allergens").map {
                    Allergen(it.str("id").orEmpty(), it.str("ru").orEmpty(), it.strings("implies"), it.str("includes_ru"))
                },
                intolerances = items("intolerances").map {
                    val f = it.obj("filter")
                    Intolerance(
                        id = it.str("id").orEmpty(),
                        ru = it.str("ru").orEmpty(),
                        lactoseMax = f.str("component_lactose_max"),
                        excludeAllergens = f.strings("exclude_allergens"),
                        excludeTags = f.strings("exclude_tags"),
                        mayContainAllowed = it.str("may_contain") == "allowed_with_note",
                        caffeineShiftH = it.obj("rules").str("caffeine_cutoff_shift_h")?.toIntOrNull() ?: 0,
                        medicalRoute = it["medical_route"].bool(),
                    )
                },
                diets = items("diets").map {
                    Diet(
                        id = it.str("id").orEmpty(),
                        ru = it.str("ru").orEmpty(),
                        excludeOrigins = it.strings("exclude_origins"),
                        excludeAllergens = it.strings("exclude_allergens"),
                        mayContainExcluded = it.str("may_contain") == "excluded",
                        mealRule = it.str("meal_rule"),
                    )
                },
                lifeStages = items("life_stage").map(::flag),
                medicalFlags = items("medical_flags").map(::flag),
                injuryPresets = items("injury_presets").map {
                    InjuryPreset(
                        id = it.str("id").orEmpty(),
                        ru = it.str("ru").orEmpty(),
                        region = it.str("region"),
                        status = it.str("status"),
                        limits = it.strings("limits"),
                        lifeStage = it.str("life_stage"),
                        medicalFlag = it.str("medical_flag"),
                        route = it.str("route"),
                    )
                },
                movementLimits = items("movement_limits").associate { it.str("id").orEmpty() to it.str("ru").orEmpty() },
                disclaimer = r.obj("storage").str("disclaimer_ru").orEmpty(),
                draft = r.str("status") == "draft",
            )
        }

        private fun flag(it: Map<String, Tree>): SafetyFlag {
            val n = it.obj("nutrition")
            return SafetyFlag(
                id = it.str("id").orEmpty(),
                ru = it.str("ru").orEmpty(),
                mode = it.str("mode") ?: "safe",
                maxLevel = n.str("max_level"),
                forbidMethods = n.strings("forbid_methods"),
                addHints = n.strings("add_hints"),
                hideNumbers = n["hide_numbers"].bool(),
                mayContainGlutenExcluded = n.str("may_contain_gluten") == "excluded",
                photoFeedbackOff = n.str("photo_feedback") == "off_by_default",
                impliesDiets = it.strings("implies_diet"),
                training = it.obj("training"),
                route = it.str("route"),
            )
        }
    }
}
