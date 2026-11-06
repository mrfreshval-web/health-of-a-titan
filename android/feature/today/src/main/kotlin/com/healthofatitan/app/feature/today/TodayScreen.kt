package com.healthofatitan.app.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
import com.healthofatitan.app.core.ui.component.HotPendingCard
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSourceChip
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.integration.whoop.TodayMetrics
import com.healthofatitan.app.integration.whoop.TodayMetricsRepository
import com.healthofatitan.app.integration.whoop.WhoopConnection
import com.healthofatitan.app.integration.whoop.WhoopStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Tab «Сегодня» (A11 §1.1). Slice 1: the state card from WHOOP (Заряд, Весы, сон) once the norm
 * is learned; the rest of the day map stays «нет данных» until its slice.
 *
 * @param stateful false renders the not-connected state without a ViewModel (navigation tests).
 */
fun NavGraphBuilder.todayScreen(onConnectWhoop: () -> Unit, stateful: Boolean = true) {
    composable(route = HotRoute.TODAY) {
        if (stateful) TodayRoute(onConnectWhoop) else TodayScreen(TodayUiState(), onConnectWhoop)
    }
}

const val TODAY_SCREEN_TAG = "screen_today"
const val TODAY_CONNECT_TAG = "today_connect_whoop"
const val TODAY_STATE_TAG = "today_state_card"

data class TodayUiState(
    val whoop: WhoopStatus? = null,
    val metrics: TodayMetrics = TodayMetrics.EMPTY,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    connection: WhoopConnection,
    metrics: TodayMetricsRepository,
) : ViewModel() {
    val state: StateFlow<TodayUiState> = combine(connection.status, metrics.latest) { s, m -> TodayUiState(s, m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())
}

@Composable
private fun TodayRoute(onConnectWhoop: () -> Unit, viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayScreen(state, onConnectWhoop)
}

private val PENDING_BLOCKS = listOf("Утро · 1 мин", "Тренировка дня", "Тарелка дня", "Работа", "Стек · утро", "Монограмма дня")

@Composable
fun TodayScreen(state: TodayUiState, onConnectWhoop: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 4.dp, bottom = HotSpacing.sectionGap)
            .testTag(TODAY_SCREEN_TAG),
        verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
    ) {
        val whoop = state.whoop
        when {
            whoop == null || !whoop.connected -> NotConnectedCard(onConnectWhoop)
            else -> StateCard(whoop, state.metrics)
        }
        PENDING_BLOCKS.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(HotSpacing.cardGap)) {
                row.forEach {
                    HotPendingCard(title = it, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 88.dp))
                }
            }
        }
    }
}

@Composable
private fun NotConnectedCard(onConnectWhoop: () -> Unit) {
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth()) {
        Text(TodayTexts.LEAD_NOT_CONNECTED, style = HotType.quote, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(18.dp))
        HotPrimaryButton(
            text = TodayTexts.CONNECT,
            onClick = onConnectWhoop,
            modifier = Modifier.fillMaxWidth().testTag(TODAY_CONNECT_TAG),
        )
    }
}

@Composable
private fun StateCard(whoop: WhoopStatus, metrics: TodayMetrics) {
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth().testTag(TODAY_STATE_TAG)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HotOverline(TodayTexts.STATE_TITLE, modifier = Modifier.weight(1f))
            HotSourceChip("WHOOP", R.drawable.core_ui_ic_band)
        }
        Spacer(Modifier.height(14.dp))
        val problem = TodayTexts.problem(whoop.syncStatus)
        when {
            metrics.dayKey == null -> Text(problem ?: TodayTexts.LOADING, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            whoop.baselineNights < TodayTexts.NORM_NIGHTS -> Learning(whoop.baselineNights, metrics)
            else -> Ready(metrics)
        }
        if (problem != null && metrics.dayKey != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                problem,
                style = MaterialTheme.typography.bodySmall,
                color = HotThemeTokens.colors.textTertiary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun Learning(nights: Int, metrics: TodayMetrics) {
    Text(TodayTexts.LEARNING, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(12.dp))
    LinearProgressIndicator(
        progress = { nights / TodayTexts.NORM_NIGHTS.toFloat() },
        color = MaterialTheme.colorScheme.primary,
        trackColor = HotThemeTokens.colors.ruleSoft,
        drawStopIndicator = {},
        modifier = Modifier.fillMaxWidth().height(4.dp),
    )
    Spacer(Modifier.height(8.dp))
    Text(TodayTexts.nights(nights), style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
    TodayTexts.sleepLine(metrics.sleepHours?.value, null)?.let {
        Spacer(Modifier.height(14.dp))
        Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Ready(metrics: TodayMetrics) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HotSpacing.cardGap)) {
        Facet(
            title = TodayTexts.CHARGE,
            word = TodayTexts.chargeWord(metrics.charge?.level),
            value = metrics.charge?.value?.let { "${it.roundToInt()} · ${TodayTexts.NORM_50}" },
            modifier = Modifier.weight(1f),
        )
        Facet(
            title = TodayTexts.BALANCE,
            word = TodayTexts.balanceWord(metrics.balance?.level),
            value = null,
            modifier = Modifier.weight(1f),
        )
    }
    TodayTexts.sleepLine(metrics.sleepHours?.value, metrics.sleepDebt?.value)?.let {
        Spacer(Modifier.height(14.dp))
        Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Facet(title: String, word: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text(word, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = HotThemeTokens.colors.textTertiary)
        }
    }
}
