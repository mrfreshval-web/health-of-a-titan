package com.healthofatitan.app.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthofatitan.app.core.database.entity.EveningCheckEntity
import com.healthofatitan.app.domain.engine.Body
import com.healthofatitan.app.domain.engine.CrisisAssessment
import com.healthofatitan.app.domain.engine.CrisisSource
import com.healthofatitan.app.domain.engine.DayClose
import com.healthofatitan.app.domain.engine.DayEngine
import com.healthofatitan.app.domain.engine.FoodClose
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Steps
import com.healthofatitan.app.domain.engine.TrainingClose
import com.healthofatitan.app.domain.engine.WorkoutMatcher
import com.healthofatitan.app.domain.today.DayTask
import com.healthofatitan.app.domain.today.RitualSettings
import com.healthofatitan.app.domain.today.Safety
import com.healthofatitan.app.domain.today.TaskMove
import com.healthofatitan.app.domain.today.TodayFood
import com.healthofatitan.app.domain.today.WeekPlan
import com.healthofatitan.app.integration.healthconnect.PhoneSteps
import com.healthofatitan.app.integration.healthconnect.StepsRepository
import com.healthofatitan.app.integration.healthconnect.WalkWindow
import com.healthofatitan.app.integration.whoop.day.DailyLoopRepository
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import com.healthofatitan.app.integration.whoop.day.DayView
import com.healthofatitan.app.integration.whoop.day.MorningCoordinator
import com.healthofatitan.app.integration.whoop.day.TodayMarks
import com.healthofatitan.app.integration.whoop.day.TodayFoodSource
import com.healthofatitan.app.integration.whoop.day.TodayMarksRepository
import com.healthofatitan.app.integration.whoop.day.TodayMorning
import com.healthofatitan.app.integration.whoop.day.TodaySafetySource
import com.healthofatitan.app.integration.whoop.day.WeekPlanSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/** What the day block shows: the latest stored plan, the time, the evening and the phone's steps. */
data class DayUiState(
    val plan: DayView? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    /** The day whose evening deck is answered (or skipped) — the latest one stored. */
    val eveningAnswered: LocalDate? = null,
    /** Just answered in this session: show «Принято» instead of the deck. */
    val eveningJustSaved: Boolean = false,
    val loaded: Boolean = false,
    /** The answers of the latest closed day («не сегодня» changes the closing line). */
    val closed: DayClose? = null,
    /** Phone steps from Health Connect (D-024), memory only. */
    val steps: PhoneSteps = PhoneSteps.Unavailable,
    /** Water, meals and task marks of today (A46 §2.3, §2.4). */
    val marks: TodayMarks = TodayMarks.EMPTY,
    /** What the week offers for today (A52 §4). */
    val candidates: List<DayTask> = emptyList(),
    val week: WeekPlan? = null,
    /** What overrides the day (A46 §4.8). */
    val safety: Safety = Safety.NONE,
    /** «ты» of «Рассвет», 1–5. */
    val self: Int? = null,
    /** The haiku shown at «Рассвет» today (T-07). */
    val haiku: List<String>? = null,
    val dawnShown: Boolean = false,
    /** «С возвращением» is due (A18 И4.4). */
    val returning: Boolean = false,
    val rituals: RitualSettings = RitualSettings(),
    /** The food plan of today and what the safety bus hides (audit 2026-11-08 S3). */
    val food: TodayFood = TodayFood(),
) {
    val today: LocalDate get() = now.toLocalDate()

    /** Today's plan exists (the night is scored). */
    val planToday: Boolean get() = plan?.dayKey == today

    /** The evening window of the plan's day: after 18:00 that day, or before 05:00 the next morning. */
    private val inEvening: Boolean
        get() {
            val p = plan ?: return false
            return (p.dayKey == today && now.hour >= EVENING_FROM_HOUR) ||
                (p.dayKey == today.minusDays(1) && now.hour < EVENING_UNTIL_HOUR)
        }

    /** «Закрыть день» (A18 И4.1, D-027: one tap each) for the plan's day, once per day. */
    val eveningFor: LocalDate?
        get() = plan?.dayKey?.takeIf { inEvening && eveningAnswered != it }

    /** The day is closed and it is still its evening: the light card instead of the questions. */
    val closedFor: LocalDate?
        get() = plan?.dayKey?.takeIf { inEvening && eveningAnswered == it }

    companion object {
        const val EVENING_FROM_HOUR = 18
        const val EVENING_UNTIL_HOUR = 5
    }
}

/** Stored answers → the engine's [DayClose]; unknown names read as «не отмечено». */
internal fun EveningCheckEntity.toClose(): DayClose = DayClose(
    training = training?.let { t -> TrainingClose.entries.firstOrNull { it.name == t } },
    food = food?.let { f -> FoodClose.entries.firstOrNull { it.name == f } },
    body = body?.let { b -> Body.entries.firstOrNull { it.name == b } },
    alcohol = alcohol,
    planHelped = planHelped,
)

/**
 * The planned walk's window for phone steps (A35 §4): the slot ±3 h like WHOOP's match
 * ([WorkoutMatcher.SLOT_WINDOW_MIN]); null when today's training is not a walk.
 */
internal fun walkWindow(plan: DayView?, zone: ZoneId): WalkWindow? {
    val item = plan?.items?.firstOrNull { it.id == DayEngine.Ids.TRAINING } ?: return null
    if (!Steps.isWalk(item.rule)) return null
    val start = item.startMin ?: return null
    val end = item.endMin ?: return null
    val midnight = plan.dayKey.atStartOfDay(zone)
    val slack = WorkoutMatcher.SLOT_WINDOW_MIN.toLong()
    return WalkWindow(
        from = midnight.plusMinutes(start - slack).toInstant(),
        to = midnight.plusMinutes(end + slack).toInstant(),
        minutes = end - start,
    )
}

/** Everything of «Сегодня» v3 that belongs to one calendar day. */
private data class DayExtras(
    val marks: TodayMarks = TodayMarks.EMPTY,
    val candidates: List<DayTask> = emptyList(),
    val week: WeekPlan? = null,
    val safety: Safety = Safety.NONE,
    val haiku: List<String>? = null,
    val dawnShown: Boolean = false,
    val self: Int? = null,
    val food: TodayFood = TodayFood(),
)

/** A source that fails or has not answered yet reads as its fallback: the screen never waits on it. */
private fun <T> Flow<T>.orElse(fallback: T): Flow<T> = onStart { emit(fallback) }.catch { emit(fallback) }

@HiltViewModel
class DayViewModel @Inject constructor(
    private val plans: DayPlanRepository,
    private val morning: MorningCoordinator,
    private val loop: DailyLoopRepository,
    private val steps: StepsRepository,
    private val marks: TodayMarksRepository,
    private val weekPlan: WeekPlanSource,
    private val safety: TodaySafetySource,
    private val morningOf: TodayMorning,
    private val foodOf: TodayFoodSource,
) : ViewModel() {

    private val justSaved = MutableStateFlow(false)

    private val clock = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(TICK_MS)
        }
    }

    private val base: Flow<DayUiState> =
        combine(plans.latest, plans.latestEvening, clock, justSaved, steps.state) { plan, evening, now, saved, phone ->
            DayUiState(
                plan = plan,
                now = now,
                eveningAnswered = evening?.dayKey?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                eveningJustSaved = saved,
                loaded = true,
                closed = evening?.toClose(),
                steps = phone,
            )
        }

    /** The day's marks, tasks, safety and «Рассвет»; switches to the new day at midnight. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val extras: Flow<DayExtras> = clock.map { it.toLocalDate() }.distinctUntilChanged().flatMapLatest { d ->
        val first = combine(
            marks.observe(d).orElse(TodayMarks.EMPTY),
            weekPlan.candidates(d).orElse(emptyList()),
            weekPlan.week(d).map<WeekPlan, WeekPlan?> { it }.orElse(null),
            safety.safety(d).orElse(Safety.NONE),
            morningOf.haiku(d).orElse(null),
        ) { m, c, w, s, h -> DayExtras(marks = m, candidates = c, week = w, safety = s, haiku = h) }
        val second = combine(
            morningOf.dawnShown(d).orElse(false),
            flow { emit(morningOf.self(d)) }.orElse(null),
        ) { shown, self -> shown to self }
        combine(first, second, foodOf.food(d).orElse(TodayFood())) { e, s, food -> e.copy(dawnShown = s.first, self = s.second, food = food) }
    }

    val state: StateFlow<DayUiState> =
        combine(base, extras.orElse(DayExtras()), loop.welcomeDue().orElse(false)) { b, e, returning ->
            b.copy(
                marks = e.marks,
                candidates = e.candidates,
                week = e.week,
                safety = e.safety,
                self = e.self,
                haiku = e.haiku,
                dawnShown = e.dawnShown,
                returning = returning,
                food = e.food,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState())

    init {
        // Phone steps: re-read every few minutes while «Сегодня» lives (memory only, R-11).
        viewModelScope.launch {
            while (true) {
                refreshSteps()
                delay(STEPS_MS)
            }
        }
    }

    /** «Сегодня» is on screen: rebuild today's plan and count it as seen (no morning push then). */
    fun opened() {
        viewModelScope.launch { runCatching { morning.onTodayOpened() } }
        refreshSteps()
    }

    fun refreshSteps() {
        viewModelScope.launch { runCatching { steps.refresh(walkWindow(state.value.plan, ZoneId.systemDefault())) } }
    }

    fun toggle(day: LocalDate, itemId: String) {
        viewModelScope.launch { plans.toggle(day, itemId) }
    }

    /** A skipped card is null (A25 §2.1); skipping the deck stores both as null. */
    fun saveEvening(day: LocalDate, alcohol: Int?, body: Body?) {
        viewModelScope.launch {
            plans.saveEvening(day, alcohol, body)
            justSaved.value = true
        }
    }

    /** «Закрыть день» (A18 И4.1): the answers, and the training item lit by the answer. */
    fun closeDay(day: LocalDate, close: DayClose) {
        viewModelScope.launch {
            plans.closeDay(day, close)
            justSaved.value = true
        }
    }

    /** A07 §4.7: the level of the evening note is logged without the note. */
    fun crisis(assessment: CrisisAssessment) {
        viewModelScope.launch { runCatching { loop.logSafety(assessment, CrisisSource.EVENING) } }
    }

    fun welcomeSeen() {
        viewModelScope.launch { runCatching { loop.welcomeShown() } }
    }

    /** «питьё» (D-047, was «вода»): one glass more or less. */
    fun water(day: LocalDate, delta: Int) {
        viewModelScope.launch { runCatching { marks.water(day, delta) } }
    }

    /** «питание»: a meal of [slot] marked or unmarked (a check-in only: no calories, A46 §2.3). */
    fun meal(day: LocalDate, slot: String, eaten: Boolean) {
        viewModelScope.launch { runCatching { marks.meal(day, slot, eaten) } }
    }

    /**
     * A task of the day done or taken back: a plan item (training, focus) keeps the plan's own mark
     * (OPEN ⇄ DONE_SELF); any other task is a mark of the day.
     */
    fun taskDone(day: LocalDate, task: DayTask, done: Boolean) {
        viewModelScope.launch {
            val item = state.value.plan?.takeIf { it.dayKey == day }?.items?.firstOrNull { it.id == task.id }
            if (item != null) {
                if (canMark(item) && (item.status == ItemStatus.OPEN) == done) plans.toggle(day, item.id)
            } else {
                runCatching { marks.taskDone(day, task.id, done) }
            }
        }
    }

    /** T-08: «Не сегодня» — the task goes back to its week goal for today. */
    fun toWeek(move: TaskMove) {
        viewModelScope.launch { runCatching { marks.toWeek(move) } }
    }

    fun backFromWeek(day: LocalDate, taskId: String) {
        viewModelScope.launch { runCatching { marks.undoToWeek(day, taskId) } }
    }

    private companion object {
        const val TICK_MS = 60_000L
        const val STEPS_MS = 5 * 60_000L
    }
}
