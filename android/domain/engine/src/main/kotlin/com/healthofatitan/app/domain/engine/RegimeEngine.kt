package com.healthofatitan.app.domain.engine

import kotlin.math.abs

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
 * conflicts (REG-03), the one-step-up rule (REG-04), the light week (REG-06) and context (CTX).
 * «Самое строгое побеждает»: every later step can only lower the regime or add a ban.
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

        // Stage 0 · safety. SAF-03: a cold reported in the evening deck.
        val sick = input.evening?.body == Body.SICK
        if (sick) {
            flags += DayFlag.SICK
            fired += FiredRule(RuleSet.SAF_03, DayTexts.SICK)
        }

        // Stage 1 · data quality (REG-05).
        val calibration = d.baselineNights < MIN_BASELINE
        val physioUsable = !calibration && d.coverage >= MIN_COVERAGE && d.whoopToday && n.orsZ != null
        val preliminary = !calibration && d.historyDays < FULL_NORM_DAYS
        if (!physioUsable) flags += DayFlag.DATA_LIMITED
        val dataWhy = when {
            calibration -> DayTexts.CALIBRATION.also { flags += DayFlag.CALIBRATION }
            !physioUsable -> DayTexts.LOW_COVERAGE
            else -> null
        } ?: if (preliminary) DayTexts.preliminary(d.historyDays) else null
        if (preliminary) flags += DayFlag.PRELIMINARY_NORM
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
        } else if (calibration && n.recoveryPct != null && n.recoveryPct <= 33.0) {
            points += 1
            signals += Signals.ORS_DAY
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

        val boostPhysio = if (calibration) (n.recoveryPct ?: -1.0) >= 67.0 else (ors != null && ors >= 0.0)
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
        fired += FiredRule(RuleSet.REG_02, DayTexts.signalsWhy(signals, n, computed))

        // REG-05 low coverage: no physiology → at most Ровно unless energy ≥ 7 and sleep ≥ 7 h.
        if (!physioUsable && !calibration && !((s.energy ?: 0) >= 7 && (h ?: 0.0) >= 7.0)) {
            regime = regime.atMost(Regime.STEADY)
        }

        // REG-01: hard caps — sleep < 5 h or illness. Self-report never lifts them.
        val hardCap = (h != null && h < 5.0) || sick
        if (hardCap) {
            regime = regime.atMost(Regime.GENTLE)
            fired += FiredRule(RuleSet.REG_01, if (h != null && h < 5.0) DayTexts.hardCapSleep(h) else DayTexts.HARD_CAP_SICK)
        }

        // REG-03: GENTLE only from data + a good morning → STEADY without intervals.
        val dataOnlyGentle = computed == Regime.GENTLE && !selfLow && !hardCap
        if (dataOnlyGentle && selfGood) {
            regime = Regime.STEADY
            forbidIntervals = true
            fired += FiredRule(RuleSet.REG_03, DayTexts.SELF_VS_DATA)
        }

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
        val alcohol = input.evening?.alcohol ?: 0
        if (alcohol >= 2) {
            val noIntervals = (n.orsZ ?: 0.0) <= -0.5
            val lighterTraining = alcohol >= 3 // CTX-01 variant = A19 TR-06
            if (noIntervals || lighterTraining) forbidIntervals = true
            if (lighterTraining) lighter = true
            fired += FiredRule(RuleSet.CTX_01, DayTexts.alcohol(alcohol, noIntervals, lighterTraining))
        }
        if ((n.trlZYesterday ?: 0.0) >= 1.5 && (n.orsZ ?: 0.0) <= -0.5) {
            forbidIntervals = true
            fired += FiredRule(RuleSet.CTX_03, DayTexts.HEAVY_YESTERDAY)
        }
        val tz = input.context.tzShiftHours
        if (abs(tz) >= 2.0) {
            flags += DayFlag.TRAVEL
            regime = regime.atMost(Regime.STEADY)
            forbidIntervals = true
            fired += FiredRule(RuleSet.CTX_04, DayTexts.travel(tz))
        }
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
