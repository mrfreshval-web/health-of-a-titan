package com.healthofatitan.app.feature.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotSecondaryButton
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.domain.today.week.ExperimentWeek
import com.healthofatitan.app.domain.today.week.TitanWeekLook
import com.healthofatitan.app.domain.today.week.WeekCopy
import com.healthofatitan.app.domain.today.week.WeekTexture
import kotlin.math.PI
import kotlin.random.Random

const val WEEK_SEAL_TAG = "week_seal"
const val WEEK_EXPERIMENT_TAG = "week_experiment"
const val WEEK_EXPERIMENT_MARK_TAG = "week_experiment_mark"
const val WEEK_FITNESS_OFFER_TAG = "week_fitness_offer"

/**
 * «Печать недели» (A53 §2, §3.1, D-050) — the MVP of the Sculpture of the week: a medallion. The
 * obverse is the figure in its stone, carved by the day of the week only (seven notches on the
 * rim, Thursday 4/7 always); the reverse (a tap) is the five supports of the week. Placeholder
 * graphics until Agents 4 and 17 (Compose Canvas; the AGSL marble comes with the final pack).
 */
@Composable
fun WeekSeal(look: TitanWeekLook, modifier: Modifier = Modifier, revealOverride: Float? = null) {
    var reverse by rememberSaveable(look.weekStart.toString()) { mutableStateOf(false) }
    val tokens = HotThemeTokens.colors
    val mode = look.mode.hotMode().color()
    val carved = (revealOverride ?: look.reveal).coerceIn(0f, 1f)
    val rim = if (tokens.isDark) Color(0xFFB9B2A3) else Color(0xFF8E877A)
    val stone = if (tokens.isDark) listOf(Color(0xFF3A3A40), Color(0xFF2A2A30)) else listOf(Color(0xFFF2EFEA), Color(0xFFDAD5CC))
    val spoken = look.spoken + ", " + WeekCopy.carvedSpoken((carved * 7).toInt())
    Box(
        modifier
            .aspectRatio(1f)
            .testTag(WEEK_SEAL_TAG)
            .clip(CircleShape)
            .clickable(role = Role.Button) { reverse = !reverse }
            .semantics { contentDescription = if (reverse) WeekCopy.SEAL_REVERSE else spoken },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(stone))
            drawCircle(rim, style = Stroke(6.dp.toPx()))
            drawCircle(rim.copy(alpha = 0.5f), radius = size.minDimension / 2 - 12.dp.toPx(), style = Stroke(1.dp.toPx()))
            // seven notches of the rim: one a day, carved by the calendar
            val r = size.minDimension / 2 - 6.dp.toPx()
            repeat(7) { i ->
                val a = (-PI / 2 + i * 2 * PI / 7 - PI / 7 * 0.6).toFloat()
                val b = (a + 2 * PI / 7 * 0.6).toFloat()
                val lit = (i + 1) / 7f <= carved + 0.001f
                drawArc(
                    color = if (lit) mode else rim.copy(alpha = 0.35f),
                    startAngle = Math.toDegrees(a.toDouble()).toFloat(),
                    sweepAngle = Math.toDegrees((b - a).toDouble()).toFloat(),
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        if (reverse) {
            SealReverse(look, mode)
        } else {
            Box(Modifier.fillMaxSize().padding(22.dp).clip(CircleShape)) {
                WeekTitanFigure(look, Modifier.fillMaxSize(), revealOverride = carved)
                StoneBlock(look, carved)
            }
        }
    }
}

/** The uncarved stone over the figure: a layer flies off each morning; texture from sleep, never cracked. */
@Composable
fun StoneBlock(look: TitanWeekLook, carved: Float, modifier: Modifier = Modifier) {
    val tokens = HotThemeTokens.colors
    val stone = if (tokens.isDark) listOf(Color(0xFF4A4A52), Color(0xFF33333A)) else listOf(Color(0xFFEDE9E2), Color(0xFFD6D0C5))
    val vein = if (tokens.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
    Canvas(modifier.fillMaxSize()) {
        val top = size.height * carved
        if (carved < 1f) {
            drawRect(Brush.verticalGradient(stone, startY = top, endY = size.height), Offset(0f, top), Size(size.width, size.height - top))
            veins(look.seed, top, vein)
            // the fresh edge of the last blow
            drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, top), Offset(size.width, top), strokeWidth = 1.5.dp.toPx())
        }
        if (look.texture == WeekTexture.POLISHED && carved > 0f) {
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0f))), size = Size(size.width, top))
        } else if (carved > 0f) {
            val rnd = Random(look.seed xor 0x5EEDL)
            repeat(140) { drawCircle(vein, radius = 0.6.dp.toPx(), center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * top)) }
        }
    }
}

private fun DrawScope.veins(seed: Long, top: Float, color: Color) {
    val rnd = Random(seed)
    repeat(5) {
        var p = Offset(rnd.nextFloat() * size.width, top + rnd.nextFloat() * (size.height - top))
        repeat(6) {
            val q = Offset(p.x + (rnd.nextFloat() - 0.5f) * size.width * 0.3f, (p.y + (rnd.nextFloat() - 0.3f) * size.height * 0.15f).coerceIn(top, size.height))
            drawLine(color, p, q, strokeWidth = 1.dp.toPx())
            p = q
        }
    }
}

@Composable
private fun SealReverse(look: TitanWeekLook, mode: Color) {
    val rule = HotThemeTokens.colors.ruleSoft
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val n = 5
            val w = size.width / (n * 2f)
            val base = size.height * 0.8f
            val maxH = size.height * 0.6f
            look.columns.take(n).forEachIndexed { i, f ->
                val x = w / 2 + i * w * 2
                drawRoundRect(rule, Offset(x, base - maxH), Size(w, maxH), CornerRadius(w / 2))
                val h = maxH * (0.15f + 0.85f * f)
                drawRoundRect(mode.copy(alpha = 0.75f), Offset(x, base - h), Size(w, h), CornerRadius(w / 2))
            }
        }
        HotOverline(WeekCopy.SEAL_REVERSE, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * «Эксперимент недели» on the week (A53 §3.2): the hypothesis, «связь, не причина», the scale of
 * Monday–Saturday and one tap «Сегодня сделал». Nothing is graded.
 */
@Composable
fun BlockExperiment(e: ExperimentWeek, onMark: (Boolean) -> Unit) {
    val mode = MaterialTheme.colorScheme.primary
    val rule = HotThemeTokens.colors.ruleSoft
    HotCard(modifier = Modifier.fillMaxWidth().testTag(WEEK_EXPERIMENT_TAG)) {
        BlockTitle(WeekCopy.EXP_TITLE, WeekCopy.EXP_LINK)
        Spacer(Modifier.height(8.dp))
        Text(e.seed.hypothesis, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = e.scale.bridge.removeSuffix(" ›") },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            e.scale.days.forEach { d ->
                Canvas(Modifier.size(18.dp)) {
                    when (d) {
                        true -> drawCircle(mode)
                        false -> drawCircle(rule, style = Stroke(1.5.dp.toPx()))
                        null -> drawCircle(rule.copy(alpha = 0.5f), radius = size.minDimension / 4)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(e.scale.bridge.removePrefix("Эксперимент · ").removeSuffix(" ›"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.testTag(WEEK_EXPERIMENT_MARK_TAG)) {
            if (e.doneToday) {
                HotTextButton("✓ " + WeekCopy.EXP_DID_TODAY, onClick = { onMark(false) })
            } else {
                HotSecondaryButton(WeekCopy.EXP_DID_TODAY, onClick = { onMark(true) })
            }
        }
    }
}

/** A53 §3.3: the fitness age only by choice, after «что это и чего не значит». */
@Composable
fun FitnessOffer(onOptIn: () -> Unit) {
    HotCard(modifier = Modifier.fillMaxWidth().testTag(WEEK_FITNESS_OFFER_TAG)) {
        BlockTitle(WeekCopy.FIT_WHAT, WeekCopy.REVIEW)
        Spacer(Modifier.height(8.dp))
        Text(WeekCopy.FIT_WHAT_TEXT, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(WeekCopy.FIT_FIRST_AFTER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(8.dp))
        HotSecondaryButton(WeekCopy.FIT_OPT_IN, onClick = onOptIn)
    }
}
