package com.healthofatitan.app.domain.today.week

import com.healthofatitan.app.domain.today.DayMode
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * «Титан недели» (A52 §2.7, D-045): the look of the Titan is assembled from the data of the week by
 * deterministic rules (Agent 18) — no AI images. The pictures are a placeholder from TitanLottie
 * (poses and light) until the visual pack of Agents 4 and 17.
 *
 * A hard week gets another figure (rest at a column), never a broken or dark one (A53 §3.1).
 */
enum class WeekPose(val words: String) {
    /** A week of recovery (≥ 3 days Бережно or SICK): rests by a column. */
    REST(WeekCopy.POSE_REST),

    /** 0–2 days in support: stands, calm. */
    STAND(WeekCopy.POSE_STAND),

    /** 3–4 days in support: holds the sky. */
    HOLD(WeekCopy.POSE_HOLD),

    /** 5–7 days in support: raises the vault. */
    RISE(WeekCopy.POSE_RISE),
}

/** The sky of the week: sleep (debt and regularity). Never a storm (no shame). */
enum class WeekSky(val words: String) { CLEAR(WeekCopy.SKY_CLEAR), HAZE(WeekCopy.SKY_HAZE), DUSK(WeekCopy.SKY_DUSK) }

/** «Деталь недели» — the main win (A52 §2.7); the person's pride wins over data (A53 §3.1). */
enum class WeekDetail(val words: String) {
    PRIDE(WeekCopy.DETAIL_PRIDE),
    ALL_GOALS(WeekCopy.DETAIL_GOALS),
    NO_SLEEP_DEBT(WeekCopy.DETAIL_SLEEP),
    FOCUS_RECORD(WeekCopy.DETAIL_FOCUS),
    MOVEMENT(WeekCopy.DETAIL_MOVE),
}

/** What the look is made of. */
data class TitanWeekInput(
    val weekStart: LocalDate,
    val today: LocalDate,
    val columns: List<DayColumn>,
    /** The five supports of the week, 0…1 (mean fill of each over the days with data). */
    val supports: List<Float>,
    val sleepDebtHours: Double?,
    val regularityPct: Double?,
    val goalsAllDone: Boolean,
    val focusRecord: Boolean = false,
    val zoneMinutes: Int? = null,
    val pride: Boolean = false,
    /** The ritual of this week is done (Sunday): the full reveal. */
    val ritualDone: Boolean = false,
)

/**
 * The parameters of the look. [reveal] is how far the figure has come out of its outline (0 = an
 * outline, 1 = in full light); Monday to Saturday it grows with the days that have data, on Sunday
 * the ritual opens it fully. [seed] varies the small things (stars, the line of the horizon) so
 * no two weeks are the same.
 */
data class TitanWeekLook(
    val weekStart: LocalDate,
    val pose: WeekPose,
    val mode: DayMode,
    /** Posture and light: 0…1 from «Дни в опоре». */
    val light: Float,
    val columns: List<Float>,
    val sky: WeekSky,
    val detail: WeekDetail?,
    val reveal: Float,
    val revealed: Boolean,
    val daysInSupport: Int,
    val seed: Long,
) {
    /** The look in words, for TalkBack and the share card (no numbers of health). */
    val spoken: String get() = listOfNotNull(WeekCopy.TITAN, pose.words, sky.words, detail?.words).joinToString(", ")
}

object TitanOfWeek {
    const val FULL_FROM = 5
    const val HOLD_FROM = 3
    const val DEBT_CLEAR_H = 1.0
    const val DEBT_DUSK_H = 4.0
    const val REGULARITY_CLEAR = 75.0
    const val MOVE_ZONES = 150

    fun pose(columns: List<DayColumn>, days: Int): WeekPose = when {
        WeekScoring.gentleWeek(columns) || columns.count { it.sick } >= 2 -> WeekPose.REST
        days >= FULL_FROM -> WeekPose.RISE
        days >= HOLD_FROM -> WeekPose.HOLD
        else -> WeekPose.STAND
    }

    fun sky(debt: Double?, regularity: Double?): WeekSky = when {
        debt == null && regularity == null -> WeekSky.HAZE
        (debt ?: 0.0) <= DEBT_CLEAR_H && (regularity ?: 100.0) >= REGULARITY_CLEAR -> WeekSky.CLEAR
        (debt ?: 0.0) >= DEBT_DUSK_H -> WeekSky.DUSK
        else -> WeekSky.HAZE
    }

    fun detail(i: TitanWeekInput): WeekDetail? = when {
        i.pride -> WeekDetail.PRIDE
        i.goalsAllDone -> WeekDetail.ALL_GOALS
        i.focusRecord -> WeekDetail.FOCUS_RECORD
        i.sleepDebtHours != null && i.sleepDebtHours <= DEBT_CLEAR_H && i.columns.count { !it.future && !it.noData } >= 5 -> WeekDetail.NO_SLEEP_DEBT
        (i.zoneMinutes ?: 0) >= MOVE_ZONES -> WeekDetail.MOVEMENT
        else -> null
    }

    /**
     * Monday–Saturday the figure comes out by the data (an outline, then light): the share of days
     * so far that have data, never more than the days that have passed. On Sunday it is held at
     * 6/7 until the ritual reveals it. A day without data takes nothing away.
     */
    fun reveal(i: TitanWeekInput): Float {
        if (i.ritualDone) return 1f
        val sunday = i.today.dayOfWeek == DayOfWeek.SUNDAY
        val withData = i.columns.count { !it.future && !it.noData }
        val cap = if (sunday) 6 else i.columns.count { !it.future }
        return (minOf(withData, cap).toFloat() / WeekScoring.DAYS).coerceIn(0f, 6f / 7f)
    }

    fun look(i: TitanWeekInput): TitanWeekLook {
        val days = WeekScoring.daysInSupport(i.columns)
        val pose = pose(i.columns, days)
        val sky = sky(i.sleepDebtHours, i.regularityPct)
        val detail = detail(i)
        val columns = (0 until WeekScoring.SUPPORTS).map { i.supports.getOrElse(it) { 0f }.coerceIn(0f, 1f) }
        return TitanWeekLook(
            weekStart = i.weekStart,
            pose = pose,
            mode = WeekScoring.dominantMode(i.columns),
            // a hard week is lit as warmly as any other: light never drops below a third
            light = (0.35f + 0.65f * days / WeekScoring.DAYS).coerceIn(0f, 1f),
            columns = columns,
            sky = sky,
            detail = detail,
            reveal = reveal(i),
            revealed = i.ritualDone,
            daysInSupport = days,
            seed = seed(i.weekStart, pose, sky, detail, columns),
        )
    }

    /** Deterministic: the same week gives the same seed; another week, another one. */
    fun seed(weekStart: LocalDate, pose: WeekPose, sky: WeekSky, detail: WeekDetail?, columns: List<Float>): Long {
        var h = 1125899906842597L
        fun mix(v: Long) {
            h = 31 * h + v
        }
        mix(weekStart.toEpochDay())
        mix(pose.ordinal.toLong())
        mix(sky.ordinal.toLong())
        mix((detail?.ordinal ?: -1).toLong())
        columns.forEach { mix((it * 20).roundToInt().toLong()) }
        return h
    }

    /** The year ribbon (A52 §2.7): one frame per week, newest last; 12 make a ring of the cycle. */
    fun ribbon(frames: List<TitanWeekLook>, year: Int): List<TitanWeekLook> =
        frames.filter { it.weekStart.year == year || it.weekStart.plusDays(6).year == year }.sortedBy { it.weekStart }

    const val RING_FRAMES = 12
}

/** «Поделиться» (A52 §2.7, Agent 7): only the image — no numbers of health, no days, no goals. */
data class ShareCard(val title: String, val slogan: String, val spoken: String, val look: TitanWeekLook) {
    val texts: List<String> get() = listOf(title, slogan, spoken)

    companion object {
        fun of(look: TitanWeekLook): ShareCard =
            ShareCard(WeekCopy.SHARE_TITLE, WeekCopy.SHARE_SLOGAN, listOfNotNull(look.pose.words, look.sky.words).joinToString(", "), look)
    }
}

/** A52 §2.7: one push a week, on Sunday — «Твой Титан недели готов» — the only push of the module. */
object WeekPush {
    const val FROM_HOUR = 18
    const val UNTIL_HOUR = 21

    /**
     * Due on Sunday between 18:00 and 21:00 if no push went out for this week yet, the ritual is
     * not already done, and nothing of safety is open (a crisis silences it).
     */
    fun due(now: java.time.LocalDateTime, pushedForWeek: LocalDate?, ritualDone: Boolean, crisis: Boolean): Boolean {
        val today = now.toLocalDate()
        if (today.dayOfWeek != DayOfWeek.SUNDAY || crisis || ritualDone) return false
        if (now.hour < FROM_HOUR || now.hour >= UNTIL_HOUR) return false
        return pushedForWeek != com.healthofatitan.app.domain.today.CalendarWeek.start(today)
    }
}
