package com.healthofatitan.app.domain.nutrition

import com.healthofatitan.app.core.model.profile.AllergySeverity
import com.healthofatitan.app.core.model.profile.ProfileDictionary
import com.healthofatitan.app.core.model.profile.UserConstraints

/** `content/profile/SCHEMA.md` §3: the one mode the profile folds into (the strongest wins). */
enum class ProfileMode(val code: String) {
    NORMAL("normal"),
    FILTER_ONLY("filter_only"),
    SAFE("safe"),

    /** Eating-disorder care (A41 §3, §6.4): «Основа» only and no numbers anywhere. */
    ED_CARE("ed_care"),
    ;

    companion object {
        fun of(code: String): ProfileMode = when {
            code.startsWith("ed_care") -> ED_CARE
            else -> entries.firstOrNull { it.code == code } ?: SAFE
        }
    }
}

/**
 * The profile folded into hard exclusions, a mode and a level cap (A41 §7 layer 0). Applied
 * first; it only narrows. Mirrors `effective()` of `scripts/check_nutrition.py`.
 *
 * @property excludeAllergens never in a meal (allergies, diets, intolerances)
 * @property strictMayContain «может содержать» is excluded too (allergy, anaphylaxis, gluten-free, celiac)
 * @property noteMayContain «может содержать» is allowed with a note («избегаю»)
 * @property lactoseMax 0 none, 1 low, 2 high
 * @property consented false: no S4 consent, so no filter at all and «Основа» only (A41 §6.5)
 */
data class FoldedProfile(
    val consented: Boolean,
    val mode: ProfileMode,
    val levelCap: Level,
    val excludeAllergens: Set<String>,
    val strictMayContain: Set<String>,
    val noteMayContain: Set<String>,
    val excludeOrigins: Set<String>,
    val excludeTags: Set<String>,
    val lactoseMax: Int,
    val mealRules: Set<String>,
    val forbidMethods: Set<String>,
    val hints: List<String>,
    val hideNumbers: Boolean,
    val photoFeedbackOff: Boolean,
    val noGeneration: Boolean,
    val restaurantWarning: Boolean,
    val caffeineShiftH: Int,
    val preferTags: Set<String>,
    val dislikes: Set<String>,
    /** The allergens the user marked themselves: the «щит» shows exactly these. */
    val shieldAllergens: List<String>,
) {
    val safeMode: Boolean get() = mode == ProfileMode.SAFE || mode == ProfileMode.ED_CARE

    companion object {
        /** No consent (or nothing marked): no filters. */
        fun none(consented: Boolean) = FoldedProfile(
            consented = consented,
            mode = ProfileMode.NORMAL,
            levelCap = if (consented) Level.PRECISE else Level.BASE,
            excludeAllergens = emptySet(),
            strictMayContain = emptySet(),
            noteMayContain = emptySet(),
            excludeOrigins = emptySet(),
            excludeTags = emptySet(),
            lactoseMax = 2,
            mealRules = emptySet(),
            forbidMethods = emptySet(),
            hints = emptyList(),
            hideNumbers = false,
            photoFeedbackOff = false,
            noGeneration = false,
            restaurantWarning = false,
            caffeineShiftH = 0,
            preferTags = emptySet(),
            dislikes = emptySet(),
            shieldAllergens = emptyList(),
        )
    }
}

object ProfileFold {

    const val NO_MEAT_WITH_DAIRY = "no_meat_with_dairy"

    /**
     * Folds [profile] with the dictionary of `constraints.yaml`. Without [consented] the profile is
     * ignored entirely (A41 §6.5: «Основа» without filters and a warning).
     */
    fun fold(profile: UserConstraints, dict: ProfileDictionary, consented: Boolean): FoldedProfile {
        if (!consented) return FoldedProfile.none(consented = false)
        val exAll = linkedSetOf<String>()
        val strictMay = linkedSetOf<String>()
        val noteMay = linkedSetOf<String>()
        val exOrig = linkedSetOf<String>()
        val exTags = linkedSetOf<String>()
        val mealRules = linkedSetOf<String>()
        val forbid = linkedSetOf<String>()
        val hints = linkedSetOf<String>()
        val prefer = linkedSetOf<String>()
        var lactoseMax = 2
        var hide = false
        var photoOff = false
        var noGeneration = false
        var restaurant = false
        var caffeineShift = 0
        var cap = Level.PRECISE
        val modes = mutableListOf(ProfileMode.NORMAL)
        fun lower(code: String?) {
            Level.of(code)?.let { cap = cap.atMost(it) }
        }

        for ((id, severity) in profile.allergies) {
            // `wheat` implies `gluten` on the component side (validator rule), not here: rye stays.
            exAll += id
            when (severity) {
                AllergySeverity.AVOID -> noteMay += id
                AllergySeverity.ALLERGY -> strictMay += id
                AllergySeverity.ANAPHYLAXIS -> {
                    strictMay += id
                    noGeneration = true
                    restaurant = true
                }
            }
        }
        for (id in profile.intolerances) {
            val it = dict.intolerance(id) ?: continue
            it.lactoseMax?.let { m -> lactoseMax = minOf(lactoseMax, LACTOSE.indexOf(m).coerceAtLeast(0)) }
            exAll += it.excludeAllergens
            if (it.mayContainAllowed) noteMay += it.excludeAllergens
            exTags += it.excludeTags
            caffeineShift = minOf(caffeineShift, it.caffeineShiftH)
        }
        if ("chewing_swallowing" in profile.intolerances) prefer += "soft"

        val diets = profile.diets.toMutableList()
        for (id in profile.medical) {
            val f = dict.medical(id) ?: continue
            diets += f.impliesDiets
            if (f.mayContainGlutenExcluded) strictMay += "gluten"
            lower(f.maxLevel)
            forbid += f.forbidMethods
            hints += f.addHints
            hide = hide || f.hideNumbers
            photoOff = photoOff || f.photoFeedbackOff
            modes += ProfileMode.of(f.mode)
        }
        for (id in profile.lifeStage) {
            val f = dict.lifeStage(id) ?: continue
            lower(f.maxLevel)
            forbid += f.forbidMethods
            hints += f.addHints
            modes += ProfileMode.of(f.mode)
        }
        for (id in diets.distinct()) {
            val d = dict.diet(id) ?: continue
            exOrig += d.excludeOrigins
            exAll += d.excludeAllergens
            if (d.mayContainExcluded) strictMay += d.excludeAllergens
            d.mealRule?.let { mealRules += it }
        }
        val mode = modes.maxBy { it.ordinal }
        if (mode == ProfileMode.ED_CARE) {
            cap = Level.BASE
            hide = true
            photoOff = true
        }
        return FoldedProfile(
            consented = true,
            mode = mode,
            levelCap = cap,
            excludeAllergens = exAll,
            strictMayContain = strictMay,
            noteMayContain = noteMay - strictMay,
            excludeOrigins = exOrig,
            excludeTags = exTags,
            lactoseMax = lactoseMax,
            mealRules = mealRules,
            forbidMethods = forbid,
            hints = hints.toList(),
            hideNumbers = hide,
            photoFeedbackOff = photoOff,
            noGeneration = noGeneration,
            restaurantWarning = restaurant,
            caffeineShiftH = caffeineShift,
            preferTags = prefer,
            dislikes = profile.dislikes,
            shieldAllergens = (profile.allergies.keys.sorted() + exAll).distinct().filter { dict.allergen(it) != null },
        )
    }

    private val LACTOSE = listOf("none", "low", "high")
}
