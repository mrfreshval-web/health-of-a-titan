package com.healthofatitan.app.feature.today

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPendingCard
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.domain.engine.Body
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.integration.whoop.day.DayItemView
import com.healthofatitan.app.integration.whoop.day.DayView
import java.time.LocalDate

const val DAY_REGIME_TAG = "today_day_regime"
const val DAY_PLAN_TAG = "today_day_plan"
const val DAY_PENDING_TAG = "today_day_pending"
const val DAY_EVENING_TAG = "today_day_evening"

/**
 * Slice 2 on «Сегодня» (A11 §1.1, A09, A35 §4): the regime card with «Почему так», the day plan
 * with tap-to-mark items (a WHOOP workout confirms the training item by itself) and, in the
 * evening, the deck of at most 3 taps (D-027). Kept apart from the slice 1 screen.
 */
@Composable
fun ColumnScope.DayRoute(viewModel: DayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.plan?.dayKey, state.today) { viewModel.opened() }
    if (!state.loaded) return
    DaySection(
        state = state,
        onToggle = viewModel::toggle,
        onEvening = viewModel::saveEvening,
    )
}

@Composable
fun ColumnScope.DaySection(
    state: DayUiState,
    onToggle: (LocalDate, String) -> Unit,
    onEvening: (LocalDate, Int?, Body?) -> Unit,
) {
    val plan = state.plan
    if (plan == null || !state.planToday) {
        HotPendingCard(
            title = DayUiTexts.PENDING_TITLE,
            caption = DayUiTexts.PENDING_CAPTION,
            modifier = Modifier.fillMaxWidth().testTag(DAY_PENDING_TAG),
        )
    } else {
        NotificationAsk()
        RegimeCard(plan)
        PlanCard(plan, onToggle)
    }
    val eveningDay = state.eveningFor
    when {
        eveningDay != null -> EveningDeckCard(eveningDay, onEvening)
        state.eveningJustSaved -> HotPendingCard(title = DayUiTexts.DONE, modifier = Modifier.fillMaxWidth())
    }
}

/** Asks for POST_NOTIFICATIONS once today's plan is ready (the morning push needs it). */
@Composable
private fun NotificationAsk() {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        dismissed = true
    }
    if (granted || dismissed) return
    HotCard(modifier = Modifier.fillMaxWidth()) {
        Text(DayUiTexts.NOTIFY_HINT, style = MaterialTheme.typography.bodyMedium)
        Row {
            HotTextButton(DayUiTexts.NOTIFY_ALLOW, onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) })
            HotTextButton(DayUiTexts.SKIP, onClick = { dismissed = true })
        }
    }
}

@Composable
private fun regimeColor(regime: Regime): Color {
    val tokens = HotThemeTokens.colors
    return when (regime) {
        Regime.BOOST -> tokens.modeRazgon
        Regime.STEADY -> tokens.modeRovno
        Regime.GENTLE -> tokens.modeBerezhno
    }
}

@Composable
private fun RegimeCard(plan: DayView) {
    var why by rememberSaveable(plan.dayKey) { mutableStateOf(false) }
    val tokens = HotThemeTokens.colors
    HotCard(modifier = Modifier.fillMaxWidth().testTag(DAY_REGIME_TAG), cornice = true) {
        HotOverline(DayUiTexts.REGIME)
        Spacer(Modifier.padding(top = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(12.dp).background(regimeColor(plan.regime), CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(plan.regime.ru, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.padding(top = 4.dp))
        Text(plan.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (plan.flags.isNotEmpty()) {
            Spacer(Modifier.padding(top = 4.dp))
            Text(
                plan.flags.joinToString(" · ") { DayUiTexts.flag(it) },
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textTertiary,
            )
        }
        if (plan.fired.isNotEmpty()) {
            HotTextButton(if (why) DayUiTexts.WHY_HIDE else DayUiTexts.WHY, onClick = { why = !why })
            if (why) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    plan.fired.forEach { rule ->
                        Text(rule.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: DayView, onToggle: (LocalDate, String) -> Unit) {
    HotCard(modifier = Modifier.fillMaxWidth().testTag(DAY_PLAN_TAG)) {
        HotOverline(DayUiTexts.PLAN)
        Spacer(Modifier.padding(top = 4.dp))
        Text(
            DayUiTexts.markers(plan.caffeineCutoffMin, plan.windDownMin, plan.bedtimeMin),
            style = MaterialTheme.typography.bodySmall,
            color = HotThemeTokens.colors.textTertiary,
        )
        Spacer(Modifier.padding(top = 6.dp))
        plan.items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = HotThemeTokens.colors.hairline)
            PlanRow(item, onClick = { onToggle(plan.dayKey, item.id) })
        }
    }
}

@Composable
private fun PlanRow(item: DayItemView, onClick: () -> Unit) {
    val done = item.status != ItemStatus.OPEN
    val fromWhoop = item.status == ItemStatus.DONE_WHOOP || item.status == ItemStatus.MODIFIED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = !fromWhoop, role = Role.Checkbox, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            DayUiTexts.slot(item.startMin, item.endMin),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(96.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            item.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
            }
            DayUiTexts.status(item.status)?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** The evening deck (D-027): only what WHOOP does not know — alcohol and how the body feels. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EveningDeckCard(day: LocalDate, onEvening: (LocalDate, Int?, Body?) -> Unit) {
    var alcohol by rememberSaveable(day) { mutableStateOf<Int?>(null) }
    var alcoholDone by rememberSaveable(day) { mutableStateOf(false) }
    HotCard(modifier = Modifier.fillMaxWidth().testTag(DAY_EVENING_TAG)) {
        HotOverline(DayUiTexts.EVENING)
        Spacer(Modifier.padding(top = 6.dp))
        if (!alcoholDone) {
            Text(DayUiTexts.ALCOHOL_Q, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayUiTexts.ALCOHOL_OPTIONS.forEach { (value, label) ->
                    FilterChip(
                        selected = alcohol == value,
                        onClick = {
                            alcohol = value
                            alcoholDone = true
                        },
                        label = { Text(label) },
                    )
                }
            }
            HotTextButton(DayUiTexts.SKIP, onClick = { alcoholDone = true })
        } else {
            Text(DayUiTexts.BODY_Q, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayUiTexts.BODY_OPTIONS.forEach { (value, label) ->
                    FilterChip(selected = false, onClick = { onEvening(day, alcohol, value) }, label = { Text(label) })
                }
            }
            HotTextButton(DayUiTexts.SKIP, onClick = { onEvening(day, alcohol, null) })
        }
    }
}
