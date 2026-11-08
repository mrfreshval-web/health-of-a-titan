package com.healthofatitan.app.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotSyncMark
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.component.SyncMarkPhase
import com.healthofatitan.app.core.ui.component.SyncMarkState
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.domain.engine.DayEngine
import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.today.DayFacts
import com.healthofatitan.app.domain.today.DayLight
import com.healthofatitan.app.domain.today.DayTask
import com.healthofatitan.app.domain.today.MeInput
import com.healthofatitan.app.domain.today.MeRules
import com.healthofatitan.app.domain.today.MeView
import com.healthofatitan.app.domain.today.Ritual
import com.healthofatitan.app.domain.today.RhythmInput
import com.healthofatitan.app.domain.today.RhythmRules
import com.healthofatitan.app.domain.today.RhythmView
import com.healthofatitan.app.domain.today.Scenario
import com.healthofatitan.app.domain.today.Scenarios
import com.healthofatitan.app.domain.today.SleepPrep
import com.healthofatitan.app.domain.today.SupportNorms
import com.healthofatitan.app.domain.today.SupportView
import com.healthofatitan.app.domain.today.Supports
import com.healthofatitan.app.domain.today.SupportsInput
import com.healthofatitan.app.domain.today.TaskRules
import com.healthofatitan.app.domain.today.TodayBlock
import com.healthofatitan.app.domain.today.TodayCopy
import com.healthofatitan.app.domain.today.TodayFood
import com.healthofatitan.app.domain.today.TodayLayout
import com.healthofatitan.app.integration.healthconnect.PhoneSteps
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
import com.healthofatitan.app.integration.whoop.day.DayView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Tab «Сегодня» v3 (A46 v0.3, D-042): «Как я сегодня, что держится и что нужно от меня?». Four
 * blocks in a fixed order (T-01) — «Я сегодня», «Опоры», «Дела дня», «Ритм дня» — under the header
 * (Винс · date · profile). Every block opens its details; nothing opens by itself (T-05). The
 * graphics are placeholders until Agents 4 and 17 («Колоннада», TitanLottie).
 *
 * @param onOpenHelp the crisis card (SOS): CrisisGuard ≥ 2 in the evening note opens it.
 * @param stateful false renders the not-connected state without a ViewModel (navigation tests).
 * @param stateCards the state cards of A44 (morning deck, reveal, discovery), shown in «Ритм дня».
 * @param onOpenRoute every tap that leaves the screen: «Почему так», a support, a task, a ritual.
 * @param onRitual a tap on a ritual point: :app knows which step of «Рассвет» is due.
 */
fun NavGraphBuilder.todayScreen(
    onConnectWhoop: () -> Unit,
    onOpenHelp: () -> Unit = {},
    stateful: Boolean = true,
    stateCards: @Composable () -> Unit = {},
    closeDay: CloseDayExtras = CloseDayExtras(),
    onOpenRoute: (String) -> Unit = {},
    onRitual: (Ritual) -> Unit = {},
    onBack: () -> Unit = {},
    onAskMentor: () -> Unit = {},
) {
    val slots = TodaySlots(top = { stateCards() }, closeDay = closeDay)
    composable(route = HotRoute.TODAY) {
        if (stateful) {
            TodayRoute(onConnectWhoop, onOpenHelp, slots, onOpenRoute, onRitual)
        } else {
            TodayScreen(
                TodayUiState(),
                onConnectWhoop,
                slots = slots,
                actions = DayActions(onOpenHelp = onOpenHelp, onOpen = onOpenRoute, onRitual = onRitual, onConnectWhoop = onConnectWhoop),
            )
        }
    }
    composable(route = HotRoute.WHY) { WhyRoute(stateful = stateful, onAsk = onAskMentor) }
    composable(
        route = HotRoute.SUPPORT,
        arguments = listOf(navArgument(HotRoute.SUPPORT_ARG) { type = NavType.StringType }),
    ) { entry ->
        SupportRoute(id = entry.arguments?.getString(HotRoute.SUPPORT_ARG), stateful = stateful, onOpen = onOpenRoute)
    }
    composable(route = HotRoute.BREATHER) { BreatherScreen(onHelp = onOpenHelp, onClose = onBack) }
    composable(route = HotRoute.ARSENAL) { ArsenalScreen() }
}

/**
 * Places for what other modules put on «Сегодня» (A46 v0.3): the state cards of A44 inside «Ритм
 * дня» and the additions of the evening inside «Закрыть день». Nothing else is added between the
 * blocks (T-01).
 */
class TodaySlots(
    /** State cards (A44): the morning deck, the reveal, a discovery — at the end of «Ритм дня». */
    val top: @Composable ColumnScope.() -> Unit = {},
    /** Rows added inside «Закрыть день» by another module (A44 evening). */
    val closeDay: CloseDayExtras = CloseDayExtras(),
)

const val TODAY_SCREEN_TAG = "screen_today"
const val TODAY_CONNECT_TAG = "today_connect_whoop"

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
    onOpenHelp: () -> Unit,
    slots: TodaySlots,
    onOpenRoute: (String) -> Unit,
    onRitual: (Ritual) -> Unit,
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
        actions = DayActions(
            onToggle = dayModel::toggle,
            onEvening = dayModel::saveEvening,
            onCloseDay = dayModel::closeDay,
            onCrisis = { a ->
                dayModel.crisis(a)
                if (a.level.autoShowHelp) onOpenHelp()
            },
            onOpenHelp = onOpenHelp,
            onSteps = dayModel::refreshSteps,
            onOpen = onOpenRoute,
            onWater = dayModel::water,
            onTaskDone = dayModel::taskDone,
            onTaskToWeek = dayModel::toWeek,
            onTaskBack = dayModel::backFromWeek,
            onRitual = onRitual,
            onConnectWhoop = onConnectWhoop,
            onStartSmall = dayModel::welcomeSeen,
        ),
        slots = slots,
    )
}

/** «Сегодня» v3 as one pure view of the state (A46 §1–§2): what each block shows. */
data class TodayView(
    val scenario: Scenario,
    val light: DayLight,
    val me: MeView,
    val supports: List<SupportView>,
    val tasks: List<DayTask>,
    val rhythm: RhythmView,
    val blocks: List<TodayBlock>,
    /** The food plan of today (S3); empty without a plan. */
    val food: TodayFood = TodayFood(),
    /** The compact «Тарелка дня» line under the supports; null without a plan or under `HIDE_FOOD_TALK`. */
    val plate: PlateLine? = null,
)

/** «Тарелка дня · Обед 13:00»: the next main meal and the card it opens (A41 §10, S3). */
data class PlateLine(val text: String, val route: String)

/**
 * The compact plate line: the next main meal opens its card; with every meal marked the whole
 * plate opens. No line without a plan, under `HIDE_FOOD_TALK` or on the sober screen (A46 §4.8);
 * no clock time under `HIDE_FOOD_WINDOWS`.
 */
internal fun plateLine(food: TodayFood, scenario: Scenario, nowMin: Int, marked: Set<String>): PlateLine? {
    if (food.quiet || food.mealsPlanned == null || scenario == Scenario.SAFETY) return null
    val next = food.next(nowMin, marked) ?: return PlateLine(TodayCopy.PLATE_OF_DAY, HotRoute.NUTRITION_DAY)
    val time = next.timeMin?.takeIf { !food.hideTimes }?.let { " " + Supports.clock(it) }.orEmpty()
    return PlateLine("${TodayCopy.PLATE_OF_DAY} · ${next.title}$time", HotRoute.nutritionMeal(next.slot))
}

/**
 * The sleep preparation of today's plan (A46 §2.3): the dinner window is the plan's dinner (NUT-01)
 * and is left out under `HIDE_FOOD_WINDOWS` (A60 §3.4). Null without a plan.
 */
internal fun sleepPrep(plan: DayView?, food: TodayFood): SleepPrep? = plan?.let {
    val dinner = it.items.firstOrNull { i -> i.id == DayEngine.Ids.DINNER }?.let { d -> d.endMin ?: d.startMin }
    SleepPrep(
        caffeineUntilMin = it.caffeineCutoffMin,
        dinnerUntilMin = dinner ?: SupportNorms.DINNER_UNTIL_MIN,
        screensFromMin = it.windDownMin,
        bedtimeMin = it.bedtimeMin,
        foodWindows = !food.hideTimes,
    )
}

/** A workout counts as «just confirmed» for scenario 4.5 for three hours after its end. */
private val AFTER_WORKOUT: Duration = Duration.ofHours(3)

private fun minuteOf(instant: Instant, zone: ZoneId): Int = instant.atZone(zone).let { it.hour * 60 + it.minute }

/**
 * Maps the WHOOP state and the day to the four blocks. Pure, so the scenarios of A46 §4 are tested
 * without a screen. Raw WHOOP values come from the in-memory glance only.
 */
fun todayView(state: TodayUiState, day: DayUiState?, zone: ZoneId = ZoneId.systemDefault()): TodayView {
    val now = day?.now ?: LocalDateTime.now()
    val today = now.toLocalDate()
    val whoop = state.whoop
    val connected = whoop?.connected == true
    val plan: DayView? = day?.plan?.takeIf { day.planToday }
    val nightReady = plan != null && DayFlag.MANUAL !in plan.flags
    val mode = plan?.regime?.dayMode()
    val glance = state.glance
    val nowInstant = now.atZone(zone).toInstant()
    val workout = glance?.workout
    val workoutEnd = workout?.start?.plusSeconds(workout.minutes * 60L)
    val trainingItem = plan?.items?.firstOrNull { it.id == DayEngine.Ids.TRAINING }
    val workoutConfirmed = trainingItem?.status == ItemStatus.DONE_WHOOP && workoutEnd != null &&
        !workoutEnd.isAfter(nowInstant) && Duration.between(workoutEnd, nowInstant) <= AFTER_WORKOUT
    val facts = DayFacts(
        now = now,
        whoopConnected = connected,
        nightReady = nightReady,
        mode = mode,
        safety = day?.safety ?: com.healthofatitan.app.domain.today.Safety.NONE,
        returning = day?.returning == true,
        dawnDone = day?.dawnShown == true,
        workoutConfirmed = workoutConfirmed,
    )
    val scenario = Scenarios.of(facts)
    val sleepHours = glance?.sleep?.takeIf { it.wakeDay == today }?.hours ?: state.metrics.sleepHours?.value?.takeIf { state.metrics.dayKey == today }
    val me = MeRules.build(
        MeInput(
            scenario = scenario,
            mode = mode,
            haiku = day?.haiku,
            safety = facts.safety,
            charge = state.metrics.charge?.value?.roundToInt()?.takeIf { state.metrics.dayKey == today },
            nightMinutes = sleepHours?.let { (it * 60).roundToInt() },
            needMinutes = glance?.sleep?.takeIf { it.wakeDay == today }?.needHours?.let { (it * 60).roundToInt() },
            self = day?.self,
            baselineNights = whoop?.baselineNights ?: 0,
        ),
    )
    // the cycle that runs today (it may have started yesterday evening)
    val cycle = glance?.cycle?.takeIf { !it.start.atZone(zone).toLocalDate().isBefore(today.minusDays(1)) }
    val phone = day?.steps as? PhoneSteps.Ready
    val food = day?.food ?: TodayFood()
    val prep = sleepPrep(plan, food) ?: SleepPrep(foodWindows = !food.hideTimes)
    val marks = day?.marks
    val supports = Supports.build(
        SupportsInput(
            mode = mode,
            scenario = scenario,
            nowMin = now.hour * 60 + now.minute,
            strain = cycle?.strain ?: glance?.dayStrain?.takeIf { !it.cycleStart.atZone(zone).toLocalDate().isBefore(today.minusDays(1)) }?.strain,
            kilojoule = cycle?.kilojoule,
            whoopSteps = cycle?.steps,
            phoneSteps = phone?.today,
            hourlySteps = phone?.hourly,
            mealsMarked = food.marked(marks?.meals.orEmpty()),
            water = marks?.water ?: 0,
            sleep = prep,
            lastWorkoutEndMin = workoutEnd?.takeIf { it.atZone(zone).toLocalDate() == today }?.let { minuteOf(it, zone) },
            lastMealMin = marks?.lastMealAt?.let { minuteOf(Instant.ofEpochMilli(it), zone) },
            food = food,
        ),
    )
    val done = marks?.done.orEmpty()
    val candidates = day?.candidates.orEmpty().map { if (it.id in done) it.copy(done = true) else it }
    val tasks = TaskRules.pick(scenario, mode, candidates, marks?.toWeek.orEmpty())
        .map { if (it.id in done) it.copy(done = true) else it }
    val rhythm = RhythmRules.build(
        RhythmInput(
            now = now,
            scenario = scenario,
            settings = day?.rituals ?: com.healthofatitan.app.domain.today.RitualSettings(),
            dawnDone = day?.dawnShown == true,
            dayClosed = day?.eveningAnswered == today || (day?.eveningAnswered == today.minusDays(1) && now.hour < 4),
            sleep = if (plan != null) prep else null,
        ),
    )
    return TodayView(
        scenario = scenario,
        light = Scenarios.light(scenario, now),
        me = me,
        supports = supports,
        tasks = tasks,
        rhythm = rhythm,
        blocks = TodayLayout.blocks(scenario, now),
        food = food,
        plate = plateLine(food, scenario, now.hour * 60 + now.minute, marks?.meals.orEmpty()),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    onConnectWhoop: () -> Unit,
    modifier: Modifier = Modifier,
    onSync: () -> Unit = {},
    /** The day (regime, plan, marks, tasks); null while it loads. */
    day: DayUiState? = null,
    actions: DayActions = DayActions(),
    slots: TodaySlots = TodaySlots(),
) {
    val whoop = state.whoop
    val connected = whoop != null && whoop.connected
    val refreshing = connected && state.progress is SyncProgress.Running
    val pull = rememberPullToRefreshState()
    val marks = rememberDayMarks(actions.onToggle)
    val view = todayView(state, day)
    val withConnect = if (actions.onConnectWhoop === NO_CONNECT) actions.copyWith(onConnectWhoop = onConnectWhoop) else actions
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
                .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 0.dp, bottom = HotSpacing.sectionGap + 56.dp)
                .testTag(TODAY_SCREEN_TAG),
            verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
        ) {
            view.blocks.forEach { block ->
                when (block) {
                    TodayBlock.ME -> BlockMe(view, onOpen = { withConnect.onOpen(HotRoute.WHY) })
                    TodayBlock.SUPPORTS -> BlockSupports(view, day?.today ?: LocalDate.now(), withConnect)
                    TodayBlock.TASKS -> BlockTasks(view, day?.today ?: LocalDate.now(), withConnect, marks)
                    TodayBlock.RHYTHM -> BlockRhythm(view, day, withConnect, slots)
                }
            }
            if (whoop != null && connected) SyncFooter(whoop, state, onSync)
        }
        // «Отмечено · Отменить» for 5 s after a mark (A46 §2.4), over the content.
        UndoBar(marks, Modifier.align(Alignment.BottomCenter).padding(horizontal = HotSpacing.edge, vertical = 12.dp))
        day?.plan?.takeIf { day.planToday }?.let { SealOverlay(it, state.glance) }
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
        SyncLight.SUCCESS -> null
        SyncLight.ERROR -> failed?.let { TodayTexts.failure(it.failure, it.httpCode) }
        SyncLight.IDLE -> if (progress is SyncProgress.Idle) TodayTexts.problem(whoop.syncStatus) else null
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HotOverline(TitanTexts.syncedLine(state.glance?.fetchedAt?.takeIf { it > 0 } ?: whoop.lastSyncAt))
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
