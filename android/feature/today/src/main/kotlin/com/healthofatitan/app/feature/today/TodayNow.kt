package com.healthofatitan.app.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.domain.today.DayTask
import com.healthofatitan.app.domain.today.TaskKind
import com.healthofatitan.app.domain.today.TodayCopy

private val NowShape = RoundedCornerShape(24.dp)
private val BadgeShape = RoundedCornerShape(12.dp)

/**
 * The card «Сейчас» (A66 §3.4, variant B «Тише» §3.2а), 84 dp: the neutral sign of the kind (40 dp),
 * «Сейчас», the title in one line, the line of the link 13 sp («→ Силовые 2 из 3 → цель») and ▶ as
 * a Dawn outline, not a fill: the «+» of the tab bar stays the one filled Dawn. A tap and ▶ do what
 * a tap on the task does; «Отметить» and «Не сегодня» are custom actions (WCAG 2.5.1).
 */
@Composable
internal fun NowCard(
    task: DayTask,
    link: String?,
    onTap: () -> Unit,
    onDone: () -> Unit,
    onNotToday: (() -> Unit)?,
) {
    val tokens = HotThemeTokens.colors
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val dawn = MaterialTheme.colorScheme.primary
    val connect = task.kind == TaskKind.CONNECT_WHOOP
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TodayTopRules.NOW_HEIGHT.dp)
            .clip(NowShape)
            .background(Brush.verticalGradient(listOf(tokens.glassTop, tokens.glassBottom)), NowShape)
            .border(1.dp, tokens.hairline, NowShape)
            .testTag(if (connect) TODAY_CONNECT_TAG else TASK_TAG + task.id)
            .clickable(role = Role.Button, onClick = onTap)
            .semantics {
                stateDescription = if (task.done) TodayCopy.DONE else ""
                customActions = listOfNotNull(
                    CustomAccessibilityAction(if (task.done) DayUiTexts.UNDO else TodayCopy.DONE) { onDone(); true },
                    onNotToday?.let { f -> CustomAccessibilityAction(TodayCopy.NOT_TODAY) { f(); true } },
                )
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(neutral.copy(alpha = 0.08f), BadgeShape)
                .clearAndSetSemantics { }
                .drawBehind { kindGlyph(task.kind, neutral) },
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                TodayTopCopy.NOW.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = neutral,
            )
            Text(
                task.title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (link != null) {
                Text(
                    link,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!task.done) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, dawn.copy(alpha = 0.6f), CircleShape)
                    .testTag(TASK_PLAY_TAG)
                    .clickable(role = Role.Button, onClick = onTap)
                    .clearAndSetSemantics {
                        contentDescription = TodayCopy.START
                        role = Role.Button
                    }
                    .drawBehind { play(dawn) },
            )
        }
    }
}

/**
 * «ещё 2 дела ›» (A66 §3.4): unfolds «Дела дня» in place. In «Тише» (§3.2а) it takes 24 dp of the
 * screen so «День» stays above the fold; the touch target is still 48 dp (§3.11): Compose grows a
 * smaller clickable to `minimumTouchTargetSize` when it hit-tests.
 */
@Composable
internal fun MoreLink(text: String, open: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Text(
        buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(color = primary)) { append(if (open) " ‹" else " ›") }
        },
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .testTag(TASKS_MORE_TAG)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** ▶: a triangle, slightly right of the centre so it reads centred. */
private fun DrawScope.play(color: Color) {
    val s = size.minDimension
    val path = Path().apply {
        moveTo(s * 0.41f, s * 0.32f)
        lineTo(s * 0.70f, s * 0.50f)
        lineTo(s * 0.41f, s * 0.68f)
        close()
    }
    drawPath(path, color)
}

/** The sign of the kind (A66 §3.4 `KindBadge`): drawn, so no new icon assets are needed. */
private fun DrawScope.kindGlyph(kind: TaskKind, color: Color) {
    val s = size.minDimension
    val c = center
    val w = 1.8.dp.toPx()
    when (kind) {
        TaskKind.TRAINING -> {
            // a dumbbell: the bar and two plates on each side
            drawLine(color, Offset(c.x - s * 0.22f, c.y), Offset(c.x + s * 0.22f, c.y), w, StrokeCap.Round)
            listOf(-0.16f, -0.22f, 0.16f, 0.22f).forEachIndexed { i, dx ->
                val h = if (i % 2 == 0) s * 0.14f else s * 0.09f
                drawLine(color, Offset(c.x + s * dx, c.y - h), Offset(c.x + s * dx, c.y + h), w, StrokeCap.Round)
            }
        }
        TaskKind.FOCUS -> {
            drawCircle(color, s * 0.18f, c, style = Stroke(w))
            drawCircle(color, s * 0.05f, c)
        }
        TaskKind.HELP -> {
            // a plain plus; never red here: the help card itself carries it (A66 §1.2)
            drawLine(color, Offset(c.x, c.y - s * 0.16f), Offset(c.x, c.y + s * 0.16f), w, StrokeCap.Round)
            drawLine(color, Offset(c.x - s * 0.16f, c.y), Offset(c.x + s * 0.16f, c.y), w, StrokeCap.Round)
        }
        else -> {
            drawCircle(color, s * 0.16f, c, style = Stroke(w))
            drawLine(color, Offset(c.x, c.y - s * 0.07f), Offset(c.x, c.y + s * 0.02f), w, StrokeCap.Round)
            drawLine(color, Offset(c.x, c.y + s * 0.02f), Offset(c.x + s * 0.06f, c.y + s * 0.06f), w, StrokeCap.Round)
        }
    }
}
