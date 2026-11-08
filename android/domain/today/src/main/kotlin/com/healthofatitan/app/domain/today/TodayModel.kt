package com.healthofatitan.app.domain.today

import java.time.LocalDate
import java.time.LocalDateTime

/** The day's mode as «Сегодня» shows it (A45 §6.2): Разгон, Ровно, Бережно. */
enum class DayMode(val word: String) {
    RAZGON("Разгон"),
    ROVNO("Ровно"),
    BEREZHNO("Бережно"),
}

/** The four blocks of «Сегодня» (A46 §1); the tag is the test tag and the id of the block. */
enum class TodayBlock(val tag: String) {
    ME("block_me"),
    SUPPORTS("block_supports"),
    TASKS("block_tasks"),
    RHYTHM("block_rhythm"),
}

/** What overrides the day (A46 §2.2 «Без хокку», §4.8): the sober screen without the Titan. */
enum class Safety {
    NONE,

    /** SAF-01: chest pain was named in a deck today; the emergency line stands on screen. */
    SAF_01,

    /** CrisisGuard ≥ 2 in the last 72 h (A07). */
    CRISIS,

    /** The storm net «Как ты на самом деле?» opened today (A44 §6.1, `CARE`). */
    CARE,
    ;

    val sober: Boolean get() = this != NONE
}

/** The scenarios of A46 §4 (4.1–4.9). */
enum class Scenario(val id: String) {
    /** 4.1: neither WHOOP nor Health Connect nor the wake deck yet: HoT does not know the person. */
    NO_SOURCE("4.1"),

    /** 4.1a: no WHOOP, but Health Connect or the wake deck: the day by self-report, Ровно at most. */
    SELF("4.1a"),
    NIGHT_PENDING("4.2"),
    AFTER_DAWN("4.3"),
    DAY("4.4"),
    AFTER_WORKOUT("4.5"),
    EVENING("4.6"),
    GENTLE("4.7"),
    SAFETY("4.8"),
    RETURN("4.9"),
}

/** Light of the day for the Titan and the columns (A46 §4: предрассветный, день, сумерки). */
enum class DayLight { DARK, PREDAWN, MORNING, DAY, DUSK }

/** Everything the screen needs to decide its scenario. */
data class DayFacts(
    val now: LocalDateTime,
    val whoopConnected: Boolean,
    /** Today's night is scored (the plan of today is built from data, not a MANUAL default). */
    val nightReady: Boolean,
    val mode: DayMode?,
    val safety: Safety = Safety.NONE,
    /** Silence ≥ 7 days before today (A18 И4.4): «С возвращением». */
    val returning: Boolean = false,
    /** «Рассвет» has been passed today. */
    val dawnDone: Boolean = false,
    /** WHOOP confirmed today's training in the last hours (A46 §4.5). */
    val workoutConfirmed: Boolean = false,
    /** Health Connect gives the phone's steps (READ_STEPS granted, D-024). */
    val healthConnect: Boolean = false,
    /** The wake deck of «Рассвет» is answered today: the person said how they are. */
    val selfReported: Boolean = false,
) {
    val today: LocalDate get() = now.toLocalDate()
}

object Scenarios {
    const val EVENING_FROM_HOUR = 18
    const val MORNING_UNTIL_HOUR = 12

    /**
     * One scenario wins, in the order of A46 §4: safety first, then the return, then data, then the
     * day. Without WHOOP the day is lived all the same (S2): Бережно stays Бережно, otherwise the
     * day runs by Health Connect and self-report ([Scenario.SELF]) or, with neither, as 4.1.
     */
    fun of(f: DayFacts): Scenario = when {
        f.safety.sober -> Scenario.SAFETY
        f.returning -> Scenario.RETURN
        !f.whoopConnected -> when {
            f.mode == DayMode.BEREZHNO -> Scenario.GENTLE
            f.healthConnect || f.selfReported -> Scenario.SELF
            else -> Scenario.NO_SOURCE
        }
        !f.nightReady -> Scenario.NIGHT_PENDING
        f.mode == DayMode.BEREZHNO -> Scenario.GENTLE
        f.now.hour >= EVENING_FROM_HOUR || f.now.hour < 4 -> Scenario.EVENING
        f.workoutConfirmed -> Scenario.AFTER_WORKOUT
        f.dawnDone && f.now.hour < MORNING_UNTIL_HOUR -> Scenario.AFTER_DAWN
        else -> Scenario.DAY
    }

    fun light(s: Scenario, now: LocalDateTime): DayLight = when {
        s == Scenario.NO_SOURCE || s == Scenario.SAFETY -> DayLight.DARK
        s == Scenario.NIGHT_PENDING -> DayLight.PREDAWN
        now.hour >= EVENING_FROM_HOUR || now.hour < 4 -> DayLight.DUSK
        now.hour < MORNING_UNTIL_HOUR -> DayLight.MORNING
        else -> DayLight.DAY
    }
}

/** T-01: the order of the blocks is the same in every scenario and at any time of day. */
object TodayLayout {
    val ORDER: List<TodayBlock> = listOf(TodayBlock.ME, TodayBlock.SUPPORTS, TodayBlock.TASKS, TodayBlock.RHYTHM)

    @Suppress("UNUSED_PARAMETER")
    fun blocks(scenario: Scenario, now: LocalDateTime): List<TodayBlock> = ORDER
}

/** What «Я сегодня» needs (A46 §2.2). Numbers are HoT values or WHOOP values held in memory only. */
data class MeInput(
    val scenario: Scenario,
    val mode: DayMode?,
    val haiku: List<String>?,
    val safety: Safety,
    /** Заряд 5–95, 50 = the personal norm. */
    val charge: Int?,
    val nightMinutes: Int?,
    val needMinutes: Int?,
    /** Bodrost of the «Рассвет» deck, 1–5. */
    val self: Int?,
    val baselineNights: Int,
    /** WHOOP is connected; without it the mode is an estimate and never above Ровно (A09 REG-05). */
    val whoopConnected: Boolean = true,
)

data class MeView(
    val showTitan: Boolean,
    val mode: DayMode?,
    val haiku: List<String>?,
    val lead: String?,
    val charge: Int?,
    val night: String?,
    val need: String?,
    val self: Int?,
    val calibration: String?,
    /** The mode is an estimate (no WHOOP: the default day and self-report), shown as «оценка». */
    val estimate: Boolean = false,
)

object MeRules {
    /** A46 §2.2 «Калибровка»: until 14 nights «Учусь тебя: N из 28», the mode at most Ровно. */
    const val CALIBRATION_NIGHTS = 14
    const val NORM_NIGHTS = 28

    /** T-07: the haiku of the day stays all day; never with SAF-01, a crisis or `CARE`. */
    fun haikuVisible(safety: Safety): Boolean = !safety.sober

    fun calibrating(nights: Int): Boolean = nights < CALIBRATION_NIGHTS

    /** The ceiling of calibration: Разгон is not given until the norm is learned. */
    fun capped(mode: DayMode?, nights: Int): DayMode? =
        if (mode == DayMode.RAZGON && calibrating(nights)) DayMode.ROVNO else mode

    /** Without WHOOP the mode comes from the default day and self-report: Ровно at most (A46 4.1a). */
    fun withoutWhoop(mode: DayMode?): DayMode? = if (mode == DayMode.RAZGON) DayMode.ROVNO else mode

    fun clock(minutes: Int): String = "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"

    fun build(i: MeInput): MeView {
        val sober = i.safety.sober || i.scenario == Scenario.SAFETY
        val lead = when (i.scenario) {
            Scenario.SAFETY -> TodayCopy.LEAD_SAFETY
            Scenario.NO_SOURCE -> TodayCopy.LEAD_NO_SOURCE
            Scenario.NIGHT_PENDING -> TodayCopy.LEAD_NIGHT_PENDING
            Scenario.RETURN -> TodayCopy.LEAD_RETURN
            else -> null
        }
        val dataShown = !sober && i.scenario != Scenario.NO_SOURCE
        // Заряд, the night and calibration are WHOOP's: without it they are not shown at all.
        val whoopShown = dataShown && i.whoopConnected
        val mode = when {
            sober || i.scenario == Scenario.NO_SOURCE || i.scenario == Scenario.NIGHT_PENDING -> null
            !i.whoopConnected -> withoutWhoop(i.mode)
            else -> capped(i.mode, i.baselineNights)
        }
        return MeView(
            showTitan = !sober,
            mode = mode,
            haiku = i.haiku?.takeIf { haikuVisible(i.safety) && !sober && it.isNotEmpty() },
            lead = lead,
            charge = i.charge?.takeIf { whoopShown && !calibrating(i.baselineNights) },
            night = i.nightMinutes?.takeIf { whoopShown }?.let(::clock),
            need = i.needMinutes?.takeIf { whoopShown }?.let(::clock),
            self = i.self?.takeIf { dataShown },
            calibration = if (whoopShown && calibrating(i.baselineNights)) TodayCopy.calibration(i.baselineNights) else null,
            estimate = mode != null && !i.whoopConnected,
        )
    }
}
