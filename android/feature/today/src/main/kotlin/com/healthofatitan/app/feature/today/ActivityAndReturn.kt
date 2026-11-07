package com.healthofatitan.app.feature.today

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.component.MiniRing
import com.healthofatitan.app.core.ui.component.TitanLights
import com.healthofatitan.app.core.ui.component.TitanScene
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.core.ui.theme.Titanium
import com.healthofatitan.app.domain.engine.DayEngine
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Steps
import com.healthofatitan.app.integration.healthconnect.HealthConnectSteps
import com.healthofatitan.app.integration.healthconnect.PhoneSteps
import com.healthofatitan.app.integration.whoop.day.DayView
import java.time.LocalDate

const val ACTIVITY_TAG = "today_activity"
const val ACTIVITY_ASK_TAG = "today_activity_ask"
const val WELCOME_BACK_TAG = "today_welcome_back"

/**
 * Activity: plan against fact (A35 §4, D-024). The phone's steps against the personal goal as a
 * ring, and — when the plan has a walk — whether the steps inside its slot confirm it. Without
 * Health Connect the card is absent; without the permission it asks in place, once, with the
 * reason (A18 И3b.5). Nothing here is stored (R-11).
 *
 * @param onPermission the permission dialog closed (granted or not): re-read the steps
 */
@Composable
fun ActivityCard(plan: DayView?, steps: PhoneSteps, mode: HotMode, onPermission: () -> Unit, modifier: Modifier = Modifier) {
    when (steps) {
        PhoneSteps.Unavailable -> Unit
        PhoneSteps.NeedsPermission -> StepsAsk(onPermission, modifier)
        is PhoneSteps.Ready -> StepsFact(plan, steps, mode, modifier)
    }
}

@Composable
private fun StepsAsk(onPermission: () -> Unit, modifier: Modifier) {
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { onPermission() }
    HotCard(modifier = modifier.fillMaxWidth().testTag(ACTIVITY_ASK_TAG)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepsGlyph(DailyTexts.STEPS, MaterialTheme.colorScheme.onSurface, size = 28.dp)
            Spacer(Modifier.width(12.dp))
            Text(DailyTexts.STEPS_ASK, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        HotTextButton(DailyTexts.STEPS_ALLOW, onClick = { launcher.launch(HealthConnectSteps.PERMISSIONS) })
    }
}

@Composable
private fun StepsFact(plan: DayView?, steps: PhoneSteps.Ready, mode: HotMode, modifier: Modifier) {
    val training = plan?.items?.firstOrNull { it.id == DayEngine.Ids.TRAINING }
    val walkMinutes = training?.takeIf { Steps.isWalk(it.rule) }?.let { t -> t.startMin?.let { s -> t.endMin?.minus(s) } }
    val walkBySteps = walkMinutes != null && training?.status == ItemStatus.OPEN && Steps.walkConfirmed(walkMinutes, steps.inSlot)
    HotCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ACTIVITY_TAG)
            .clearAndSetSemantics {
                contentDescription = DailyTexts.stepsSpoken(steps.today, steps.goal) +
                    if (walkBySteps) ", " + DailyTexts.WALK_BY_STEPS else ""
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniRing(steps.fraction, MaterialTheme.colorScheme.onSurface, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepsGlyph(DailyTexts.STEPS, HotThemeTokens.colors.textTertiary, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    HotOverline(DailyTexts.STEPS)
                }
                Spacer(Modifier.height(4.dp))
                Text(DailyTexts.steps(steps.today, steps.goal), style = HotType.ringValue.copy(fontSize = HotType.ringValue.fontSize * 1.3f))
            }
            if (training != null && walkMinutes != null) {
                KindBadge(training.kind, mode.color(), sizeDp = 40, filled = walkBySteps || training.status != ItemStatus.OPEN)
                if (walkBySteps) {
                    Spacer(Modifier.width(6.dp))
                    StatusGlyph(ItemStatus.DONE_WHOOP, mode.color(), Titanium.Depth, size = 22.dp)
                }
            }
        }
    }
}

/**
 * «С возвращением» (A18 И4.4, A11 §3.3, A37 §6 «возвращение — Титан снова в свете, кольцо
 * начинается»): after 7+ days of silence, once, before «Сегодня». No numbers of the pause, no
 * reproach; the minimal plan waits behind the one button.
 */
@Composable
fun WelcomeBackScreen(today: LocalDate, onStart: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HotSpacing.edge, vertical = 24.dp)
            .testTag(WELCOME_BACK_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        TitanScene(
            modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp).aspectRatio(1f).clearAndSetSemantics { },
            light = TitanLights.of(HotMode.ROVNO),
            ring = { 0.12f },
            ringColor = MaterialTheme.colorScheme.primary,
            ringTrack = HotThemeTokens.colors.hairline,
            ringStroke = 8f,
        )
        Text(DailyTexts.WELCOME_TITLE, style = HotType.headline, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(DailyTexts.WELCOME_LINE, style = HotType.quote, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Text(DailyTexts.newStart(today), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        HotPrimaryButton(DailyTexts.WELCOME_START, onClick = onStart, trailingArrow = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
    }
}
