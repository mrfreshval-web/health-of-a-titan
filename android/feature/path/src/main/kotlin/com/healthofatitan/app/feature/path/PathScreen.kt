package com.healthofatitan.app.feature.path

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.core.ui.theme.PathColors
import com.healthofatitan.app.domain.path.BalanceKind
import com.healthofatitan.app.domain.path.ChangeProposal
import com.healthofatitan.app.domain.path.ChangeResult
import com.healthofatitan.app.domain.path.ComponentKey
import com.healthofatitan.app.domain.path.Element
import com.healthofatitan.app.domain.path.ElementView
import com.healthofatitan.app.domain.path.GoalChange
import com.healthofatitan.app.domain.path.GoalStatus
import com.healthofatitan.app.domain.path.PartState
import com.healthofatitan.app.domain.path.PathCopy
import com.healthofatitan.app.domain.path.PathLexicon
import com.healthofatitan.app.domain.path.PathOverview
import com.healthofatitan.app.domain.path.PracticalGoal
import com.healthofatitan.app.domain.path.Refusal
import com.healthofatitan.app.domain.path.StageRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

const val PATH_SCREEN_TAG = "screen_path"
const val PATH_GOAL_TAG = "path_goal"
const val PATH_WHY_TAG = "path_why"
const val PATH_EMPTY_TAG = "path_empty"
const val PATH_NAME_GOAL_TAG = "path_name_goal"
const val PATH_BALANCE_TAG = "path_balance"
const val PATH_TRAIL_TAG = "path_trail"
const val PATH_MENU_TAG = "path_menu"
const val PATH_UNDO_TAG = "path_undo"
const val PATH_LOOK_BACK_TAG = "path_look_back"

fun elementTag(e: Element) = "path_element_${e.name.lowercase()}"

/**
 * Tab «Путь» (A55 §1, A58-VP §5.1): the goal, «Зачем», «Срез мира» with both Titans, the balance
 * and its one step, the trail of the stage, the three elements and the milestones. No ring with a
 * frieze in this slice (D-048 п. 6). Settings, the mentor and help stay under the avatar.
 *
 * @param stateful false renders the empty path without ViewModels (navigation tests)
 */
fun NavGraphBuilder.pathScreen(
    stateful: Boolean = true,
    onOpenRitual: () -> Unit = {},
    onLookBack: () -> Unit = {},
    onHelp: () -> Unit = {},
) {
    composable(route = HotRoute.PATH) {
        if (stateful) {
            PathRoute(onOpenRitual, onLookBack, onHelp)
        } else {
            PathScreen(
                PathUi(PathData.empty(LocalDate.now())),
                PathActions(onNameGoal = onOpenRitual, onFromCarrying = onOpenRitual, onLookBack = onLookBack),
            )
        }
    }
}

/** The screen's state: the data and what a change in progress shows. */
data class PathUi(
    val data: PathData,
    val proposal: ChangeProposal? = null,
    val refusal: String? = null,
    val undo: Boolean = false,
    /** One phrase for TalkBack after a moment (§1.6 «Моменты»). */
    val announce: String? = null,
)

class PathActions(
    val onNameGoal: () -> Unit = {},
    val onFromCarrying: () -> Unit = {},
    val onMark: (ComponentKey, Int) -> Unit = { _, _ -> },
    val onPropose: (GoalChange) -> Unit = {},
    val onApply: (override: Boolean) -> Unit = {},
    val onKeep: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onHelp: () -> Unit = {},
    /** D-051: «Оглянуться ›» opens «За плечами», the reverse plan of the Path. */
    val onLookBack: () -> Unit = {},
)

@HiltViewModel
class PathViewModel @Inject constructor(private val repository: PathRepository) : ViewModel() {
    private val transient = MutableStateFlow(PathUi(PathData.empty(LocalDate.now())))

    val ui: StateFlow<PathUi?> = combine(repository.data, transient) { d, t -> t.copy(data = d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun mark(key: ComponentKey, mark: Int) {
        viewModelScope.launch {
            val moved = repository.markTaken(key, mark)
            transient.update { it.copy(announce = if (moved) PathCopy.a11yFireMoved(mark, key) else PathCopy.markTaken(mark)) }
        }
    }

    fun propose(change: GoalChange) {
        viewModelScope.launch { show(repository.propose(change)) }
    }

    fun apply(override: Boolean) {
        val p = transient.value.proposal ?: return
        viewModelScope.launch { show(repository.apply(p, override)) }
    }

    fun keep() = transient.update { it.copy(proposal = null, refusal = null) }

    fun undo() {
        viewModelScope.launch {
            repository.undo()
            transient.update { it.copy(undo = false) }
        }
    }

    private fun show(result: ChangeResult) = transient.update {
        when (result) {
            is ChangeResult.Proposed -> it.copy(proposal = result.proposal, refusal = null)
            is ChangeResult.Applied -> it.copy(proposal = null, refusal = null, undo = true)
            is ChangeResult.Refused -> it.copy(
                proposal = if (result.reason == Refusal.SOFT_LIMIT) it.proposal else null,
                refusal = result.violations.firstOrNull()?.let(PathCopy::violation) ?: if (result.reason == Refusal.SOFT_LIMIT) PathCopy.SOFT_LIMIT else null,
            )
        }
    }
}

@Composable
private fun PathRoute(onOpenRitual: () -> Unit, onLookBack: () -> Unit, onHelp: () -> Unit, viewModel: PathViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    PathScreen(
        ui = ui ?: PathUi(PathData.empty(LocalDate.now())),
        actions = PathActions(
            onNameGoal = onOpenRitual,
            onFromCarrying = onOpenRitual,
            onMark = viewModel::mark,
            onPropose = viewModel::propose,
            onApply = viewModel::apply,
            onKeep = viewModel::keep,
            onUndo = viewModel::undo,
            onHelp = onHelp,
            onLookBack = onLookBack,
        ),
    )
}

/** Which sheet of the path is open. */
sealed interface PathSheet {
    data object Menu : PathSheet
    data object Pause : PathSheet
    data object Release : PathSheet
    data object Crossroads : PathSheet
    data class Part(val key: ComponentKey) : PathSheet
}

/**
 * @param still no motion (screenshots)
 */
@Composable
fun PathScreen(ui: PathUi, actions: PathActions, modifier: Modifier = Modifier, still: Boolean = false, initialSheet: PathSheet? = null) {
    val ov = ui.data.overview
    val goal = ov.goal
    var expanded by rememberSaveable(goal?.id) { mutableStateOf(defaultElement(ov).name) }
    var whyShown by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(initialSheet) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = HotSpacing.sectionGap)
            .testTag(PATH_SCREEN_TAG),
    ) {
        if (goal != null) {
            GoalHeader(goal, ov, onMenu = { sheet = PathSheet.Menu }, modifier = Modifier.padding(horizontal = HotSpacing.edge))
            val why = goal.why
            if (!why.isNullOrBlank() && (ov.whyVisible || whyShown)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    why,
                    style = HotType.voice,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = HotSpacing.edge).testTag(PATH_WHY_TAG),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        WorldSlice(
            overview = ov,
            mode = ui.data.mode,
            onTier = { expanded = it.name },
            onPart = { key -> sheet = PathSheet.Part(key) },
            onStar = { whyShown = !whyShown },
            still = still,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        ui.announce?.let { a ->
            Text(
                a,
                style = PathText.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = HotSpacing.edge, vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Column(
            Modifier.padding(horizontal = HotSpacing.edge),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(0.dp))
            if (goal == null) {
                EmptyCard(actions)
            } else {
                BalanceCard(ov, onStep = { ov.balance.lever?.let { sheet = PathSheet.Part(it) } })
                if (ui.undo) {
                    HotTextButton(PathCopy.UNDO, onClick = actions.onUndo, modifier = Modifier.testTag(PATH_UNDO_TAG))
                }
                Spacer(Modifier.height(16.dp))
                StageTrail(ov)
                Spacer(Modifier.height(8.dp))
                ov.elements.forEach { ev ->
                    ElementCard(
                        ev, expanded = expanded == ev.element.name,
                        onToggle = { expanded = ev.element.name },
                        onPart = { sheet = PathSheet.Part(it) },
                        still = still,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Milestones(goal, ov.today)
            }
            HotTextButton(PathCopy.LOOK_BACK, onClick = actions.onLookBack, modifier = Modifier.testTag(PATH_LOOK_BACK_TAG))
        }
    }
    PathSheets(ui, actions, sheet, onSheet = { sheet = it })
}

private fun defaultElement(ov: PathOverview): Element = ov.balance.lever?.element ?: ov.goal?.lever?.key?.element ?: Element.WATER

@Composable
private fun GoalHeader(goal: PracticalGoal, ov: PathOverview, onMenu: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().testTag(PATH_GOAL_TAG), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(goal.title, style = PathText.goalTitle, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
            val line = buildList {
                add(PathCopy.deadline(goal.deadline))
                if (ov.stagesTotal > 1) add(PathCopy.stage(goal.stage, ov.stagesTotal))
                if (goal.status != GoalStatus.ACTIVE) add(PathCopy.statusWord(goal.status))
            }.joinToString(" · ")
            Text(line, style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onMenu, modifier = Modifier.size(48.dp).testTag(PATH_MENU_TAG)) {
            Icon(painterResource(R.drawable.core_ui_ic_more), contentDescription = PathCopy.MENU, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** «Путь ждёт цели. Опоры дня держу я.» (A56 §2.1). */
@Composable
private fun EmptyCard(actions: PathActions) {
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth().testTag(PATH_EMPTY_TAG)) {
        Text(PathCopy.EMPTY_TITLE, style = PathText.cardTitle.copy(fontSize = 19.sp), color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(PathCopy.EMPTY_LINE, style = PathText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        HotPrimaryButton(PathCopy.NAME_GOAL, onClick = actions.onNameGoal, modifier = Modifier.fillMaxWidth().testTag(PATH_NAME_GOAL_TAG))
        HotTextButton(PathCopy.FROM_CARRYING, onClick = actions.onFromCarrying)
    }
}

/** The label of balance and the one step (A55 §1 п. 5): a word, never a number. */
@Composable
private fun BalanceCard(ov: PathOverview, onStep: () -> Unit) {
    val c = PathColors.current
    val b = ov.balance
    val color = when (b.kind) {
        BalanceKind.LEVER -> c.lever
        BalanceKind.EVEN -> c.earth
        BalanceKind.HEAVY, BalanceKind.PAUSED, BalanceKind.HELD -> c.water
        BalanceKind.NO_GOAL -> MaterialTheme.colorScheme.onSurface
    }
    HotCard(modifier = Modifier.fillMaxWidth().testTag(PATH_BALANCE_TAG)) {
        Text(b.title, style = PathText.cardTitle, color = color)
        Spacer(Modifier.height(4.dp))
        Text(b.sub, style = PathText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (b.kind == BalanceKind.LEVER && b.lever?.markable == true) {
            Spacer(Modifier.height(4.dp))
            HotTextButton(PathCopy.MARK_TAKEN_ACTION, onClick = onStep)
        }
    }
}

/**
 * The trail of the stage (§5.1 block 6): 12 stones by the calendar — no «missed» weeks; milestones
 * r 7, the current stone r 6, passed r 3.5, future r 3.5 outlined; ‖ on pause.
 */
@Composable
private fun StageTrail(ov: PathOverview) {
    val c = PathColors.current
    val tokens = HotThemeTokens.colors
    val h = ov.horizon
    Column(Modifier.fillMaxWidth().testTag(PATH_TRAIL_TAG)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HotOverline(PathCopy.STAGE_OVERLINE)
            Spacer(Modifier.weight(1f))
            val right = buildList {
                add(PathCopy.week(ov.week))
                ov.nextMilestone?.let { (_, days) -> add(PathCopy.toMilestone(days)) }
            }.joinToString(" · ")
            Text(right, style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
        }
        Spacer(Modifier.height(8.dp))
        val desc = PathCopy.week(ov.week) + (ov.nextMilestone?.let { ". " + PathCopy.toMilestone(it.second) } ?: "")
        Canvas(Modifier.fillMaxWidth().height(34.dp).semantics { contentDescription = desc }) {
            val n = StageRules.WEEKS
            val pad = 8.dp.toPx()
            val step = (size.width - 2 * pad) / (n - 1)
            val y = size.height / 2
            drawLine(tokens.rule, Offset(pad, y), Offset(size.width - pad, y), strokeWidth = 1.dp.toPx())
            for (i in 1..n) {
                val x = pad + (i - 1) * step
                val milestone = i in StageRules.MILESTONES
                when {
                    i == ov.week -> drawCircle(c.goalTick, 6.dp.toPx(), Offset(x, y))
                    i < ov.week -> drawCircle(c.earth, 3.5.dp.toPx(), Offset(x, y))
                    else -> drawCircle(tokens.rule, 3.5.dp.toPx(), Offset(x, y), style = Stroke(1.dp.toPx()))
                }
                if (milestone) drawCircle(c.earth.copy(alpha = 0.7f), 7.dp.toPx(), Offset(x, y), style = Stroke(1.dp.toPx()))
                if (ov.paused && i == ov.week) {
                    val dx = 2.dp.toPx()
                    val top = y - 16.dp.toPx()
                    drawLine(tokens.textTertiary, Offset(x - dx, top), Offset(x - dx, top + 7.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                    drawLine(tokens.textTertiary, Offset(x + dx, top), Offset(x + dx, top + 7.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                }
            }
        }
        if (h != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (h.seated) PathCopy.HORIZON_SEATED else PathCopy.horizonLine(h.step),
                style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One element (§5.1 block 7): the row 56 dp, unfolded — its Titan 72 dp and the parts of the goal. */
@Composable
private fun ElementCard(ev: ElementView, expanded: Boolean, onToggle: () -> Unit, onPart: (ComponentKey) -> Unit, still: Boolean) {
    val material = materialOf(ev.element)
    HotCard(
        modifier = Modifier.fillMaxWidth().testTag(elementTag(ev.element)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(material))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(PathLexicon.element(ev.element), style = PathText.cardTitle, color = MaterialTheme.colorScheme.onSurface)
                Text(PathLexicon.motto(ev.element), style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(ev.status, style = PathText.caption, color = material)
        }
        if (expanded) {
            Row(verticalAlignment = Alignment.Top) {
                PathTitan(PathArt.header(ev.element), Modifier.size(72.dp), still = still)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    ev.parts.sortedBy { roleOrder(it.role) }.forEach { p ->
                        Column(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPart(p.key) }.padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(PathLexicon.name(p.key), style = PathText.body.copy(fontWeight = PathText.cardTitle.fontWeight), color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(8.dp))
                                p.roleWord()?.let { Text(it, style = PathText.caption, color = material) }
                                if (p.lever) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(PathCopy.LEVER_BADGE, style = PathText.caption, color = PathColors.current.lever)
                                }
                            }
                            val line = when (p.state) {
                                PartState.MARKS -> PathCopy.markLine(p.mark.takeIf { it > 0 }, p.target)
                                else -> PathCopy.PRACTICE
                            } + (p.safeVersion?.let { " · $it" } ?: "")
                            Text(line, style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (ev.outside > 0) {
                        Text(
                            PathCopy.more(ev.outside, PathLexicon.element(ev.element)),
                            style = PathText.caption, color = HotThemeTokens.colors.textTertiary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Milestones of the stage and the measurement (§5.1 block 8). */
@Composable
private fun Milestones(goal: PracticalGoal, today: LocalDate) {
    Column(Modifier.fillMaxWidth()) {
        HotOverline(PathCopy.MILESTONES)
        Spacer(Modifier.height(8.dp))
        StageRules.MILESTONES.forEach { w ->
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(PathCopy.milestone(w, StageRules.milestone(goal, w, today)), style = PathText.body, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                if (w < StageRules.WEEKS) Text(PathCopy.MEASURE, style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
