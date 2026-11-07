package com.healthofatitan.app.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotSecondaryButton
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.domain.today.TodayCopy
import com.healthofatitan.app.domain.today.WhyBuilder
import com.healthofatitan.app.domain.today.WhyInput
import com.healthofatitan.app.domain.today.WhyMetric
import com.healthofatitan.app.domain.today.WhySection
import com.healthofatitan.app.domain.today.WhySectionId
import java.time.LocalDate
import kotlin.math.roundToInt

const val WHY_SCREEN_TAG = "screen_why"
const val WHY_SECTION_TAG = "why_"

/**
 * «Почему так» from what the phone holds (A46 §3): the night of today from the in-memory glance,
 * the corridors of the window, «ты» of «Рассвет». A night that is not today's is not shown as
 * today's: every missing value reads «нет данных».
 */
fun whyInput(state: TodayUiState, day: DayUiState?, today: LocalDate = day?.today ?: LocalDate.now()): WhyInput {
    val glance = state.glance
    val sleep = glance?.sleep?.takeIf { it.wakeDay == today }
    val recovery = glance?.recovery?.takeIf { sleep != null }
    val c = glance?.corridors
    val charge = state.metrics.charge?.value?.takeIf { state.metrics.dayKey == today }
    return WhyInput(
        mode = day?.plan?.takeIf { it.dayKey == today }?.regime?.dayMode(),
        sleepMin = sleep?.hours?.let { (it * 60).roundToInt() },
        needMin = sleep?.needHours?.let { (it * 60).roundToInt() },
        deepMin = sleep?.stages?.deepMin,
        remMin = sleep?.stages?.remMin,
        lightMin = sleep?.stages?.lightMin,
        awakeMin = sleep?.stages?.awakeMin,
        disturbances = sleep?.disturbances,
        efficiency = sleep?.efficiency,
        consistency = sleep?.consistency,
        debtHours = state.metrics.sleepDebt?.value?.takeIf { state.metrics.dayKey == today },
        restingHr = recovery?.restingHr,
        restingHrCorridor = c?.restingHr,
        restingHr7 = c?.restingHr7,
        hrv = recovery?.hrvMs,
        hrvCorridor = c?.hrv,
        respiratoryRate = sleep?.respiratoryRate,
        respiratoryCorridor = c?.respiratoryRate,
        spo2 = recovery?.spo2,
        spo2Corridor = c?.spo2,
        skinTemp = recovery?.skinTempC,
        skinTempCorridor = c?.skinTemp,
        self = day?.self,
        // Заряд 5–95 on the 1–5 scale of the deck, for «ты ↔ WHOOP»
        dataLevel = charge?.let { ((it - 5.0) / 18.0).toInt() + 1 }?.coerceIn(1, 5),
    )
}

@Composable
fun WhyRoute(stateful: Boolean, onAsk: () -> Unit) {
    if (!stateful) {
        WhyScreen(WhyBuilder.build(WhyInput()), onAsk)
        return
    }
    val today: TodayViewModel = hiltViewModel()
    val dayModel: DayViewModel = hiltViewModel()
    val state by today.state.collectAsStateWithLifecycle()
    val day by dayModel.state.collectAsStateWithLifecycle()
    WhyScreen(WhyBuilder.build(whyInput(state, day.takeIf { it.loaded })), onAsk)
}

/**
 * The sections of A46 §3: Итог, Сон, Сердце и сосуды, Дыхание, Отклонения, Ты ↔ WHOOP. Each row
 * has its source (T-11); the arrow says only where the value stands against the person's own
 * corridor — no diagnosis, no colour of alarm.
 */
@Composable
fun WhyScreen(sections: List<WhySection>, onAsk: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 4.dp, bottom = HotSpacing.sectionGap)
            .testTag(WHY_SCREEN_TAG),
        verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
    ) {
        sections.forEach { section -> WhyCard(section) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HotSecondaryButton(TodayCopy.WHY_DISAGREE, onClick = onAsk, modifier = Modifier.weight(1f))
            HotSecondaryButton(TodayCopy.WHY_ASK, onClick = onAsk, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun WhyCard(section: WhySection) {
    HotCard(modifier = Modifier.fillMaxWidth().testTag(WHY_SECTION_TAG + section.id.name.lowercase())) {
        Text(
            section.id.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        if (section.id == WhySectionId.SUMMARY && section.metrics.isEmpty()) {
            Text(TodayCopy.NO_DATA, style = MaterialTheme.typography.bodyMedium, color = HotThemeTokens.colors.textTertiary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            section.metrics.forEach { WhyRow(it) }
        }
        section.text?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WhyRow(m: WhyMetric) {
    val tertiary = HotThemeTokens.colors.textTertiary
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(
                listOfNotNull(m.value ?: TodayCopy.NO_DATA, m.arrow?.glyph).joinToString(" "),
                style = MaterialTheme.typography.bodyLarge,
                color = if (m.value == null) tertiary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HotOverline(listOfNotNull(m.corridor, m.source, TodayCopy.ESTIMATE.takeIf { m.estimate }).joinToString(" · "))
    }
}
