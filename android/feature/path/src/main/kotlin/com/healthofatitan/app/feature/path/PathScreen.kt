package com.healthofatitan.app.feature.path

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.domain.path.GoalPlan
import com.healthofatitan.app.domain.path.GoalProgress
import com.healthofatitan.app.domain.path.PathState
import com.healthofatitan.app.domain.path.SphereLight
import com.healthofatitan.app.domain.path.StepVersion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

const val PATH_SCREEN_TAG = "screen_path"
const val PATH_HERO_TAG = "path_hero"
const val PATH_PROPOSAL_TAG = "path_proposal"
const val PATH_TAKE_TAG = "path_take"
const val PATH_OTHER_TAG = "path_other"
const val PATH_CHANGE_TAG = "path_change"
const val PATH_GOAL_TAG = "path_goal"
const val PATH_TITAN_TAG = "path_your_titan"

/**
 * Tab «Путь» (D-033, A42 §3.1): the Titan in the ring of the 12-week goal, one line «цель ·
 * этап · неделя», the step of the day, «Твой Титан» by six spheres and the goal history.
 * Settings, the mentor and help live under the avatar in the header corner.
 *
 * @param stateful false renders the default state without ViewModels (navigation tests).
 */
fun NavGraphBuilder.pathScreen(stateful: Boolean = true) {
    composable(route = HotRoute.PATH) {
        if (stateful) PathRoute() else PathScreen(PathView(PathState.empty(LocalDate.now())), PathActions())
    }
}

class PathActions(
    val onTake: (GoalPlan) -> Unit = {},
    val onToggleStep: (StepVersion) -> Unit = {},
)

@HiltViewModel
class PathViewModel @Inject constructor(private val repository: PathRepository) : ViewModel() {
    val view: StateFlow<PathView?> = repository.view
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun take(plan: GoalPlan) {
        viewModelScope.launch { repository.take(plan) }
    }

    fun toggleStep(version: StepVersion) {
        viewModelScope.launch { repository.toggleStep(version) }
    }
}

@Composable
private fun PathRoute(viewModel: PathViewModel = hiltViewModel()) {
    val view by viewModel.view.collectAsStateWithLifecycle()
    PathScreen(
        view = view ?: PathView(PathState.empty(LocalDate.now())),
        actions = PathActions(onTake = viewModel::take, onToggleStep = viewModel::toggleStep),
    )
}

@Composable
fun PathScreen(view: PathView, actions: PathActions, modifier: Modifier = Modifier) {
    val s = view.state
    val active = s.active?.takeIf { !it.finished }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var pick by rememberSaveable { mutableIntStateOf(0) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 4.dp, bottom = HotSpacing.sectionGap)
            .testTag(PATH_SCREEN_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
    ) {
        PathHero(s.active, Modifier.fillMaxWidth().widthIn(max = 380.dp).aspectRatio(1f).testTag(PATH_HERO_TAG))
        if (active != null) {
            GoalLine(active)
        } else if (s.active?.finished == true) {
            Text(PathTexts.CYCLE_DONE, style = HotType.quote, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        }
        if (active == null || choosing) {
            val proposals = s.proposals
            if (proposals.isNotEmpty()) {
                ProposalCard(
                    plan = proposals[pick % proposals.size],
                    more = proposals.size > 1,
                    replacing = active != null,
                    onTake = {
                        actions.onTake(it)
                        choosing = false
                        pick = 0
                    },
                    onOther = { pick = (pick + 1) % proposals.size },
                    onKeep = { choosing = false },
                )
            }
        } else {
            PathStepCard(
                plan = active.goal.plan,
                version = s.stepVersion,
                done = s.stepDone,
                marked = s.stepMarked,
                weekDone = active.weekDone,
                weekTarget = active.weekTarget,
                bedtimeMin = view.bedtimeMin,
                onToggle = { actions.onToggleStep(s.stepVersion) },
            )
        }
        YourTitan(s.spheres, s.litSpheres)
        if (active != null && !choosing) {
            HotTextButton(PathTexts.CHANGE, onClick = { choosing = true }, modifier = Modifier.testTag(PATH_CHANGE_TAG))
        }
        if (s.past.isNotEmpty()) PastGoals(s.past)
    }
}

/** «Сон 7 ч · 5 из 7» with its pictogram, and «этап 2 из 3 · неделя 6 из 12» (A42 §3.1). */
@Composable
private fun GoalLine(p: GoalProgress) {
    val primary = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.testTag(PATH_GOAL_TAG)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SphereIcon(p.goal.plan.sphere, size = 30.dp, color = primary)
            Spacer(Modifier.width(12.dp))
            Text(PathTexts.goal(p.goal.plan), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(8.dp))
        HotOverline(PathTexts.where(p.stage, p.week))
    }
}

/** One goal from data, taken in one tap (И8.3); «Другая» shows the next sphere (A42 §3.2). */
@Composable
private fun ProposalCard(
    plan: GoalPlan,
    more: Boolean,
    replacing: Boolean,
    onTake: (GoalPlan) -> Unit,
    onOther: () -> Unit,
    onKeep: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth().testTag(PATH_PROPOSAL_TAG)) {
        HotOverline(PathTexts.GOAL_OVERLINE)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SphereIcon(plan.sphere, size = 34.dp, color = primary)
            Spacer(Modifier.width(14.dp))
            Text(PathTexts.goal(plan), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(10.dp))
        Text(PathTexts.now(plan), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        StageRow(plan)
        Spacer(Modifier.height(20.dp))
        HotPrimaryButton(PathTexts.TAKE, onClick = { onTake(plan) }, modifier = Modifier.fillMaxWidth().testTag(PATH_TAKE_TAG))
        if (more || replacing) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (more) HotTextButton(PathTexts.OTHER, onClick = onOther, modifier = Modifier.testTag(PATH_OTHER_TAG))
                if (replacing) HotTextButton(PathTexts.KEEP, onClick = onKeep)
            }
        }
    }
}

/** Three stages of 4 weeks as three numbered steps: «4 → 5 → 5 из 7». */
@Composable
private fun StageRow(plan: GoalPlan) {
    val tokens = HotThemeTokens.colors
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Этапы: " + PathTexts.stages(plan) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        plan.stages.forEachIndexed { i, days ->
            if (i > 0) {
                Box(Modifier.weight(1f).height(1.dp).background(tokens.rule))
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, if (i == 0) MaterialTheme.colorScheme.primary else tokens.rule, CircleShape),
            ) {
                Text("$days", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.width(10.dp))
        HotOverline("из 7")
    }
}

/**
 * «Твой Титан» (A37 §8): six spheres as light. In light at 70 % of the week's days (50 % in
 * «Бережно»); below it a sphere is a contour, never black; a sphere HoT cannot see yet is a
 * faint contour (no data is not a failure).
 */
@Composable
private fun YourTitan(spheres: List<SphereLight>, lit: Int) {
    val tokens = HotThemeTokens.colors
    val primary = MaterialTheme.colorScheme.primary
    HotCard(modifier = Modifier.fillMaxWidth().testTag(PATH_TITAN_TAG)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HotOverline(PathTexts.TITAN)
            Spacer(Modifier.weight(1f))
            Text(PathTexts.inLight(lit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            spheres.forEach { light ->
                val color = when {
                    light.lit -> primary
                    light.share != null -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> tokens.textTertiary.copy(alpha = 0.6f)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (light.lit) primary.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
                        .border(1.dp, if (light.lit) primary else if (light.share != null) tokens.rule else tokens.ruleSoft, CircleShape),
                ) {
                    SphereIcon(light.sphere, size = 22.dp, color = color)
                }
            }
        }
    }
}

/** Earlier goals keep their weeks: changing a goal never erases the path (A42 §3.2 п. 4). */
@Composable
private fun PastGoals(past: List<GoalProgress>) {
    HotCard(modifier = Modifier.fillMaxWidth()) {
        HotOverline(PathTexts.PAST)
        past.forEach { p ->
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SphereIcon(p.goal.plan.sphere, size = 22.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(PathTexts.goal(p.goal.plan), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(PathTexts.litWeeks(p.litWeeks), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
