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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
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
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.core.ui.theme.PathColors
import com.healthofatitan.app.domain.engine.CrisisGuard
import com.healthofatitan.app.domain.path.ComponentKey
import com.healthofatitan.app.domain.path.Element
import com.healthofatitan.app.domain.path.GoalGate
import com.healthofatitan.app.domain.path.GoalMode
import com.healthofatitan.app.domain.path.GoalStatus
import com.healthofatitan.app.domain.path.HorizonTitan
import com.healthofatitan.app.domain.path.PathCopy
import com.healthofatitan.app.domain.path.PathLexicon
import com.healthofatitan.app.domain.path.PathOverviewBuilder
import com.healthofatitan.app.domain.path.PathTemplates
import com.healthofatitan.app.domain.path.PracticalGoal
import com.healthofatitan.app.domain.path.Realism
import com.healthofatitan.app.domain.path.Role
import com.healthofatitan.app.domain.path.StageRules
import com.healthofatitan.app.feature.path.logic.DeadlineChoice
import com.healthofatitan.app.feature.path.logic.RitualDraft
import com.healthofatitan.app.feature.path.logic.RitualLogic
import com.healthofatitan.app.feature.path.logic.RitualStep
import com.healthofatitan.app.feature.path.slice.SliceGeometry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

const val RITUAL_SCREEN_TAG = "screen_path_ritual"
const val RITUAL_NEXT_TAG = "ritual_next"
const val RITUAL_SKIP_TAG = "ritual_skip"
const val RITUAL_HELP_TAG = "ritual_help"
const val RITUAL_FIELD_TAG = "ritual_field"
const val RITUAL_CRISIS_TAG = "ritual_crisis"

fun ritualTag(step: RitualStep) = "ritual_${step.name.lowercase()}"

/** «Назови цель» (A56 §2): full screen, outside the tabs. */
fun NavGraphBuilder.pathRitualScreen(onClose: () -> Unit, onHelp: () -> Unit, stateful: Boolean = true) {
    composable(route = HotRoute.PATH_RITUAL) {
        if (stateful) {
            RitualRoute(onClose, onHelp)
        } else {
            RitualScreen(RitualUi(RitualDraft(), PathData.empty(LocalDate.now())), RitualActions(onClose = onClose, onHelp = onHelp))
        }
    }
}

/** The ritual's state: the draft and the data it reads («Дано», sleep, the other goals). */
data class RitualUi(
    val draft: RitualDraft,
    val data: PathData,
    /** CrisisGuard on the last free text: 1 keeps help visible, ≥ 2 opens SOS (A07). */
    val crisis: Int = 0,
)

class RitualActions(
    val onDraft: (RitualDraft) -> Unit = {},
    val onText: (String, (RitualDraft, String) -> RitualDraft) -> Unit = { _, _ -> },
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onTake: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onHelp: () -> Unit = {},
)

@HiltViewModel
class RitualViewModel @Inject constructor(private val repository: PathRepository) : ViewModel() {
    private val draft = MutableStateFlow(RitualDraft())
    private val crisis = MutableStateFlow(0)

    val ui: StateFlow<RitualUi?> = combine(draft, repository.data, crisis) { d, data, c -> RitualUi(d, data, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun set(d: RitualDraft) = draft.update { d }

    /** Free text is read by CrisisGuard on the phone first; it never leaves the device (P-21). */
    fun text(value: String, apply: (RitualDraft, String) -> RitualDraft) {
        crisis.value = maxOf(crisis.value, CrisisGuard.assess(value).level.value)
        draft.update { apply(it, value) }
    }

    fun next() = draft.update { d -> d.copy(step = RitualStep.entries.getOrElse(d.step.ordinal + 1) { d.step }) }

    fun back() = draft.update { d -> d.copy(step = RitualStep.entries.getOrElse(d.step.ordinal - 1) { d.step }) }

    /** «Беру путь»: the goal starts today; the first fire from «Взято» flies at once. */
    fun take(onDone: () -> Unit) {
        viewModelScope.launch {
            val data = repository.data.first()
            val goal = build(draft.value, data)
            if (RitualLogic.violations(goal, data.given, PathFeatureFlags.current, data.goals).isEmpty()) {
                repository.start(goal, RitualLogic.firstFire(goal, emptyList()), draft.value.letter)
                onDone()
            }
        }
    }
}

internal fun build(d: RitualDraft, data: PathData, nowMs: Long = System.currentTimeMillis()): PracticalGoal {
    val flags = PathFeatureFlags.current
    val dec = RitualLogic.decomposition(d, flags, data.given, emptyList(), data.sufficiency)
    return RitualLogic.build(d, data.today, nowMs, flags, data.given, dec)
}

@Composable
private fun RitualRoute(onClose: () -> Unit, onHelp: () -> Unit, viewModel: RitualViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val state = ui ?: RitualUi(RitualDraft(), PathData.empty(LocalDate.now()))
    LaunchedEffect(state.crisis) { if (state.crisis >= 2) onHelp() }
    RitualScreen(
        state,
        RitualActions(
            onDraft = viewModel::set,
            onText = viewModel::text,
            onNext = viewModel::next,
            onBack = { if (state.draft.step == RitualStep.GOAL) onClose() else viewModel.back() },
            onTake = { viewModel.take(onClose) },
            onClose = onClose,
            onHelp = onHelp,
        ),
    )
}

/**
 * The frame of every screen (A58-VP §5.2): «Назад» · step dots · «Помощь» (≤ 2 taps to A07), the
 * illustration, the title, the content, the main button 56 dp and «Пропустить» in the thumb zone.
 */
@Composable
fun RitualScreen(ui: RitualUi, actions: RitualActions, modifier: Modifier = Modifier, still: Boolean = false) {
    val d = ui.draft
    val flags = PathFeatureFlags.current
    val title = RitualLogic.title(d, flags, ui.data.given)
    val gate = RitualLogic.gate(d, flags, ui.data.given)
    val ready = when (d.step) {
        RitualStep.GOAL -> title.isNotBlank() && gate == GoalGate.OK
        RitualStep.PARTS, RitualStep.TAKE -> RitualLogic.violations(build(d, ui.data), ui.data.given, flags, ui.data.goals).isEmpty()
        else -> true
    }
    Column(modifier.fillMaxSize().testTag(RITUAL_SCREEN_TAG).imePadding()) {
        RitualBar(d.step, actions)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).testTag(ritualTag(d.step)),
        ) {
            when (d.step) {
                RitualStep.GOAL -> GoalStep(ui, actions, gate, still)
                RitualStep.TRUTH -> TruthStep(ui, actions)
                RitualStep.BE -> BeStep(ui, actions)
                RitualStep.PARTS -> PartsStep(ui, actions, still)
                RitualStep.OBSTACLE -> ObstacleStep(ui, actions)
                RitualStep.REAL -> RealStep(ui, actions)
                RitualStep.TAKE -> TakeStep(ui, actions, still)
            }
            if (ui.crisis >= 1) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().testTag(RITUAL_CRISIS_TAG), verticalAlignment = Alignment.CenterVertically) {
                    Text(PathCopy.CRISIS_LINE, style = PathText.body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    HotTextButton(PathCopy.HELP, onClick = actions.onHelp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            HotPrimaryButton(
                PathCopy.CTA[d.step.ordinal],
                enabled = ready,
                onClick = if (d.step == RitualStep.TAKE) actions.onTake else actions.onNext,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(RITUAL_NEXT_TAG),
            )
            if (d.step != RitualStep.GOAL && d.step != RitualStep.TAKE) {
                HotTextButton(PathCopy.SKIP, onClick = actions.onNext, modifier = Modifier.align(Alignment.CenterHorizontally).testTag(RITUAL_SKIP_TAG))
            } else if (d.step == RitualStep.GOAL) {
                HotTextButton(PathCopy.SKIP, onClick = actions.onClose, modifier = Modifier.align(Alignment.CenterHorizontally).testTag(RITUAL_SKIP_TAG))
            }
        }
    }
}

@Composable
private fun RitualBar(step: RitualStep, actions: RitualActions) {
    val tokens = HotThemeTokens.colors
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.onBack, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.core_ui_ic_back), contentDescription = PathCopy.BACK, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier.weight(1f).semantics { contentDescription = "${step.ordinal + 1} из ${RitualStep.entries.size}" },
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            RitualStep.entries.forEach { s ->
                Box(
                    Modifier
                        .size(width = if (s == step) 22.dp else 8.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (s.ordinal <= step.ordinal) dawnText() else tokens.rule),
                )
            }
        }
        IconButton(onClick = actions.onHelp, modifier = Modifier.size(48.dp).testTag(RITUAL_HELP_TAG)) {
            Icon(painterResource(R.drawable.core_ui_ic_sos), contentDescription = PathCopy.HELP, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Heading(text: String, sub: String?, first: Boolean = false) {
    Spacer(Modifier.height(16.dp))
    Text(
        text,
        style = if (first) PathText.ritualH1First else PathText.ritualH1,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.semantics { heading() },
    )
    if (sub != null) {
        Spacer(Modifier.height(6.dp))
        Text(sub, style = PathText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun Field(
    value: String,
    hint: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = PathText.body,
    max: Int = 120,
    single: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= max) onValue(it) },
        placeholder = { Text(hint, style = style, color = HotThemeTokens.colors.textTertiary) },
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
        singleLine = single,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = dawnText(), unfocusedBorderColor = HotThemeTokens.colors.rule),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(RITUAL_FIELD_TAG),
    )
}

/** Э1 · Цель: templates, a hint from data, one's own words; the Titan offers the empty place in his palms. */
@Composable
private fun GoalStep(ui: RitualUi, actions: RitualActions, gate: GoalGate, still: Boolean) {
    val d = ui.draft
    val c = PathColors.current
    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
        PathTitan(PathArt.PALMS, Modifier.size(240.dp), once = true, still = still)
        Canvas(Modifier.size(120.dp, 46.dp).align(Alignment.Center)) {
            drawOval(
                c.star.copy(alpha = 0.25f), topLeft = Offset.Zero, size = Size(size.width, size.height),
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
            )
        }
    }
    Heading(PathCopy.E1_TITLE, PathCopy.E1_SUB, first = true)
    var more by remember { mutableStateOf(false) }
    val offered = PathTemplates.offered(PathFeatureFlags.current, ui.data.given)
    val shown = if (more) offered else offered.take(6)
    PathChips(
        shown.map { it.title } + if (!more && offered.size > 6) listOf(PathCopy.MORE) else emptyList(),
        selected = shown.indexOfFirst { it.id == d.templateId }.takeIf { it >= 0 },
        onPick = { i ->
            if (i >= shown.size) more = true else actions.onDraft(d.copy(templateId = shown[i].id, text = ""))
        },
        tag = "ritual_template",
    )
    ui.data.sleepHours?.takeIf { it < PathData.SLEEP_THRESHOLD }?.let { h ->
        Spacer(Modifier.height(12.dp))
        val hours = String.format(java.util.Locale.forLanguageTag("ru"), "%.1f", h)
        HintRow(PathCopy.e1FromSleep(hours)) { actions.onDraft(d.copy(templateId = PathTemplates.ENERGY, text = "")) }
    }
    Spacer(Modifier.height(16.dp))
    HotOverline(PathCopy.E1_OWN)
    Spacer(Modifier.height(8.dp))
    Field(d.text, PathCopy.E1_HINT, { v -> actions.onText(v) { dr, t -> dr.copy(text = t, templateId = null) } }, max = RitualLogic.TITLE_MAX)
    PathCopy.gate(gate)?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = PathText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ReviewNote()
    }
}

@Composable
private fun HintRow(text: String, onClick: () -> Unit) {
    val tokens = HotThemeTokens.colors
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tokens.glassBottom)
            .border(1.dp, tokens.rule, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = PathText.body, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Э2 · Правда к сроку и «Зачем»: the star fills as «Зачем» is written. */
@Composable
private fun TruthStep(ui: RitualUi, actions: RitualActions) {
    val d = ui.draft
    val c = PathColors.current
    val alpha = 0.25f + 0.75f * minOf(d.why.trim().length / 20f, 1f)
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val s = 1.dp.toPx()
        val cx = size.width / 2
        drawLine(c.star.copy(alpha = c.axis), Offset(cx, 44 * s), Offset(cx, size.height), strokeWidth = s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2 * s, 4 * s)))
        withTransform({ translate(cx - 194 * s, 0f); scale(s, s, pivot = Offset.Zero) }) {
            drawPath(starPath(SliceGeometry.STAR, SliceGeometry.STAR_OUTER, SliceGeometry.STAR_INNER), c.star.copy(alpha = alpha))
        }
    }
    Heading(PathCopy.E2_TITLE, null)
    HotOverline(PathCopy.E2_DEADLINE)
    Spacer(Modifier.height(8.dp))
    PathChips(PathCopy.E2_DEADLINES, d.deadline.ordinal, { actions.onDraft(d.copy(deadline = DeadlineChoice.entries[it])) }, tag = "ritual_deadline")
    Spacer(Modifier.height(4.dp))
    Text(PathCopy.deadline(RitualLogic.deadline(d.deadline, ui.data.today)), style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    RitualLogic.criteria(d, PathFeatureFlags.current, ui.data.given).forEach { cr ->
        HintRow(PathCopy.criterion(cr)) {}
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(12.dp))
    Text(PathCopy.E2_WHY, style = PathText.option, color = MaterialTheme.colorScheme.onSurface)
    Text(PathCopy.E2_WHY_HINT, style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Field(d.why, PathCopy.WHY_EMPTY, { v -> actions.onText(v) { dr, t -> dr.copy(why = t) } }, style = HotType.voice)
}

/** Э3 · Кем ты будешь: the Titan of the horizon gets clearer with every letter. */
@Composable
private fun BeStep(ui: RitualUi, actions: RitualActions) {
    val d = ui.draft
    val c = PathColors.current
    val deadline = RitualLogic.deadline(d.deadline, ui.data.today)
    val name = PathCopy.horizonName(d.beName, deadline)
    val alpha = 0.3f + 0.6f * minOf(d.beName.trim().length / 16f, 1f)
    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.TopCenter) {
        Text(name, style = HotType.horizonNameLarge, color = c.horizonLine, maxLines = 1, modifier = Modifier.padding(top = 16.dp))
        Canvas(Modifier.fillMaxWidth().height(260.dp).align(Alignment.BottomCenter).semantics { contentDescription = name }) {
            // the figure at week 12 of the slice's scale (the height of the day Titan), centred
            val k = size.height / 130f
            withTransform({
                translate(size.width / 2 - SliceGeometry.CX.toFloat() * k, size.height - SliceGeometry.HORIZON_Y.toFloat() * k)
                scale(k, k, pivot = Offset.Zero)
            }) {
                drawHorizonTitan(
                    HorizonTitan(0, name, emptyList(), 0, seated = false),
                    week = 4, lightOnly = false, colors = c, elements = emptyList(),
                    tint = c.horizonLine.copy(alpha = alpha),
                )
            }
        }
    }
    Heading(PathCopy.E3_TITLE, PathCopy.E3_SUB)
    Field(d.beName, PathCopy.E3_HINT, { v -> actions.onText(v) { dr, t -> dr.copy(beName = t) } }, style = HotType.horizonNameLarge, max = 60)
    Spacer(Modifier.height(8.dp))
    Text(PathCopy.e3Default(deadline), style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The preview of the goal on a slice (Э4, Э7): the same drawing as the screen «Путь». */
@Composable
private fun PreviewSlice(goal: PracticalGoal, ui: RitualUi, still: Boolean, transferred: Set<ComponentKey> = emptySet(), scale: Float = 0.85f) {
    val ov = PathOverviewBuilder.build(ui.data.today, goal.copy(status = GoalStatus.ACTIVE), emptyMap(), transferred, ui.data.given)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        WorldSlice(ov, Modifier.fillMaxWidth(scale), mode = ui.data.mode, why = goal.why, still = still)
    }
}

/** Э4 · Раскладка: the parts, their roles and the lever; «Дано» keeps its safe versions. */
@Composable
private fun PartsStep(ui: RitualUi, actions: RitualActions, still: Boolean) {
    val d = ui.draft
    val flags = PathFeatureFlags.current
    val dec = RitualLogic.decomposition(d, flags, ui.data.given, emptyList(), ui.data.sufficiency)
    val parts = RitualLogic.parts(d, dec)
    val goal = build(d, ui.data)
    PreviewSlice(goal, ui, still)
    Heading(PathCopy.E4_TITLE, PathCopy.E4_SUB)
    parts.forEach { p ->
        val material = materialOf(p.key.element)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { actions.onDraft(d.copy(parts = RitualLogic.cycleRole(parts, p.key))) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(material))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(PathLexicon.name(p.key), style = PathText.body, color = MaterialTheme.colorScheme.onSurface)
                p.safeVersion?.let { Text(it, style = PathText.caption, color = PathColors.current.dano) }
            }
            Text(PathLexicon.role(p.role), style = PathText.caption, color = material)
            val left = RitualLogic.remove(parts, p.key)
            if (left != null) {
                HotTextButton(PathCopy.E4_REMOVE, onClick = { actions.onDraft(d.copy(parts = left)) })
            }
        }
    }
    dec.lever?.let { l ->
        Spacer(Modifier.height(8.dp))
        val detail = ui.data.sleepHours?.takeIf { l.key == ComponentKey.SLEEP }?.let { String.format(java.util.Locale.forLanguageTag("ru"), "%.1f ч из 7", it) }
        Text(PathCopy.e4Lever(PathLexicon.name(l.key), detail), style = PathText.caption, color = PathColors.current.lever)
    }
    dec.givenNotes.forEach { Text(PathCopy.e4Given(it), style = PathText.caption, color = PathColors.current.dano) }
    dec.startFrom?.let { Text(PathCopy.e4Start(it.since), style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Spacer(Modifier.height(12.dp))
    HotOverline(PathCopy.E4_ADD)
    Spacer(Modifier.height(8.dp))
    PathChips(Element.entries.map(PathLexicon::element), null, { i ->
        actions.onDraft(d.copy(parts = RitualLogic.add(parts, Element.entries[i], flags, ui.data.given)))
    }, tag = "ritual_add")
    RitualLogic.violations(goal, ui.data.given, flags, ui.data.goals).forEach {
        Text(PathCopy.violation(it), style = PathText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Э5 · Что встанет на пути: an obstacle and «если — то» (WOOP); it becomes the first goal of the week. */
@Composable
private fun ObstacleStep(ui: RitualUi, actions: RitualActions) {
    val d = ui.draft
    val tokens = HotThemeTokens.colors
    Spacer(Modifier.height(24.dp))
    Heading(PathCopy.E5_TITLE, null)
    val hint = RitualLogic.basis(d, PathFeatureFlags.current, ui.data.given)?.obstacleHint
    if (hint != null) {
        PathChips(listOf(hint), if (d.obstacle == hint) 0 else null, { actions.onDraft(d.copy(obstacle = hint)) }, tag = "ritual_obstacle")
        Spacer(Modifier.height(12.dp))
    }
    HotOverline(PathCopy.E5_OWN)
    Spacer(Modifier.height(8.dp))
    Field(d.obstacle, hint ?: PathCopy.E5_OWN, { v -> actions.onText(v) { dr, t -> dr.copy(obstacle = t) } })
    Spacer(Modifier.height(16.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(tokens.glassBottom).border(1.dp, tokens.rule, RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        HotOverline(PathCopy.E5_IF_THEN)
        Spacer(Modifier.height(8.dp))
        Field(
            d.ifThen,
            PathCopy.e5IfThen(d.obstacle.ifBlank { "…" }),
            { v -> actions.onText(v) { dr, t -> dr.copy(ifThen = t) } },
            style = PathText.body.copy(fontSize = 17.sp, lineHeight = 24.sp),
            single = false,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(PathCopy.E5_FIRST_WEEK, style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Э6 · Как реально (R7): «к сроку» or «целиком»; never a dangerous pace, never a «потолок». */
@Composable
private fun RealStep(ui: RitualUi, actions: RitualActions) {
    val d = ui.draft
    val deadline = RitualLogic.deadline(d.deadline, ui.data.today)
    val realism = RitualLogic.realism(d, ui.data.today, PathFeatureFlags.current, ui.data.given)
    Spacer(Modifier.height(24.dp))
    val line = when (realism) {
        Realism.NoPace -> PathCopy.E6_NO_PACE
        is Realism.OnTrack -> PathCopy.E6_ON_TRACK
        is Realism.Split -> null
        Realism.ProcessOnly -> PathCopy.E6_PROCESS_ONLY
    }
    Heading(PathCopy.E6_TITLE, line)
    val unit = RitualLogic.criteria(d, PathFeatureFlags.current, ui.data.given).firstOrNull()?.unit.orEmpty()
    val byDateTitle = (realism as? Realism.Split)?.let { PathCopy.e6ByDate(deadline, "${fmt(it.byDate.start)}…${fmt(it.byDate.endInclusive)} $unit".trim()) }
        ?: PathCopy.deadline(deadline)
    val fullTitle = (realism as? Realism.Split)?.let { PathCopy.e6Full(RitualLogic.criteria(d, PathFeatureFlags.current, ui.data.given).firstOrNull()?.let(PathCopy::criterion).orEmpty(), it.fullBy) }
        ?: PathCopy.E6_FULL_SUB
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionCard(byDateTitle, PathCopy.E6_BY_DATE_SUB, d.mode == GoalMode.BY_DATE, { actions.onDraft(d.copy(mode = GoalMode.BY_DATE)) }, overline = PathCopy.E6_BY_DATE, minHeight = 96)
        OptionCard(fullTitle, if (realism is Realism.Split) PathCopy.E6_FULL_SUB else null, d.mode == GoalMode.FULL, { actions.onDraft(d.copy(mode = GoalMode.FULL)) }, overline = PathCopy.E6_FULL, minHeight = 96)
    }
    Spacer(Modifier.height(12.dp))
    Text(PathCopy.E6_NOT_CEILING, style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun fmt(v: Double): String = if (v == kotlin.math.floor(v)) v.toLong().toString() else v.toString().replace('.', ',')

/** Э7 · Беру путь: the Titan of the horizon is already coming; the first fire is already yours. */
@Composable
private fun TakeStep(ui: RitualUi, actions: RitualActions, still: Boolean) {
    val d = ui.draft
    val goal = build(d, ui.data)
    val first = goal.components.firstOrNull { it.role == Role.LEAD && it.startMark != null }?.key
    PreviewSlice(goal, ui, still, transferred = setOfNotNull(first), scale = 0.9f)
    Heading(PathCopy.E7_COMING, null)
    Text(PathCopy.horizonName(goal.beName, goal.deadline), style = HotType.horizonNameLarge, color = PathColors.current.horizonLine)
    Spacer(Modifier.height(8.dp))
    Text(
        PathCopy.e7Trail(StageRules.milestone(goal, 4, ui.data.today), StageRules.milestone(goal, 8, ui.data.today), StageRules.milestone(goal, 12, ui.data.today)),
        style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    first?.let {
        Spacer(Modifier.height(4.dp))
        Text(PathCopy.e7FirstFire(PathLexicon.name(it)), style = PathText.caption, color = materialOf(it.element))
    }
    // D-051 (tech §6.4): «Письмо — позже». The letter to oneself is built and waits behind the flag.
    if (PathFeatureFlags.LETTER) {
        Spacer(Modifier.height(16.dp))
        var writing by remember { mutableStateOf(d.letter.isNotEmpty()) }
        if (!writing) {
            val rule = HotThemeTokens.colors.rule
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { writing = true }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                    drawRoundRect(
                        rule, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
                    )
                }
                Text(PathCopy.E7_LETTER, style = PathText.body, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            }
        } else {
            Text(PathCopy.E7_LETTER_HINT, style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Field(d.letter, PathCopy.E7_LETTER_HINT, { v -> actions.onText(v) { dr, t -> dr.copy(letter = t) } }, max = 300, single = false)
        }
    }
}
