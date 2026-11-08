package com.healthofatitan.app.feature.week

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotCardShape
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.component.ModeGlyph
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.domain.today.GoalMeasure
import com.healthofatitan.app.domain.today.Sphere
import com.healthofatitan.app.domain.today.WeekGoal
import com.healthofatitan.app.domain.today.week.DayColumn
import com.healthofatitan.app.domain.today.week.DayDot
import com.healthofatitan.app.domain.today.week.GoalCard
import com.healthofatitan.app.domain.today.week.Verdict
import com.healthofatitan.app.domain.today.week.WeekCopy
import com.healthofatitan.app.domain.today.week.WeekFaceCopy
import com.healthofatitan.app.domain.today.week.WeekFaceView
import com.healthofatitan.app.domain.today.week.WeekGoalRules
import com.healthofatitan.app.domain.today.week.WeekScoring
import java.time.LocalDate

const val WEEK_VERDICT_TAG = "week_verdict"
const val WEEK_SCULPTURE_TAG = "week_sculpture_blank"
const val WEEK_PATH_LINK_TAG = "week_path_link"
const val WEEK_NO_GOAL_TAG = "week_no_goal"
const val WEEK_NAME_GOAL_TAG = "week_name_goal"
const val WEEK_SEEN_TAG = "week_seen_now"
const val WEEK_SEEN_NIGHTS_TAG = "week_seen_nights"
const val WEEK_SEEN_BEHIND_TAG = "week_seen_behind"

/** A66 §1.2: the edge of «Неделя» v3 (16 dp, not the old 24) and the gap between cards. */
internal val WeekEdge = 16.dp
internal val WeekGap = 12.dp

private val Caption = 13.sp

// ---------------------------------------------------------------- Вердикт недели (A66 §4.2)

/**
 * «Цели недели · 2 из 3 · держатся · Осталось: …» on the left, the blank of the Sculpture on the
 * right (mockup 06, y 100–316). A phrase stands instead of the number when nothing holds yet, in
 * a first week and in care — never «0 из 3».
 */
@Composable
fun WeekVerdict(verdict: Verdict, carved: Int, onOpenPath: () -> Unit, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val steel = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth().testTag(WEEK_VERDICT_TAG), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Column(
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(WeekFaceCopy.VERDICT_OVERLINE, verdict.count, verdict.word, verdict.left).joinToString(". ")
                },
            ) {
                Text(WeekFaceCopy.VERDICT_OVERLINE, style = MaterialTheme.typography.bodyMedium.copy(fontSize = Caption), color = steel, modifier = Modifier.semantics { heading() })
                val count = verdict.count
                if (count != null) {
                    Text(
                        count,
                        style = HotType.headline.copy(lineHeight = 62.sp),
                        color = ink,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    verdict.word?.let { Text(it, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp), color = ink) }
                } else {
                    verdict.word?.let {
                        Text(it, style = HotType.numeral.copy(fontSize = 28.sp, lineHeight = 34.sp), color = ink, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                verdict.left?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                        color = steel,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            verdict.pathLink?.let { link ->
                Row(
                    Modifier
                        .padding(top = 8.dp)
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = onOpenPath)
                        .testTag(WEEK_PATH_LINK_TAG),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(link, style = MaterialTheme.typography.bodyMedium.copy(fontSize = Caption), color = steel)
                    Text(" ›", style = MaterialTheme.typography.bodyMedium.copy(fontSize = Caption), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SculptureBlank(carved, Modifier.size(width = 120.dp, height = 168.dp))
            Text(
                WeekFaceCopy.sculpture(carved).uppercase(),
                style = HotType.mono,
                color = steel,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(120.dp).padding(top = 6.dp).clearAndSetSemantics { },
            )
        }
    }
}

/**
 * The blank of the Sculpture of the week (A66 §4.2, §11.4: a placeholder until the render): a
 * marble block on a plinth, and the figure showing from the top down by the share of the week
 * already lived ([carved] of 7, A59). Drawn with Compose only.
 */
@Composable
fun SculptureBlank(carved: Int, modifier: Modifier = Modifier) {
    val dark = HotThemeTokens.colors.isDark
    val shape = RoundedCornerShape(16.dp)
    val ground = if (dark) Color(0xFF101215) else Color(0xFFE9E6E0)
    val warm = Color(0xFFFFDCAA)
    val marble = listOf(Color(0xFFF3F1EC), Color(0xFFD9D4CA), Color(0xFFB9B2A6))
    val block = listOf(Color(0xFFA8A196), Color(0xFF958E83), Color(0xFF7A746B))
    val plinth = Color(0xFF2A2620)
    val share = carved.coerceIn(0, 7) / 7f
    Canvas(
        modifier
            .clip(shape)
            .border(1.dp, HotThemeTokens.colors.hairline, shape)
            .testTag(WEEK_SCULPTURE_TAG)
            .semantics { contentDescription = WeekFaceCopy.sculptureSpoken(carved) },
    ) {
        val w = size.width
        val h = size.height
        drawRect(ground)
        drawRect(Brush.radialGradient(listOf(warm.copy(alpha = 0.10f), Color.Transparent), center = Offset(w * 0.3f, h * 0.2f), radius = w))
        // the figure: a head, the shoulders with the arms open, the torso — marble, light from above
        val figure = Brush.verticalGradient(marble, startY = h * 0.15f, endY = h * 0.85f)
        drawCircle(figure, w * 0.06f, Offset(w * 0.5f, h * 0.22f))
        drawRect(figure, Offset(w * 0.47f, h * 0.26f), Size(w * 0.06f, h * 0.05f))
        val body = Path().apply {
            moveTo(w * 0.02f, h * 0.33f)
            lineTo(w * 0.98f, h * 0.33f)
            lineTo(w * 0.98f, h * 0.36f)
            lineTo(w * 0.66f, h * 0.38f)
            lineTo(w * 0.60f, h * 0.80f)
            lineTo(w * 0.40f, h * 0.80f)
            lineTo(w * 0.34f, h * 0.38f)
            lineTo(w * 0.02f, h * 0.36f)
            close()
        }
        drawPath(body, figure)
        // the block still covers the part of the week not lived; its top is the rough line of the chisel
        val bh = h * (1f - share) + 18.dp.toPx()
        val top = h - bh
        val x0 = 6.dp.toPx()
        val bw = w - 2 * x0
        val jag = listOf(0f to 0.18f, 0.14f to 0.06f, 0.30f to 0.14f, 0.47f to 0f, 0.62f to 0.09f, 0.80f to 0.03f, 1f to 0.15f)
        val rough = (bh * 0.18f).coerceAtMost(30.dp.toPx())
        val stone = Path().apply {
            jag.forEachIndexed { i, (fx, fy) ->
                val p = Offset(x0 + bw * fx, top + rough * (fy / 0.18f))
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            lineTo(x0 + bw, h)
            lineTo(x0, h)
            close()
        }
        drawPath(stone, Brush.verticalGradient(block, startY = top, endY = h))
        drawPath(stone, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), center = Offset(x0 + bw * 0.3f, top + bh * 0.4f), radius = 40.dp.toPx()))
        drawRect(plinth, Offset(0f, h - 10.dp.toPx()), Size(w, 10.dp.toPx()))
    }
}

// ---------------------------------------------------------------- Карточка цели (A66 §4.3)

/**
 * A goal of the week, 116 dp: the glyph, the name, the chip of the source in the title row and
 * «2 из 3» on the right; the dots of Monday…Sunday with today ringed in Рассвет; what is left.
 * One TalkBack node (A66 §4.9). A tap opens «поменять число» without reproach.
 */
@Composable
fun WeekGoalCard(card: GoalCard, expanded: Boolean, onTap: () -> Unit, actions: WeekActions, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val steel = MaterialTheme.colorScheme.onSurfaceVariant
    HotCard(modifier = modifier.fillMaxWidth().heightIn(min = 116.dp).testTag("week_goal_${card.goal.id}"), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = WeekCopy.GOAL_CHANGE, onClick = onTap)
                .clearAndSetSemantics {
                    contentDescription = card.spoken
                    role = Role.Button
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalGlyph(card.goal, ink, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    card.goal.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                SourceChip(card.chip)
                Spacer(Modifier.weight(1f).width(8.dp))
                Text(card.count, style = HotType.numeral.copy(fontSize = 22.sp, lineHeight = 26.sp), color = ink, softWrap = false, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            DotsRow(card.dots, card.todayIndex, Modifier.fillMaxWidth().padding(start = 20.dp))
            Spacer(Modifier.height(6.dp))
            Text(card.left, style = MaterialTheme.typography.bodyMedium.copy(fontSize = Caption), color = steel, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 28.dp))
        }
        if (expanded) {
            Text(WeekCopy.GOAL_NO_PENALTY, style = MaterialTheme.typography.bodySmall, color = steel, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HotTextButton(WeekCopy.GOAL_LIGHTER, onClick = { actions.onLighter(card.goal.id) })
                HotTextButton(WeekCopy.GOAL_MORE, onClick = { actions.onHeavier(card.goal.id) })
                HotTextButton(WeekCopy.GOAL_REMOVE, onClick = { actions.onRemove(card.goal.id) })
            }
        }
    }
}

/** The chip of the source: Рассвет on a 6 % wash, 12 sp, 20 dp high. */
@Composable
private fun SourceChip(text: String) {
    val dawn = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .height(20.dp)
            .background(dawn.copy(alpha = 0.06f), shape)
            .border(1.dp, dawn.copy(alpha = 0.35f), shape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 14.sp), color = dawn, maxLines = 1, softWrap = false)
    }
}

/** Seven dots of 28 dp (A66 §1.1, Б3): ● done · ○ planned · «·» not needed · ◌ no data (dashed only for no data). */
@Composable
private fun DotsRow(dots: List<DayDot>, todayIndex: Int, modifier: Modifier = Modifier) {
    val bone = MaterialTheme.colorScheme.onSurface
    val steel = MaterialTheme.colorScheme.onSurfaceVariant
    val graphite = HotThemeTokens.colors.textTertiary
    val dawn = MaterialTheme.colorScheme.primary
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        dots.forEachIndexed { i, dot ->
            Canvas(Modifier.size(28.dp)) {
                val c = center
                val r = 7.dp.toPx()
                if (i == todayIndex) drawCircle(dawn, 13.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                when (dot) {
                    DayDot.DONE -> drawCircle(bone, r, c)
                    DayDot.PLANNED -> drawCircle(steel, r - 0.75.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
                    DayDot.NO_DATA -> drawCircle(
                        steel.copy(alpha = 0.8f), r - 0.75.dp.toPx(), c,
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 2.5.dp.toPx()))),
                    )
                    DayDot.NOT_NEEDED -> drawCircle(graphite, 2.dp.toPx(), c)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Дни недели (A66 §4.2)

/**
 * Seven cells of 48 dp, Monday…Sunday: the day, the glyph of its mode, the dot (● the day held,
 * ◌ no data, ○ ahead or not yet) and the date; today stands in a Рассвет frame. A tap on a lived
 * day opens it read-only.
 */
@Composable
fun WeekDaysRow(columns: List<DayColumn>, picked: LocalDate?, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val bone = MaterialTheme.colorScheme.onSurface
    val steel = MaterialTheme.colorScheme.onSurfaceVariant
    val graphite = HotThemeTokens.colors.textTertiary
    val dawn = MaterialTheme.colorScheme.primary
    Row(
        modifier.fillMaxWidth().height(88.dp).testTag(WEEK_COLUMNS_TAG).semantics { contentDescription = WeekCopy.SPOKEN_COLUMNS },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        columns.forEach { c ->
            val spoken = WeekScoring.short(c.day) + ": " + when {
                c.future -> WeekCopy.DAY_FUTURE
                c.sick -> WeekCopy.DAY_REST
                c.noData -> WeekCopy.DAY_NO_DATA
                c.inSupport -> WeekCopy.DAY_IN_SUPPORT
                else -> "${WeekCopy.SUPPORTS_FILLED} ${c.supportsFilled}"
            } + if (c.today) ", ${WeekCopy.DAY_TODAY}" else ""
            val shape = RoundedCornerShape(14.dp)
            val frame = c.today || c.day == picked
            Column(
                Modifier
                    .width(48.dp)
                    .height(80.dp)
                    .then(if (frame) Modifier.background(dawn.copy(alpha = 0.07f), shape).border(1.dp, dawn.copy(alpha = 0.3f), shape) else Modifier)
                    .clip(shape)
                    .clickable(enabled = !c.future, role = Role.Button) { onPick(c.day) }
                    .testTag("week_col_${c.day.dayOfWeek.value}")
                    .semantics(mergeDescendants = true) { contentDescription = spoken }
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(WeekScoring.short(c.day), style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = if (c.today) bone else steel)
                val mode = c.mode?.takeIf { !c.future }?.hotMode()
                if (mode != null) ModeGlyph(mode, size = 16.dp) else Spacer(Modifier.size(16.dp))
                Canvas(Modifier.size(8.dp)) {
                    val r = size.minDimension / 2
                    when {
                        c.inSupport -> drawCircle(bone, r)
                        c.noData -> drawCircle(
                            steel.copy(alpha = 0.8f), r - 0.5.dp.toPx(),
                            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 1.5.dp.toPx()))),
                        )
                        else -> drawCircle(graphite, r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
                    }
                }
                Text(c.day.dayOfMonth.toString(), style = HotType.mono, color = graphite)
            }
        }
    }
}

// ---------------------------------------------------------------- Неделя без Пути (A66 §0.3)

/**
 * «Пока нет цели · Назови цель — неделя соберётся из неё» (mockup 09): without a goal of the
 * Path HoT does not invent a plan from data; the one action is to name the goal (90 s).
 */
@Composable
fun NoGoalCard(onNameGoal: () -> Unit, modifier: Modifier = Modifier) {
    val dawn = MaterialTheme.colorScheme.primary
    val glow = HotThemeTokens.colors.titanLight.last()
    HotCard(
        cornice = true,
        modifier = modifier
            .fillMaxWidth()
            .testTag(WEEK_NO_GOAL_TAG)
            .clip(HotCardShape)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(glow.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width * 0.86f, size.height * 0.42f), radius = size.width * 0.42f),
                    radius = size.width * 0.42f,
                    center = Offset(size.width * 0.86f, size.height * 0.42f),
                )
            },
    ) {
        Column(Modifier.fillMaxWidth(0.66f)) {
            Text(WeekFaceCopy.NO_GOAL_OVERLINE.uppercase(), style = HotType.mono.copy(fontSize = 12.sp), color = dawn)
            Spacer(Modifier.height(8.dp))
            Text(
                WeekFaceCopy.NO_GOAL_TITLE,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(10.dp))
            Text(WeekFaceCopy.NO_GOAL_TEXT, style = MaterialTheme.typography.bodyMedium.copy(fontSize = Caption, lineHeight = 19.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        HotPrimaryButton(WeekFaceCopy.NO_GOAL_BUTTON, onClick = onNameGoal, modifier = Modifier.fillMaxWidth().testTag(WEEK_NAME_GOAL_TAG))
    }
}

/** «Что видно уже сейчас»: the nights of the week and the workouts behind; a line without data is not shown. */
@Composable
fun SeenNow(face: WeekFaceView.NoPath, onOpenBehind: () -> Unit, modifier: Modifier = Modifier) {
    if (face.nights == null && face.behind == null) return
    val steel = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth().testTag(WEEK_SEEN_TAG), verticalArrangement = Arrangement.spacedBy(WeekGap)) {
        Text(WeekFaceCopy.SEEN_NOW, style = MaterialTheme.typography.bodyMedium.copy(fontSize = Caption), color = steel, modifier = Modifier.semantics { heading() })
        face.nights?.let { n ->
            GlassRow(Modifier.testTag(WEEK_SEEN_NIGHTS_TAG), icon = { Glyph(MOON, steel, it) }) {
                Text(WeekFaceCopy.nights(n.meanMinutes), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(WeekCopy.ofTotal(n.nights, n.of), style = MaterialTheme.typography.bodyMedium.copy(fontSize = Caption), color = steel, softWrap = false)
            }
        }
        face.behind?.let { b ->
            GlassRow(Modifier.testTag(WEEK_SEEN_BEHIND_TAG), onClick = onOpenBehind, icon = { Glyph(BEHIND, steel, it) }) {
                Text(WeekFaceCopy.behind(b.workouts, b.days), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Glyph(CHEVRON, steel, Modifier.size(18.dp))
            }
        }
    }
}

/** A glass row of 72 dp with a 16 dp radius (A66 §1.2 «строка 16»). */
@Composable
private fun GlassRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    icon: @Composable (Modifier) -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val tokens = HotThemeTokens.colors
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, tokens.hairline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .background(Brush.verticalGradient(listOf(tokens.glassTop, tokens.glassBottom)))
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .semantics(mergeDescendants = true) { }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            icon(Modifier.size(20.dp))
            content()
        }
    }
}

// ---------------------------------------------------------------- Глифы (24 × 24, штрих 1.6, mockups-v3)

private const val STRENGTH = "M3 12h18M6 8v8M18 8v8M3.5 10v4M20.5 10v4"
private const val MOON = "M19 14.5A7.5 7.5 0 1 1 9.5 5a6 6 0 0 0 9.5 9.5z"
private const val WALK = "M11 21l2-6-3-3 1-4 3 3h3M8 13l2-5"
private const val FOCUS = "M12 5a7 7 0 1 0 0.01 0zM12 9.5a2.5 2.5 0 1 0 0.01 0z"
private const val FLAG = "M6 21V4M6 4h11l-2 4 2 4H6"
private const val BEHIND = "M4 15a8 8 0 0 1 16 0M8 11l-2-2"
private const val CHEVRON = "M9 6l6 6-6 6"

@Composable
private fun GoalGlyph(goal: WeekGoal, color: Color, modifier: Modifier) {
    val d = when {
        goal.id == WeekGoalRules.ID_TRAINING -> STRENGTH
        goal.id == WeekGoalRules.ID_SLEEP || goal.sphere == Sphere.SLEEP -> MOON
        goal.id == WeekGoalRules.ID_ZONES || (goal.sphere == Sphere.BODY && goal.measure != GoalMeasure.MINUTES) -> WALK
        goal.id == WeekGoalRules.ID_FOCUS || goal.sphere == Sphere.WORK -> FOCUS
        else -> FLAG
    }
    Glyph(d, color, modifier, walkerHead = d == WALK, behindBase = false)
}

@Composable
private fun Glyph(d: String, color: Color, modifier: Modifier, walkerHead: Boolean = false, behindBase: Boolean = d == BEHIND) {
    val path = androidx.compose.runtime.remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(modifier.clearAndSetSemantics { }) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val s = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(path, color, style = s)
            if (walkerHead) drawCircle(color, 1.6f, Offset(13f, 5f), style = s)
            if (behindBase) drawOval(color, Offset(7f, 17.4f), Size(10f, 3.2f), style = s)
        }
    }
}
