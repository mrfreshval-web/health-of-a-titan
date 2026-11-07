package com.healthofatitan.app.domain.training

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** What the A19 weekly queue of the day engine asked for (`:domain:engine.TrainingType`). */
enum class DayTrainingKind { STRENGTH, INTERVALS, ZONE2, MOBILITY, WALK, REST }

/** Where the session happens (`vocab.place`), with the inventory assumed there until data says otherwise (D-027). */
enum class TrainingPlace(val id: String, val ru: String, val equipment: Set<String>) {
    HOME("home", "Дом", setOf("none", "mat", "wall", "chair")),
    GYM("gym", "Зал", setOf("none", "mat", "wall", "chair", "dumbbells", "kettlebell", "machine_cable", "bike", "rower", "treadmill", "band", "box", "bar", "barbell", "rack", "trap_bar")),
    OUTDOOR("outdoor", "Улица", setOf("none", "wall")),
    HOTEL("hotel", "Отель", setOf("none", "mat", "wall", "chair")),
}

/** Day flags the training gates read (`rules.gates`, `rules.modifiers`). */
enum class TrainingFlag { EMERGENCY, STOP_SIGNAL, SICK, SEE_SPECIALIST, ED_CARE, LIGHT_WEEK, TRAVEL }

data class TrainingRequest(
    val mode: DayMode,
    val kind: DayTrainingKind? = null,
    val minutes: Int? = null,
    val place: TrainingPlace = TrainingPlace.HOME,
    val equipment: Set<String> = place.equipment,
    val tier: Tier = Tier.CLASSIC,
    /** Training codes of the profile (A40 §8), see [TrainingProfile.codes]. */
    val codes: Set<String> = emptySet(),
    val flags: Set<TrainingFlag> = emptySet(),
)

/** A rule that changed the session, for the «Почему так» line (A40 §4.2 step 8). */
data class TrainingFired(val id: String, val why: String)

enum class HiddenReason { EMERGENCY, STOP_SIGNAL, DOCTOR, NO_PROGRAM }

sealed interface TrainingDecision {
    val fired: List<TrainingFired>

    data class Hidden(val reason: HiddenReason, override val fired: List<TrainingFired>) : TrainingDecision

    data class Planned(val session: TrainingSession, override val fired: List<TrainingFired>) : TrainingDecision
}

data class SessionItem(
    val exercise: Exercise,
    val sets: Int,
    val reps: String?,
    val timeS: Int?,
    val distanceM: Int?,
    val perSide: Boolean,
    /** Rest after one set (or after the pair in a superset); 0 = go on. */
    val restS: Int,
    val tempo: String?,
    val rpe: String?,
    val zone: String?,
    val pair: String?,
    val noteRu: String?,
    /** The exercise of the template this one replaced (profile or day filter), if any. */
    val replaced: String? = null,
)

data class SessionBlock(
    val type: BlockType,
    val format: String,
    val minutes: Int,
    val rounds: Int,
    val restBetweenRoundsS: Int,
    val items: List<SessionItem>,
)

/**
 * The session of the day, ready for the player. [ref] = `templateId@version` (ADR-05): the same
 * request always gives the same session, and a stored workout can be reproduced.
 */
data class TrainingSession(
    val template: Template,
    /** The template the matrix chose before a regime swap. */
    val chosenTemplateId: String,
    val mode: DayMode,
    val lighter: Boolean,
    val blocks: List<SessionBlock>,
    val minutes: Int,
    val rpeMax: Int?,
    val zoneMax: String?,
) {
    val ref: String get() = template.ref
    val swapped: Boolean get() = chosenTemplateId != template.id
    val items: List<SessionItem> get() = blocks.flatMap { it.items }
}

/**
 * The deterministic configurator of A40 §4.2 for the slice «Классика»: gates → regime ceiling →
 * modifiers → template by kind × place × time → its version for the day (`modes`) → profile
 * filter with replacements → parameters. Rules only make a session lighter or safer, never
 * heavier; warm-up with mobility and cool-down with stretching always stay (M-ALL).
 */
class TrainingEngine(private val lib: TrainingLibrary) {

    fun plan(request: TrainingRequest): TrainingDecision {
        val fired = mutableListOf<TrainingFired>()
        val codes = request.codes
        val codeRules = codes.mapNotNull { lib.rules.profileCodes[it] }

        // 1 · Gates (rules.gates): the first hit wins for hiding.
        if (TrainingFlag.EMERGENCY in request.flags) {
            fired += TrainingFired("TG-01", TrainingWhy.EMERGENCY)
            return TrainingDecision.Hidden(HiddenReason.EMERGENCY, fired)
        }
        if (TrainingFlag.STOP_SIGNAL in request.flags) {
            fired += TrainingFired("TG-07", TrainingWhy.STOP_SIGNAL)
            return TrainingDecision.Hidden(HiddenReason.STOP_SIGNAL, fired)
        }
        codeRules.firstOrNull { it.hideTraining }?.let {
            fired += TrainingFired(it.id, TrainingWhy.DOCTOR)
            return TrainingDecision.Hidden(HiddenReason.DOCTOR, fired)
        }
        codeRules.firstOrNull { it.noProgram }?.let {
            fired += TrainingFired("TG-05", TrainingWhy.NO_PROGRAM)
            return TrainingDecision.Hidden(HiddenReason.NO_PROGRAM, fired)
        }

        var mode = request.mode
        var tier = request.tier
        codeRules.mapNotNull { it.maxLevel }.minByOrNull { it.order }?.let { cap -> if (tier.order > cap.order) tier = cap }
        val forbidTags = mutableSetOf<String>()
        val forbidPatterns = mutableSetOf<String>()
        val forbidMethods = mutableSetOf<String>()
        val rpeCaps = mutableListOf<Int>()
        var zoneMax: String? = null
        var maxMinutes: Int? = null
        var onlyTemplates: List<String>? = null
        var volumePct = 0

        if (TrainingFlag.SICK in request.flags) {
            fired += TrainingFired("TG-02", TrainingWhy.SICK)
            onlyTemplates = listOf(SICK_TEMPLATE)
            maxMinutes = 20
            zoneMax = "Z1"
        } else if (TrainingFlag.SEE_SPECIALIST in request.flags) {
            fired += TrainingFired("TG-03", TrainingWhy.SEE_SPECIALIST)
            onlyTemplates = SPECIALIST_TEMPLATES
        }
        if ("screening_not_cleared" in codes) {
            fired += TrainingFired("TG-04", TrainingWhy.SCREENING)
            if (tier.order > Tier.CLASSIC.order) tier = Tier.CLASSIC
            rpeCaps += 6
            forbidTags += listOf("heavy_load", "valsalva", "max_speed", "inversion")
            forbidMethods += listOf("hiit", "sprint", "metcon", "strength_periodized")
        }
        if (TrainingFlag.ED_CARE in request.flags) {
            fired += TrainingFired("TG-06", TrainingWhy.ED_CARE)
            forbidMethods += "metcon"
        }

        // 2 · Modifiers that only lower (rules.modifiers).
        if (TrainingFlag.LIGHT_WEEK in request.flags) {
            if (mode.order > DayMode.STEADY.order) mode = DayMode.STEADY
            volumePct = -40
            forbidMethods += listOf("hiit", "sprint", "metcon")
            fired += TrainingFired("TM-01", TrainingWhy.LIGHT_WEEK)
        }
        if (TrainingFlag.TRAVEL in request.flags) {
            if (mode.order > DayMode.STEADY.order) mode = DayMode.STEADY
            forbidMethods += listOf("hiit", "sprint")
            fired += TrainingFired("TM-11", TrainingWhy.TRAVEL)
        }

        // 3 · Regime ceiling (rules.regime_caps) and profile codes.
        val cap = lib.rules.regimeCaps.getValue(mode)
        cap.rpeMax[tier]?.let(rpeCaps::add)
        forbidTags += cap.forbidTags
        if (cap.zoneMax != null && zoneMax == null) zoneMax = cap.zoneMax
        for (r in codeRules) {
            forbidTags += r.forbidTags
            forbidPatterns += r.forbidPatterns
            forbidMethods += r.forbidMethods
            r.rpeMax?.let(rpeCaps::add)
        }

        // 4 · Template by kind × place × time (A40 §4.3, «Классика»).
        val place = if (TrainingFlag.TRAVEL in request.flags) TrainingPlace.HOTEL else request.place
        val equipment = request.equipment + "none"
        val chosenId = onlyTemplates?.let { only -> pickMobility(only, request.minutes) }
            ?: pick(request.kind, mode, place, equipment, request.minutes, fired)

        // 5 · Version for the day: full, lighter or another template (modes).
        var useLighter = false
        var template = lib.template(chosenId) ?: lib.template(SICK_TEMPLATE)!!
        val seen = mutableSetOf(template.id)
        while (true) {
            when (val use = template.modes[mode] ?: ModeUse.Full) {
                ModeUse.Full -> break
                ModeUse.Lighter -> { useLighter = true; break }
                is ModeUse.Swap -> {
                    val next = lib.template(use.templateId) ?: break
                    if (!seen.add(next.id)) break
                    template = next
                }
            }
        }
        val allowed = cap.allowMethods
        if ((allowed != null && template.method !in allowed && template.method !in MOBILITY_METHODS) || template.method in forbidMethods) {
            template = lib.template(SICK_TEMPLATE)!!
            useLighter = false
        }
        when {
            template.id != chosenId -> fired += TrainingFired("MODE", TrainingWhy.swapped(mode, lib.template(chosenId)?.nameRu ?: chosenId, template.nameRu))
            useLighter -> fired += TrainingFired("MODE", TrainingWhy.lighter(mode))
            else -> fired += TrainingFired("MODE", TrainingWhy.full(mode))
        }
        if (useLighter) template.lighter.rpeMax?.let(rpeCaps::add)
        if (useLighter) template.lighter.zoneMax?.let { zoneMax = minZone(zoneMax, it) }
        upper(template.intensity.rpe)?.let(rpeCaps::add)

        // 6 · Blocks: lighter, volume, profile filter with replacements, M-ALL.
        val forbid = Forbid(forbidTags, forbidPatterns, codes, tier, place, template.equipment + equipment)
        val replaced = mutableListOf<String>()
        var blocks = template.blocks
            .filter { b -> !(useLighter && b.type in template.lighter.dropBlocks && b.type in DROPPABLE) }
            .map { b -> block(b, template, useLighter, volumePct, forbid, replaced) }
            .filter { it.items.isNotEmpty() || it.type == BlockType.WARMUP || it.type == BlockType.COOLDOWN }
        blocks = ensureMobilityEverywhere(blocks, forbid, fired)
        if (replaced.isNotEmpty()) fired += TrainingFired("PROFILE", TrainingWhy.replaced(replaced.size))

        var minutes = blocks.sumOf { it.minutes }
        val limit = maxMinutes
        if (limit != null && minutes > limit) {
            blocks = fit(blocks, limit)
            minutes = blocks.sumOf { it.minutes }
        }

        val session = TrainingSession(
            template = template,
            chosenTemplateId = chosenId,
            mode = mode,
            lighter = useLighter,
            blocks = blocks,
            minutes = minutes,
            rpeMax = rpeCaps.minOrNull(),
            zoneMax = zoneMax,
        )
        return TrainingDecision.Planned(session, fired)
    }

    private fun pickMobility(only: List<String>, minutes: Int?): String {
        if (only.size == 1) return only.first()
        val m = minutes ?: 25
        return when {
            m <= 15 -> only.firstOrNull { it == "tpl-mob-morning" }
            m >= 30 -> only.firstOrNull { it == "tpl-mob-recovery" }
            else -> only.firstOrNull { it == "tpl-mob-deep-stretch" }
        } ?: only.first()
    }

    /** A40 §4.3 matrix for «Классика». Intervals are not a Classic method: they become Zone 2. */
    private fun pick(kind: DayTrainingKind?, mode: DayMode, place: TrainingPlace, equipment: Set<String>, minutes: Int?, fired: MutableList<TrainingFired>): String {
        val weights = equipment.any { it == "dumbbells" || it == "kettlebell" }
        fun strength(): String = when {
            place == TrainingPlace.GYM -> if ((minutes ?: 45) < 40 && weights) "tpl-cl-db-strength" else "tpl-cl-gym-circuit"
            place == TrainingPlace.HOME && weights -> "tpl-cl-db-strength"
            else -> "tpl-cl-functional-home"
        }
        return when (kind) {
            DayTrainingKind.STRENGTH -> strength()
            DayTrainingKind.INTERVALS -> {
                fired += TrainingFired("CL-INT", TrainingWhy.CLASSIC_NO_INTERVALS)
                "tpl-cl-zone2"
            }
            DayTrainingKind.ZONE2, DayTrainingKind.WALK -> if (mode == DayMode.GENTLE) "tpl-mob-recovery" else "tpl-cl-zone2"
            DayTrainingKind.MOBILITY -> if ((minutes ?: 30) <= 15) "tpl-mob-morning" else "tpl-mob-recovery"
            DayTrainingKind.REST -> "tpl-mob-morning"
            null -> if (mode == DayMode.GENTLE) "tpl-mob-recovery" else strength()
        }
    }

    private class Forbid(
        val tags: Set<String>,
        val patterns: Set<String>,
        val codes: Set<String>,
        val tier: Tier,
        val place: TrainingPlace,
        val equipment: Set<String>,
    ) {
        /** May the user do it today at all (tags, patterns, own contraindications, level)? */
        fun allows(e: Exercise): Boolean =
            !e.retired && e.riskTags.none { it in tags } && e.pattern !in patterns &&
                e.contraindications.none { it in codes } && e.level.order <= tier.order

        /** A replacement must also fit the place and the inventory. */
        fun fits(e: Exercise): Boolean = allows(e) && place.id in e.place && e.equipment.any { it in equipment }
    }

    private fun block(
        b: TemplateBlock,
        t: Template,
        lighter: Boolean,
        volumePct: Int,
        forbid: Forbid,
        replaced: MutableList<String>,
    ): SessionBlock {
        val core = b.type == BlockType.MAIN || b.type == BlockType.FINISHER
        val l = t.lighter
        val items = b.items
            .filter { !(lighter && core && it.ex in l.dropExercises) }
            .mapNotNull { item -> resolve(item, forbid)?.let { (ex, from) -> Triple(item, ex, from) } }
            .map { (item, ex, from) ->
                if (from != null) replaced += from
                val dose = if (from == null) item.dose.over(ex.default) else replacementDose(item.dose, ex)
                var sets = dose.sets ?: 1
                var time = dose.timeS
                if (core && lighter && l.setsDelta < 0 && sets > 1) sets = max(1, sets + l.setsDelta)
                if (core && volumePct < 0 && sets > 1) sets = max(1, ceil(sets * (100 + volumePct) / 100.0).toInt())
                if (core && lighter && l.durationPct < 0 && time != null) time = scaleTime(time, l.durationPct)
                if (core && volumePct < 0 && time != null && b.items.size == 1) time = scaleTime(time, volumePct)
                SessionItem(
                    exercise = ex,
                    sets = sets,
                    reps = dose.reps,
                    timeS = time,
                    distanceM = dose.distanceM,
                    perSide = item.perSide || ex.measure.perSide,
                    restS = restFor(item, ex, b.format),
                    tempo = dose.tempo,
                    rpe = dose.rpe,
                    zone = dose.zone,
                    pair = item.pair,
                    noteRu = item.noteRu,
                    replaced = from,
                )
            }
        var rounds = b.rounds ?: 1
        // In a circuit a round is the set: `sets_delta` of the lighter version takes a round off.
        val roundsDelta = if (l.roundsDelta != 0) l.roundsDelta else if (b.format == "circuit") l.setsDelta else 0
        if (core && lighter && roundsDelta < 0 && rounds > 1) rounds = max(1, rounds + roundsDelta)
        if (core && volumePct < 0 && rounds > 1) rounds = max(1, ceil(rounds * (100 + volumePct) / 100.0).toInt())
        var minutes = b.minutes
        if (core && b.items.isNotEmpty()) {
            val share = items.size.toDouble() / b.items.size
            var k = share
            if (lighter && l.durationPct < 0) k *= (100 + l.durationPct) / 100.0
            if (lighter && l.setsDelta < 0 && b.format != "circuit") k *= setsShare(b, l.setsDelta)
            if (b.rounds != null && b.rounds > 0) k *= rounds.toDouble() / b.rounds
            if (volumePct < 0) k *= (100 + volumePct) / 100.0
            minutes = max(if (items.isEmpty()) 0 else 2, (b.minutes * k).roundToInt())
        }
        return SessionBlock(b.type, b.format, minutes, rounds, b.restBetweenRoundsS ?: if (rounds > 1) DEFAULT_ROUND_REST_S else 0, items)
    }

    /** A replacement keeps the planned sets and is measured its own way (reps, time or distance). */
    private fun replacementDose(planned: Dose, ex: Exercise): Dose {
        val d = ex.default
        val base = Dose(sets = planned.sets ?: d.sets, restS = planned.restS, tempo = d.tempo, rpe = planned.rpe ?: d.rpe, zone = planned.zone ?: d.zone)
        return when {
            ex.measure.timed -> base.copy(timeS = planned.timeS ?: d.timeS ?: MIN_TIMED_S * 2)
            ex.measure == Measure.DISTANCE -> base.copy(distanceM = planned.distanceM ?: d.distanceM)
            else -> base.copy(reps = planned.reps ?: d.reps)
        }
    }

    private fun setsShare(b: TemplateBlock, delta: Int): Double {
        val before = b.items.sumOf { it.dose.sets ?: 1 }
        if (before == 0) return 1.0
        val after = b.items.sumOf { val s = it.dose.sets ?: 1; if (s > 1) max(1, s + delta) else s }
        return after.toDouble() / before
    }

    private fun scaleTime(s: Int, pct: Int): Int = max(MIN_TIMED_S, ((s * (100 + pct) / 100.0) / 5).roundToInt() * 5)

    private fun restFor(item: TemplateItem, ex: Exercise, format: String): Int {
        item.dose.restS?.let { return it }
        return when (format) {
            "straight_sets", "superset", "skill_practice" ->
                ex.default.restS ?: if ("heavy_load" in ex.riskTags) lib.rules.player.restHeavyS else lib.rules.player.restByCategory[ex.category] ?: 0
            else -> 0
        }
    }

    /**
     * A40 §4.2 step 6: forbidden exercise → `alt` of the template → `swaps` → `regressions` → the
     * same pattern without forbidden tags → skip. Returns the exercise and the replaced id.
     */
    private fun resolve(item: TemplateItem, forbid: Forbid): Pair<Exercise, String?>? {
        val original = lib.exercise(item.ex) ?: return null
        if (forbid.allows(original)) return original to null
        val chain = item.alt + original.swaps + original.regressions
        chain.asSequence().mapNotNull(lib::exercise).firstOrNull(forbid::fits)?.let { return it to original.id }
        lib.exercises.values.asSequence()
            .filter { it.pattern == original.pattern && it.category == original.category && it.id != original.id }
            .sortedBy { it.id }
            .firstOrNull(forbid::fits)
            ?.let { return it to original.id }
        return null
    }

    /**
     * M-ALL (A40 §2.4): the first block is a warm-up with mobility, the last a cool-down with a
     * stretch that ends with a stretch or breathing. If the profile filter took them away, the
     * safest allowed exercise of the same kind takes their place.
     */
    private fun ensureMobilityEverywhere(blocks: List<SessionBlock>, forbid: Forbid, fired: MutableList<TrainingFired>): List<SessionBlock> {
        val out = blocks.toMutableList()
        var added = false
        val wi = out.indexOfFirst { it.type == BlockType.WARMUP }
        if (wi >= 0 && out[wi].items.none { it.exercise.isMobility }) {
            safest(WARMUP_FALLBACK, forbid)?.let { ex ->
                out[wi] = out[wi].copy(items = listOf(fallbackItem(ex)) + out[wi].items)
                added = true
            }
        }
        val ci = out.indexOfLast { it.type == BlockType.COOLDOWN }
        if (ci >= 0) {
            var cool = out[ci]
            if (cool.items.none { it.exercise.isStretch }) {
                safest(COOLDOWN_FALLBACK, forbid)?.let { ex ->
                    cool = cool.copy(items = cool.items + fallbackItem(ex))
                    added = true
                }
            }
            val last = cool.items.lastOrNull()?.exercise
            if (last == null || !(last.isStretch || last.isBreathing)) {
                lib.exercise(BREATH)?.takeIf(forbid::allows)?.let { ex ->
                    cool = cool.copy(items = cool.items + fallbackItem(ex))
                    added = true
                }
            }
            out[ci] = cool
        }
        if (added) fired += TrainingFired("M-ALL", TrainingWhy.MOBILITY_KEPT)
        return out
    }

    private fun safest(ids: List<String>, forbid: Forbid): Exercise? =
        ids.asSequence().mapNotNull(lib::exercise).firstOrNull(forbid::fits)
            ?: ids.asSequence().mapNotNull(lib::exercise).firstOrNull(forbid::allows)

    private fun fallbackItem(ex: Exercise) = SessionItem(
        exercise = ex,
        sets = 1,
        reps = if (ex.measure.timed) null else ex.default.reps,
        timeS = if (ex.measure.timed) ex.default.timeS ?: 30 else null,
        distanceM = null,
        perSide = ex.measure.perSide,
        restS = 0,
        tempo = ex.default.tempo,
        rpe = null,
        zone = null,
        pair = null,
        noteRu = null,
    )

    /** Fit into a hard limit (illness ≤ 20 min): only main and finisher shrink. */
    private fun fit(blocks: List<SessionBlock>, limit: Int): List<SessionBlock> {
        val fixed = blocks.filter { it.type == BlockType.WARMUP || it.type == BlockType.COOLDOWN }.sumOf { it.minutes }
        val core = blocks.filter { it.type == BlockType.MAIN || it.type == BlockType.FINISHER }.sumOf { it.minutes }
        if (core <= 0) return blocks
        val k = ((limit - fixed).coerceAtLeast(2)).toDouble() / core
        if (k >= 1.0) return blocks
        return blocks.map { b ->
            if (b.type != BlockType.MAIN && b.type != BlockType.FINISHER) return@map b
            b.copy(
                minutes = max(2, (b.minutes * k).roundToInt()),
                items = b.items.map { i -> if (i.timeS != null) i.copy(timeS = max(MIN_TIMED_S, ((i.timeS * k) / 5).roundToInt() * 5)) else i },
            )
        }
    }

    companion object {
        const val SICK_TEMPLATE = "tpl-mob-recovery"
        val SPECIALIST_TEMPLATES = listOf("tpl-mob-morning", "tpl-mob-recovery", "tpl-mob-deep-stretch")
        val MOBILITY_METHODS = setOf("mobility", "stretching")
        private val DROPPABLE = setOf(BlockType.MAIN, BlockType.FINISHER)
        private const val DEFAULT_ROUND_REST_S = 60
        private const val MIN_TIMED_S = 15
        private const val BREATH = "ex-breath-long-exhale"
        private val WARMUP_FALLBACK = listOf("ex-arm-circles", "ex-cat-cow", "ex-leg-swings", "ex-worlds-greatest", "ex-ankle-knee-wall", "ex-open-book", "ex-band-dislocate")
        private val COOLDOWN_FALLBACK = listOf(
            "ex-stretch-calf", "ex-stretch-lat-wall", "ex-stretch-hamstring", "ex-stretch-hip-flexor",
            "ex-stretch-quad", "ex-stretch-chest-doorway", "ex-stretch-child", "ex-stretch-pigeon",
        )

        /** "6-7" → 7, "8" → 8. */
        fun upper(rpe: String?): Int? = rpe?.split('-', '–')?.mapNotNull { it.trim().toIntOrNull() }?.maxOrNull()

        private fun minZone(a: String?, b: String): String = if (a == null || b < a) b else a
    }
}
