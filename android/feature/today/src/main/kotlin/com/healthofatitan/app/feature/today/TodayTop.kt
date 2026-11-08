package com.healthofatitan.app.feature.today

import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.domain.today.DayMode
import com.healthofatitan.app.domain.today.DayRowView
import com.healthofatitan.app.domain.today.DayRules
import com.healthofatitan.app.domain.today.DayTask
import com.healthofatitan.app.domain.today.MeRules
import com.healthofatitan.app.domain.today.MeView
import com.healthofatitan.app.domain.today.Scenario
import com.healthofatitan.app.domain.today.TaskKind
import com.healthofatitan.app.domain.today.TodayCopy
import com.healthofatitan.app.domain.today.WeekPlan
import kotlin.math.roundToInt

/**
 * The new words of the top of «Сегодня» by mockup 05b (A66 §3.3, §3.4, §3.9, §3.11). DRAFT: waits
 * for the review of Agents 16 and 10 (wording) and 3 (health). The words compare a value with the
 * person's own norm only; no medical meaning is given.
 */
object TodayTopCopy {
    const val CHARGE = "Заряд"
    const val NIGHT = "Ночь"
    const val YOU = "Ты"
    const val ABOVE = "выше обычного"
    const val USUAL = "как обычно"
    const val BELOW = "ниже обычного"

    /** Бережно (A66 §3.3, А9): a low value is not a reproach. */
    const val BELOW_GENTLE = "ниже обычного — так бывает"
    const val NO_DATA = "нет данных"
    const val COUNTING = "досчитывается"

    /** «Ты» before «Рассвет» is answered: one tap opens «Как ты?» (A66 §3.3). */
    const val MARK = "отметить"
    const val NOW = "сейчас"
    const val LESS = "свернуть"
    const val NO_VALUE = "—"

    fun mode(word: String): String = "Режим $word"

    fun chargeSpoken(value: Int, word: String?): String = listOfNotNull("$CHARGE $value", word).joinToString(", ")

    fun dayClosed(percent: Int): String = "День закрыт на $percent %"

    /** «ещё 2 дела ›»: the rest of the tasks of the day, unfolded in place (A66 §3.4). */
    fun more(n: Int): String = "ещё $n ${tasksWord(n)}"

    /** дело · дела · дел. */
    fun tasksWord(n: Int): String {
        val mod100 = n % 100
        val mod10 = n % 10
        return when {
            mod100 in 11..14 -> "дел"
            mod10 == 1 -> "дело"
            mod10 in 2..4 -> "дела"
            else -> "дел"
        }
    }

    fun ofTarget(title: String, done: Int, target: Int): String = "$title $done из $target"

    /** Every user-facing string above, for the stop-dictionary test (T-13). */
    fun all(): List<String> = listOf(
        CHARGE, NIGHT, YOU, ABOVE, USUAL, BELOW, BELOW_GENTLE, NO_DATA, COUNTING, MARK, NOW, LESS,
        mode("Ровно"), chargeSpoken(57, USUAL), dayClosed(40), more(1), more(2), more(5), ofTarget("Силовые", 2, 3),
    )
}

/** Which metric a tile shows (A66 §3.3). */
enum class MetricKind { CHARGE, NIGHT, YOU }

/**
 * One tile of «Заряд · Ночь · Ты» (A66 §3.3): the number in the arc, the word to the norm, where a
 * tap leads. [arc] null is «нет данных»: the dashed arc and «—».
 */
data class MetricTileView(
    val kind: MetricKind,
    val label: String,
    val value: String?,
    val word: String?,
    val arc: Float?,
    val band: ClosedFloatingPointRange<Float>?,
    val route: String,
) {
    val shown: String get() = value ?: TodayTopCopy.NO_VALUE

    /** The tile read as one sentence: «Заряд 68, выше обычного» — never «дуга, 68». */
    val spoken: String get() = listOfNotNull(label + (value?.let { " $it" } ?: ""), word).joinToString(", ")
}

/** «Сейчас» (A66 §3.4): the one action on top, the rest of the tasks folded under it. */
data class NowPick(val now: DayTask?, val rest: List<DayTask>, val link: String?) {
    companion object {
        val EMPTY = NowPick(null, emptyList(), null)
    }
}

/**
 * The pure rules of the top of «Сегодня», variant B «Тише» (A66 v0.5 §1.1, §3.2а–3.4): where the
 * blocks stand, how large the Titan is, where the sun stands, how high the columns glow, what the
 * three tiles say and which task is «Сейчас».
 */
object TodayTopRules {
    /** The scale of Заряд (A25): 5–95, the personal norm 50, «как обычно» |z| < 0.5 → 43–57. */
    const val CHARGE_MIN = 5
    const val CHARGE_MAX = 95
    const val NORM = 50
    const val USUAL_LOW = 43
    const val USUAL_HIGH = 57

    /** The screen above the scene (status bar and the header, A66 §3.2а): the scene starts at y 100. */
    const val SCENE_TOP = 100f

    /**
     * Variant B «Тише» (A66 v0.5 §3.2а, mockup `today-calm/B-tishe.png`, `calm.py` `variant_b`): the
     * places on the 412 × 915 dp screen, y from the top of the screen. The scene runs from under the
     * header to the line of the mode word; the haiku, the tiles, «Сейчас» and «День» follow it.
     */
    const val FEET_Y = 322f
    const val MODE_WORD_Y = 334f
    const val MODE_WORD_HEIGHT = 20f
    const val HAIKU_Y = 360f
    const val HAIKU_SIZE = 15f
    const val HAIKU_LINE = 19f
    const val HAIKU_LINES = 3
    const val METRICS_Y = 436f
    const val METRIC_ARC = 50f
    const val NOW_Y = 540f
    const val NOW_HEIGHT = 84f
    const val DAY_Y = 644f
    const val DAY_END_Y = 760f

    /** The bottom of the screen the tab bar leaves free (mockup: the bar starts at y 811). */
    const val TAB_BAR_Y = 811f

    /** The gap between the blocks of «Сегодня» (HotSpacing.cardGap): the tiles end where it begins. */
    const val BLOCK_GAP = 16f

    /** The scene ends under the mode word: the word stands inside it, on the ground. */
    const val SCENE_HEIGHT = MODE_WORD_Y + MODE_WORD_HEIGHT - SCENE_TOP

    /** The mode word in dp from the top of the scene. */
    const val MODE_WORD_TOP = MODE_WORD_Y - SCENE_TOP

    /** From the end of the scene to the haiku, from the haiku to the tiles. */
    const val HAIKU_GAP = HAIKU_Y - SCENE_TOP - SCENE_HEIGHT
    const val METRICS_GAP = METRICS_Y - HAIKU_Y - HAIKU_LINE * HAIKU_LINES

    /** The row of the three tiles: from its top to the gap before «Сейчас». */
    const val METRICS_HEIGHT = NOW_Y - BLOCK_GAP - METRICS_Y

    /** The sun (A66 §3.2а): y = 322 − 1.99 × Заряд on the screen at the reference frame, a 38 dp disk. */
    const val SUN_PER_POINT = 1.99f
    const val SUN_DIAMETER = 38f

    /** The one glow of the screen: the sky around the sun (A66 §3.2а «ореол неба 0,2»). */
    const val SKY_GLOW = 0.2f

    /** The columns are quieter (A66 §3.2а): the render desaturated and dimmed, the glow of the day 0.32. */
    const val COLUMN_SATURATION = 0.55f
    const val COLUMN_BRIGHTNESS = 0.85f
    const val COLUMN_GLOW = 0.32f

    /** The Titan frame on «Сегодня» (A66 §3.2а): 280 dp, left 66 dp on 412, shares of the side. */
    const val FRAME = 280f
    const val FRAME_LEFT = 66f
    const val FEET = 0.875f
    const val CAPITAL = 0.134f
    const val BASE = 0.893f
    val COLUMNS: List<ClosedFloatingPointRange<Float>> = listOf(0.115f..0.29f, 0.71f..0.885f)

    /** «Ты» is 1–5. */
    const val SELF_MAX = 5

    /** The night arc runs 0–12 h; the band is the need ± 30 min. */
    const val NIGHT_SCALE_MIN = 12 * 60
    const val NIGHT_BAND_MIN = 30

    /** The z of a usual value (A66 §3.3: |z| < 0.5). */
    const val USUAL_Z = 0.5

    /** The feet of the Titan in dp from the top of the scene. */
    val feetY: Float get() = FEET_Y - SCENE_TOP

    /** The top of the reference frame in dp from the top of the scene (above it: the frame is cut). */
    val frameTop: Float get() = feetY - FEET * FRAME

    /** The top of the head in the render, as a share of the frame (the capitals stand higher). */
    const val HEAD = 0.203f

    /**
     * The rule of the head (A66 §3.2а, obligatory): the top of the head is never higher than the
     * bottom of the header + 24 dp, on any width. The scene starts at the bottom of the header.
     */
    const val HEAD_ROOM = 24f

    /** The width of the mockup the frame was drawn on. */
    const val SCREEN_WIDTH = 412f

    /**
     * The side of the Titan frame on a scene [width] dp wide: 280 : 412 of the width, but never so
     * large that the head comes closer than [HEAD_ROOM] to the header on a wide phone.
     */
    fun frameSide(width: Float): Float = minOf(width * FRAME / SCREEN_WIDTH, (feetY - HEAD_ROOM) / (FEET - HEAD))

    /** The left edge of the frame on a scene [width] dp wide: the axis of the Titan is the centre. */
    fun frameLeft(width: Float, side: Float = frameSide(width)): Float = (width - side) / 2f

    /** The top of the head in dp from the top of the scene for a frame of [side] dp. */
    fun headTop(side: Float): Float = feetY - (FEET - HEAD) * side

    /**
     * The centre of the sun, in dp from the top of the scene: linear in Заряд (A66 §1.1), from the
     * line of the feet (Заряд 0) to 10 dp above the head (Заряд 100); its travel follows the frame
     * when a narrow or wide phone resizes it. Never stretched above or squeezed below the scale.
     */
    fun sunCenterY(charge: Float, side: Float = FRAME): Float =
        feetY - SUN_PER_POINT * (side / FRAME) * charge.coerceIn(CHARGE_MIN.toFloat(), CHARGE_MAX.toFloat())

    /**
     * The share of the closed day (A66 §1.1): the mean of the counted rows of «День», each at most 1
     * (under `ED_CARE` two rows, A66 §3.5). A row without a norm to count against is left out.
     */
    fun dayShare(rows: List<DayRowView>): Float = DayRules.share(rows)

    /** The top of the column glow as a share of the frame: from the base up by the share of the day. */
    fun glowTop(share: Float): Float = BASE - (BASE - CAPITAL) * share.coerceIn(0f, 1f)

    fun percent(share: Float): Int = (share.coerceIn(0f, 1f) * 100f).roundToInt()

    /** The word to the norm (A66 §3.3); Бережно softens «ниже», the return has no «ниже» at all. */
    fun word(z: Double?, gentle: Boolean, returning: Boolean): String? = when {
        z == null -> null
        z >= USUAL_Z -> TodayTopCopy.ABOVE
        z > -USUAL_Z -> TodayTopCopy.USUAL
        returning -> null
        gentle -> TodayTopCopy.BELOW_GENTLE
        else -> TodayTopCopy.BELOW
    }

    /** Заряд to its corridor 43–57 (z = (Заряд − 50) / 15, A25). */
    fun chargeWord(charge: Int, gentle: Boolean, returning: Boolean): String? = when {
        charge > USUAL_HIGH -> TodayTopCopy.ABOVE
        charge >= USUAL_LOW -> TodayTopCopy.USUAL
        else -> word(-1.0, gentle, returning)
    }

    fun chargeArc(charge: Int): Float = (charge.coerceIn(CHARGE_MIN, CHARGE_MAX) - CHARGE_MIN).toFloat() / (CHARGE_MAX - CHARGE_MIN)

    val chargeBand: ClosedFloatingPointRange<Float> = chargeArc(USUAL_LOW)..chargeArc(USUAL_HIGH)

    fun nightArc(minutes: Int): Float = (minutes.toFloat() / NIGHT_SCALE_MIN).coerceIn(0f, 1f)

    fun nightBand(needMinutes: Int?): ClosedFloatingPointRange<Float>? =
        needMinutes?.let { nightArc(it - NIGHT_BAND_MIN)..nightArc(it + NIGHT_BAND_MIN) }

    fun selfArc(self: Int): Float = (self.coerceIn(1, SELF_MAX) - 1).toFloat() / (SELF_MAX - 1)

    /**
     * The three tiles (A66 §3.3) from what «Я сегодня» already shows ([me] holds the gates: no WHOOP,
     * calibration, the sober screen). Without a value: «—», the dashed arc and an honest word.
     *
     * @param nightMinutes the length of the night, when [me] shows it
     * @param needMinutes the need of the night
     * @param nightZ the night against the person's own norm (SLP z of today), when known
     */
    fun tiles(
        me: MeView,
        scenario: Scenario,
        nightMinutes: Int?,
        needMinutes: Int?,
        nightZ: Double?,
    ): List<MetricTileView> {
        val gentle = scenario == Scenario.GENTLE || me.mode == DayMode.BEREZHNO
        val returning = scenario == Scenario.RETURN
        val pending = scenario == Scenario.NIGHT_PENDING
        val charge = me.charge
        val chargeTile = MetricTileView(
            kind = MetricKind.CHARGE,
            label = TodayTopCopy.CHARGE,
            value = charge?.toString(),
            word = when {
                charge != null -> chargeWord(charge, gentle, returning)
                pending -> TodayTopCopy.COUNTING
                me.calibration != null -> null
                else -> TodayTopCopy.NO_DATA
            },
            arc = charge?.let(::chargeArc),
            band = charge?.let { chargeBand },
            route = HotRoute.WHY,
        )
        val night = nightMinutes?.takeIf { me.night != null }
        val nightTile = MetricTileView(
            kind = MetricKind.NIGHT,
            label = TodayTopCopy.NIGHT,
            value = night?.let(MeRules::clock),
            word = when {
                night == null -> if (pending) TodayTopCopy.COUNTING else TodayTopCopy.NO_DATA
                else -> word(nightZ, gentle, returning) ?: me.need?.let(TodayCopy::need)
            },
            arc = night?.let(::nightArc),
            band = night?.let { nightBand(needMinutes) },
            route = HotRoute.WHY,
        )
        val self = me.self
        val youTile = MetricTileView(
            kind = MetricKind.YOU,
            label = TodayTopCopy.YOU,
            value = self?.let { "$it/$SELF_MAX" },
            word = if (self != null) TodayCopy.YOU_CAPTION else TodayTopCopy.MARK,
            arc = self?.let(::selfArc),
            band = null,
            route = if (self != null) HotRoute.WHY else HotRoute.CHECKIN,
        )
        return listOf(chargeTile, nightTile, youTile)
    }

    /** The scene as one TalkBack node (A66 §3.11): «Режим Ровно. Заряд 57, как обычно. День закрыт на 40 %». */
    fun sceneSpoken(mode: DayMode?, charge: MetricTileView?, share: Float): String = listOfNotNull(
        mode?.let { TodayTopCopy.mode(it.word) },
        charge?.let { c -> c.value?.toIntOrNull()?.let { TodayTopCopy.chargeSpoken(it, c.word) } },
        TodayTopCopy.dayClosed(percent(share)),
    ).joinToString(". ")

    /**
     * «Сейчас» (A66 §3.4, step 5 of its priority): the first open task of the day. With no source at
     * all the first step is to connect one (A66 §3.8 «Первый день»). The rest keep their order.
     */
    fun now(tasks: List<DayTask>, scenario: Scenario, week: WeekPlan?): NowPick {
        val connect = tasks.firstOrNull { it.kind == TaskKind.CONNECT_WHOOP }.takeIf { scenario == Scenario.NO_SOURCE }
        val now = connect ?: tasks.firstOrNull { !it.done } ?: return NowPick(null, tasks, null)
        return NowPick(now, tasks.filter { it.id != now.id }, link(now, week))
    }

    /**
     * The line of the link under «Сейчас» (A66 §3.4): «→ Силовые 2 из 3 → Пробежать 5 км» — the week
     * goal with its progress, then the Path goal the step serves. Milestones of the Path are not in
     * the week's data yet, so the goal of the Path is the last link. Without either: why the task.
     */
    fun link(task: DayTask, week: WeekPlan?): String? {
        val goal = week?.goal(task.weekGoalId)
        val weekPart = goal?.let { TodayTopCopy.ofTarget(it.title, it.progress.coerceAtMost(it.target), it.target) } ?: task.week
        val parts = listOfNotNull(weekPart, task.goal)
        return if (parts.isEmpty()) task.why else parts.joinToString(" ") { "→ $it" }
    }
}
