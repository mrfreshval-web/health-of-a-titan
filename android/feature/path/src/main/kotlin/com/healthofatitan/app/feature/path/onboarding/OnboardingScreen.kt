package com.healthofatitan.app.feature.path.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.domain.path.PathLexicon
import com.healthofatitan.app.domain.today.WeekGoal

const val ONBOARDING_TAG = "screen_onboarding"
const val ONBOARDING_NEXT_TAG = "onboarding_next"
const val ONBOARDING_LATER_TAG = "onboarding_later"
const val ONBOARDING_HELP_TAG = "onboarding_help"

fun onboardingStepTag(step: OnboardingStep) = "onboarding_${step.name.lowercase()}"

/** What the screens of the first launch can do. */
class OnboardingActions(
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onLater: () -> Unit = {},
    val onHelp: () -> Unit = {},
    val onToggle: (question: Int, chip: String) -> Unit = { _, _ -> },
    val onGoal: (String) -> Unit = {},
    val onWhy: (String) -> Unit = {},
    val onWeeks: (Int) -> Unit = {},
    val onChange: (goalId: String, delta: Int) -> Unit = { _, _ -> },
)

/**
 * The first launch (A66 §0.2, mockups v3 01–04), full screen over the app after the gate 18+ and
 * «Вход»; help is one tap on every step and never behind it (T-06).
 */
@Composable
fun FirstLaunchRoute(onHelp: () -> Unit, onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val state = ui ?: return
    LaunchedEffect(state.crisis) { if (state.crisis >= 2) onHelp() }
    // Step 3 after a restart: the week is already assembled, read it.
    LaunchedEffect(state.step) { if (state.step == OnboardingStep.WEEK && state.week.isEmpty()) viewModel.refreshWeek() }
    OnboardingScreen(
        state,
        OnboardingActions(
            onNext = { viewModel.next(onDone) },
            onBack = viewModel::back,
            onLater = { viewModel.later(onDone) },
            onHelp = onHelp,
            onToggle = viewModel::toggle,
            onGoal = viewModel::goalText,
            onWhy = viewModel::why,
            onWeeks = viewModel::weeks,
            onChange = viewModel::change,
        ),
    )
}

@Composable
fun OnboardingScreen(ui: OnboardingUi, actions: OnboardingActions) {
    HotBackground {
        Box(Modifier.fillMaxSize().testTag(ONBOARDING_TAG)) {
            if (ui.step == OnboardingStep.WELCOME) {
                Welcome(actions)
            } else {
                Step(ui, actions)
            }
        }
    }
}

@Composable
private fun Welcome(actions: OnboardingActions) {
    Column(Modifier.fillMaxSize().testTag(onboardingStepTag(OnboardingStep.WELCOME))) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Image(
                painterResource(R.drawable.core_ui_titan_hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            HotTextButton(OnboardingCopy.HELP, actions.onHelp, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp).testTag(ONBOARDING_HELP_TAG))
        }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(OnboardingCopy.WORDMARK, style = HotType.wordmark, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(40.dp))
            Text(OnboardingCopy.VALUE, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            HotPrimaryButton(OnboardingCopy.START, actions.onNext, Modifier.fillMaxWidth().testTag(ONBOARDING_NEXT_TAG))
        }
    }
}

@Composable
private fun Step(ui: OnboardingUi, actions: OnboardingActions) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag(onboardingStepTag(ui.step))) {
        TopRow(ui, actions)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                OnboardingCopy.stepOf(OnboardingRules.position(ui.step)),
                style = HotType.wordmark,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            when (ui.step) {
                OnboardingStep.BEHIND -> BehindStep(ui, actions)
                OnboardingStep.PATH -> PathStep(ui, actions)
                OnboardingStep.WEEK -> WeekStep(ui, actions)
                OnboardingStep.WELCOME -> Unit
            }
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            HotPrimaryButton(
                text = if (ui.step == OnboardingStep.WEEK) OnboardingCopy.ASSEMBLE else OnboardingCopy.NEXT,
                onClick = actions.onNext,
                enabled = ui.canGoOn,
                loading = ui.busy,
                modifier = Modifier.fillMaxWidth().testTag(ONBOARDING_NEXT_TAG),
            )
            HotTextButton(OnboardingCopy.LATER, actions.onLater, Modifier.testTag(ONBOARDING_LATER_TAG))
        }
    }
}

@Composable
private fun TopRow(ui: OnboardingUi, actions: OnboardingActions) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(96.dp)) {
            if (ui.step != OnboardingStep.BEHIND) {
                Surface(
                    onClick = actions.onBack,
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, HotThemeTokens.colors.rule),
                    modifier = Modifier.size(44.dp).semantics { contentDescription = OnboardingCopy.BACK },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.core_ui_ic_back), contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
            for (i in 1..3) {
                val active = i == OnboardingRules.position(ui.step)
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (active) 22.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (active || i < OnboardingRules.position(ui.step)) MaterialTheme.colorScheme.primary else HotThemeTokens.colors.rule),
                )
            }
        }
        Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) {
            HotTextButton(OnboardingCopy.HELP, actions.onHelp, Modifier.testTag(ONBOARDING_HELP_TAG))
        }
    }
}

@Composable
private fun Title(title: String, sub: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
}

// ── Step 1 · «Откуда ты идёшь» ───────────────────────────────────────────────────────────────────

@Composable
private fun BehindStep(ui: OnboardingUi, actions: OnboardingActions) {
    Title(OnboardingCopy.BEHIND_TITLE, OnboardingCopy.BEHIND_SUB)
    HotCard(Modifier.fillMaxWidth()) {
        val h = ui.history
        if (h == null) {
            Text(OnboardingCopy.IMPORT_NONE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(OnboardingCopy.SOURCE, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(OnboardingCopy.period(h.from, h.to), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Fact(h.trainings.toString(), OnboardingCopy.TRAININGS)
                Fact(h.nightHours?.let(OnboardingCopy::hours) ?: "—", OnboardingCopy.NIGHT)
                Fact(h.charge?.toString() ?: "—", OnboardingCopy.CHARGE)
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Question(OnboardingCopy.TRIED, OnboardingCopy.TRIED_CHIPS, ui.answers.tried) { actions.onToggle(0, it) }
    Question(OnboardingCopy.WORKED, OnboardingCopy.WORKED_CHIPS, ui.answers.worked) { actions.onToggle(1, it) }
    Question(OnboardingCopy.DIFFERENT_Q, OnboardingCopy.DIFFERENT, ui.answers.different) { actions.onToggle(2, it) }
    Text(OnboardingCopy.NO_SCORE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Fact(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1, softWrap = false)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Question(title: String, chips: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { chip -> Chip(chip, chip in selected) { onToggle(chip) } }
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = HotThemeTokens.colors
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else tokens.rule, shape)
            .background(if (selected) tokens.ruleSoft else Color.Transparent, shape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (selected) "✓ $text" else text, style = MaterialTheme.typography.bodyMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

// ── Step 2 · «Куда ты идёшь» ─────────────────────────────────────────────────────────────────────

@Composable
private fun PathStep(ui: OnboardingUi, actions: OnboardingActions) {
    Title(OnboardingCopy.PATH_TITLE, OnboardingCopy.PATH_SUB)
    Label(OnboardingCopy.GOAL)
    Field(ui.goalText, OnboardingCopy.GOAL_HINT, actions.onGoal, MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(16.dp))
    Label(OnboardingCopy.WHY)
    Field(ui.why, OnboardingCopy.WHY_HINT, actions.onWhy, HotType.voice)
    if (ui.crisis >= 1) {
        Spacer(Modifier.height(8.dp))
        HotTextButton(OnboardingCopy.HELP, actions.onHelp)
    }
    Spacer(Modifier.height(16.dp))
    Label(OnboardingCopy.STAGE)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OnboardingRules.STAGES.forEach { n ->
            val selected = n == ui.weeks
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else HotThemeTokens.colors.rule, shape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { actions.onWeeks(n) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(OnboardingCopy.weeks(n), style = MaterialTheme.typography.bodyMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    val lever = ui.suggestion.lever
    if (ui.goal != null) {
        Spacer(Modifier.height(20.dp))
        HotCard(Modifier.fillMaxWidth()) {
            Text(OnboardingCopy.SUGGESTS, style = HotType.wordmark, color = MaterialTheme.colorScheme.primary)
            if (lever != null) {
                Spacer(Modifier.height(8.dp))
                Text(OnboardingCopy.leverTitle(PathLexicon.name(lever)), style = MaterialTheme.typography.titleMedium)
                ui.suggestion.reason?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            ui.suggestion.milestones.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(OnboardingCopy.milestone(m.index, m.label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(m.date.format(OnboardingCopy.DATE), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    ui.stops.forEach {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Field(value: String, hint: String, onChange: (String) -> Unit, style: TextStyle) {
    val shape = RoundedCornerShape(16.dp)
    val color = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = style.copy(color = color),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(HotThemeTokens.colors.glassTop, HotThemeTokens.colors.glassBottom)), shape)
            .border(1.dp, if (value.isNotEmpty()) MaterialTheme.colorScheme.primary else HotThemeTokens.colors.rule, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            }
        },
    )
}

// ── Step 3 · «Первая неделя из Пути» ─────────────────────────────────────────────────────────────

@Composable
private fun WeekStep(ui: OnboardingUi, actions: OnboardingActions) {
    Title(OnboardingCopy.WEEK_TITLE, OnboardingCopy.WEEK_SUB)
    if (ui.week.isEmpty()) {
        Text(OnboardingCopy.WEEK_EMPTY, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ui.week.forEach { g ->
        WeekGoalEditor(g, OnboardingRules.chip(g, ui.suggestion.lever), actions)
        Spacer(Modifier.height(12.dp))
    }
    Text(OnboardingCopy.WEEK_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WeekGoalEditor(goal: WeekGoal, chip: String, actions: OnboardingActions) {
    HotCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(goal.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                chip,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stepper("−", OnboardingCopy.LESS) { actions.onChange(goal.id, -1) }
            Text(
                goal.target.toString(),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(56.dp),
            )
            Stepper("+", OnboardingCopy.MORE) { actions.onChange(goal.id, 1) }
            Spacer(Modifier.width(12.dp))
            Text(goal.unit.ifEmpty { OnboardingCopy.PER_WEEK }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Stepper(symbol: String, a11y: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .border(1.dp, HotThemeTokens.colors.rule, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = a11y },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge)
    }
}
