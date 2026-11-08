package com.healthofatitan.app.domain.engine

import kotlin.math.abs
import kotlin.math.ceil

/**
 * Result of the first four stages of A09 §1 (safety → data → regime → context). The map and
 * schedule stages ([DayEngine]) only read it and may make the day stricter, never looser.
 *
 * @param lighter training at most L1 (lighter strength, intervals replaced)
 * @param volumeFactor training volume multiplier (REG-06 / A19 TR-01: 0.6)
 */
data class RegimeDecision(
    val regime: Regime,
    val computed: Regime,
    val points: Int,
    val signals: Set<String>,
    val flags: Set<DayFlag>,
    val forbidIntervals: Boolean,
    val lighter: Boolean,
    val volumeFactor: Double,
    val sick: Boolean,
    val alcoholServings: Int,
    val fired: List<FiredRule>,
)

/**
 * A09 §2 as pure functions over [DayInput]: signals → points → regime, then caps (REG-01),
 * conflicts (REG-03), the one-step-up rule (REG-04), the light week (REG-06), safety (SAF-03/04)
 * and context (CTX, EVE-01). «Самое строгое побеждает»: every later step can only lower the
 * regime or add a ban.
 */
object RegimeEngine {

    const val MIN_BASELINE = 14
    const val FULL_NORM_DAYS = 28
    const val MIN_COVERAGE = 0.6

    fun decide(input: DayInput): RegimeDecision {
        val n = input.night
        val d = input.data
        val y = input.yesterday
        val s = input.self
        val fired = mutableListOf<FiredRule>()
        val flags = linkedSetOf<DayFlag>()
        var forbidIntervals = false
        var lighter = false
        var volumeFactor = 1.0

        // Facts of context the safety stage needs too (A09 predicate `known_cause`).
        val alcohol = input.evening?.alcohol ?: 0
        val tz = input.context.tzShiftHours
        val tzDays = input.context.daysSinceShift ?: 1
        val heavyLoadYesterday = (n.trlZYesterday ?: 0.0) >= 1.5
        val knownCause = alcohol >= 2 || (abs(tz) >= 2.0 && tzDays <= 2) || heavyLoadYesterday

        // Stage 1 · data quality (REG-05@2). WHOOP Recovery % is never read (A25 P7): while the
        // norm is calibrating there is no physiology signal and no Разгон. No scored night by
        // 09:30 → MANUAL: the day is built by default, Ровно at most.
        val calibration = d.baselineNights < MIN_BASELINE
        val manual = !d.whoopToday
        val physioUsable = !calibration && !manual && d.coverage >= MIN_COVERAGE && n.orsZ != null
        val preliminary = !calibration && d.historyDays < FULL_NORM_DAYS
        if (!physioUsable) flags += DayFlag.DATA_LIMITED
        if (manual) flags += DayFlag.MANUAL
        if (calibration) flags += DayFlag.CALIBRATION
        if (preliminary) flags += DayFlag.PRELIMINARY_NORM
        val dataWhy = when {
            manual -> if (d.whoopConnected) DayTexts.MANUAL else DayTexts.MANUAL_NO_WHOOP
            calibration -> DayTexts.CALIBRATION
            !physioUsable -> DayTexts.LOW_COVERAGE
            preliminary -> DayTexts.preliminary(d.historyDays)
            else -> null
        }

        // Stage 0 · safety (listed first in «Почему так»). SAF-03: a cold from the evening deck.
        val sick = input.evening?.body == Body.SICK
        if (sick) {
            flags += DayFlag.SICK
            fired += FiredRule(RuleSet.SAF_03, DayTexts.SICK)
            if (y.sickStreak >= 2 || n.rhrZStreakGe2 >= 3) fired += FiredRule(RuleSet.SAF_03, DayTexts.SICK_SEE_DOCTOR)
        }
        // SAF-04: resting HR and breathing well above the norm without a known cause → Ровно at
        // most, no intervals and a soft «прислушайся» (no illness names, Agent 7).
        val physioShift = physioUsable && !sick && !knownCause &&
            (n.rhrZ ?: 0.0) >= 2.0 && (n.rrZ ?: 0.0) >= 1.5
        if (physioShift) {
            forbidIntervals = true
            fired += FiredRule(RuleSet.SAF_04, DayTexts.PHYSIO_SHIFT)
        }
        if (dataWhy != null) fired += FiredRule(RuleSet.REG_05, dataWhy)

        // Stage 2 · regime (REG-02): points of strain, A09 §2.1.
        val signals = linkedSetOf<String>()
        var points = 0
        var held = false
        val ors = if (physioUsable) n.orsZ else null
        if (ors != null) {
            val orsPoints = when {
                ors <= -2.0 -> 2
                ors <= -1.0 -> 1
                Signals.ORS_DAY in y.signals && ors <= -0.5 -> 1.also { held = true } // exit threshold
                else -> 0
            }
            if (orsPoints > 0) {
                points += orsPoints
                signals += Signals.ORS_DAY
            }
            val trend = n.orsEwma7
            if (trend != null && trend <= -0.5) {
                points += 1
                signals += Signals.ORS_TREND
            }
        }

        val h = n.sleepHours
        val prev = n.prevSleepHours
        val sleepShort = (h != null && h < 6.0) ||
            (h != null && prev != null && h < 7.0 && prev < 7.0) ||
            (n.sleepDebt7h ?: 0.0) >= 5.0
        if (sleepShort) {
            points += 1
            signals += Signals.SLEEP
        }

        val lrb = n.lrb
        if (lrb != null && (lrb > 1.0 || (Signals.LOAD in y.signals && lrb > 0.75))) {
            points += 1
            signals += Signals.LOAD
            if (lrb <= 1.0) held = true
        }

        val selfLow = (s.energy != null && s.energy <= 3) ||
            (s.energyZ != null && s.energyZ <= -1.0) ||
            s.sleepQuality == 1
        if (selfLow) {
            points += 1
            signals += Signals.SELF
        }
        val selfGood = ((s.energy != null && s.energy >= 7) || (s.energyZ != null && s.energyZ >= 1.0)) &&
            !(s.sleepQuality != null && s.sleepQuality <= 2)

        // Разгон needs the physiology of the night: never while calibrating or without data.
        val boostPhysio = ors != null && ors >= 0.0
        val energyOk = if (s.energy != null) {
            s.energy >= 6
        } else {
            !preliminary && ors != null && ors >= 0.5
        }
        val strainOk = (s.expectedStrain ?: 0) < 7
        val computed = when {
            points >= 3 || (selfLow && points >= 2) -> Regime.GENTLE
            points == 0 && boostPhysio && (h ?: 0.0) >= 7.0 && energyOk && strainOk -> Regime.BOOST
            else -> Regime.STEADY
        }
        var regime = computed
        if (h != null || s.energy != null || physioUsable) {
            fired += FiredRule(RuleSet.REG_02, DayTexts.signalsWhy(signals, n, computed))
        }

        // REG-05: without usable physiology the day is Ровно at most.
        if (!physioUsable) regime = regime.atMost(Regime.STEADY)

        // REG-01: hard caps — sleep < 5 h or illness. Self-report never lifts them.
        val hardCap = (h != null && h < 5.0) || sick
        if (hardCap) {
            regime = regime.atMost(Regime.GENTLE)
            fired += FiredRule(RuleSet.REG_01, if (h != null && h < 5.0) DayTexts.hardCapSleep(h) else DayTexts.HARD_CAP_SICK)
        }

        // REG-03: GENTLE only from data + a good morning → STEADY without intervals (never with SAF).
        val dataOnlyGentle = computed == Regime.GENTLE && !selfLow && !hardCap && !physioShift
        if (dataOnlyGentle && selfGood) {
            regime = Regime.STEADY
            forbidIntervals = true
            fired += FiredRule(RuleSet.REG_03, DayTexts.SELF_VS_DATA)
        }
        if (physioShift) regime = regime.atMost(Regime.STEADY)

        // REG-04: down at once, up by one step a day; the exit thresholds kept a signal on.
        val yr = y.regime
        if (yr != null && regime.order > yr.order + 1) {
            regime = Regime.ofOrder(yr.order + 1)
            fired += FiredRule(RuleSet.REG_04, DayTexts.stepUp(yr))
        } else if (held) {
            fired += FiredRule(RuleSet.REG_04, DayTexts.HYSTERESIS)
        }

        // REG-06: light week — 3 GENTLE days in a row, or Весы > +1 for 7 days; lasts 7 days.
        val streak = y.gentleStreak + if (regime == Regime.GENTLE) 1 else 0
        val lightWhy = when {
            y.lightWeekDay in 1..6 -> DayTexts.lightWeekDay(y.lightWeekDay + 1)
            streak >= 3 -> DayTexts.LIGHT_WEEK_GENTLE
            n.lrbDaysAbove1 >= 7 -> DayTexts.LIGHT_WEEK_LOAD
            else -> null
        }
        if (lightWhy != null) {
            flags += DayFlag.LIGHT_WEEK
            regime = regime.atMost(Regime.STEADY)
            forbidIntervals = true
            lighter = true
            volumeFactor = 0.6
            fired += FiredRule(RuleSet.REG_06, lightWhy)
        }

        // Stage 3 · context. Explains and makes the plan stricter, never looser.
        if (alcohol >= 2) {
            val lowRecovery = (ors ?: 0.0) <= -0.5
            val lighterTraining = alcohol >= 3 // CTX-01 heavy_or_late = A19 TR-06
            if (lowRecovery || lighterTraining) forbidIntervals = true
            if (lighterTraining) lighter = true
            fired += FiredRule(RuleSet.CTX_01, DayTexts.alcohol(alcohol, lowRecovery, lighterTraining))
        }
        // CTX-03: a hard session yesterday (high TRL or WHOOP intervals) + recovery below the norm.
        val yesterdayDay = input.dayKey.minusDays(1)
        val intervalsYesterday = input.history.any { it.dayKey == yesterdayDay && it.intensity == Intensity.HIGH }
        if ((heavyLoadYesterday || intervalsYesterday) && (ors ?: 0.0) <= -0.5) {
            forbidIntervals = true
            fired += FiredRule(RuleSet.CTX_03, DayTexts.HEAVY_YESTERDAY)
        }
        // CTX-04: one TRAVEL day per hour of shift, at most 3; the first day is Ровно, no intervals.
        val travelDays = minOf(3, ceil(abs(tz)).toInt())
        if (abs(tz) >= 2.0 && tzDays <= travelDays) {
            flags += DayFlag.TRAVEL
            val firstDay = tzDays <= 1
            if (firstDay) {
                regime = regime.atMost(Regime.STEADY)
                forbidIntervals = true
            }
            fired += FiredRule(RuleSet.CTX_04, DayTexts.travel(tz, firstDay))
        }
        // EVE-01 (day@2): «тело забито» in the evening deck → lighter training, no intervals.
        if (input.evening?.body == Body.SORE) {
            lighter = true
            forbidIntervals = true
            fired += FiredRule(RuleSet.EVE_01, DayTexts.SORE)
        }

        return RegimeDecision(
            regime = regime,
            computed = computed,
            points = points,
            signals = signals,
            flags = flags,
            forbidIntervals = forbidIntervals || regime != Regime.BOOST,
            lighter = lighter,
            volumeFactor = volumeFactor,
            sick = sick,
            alcoholServings = alcohol,
            fired = fired,
        )
    }
}
