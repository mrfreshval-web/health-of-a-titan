package com.healthofatitan.app.domain.engine

import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * A19 §1.2: the training of the day. A09 only sets the ceiling, bans and the slot (Agent 12's
 * review, A09 §9 p. 1); the choice itself follows the A19 steps:
 *  0. safety gate (illness → rest or a walk ≤ 20 min), GENTLE → ACT-01 walk;
 *  1. ceiling by regime; 2. weekly queue from the WHOOP sessions of the last 28 days;
 *  3. modifiers (TR-01 light week, TR-05 short night, TR-06 alcohol, TR-07 late slot);
 *  4. template by type × place × time.
 */
object TrainingSelector {

    const val DEFAULT_START = 18 * 60
    const val DEFAULT_MINUTES = 45

    /** A19 §2.1 note: «средний» after 4 weeks with ≥ 2 strength sessions a week. [Оценка] */
    const val INTERMEDIATE_STRENGTH_28D = 8

    fun select(input: DayInput, r: RegimeDecision, bedtimeMin: Int, fired: MutableList<FiredRule>): TrainingChoice {
        val sc = input.schedule
        val today = input.dayKey

        // Step 0 · safety gate.
        if (r.sick) {
            return TrainingChoice(TrainingType.REST, TrainingLevel.L3, null, 20, null)
        }
        if (r.regime == Regime.GENTLE) {
            fired += FiredRule(RuleSet.ACT_01, DayTexts.WALK)
            return TrainingChoice(TrainingType.WALK, TrainingLevel.L2, "T15", 30, null)
        }

        // Step 2 · weekly queue (cardinal rules of A19, goal «общая форма»).
        val history = input.history.filter { it.dayKey < today && it.dayKey >= today.minusDays(28) && it.minutes >= 10 }
        val intermediate = history.count { it.kind == SessionKind.STRENGTH } >= INTERMEDIATE_STRENGTH_28D
        val monday = today.with(DayOfWeek.MONDAY)
        val week = history.filter { it.dayKey >= monday }
        val strengthWeek = week.count { it.kind == SessionKind.STRENGTH }
        val dayIndex = today.dayOfWeek.value - 1
        val daysLeft = 7 - dayIndex
        val lastStrength = history.filter { it.kind == SessionKind.STRENGTH }.maxOfOrNull { it.dayKey }
        val daysSinceStrength = lastStrength?.let { ChronoUnit.DAYS.between(it, today).toInt() }
        val intervals = history.filter { it.kind == SessionKind.CARDIO && it.intensity == Intensity.HIGH }
        val intervals7 = intervals.count { it.dayKey >= today.minusDays(7) }
        val daysSinceIntervals = intervals.maxOfOrNull { it.dayKey }?.let { ChronoUnit.DAYS.between(it, today).toInt() } ?: 99
        val weekMinutes = week.sumOf { it.minutes }
        val trainedDays = history.map { it.dayKey }.toSet()
        var consecutive = 0
        while (today.minusDays(consecutive + 1L) in trainedDays) consecutive++

        val (queued, queueWhy) = when {
            consecutive >= 6 -> TrainingType.MOBILITY to DayTexts.QUEUE_REST
            strengthWeek < 2 && daysLeft <= (2 - strengthWeek) + 1 -> TrainingType.STRENGTH to DayTexts.queueStrength(strengthWeek)
            daysSinceStrength == null -> TrainingType.STRENGTH to DayTexts.queueStrength(strengthWeek)
            daysSinceStrength >= 3 -> TrainingType.STRENGTH to DayTexts.QUEUE_STRENGTH_GAP
            r.regime == Regime.BOOST && intermediate && intervals7 < 2 && daysSinceIntervals >= 2 -> TrainingType.INTERVALS to DayTexts.QUEUE_INTERVALS
            weekMinutes < 150 * (dayIndex + 1) / 7 -> TrainingType.ZONE2 to DayTexts.queueZone2(weekMinutes)
            daysSinceStrength >= 2 -> TrainingType.STRENGTH to DayTexts.QUEUE_DEFAULT
            else -> TrainingType.ZONE2 to DayTexts.QUEUE_DEFAULT
        }
        fired += FiredRule(RuleSet.A19_QUEUE, queueWhy)

        // Step 1 and 3 · ceiling and modifiers: they only lower.
        var type = queued
        var level = if (r.regime == Regime.BOOST && !r.lighter) TrainingLevel.L0 else TrainingLevel.L1
        var replaced: TrainingType? = null
        fun replaceWith(to: TrainingType) {
            if (type == to) return
            if (replaced == null) replaced = type
            type = to
        }
        if (type == TrainingType.INTERVALS && (r.forbidIntervals || !intermediate)) replaceWith(TrainingType.ZONE2)

        val sleep = input.night.sleepHours
        if (sleep != null && sleep < 6.0 && (type == TrainingType.STRENGTH || type == TrainingType.INTERVALS)) {
            if (type == TrainingType.INTERVALS) replaceWith(TrainingType.ZONE2)
            level = TrainingLevel.L1
            fired += FiredRule(RuleSet.TR_05, DayTexts.SHORT_NIGHT_TRAINING)
        }

        val usual = sc.trainingMinutes ?: DEFAULT_MINUTES
        fun minutesFor(t: TrainingType): Int {
            val raw = when (t) {
                TrainingType.INTERVALS -> if (usual >= 40) 40 else 20
                TrainingType.ZONE2 -> usual.coerceIn(30, 60)
                TrainingType.MOBILITY, TrainingType.WALK -> 30
                TrainingType.STRENGTH -> usual.coerceIn(20, 60)
                TrainingType.REST -> 20
            }
            if (t == TrainingType.INTERVALS) return raw // T11 is 40 min, T12 20 min
            val scaled = if (r.volumeFactor < 1.0) max(20, (raw * r.volumeFactor).toInt()) else raw
            return Templates.snap(scaled)
        }
        var minutes = minutesFor(type)

        // ACT-03 slot in the usual window; TR-07: intensive work ends ≥ 2 h before bedtime.
        val fromHistory = sc.trainingStartMin != null
        var start = (sc.trainingStartMin ?: DEFAULT_START).coerceAtLeast(sc.wakeMin + 30)
        val intensive = type == TrainingType.INTERVALS || (type == TrainingType.STRENGTH && level == TrainingLevel.L0)
        if (intensive && start + minutes > bedtimeMin - 120) {
            if (type == TrainingType.INTERVALS) replaceWith(TrainingType.ZONE2) else level = TrainingLevel.L1
            minutes = minutesFor(type)
            fired += FiredRule(RuleSet.TR_07, DayTexts.LATE_INTENSE)
        }
        // Any training ends before the wind-down hour.
        if (start + minutes > bedtimeMin - 60) start = max(sc.wakeMin + 30, bedtimeMin - 60 - minutes)
        fired += FiredRule(RuleSet.ACT_03, DayTexts.trainingSlot(start, fromHistory))

        replaced?.let { fired += FiredRule(RuleSet.A19_QUEUE, DayTexts.replaced(it, type)) }

        val template = Templates.pick(type, Templates.defaultPlace(type), minutes, intermediate)
        return TrainingChoice(type, level, template, minutes, start, replaced)
    }
}
