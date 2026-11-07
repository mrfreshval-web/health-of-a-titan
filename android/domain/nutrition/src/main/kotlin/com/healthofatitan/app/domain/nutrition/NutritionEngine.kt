package com.healthofatitan.app.domain.nutrition

import java.time.LocalDate

/** Regime of the day from the day engine (A09 §2): «Разгон / Ровно / Бережно». */
enum class DayMode { RAZGON, RAVNO, BEREZHNO }

/** Flags of the day (A09 §2) and the circumstance chips of «Сегодня» (A41 §10). */
enum class DayCondition { SICK, TRAVEL, LIGHT_WEEK, NO_TIME }

/** The evening one-tap mark (A41 §10, A25 FUEL ①). [code] is stored; never rename. */
enum class EveningMark(val code: String) {
    PLATE("plate"),
    PARTLY("partly"),
    ATE("ate"),
    ;

    companion object {
        fun of(code: String?): EveningMark? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Everything one day of food needs. Pure data: the same input always gives the same plan.
 * Times are minutes from 00:00 of the day ([bedtimeMin] may be ≥ 1440).
 *
 * @property replaced how many times «Заменить» was tapped per slot today (rotates the choice)
 */
data class NutritionDayInput(
    val dayKey: LocalDate,
    val chosenLevel: Level,
    val profile: FoldedProfile,
    val mode: DayMode = DayMode.RAVNO,
    val conditions: Set<DayCondition> = emptySet(),
    val loadToday: LoadClass = LoadClass.REST,
    val loadTomorrow: LoadClass? = null,
    val wakeMin: Int = 7 * 60,
    val bedtimeMin: Int = 23 * 60,
    val trainingStartMin: Int? = null,
    val trainingMinutes: Int? = null,
    val sleepHours: Double? = null,
    val replaced: Map<String, Int> = emptyMap(),
)

/** One line of «почему» with its rule id (texts are `why_ru` of rules.yaml, never generated). */
data class WhyLine(val ruleId: String, val text: String)

/**
 * A meal of the day on the line of the day.
 *
 * @property timeMin null when numbers are hidden (ed_care) — the order stays
 * @property hands hand portions by role; null when numbers are hidden
 * @property afterTraining this meal is also the meal after training (`merge_with_next_meal`)
 * @property meal null = the universal plate of allowed components (no library meal fits)
 * @property options how many library meals fit this slot today
 */
data class PlannedSlot(
    val slot: String,
    val ru: String,
    val timeMin: Int?,
    val plate: Plate,
    val hands: Map<String, Double>?,
    val optional: Boolean,
    val afterTraining: Boolean,
    val meal: ResolvedMeal?,
    val options: Int,
)

/**
 * The food of one day (A41 §10 «Сегодня»).
 *
 * @property lowered the level shown is below the chosen one (safety or the profile): «твой уровень на месте»
 * @property plateOfDay the plate the day is named after («Рабочий день»)
 * @property caffeineCutoffMin NR-21; null when numbers are hidden
 * @property methods methods shown today: not blocked, not forbidden by the profile, no numbers when hidden
 */
data class NutritionDayPlan(
    val dayKey: LocalDate,
    val chosenLevel: Level,
    val level: Level,
    val lowered: Boolean,
    val mode: ProfileMode,
    val template: DayTemplate,
    val plateOfDay: Plate,
    val overlays: List<Overlay>,
    val slots: List<PlannedSlot>,
    val why: List<WhyLine>,
    val hints: List<String>,
    val hideNumbers: Boolean,
    val caffeineCutoffMin: Int?,
    val methods: List<String>,
    val consented: Boolean,
    val shieldAllergens: List<String>,
    val restaurantWarning: Boolean,
)

/**
 * A41 §9: the deterministic food engine. Profile fold → template by level × load → rules and
 * overlays in priority order (safety → regime → sleep → load → context) → slots on the anchors of
 * the day → meals: filter + swaps → level and slot → rule tags and context → dislikes → choice.
 * No network, no LLM, no energy numbers (A41 §3, D-032: `energy_reference` stays blocked).
 */
object NutritionEngine {

    private val MAIN = listOf("breakfast", "lunch", "dinner")
    private val PLATE_ORDER = listOf("easy", "base", "moderate", "hard")
    private val SITUATIONAL = setOf(
        "after_short_sleep", "sick_day", "tomorrow_long", "travel", "gentle", "soft",
        "pre_training", "during_training", "post_training", "high_load", "low_load",
    )
    private val ROAD = setOf("travel", "airport", "fastfood", "outdoor")

    fun plan(input: NutritionDayInput, content: NutritionContent): NutritionDayPlan {
        val p = input.profile
        val why = mutableListOf<WhyLine>()
        val hints = linkedSetOf<String>()
        val overlays = mutableListOf<Overlay>()
        fun fire(id: String) {
            content.why(id)?.let { why += WhyLine(id, it) }
        }
        fun overlay(id: String) {
            content.overlays[id]?.let { o ->
                overlays += o
                o.hint?.let { h -> content.hints[h]?.let(hints::add) }
            }
        }

        // ---- 1. Safety and profile (always first)
        val level = input.chosenLevel.atMost(p.levelCap)
        if (p.mode == ProfileMode.ED_CARE) {
            fire("NR-01")
            overlay("OV-ed-care")
        } else if (p.mode == ProfileMode.SAFE) {
            fire("NR-02")
            overlay("OV-safe")
        }
        p.hints.forEach { h -> content.hints[h]?.let(hints::add) }
        val sick = DayCondition.SICK in input.conditions
        if (sick) {
            fire("NR-03")
            overlay("OV-sick")
        }
        val hideNumbers = p.hideNumbers
        val hideHands = hideNumbers || overlays.any { it.hideNumbers }

        // ---- 2. Template by level × load, the regime of the day
        val template = content.template(level, input.loadToday)
        val gentle = input.mode == DayMode.BEREZHNO
        if (gentle) {
            fire("NR-10")
            overlay("OV-gentle")
        }
        val heavy = input.loadToday == LoadClass.HARD || input.loadToday == LoadClass.LONG
        val boostFuel = input.mode == DayMode.RAZGON && heavy
        if (boostFuel) fire("NR-11")
        if (DayCondition.LIGHT_WEEK in input.conditions) {
            fire("NR-12")
            content.hints["H-LIGHTWEEK"]?.let(hints::add)
        }

        // ---- 3. Sleep
        val shortNight = input.sleepHours != null && input.sleepHours < 6.0
        if (shortNight) fire("NR-20")
        val caffeineShift = if (p.caffeineShiftH < 0) 11 else 9
        val caffeine = (input.bedtimeMin - caffeineShift * 60).takeIf { !hideNumbers }
        if (caffeine != null) fire("NR-21")

        // ---- 4. Training load
        val start = input.trainingStartMin
        val minutes = input.trainingMinutes ?: 0
        val trains = input.loadToday != LoadClass.REST && start != null
        val end = if (start != null) start + minutes else null
        val longSession = minutes >= 75 && level != Level.BASE
        if (trains) fire("NR-31")
        if (trains && longSession) fire("NR-32")
        val tomorrowHeavy = input.loadTomorrow == LoadClass.HARD || input.loadTomorrow == LoadClass.LONG
        val dinnerStarchPlus = tomorrowHeavy && level == Level.STRUCTURED
        if (dinnerStarchPlus) {
            fire("NR-34")
            content.hints["H-FUEL-TOMORROW"]?.let(hints::add)
        }
        val dinnerHardForLong = input.loadTomorrow == LoadClass.LONG && level == Level.PRECISE
        if (dinnerHardForLong) {
            fire("NR-35")
            content.hints["H-FUEL-TOMORROW"]?.let(hints::add)
        }
        val lateTraining = end != null && end >= input.bedtimeMin - 120
        if (lateTraining) {
            fire("NR-37")
            content.hints["H-LATE-TRAINING"]?.let(hints::add)
        }

        // ---- 5. Context
        if (DayCondition.TRAVEL in input.conditions) {
            fire("NR-40")
            overlay("OV-travel")
        }
        if (DayCondition.NO_TIME in input.conditions) {
            fire("NR-41")
            overlay("OV-no-time")
        }

        // ---- Slots on the anchors of the day
        val wake = input.wakeMin
        val breakfastAt = wake + 60
        val dinnerAt = input.bedtimeMin - 150
        val lunchAt = minOf(breakfastAt + 300, (wake + input.bedtimeMin) / 2).roundTo(15)
        val drafts = mutableListOf<Draft>()
        val templateSlots = template.slots.toMutableList()
        if (trains && templateSlots.none { it.slot == "post_training" }) {
            templateSlots += TemplateSlot("post_training", mainPlate(template), optional = false, mergeWithNextMeal = true, needsLongSession = false)
        }
        if (boostFuel && templateSlots.none { it.slot == "pre_training" }) {
            templateSlots += TemplateSlot("pre_training", "snack", optional = false, mergeWithNextMeal = false, needsLongSession = false)
        }
        if (trains && longSession && templateSlots.none { it.slot == "during_training" }) {
            templateSlots += TemplateSlot("during_training", "snack", optional = false, mergeWithNextMeal = false, needsLongSession = true)
        }
        for (ts in templateSlots) {
            var plate = ts.plate
            var optional = ts.optional
            val at: Int = when (ts.slot) {
                "breakfast" -> breakfastAt
                "lunch" -> lunchAt
                "dinner" -> dinnerAt
                "snack" -> ((lunchAt + dinnerAt) / 2).roundTo(15)
                "evening_snack" -> input.bedtimeMin - 90
                "pre_training" -> {
                    if (start == null) continue
                    val t = start - if (plate == "snack") 45 else 120
                    if (t < breakfastAt + 45) continue // breakfast is the meal before
                    if (t - lunchAt in 0..74) continue // lunch is the meal before
                    t
                }
                "during_training" -> {
                    if (start == null || !longSession) continue
                    start + minutes / 2
                }
                "post_training" -> {
                    if (end == null) continue
                    if (boostFuel) plate = "hard"
                    end + 30
                }
                else -> continue
            }
            if (ts.needsLongSession && !(start != null && minutes >= 75)) continue
            if (ts.slot == "pre_training" && boostFuel) optional = false
            drafts += Draft(ts.slot, at, plate, optional, ts.mergeWithNextMeal)
        }
        // Rule plates
        drafts.replaceAll { d ->
            when {
                d.slot == "dinner" && dinnerHardForLong -> d.copy(plate = "hard")
                d.slot == "dinner" && lateTraining -> d.copy(plate = "moderate")
                else -> d
            }
        }
        // post_training merges with the next main meal within 2 h (A41 templates: merge_with_next_meal)
        drafts.firstOrNull { it.slot == "post_training" && it.merge }?.let { post ->
            // NR-37: after a late session dinner and recovery are one meal, whatever the clock says.
            val next = if (lateTraining) {
                drafts.firstOrNull { it.slot == "dinner" }
            } else {
                drafts.filter { it.slot in MAIN && it.at >= post.at - 30 && it.at - post.at <= 120 }.minByOrNull { it.at }
            }
            if (next != null) {
                drafts.remove(post)
                drafts[drafts.indexOf(next)] = next.copy(
                    at = maxOf(next.at, post.at),
                    plate = maxPlate(next.plate, post.plate),
                    afterTraining = true,
                )
            }
        }
        // Overlays only simplify: one plate for a sick day, a cap on a gentle day, main meals kept.
        val forcePlate = overlays.firstNotNullOfOrNull { it.plate }
        val cap = overlays.mapNotNull { it.plateCap }.minByOrNull { PLATE_ORDER.indexOf(it) }
        val keep = overlays.flatMap { it.keepSlots }.toSet()
        drafts.replaceAll { d ->
            var plate = d.plate
            if (forcePlate != null && plate != "snack") plate = forcePlate
            if (cap != null && plate != "snack" && PLATE_ORDER.indexOf(plate) > PLATE_ORDER.indexOf(cap)) plate = cap
            d.copy(plate = plate, optional = d.optional && d.slot !in keep)
        }
        drafts.sortBy { it.at }

        // ---- Meals
        val preferTags = overlays.flatMap { it.preferTags }.toSet() + p.preferTags
        val preferContext = overlays.flatMap { it.preferContext }.toSet()
        val maxPrep = overlays.mapNotNull { it.maxPrepMin }.minOrNull()
        val used = mutableSetOf<String>()
        val slots = drafts.map { d ->
            val slotTags = buildSet {
                addAll(preferTags)
                if (d.slot == "breakfast" && shortNight) addAll(listOf("after_short_sleep", "gentle"))
                if (d.slot == "pre_training") addAll(listOf("pre_training", "quick"))
                if (d.slot == "post_training" || d.afterTraining) add("post_training")
                if (d.slot == "during_training") add("during_training")
                if (d.slot == "dinner" && dinnerHardForLong) addAll(listOf("tomorrow_long", "fuel"))
                if (heavy) add("high_load")
            }
            val pool = candidates(d, level, p, content, input.dayKey, slotTags, preferContext, maxPrep)
            val fresh = pool.filter { it.meal.id !in used }.ifEmpty { pool }
            val k = input.replaced[d.slot] ?: 0
            val pick = fresh.getOrNull(if (fresh.isEmpty()) 0 else k % fresh.size)
            pick?.let { used += it.meal.id }
            val plate = content.plate(d.plate)
            val hands = if (hideHands) {
                null
            } else {
                plate.hands.toMutableMap().also { h ->
                    if (d.slot == "dinner" && dinnerStarchPlus) h[Roles.STARCH] = (h[Roles.STARCH] ?: 0.0) + 1.0
                }
            }
            PlannedSlot(
                slot = d.slot,
                ru = content.slotRu(d.slot),
                timeMin = d.at.takeIf { !hideNumbers },
                plate = plate,
                hands = hands,
                optional = d.optional,
                afterTraining = d.afterTraining,
                meal = pick,
                options = pool.size,
            )
        }

        val methods = template.methods.filter { id ->
            val m = content.methods[id] ?: return@filter false
            !m.blocked && id !in p.forbidMethods && m.fromLevel.ordinal <= level.ordinal &&
                (!hideNumbers || m.numbers == "none" || m.numbers == "time_only") &&
                m.numbers != "kcal_range"
        }
        val plateOfDay = content.plate(
            forcePlate ?: slots.firstOrNull { it.slot == "lunch" }?.plate?.id ?: mainPlate(template),
        )
        return NutritionDayPlan(
            dayKey = input.dayKey,
            chosenLevel = input.chosenLevel,
            level = level,
            lowered = level != input.chosenLevel,
            mode = p.mode,
            template = template,
            plateOfDay = plateOfDay,
            overlays = overlays,
            slots = slots,
            why = why.distinctBy { it.ruleId },
            hints = hints.toList(),
            hideNumbers = hideNumbers,
            caffeineCutoffMin = caffeine,
            methods = methods,
            consented = p.consented,
            shieldAllergens = p.shieldAllergens,
            restaurantWarning = p.restaurantWarning,
        )
    }

    /**
     * Library meals for a slot, best first: the profile filter (with swaps) at the day's level,
     * then the neighbouring level if nothing fits; scored by rule tags, context, prep time and
     * dislikes; ties broken by a stable hash of the day so the plan varies day to day but is
     * reproducible.
     */
    fun candidates(
        d: Draft,
        level: Level,
        p: FoldedProfile,
        content: NutritionContent,
        day: LocalDate,
        preferTags: Set<String>,
        preferContext: Set<String>,
        maxPrep: Int?,
    ): List<ResolvedMeal> {
        val slotKey = if (d.slot == "post_training" || d.slot == "evening_snack") setOf(d.slot, "snack") else setOf(d.slot)
        fun pool(levels: Set<Level>) = content.meals.asSequence()
            .filter { !it.retired && it.levels.any { l -> l in levels } && it.slots.any { s -> s in slotKey } }
            .mapNotNull { MealFilter.resolve(it, p, content) }
            .toList()
        var pool = pool(setOf(level))
        if (pool.isEmpty()) pool = pool(Level.entries.filter { kotlin.math.abs(it.ordinal - level.ordinal) <= 1 }.toSet())
        if (maxPrep != null) pool.filter { (it.meal.prepMin ?: 0) <= maxPrep }.takeIf { it.isNotEmpty() }?.let { pool = it }
        fun score(r: ResolvedMeal): Int {
            var s = 0
            s += 2 * (r.meal.tags intersect preferTags).size
            // A meal made for a situation (after a short night, a sick day, the road) waits for it.
            s -= 2 * (r.meal.tags intersect SITUATIONAL - preferTags).size
            if (r.meal.context.any { it in preferContext }) s += 2
            if (preferContext.isEmpty() && r.meal.context.all { it in ROAD }) s -= 2
            if (r.meal.plate == d.plate) s += 1
            s -= 3 * r.components.count { it.first.id in p.dislikes }
            return s
        }
        return pool.sortedWith(compareByDescending<ResolvedMeal> { score(it) }.thenBy { stableHash("$day/${d.slot}/${it.meal.id}") })
    }

    private fun mainPlate(t: DayTemplate): String =
        t.slots.firstOrNull { it.slot == "lunch" }?.plate ?: t.slots.firstOrNull { it.slot in MAIN }?.plate ?: "base"

    private fun maxPlate(a: String, b: String): String =
        if (PLATE_ORDER.indexOf(b) > PLATE_ORDER.indexOf(a)) b else a

    private fun Int.roundTo(step: Int): Int = (this + step / 2) / step * step

    /** FNV-1a: stable across runs and JVMs (String.hashCode is too, but this mixes better). */
    private fun stableHash(s: String): Long {
        var h = -0x340d631b7bdddcdbL
        for (c in s) {
            h = h xor c.code.toLong()
            h *= 0x100000001b3L
        }
        return h
    }

    /** A slot before meals are chosen. */
    data class Draft(
        val slot: String,
        val at: Int,
        val plate: String,
        val optional: Boolean,
        val merge: Boolean,
        val afterTraining: Boolean = false,
    )
}
