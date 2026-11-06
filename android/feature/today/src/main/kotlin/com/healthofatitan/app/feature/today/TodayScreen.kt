package com.healthofatitan.app.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
import com.healthofatitan.app.core.ui.component.HotNormRing
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotRatioBar
import com.healthofatitan.app.core.ui.component.HotSourceChip
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotStatTile
import com.healthofatitan.app.core.ui.component.HotSyncMark
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.component.SyncMarkPhase
import com.healthofatitan.app.core.ui.component.SyncMarkState
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.integration.whoop.SyncIndicator
import com.healthofatitan.app.integration.whoop.SyncIndicators
import com.healthofatitan.app.integration.whoop.SyncLight
import com.healthofatitan.app.integration.whoop.SyncProgress
import com.healthofatitan.app.integration.whoop.SyncTrigger
import com.healthofatitan.app.integration.whoop.TodayMetrics
import com.healthofatitan.app.integration.whoop.TodayMetricsRepository
import com.healthofatitan.app.integration.whoop.WhoopConnection
import com.healthofatitan.app.integration.whoop.WhoopGlance
import com.healthofatitan.app.integration.whoop.WhoopStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Tab «Сегодня» (A11 §1.1). Slice 1: what WHOOP brought, from the first sync on — last night's
 * sleep against the need, recovery inputs, the last workout — and, once the norm is learned,
 * Заряд and Весы. While the norm is calibrating a ring «9/14» stands in for any explanation.
 * Pull down to refresh: the HoT mark is the indicator.
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
const val TODAY_SLEEP_TAG = "today_sleep"

data class TodayUiState(
    val whoop: WhoopStatus? = null,
    val metrics: TodayMetrics = TodayMetrics.EMPTY,
    val progress: SyncProgress = SyncProgress.Idle,
    /** Raw WHOOP glance, memory only (R-11). */
    val glance: WhoopGlance? = null,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val connection: WhoopConnection,
    metrics: TodayMetricsRepository,
) : ViewModel() {
    val state: StateFlow<TodayUiState> =
        combine(connection.status, metrics.latest, connection.progress, connection.glance) { s, m, p, g -> TodayUiState(s, m, p, g) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun sync() {
        viewModelScope.launch { connection.syncNow(SyncTrigger.MANUAL) }
    }
}

@Composable
private fun TodayRoute(onConnectWhoop: () -> Unit, viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayScreen(state, onConnectWhoop, onSync = viewModel::sync)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(state: TodayUiState, onConnectWhoop: () -> Unit, modifier: Modifier = Modifier, onSync: () -> Unit = {}) {
    val whoop = state.whoop
    val connected = whoop != null && whoop.connected
    val refreshing = connected && state.progress is SyncProgress.Running
    val pull = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { if (connected) onSync() },
        state = pull,
        modifier = modifier.fillMaxSize(),
        indicator = { if (connected) PullIndicator(pull, refreshing, state.progress) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 4.dp, bottom = HotSpacing.sectionGap)
                .testTag(TODAY_SCREEN_TAG),
            verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
        ) {
            if (!connected) {
                NotConnectedCard(onConnectWhoop)
            } else {
                StateCard(whoop!!, state, onSync)
                Tiles(state)
            }
        }
    }
}

/** Pull-to-refresh: the ring of the mark draws with the finger, then the pulse runs while syncing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.PullIndicator(pull: PullToRefreshState, refreshing: Boolean, progress: SyncProgress) {
    val fraction = pull.distanceFraction
    if (!refreshing && fraction <= 0f) return
    val mark = if (refreshing) {
        SyncMarkState(SyncMarkPhase.RUNNING, (progress as? SyncProgress.Running)?.step?.fraction, (progress as? SyncProgress.Running)?.startedAt ?: 0L)
    } else {
        SyncMarkState(SyncMarkPhase.RUNNING, fraction.coerceIn(0f, 1f), tracking = true)
    }
    HotSyncMark(
        state = mark,
        size = 40.dp,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .graphicsLayer {
                val f = if (refreshing) 1f else pull.distanceFraction.coerceIn(0f, 1.4f)
                translationY = f * 56.dp.toPx() - 44.dp.toPx()
                alpha = (f * 1.6f).coerceIn(0f, 1f)
            },
    )
}

@Composable
private fun NotConnectedCard(onConnectWhoop: () -> Unit) {
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth()) {
        HotSyncMark(SyncMarkState.Idle, size = 88.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
        Text(TodayTexts.LEAD_NOT_CONNECTED, style = HotType.quote, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(18.dp))
        HotPrimaryButton(
            text = TodayTexts.CONNECT,
            onClick = onConnectWhoop,
            modifier = Modifier.fillMaxWidth().testTag(TODAY_CONNECT_TAG),
        )
    }
}

/** Live indicator for the status line; re-evaluated when a fresh success goes stale. */
@Composable
private fun rememberSyncIndicator(progress: SyncProgress): SyncIndicator {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(progress) {
        now = System.currentTimeMillis()
        val next = SyncIndicators.nextChangeAt(progress, now) ?: return@LaunchedEffect
        delay(next - now + 50)
        now = System.currentTimeMillis()
    }
    return SyncIndicators.of(progress, now)
}

@Composable
private fun StateCard(whoop: WhoopStatus, state: TodayUiState, onSync: () -> Unit) {
    val metrics = state.metrics
    val normReady = whoop.baselineNights >= TodayTexts.NORM_NIGHTS && metrics.dayKey != null
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth().testTag(TODAY_STATE_TAG)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HotOverline(TodayTexts.STATE_TITLE, modifier = Modifier.weight(1f))
            HotSourceChip("WHOOP", R.drawable.core_ui_ic_band)
        }
        Spacer(Modifier.height(14.dp))
        if (normReady) {
            Ready(metrics)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HotNormRing(whoop.baselineNights.coerceAtMost(TodayTexts.NORM_NIGHTS), TodayTexts.NORM_NIGHTS, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Text(TodayTexts.NORM, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        StatusLine(whoop, state.progress, onSync)
    }
}

@Composable
private fun StatusLine(whoop: WhoopStatus, progress: SyncProgress, onSync: () -> Unit) {
    val indicator = rememberSyncIndicator(progress)
    val failed = progress as? SyncProgress.Failed
    val text = when (indicator.light) {
        SyncLight.RUNNING -> TodayTexts.LOADING
        SyncLight.SUCCESS -> (progress as? SyncProgress.Succeeded)?.counts?.let { TodayTexts.done(it.nights, it.workouts) }
        SyncLight.ERROR -> failed?.let { TodayTexts.failure(it.failure, it.httpCode) }
        SyncLight.IDLE -> if (progress is SyncProgress.Idle) TodayTexts.problem(whoop.syncStatus) else null
    } ?: return
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (indicator.light == SyncLight.SUCCESS) MaterialTheme.colorScheme.primary else HotThemeTokens.colors.textTertiary,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (indicator.light == SyncLight.ERROR && failed?.willRetry == false) {
            HotTextButton(TodayTexts.RETRY, onClick = onSync)
        }
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

/** Four facts of the morning as tiles: sleep, recovery, workout, steps. Numbers, not sentences. */
@Composable
private fun Tiles(state: TodayUiState) {
    val glance = state.glance
    val metrics = state.metrics
    val sleepHours = glance?.sleep?.hours ?: metrics.sleepHours?.value
    val need = glance?.sleep?.needHours
    val recovery = glance?.recovery
    val workout = glance?.workout
    val orsLevel = metrics.recovery?.level
    val sleepRatio: (@Composable () -> Unit)? = if (sleepHours != null && need != null && need > 0) {
        { HotRatioBar((sleepHours / need).toFloat()) }
    } else {
        null
    }

    TileRow {
        HotStatTile(
            label = TodayTexts.SLEEP,
            value = sleepHours?.let(TodayTexts::clock),
            caption = TodayTexts.sleepCaption(need, metrics.sleepDebt?.value),
            pendingCaption = TodayTexts.NO_DATA,
            footer = sleepRatio,
            modifier = Modifier.weight(1f).fillMaxHeight().testTag(TODAY_SLEEP_TAG),
        )
        HotStatTile(
            label = TodayTexts.RECOVERY,
            value = recovery?.hrvMs?.let(TodayTexts::hrv) ?: TodayTexts.levelShort(orsLevel),
            caption = listOfNotNull(
                TodayTexts.levelShort(orsLevel).takeIf { recovery?.hrvMs != null },
                TodayTexts.HRV_LABEL.takeIf { recovery?.hrvMs != null },
                recovery?.restingHr?.let(TodayTexts::restingHr),
            ).joinToString(" · ").ifEmpty { null },
            pendingCaption = TodayTexts.NO_DATA,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
    TileRow {
        HotStatTile(
            label = TodayTexts.WORKOUT,
            value = workout?.let { TodayTexts.duration(it.minutes / 60.0) },
            caption = workout?.let { TodayTexts.workoutCaption(it.sport, it.start) },
            pendingCaption = TodayTexts.NO_DATA,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        HotStatTile(
            label = TodayTexts.STEPS,
            value = null,
            pendingCaption = TodayTexts.STEPS_PENDING,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun TileRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
        content = content,
    )
}
