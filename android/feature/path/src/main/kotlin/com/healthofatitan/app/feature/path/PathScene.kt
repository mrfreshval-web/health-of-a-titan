package com.healthofatitan.app.feature.path

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.core.ui.theme.PathColors
import com.healthofatitan.app.core.ui.theme.Titanium
import com.healthofatitan.app.feature.path.logic.LeverView
import com.healthofatitan.app.feature.path.logic.MilestoneView
import com.healthofatitan.app.feature.path.logic.PathStage
import com.healthofatitan.app.feature.path.logic.PathV3Copy
import com.healthofatitan.app.feature.path.logic.StageCell
import com.healthofatitan.app.feature.path.logic.StageWeeksView

const val PATH_SCENE_TAG = "path_scene"
const val PATH_MILESTONE_TAG = "path_milestone"
const val PATH_WEEK_LINK_TAG = "path_week_link"
const val PATH_STAGE_WEEKS_TAG = "path_stage_weeks"
const val PATH_PARTS_TAG = "path_parts"

/** The frame of «Путь» v3 (mockup 07): the scene 340 dp, the horizon at 214 dp. */
private val SCENE_H = 340.dp
private val HORIZON_Y = 214.dp
private val HORIZON_TITAN = 150.dp
private val YOU_MAX = 118.dp

/** The feet stand at 0.875 of the frame of titan_t (A66 §11.2). */
private const val FEET = 0.875f

/**
 * `PathScene` (A66 §5.2, §11.1): the trail from the foreground to the horizon, the stelae of the
 * milestones 4 · 8 · 12, «ты» on the trail at the current week and the Titan of the horizon — the
 * same figure as a mask filled with warm light, with a halo (§1.1 «Свет-фигура» = the future).
 * The art is one TalkBack node (§5.7); the title, «Зачем» and the menu above it stay their own.
 *
 * @param week the week of the stage, 0 without a goal (an empty trail, soft light without a figure)
 * @param lightOnly the Titan of the horizon is a pillar of light (ed_care or «Форма», A57 §4.1)
 */
@Composable
internal fun PathScene(
    week: Int,
    description: String,
    lightOnly: Boolean,
    paused: Boolean,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    val tokens = HotThemeTokens.colors
    val dawn = dawnText()
    val glow = Titanium.Dawn
    val hasGoal = week > 0
    BoxWithConstraints(modifier.fillMaxWidth().height(SCENE_H).testTag(PATH_SCENE_TAG)) {
        val w = maxWidth
        val cx = w / 2
        val ground = SCENE_H - HORIZON_Y
        val halfBottom = w * 0.136f
        fun half(g: Float): Dp = 2.dp + (halfBottom - 2.dp) * g
        Box(Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = description }) {
            Canvas(Modifier.fillMaxSize()) {
                val hy = HORIZON_Y.toPx()
                val c = size.width / 2
                // the one warm light on the horizon and the ground below it
                drawRect(
                    Brush.radialGradient(
                        listOf(glow.copy(alpha = if (tokens.isDark) 0.26f else 0.30f), Color.Transparent),
                        center = Offset(c, hy),
                        radius = size.width * 0.55f,
                    ),
                )
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, if (tokens.isDark) Color.Black.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.03f)), startY = hy, endY = size.height),
                    topLeft = Offset(0f, hy),
                    size = Size(size.width, size.height - hy),
                )
                drawLine(glow.copy(alpha = 0.25f), Offset(0f, hy), Offset(size.width, hy), strokeWidth = 1.dp.toPx())
                // the trail
                val hb = halfBottom.toPx()
                val bottom = size.height
                val trail = Path().apply {
                    moveTo(c - hb, bottom)
                    cubicTo(c - hb * 0.46f, bottom - (bottom - hy) * 0.4f, c - hb * 0.1f, hy + (bottom - hy) * 0.3f, c - 2.dp.toPx(), hy)
                    lineTo(c + 2.dp.toPx(), hy)
                    cubicTo(c + hb * 0.1f, hy + (bottom - hy) * 0.3f, c + hb * 0.46f, bottom - (bottom - hy) * 0.4f, c + hb, bottom)
                    close()
                }
                drawPath(trail, canvasInk(tokens.isDark).copy(alpha = 0.07f))
                drawLine(
                    glow.copy(alpha = 0.35f), Offset(c, bottom), Offset(c, hy), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx())),
                )
            }
            // the stelae of the milestones
            if (hasGoal) {
                PathStage.stelae().forEach { s ->
                    val g = 1f - s.depth
                    val sw = 14.dp * s.scale
                    val sh = 36.dp * s.scale
                    val y = HORIZON_Y + ground * g
                    val x = if (s.side > 0) cx + half(g) + 6.dp else cx - half(g) - 6.dp - sw
                    Box(
                        Modifier
                            .offset(x, y - sh)
                            .size(sw, sh)
                            .clip(RoundedCornerShape(topStart = sw / 2, topEnd = sw / 2, bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(Brush.verticalGradient(tokens.columnLight)),
                    )
                    Text(
                        s.week.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = maxOf(10f, 13f * s.scale).sp),
                        color = dawn,
                        modifier = Modifier.offset(if (s.side > 0) x + sw + 4.dp else x - 14.dp, y - sh - 2.dp),
                    )
                }
            }
            // the Titan of the horizon: light, larger than «ты», of another material (В3)
            val top = HORIZON_Y - HORIZON_TITAN * FEET
            Box(Modifier.offset(cx - HORIZON_TITAN / 2, top).size(HORIZON_TITAN)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawOval(
                        Brush.radialGradient(listOf(Color(0xFFFFD08A).copy(alpha = 0.35f), Color.Transparent)),
                        topLeft = Offset(size.width * 0.18f, size.height * 0.12f),
                        size = Size(size.width * 0.64f, size.height * 0.8f),
                    )
                }
                if (hasGoal && !lightOnly) {
                    LightFigure(Modifier.fillMaxSize().blur(4.dp).graphicsLayer { alpha = 0.8f; scaleX = 1.04f; scaleY = 1.04f }, listOf(glow, glow))
                    LightFigure(Modifier.fillMaxSize().graphicsLayer { alpha = if (paused) 0.75f else 1f }, Titanium.LightWarm)
                }
            }
            if (hasGoal) {
                Text(
                    PathV3Copy.HORIZON.replace(" ", "\n"),
                    style = HotType.horizonNameLarge,
                    color = dawn,
                    modifier = Modifier.offset(cx + 58.dp, HORIZON_Y - 64.dp),
                )
            }
            // «ты» on the trail
            PathStage.you(week)?.let { m ->
                val g = 1f - m.depth
                val s = (YOU_MAX * m.scale).coerceAtLeast(40.dp)
                val y = HORIZON_Y + ground * g
                val x = cx - half(g) - s * 0.28f
                Image(
                    painterResource(R.drawable.core_ui_titan_t),
                    contentDescription = null,
                    modifier = Modifier.offset(x - s / 2, y - s * FEET).size(s),
                )
                Text(
                    PathV3Copy.youLabel(week),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.offset(x + s * 0.3f, y - s * 0.32f),
                )
            }
        }
        header()
    }
}

/** A plain onSurface for the canvas (a DrawScope cannot read the theme). */
private fun canvasInk(dark: Boolean): Color = if (dark) Titanium.Bone else Titanium.Ink

/** titan_t as an alpha mask filled with light (BlendMode.SrcIn over its own alpha). */
@Composable
private fun LightFigure(modifier: Modifier, light: List<Color>) {
    Image(
        painterResource(R.drawable.core_ui_titan_t),
        contentDescription = null,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(light), blendMode = BlendMode.SrcIn)
            },
    )
}

/**
 * `MilestoneCard` (A66 §5.2): the next milestone, the measurement, the lever of the stage in one line
 * and «Цели недели · 2 из 3 ›». A paused or held path shows no dates (В4).
 */
@Composable
internal fun MilestoneCard(
    milestone: MilestoneView?,
    hold: Pair<String, String>?,
    lever: LeverView?,
    weekLink: String?,
    onWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PathColors.current
    val steel = MaterialTheme.colorScheme.onSurfaceVariant
    HotCard(modifier = modifier.fillMaxWidth().testTag(PATH_MILESTONE_TAG), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        when {
            hold != null -> {
                Text(hold.first, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(hold.second, style = MaterialTheme.typography.bodyMedium, color = steel)
            }
            milestone != null -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(milestone.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Text(milestone.date, style = MaterialTheme.typography.bodySmall, color = steel, softWrap = false)
                }
                milestone.measure?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = steel)
                }
                if (milestone.meeting) {
                    Spacer(Modifier.height(4.dp))
                    Text(PathV3Copy.MEETING, style = MaterialTheme.typography.bodyMedium, color = dawnText())
                }
            }
            else -> Text(PathV3Copy.STAGE_BEHIND, style = MaterialTheme.typography.bodyMedium, color = steel)
        }
        if (hold == null && lever != null) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = HotThemeTokens.colors.hairline)
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(c.lever))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(lever.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    lever.line?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = steel) }
                }
            }
        }
        if (hold == null && weekLink != null) {
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onWeek)
                    .testTag(PATH_WEEK_LINK_TAG),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(weekLink, style = MaterialTheme.typography.bodyMedium, color = dawnText())
            }
        }
    }
}

/**
 * `StageWeeks` (A66 §5.2, §5.7): 12 cells — counted filled, the current one outlined in «Рассвет»,
 * the milestone numbers in the accent, a week behind that was not counted a neutral outline (never
 * red, never dimmed, §6.5 п. 8), a pause hatched. The cells are not touch targets; the row is one
 * TalkBack line.
 */
@Composable
internal fun StageWeeks(view: StageWeeksView, modifier: Modifier = Modifier) {
    val tokens = HotThemeTokens.colors
    val dawn = dawnText()
    val bone = MaterialTheme.colorScheme.onSurface
    val steel = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth().testTag(PATH_STAGE_WEEKS_TAG).clearAndSetSemantics { contentDescription = view.spoken }) {
        Row(Modifier.fillMaxWidth()) {
            Text(PathV3Copy.STAGE_WEEKS, style = MaterialTheme.typography.bodySmall, color = steel, modifier = Modifier.weight(1f))
            view.counted?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = steel) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            view.cells.forEach { cell ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val shape = RoundedCornerShape(8.dp)
                    val base = Modifier.size(24.dp).clip(shape)
                    val box = when (cell.state) {
                        StageCell.COUNTED -> base.background(bone)
                        StageCell.NOW -> base.background(Titanium.Dawn.copy(alpha = 0.15f)).border(2.dp, dawn, shape)
                        StageCell.AHEAD -> base.border(1.5.dp, tokens.rule, shape)
                        StageCell.PAST -> base.border(1.5.dp, steel.copy(alpha = 0.6f), shape)
                        StageCell.PAUSE -> base.border(1.5.dp, steel.copy(alpha = 0.6f), shape).drawBehind {
                            clipRect {
                                val step = 4.dp.toPx()
                                var x = -size.height
                                while (x < size.width) {
                                    drawLine(steel.copy(alpha = 0.5f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.dp.toPx())
                                    x += step
                                }
                            }
                        }
                    }
                    Box(box)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        cell.week.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (cell.milestone) dawn else tokens.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** «Из чего состоит цель ›» (A66 §5.3 level 2): opens the slice, the elements and the parts below. */
@Composable
internal fun PartsRow(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = HotThemeTokens.colors.hairline)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClickLabel = PathV3Copy.PARTS, onClick = onToggle)
                .testTag(PATH_PARTS_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(PathV3Copy.PARTS, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Icon(
                painterResource(R.drawable.core_ui_ic_chevron),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(if (expanded) 90f else 0f),
            )
        }
    }
}
