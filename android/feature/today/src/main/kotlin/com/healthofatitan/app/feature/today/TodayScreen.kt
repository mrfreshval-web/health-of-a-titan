package com.healthofatitan.app.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotRatioBar
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotStatTile
import com.healthofatitan.app.core.ui.component.HotSyncMark
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.component.SyncMarkPhase
import com.healthofatitan.app.core.ui.component.SyncMarkState
import com.healthofatitan.app.core.ui.component.TitanScene
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
import com.healthofatitan.app.integration.whoop.day.StrainCeiling
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Tab «Сегодня» in «Титаниум» (A37 §1 scenario 2, s2). From top to bottom: the date, the hero
 * (the Titan inside the Заряд ring, lit by the day's mode, with the mode word and three data
 * tokens), «Почему так», «Сейчас» — the one next action, «Линия дня», the full plan, the
 * evening deck, and the WHOOP sync line. Before today's plan exists the four WHOOP tiles of
 * slice 1 stand in. Pull down to refresh: the short «Титан приходит» (0.7 s) is the indicator.
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
private fun TodayRoute(
    onConnectWhoop: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
    dayModel: DayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val day by dayModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(day.plan?.dayKey, day.today) { dayModel.opened() }
    TodayScreen(
        state = state,
        onConnectWhoop = onConnectWhoop,
        onSync = viewModel::sync,
        day = day.takeIf { it.loaded },
        actions = DayActions(onToggle = dayModel::toggle, onEvening = dayModel::saveEvening),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    onConnectWhoop: () -> Unit,
    modifier: Modifier = Modifier,
    onSync: () -> Unit = {},
    /** Slice 2: the day (regime, plan, evening); null while it loads. */
    day: DayUiState? = null,
    actions: DayActions = DayActions(),
) {
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
                NotConnected(onConnectWhoop)
            } else {
                Connected(whoop!!, state, day, actions, onSync)
            }
        }
    }
}

@Composable
private fun ColumnScope.Connected(whoop: WhoopStatus, state: TodayUiState, day: DayUiState?, actions: DayActions, onSync: () -> Unit) {
    val metrics = state.metrics
    val glance = state.glance
    val today = day?.today ?: LocalDate.now()
    val plan = day?.plan?.takeIf { day.planToday }
    val mode = plan?.regime?.mode()
    val nowMin = day?.now?.let { it.hour * 60 + it.minute } ?: 0
    val normReady = whoop.baselineNights >= TodayTexts.NORM_NIGHTS && metrics.dayKey != null
    val sleep = glance?.sleep?.hours ?: metrics.sleepHours?.value
    val tokens = listOfNotNull(
        sleep?.let { "${TitanTexts.SLEEP} ${TitanTexts.hm(it)}" },
        glance?.recovery?.restingHr?.let { "${TitanTexts.PULSE} ${it.roundToInt()}" },
        plan?.let { "${TitanTexts.CEILING} ${TitanTexts.ceiling(StrainCeiling.of(it.regime))}" },
    )

    HotOverline(TitanTexts.date(today), modifier = Modifier.align(Alignment.CenterHorizontally))
    // A39 §7 order: safety → mode → «Почему так» → plan → evening
    plan?.let { SafetyCard(it) }
    TodayHero(
        mode = mode,
        charge = metrics.charge?.value?.roundToInt()?.takeIf { normReady },
        normNights = whoop.baselineNights.coerceAtMost(TodayTexts.NORM_NIGHTS).takeIf { !normReady },
        tokens = tokens,
        summary = plan?.summary,
        flags = plan?.flags.orEmpty(),
        modifier = Modifier.testTag(TODAY_STATE_TAG),
    )
    if (plan != null) {
        RegimeWhy(plan)
        NotificationAsk()
        nowItem(plan.items, nowMin)?.let { NowCard(plan, it, actions.onToggle, mode!!) }
        if (plan.items.any { it.startMin != null }) {
            HotOverline(TitanTexts.DAY_LINE)
            DayLine(plan, nowMin, mode!!)
        }
        PlanCard(plan, actions.onToggle)
        SealOverlay(plan, glance)
    } else {
        if (day != null) DayPending()
        Tiles(state)
    }
    val eveningDay = day?.eveningFor
    when {
        eveningDay != null -> EveningDeckCard(eveningDay, actions.onEvening)
        day?.eveningJustSaved == true -> EveningSaved()
    }
    SyncFooter(whoop, state, onSync)
}

/** Not connected: the Titan alone in the dark, one line and the way to WHOOP (A37 s1). */
@Composable
private fun NotConnected(onConnectWhoop: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        TitanScene(
            modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp).aspectRatio(1f),
            ring = { 1f },
            ringDashed = true,
            ringColor = HotThemeTokens.colors.rule,
            ringStroke = 6f,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            TodayTexts.LEAD_NOT_CONNECTED,
            style = HotType.quote,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        HotPrimaryButton(
            text = TodayTexts.CONNECT,
            onClick = onConnectWhoop,
            trailingArrow = true,
            modifier = Modifier.fillMaxWidth().testTag(TODAY_CONNECT_TAG),
        )
    }
}

/** Pull-to-refresh: the ring draws with the finger, then the short «Титан приходит» runs (0.7 s). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.PullIndicator(pull: PullToRefreshState, refreshing: Boolean, progress: SyncProgress) {
    val fraction = pull.distanceFraction
    if (!refreshing && fraction <= 0f) return
    val running = progress as? SyncProgress.Running
    val mark = if (refreshing) {
        SyncMarkState(SyncMarkPhase.RUNNING, running?.step?.fraction, running?.startedAt ?: 0L, quick = true)
    } else {
        SyncMarkState(SyncMarkPhase.RUNNING, fraction.coerceIn(0f, 1f), tracking = true, quick = true)
    }
    HotSyncMark(
        state = mark,
        size = 44.dp,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .graphicsLayer {
                val f = if (refreshing) 1f else pull.distanceFraction.coerceIn(0f, 1.4f)
                translationY = f * 56.dp.toPx() - 44.dp.toPx()
                alpha = (f * 1.6f).coerceIn(0f, 1f)
            },
    )
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

/** «WHOOP · 06:41» at the end of the day, with the sync status and «Повторить» on a final failure. */
@Composable
private fun SyncFooter(whoop: WhoopStatus, state: TodayUiState, onSync: () -> Unit) {
    val progress = state.progress
    val indicator = rememberSyncIndicator(progress)
    val failed = progress as? SyncProgress.Failed
    val text = when (indicator.light) {
        SyncLight.RUNNING -> TodayTexts.LOADING
        SyncLight.SUCCESS -> (progress as? SyncProgress.Succeeded)?.counts?.let { TodayTexts.done(it.nights, it.workouts) }
        SyncLight.ERROR -> failed?.let { TodayTexts.failure(it.failure, it.httpCode) }
        SyncLight.IDLE -> if (progress is SyncProgress.Idle) TodayTexts.problem(whoop.syncStatus) else null
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HotOverline(TitanTexts.synced(state.glance?.fetchedAt?.takeIf { it > 0 }))
        if (text != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (indicator.light == SyncLight.SUCCESS) MaterialTheme.colorScheme.primary else HotThemeTokens.colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (indicator.light == SyncLight.ERROR && failed?.willRetry == false) {
            HotTextButton(TodayTexts.RETRY, onClick = onSync)
        }
    }
}

/** Four facts of the morning as tiles: sleep, recovery, workout, steps. Shown until today's plan exists. */
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
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
        content = content,
    )
}
