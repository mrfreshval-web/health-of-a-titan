package com.healthofatitan.app.core.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import java.util.Locale
import kotlin.math.roundToInt

const val STATUS_RINGS_TAG = "status_rings"

/**
 * The three rings of the header (D-029, A11 §1.3, A37 §10 «фишка 2»): load against the mode's
 * ceiling, steps against the goal, time left until bedtime. Every value comes from data (D-027);
 * a missing value is an honest dashed ring, never a zero.
 *
 * @param strain today's WHOOP day strain (memory only, R-11), null = no data yet
 * @param strainCeiling the mode's ceiling for the day
 * @param steps / stepsGoal null = no step source yet (dashed)
 * @param untilBedMin minutes left until the bedtime anchor; null = no plan yet
 * @param dayFraction share of the waking day already passed (wake → bedtime), the third ring
 * @param bedtimeMin the bedtime anchor, minutes from 00:00 (for «почему»)
 */
@Immutable
data class StatusRings(
    val strain: Double? = null,
    val strainCeiling: Double? = null,
    val steps: Int? = null,
    val stepsGoal: Int? = null,
    val untilBedMin: Int? = null,
    val dayFraction: Float? = null,
    val bedtimeMin: Int? = null,
    val mode: HotMode? = null,
) {
    val strainFraction: Float? get() = if (strain != null && strainCeiling != null && strainCeiling > 0) (strain / strainCeiling).toFloat() else null
    val stepsFraction: Float? get() = if (steps != null && stepsGoal != null && stepsGoal > 0) steps.toFloat() / stepsGoal else null

    companion object {
        val Empty = StatusRings()
    }
}

/**
 * Words of the rings: numbers in the pill, the TalkBack sentence and the one-line «почему».
 * Review: Agent 12 (UX copy), Agent 10 (tone).
 */
object StatusRingsTexts {
    private val ru: Locale = Locale.forLanguageTag("ru")

    /** «8,4» — one decimal, Russian comma. */
    fun strain(v: Double): String = String.format(ru, "%.1f", v)

    /** «6,8k», «940». */
    fun steps(v: Int): String = if (v >= 1000) String.format(ru, "%.1fk", v / 1000.0) else v.toString()

    /** «9:13» — hours and minutes left; «0:00» after bedtime. */
    fun until(min: Int): String {
        val m = min.coerceAtLeast(0)
        return "${m / 60}:${(m % 60).toString().padStart(2, '0')}"
    }

    fun clock(min: Int): String {
        val m = ((min % 1440) + 1440) % 1440
        return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
    }

    private fun hoursMinutes(min: Int): String {
        val m = min.coerceAtLeast(0)
        val h = m / 60
        val r = m % 60
        return listOfNotNull(
            if (h > 0) "$h ${plural(h, "час", "часа", "часов")}" else null,
            if (r > 0 || h == 0) "$r ${plural(r, "минута", "минуты", "минут")}" else null,
        ).joinToString(" ")
    }

    private fun stepsSpoken(v: Int): String = String.format(ru, "%,d", v).replace(' ', ' ').replace(' ', ' ')

    private fun ceiling(v: Double): String = if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else strain(v)

    /** TalkBack, A11 §1.3: «Нагрузка 8,4 из 12, шаги 6 800 из 9 000, до отбоя 9 часов 13 минут». */
    fun spoken(r: StatusRings): String = listOf(
        if (r.strain != null && r.strainCeiling != null) "Нагрузка ${strain(r.strain)} из ${ceiling(r.strainCeiling)}"
        else if (r.strain != null) "Нагрузка ${strain(r.strain)}" else "Нагрузка: нет данных",
        if (r.steps != null && r.stepsGoal != null) "шаги ${stepsSpoken(r.steps)} из ${stepsSpoken(r.stepsGoal)}"
        else if (r.steps != null) "шаги ${stepsSpoken(r.steps)}" else "шаги: нет данных",
        r.untilBedMin?.let { "до отбоя ${hoursMinutes(it)}" } ?: "до отбоя: нет плана",
    ).joinToString(", ")

    /** The one line «почему» under the header after a tap (A37 §2: число → строка «почему»). */
    fun why(r: StatusRings): String = listOf(
        when {
            r.strainCeiling != null && r.mode != null -> "потолок нагрузки «${r.mode.word}» — ${ceiling(r.strainCeiling)}"
            else -> "нагрузку WHOOP пришлёт после синхронизации"
        },
        if (r.steps == null) "шаги появятся с Health Connect" else "шаги телефона",
        r.bedtimeMin?.let { "отбой в ${clock(it)}" } ?: "отбой — когда будет план",
    ).joinToString(" · ")

    private fun plural(n: Int, one: String, few: String, many: String): String {
        val m10 = n % 10
        val m100 = n % 100
        return when {
            m10 == 1 && m100 != 11 -> one
            m10 in 2..4 && m100 !in 12..14 -> few
            else -> many
        }
    }
}

/**
 * The glass pill with the three rings (mockup s2: «◔ 8,4 ◯ 6,8k ◔ 9:13»). One 48 dp touch
 * target: a tap shows the «почему» line ([onClick]). Rings spring to their value (A37 §9.6).
 */
@Composable
fun HotStatusRings(
    rings: StatusRings,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    expanded: Boolean = false,
) {
    val tokens = HotThemeTokens.colors
    val strainColor = rings.mode?.color() ?: MaterialTheme.colorScheme.primary
    val stepsColor = MaterialTheme.colorScheme.onSurface
    val bedColor = tokens.modeBerezhno
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .heightIn(min = 48.dp)
            .border(1.dp, tokens.hairline, CircleShape)
            .drawBehind {
                drawRoundRect(
                    Brush.verticalGradient(listOf(tokens.glassTop, tokens.glassBottom)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                )
            }
            .semantics(mergeDescendants = true) {
                contentDescription = StatusRingsTexts.spoken(rings)
                stateDescription = if (expanded) "пояснение открыто" else "нажми, чтобы узнать почему"
                role = Role.Button
            }
            .testTag(STATUS_RINGS_TAG),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 14.dp, top = 9.dp, bottom = 9.dp).animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RingValue(rings.strainFraction, rings.strain?.let(StatusRingsTexts::strain), strainColor)
            RingValue(rings.stepsFraction, rings.steps?.let(StatusRingsTexts::steps), stepsColor)
            RingValue(rings.dayFraction, rings.untilBedMin?.let(StatusRingsTexts::until), bedColor)
        }
    }
}

@Composable
private fun RingValue(fraction: Float?, value: String?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics { }) {
        MiniRing(fraction, color, dashed = value == null)
        Spacer(Modifier.width(6.dp))
        Text(value ?: "—", style = HotType.ringValue, color = if (value == null) HotThemeTokens.colors.textTertiary else MaterialTheme.colorScheme.onSurface)
    }
}

/** A thin ring, 18 dp (A11 §1.3): track + arc from 12 o'clock; dashed when there is no data. */
@Composable
fun MiniRing(fraction: Float?, color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp, dashed: Boolean = fraction == null) {
    val track = HotThemeTokens.colors.rule
    val f by animateFloatAsState((fraction ?: 0f).coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = 380f), label = "ring")
    Spacer(
        modifier
            .size(size)
            .drawBehind {
                val w = 2.dp.toPx()
                val inset = w / 2
                val arcSize = Size(this.size.width - w, this.size.height - w)
                if (dashed) {
                    drawArc(
                        track, 0f, 360f, false, Offset(inset, inset), arcSize,
                        style = Stroke(w, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 3.dp.toPx()))),
                    )
                    return@drawBehind
                }
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
                if (f > 0f) drawArc(color, -90f, 360f * f, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            },
    )
}
