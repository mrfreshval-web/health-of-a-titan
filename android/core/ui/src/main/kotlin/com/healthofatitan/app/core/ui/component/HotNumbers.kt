package com.healthofatitan.app.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType

const val TIME_CELL_START_TAG = "time_cell_start"
const val TIME_CELL_SPAN_TAG = "time_cell_span"
const val LEARNING_BAR_TAG = "learning_bar"

/**
 * One rule for numbers on every screen (UX review of build 119, R1: «10:22–11:2» + «2», «14/1» +
 * «4»). A number never wraps: tabular figures, one line, no soft wrap; a list gets a time column
 * of a fixed width that holds the longest clock; a ring holds one number, never a fraction.
 */
object HotNumbers {
    /** Time column of a list: «23:05» in [HotType.time] and «180 мин» under it. */
    val TimeColumn: Dp = 64.dp

    /** «07:05», «23:59»; minutes from 00:00, wraps over midnight. */
    fun clock(min: Int): String {
        val m = ((min % 1440) + 1440) % 1440
        return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
    }

    /** Minutes of a slot, over midnight too: 23:05 → 00:20 is 75. */
    fun span(startMin: Int, endMin: Int): Int = ((endMin - startMin) % 1440 + 1440) % 1440

    /** «45 мин»: one short number, never «1 ч 30 мин» in a column. */
    fun duration(min: Int): String = "$min мин"

    /** Tabular, lining figures on any style: numbers keep their width when they change. */
    fun tabular(style: TextStyle): TextStyle = style.merge(TextStyle(fontFeatureSettings = "tnum, lnum"))
}

/** A number on one line: tabular figures, no wrap, clipped rather than broken (R1). */
@Composable
fun HotNumber(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = HotType.ringValue,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        style = HotNumbers.tabular(style),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        textAlign = textAlign,
    )
}

/**
 * The time column of a plan row (R1): the start on the first line and the length small under
 * it — «10:52» / «60 мин» instead of «10:52–11:52» squeezed into two broken lines.
 */
@Composable
fun HotTimeCell(
    startMin: Int?,
    endMin: Int?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Column(modifier.width(HotNumbers.TimeColumn)) {
        if (startMin != null) {
            HotNumber(HotNumbers.clock(startMin), style = HotType.time, color = color, modifier = Modifier.testTag(TIME_CELL_START_TAG))
            if (endMin != null) {
                Spacer(Modifier.height(3.dp))
                HotNumber(
                    HotNumbers.duration(HotNumbers.span(startMin, endMin)),
                    style = MaterialTheme.typography.bodySmall,
                    color = HotThemeTokens.colors.textTertiary,
                    modifier = Modifier.testTag(TIME_CELL_SPAN_TAG),
                )
            }
        }
    }
}

/**
 * The personal norm as numbers (A25 P1, RegimeEngine): the norm works from [MIN_NIGHTS] nights
 * with HRV in the window and is full at [FULL_NIGHTS] — the 28-day window of the metric engine.
 */
object HotNorm {
    const val MIN_NIGHTS = 14
    const val FULL_NIGHTS = 28

    /** «Учусь тебя: 9 из 28 ночей», «Норма есть: 21 из 28 ночей», «Норма полная: 28 ночей». */
    fun label(nights: Int): String {
        val n = nights.coerceIn(0, FULL_NIGHTS)
        return when {
            n < MIN_NIGHTS -> "Учусь тебя: $n из $FULL_NIGHTS ${nightsWord(FULL_NIGHTS)}"
            n < FULL_NIGHTS -> "Норма есть: $n из $FULL_NIGHTS ${nightsWord(FULL_NIGHTS)}"
            else -> "Норма полная: $FULL_NIGHTS ${nightsWord(FULL_NIGHTS)}"
        }
    }

    /**
     * What the bar means, on a tap. Describes the metric engine (A25), no advice about the body.
     * Review: Agent 12 (copy), Agent 3 (wording about HRV / resting HR).
     */
    fun explain(nights: Int): String {
        val n = nights.coerceIn(0, FULL_NIGHTS)
        val now = when {
            n < MIN_NIGHTS -> "Ещё ${MIN_NIGHTS - n} ${nightsWord(MIN_NIGHTS - n)} — и включу Заряд и Разгон."
            n < FULL_NIGHTS -> "Норма уже работает; к $FULL_NIGHTS-й ночи станет точнее."
            else -> "Окно полное: каждое утро сравниваю с последними $FULL_NIGHTS ночами."
        }
        return "Норма — твой обычный уровень за последние $FULL_NIGHTS ночей: ВСР, пульс покоя, дыхание и сон. " +
            "С $MIN_NIGHTS-й ночи сравниваю с ней каждое утро. $now " +
            "Копятся только показатели HoT, сырые данные WHOOP не храню."
    }

    /** TalkBack. */
    fun spoken(nights: Int): String = label(nights).replace(":", ",")

    internal fun nightsWord(n: Int): String {
        val m100 = n % 100
        val m10 = n % 10
        return when {
            m100 in 11..14 -> "ночей"
            m10 == 1 -> "ночь"
            m10 in 2..4 -> "ночи"
            else -> "ночей"
        }
    }
}

/**
 * «Учусь тебя: 9 из 28 ночей» (R5): a bar instead of the «14/14» ring, a tick where the norm
 * starts to work (14) and the full window at the end (28). A tap opens what is learned and what
 * changes when it is ready.
 */
@Composable
fun HotLearningBar(nights: Int, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val gold = MaterialTheme.colorScheme.primary
    val track = HotThemeTokens.colors.ruleSoft
    val tick = HotThemeTokens.colors.textTertiary
    val n = nights.coerceIn(0, HotNorm.FULL_NIGHTS)
    val fraction = n / HotNorm.FULL_NIGHTS.toFloat()
    val milestone = HotNorm.MIN_NIGHTS / HotNorm.FULL_NIGHTS.toFloat()
    Column(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Что это") { open = !open }
            .semantics(mergeDescendants = true) {
                contentDescription = HotNorm.spoken(nights)
                stateDescription = if (open) "пояснение открыто" else "нажми, чтобы узнать"
            }
            .padding(vertical = 6.dp)
            .testTag(LEARNING_BAR_TAG),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HotNumber(
                HotNorm.label(nights),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).clearAndSetSemantics { },
            )
            Text(if (open) "⌃" else "⌄", style = MaterialTheme.typography.titleMedium, color = tick, modifier = Modifier.clearAndSetSemantics { })
        }
        Spacer(Modifier.height(10.dp))
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .drawBehind {
                    val h = 4.dp.toPx()
                    val y = size.height / 2
                    drawLine(track, Offset(h / 2, y), Offset(size.width - h / 2, y), h, StrokeCap.Round)
                    if (fraction > 0f) drawLine(gold, Offset(h / 2, y), Offset(h / 2 + (size.width - h) * fraction, y), h, StrokeCap.Round)
                    val x = h / 2 + (size.width - h) * milestone
                    drawLine(tick, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                },
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            HotNumber("0", style = HotType.mono, color = tick)
            HotNumber("${HotNorm.MIN_NIGHTS}", style = HotType.mono, color = tick)
            HotNumber("${HotNorm.FULL_NIGHTS}", style = HotType.mono, color = tick)
        }
        AnimatedVisibility(open) {
            Text(
                HotNorm.explain(nights),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
