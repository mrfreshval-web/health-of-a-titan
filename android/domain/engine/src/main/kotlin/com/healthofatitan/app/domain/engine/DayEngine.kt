package com.healthofatitan.app.domain.engine

import kotlin.math.max

/**
 * A09 §1: the deterministic pipeline of one morning. Stages 0–3 live in [RegimeEngine]; here the
 * map and the schedule: bedtime and the caffeine line (SLP-01/02), light (SLP-03), a short nap
 * (SLP-05), focus (WRK-01/02), dinner (NUT-01), the training (A19 via [TrainingSelector], ACT-01/03).
 * No network and no LLM: the same [DayInput] always gives the same [DayPlan].
 */
object DayEngine {

    /** A09 §3 budget: ≤ 7 visible items; markers (coffee, wind-down) are a line, not items. */
    const val BUDGET = 7

    object Ids {
        const val TRAINING = "training"
        const val BEDTIME = "bedtime"
        const val WATER = "water"
        const val LIGHT = "light"
        const val NAP = "nap"
        const val FOCUS = "focus"
        const val BREAKFAST = "breakfast"
        const val DINNER = "dinner"
    }

    fun plan(input: DayInput): DayPlan {
        val r = RegimeEngine.decide(input)
        val fired = r.fired.toMutableList()
        val sc = input.schedule
        val n = input.night
        val wake = sc.wakeMin
        val shortNight = n.sleepHours != null && n.sleepHours < 6.0
        val travel = DayFlag.TRAVEL in r.flags

        // SLP-01: −30 min on a GENTLE day (computed or final, YAML `regime.computed`) or after a
        // night < 6 h (min, not sum; never below −60).
        val shifted = r.regime == Regime.GENTLE || r.computed == Regime.GENTLE || shortNight
        val bedtime = sc.bedtimeMin + if (shifted) -30 else 0
        fired += FiredRule(RuleSet.SLP_01, DayTexts.bedtime(bedtime, sc.wakeTomorrowMin, sc.wakeTomorrowSource, sc.sleepNeedMin, shifted))
        // SLP-02: caffeine line = bedtime − 9 h; moves with the bedtime.
        val cutoff = bedtime - 9 * 60
        fired += FiredRule(RuleSet.SLP_02, DayTexts.caffeine(cutoff))
        val windDown = bedtime - 60

        val training = TrainingSelector.select(input, r, bedtime, fired)

        val items = mutableListOf<PlanItem>()
        val trainingRule = when {
            r.sick -> RuleSet.SAF_03
            training.type == TrainingType.WALK -> RuleSet.ACT_01
            else -> RuleSet.ACT_03
        }
        items += PlanItem(
            id = Ids.TRAINING,
            rule = trainingRule,
            kind = ItemKind.TRAINING,
            startMin = training.startMin,
            endMin = training.startMin?.let { it + training.minutes },
            title = DayTexts.trainingTitle(training),
            detail = DayTexts.trainingDetail(training),
            priority = 0,
        )
        items += PlanItem(Ids.BEDTIME, RuleSet.SLP_01, ItemKind.SLEEP, bedtime, null, DayTexts.ITEM_BEDTIME, priority = 1)

        if (r.alcoholServings >= 2) {
            items += PlanItem(Ids.WATER, RuleSet.CTX_01, ItemKind.NUTRITION, wake, null, DayTexts.ITEM_WATER, priority = 2)
        }

        if (travel) {
            // CTX-04: light by local time — east: morning, west: afternoon, > 6 h: daytime.
            val shift = input.context.tzShiftHours
            val at = when {
                kotlin.math.abs(shift) > 6.0 -> 13 * 60
                shift > 0 -> wake
                else -> 15 * 60
            }
            items += PlanItem(Ids.LIGHT, RuleSet.CTX_04, ItemKind.LIGHT, at, at + 20, DayTexts.ITEM_LIGHT_LOCAL, priority = 3)
        } else {
            items += PlanItem(Ids.LIGHT, RuleSet.SLP_03, ItemKind.LIGHT, wake, wake + 60, DayTexts.ITEM_LIGHT, priority = 3)
            fired += FiredRule(RuleSet.SLP_03, DayTexts.LIGHT)
        }

        if (shortNight && !r.sick) {
            // Ends by 15:00 and ≥ 7 h before bedtime (SLP-05 slot). Optional: the first to go
            // when the plan is over budget (schedule.yaml `drop_first: optional`).
            val napAt = max(13 * 60 + 15, wake + 6 * 60).coerceAtMost(minOf(14 * 60 + 40, bedtime - 7 * 60 - 20))
            items += PlanItem(Ids.NAP, RuleSet.SLP_05, ItemKind.NAP, napAt, napAt + 20, DayTexts.ITEM_NAP, priority = 9)
            fired += FiredRule(RuleSet.SLP_05, DayTexts.nap(n.sleepHours!!))
        }

        // WRK-01 / WRK-02: focus 2–5 h after waking; one main task on a GENTLE day or after < 6 h.
        val oneTask = r.regime == Regime.GENTLE || shortNight
        val (blocks, blockMin) = when {
            oneTask -> 1 to if (r.regime == Regime.GENTLE) 45 else 60
            r.regime == Regime.BOOST -> 2 to 90
            else -> 2 to 60
        }
        val focusAt = wake + 2 * 60
        items += PlanItem(
            Ids.FOCUS,
            if (oneTask) RuleSet.WRK_02 else RuleSet.WRK_01,
            ItemKind.WORK,
            focusAt,
            focusAt + blocks * blockMin,
            DayTexts.ITEM_FOCUS,
            DayTexts.focusDetail(blocks, blockMin),
            priority = 5,
        )
        fired += FiredRule(if (oneTask) RuleSet.WRK_02 else RuleSet.WRK_01, if (oneTask) DayTexts.ONE_TASK else DayTexts.FOCUS)

        if ((shortNight && !r.sick) || r.alcoholServings >= 2) {
            val rule = if (r.alcoholServings >= 2) RuleSet.CTX_01 else RuleSet.SLP_05
            items += PlanItem(Ids.BREAKFAST, rule, ItemKind.NUTRITION, wake + 30, null, DayTexts.ITEM_BREAKFAST, priority = 6)
        }

        // NUT-01: dinner ends 3 h before bedtime.
        val dinnerEnd = bedtime - 3 * 60
        items += PlanItem(Ids.DINNER, RuleSet.NUT_01, ItemKind.NUTRITION, dinnerEnd - 45, dinnerEnd, DayTexts.ITEM_DINNER, priority = 7)
        fired += FiredRule(RuleSet.NUT_01, DayTexts.dinner(dinnerEnd))

        val visible = items.sortedBy { it.priority }.take(BUDGET).sortedWith(compareBy({ it.startMin ?: Int.MAX_VALUE }, { it.priority }))

        val plan = DayPlan(
            dayKey = input.dayKey,
            regime = r.regime,
            regimeComputed = r.computed,
            points = r.points,
            signals = r.signals,
            flags = r.flags,
            forbidIntervals = r.forbidIntervals,
            fired = fired.distinctBy { it.ref to it.why },
            training = training,
            items = visible,
            bedtimeMin = bedtime,
            caffeineCutoffMin = cutoff,
            windDownMin = windDown,
            headline = DayTexts.headline(r.regime, r.flags),
            summary = DayTexts.summary(n, training, bedtime),
        )
        // RET-01 last: after a pause the plan becomes its minimal version (A18 И4.4).
        return ReturnMode.apply(plan, input.silenceDays)
    }
}
