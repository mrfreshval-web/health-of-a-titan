package com.healthofatitan.app.domain.nutrition

import com.healthofatitan.app.core.model.content.Tree
import com.healthofatitan.app.core.model.content.bool
import com.healthofatitan.app.core.model.content.list
import com.healthofatitan.app.core.model.content.num
import com.healthofatitan.app.core.model.content.obj
import com.healthofatitan.app.core.model.content.str
import com.healthofatitan.app.core.model.content.strings
import com.healthofatitan.app.core.model.profile.ProfileDictionary

/** Nutrition levels (A41 §4): the user picks one; safety and the profile may only lower it. */
enum class Level(val code: String) {
    BASE("base"),
    STRUCTURED("structured"),
    PRECISE("precise"),
    ;

    fun atMost(cap: Level): Level = if (ordinal > cap.ordinal) cap else this

    companion object {
        fun of(code: String?): Level? = entries.firstOrNull { it.code == code }
    }
}

/** Load class of the day from training (A41 §8.2, A40 contract). */
enum class LoadClass(val code: String) {
    REST("rest"),
    LIGHT("light"),
    MODERATE("moderate"),
    HARD("hard"),
    LONG("long"),
    ;

    companion object {
        fun of(code: String?): LoadClass? = entries.firstOrNull { it.code == code }
    }
}

/** Plate roles of components.yaml (`roles`). */
object Roles {
    const val PROTEIN = "protein"
    const val STARCH = "starch"
    const val VEG = "veg"
    const val FRUIT = "fruit"
    const val FAT = "fat"
    const val EXTRA = "extra"
    const val DRINK = "drink"
}

/** components.yaml. [lactose]: 0 none, 1 low, 2 high. No energy field on purpose (A41 §3). */
data class Component(
    val id: String,
    val ru: String,
    val group: String,
    val role: String,
    val allergens: Set<String>,
    val mayContain: Set<String>,
    val origin: Set<String>,
    val lactose: Int,
    val tags: Set<String>,
)

data class MealPart(val component: String, val hands: Double)

/** meals.yaml: a meal is components + hand portions + slots + context + swaps. */
data class Meal(
    val id: String,
    val ru: String,
    val levels: Set<Level>,
    val slots: Set<String>,
    val plate: String,
    val parts: List<MealPart>,
    val context: List<String>,
    val prepMin: Int?,
    val swaps: Map<String, List<String>>,
    val tags: Set<String>,
    val retired: Boolean,
)

/** templates.yaml → plates: shares by role (sum 1.0) and hand portions for one main meal. */
data class Plate(val id: String, val ru: String, val share: Map<String, Double>, val hands: Map<String, Double>)

data class SlotDef(val id: String, val ru: String, val optional: Boolean)

data class TemplateSlot(
    val slot: String,
    val plate: String,
    val optional: Boolean,
    val mergeWithNextMeal: Boolean,
    /** `when: session.duration_min >= 75`. */
    val needsLongSession: Boolean,
)

data class DayTemplate(
    val id: String,
    val level: Level,
    val loads: Set<LoadClass>,
    val ru: String,
    val slots: List<TemplateSlot>,
    val methods: List<String>,
)

/** templates.yaml → overlays: they only simplify (never raise a level, plate or number). */
data class Overlay(
    val id: String,
    val ru: String,
    val plate: String?,
    val plateCap: String?,
    val preferTags: Set<String>,
    val preferContext: List<String>,
    val keepSlots: Set<String>,
    val maxPrepMin: Int?,
    val hideNumbers: Boolean,
    val hint: String?,
)

/** methods.yaml. [numbers]: none, time_only, grams_range, kcal_range. */
data class Method(
    val id: String,
    val ru: String,
    val fromLevel: Level,
    val default: String,
    val optIn: Boolean,
    val numbers: String,
    val blocked: Boolean,
    val evidence: String?,
    val whatRu: String?,
)

/** A rule of rules.yaml as the UI explains it: the id and `why_ru` (content only). */
data class RuleText(val id: String, val why: String, val evidence: String?)

/**
 * `content/nutrition` + `content/profile` read into memory (A41 §7). Built from the compiled
 * JSON assets on the phone and from the YAML in tests ([Tree]). [draft] = any file is still
 * `status: draft`: the UI marks its texts as pending review by Agents 3 and 10.
 */
data class NutritionContent(
    val profile: ProfileDictionary,
    val components: Map<String, Component>,
    val meals: List<Meal>,
    val plates: Map<String, Plate>,
    val slots: Map<String, SlotDef>,
    val templates: List<DayTemplate>,
    val overlays: Map<String, Overlay>,
    val methods: Map<String, Method>,
    val levels: Map<Level, String>,
    val rules: Map<String, RuleText>,
    val hints: Map<String, String>,
    val draft: Boolean,
) {
    fun component(id: String): Component? = components[id]

    fun plate(id: String): Plate = plates[id] ?: plates.getValue("base")

    fun slotRu(id: String): String = slots[id]?.ru ?: id

    fun why(ruleId: String): String? = rules[ruleId]?.why

    /** Exactly one template per level × load class (checked by check_nutrition.py and the tests). */
    fun template(level: Level, load: LoadClass): DayTemplate =
        templates.first { it.level == level && load in it.loads }

    companion object {
        /** Asset paths written by `contentCompile` (YAML → JSON, same tree). */
        val ASSETS: Map<String, String> = linkedMapOf(
            "constraints" to "content/profile/constraints.json",
            "components" to "content/nutrition/components.json",
            "meals" to "content/nutrition/meals.json",
            "templates" to "content/nutrition/templates.json",
            "methods" to "content/nutrition/methods.json",
            "rules" to "content/nutrition/rules.json",
        )

        /** Reads the six trees keyed as in [ASSETS]. */
        fun parse(trees: Map<String, Tree>): NutritionContent {
            val comps = trees["components"].obj()
            val meals = trees["meals"].obj()
            val tpl = trees["templates"].obj()
            val met = trees["methods"].obj()
            val rul = trees["rules"].obj()
            val draft = listOf(comps, meals, tpl, met, rul, trees["constraints"].obj()).any { it.str("status") == "draft" }

            return NutritionContent(
                profile = ProfileDictionary.parse(trees["constraints"]),
                components = comps.list("components").map { it.obj() }.associate { c ->
                    val id = c.str("id").orEmpty()
                    id to Component(
                        id = id,
                        ru = c.str("ru").orEmpty(),
                        group = c.str("group").orEmpty(),
                        role = c.str("role").orEmpty(),
                        allergens = c.strings("allergens").toSet(),
                        mayContain = c.strings("may_contain").toSet(),
                        origin = c.strings("origin").toSet(),
                        lactose = LACTOSE.indexOf(c.str("lactose") ?: "none").coerceAtLeast(0),
                        tags = c.strings("tags").toSet(),
                    )
                },
                meals = meals.list("meals").map { it.obj() }.map { m ->
                    Meal(
                        id = m.str("id").orEmpty(),
                        ru = m.str("ru").orEmpty(),
                        levels = m.strings("levels").mapNotNull(Level::of).toSet(),
                        slots = m.strings("slots").toSet(),
                        plate = m.str("plate") ?: "base",
                        parts = m.list("components").map { it.obj() }.map { MealPart(it.str("c").orEmpty(), it["n"].num() ?: 1.0) },
                        context = m.strings("context"),
                        prepMin = m["prep_min"].num()?.toInt(),
                        swaps = m.obj("swaps").mapValues { it.value.strings() },
                        tags = m.strings("tags").toSet(),
                        retired = m["retired"].bool(),
                    )
                },
                plates = tpl.obj("plates").mapValues { (id, v) ->
                    val p = v.obj()
                    Plate(
                        id = id,
                        ru = p.str("ru").orEmpty(),
                        share = p.obj("share").mapValues { it.value.num() ?: 0.0 },
                        hands = p.obj("hands").mapValues { it.value.num() ?: 0.0 },
                    )
                },
                slots = tpl.list("slots").map { it.obj() }.associate { s ->
                    val id = s.str("id").orEmpty()
                    id to SlotDef(id, s.str("ru").orEmpty(), s["optional"].bool())
                },
                templates = tpl.list("templates").map { it.obj() }.map { t ->
                    DayTemplate(
                        id = t.str("id").orEmpty(),
                        level = Level.of(t.str("level")) ?: Level.BASE,
                        loads = t.strings("load").mapNotNull(LoadClass::of).toSet(),
                        ru = t.str("ru").orEmpty(),
                        slots = t.list("slots").map { it.obj() }.map { s ->
                            TemplateSlot(
                                slot = s.str("slot").orEmpty(),
                                plate = s.str("plate") ?: "base",
                                optional = s["optional"].bool(),
                                mergeWithNextMeal = s["merge_with_next_meal"].bool(),
                                needsLongSession = s.str("when")?.contains("duration") == true,
                            )
                        },
                        methods = t.strings("methods"),
                    )
                },
                overlays = tpl.list("overlays").map { it.obj() }.associate { o ->
                    val id = o.str("id").orEmpty()
                    val e = o.obj("effect")
                    id to Overlay(
                        id = id,
                        ru = o.str("ru").orEmpty(),
                        plate = e.str("plate"),
                        plateCap = e.str("plate_cap"),
                        preferTags = e.strings("prefer_tags").toSet(),
                        preferContext = e.strings("prefer_context"),
                        keepSlots = e.strings("keep_slots").toSet(),
                        maxPrepMin = e["max_prep_min"].num()?.toInt(),
                        hideNumbers = e.str("numbers") == "hide",
                        hint = e.str("hint"),
                    )
                },
                methods = met.list("methods").map { it.obj() }.associate { m ->
                    val id = m.str("id").orEmpty()
                    id to Method(
                        id = id,
                        ru = m.str("ru").orEmpty(),
                        fromLevel = Level.of(m.str("from_level")) ?: Level.BASE,
                        default = m.str("default") ?: "on",
                        optIn = m["opt_in"].bool(),
                        numbers = m.str("numbers") ?: "none",
                        blocked = m.str("status") == "blocked",
                        evidence = m.str("evidence"),
                        whatRu = m.str("what_ru"),
                    )
                },
                levels = met.list("levels").map { it.obj() }.mapNotNull { l -> Level.of(l.str("id"))?.let { it to l.str("ru").orEmpty() } }.toMap(),
                rules = rul.list("rules").map { it.obj() }.associate { r ->
                    val id = r.str("id").orEmpty()
                    id to RuleText(id, r.str("why_ru").orEmpty(), r.str("evidence"))
                },
                hints = rul.obj("hints").mapValues { it.value.str().orEmpty() },
                draft = draft,
            )
        }

        private val LACTOSE = listOf("none", "low", "high")
    }
}
