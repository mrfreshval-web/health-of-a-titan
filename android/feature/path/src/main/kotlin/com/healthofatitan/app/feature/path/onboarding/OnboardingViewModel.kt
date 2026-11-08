package com.healthofatitan.app.feature.path.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.PathDao
import com.healthofatitan.app.core.datastore.FirstLaunchPrefs
import com.healthofatitan.app.core.datastore.FirstLaunchSettings
import com.healthofatitan.app.core.ui.crash.CrashLog
import com.healthofatitan.app.domain.engine.CrisisGuard
import com.healthofatitan.app.domain.metrics.Metrics
import com.healthofatitan.app.domain.path.GoalLayer
import com.healthofatitan.app.domain.path.GoalLifecycle
import com.healthofatitan.app.domain.path.GoalStatus
import com.healthofatitan.app.domain.path.PathCopy
import com.healthofatitan.app.domain.path.PracticalGoal
import com.healthofatitan.app.domain.today.CalendarWeek
import com.healthofatitan.app.domain.today.WeekGoal
import com.healthofatitan.app.feature.path.PathData
import com.healthofatitan.app.feature.path.PathFeatureFlags
import com.healthofatitan.app.feature.path.PathRepository
import com.healthofatitan.app.feature.path.build
import com.healthofatitan.app.feature.path.logic.RitualDraft
import com.healthofatitan.app.feature.path.logic.RitualLogic
import com.healthofatitan.app.integration.whoop.week.WeekRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * The first launch is shown while it is not passed and the person has no goal of the Path yet (a
 * person who already took a goal in «Назови цель» is not walked through it again).
 */
class FirstLaunchGateSource @Inject constructor(
    private val settings: FirstLaunchSettings,
    private val dao: PathDao,
) {
    val gate: Flow<FirstLaunchGate> =
        combine(settings.prefs, dao.observeGoals(HotDatabase.LOCAL_USER)) { prefs, goals ->
            val hasGoal = goals.any { runCatching { GoalLifecycle.layer(GoalStatus.valueOf(it.status)) == GoalLayer.PATH }.getOrDefault(false) }
            // A goal taken on step 2 does not end the first launch: step 3 is still to come.
            if (OnboardingRules.passed(prefs.done, prefs.step != null, hasGoal)) FirstLaunchGate.DONE else FirstLaunchGate.SHOW
        }.distinctUntilChanged()
}

/** What the first launch draws. */
data class OnboardingUi(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val answers: BehindAnswers = BehindAnswers(),
    val history: BehindImport? = null,
    val goalText: String = "",
    val why: String = "",
    val weeks: Int = OnboardingRules.DEFAULT_STAGE,
    val today: LocalDate = LocalDate.now(),
    /** The goal as it will be taken, for «HoT предлагает»; null without text. */
    val goal: PracticalGoal? = null,
    val suggestion: StageSuggestion = StageSuggestion(null, null, emptyList()),
    /** What still stops «Дальше» on step 2, as lines (P-rules of the Path). */
    val stops: List<String> = emptyList(),
    val week: List<WeekGoal> = emptyList(),
    /** CrisisGuard on the last free text: 1 keeps help visible, ≥ 2 opens SOS (A07). */
    val crisis: Int = 0,
    val busy: Boolean = false,
) {
    val canGoOn: Boolean
        get() = !busy && when (step) {
            OnboardingStep.PATH -> goalText.isNotBlank() && stops.isEmpty()
            else -> true
        }
}

private data class Draft(
    val step: OnboardingStep? = null,
    val answers: BehindAnswers = BehindAnswers(),
    val goalText: String = "",
    val why: String = "",
    val weeks: Int = OnboardingRules.DEFAULT_STAGE,
    val week: List<WeekGoal> = emptyList(),
    val crisis: Int = 0,
    val busy: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: FirstLaunchSettings,
    private val repository: PathRepository,
    private val week: WeekRepository,
    private val dao: PathDao,
    private val clock: Clock,
) : ViewModel() {
    private val user = HotDatabase.LOCAL_USER
    private val draft = MutableStateFlow(Draft())

    private val history: Flow<BehindImport?> = run {
        val today = LocalDate.now(clock)
        val from = today.minusDays(OnboardingRules.IMPORT_DAYS).toString()
        combine(
            dao.observeMetric(user, Metrics.SLP.id, Metrics.SLP.version, from),
            dao.observeMetric(user, Metrics.ECAP.id, Metrics.ECAP.version, from),
            dao.observeTrainingDays(user, from),
        ) { sleep, charge, trainings ->
            val days = (sleep.map { it.dayKey } + charge.map { it.dayKey } + trainings).mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (days.isEmpty()) {
                null
            } else {
                BehindImport(
                    from = days.min(),
                    to = days.max(),
                    trainings = trainings.size,
                    nightHours = sleep.mapNotNull { it.value }.takeIf { it.isNotEmpty() }?.average(),
                    charge = charge.mapNotNull { it.value }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                )
            }
        }.catch { emit(null) }
    }

    private val data: Flow<PathData> = repository.data.catch { emit(PathData.empty(LocalDate.now(clock))) }

    val ui: StateFlow<OnboardingUi?> = combine(draft, settings.prefs.catch { emit(FirstLaunchPrefs()) }, data, history) { d, prefs, data, history ->
        val step = d.step ?: prefs.step?.let { s -> OnboardingStep.entries.firstOrNull { it.name == s } } ?: OnboardingStep.WELCOME
        val answers = if (d.step == null && d.answers == BehindAnswers()) BehindAnswers(prefs.tried, prefs.worked, prefs.different) else d.answers
        val taken = data.goal
        val goal = taken ?: preview(d, data)
        val stops = if (taken != null || goal == null) {
            emptyList()
        } else {
            RitualLogic.violations(goal, data.given, PathFeatureFlags.current, data.goals).map { PathCopy.violation(it) }
        }
        OnboardingUi(
            step = step,
            answers = answers,
            history = history,
            goalText = d.goalText.ifEmpty { taken?.title.orEmpty() },
            why = d.why.ifEmpty { taken?.why.orEmpty() },
            weeks = d.weeks,
            today = data.today,
            goal = goal,
            suggestion = OnboardingRules.suggestion(goal, answers, data.today, d.weeks),
            stops = stops,
            week = d.week,
            crisis = d.crisis,
            busy = d.busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun preview(d: Draft, data: PathData): PracticalGoal? {
        if (d.goalText.isBlank()) return null
        val goal = runCatching { build(RitualDraft(text = d.goalText.trim(), why = d.why), data) }.getOrNull() ?: return null
        val parts = OnboardingRules.withLever(goal.components, OnboardingRules.suggestedLever(d.answers, goal.components))
        return goal.copy(components = parts, deadline = OnboardingRules.deadline(data.today, d.weeks))
    }

    private fun current(): OnboardingUi? = ui.value

    fun toggle(question: Int, chip: String) {
        val base = current()?.answers ?: BehindAnswers()
        draft.update { d ->
            val a = if (d.step == null) base else d.answers
            val next = when (question) {
                0 -> a.copy(tried = OnboardingRules.toggle(a.tried, chip))
                1 -> a.copy(worked = OnboardingRules.toggle(a.worked, chip))
                else -> a.copy(different = OnboardingRules.toggle(a.different, chip))
            }
            d.copy(step = d.step ?: current()?.step, answers = next)
        }
    }

    /** Free text is read by CrisisGuard on the phone first; it never leaves the device (P-21). */
    fun goalText(value: String) = text(value) { copy(goalText = value.take(RitualLogic.TITLE_MAX)) }

    fun why(value: String) = text(value) { copy(why = value.take(WHY_MAX)) }

    private fun text(value: String, apply: Draft.() -> Draft) {
        val level = CrisisGuard.assess(value).level.value
        draft.update { d -> d.apply().copy(step = d.step ?: current()?.step, crisis = maxOf(d.crisis, level)) }
    }

    fun weeks(n: Int) = draft.update { it.copy(step = it.step ?: current()?.step, weeks = n) }

    fun back() {
        val ui = current() ?: return
        val prev = OnboardingRules.back(ui.step) ?: return
        go(prev)
    }

    private fun go(step: OnboardingStep) {
        draft.update { it.copy(step = step, answers = current()?.answers ?: it.answers) }
        viewModelScope.launch { runCatching { settings.setStep(step.name) } }
    }

    /** «Позже» (A66 §0.2): the first launch ends, «Сегодня» is the home; §0.3 asks for a goal there. */
    fun later(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { settings.finish() }
            onDone()
        }
    }

    /** «Начать», «Дальше», «Собрать неделю». */
    fun next(onDone: () -> Unit) {
        val ui = current() ?: return
        if (!ui.canGoOn) return
        when (ui.step) {
            OnboardingStep.WELCOME -> go(OnboardingStep.BEHIND)
            OnboardingStep.BEHIND -> {
                viewModelScope.launch { runCatching { settings.setAnswers(ui.answers.tried, ui.answers.worked, ui.answers.different) } }
                go(OnboardingStep.PATH)
            }
            OnboardingStep.PATH -> take(ui)
            OnboardingStep.WEEK -> later(onDone)
        }
    }

    /** «Беру путь» from step 2: the goal starts today, the week pours its goals once (A67 С-3). */
    private fun take(ui: OnboardingUi) {
        draft.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching {
                val data = repository.data.first()
                if (data.goal == null) {
                    val goal = ui.goal ?: return@runCatching
                    if (RitualLogic.violations(goal, data.given, PathFeatureFlags.current, data.goals).isNotEmpty()) return@runCatching
                    repository.start(goal, RitualLogic.firstFire(goal, emptyList()), null)
                }
                settings.setStep(OnboardingStep.WEEK.name)
                val goals = loadWeek()
                draft.update { it.copy(step = OnboardingStep.WEEK, week = goals) }
            }.onFailure { CrashLog.record("onboarding.take", it) }
            draft.update { it.copy(busy = false) }
        }
    }

    private suspend fun loadWeek(): List<WeekGoal> {
        val monday = CalendarWeek.start(LocalDate.now(clock))
        val (plan, _) = week.ensurePlan(monday)
        return OnboardingRules.weekGoals(plan.goals)
    }

    /** Step 3 opened again after a restart: read the week that is already assembled. */
    fun refreshWeek() {
        viewModelScope.launch {
            runCatching { loadWeek() }.onSuccess { goals -> draft.update { it.copy(week = goals) } }
        }
    }

    /** «−» and «+» of a goal of the week (A52 §2.2: without penalty). */
    fun change(goalId: String, delta: Int) {
        viewModelScope.launch {
            runCatching {
                week.changeTarget(goalId, delta)
                val goals = loadWeek()
                draft.update { it.copy(week = goals) }
            }
        }
    }

    private companion object {
        const val WHY_MAX = 120
    }
}
