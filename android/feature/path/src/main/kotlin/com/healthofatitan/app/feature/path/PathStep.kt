package com.healthofatitan.app.feature.path

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotCardShape
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPendingCard
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.domain.path.GoalPlan
import com.healthofatitan.app.domain.path.StepVersion

const val PATH_STEP_TAG = "path_step"
const val PATH_STEP_EMPTY_TAG = "path_step_empty"

/**
 * «Шаг дня» on «Сегодня» (A42 §3.1, И8.5): one row with the sphere pictogram and a tiny ring of
 * the week goal. It is a separate composable: «Сегодня» only gives it a slot, the path owns it.
 * Without a goal it is a quiet invitation to the tab «Путь».
 */
@Composable
fun PathTodayStep(onOpenPath: () -> Unit, modifier: Modifier = Modifier, viewModel: PathViewModel = hiltViewModel()) {
    val view by viewModel.view.collectAsStateWithLifecycle()
    view?.let { PathTodayStepContent(it, onToggle = viewModel::toggleStep, onOpenPath = onOpenPath, modifier = modifier) }
}

@Composable
fun PathTodayStepContent(view: PathView, onToggle: (StepVersion) -> Unit, onOpenPath: () -> Unit, modifier: Modifier = Modifier) {
    val s = view.state
    val active = s.active?.takeIf { !it.finished }
    if (active == null) {
        HotPendingCard(
            title = PathTexts.NO_GOAL_TODAY,
            caption = PathTexts.NO_GOAL_TODAY_CAPTION,
            modifier = modifier.fillMaxWidth().clip(HotCardShape).clickable(onClick = onOpenPath).testTag(PATH_STEP_EMPTY_TAG),
        )
    } else {
        PathStepCard(
            plan = active.goal.plan,
            version = s.stepVersion,
            done = s.stepDone,
            marked = s.stepMarked,
            weekDone = active.weekDone,
            weekTarget = active.weekTarget,
            bedtimeMin = view.bedtimeMin,
            onToggle = { onToggle(s.stepVersion) },
            modifier = modifier,
        )
    }
}

/**
 * The step card: the sphere lights up when the step counts (A42 §3.4 «День»). A step the data
 * already counted (WHOOP saw the night or the workout) cannot be untapped.
 */
@Composable
fun PathStepCard(
    plan: GoalPlan,
    version: StepVersion,
    done: Boolean,
    marked: Boolean,
    weekDone: Int,
    weekTarget: Int,
    bedtimeMin: Int?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = HotThemeTokens.colors
    val primary = MaterialTheme.colorScheme.primary
    HotCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(HotCardShape)
            .toggleable(value = done, enabled = !done || marked, role = Role.Checkbox, onValueChange = { onToggle() })
            .testTag(PATH_STEP_TAG),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (done) primary.copy(alpha = 0.16f) else tokens.ruleSoft)
                    .border(1.dp, if (done) primary else tokens.rule, CircleShape),
            ) {
                SphereIcon(plan.sphere, size = 26.dp, color = if (done) primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HotOverline(PathTexts.TODAY_OVERLINE)
                Text(PathTexts.stepTitle(plan.sphere, version), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                PathTexts.stepDetail(plan.sphere, version, bedtimeMin)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            WeekRing(weekDone, weekTarget)
        }
    }
}

/** The tiny ring of the week goal: «2/4» inside, filled by the days that counted. */
@Composable
fun WeekRing(done: Int, target: Int, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val track = HotThemeTokens.colors.ruleSoft
    val fraction = if (target > 0) (done.toFloat() / target).coerceIn(0f, 1f) else 0f
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(44.dp).semantics(mergeDescendants = true) { contentDescription = PathTexts.weekA11y(done, target) },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 3.dp.toPx()
            drawCircle(track, size.minDimension / 2f - w, style = Stroke(w))
            if (fraction > 0f) {
                val inset = w
                drawArc(
                    primary, -90f, 360f * fraction, false,
                    androidx.compose.ui.geometry.Offset(inset, inset),
                    androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            PathTexts.week(done, target),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
