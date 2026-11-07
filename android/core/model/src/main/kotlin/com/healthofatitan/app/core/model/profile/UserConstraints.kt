package com.healthofatitan.app.core.model.profile

/**
 * The user's specifics (профиль особенностей, `content/profile/SCHEMA.md`): ONE profile for
 * nutrition (A41) and training (A40). Only closed codes of `content/profile/constraints.yaml`;
 * the meaning of every code lives in [ProfileDictionary]. What the user tells us, never a
 * diagnosis. Class S4 (A25 §6): kept only in the encrypted database, never in logs, analytics,
 * pushes or widgets; Claude gets exclusion codes only ([llmCodes]).
 *
 * @property allergies allergen code → how strict («избегаю / аллергия / тяжёлая реакция»)
 * @property intolerances `intolerances[].id` (lactose, caffeine, spicy…)
 * @property diets `diets[].id`; they combine (vegan + gluten_free)
 * @property lifeStage `life_stage[].id` (pregnancy, lactation, postpartum_6m)
 * @property medical `medical_flags[].id` (ed_history, diabetes, kidney…)
 * @property injuries `injury_presets[].id` (knee_injury, lower_back_pain…): read by training
 * @property dislikes component ids of `content/nutrition/components.yaml`, soft, ≤ 20
 */
data class UserConstraints(
    val allergies: Map<String, AllergySeverity> = emptyMap(),
    val intolerances: Set<String> = emptySet(),
    val diets: Set<String> = emptySet(),
    val lifeStage: Set<String> = emptySet(),
    val medical: Set<String> = emptySet(),
    val injuries: Set<String> = emptySet(),
    val dislikes: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = allergies.isEmpty() && intolerances.isEmpty() && diets.isEmpty() && lifeStage.isEmpty() &&
            medical.isEmpty() && injuries.isEmpty() && dislikes.isEmpty()

    /** True when a category holds anything (the profile screen shows «удалить» only then). */
    fun has(category: ProfileCategory): Boolean = codes(category).isNotEmpty()

    /** Codes of one category as stored; allergens as `code:severity`. */
    fun codes(category: ProfileCategory): List<String> = when (category) {
        ProfileCategory.ALLERGENS -> allergies.entries.sortedBy { it.key }.map { "${it.key}:${it.value.code}" }
        ProfileCategory.INTOLERANCES -> intolerances.sorted()
        ProfileCategory.DIETS -> diets.sorted()
        ProfileCategory.LIFE_STAGE -> lifeStage.sorted()
        ProfileCategory.MEDICAL -> medical.sorted()
        ProfileCategory.INJURIES -> injuries.sorted()
        ProfileCategory.DISLIKES -> dislikes.sorted()
    }

    /** The same profile with one category emptied («удалить по категории», Agent 7). */
    fun without(category: ProfileCategory): UserConstraints = when (category) {
        ProfileCategory.ALLERGENS -> copy(allergies = emptyMap())
        ProfileCategory.INTOLERANCES -> copy(intolerances = emptySet())
        ProfileCategory.DIETS -> copy(diets = emptySet())
        ProfileCategory.LIFE_STAGE -> copy(lifeStage = emptySet())
        ProfileCategory.MEDICAL -> copy(medical = emptySet())
        ProfileCategory.INJURIES -> copy(injuries = emptySet())
        ProfileCategory.DISLIKES -> copy(dislikes = emptySet())
    }

    /**
     * What may leave the phone for Claude (`storage.to_llm: codes_only`): allergen and diet codes
     * and a bare `safe_mode` flag. Medical flag names, life stage and injuries never leave.
     */
    fun llmCodes(): Map<String, Any> = mapOf(
        "exclude_allergens" to allergies.keys.sorted(),
        "diets" to diets.sorted(),
        "safe_mode" to (medical.isNotEmpty() || lifeStage.isNotEmpty()),
    )

    companion object {
        val NONE = UserConstraints()

        /** Rebuilds a profile from stored category codes (the inverse of [codes]). Unknown entries are dropped. */
        fun fromCodes(stored: Map<ProfileCategory, List<String>>): UserConstraints {
            fun set(c: ProfileCategory) = stored[c].orEmpty().filter { it.isNotBlank() }.toSet()
            val allergies = stored[ProfileCategory.ALLERGENS].orEmpty().mapNotNull { entry ->
                val id = entry.substringBefore(':').trim()
                val sev = AllergySeverity.of(entry.substringAfter(':', "allergy").trim()) ?: return@mapNotNull null
                if (id.isEmpty()) null else id to sev
            }.toMap()
            return UserConstraints(
                allergies = allergies,
                intolerances = set(ProfileCategory.INTOLERANCES),
                diets = set(ProfileCategory.DIETS),
                lifeStage = set(ProfileCategory.LIFE_STAGE),
                medical = set(ProfileCategory.MEDICAL),
                injuries = set(ProfileCategory.INJURIES),
                dislikes = set(ProfileCategory.DISLIKES),
            )
        }
    }
}

/** `allergy_severity` of constraints.yaml: how «может содержать» is treated. */
enum class AllergySeverity(val code: String) {
    /** «Избегаю»: may-contain is allowed with a note. */
    AVOID("avoid"),

    /** «Аллергия»: may-contain is excluded. */
    ALLERGY("allergy"),

    /** «Тяжёлая реакция»: may-contain excluded, restaurant warning, no generated meals. */
    ANAPHYLAXIS("anaphylaxis"),
    ;

    /** The next step of the one-tap cycle on the profile screen: none → avoid → allergy → anaphylaxis → none. */
    fun next(): AllergySeverity? = entries.getOrNull(ordinal + 1)

    companion object {
        fun of(code: String): AllergySeverity? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Categories of the profile: consent, storage and deletion go by category (Agent 7, A41 §6.5).
 * [key] is the stored id; never rename.
 */
enum class ProfileCategory(val key: String) {
    ALLERGENS("allergens"),
    INTOLERANCES("intolerances"),
    DIETS("diets"),
    LIFE_STAGE("life_stage"),
    MEDICAL("medical"),
    INJURIES("injuries"),
    DISLIKES("dislikes"),
    ;

    companion object {
        fun of(key: String): ProfileCategory? = entries.firstOrNull { it.key == key }
    }
}
