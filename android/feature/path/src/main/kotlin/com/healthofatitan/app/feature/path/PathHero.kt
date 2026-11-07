package com.healthofatitan.app.feature.path

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.component.TitanLights
import com.healthofatitan.app.core.ui.component.TitanScene
import com.healthofatitan.app.core.ui.component.titanRingPoint
import com.healthofatitan.app.core.ui.component.titanRingRadius
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.Titanium
import com.healthofatitan.app.domain.path.GoalProgress
import com.healthofatitan.app.domain.path.PathRules
import com.healthofatitan.app.domain.path.WeekState

/**
 * The hero of «Путь» (A42 §3.1, j2-path): the Titan in the ring of the goal — 12 week segments
 * with three milestone points. A lit segment is a week whose goal was met; a week «мимо» keeps
 * its contour (never red, never empty); the current week fills as its days count; weeks ahead are
 * a soft track. The ring and the figure are one hero, the only thing that glows (A37 §9.5).
 */
@Composable
fun PathHero(progress: GoalProgress?, modifier: Modifier = Modifier) {
    val tokens = HotThemeTokens.colors
    val light = MaterialTheme.colorScheme.primary
    val glow = tokens.glow
    // a milestone dot sits on the ring over the track: punch it out with the screen's depth
    val punch = if (tokens.isDark) Titanium.Depth else Titanium.Porcelain
    val description = progress?.let { PathTexts.where(it.stage, it.week) } ?: PathTexts.GOAL_OVERLINE
    Box(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxSize()) {
            val r = titanRingRadius(size)
            val top = titanRingPoint(size, 0f)
            val bottom = titanRingPoint(size, 0.5f)
            val c = Offset((top.x + bottom.x) / 2f, (top.y + bottom.y) / 2f)
            val stroke = 5.dp.toPx()
            val weeks = progress?.weeks
            for (i in 0 until PathRules.CYCLE_WEEKS) {
                val start = -90f + i * SEGMENT + GAP / 2f
                val sweep = SEGMENT - GAP
                val mark = weeks?.get(i)
                when (mark?.state ?: WeekState.FUTURE) {
                    WeekState.LIT -> {
                        if (glow) arc(c, r, start, sweep, light.copy(alpha = 0.18f), stroke * 3f)
                        arc(c, r, start, sweep, light, stroke)
                    }
                    WeekState.CURRENT -> {
                        arc(c, r, start, sweep, tokens.rule, stroke)
                        val f = if (mark!!.target > 0) (mark.done.toFloat() / mark.target).coerceIn(0f, 1f) else 0f
                        if (f > 0f) arc(c, r, start, sweep * f, light.copy(alpha = 0.75f), stroke)
                    }
                    WeekState.MISSED -> arc(c, r, start, sweep, tokens.rule, 1.5.dp.toPx())
                    WeekState.FUTURE -> arc(c, r, start, sweep, tokens.ruleSoft, stroke)
                }
            }
            // three milestones: the end of each 4-week stage (A42 §3.3)
            for (m in 1..PathRules.STAGES) {
                val a = Math.toRadians((-90.0 + m * PathRules.STAGE_WEEKS * SEGMENT)).toFloat()
                val p = Offset(c.x + r * kotlin.math.cos(a), c.y + r * kotlin.math.sin(a))
                val passed = progress != null && (progress.finished || progress.week > m * PathRules.STAGE_WEEKS)
                drawCircle(punch, 6.dp.toPx(), p)
                if (passed) {
                    drawCircle(light, 4.5.dp.toPx(), p)
                } else {
                    drawCircle(light.copy(alpha = if (progress == null) 0.35f else 0.8f), 4.5.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
                }
            }
        }
        TitanScene(Modifier.fillMaxSize(), light = TitanLights.default())
    }
}

private const val SEGMENT = 360f / 12f
private const val GAP = 4f

private fun DrawScope.arc(c: Offset, r: Float, start: Float, sweep: Float, color: Color, width: Float) {
    drawArc(color, start, sweep, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(width, cap = StrokeCap.Round))
}
