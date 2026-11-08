package com.healthofatitan.app.domain.engine

import com.healthofatitan.app.core.model.profile.AddressForm
import java.time.LocalDate

/**
 * A09 §2: three regimes. «Восстановление», illness, travel are flags on top, not a fourth regime.
 * [order] is the ladder used by caps (min) and by the one-step-up rule (REG-04).
 */
enum class Regime(val order: Int, val ru: String) {
    GENTLE(0, "Бережно"),
    STEADY(1, "Ровно"),
    BOOST(2, "Разгон"),
    ;

    fun atMost(cap: Regime): Regime = if (order > cap.order) cap else this

    companion object {
        fun ofOrder(order: Int): Regime = entries.first { it.order == order.coerceIn(0, 2) }
    }
}

/** A09 §2 `DayState.flags` (the subset this engine can raise from WHOOP data and the evening deck). */
enum class DayFlag {
    SICK, TRAVEL, LIGHT_WEEK, CALIBRATION, PRELIMINARY_NORM, DATA_LIMITED, MANUAL,

    /** RET-01: the first day after silence ≥ 2 days — the minimal version, warm words (A18 И4.4). */
    RETURN,
}

/** Signals of A09 §2.1 (points of strain). Ids are stored with the day for REG-04 exit thresholds. */
object Signals {
    const val ORS_DAY = "SG-ORS-DAY"
    const val ORS_TREND = "SG-ORS-TREND"
    const val SLEEP = "SG-SLEEP"
    const val LOAD = "SG-LOAD"
    const val SELF = "SG-SELF"
}

/** `ruleId@ver` (A09 §6): every fired rule and every plan item carries the version that made it. */
data class RuleRef(val id: String, val version: Int) {
    override fun toString(): String = "$id@$version"

    companion object {
        fun parse(text: String): RuleRef? {
            val at = text.lastIndexOf('@')
            if (at <= 0) return null
            val v = text.substring(at + 1).toIntOrNull() ?: return null
            return RuleRef(text.substring(0, at), v)
        }
    }
}

/** One line of «Почему так»: the rule and its explanation, built from the user's own data. */
data class FiredRule(val ref: RuleRef, val why: String)

/** Answers of the evening deck about the day that is closing (A25 §2.3, D-027: ≤ 3 taps). */
data class EveningFacts(
    /** В4: servings of alcohol, 0…4 (4 = 4+); null = skipped. */
    val alcohol: Int? = null,
    /** У5 moved to the evening: how the body feels; null = skipped. */
    val body: Body? = null,
)

enum class Body { FINE, SORE, SICK }

/** Morning cards (A25 §2.2). Not asked in slice 2 (D-027: data first); kept so A09 scenarios hold. */
data class SelfReport(
    /** У2 energy 1–10. */
    val energy: Int? = null,
    /** z of У2 against own answers (n ≥ 10). */
    val energyZ: Double? = null,
    /** У1 sleep quality 1–5. */
    val sleepQuality: Int? = null,
    /** У6 expected strain of the day 1–10. */
    val expectedStrain: Int? = null,
)

/**
 * Last night and the week, all derived HoT values (A25 `metricId@ver`, see [RuleSet.METRICS_REQUIRED]).
 *
 * @param orsZ ORS@1 of the night (z units, before EWMA)
 * @param orsEwma7 7-day EWMA of ORS@1 (SG-ORS-TREND)
 * @param rhrZ RHRZ@1: z of last night's resting heart rate against the 28-day norm (SAF-04)
 * @param rrZ RRZ@1: z of last night's respiratory rate (SAF-04)
 * @param rhrZStreakGe2 nights in a row, up to last night, with [rhrZ] ≥ +2 (SAF-03 see_doctor)
 *
 * WHOOP Recovery % is not read at all (A25 P7, day@2 REG-05): during calibration the engine
 * does not use a physiology signal and does not give Разгон.
 */
data class NightFacts(
    val orsZ: Double? = null,
    val orsEwma7: Double? = null,
    val sleepHours: Double? = null,
    val prevSleepHours: Double? = null,
    val sleepDebt7h: Double? = null,
    val lrb: Double? = null,
    val lrbDaysAbove1: Int = 0,
    val trlZYesterday: Double? = null,
    val chargeLevel: Int? = null,
    val rhrZ: Double? = null,
    val rrZ: Double? = null,
    val rhrZStreakGe2: Int = 0,
)

/** A09 REG-05 inputs. [historyDays] = days since the first WHOOP night HoT knows (norm younger than 28 days → preliminary). */
data class DataFacts(
    val baselineNights: Int = 28,
    val historyDays: Int = 60,
    val coverage: Double = 1.0,
    val whoopToday: Boolean = true,
    /** S2: WHOOP is not connected at all; the MANUAL day then says so instead of «ещё не посчитал». */
    val whoopConnected: Boolean = true,
)

/**
 * What the engine needs from earlier days (they are stored, never recomputed — A09 §6).
 *
 * @param gentleStreak GENTLE days in a row right before today
 * @param lightWeekDay day of an active «лёгкая неделя» yesterday (1…7), 0 if none
 * @param sickStreak SICK days in a row right before today (SAF-03 see_doctor)
 */
data class YesterdayFacts(
    val regime: Regime? = null,
    val signals: Set<String> = emptySet(),
    val gentleStreak: Int = 0,
    val lightWeekDay: Int = 0,
    val sickStreak: Int = 0,
)

/**
 * CTX-04: signed shift of the phone's time zone against the home zone, hours (+ = east), and
 * how many nights were already slept in the new zone (0 = arrived today, 1 = first morning
 * there). Null = unknown, treated as the first day (the safer reading).
 */
data class ContextFacts(val tzShiftHours: Double = 0.0, val daysSinceShift: Int? = null)

/** Training kinds of A19 §1.2 step 1 plus the GENTLE walk (ACT-01) and rest. */
enum class TrainingType(val ru: String) {
    STRENGTH("Силовая"),
    INTERVALS("Интервалы"),
    ZONE2("Zone 2"),
    MOBILITY("Мобильность"),
    WALK("Прогулка"),
    REST("Отдых"),
}

/** A19 §1.3: L0 full, L1 lighter, L2 recovery, L3 rest. */
enum class TrainingLevel { L0, L1, L2, L3 }

/** What WHOOP recorded, classified by HoT (no zones or heart rate are kept, only these classes). */
enum class SessionKind { STRENGTH, CARDIO }

/**
 * Intensity class from the share of time in WHOOP heart-rate zones, computed in memory during the
 * sync (A35 §4): HIGH — zones 4–5 ≥ 20 % of the session (the work part of intervals), ZONE2 —
 * zones 1–2 ≥ 60 %, EASY — zones 0–1 ≥ 60 %, otherwise MIXED. Thresholds [Оценка], review on day 30.
 */
enum class Intensity { HIGH, ZONE2, EASY, MIXED }

/** A training session seen by WHOOP, as HoT keeps it (`TrainingSession`, A14 §3.2). */
data class Session(
    val id: String,
    val dayKey: LocalDate,
    /** Local start, minutes from 00:00 of [dayKey] (may be ≥ 1440 after midnight). */
    val startMin: Int,
    val minutes: Int,
    val kind: SessionKind,
    val intensity: Intensity,
)

/** Where the anchor came from — shown next to the value (D-027 rule 3). */
enum class AnchorSource(val ru: String) {
    ALARM("будильник"),
    WHOOP("WHOOP"),
    DEFAULT("по умолчанию"),
}

/**
 * The day's anchors (A09 §7a), already resolved for one day. Times are minutes from 00:00 of the
 * day; [bedtimeMin] may be ≥ 1440 (after midnight).
 */
data class ScheduleFacts(
    val wakeMin: Int = 7 * 60,
    val wakeTomorrowMin: Int = 7 * 60,
    val wakeTomorrowSource: AnchorSource = AnchorSource.DEFAULT,
    val sleepNeedMin: Int? = null,
    val bedtimeMin: Int = 23 * 60,
    val trainingStartMin: Int? = null,
    val trainingMinutes: Int? = null,
)

/** Everything one morning needs. Pure data: the same input always gives the same plan. */
data class DayInput(
    val dayKey: LocalDate,
    val night: NightFacts = NightFacts(),
    val data: DataFacts = DataFacts(),
    val yesterday: YesterdayFacts = YesterdayFacts(),
    val evening: EveningFacts? = null,
    val self: SelfReport = SelfReport(),
    val context: ContextFacts = ContextFacts(),
    val schedule: ScheduleFacts = ScheduleFacts(),
    /** WHOOP sessions of the last 28 days before today (A19 weekly queue). */
    val history: List<Session> = emptyList(),
    /** RET-01: whole days before today without opening HoT ([ReturnPolicy.silenceDays]). */
    val silenceDays: Int = 0,
    /**
     * How the texts speak to the person (A61 §4.1, D-055): like the mentor's character it changes
     * only the words, never the plan, the rules or safety. The texts of [DayEngine] are neutral
     * (A39); the `why` of rules with m/f forms read it once their texts are reviewed (Agents 10, 16).
     */
    val address: AddressForm = AddressForm.NEUTRAL,
)

/** Kinds of plan items; the budget and the order of «Сегодня» use them. */
enum class ItemKind { TRAINING, SLEEP, LIGHT, WORK, NUTRITION, NAP }

/**
 * A plan item (A14 `PlanItem`). [id] is stable within a day, so a rebuild keeps the user's marks.
 *
 * @param startMin minutes from 00:00 of the day; null = any time
 */
data class PlanItem(
    val id: String,
    val rule: RuleRef,
    val kind: ItemKind,
    val startMin: Int?,
    val endMin: Int? = null,
    val title: String,
    val detail: String? = null,
    val priority: Int,
)

/** The training chosen by A19 for the day. */
data class TrainingChoice(
    val type: TrainingType,
    val level: TrainingLevel,
    val templateId: String?,
    val minutes: Int,
    val startMin: Int?,
    /** What the weekly queue asked for before caps and modifiers, if it was replaced. */
    val replaced: TrainingType? = null,
)

/** A09 output for one day: what «Сегодня», the push and tomorrow's engine read. */
data class DayPlan(
    val dayKey: LocalDate,
    val regime: Regime,
    val regimeComputed: Regime,
    val points: Int,
    val signals: Set<String>,
    val flags: Set<DayFlag>,
    val forbidIntervals: Boolean,
    val fired: List<FiredRule>,
    val training: TrainingChoice,
    val items: List<PlanItem>,
    val bedtimeMin: Int,
    val caffeineCutoffMin: Int,
    val windDownMin: Int,
    /** Push title, ≤ 40 characters (A11 §2). No S4/S5 content. */
    val headline: String,
    /** Push text, ≤ 110 characters (A11 §2). No alcohol, no intimacy. */
    val summary: String,
    val ruleSet: String = RuleSet.VERSION,
)
