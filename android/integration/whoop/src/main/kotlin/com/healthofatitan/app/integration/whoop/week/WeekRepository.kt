package com.healthofatitan.app.integration.whoop.week

import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.ProfileSettingsStore
import com.healthofatitan.app.core.database.dao.CheckinDao
import com.healthofatitan.app.core.database.dao.DayDao
import com.healthofatitan.app.core.database.dao.MetricDao
import com.healthofatitan.app.core.database.dao.TodayDao
import com.healthofatitan.app.core.database.dao.WeekDao
import com.healthofatitan.app.core.database.entity.BodyInputEntity
import com.healthofatitan.app.core.database.entity.DayPlanEntity
import com.healthofatitan.app.core.database.entity.TodayMarkEntity
import com.healthofatitan.app.core.database.entity.WeekDayScoreEntity
import com.healthofatitan.app.core.database.entity.WeekExperimentEntity
import com.healthofatitan.app.core.database.entity.WeekFrameEntity
import com.healthofatitan.app.core.database.entity.WeekGoalEntity
import com.healthofatitan.app.core.database.entity.WeekPlanEntity
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.model.profile.ProfileSettings
import com.healthofatitan.app.core.model.profile.SexForCalc
import com.healthofatitan.app.domain.engine.DayEngine
import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.metrics.Metrics
import com.healthofatitan.app.domain.today.CalendarWeek
import com.healthofatitan.app.domain.today.DayMode
import com.healthofatitan.app.domain.today.DayScore
import com.healthofatitan.app.domain.today.DayTask
import com.healthofatitan.app.domain.today.GoalMeasure
import com.healthofatitan.app.domain.today.GoalSource
import com.healthofatitan.app.domain.today.Scenario
import com.healthofatitan.app.domain.today.SleepPrep
import com.healthofatitan.app.domain.today.Sphere
import com.healthofatitan.app.domain.today.SupportNorms
import com.healthofatitan.app.domain.today.Supports
import com.healthofatitan.app.domain.today.SupportsInput
import com.healthofatitan.app.domain.today.TaskKind
import com.healthofatitan.app.domain.today.TodayCopy
import com.healthofatitan.app.domain.today.WeekGoal
import com.healthofatitan.app.domain.today.WeekPlan
import com.healthofatitan.app.domain.today.week.Activity
import com.healthofatitan.app.domain.today.week.BodySeries
import com.healthofatitan.app.domain.today.week.DayFact
import com.healthofatitan.app.domain.today.week.Experiment
import com.healthofatitan.app.domain.today.week.ExperimentGate
import com.healthofatitan.app.domain.today.week.ExperimentMetric
import com.healthofatitan.app.domain.today.week.ExperimentOutcome
import com.healthofatitan.app.domain.today.week.ExperimentPoint
import com.healthofatitan.app.domain.today.week.ExperimentResult
import com.healthofatitan.app.domain.today.week.ExperimentRules
import com.healthofatitan.app.domain.today.week.ExperimentSeed
import com.healthofatitan.app.domain.today.week.ExperimentSource
import com.healthofatitan.app.domain.today.week.ExperimentStatus
import com.healthofatitan.app.domain.today.week.FitnessAge
import com.healthofatitan.app.domain.today.week.FitnessGate
import com.healthofatitan.app.domain.today.week.FitnessInput
import com.healthofatitan.app.domain.today.week.FitnessMilestones
import com.healthofatitan.app.domain.today.week.LastWeek
import com.healthofatitan.app.domain.today.week.MilestoneInputs
import com.healthofatitan.app.domain.today.week.PathWeek
import com.healthofatitan.app.domain.today.week.Pattern
import com.healthofatitan.app.domain.today.week.PatternTexts
import com.healthofatitan.app.domain.today.week.PlanStatus
import com.healthofatitan.app.domain.today.week.Proposal
import com.healthofatitan.app.domain.today.week.RitualAnswers
import com.healthofatitan.app.domain.today.week.RitualRules
import com.healthofatitan.app.domain.today.week.RitualState
import com.healthofatitan.app.domain.today.week.RitualView
import com.healthofatitan.app.domain.today.week.SculptureRevealed
import com.healthofatitan.app.domain.today.week.Sex
import com.healthofatitan.app.domain.today.week.Skew
import com.healthofatitan.app.domain.today.week.SupportsWeekInput
import com.healthofatitan.app.domain.today.week.TitanOfWeek
import com.healthofatitan.app.domain.today.week.TitanWeekLook
import com.healthofatitan.app.domain.today.week.WeekBuilder
import com.healthofatitan.app.domain.today.week.WeekCopy
import com.healthofatitan.app.domain.today.week.WeekDetail
import com.healthofatitan.app.domain.today.week.WeekGoalRules
import com.healthofatitan.app.domain.today.week.WeekInput
import com.healthofatitan.app.domain.today.week.WeekKind
import com.healthofatitan.app.domain.today.week.WeekPose
import com.healthofatitan.app.domain.today.week.WeekPush
import com.healthofatitan.app.domain.today.week.WeekScoring
import com.healthofatitan.app.domain.today.week.WeekSky
import com.healthofatitan.app.domain.today.week.WeekView
import com.healthofatitan.app.domain.today.week.WeightInput
import com.healthofatitan.app.integration.whoop.WhoopDay
import com.healthofatitan.app.integration.whoop.WhoopGlance
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import com.healthofatitan.app.integration.whoop.day.WeekPlanSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What overrides the week, bound in :app (crisis log, StormNet of the state cards, the profile). */
interface WeekSignals {
    /** CrisisGuard ≥ 2 in the last 72 h, read from the safety bus (A60 §3): the Sunday push stays silent. */
    suspend fun crisis(): Boolean

    /** When «Как ты на самом деле?» (StormNet) last opened; skews hide for 7 days after it (W-04). */
    suspend fun stormShownAt(): Instant?

    /** The profile of specifics (A41): `ed_care`, pregnancy, heart medication (beta-blockers). */
    suspend fun profile(): WeekProfile

    companion object {
        val None = object : WeekSignals {
            override suspend fun crisis(): Boolean = false
            override suspend fun stormShownAt(): Instant? = null
            override suspend fun profile(): WeekProfile = WeekProfile()
        }
    }
}

data class WeekProfile(val edCare: Boolean = false, val pregnancy: Boolean = false, val betaBlockers: Boolean = false, val healthAnxiety: Boolean = false)

/**
 * The seam to «Путь» (A57 §4.3, D-050, A67 С-3), bound by `:feature:path` (`PathWeekBridge`): the
 * stage of the active goal gives the goals of the week ([stageGoals], мост ①); the lever of the
 * goal becomes the experiment of the week (С4); on Sunday «Путь» listens to
 * [WeekRepository.revealed] (the Sculpture's lights fly to the details of «Срез», С4).
 */
interface WeekPathBridge {
    /** The lever of the Path goal as an experiment, or null when there is no goal (slice С4). */
    suspend fun lever(): ExperimentSeed? = null

    /**
     * The goal of the Path as the weeks see it, live: its stage goals, a pause, or no goal. A new
     * value re-reads the current week (a goal taken today pours into it, A56 §2).
     */
    fun stageGoals(): Flow<PathWeek> = flowOf(PathWeek.NoGoal)

    companion object {
        val None: WeekPathBridge = object : WeekPathBridge {}
    }
}

/** The Sunday push «Твой Титан недели готов» (A52 §2.7), shown by :app. */
fun interface WeekNotifier {
    fun showWeek(title: String, text: String): Boolean
}

/** Everything the screen «Неделя» draws. */
data class WeekScreenState(
    val view: WeekView,
    val frames: List<TitanWeekLook> = emptyList(),
    val ritual: RitualView? = null,
    /** The goal ids for which «+1 шаг» is offered in the ritual. */
    val suggestUp: Set<String> = emptySet(),
    val whoopConnected: Boolean = true,
    val dayDetails: Map<LocalDate, DayDetail> = emptyMap(),
    val bodyInputs: BodyInputs = BodyInputs(),
    /** «Что значит эта поза» on the first two Sundays (A53 §3.1). */
    val poseCaption: String? = null,
    val fitnessOptedIn: Boolean = false,
)

/** A day of the week for the read-only tap on its column (A52 §2.1). */
data class DayDetail(val day: LocalDate, val mode: DayMode?, val supportsFilled: Int, val tasksDone: Int, val tasks: List<String>, val haiku: List<String>?)

data class BodyInputs(val ageYears: Int? = null, val sex: Sex? = null, val waistCm: Double? = null, val waistDay: LocalDate? = null)

/**
 * «Неделя» (A52, D-044, D-045) and the real [WeekPlanSource] behind «Сегодня»: the goals of the
 * week live in Room 11 (`week_plan`, `week_goal`); their progress is counted live from what is
 * already stored (plan items, WHOOP sessions, marks of the day) and from WHOOP values held in memory
 * ([WhoopGlance.days], incl. the workouts' `zone_durations`). A day's score is stored at 04:00 the
 * next day (`week_day_score`); the frames of the Titan of the week make the year ribbon.
 *
 * Raw WHOOP values never reach the database: only HoT-derived shares and counts do.
 */
class WeekRepository(
    private val weekDao: WeekDao,
    private val todayDao: TodayDao,
    private val dayDao: DayDao,
    private val metricDao: MetricDao,
    private val checkinDao: CheckinDao,
    private val plans: DayPlanRepository,
    private val glance: StateFlow<WhoopGlance?>,
    private val signals: WeekSignals,
    private val clock: Clock,
    private val whoopConnected: Flow<Boolean> = flowOf(true),
    /** The haiku shown at «Рассвет» that day, for the read-only day (A52 §2.1 «Тап по колонне»). */
    private val haiku: suspend (LocalDate) -> List<String>? = { null },
    private val path: WeekPathBridge = WeekPathBridge.None,
    /**
     * The profile «Я» (A61 §2.1, D-055): the year of birth and the sex for calculations live there
     * now; `body_input` keeps the waist (and is read for age and sex only without a profile, in tests).
     */
    private val profile: ProfileSettingsStore? = null,
) : WeekPlanSource {
    private val user = HotDatabase.LOCAL_USER
    private val changes = MutableStateFlow(0)
    private val revealedEvents = MutableSharedFlow<SculptureRevealed>(replay = 1, extraBufferCapacity = 4)

    /** Sunday: the Sculpture was revealed in the ritual (A57 §4.3) — for «Путь». */
    val revealed: SharedFlow<SculptureRevealed> = revealedEvents.asSharedFlow()
    private val mutex = Mutex()

    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    private val ticker: Flow<LocalDateTime> = flow {
        while (true) {
            emit(now())
            delay(TICK_MS)
        }
    }

    private fun bump() {
        changes.value++
    }

    // ── WeekPlanSource ───────────────────────────────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun week(day: LocalDate): Flow<WeekPlan> =
        combine(changes, glance, todayDao.observeDay(user, day.toString()), pathFlow) { _, g, _, _ -> g }.mapLatest { g -> withProgress(day, g) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun candidates(day: LocalDate): Flow<List<DayTask>> =
        combine(changes, glance, plans.latest, todayDao.observeDay(user, day.toString()), pathFlow) { _, g, plan, marks, p -> Pair(Triple(g, plan, marks), p) }
            .mapLatest { (t, p) -> candidatesOf(day, t.first, t.second, t.third, p) }

    /** The Path as the weeks see it (A67 С-3); a broken bridge is «no goal», never a broken week. */
    private val pathFlow: Flow<PathWeek> = path.stageGoals().catchToNoGoal().distinctUntilChanged()

    private suspend fun pathWeek(): PathWeek = runCatching { pathFlow.first() }.getOrDefault(PathWeek.NoGoal)

    private suspend fun candidatesOf(
        day: LocalDate,
        g: WhoopGlance?,
        plan: com.healthofatitan.app.integration.whoop.day.DayView?,
        marks: List<TodayMarkEntity>,
        pathNow: PathWeek = PathWeek.NoGoal,
    ): List<DayTask> {
        // A67 С-4: the steps of the stage carry the short name of the goal; the mode of the day and
        // the safety bus decide the rest further down (TaskRules.pick), the goal never overrides them.
        val goalName = (pathNow as? PathWeek.Active)?.name
        val week = withProgress(day, g)
        val sick = plan?.dayKey == day && DayFlag.SICK in plan.flags
        val doneToday = marks.filter { it.kind == TodayMarkEntity.KIND_TASK_DONE }.mapNotNull { WeekGoalRules.goalOfTask(it.ref) }.toSet()
        // The plan of today carries the workout and the focus block (stream B, A09 WRK): they stay
        // plan items (WHOOP confirms the workout), tied to their week goals.
        val items = plan?.takeIf { it.dayKey == day }?.items.orEmpty()
        val fromPlan = items.mapNotNull { item ->
            val goal = when (item.id) {
                DayEngine.Ids.TRAINING -> week.goal(WeekGoalRules.ID_TRAINING)
                DayEngine.Ids.FOCUS -> week.goal(WeekGoalRules.ID_FOCUS)
                else -> null
            } ?: return@mapNotNull null
            DayTask(
                id = item.id,
                kind = goal.kind,
                title = item.title,
                why = item.detail,
                weekGoalId = goal.id,
                week = TodayCopy.week(goal.progress.coerceAtMost(goal.target), goal.target),
                hasMini = true,
                done = item.status != ItemStatus.OPEN,
                route = if (goal.kind == TaskKind.TRAINING) HotRoute.TRAINING else null,
                goal = WeekGoalRules.goalMark(goal, goalName),
            )
        }
        val covered = fromPlan.mapNotNull { it.weekGoalId }.toSet()
        val rest = WeekGoalRules.candidates(week, day, doneToday, paused = sick, goal = goalName).filter { it.weekGoalId !in covered }
        return fromPlan + rest
    }

    // ── The plan of a week ───────────────────────────────────────────────────────────────────────

    private fun WeekGoalEntity.toGoal(): WeekGoal = WeekGoal(
        id = goalId,
        sphere = runCatching { Sphere.valueOf(sphere) }.getOrDefault(Sphere.BODY),
        kind = runCatching { TaskKind.valueOf(kind) }.getOrDefault(TaskKind.REMINDER),
        title = title,
        target = target,
        unit = unit,
        progress = 0,
        source = WeekGoalRules.sourceOf(goalId, source),
        measure = runCatching { GoalMeasure.valueOf(measure) }.getOrDefault(GoalMeasure.TIMES),
    )

    private fun WeekGoal.toRow(monday: LocalDate, order: Int) = WeekGoalEntity(
        user, monday.toString(), id, sphere.name, kind.name, measure.name, title, target, unit, source.name, order,
    )

    /** What kind of week [monday] starts (A52 §3.1, §3.6). */
    private suspend fun kindOf(monday: LocalDate): WeekKind {
        val first = weekDao.firstPlan(user)?.weekStart?.let(LocalDate::parse)
        if (first == null || !first.isBefore(monday)) return WeekKind.FIRST
        val previous = weekDao.plan(user, monday.minusDays(7).toString())
        return if (previous == null) WeekKind.RETURN else WeekKind.NORMAL
    }

    /**
     * The stored plan of [monday]; without one, HoT proposes it from the week before (A52 §2.2) —
     * the days take tasks from it at once; it waits for the Sunday ritual or becomes AUTO (W-06).
     */
    suspend fun ensurePlan(monday: LocalDate): Pair<WeekPlan, PlanStatus> = mutex.withLock {
        val stored = weekDao.plan(user, monday.toString())
        if (stored != null) {
            val status = runCatching { PlanStatus.valueOf(stored.status) }.getOrDefault(PlanStatus.PROPOSED)
            val now = WeekGoalRules.status(status, monday, now())
            if (now != status) weekDao.upsertPlan(stored.copy(status = now.name))
            val plan = WeekPlan(monday, weekDao.goals(user, monday.toString()).map { it.toGoal() })
            // «Беру путь» this week: the goals of the stage pour into it once (A56 §2, A67 С-3)
            val poured = if (monday == CalendarWeek.start(now().toLocalDate())) WeekGoalRules.pour(plan, pathWeek()) else null
            if (poured != null) {
                weekDao.savePlan(stored.copy(status = now.name), poured.goals.mapIndexed { i, g -> g.toRow(monday, i) })
                return@withLock poured to now
            }
            return@withLock plan to now
        }
        val proposal = propose(monday, glance.value)
        val status = WeekGoalRules.status(PlanStatus.PROPOSED, monday, now())
        weekDao.savePlan(
            WeekPlanEntity(user, monday.toString(), status.name, assembledAt = clock.millis()),
            proposal.plan.goals.mapIndexed { i, g -> g.toRow(monday, i) },
        )
        proposal.plan to status
    }

    private suspend fun propose(monday: LocalDate, g: WhoopGlance?): Proposal {
        val prevMonday = monday.minusDays(7)
        val prevRow = weekDao.plan(user, prevMonday.toString())
        val previous = prevRow?.let { withProgressOf(prevMonday, WeekPlan(prevMonday, weekDao.goals(user, prevMonday.toString()).map { it.toGoal() }), g, prevMonday.plusDays(6)) }
        val last = previous?.let {
            val scores = weekDao.scores(user, prevMonday.toString(), prevMonday.plusDays(6).toString())
            LastWeek(
                completion = WeekGoalRules.completion(it.goals),
                gentleDays = scores.count { s -> s.mode == DayMode.BEREZHNO.name },
                chargeInNorm = chargeInNorm(prevMonday, prevMonday.plusDays(6)),
            )
        }
        return WeekGoalRules.propose(monday, previous, last, kindOf(monday), path = pathWeek())
    }

    // ── Progress of the goals ────────────────────────────────────────────────────────────────────

    private suspend fun withProgress(day: LocalDate, g: WhoopGlance?): WeekPlan {
        val monday = CalendarWeek.start(day)
        val (plan, _) = ensurePlan(monday)
        return withProgressOf(monday, plan, g, day)
    }

    private fun doneStatus(s: String) = s != ItemStatus.OPEN.name

    /** The progress of each goal of the week of [monday] up to [until], counted from what is stored and WHOOP memory. */
    private suspend fun withProgressOf(monday: LocalDate, plan: WeekPlan, g: WhoopGlance?, until: LocalDate): WeekPlan {
        val days = (0..6).map { monday.plusDays(it.toLong()) }.filter { !it.isAfter(until) }
        val keys = days.map { it.toString() }
        val doneMarks = runCatching { todayDao.since(user, monday.toString(), TodayMarkEntity.KIND_TASK_DONE) }.getOrDefault(emptyList())
            .filter { it.dayKey in keys }
        fun markDays(vararg refs: String): Set<String> = doneMarks.filter { it.ref in refs }.map { it.dayKey }.toSet()
        val items = days.associateWith { d -> runCatching { dayDao.items(user, d.toString()) }.getOrDefault(emptyList()) }
        fun itemDays(id: String): Set<String> = items.filter { (_, list) -> list.any { it.itemId == id && doneStatus(it.status) } }.keys.map { it.toString() }.toSet()
        val sessions = runCatching { dayDao.sessions(user, monday.toString()) }.getOrDefault(emptyList()).filter { it.dayKey in keys }
        val whoopDays = g?.days.orEmpty().filter { it.day in days }
        val sleepMetric = runCatching { metricDao.series(user, Metrics.SLP.id, Metrics.SLP.version, monday.toString()) }.getOrDefault(emptyList())
            .filter { it.dayKey in keys }
        val goals = plan.goals.map { goal ->
            val progress = when (goal.id) {
                WeekGoalRules.ID_TRAINING -> (sessions.map { it.dayKey }.toSet() + itemDays(DayEngine.Ids.TRAINING) + markDays(WeekGoalRules.taskId(goal.id), DayEngine.Ids.TRAINING)).size
                WeekGoalRules.ID_FOCUS -> (itemDays(DayEngine.Ids.FOCUS) + markDays(WeekGoalRules.taskId(goal.id), DayEngine.Ids.FOCUS)).size
                WeekGoalRules.ID_ZONES -> whoopDays.sumOf { d -> d.workouts.sumOf { it.activeMinutes } }
                WeekGoalRules.ID_SLEEP -> if (whoopDays.any { it.sleepHours != null }) {
                    whoopDays.count { d -> d.sleepHours != null && d.sleepHours >= (d.needHours ?: SLEEP_FALLBACK_H) }
                } else {
                    sleepMetric.count { (it.value ?: 0.0) >= SLEEP_FALLBACK_H }
                }
                else -> markDays(WeekGoalRules.taskId(goal.id)).size
            }
            goal.copy(progress = progress)
        }
        return WeekPlan(monday, goals)
    }

    private suspend fun chargeInNorm(from: LocalDate, to: LocalDate): Boolean {
        val rows = runCatching { metricDao.series(user, Metrics.ECAP.id, Metrics.ECAP.version, from.toString()) }.getOrDefault(emptyList())
            .filter { it.dayKey <= to.toString() && it.level != null }
        return rows.isNotEmpty() && rows.count { it.level == 0 } * 2 >= rows.size
    }

    // ── Days ─────────────────────────────────────────────────────────────────────────────────────

    private fun Regime.mode(): DayMode = when (this) {
        Regime.GENTLE -> DayMode.BEREZHNO
        Regime.STEADY -> DayMode.ROVNO
        Regime.BOOST -> DayMode.RAZGON
    }

    private fun DayPlanEntity.mode(): DayMode? = runCatching { Regime.valueOf(regime).mode() }.getOrNull()

    private fun DayPlanEntity.sick(): Boolean = DayFlag.SICK.name in flags.split(',')

    private fun minuteOf(millis: Long): Int {
        val t = Instant.ofEpochMilli(millis).atZone(clock.zone).toLocalTime()
        return t.hour * 60 + t.minute
    }

    /** The five supports of a day as shares of their norms (A46 §2.3), from the same rules as «Сегодня». */
    private fun fillsOf(day: LocalDate, now: LocalDateTime, plan: DayPlanEntity?, w: WhoopDay?, marks: List<TodayMarkEntity>): List<Float>? {
        val water = marks.firstOrNull { it.kind == TodayMarkEntity.KIND_WATER }?.count ?: 0
        val meals = marks.filter { it.kind == TodayMarkEntity.KIND_MEAL }
        if (plan == null && w == null && marks.isEmpty()) return null
        val lastWorkoutEnd = w?.workouts?.maxOfOrNull { it.start.plusSeconds(it.minutes * 60L) }
            ?.takeIf { it.atZone(clock.zone).toLocalDate() == day }?.let { minuteOf(it.toEpochMilli()) }
        val nowMin = if (day == now.toLocalDate()) now.hour * 60 + now.minute else MINUTES_IN_DAY - 1
        val input = SupportsInput(
            mode = plan?.mode(),
            scenario = Scenario.DAY,
            nowMin = nowMin,
            strain = w?.strain,
            whoopSteps = w?.steps,
            mealsMarked = meals.size,
            water = water,
            sleep = SleepPrep(
                caffeineUntilMin = plan?.caffeineCutoffMin ?: SupportNorms.CAFFEINE_UNTIL_MIN,
                screensFromMin = plan?.windDownMin ?: SupportNorms.SCREENS_FROM_MIN,
                bedtimeMin = plan?.bedtimeMin,
            ),
            lastWorkoutEndMin = lastWorkoutEnd,
            lastMealMin = meals.maxOfOrNull { it.at }?.let(::minuteOf),
        )
        return Supports.build(input).map { it.fill }
    }

    private data class DayData(val fact: DayFact, val detail: DayDetail, val fills: List<Float>?, val fromStore: Boolean = false)

    private suspend fun dayData(day: LocalDate, now: LocalDateTime, g: WhoopGlance?, stored: WeekDayScoreEntity?): DayData {
        val key = day.toString()
        val plan = runCatching { dayDao.plan(user, key) }.getOrNull()
        val marks = runCatching { todayDao.day(user, key) }.getOrDefault(emptyList())
        val items = runCatching { dayDao.items(user, key) }.getOrDefault(emptyList())
        val w = g?.days?.firstOrNull { it.day == day }
        val doneItems = items.filter { it.itemId in TASK_ITEMS && doneStatus(it.status) }
        val doneMarks = marks.filter { it.kind == TodayMarkEntity.KIND_TASK_DONE }
        val tasks = (doneItems.map { it.title } + doneMarks.map { it.ref }).distinct()
        val tasksDone = (doneItems.map { it.itemId } + doneMarks.map { it.ref }).distinct().size
        val mode = plan?.mode() ?: stored?.mode?.let { runCatching { DayMode.valueOf(it) }.getOrNull() }
        // After a restart WHOOP memory is empty: a day already scored keeps its stored score.
        val keep = stored != null && w == null && WeekScoring.closed(day, now)
        val storedFills = stored?.fills?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 5 }
        val fills = if (keep) storedFills else fillsOf(day, now, plan, w, marks) ?: storedFills
        val fact = DayFact(
            day = day,
            mode = mode,
            fills = fills,
            tasksDone = tasksDone,
            sick = plan?.sick() ?: stored?.sick ?: false,
            score = stored?.takeIf { keep }?.let { DayScore(it.inSupport, it.supportsFilled, it.tasksDone) },
        )
        val score = WeekScoring.score(fact)
        return DayData(
            fact,
            DayDetail(day, mode, score?.supportsFilled ?: 0, score?.tasksDone ?: 0, tasks, runCatching { haiku(day) }.getOrNull()),
            fills,
            keep,
        )
    }

    /** A52 §4: a day's score is stored at 04:00 the next day; a stored score is the day's record after that. */
    private suspend fun storeClosed(days: List<DayData>, now: LocalDateTime) {
        val rows = days.filter { WeekScoring.closed(it.fact.day, now) && !it.fromStore }.mapNotNull { d ->
            val s = WeekScoring.score(d.fact) ?: return@mapNotNull null
            WeekDayScoreEntity(
                user, d.fact.day.toString(), s.dayInSupport, s.supportsFilled, s.tasksDone, d.fact.mode?.name, d.fact.sick,
                d.fills?.joinToString(",") { String.format(java.util.Locale.ROOT, "%.2f", it) }.orEmpty(), clock.millis(),
            )
        }
        if (rows.isNotEmpty()) runCatching { weekDao.upsertScores(rows) }
    }

    // ── The screen ───────────────────────────────────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<WeekScreenState> =
        combine(ticker.map { it.truncatedTo(ChronoUnit.MINUTES) }.distinctUntilChanged(), glance, changes, whoopConnected, weekDao.observeFrames(user)) { now, g, _, connected, frames ->
            Snapshot(now, g, connected, frames)
        }.mapLatest { s -> load(s.now, s.glance, s.connected, s.frames) }

    private data class Snapshot(val now: LocalDateTime, val glance: WhoopGlance?, val connected: Boolean, val frames: List<WeekFrameEntity>)

    suspend fun load(now: LocalDateTime, g: WhoopGlance?, whoopConnected: Boolean = true, frames: List<WeekFrameEntity> = emptyList()): WeekScreenState {
        val today = now.toLocalDate()
        val monday = CalendarWeek.start(today)
        val (rawPlan, status) = ensurePlan(monday)
        val plan = withProgressOf(monday, rawPlan, g, today)
        val stored = weekDao.scores(user, monday.toString(), monday.plusDays(6).toString()).associateBy { it.dayKey }
        val days = (0..6).map { monday.plusDays(it.toLong()) }.filter { !it.isAfter(today) }
            .map { dayData(it, now, g, stored[it.toString()]) }
        storeClosed(days, now)
        val facts = days.map { it.fact }
        val kind = kindOf(monday)
        val profile = runCatching { signals.profile() }.getOrDefault(WeekProfile())
        val storm = runCatching { signals.stormShownAt() }.getOrNull()
            ?.let { ChronoUnit.DAYS.between(it.atZone(clock.zone).toLocalDate(), today) in 0..6 } == true
        val weekDays = g?.days.orEmpty().filter { CalendarWeek.contains(monday, it.day) && !it.day.isAfter(today) }
        val supports = withMetrics(supportsWeek(days, weekDays), monday, today)
        val fillsByDay = days.mapNotNull { it.fills }
        val supportFills = if (fillsByDay.isEmpty()) emptyList() else (0 until 5).map { i -> fillsByDay.map { it[i] }.average().toFloat() }
        val row = weekDao.plan(user, monday.toString())
        val prevRow = weekDao.plan(user, monday.minusDays(7).toString())
        val ritual = RitualState(
            doneAt = row?.ritualDoneAt,
            pride = row?.pride,
            pastWeekOpen = prevRow != null && prevRow.ritualDoneAt == null && now.isBefore(WeekGoalRules.deadline(monday)),
        )
        val prevScores = weekDao.scores(user, monday.minusDays(7).toString(), monday.minusDays(1).toString())
        val inputs = bodyInputs()
        val todayPlan = runCatching { dayDao.plan(user, today.toString()) }.getOrNull()
        val sickNow = todayPlan?.sick() == true
        val firstWeek = weekDao.firstPlan(user)?.weekStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val weeksOfData = firstWeek?.let { ChronoUnit.WEEKS.between(it, monday).toInt() + 1 } ?: 0
        val optedIn = weekDao.latestInput(user, BodyInputEntity.KIND_FITNESS_OPT_IN)?.value == 1.0
        val fitnessGate = FitnessGate(
            sick = sickNow, pregnancy = profile.pregnancy, betaBlockers = profile.betaBlockers, edCare = profile.edCare, stormNet = storm,
            optedIn = optedIn, healthAnxiety = profile.healthAnxiety, weeksOfData = weeksOfData,
        )
        val fitnessInput = FitnessInput(inputs.ageYears, inputs.sex, inputs.waistCm, g?.corridors?.restingHr7, activity(g, today))
        val milestones = weekDao.lastMilestones(user).filter { it.weekStart != monday.toString() }
        val milestoneHere = FitnessMilestones.number(firstWeek, monday) ?: if (milestones.isEmpty()) weeksOfData / FitnessMilestones.EVERY else null
        val fitnessNow = if (fitnessGate.open && milestoneHere != null) milestoneOf(fitnessInput, milestoneHere) else null
        val storedFrame = weekDao.frame(user, monday.toString())?.takeIf { it.fitnessMilestone != null }
        val latest = fitnessNow?.let { FrameFitness(it.age, it.milestone, it.vo2, it.pa, it.waist) }
            ?: storedFrame?.let { FrameFitness(it.fitnessAge, it.fitnessMilestone, it.fitnessVo2, it.fitnessPa, it.fitnessWaist) }
            ?: milestones.firstOrNull()?.let { FrameFitness(it.fitnessAge, it.fitnessMilestone, it.fitnessVo2, it.fitnessPa, it.fitnessWaist) }
        val before = milestones.firstOrNull { it.fitnessMilestone != latest?.milestone }
        val lever = if (latest != null && before != null && inputs.sex != null) leverOf(inputs.sex, before, latest) else null
        val experiment = experimentOf(monday)
        val experimentDays = experiment?.let { e -> experimentDays(e.seed.id, monday, today) }.orEmpty()
        val input = WeekInput(
            now = now,
            facts = facts,
            plan = plan,
            planStatus = status,
            kind = kind,
            stormNetWithin7Days = storm,
            crisis = signals.crisis(),
            whoopConnected = whoopConnected,
            supports = supports,
            supportFills = supportFills,
            body = bodySeries(weekDays, monday, today, g),
            patterns = patterns(),
            weight = WeightInput(edCare = profile.edCare),
            fitnessGate = fitnessGate,
            fitness = fitnessInput,
            fitnessFourWeeksAgo = before?.fitnessAge,
            fitnessAtMilestone = latest?.age,
            fitnessMilestone = latest?.milestone,
            fitnessLever = lever,
            experiment = experiment,
            experimentDays = experimentDays,
            edCare = profile.edCare,
            waistAgeDays = inputs.waistDay?.let { ChronoUnit.DAYS.between(it, today).toInt() },
            ritual = ritual,
            previousDaysInSupport = prevScores.takeIf { it.isNotEmpty() }?.count { it.inSupport },
            pathSteps = plan.goals.filter { it.source == GoalSource.STAGE && it.progress > 0 }.size,
            returnedToday = runCatching { todayDao.day(user, today.toString()) }.getOrDefault(emptyList()).count { it.kind == TodayMarkEntity.KIND_TASK_TO_WEEK },
            sickNow = sickNow,
        )
        val view = WeekBuilder.build(input)
        saveFrame(view, fitnessNow)
        val ritualFor = WeekGoalRules.ritualFor(now)
        val ritualView = if (ritualFor != null) {
            val proposal = if (ritualFor == monday) {
                // Monday: the ritual of the past week assembles this one (already proposed)
                Proposal(rawPlan, emptySet(), WeekCopy.GOAL_PROPOSED)
            } else {
                propose(ritualFor, g)
            }
            val closing = ritualFor.minusDays(7)
            val result = experimentResult(closing, g)
            val gate = ExperimentGate(
                gentleWeek = WeekScoring.gentleWeek(view.columns) && closing == monday,
                stormNet = storm,
                edCare = profile.edCare,
                crisis = input.crisis,
            )
            val offers = runCatching { ExperimentRules.offers(input.patterns, runCatching { path.lever() }.getOrNull(), gate, experimentOf(closing)?.seed) }
                .getOrDefault(emptyList())
            RitualRules.withBeforeAfter(RitualRules.view(view, proposal, result, offers), input.previousDaysInSupport, view.daysInSupport)
        } else {
            null
        }
        return WeekScreenState(
            view = view,
            frames = frames.mapNotNull { it.toLook() },
            ritual = ritualView,
            suggestUp = ritualView?.proposal?.suggestUp.orEmpty(),
            whoopConnected = whoopConnected,
            dayDetails = days.associate { it.fact.day to it.detail },
            bodyInputs = inputs,
            poseCaption = TitanOfWeek.poseCaption(view.titan, frames.count { it.revealedAt != null && it.weekStart < monday.toString() }),
            fitnessOptedIn = optedIn,
        )
    }

    // ── «Эксперимент недели» (A53 §3.2, D-050) ───────────────────────────────────────────────────

    private suspend fun experimentOf(monday: LocalDate): Experiment? = runCatching { weekDao.experiment(user, monday.toString()) }.getOrNull()?.let { r ->
        runCatching {
            Experiment(
                monday,
                ExperimentSeed(r.experimentId, r.hypothesis, r.action, ExperimentMetric.valueOf(r.metric), ExperimentSource.valueOf(r.source), r.food),
                ExperimentStatus.valueOf(r.status),
            )
        }.getOrNull()
    }

    private suspend fun experimentDays(id: String, monday: LocalDate, until: LocalDate): Set<LocalDate> =
        runCatching { todayDao.since(user, monday.toString(), TodayMarkEntity.KIND_EXPERIMENT) }.getOrDefault(emptyList())
            .filter { it.ref == id && it.count > 0 }
            .mapNotNull { runCatching { LocalDate.parse(it.dayKey) }.getOrNull() }
            .filter { !it.isAfter(until) && it.isBefore(monday.plusDays(7)) }
            .toSet()

    /** The measure of a day: sleep and pulse from WHOOP memory, Заряд from the stored HoT metric. */
    private suspend fun valuesOf(metric: ExperimentMetric, from: LocalDate, to: LocalDate, g: WhoopGlance?): Map<LocalDate, Double> = when (metric) {
        ExperimentMetric.SLEEP -> g?.days.orEmpty().filter { it.sleepHours != null }.associate { it.day to it.sleepHours!! * 60 }
        ExperimentMetric.PULSE -> g?.days.orEmpty().filter { it.restingHr != null }.associate { it.day to it.restingHr!! }
        ExperimentMetric.CHARGE -> runCatching { metricDao.series(user, Metrics.ECAP.id, Metrics.ECAP.version, from.toString()) }.getOrDefault(emptyList())
            .filter { it.value != null }.mapNotNull { r -> runCatching { LocalDate.parse(r.dayKey) to r.value!! }.getOrNull() }.toMap()
    }.filterKeys { !it.isBefore(from) && !it.isAfter(to) }

    /** The answer of the experiment of the week [monday] — stored once given, else counted now. */
    private suspend fun experimentResult(monday: LocalDate, g: WhoopGlance?): ExperimentResult? {
        val row = runCatching { weekDao.experiment(user, monday.toString()) }.getOrNull() ?: return null
        if (row.status != ExperimentStatus.TAKEN.name) return null
        val outcome = row.outcome
        val text = row.resultText
        if (outcome != null && text != null) {
            return ExperimentResult(runCatching { ExperimentOutcome.valueOf(outcome) }.getOrDefault(ExperimentOutcome.TOO_FEW), WeekCopy.EXP_LEARNED, text, row.points ?: 0)
        }
        val e = experimentOf(monday) ?: return null
        val end = monday.plusDays(ExperimentRules.DAYS.toLong() - 1)
        val did = experimentDays(e.seed.id, monday, end)
        val values = valuesOf(e.seed.metric, monday.minusDays(7), end, g)
        val points = values.map { (d, v) -> ExperimentPoint(d, d in did, v) }
        return ExperimentRules.result(e.seed, points)
    }

    /** «Сегодня сделал» on the scale of the experiment. */
    suspend fun markExperiment(day: LocalDate, did: Boolean) {
        val e = experimentOf(CalendarWeek.start(day))?.takeIf { it.status == ExperimentStatus.TAKEN } ?: return
        if (did) {
            todayDao.upsert(TodayMarkEntity(user, day.toString(), TodayMarkEntity.KIND_EXPERIMENT, e.seed.id, 1, null, clock.millis()))
        } else {
            todayDao.delete(user, day.toString(), TodayMarkEntity.KIND_EXPERIMENT, e.seed.id)
        }
        bump()
    }

    /** The quiet bridge on «Сегодня»: «Эксперимент · 3 из 5 ›», or null when no experiment runs. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun experimentBridge(day: LocalDate): Flow<String?> =
        combine(changes, todayDao.observeDay(user, day.toString())) { _, _ -> Unit }.mapLatest {
            val monday = CalendarWeek.start(day)
            experimentOf(monday)?.takeIf { it.status == ExperimentStatus.TAKEN }?.let { e ->
                ExperimentRules.scale(monday, day, experimentDays(e.seed.id, monday, day)).bridge
            }
        }

    // ── Fitness age at milestones (A53 §3.3, D-050) ──────────────────────────────────────────────

    private data class FrameFitness(val age: Double?, val milestone: Int?, val vo2: Double?, val pa: Double?, val waist: Double?)

    private fun milestoneOf(i: FitnessInput, number: Int): FrameFitness? {
        val age = FitnessAge.estimate(i) ?: return null
        val pa = FitnessAge.paIndex(i.activity)
        val vo2 = FitnessAge.vo2(i.sex!!, i.ageYears!!, i.waistCm!!, i.restingHr!!, pa)
        return FrameFitness(age, number.coerceAtLeast(1), vo2, pa, i.waistCm)
    }

    private fun leverOf(sex: Sex, before: WeekFrameEntity, now: FrameFitness): String? {
        val b = MilestoneInputs(before.fitnessVo2 ?: return null, before.fitnessPa ?: return null, before.fitnessWaist ?: return null)
        val n = MilestoneInputs(now.vo2 ?: return null, now.pa ?: return null, now.waist ?: return null)
        return FitnessMilestones.lever(sex, b, n)
    }

    /** The choice «показывать фитнес-возраст на вехах» (A53 §3.3). */
    suspend fun setFitnessOptIn(on: Boolean) {
        weekDao.upsertInput(BodyInputEntity(user, BodyInputEntity.KIND_FITNESS_OPT_IN, now().toLocalDate().toString(), if (on) 1.0 else 0.0, null, clock.millis()))
        bump()
    }

    private fun supportsWeek(days: List<DayData>, weekDays: List<WhoopDay>): SupportsWeekInput {
        val goal = SupportNorms.stepsGoal(null)
        val stepDays = weekDays.filter { it.steps != null }
        val sleepDays = weekDays.filter { it.sleepHours != null }
        val mealsKnown = days.size
        val mealDays = days.count { d -> (d.fills?.getOrNull(2) ?: 0f) >= SupportNorms.FILLED.toFloat() }
        val drinks = days.mapNotNull { d -> d.fills?.getOrNull(3) }
        return SupportsWeekInput(
            balanceZ = emptyList(),
            stepDays = stepDays.takeIf { it.isNotEmpty() }?.count { (it.steps ?: 0) >= goal },
            stepDaysKnown = stepDays.size,
            zoneMinutes = weekDays.takeIf { w -> w.any { it.workouts.isNotEmpty() } }?.sumOf { d -> d.workouts.sumOf { it.activeMinutes } },
            mealDays = mealDays.takeIf { mealsKnown > 0 },
            mealDaysKnown = mealsKnown,
            drinkShare = drinks.takeIf { it.isNotEmpty() }?.average(),
            regularityPct = weekDays.mapNotNull { it.consistency }.takeIf { it.isNotEmpty() }?.average(),
            nightsAtNeed = sleepDays.takeIf { it.isNotEmpty() }?.count { (it.sleepHours ?: 0.0) >= (it.needHours ?: SLEEP_FALLBACK_H) },
            nightsKnown = sleepDays.size,
        )
    }

    /** «Весы» (LRB z), Заряд in norm and the 7-day sleep debt from stored HoT metrics. */
    private suspend fun withMetrics(base: SupportsWeekInput, monday: LocalDate, today: LocalDate): SupportsWeekInput {
        val lrb = runCatching { metricDao.series(user, Metrics.LRB.id, Metrics.LRB.version, monday.toString()) }.getOrDefault(emptyList())
            .filter { it.dayKey <= today.toString() }.mapNotNull { it.z ?: it.value }
        val debt = runCatching { metricDao.series(user, Metrics.SLP_DEBT.id, Metrics.SLP_DEBT.version, monday.minusDays(7).toString()) }.getOrDefault(emptyList())
            .filter { it.dayKey <= today.toString() }.lastOrNull()?.value
        return base.copy(balanceZ = lrb, chargeInNorm = chargeInNorm(monday, today), sleepDebtHours = debt)
    }

    private fun bodySeries(weekDays: List<WhoopDay>, monday: LocalDate, today: LocalDate, g: WhoopGlance?): List<BodySeries> {
        val c = g?.corridors
        val days = (0..6).map { monday.plusDays(it.toLong()) }.filter { !it.isAfter(today) }
        fun series(pick: (WhoopDay) -> Double?): List<Double?> = days.map { d -> weekDays.firstOrNull { it.day == d }?.let(pick) }
        return listOf(
            BodySeries(WeekCopy.B_RHR, series { it.restingHr }, c?.restingHr),
            BodySeries(WeekCopy.B_HRV, series { it.hrvMs }, c?.hrv),
            BodySeries(WeekCopy.B_RESP, series { it.respiratoryRate }, c?.respiratoryRate),
            BodySeries(WeekCopy.B_SPO2, series { it.spo2 }, c?.spo2),
            BodySeries(WeekCopy.B_SKIN, series { it.skinTempC }, c?.skinTemp),
        )
    }

    /** Workouts of the last 4 weeks as the HUNT questionnaire would read them (D-045). */
    private fun activity(g: WhoopGlance?, today: LocalDate): Activity? {
        val from = today.minusDays(27)
        val workouts = g?.days.orEmpty().filter { !it.day.isBefore(from) && !it.day.isAfter(today) }.flatMap { it.workouts }
        if (workouts.isEmpty()) return null
        val zoned = workouts.sumOf { it.zonedMinutes }.coerceAtLeast(1)
        return Activity(
            perWeek = workouts.size / 4.0,
            meanMinutes = workouts.map { it.minutes }.average(),
            hardShare = workouts.sumOf { it.hardMinutes }.toDouble() / zoned,
            moderateShare = workouts.sumOf { it.activeMinutes }.toDouble() / zoned,
        )
    }

    /** The discoveries of the state cards (A44 §5, Agent 9) as patterns; «Только ты» never comes here. */
    private suspend fun patterns(): List<Pattern> =
        runCatching { checkinDao.discoveries(user) }.getOrDefault(emptyList()).mapNotNull { d ->
            val text = PatternTexts.BY_PAIR[d.pairId] ?: return@mapNotNull null
            val steady = d.ciLow * d.ciHigh > 0
            Pattern(
                id = d.pairId.lowercase(),
                text = "${if (steady) text.positive else text.gentle} (${d.n1} из ${d.n1 + d.n0})",
                evidenceN = d.n1 + d.n0,
                effect = d.effect,
                action = text.action,
                onlyYou = d.sensitivity == "S5",
                steady = steady,
            )
        }

    private suspend fun saveFrame(view: WeekView, fitness: FrameFitness?) {
        val t = view.titan
        val prev = weekDao.frame(user, view.weekStart.toString())
        val row = WeekFrameEntity(
            user, view.weekStart.toString(), t.pose.name, t.mode.name, t.light, t.columns.joinToString(",") { String.format(java.util.Locale.ROOT, "%.2f", it) },
            t.sky.name, t.detail?.name, t.daysInSupport, t.seed, prev?.revealedAt ?: if (t.revealed) clock.millis() else null,
            fitness?.age ?: prev?.fitnessAge, clock.millis(),
            fitnessMilestone = fitness?.milestone ?: prev?.fitnessMilestone,
            fitnessVo2 = fitness?.vo2 ?: prev?.fitnessVo2,
            fitnessPa = fitness?.pa ?: prev?.fitnessPa,
            fitnessWaist = fitness?.waist ?: prev?.fitnessWaist,
        )
        if (prev == null || prev.copy(computedAt = 0) != row.copy(computedAt = 0)) runCatching { weekDao.upsertFrame(row) }
    }

    private fun WeekFrameEntity.toLook(): TitanWeekLook? = runCatching {
        val cols = columns.split(',').mapNotNull { it.toFloatOrNull() }
        TitanWeekLook(
            weekStart = LocalDate.parse(weekStart),
            pose = WeekPose.valueOf(pose),
            mode = DayMode.valueOf(mode),
            light = lightLevel,
            columns = cols,
            sky = WeekSky.valueOf(sky),
            detail = detail?.let { WeekDetail.valueOf(it) },
            reveal = if (revealedAt != null) 1f else 0.85f,
            revealed = revealedAt != null,
            daysInSupport = daysInSupport,
            seed = seed,
        )
    }.getOrNull()

    private fun ExperimentSeed.toRow(monday: LocalDate, status: ExperimentStatus) = WeekExperimentEntity(
        user, monday.toString(), id, hypothesis.take(MAX_TEXT), action.take(MAX_TEXT), metric.name, source.name, food, status.name, decidedAt = clock.millis(),
    )

    // ── Body inputs (fitness age) ────────────────────────────────────────────────────────────────

    suspend fun bodyInputs(): BodyInputs {
        val waist = weekDao.latestInput(user, BodyInputEntity.KIND_WAIST)
        // MIGRATION_12_13 seeded the profile from the legacy rows: with a profile, it alone decides
        // (a «не указывать» chosen there must not bring an old sex back).
        val person = profile?.current()
        return BodyInputs(
            ageYears = if (person != null) {
                person.ageIn(now().year)
            } else {
                weekDao.latestInput(user, BodyInputEntity.KIND_AGE)?.value?.toInt()
            },
            sex = if (person != null) {
                person.sexForCalc.toSex()
            } else {
                weekDao.latestInput(user, BodyInputEntity.KIND_SEX)?.text?.let { t -> Sex.entries.firstOrNull { it.name.equals(t, ignoreCase = true) } }
            },
            waistCm = waist?.value,
            waistDay = waist?.dayKey?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
    }

    /**
     * The age and the sex typed under the fitness age go to the profile «Я» only (one place for
     * them, A61 §2.1): with a profile nothing about them is written to `body_input` (Agent 7,
     * 2026-11-07). The legacy rows are written only without a profile (tests), adults only.
     */
    suspend fun saveBodyInputs(ageYears: Int?, sex: Sex?, waistCm: Double?) {
        val day = now().toLocalDate().toString()
        val at = clock.millis()
        val p = profile
        if (p != null) {
            val birthYear = ageYears?.let { now().year - it }
            if (ProfileSettings.validBirthYear(birthYear, now().year)) p.setBirthYear(birthYear)
            sex?.let { p.setSexForCalc(if (it == Sex.MALE) SexForCalc.MALE else SexForCalc.FEMALE) }
        } else {
            ageYears?.takeIf { it in ProfileSettings.MIN_AGE..ProfileSettings.MAX_AGE }
                ?.let { weekDao.upsertInput(BodyInputEntity(user, BodyInputEntity.KIND_AGE, day, it.toDouble(), null, at)) }
            sex?.let { weekDao.upsertInput(BodyInputEntity(user, BodyInputEntity.KIND_SEX, day, null, it.name.lowercase(), at)) }
        }
        waistCm?.takeIf { it in 40.0..200.0 }?.let { weekDao.upsertInput(BodyInputEntity(user, BodyInputEntity.KIND_WAIST, day, it, null, at)) }
        bump()
    }

    // ── Changes of goals (without penalty, A52 §2.2 «Тап») ───────────────────────────────────────

    suspend fun changeTarget(goalId: String, delta: Int) {
        val monday = CalendarWeek.start(now().toLocalDate())
        val rows = weekDao.goals(user, monday.toString())
        val row = rows.firstOrNull { it.goalId == goalId } ?: return
        val g = row.toGoal()
        val next = when {
            delta > 0 -> WeekGoalRules.heavier(g)
            delta < 0 -> WeekGoalRules.lighter(g)
            else -> g
        }
        weekDao.upsertGoals(listOf(row.copy(target = next.target)))
        bump()
    }

    suspend fun removeGoal(goalId: String) {
        weekDao.deleteGoal(user, CalendarWeek.start(now().toLocalDate()).toString(), goalId)
        bump()
    }

    /** «Напоминания как дела»: a reminder is a goal of days; the days take it as a task. */
    suspend fun addReminder(title: String, days: Int) {
        val text = title.trim().take(MAX_TITLE).takeIf { it.isNotEmpty() } ?: return
        val monday = CalendarWeek.start(now().toLocalDate())
        ensurePlan(monday)
        val rows = weekDao.goals(user, monday.toString())
        val id = WeekGoalRules.REMINDER_PREFIX + (rows.count { it.goalId.startsWith(WeekGoalRules.REMINDER_PREFIX) } + 1) + "-" + clock.millis() % 100_000
        val goal = WeekGoal(id, Sphere.MIND, TaskKind.REMINDER, text, days.coerceIn(1, 7), WeekCopy.UNIT_DAYS, 0, GoalSource.PERSON, GoalMeasure.DAYS)
        weekDao.upsertGoals(listOf(goal.toRow(monday, rows.size)))
        bump()
    }

    /**
     * «Итог и сборка» done (A52 §2.6): the next week's goals as approved, the pride and what drags
     * on kept with the closing week, the Titan of the week revealed.
     */
    suspend fun finishRitual(ritual: RitualView, skew: Skew?, answers: RitualAnswers) {
        val now = now()
        val target = WeekGoalRules.ritualFor(now) ?: return
        val closing = target.minusDays(7)
        val plan = RitualRules.apply(ritual.proposal, skew, answers)
        val existing = weekDao.plan(user, target.toString())
        weekDao.savePlan(
            WeekPlanEntity(user, target.toString(), PlanStatus.APPROVED.name, assembledAt = clock.millis(), pushedAt = existing?.pushedAt),
            plan.goals.mapIndexed { i, g -> g.toRow(target, i) },
        )
        val closingRow = weekDao.plan(user, closing.toString()) ?: WeekPlanEntity(user, closing.toString(), PlanStatus.AUTO.name, assembledAt = clock.millis())
        weekDao.upsertPlan(closingRow.copy(ritualDoneAt = clock.millis(), pride = answers.pride?.trim()?.take(MAX_TEXT), carry = answers.carry?.trim()?.take(MAX_TEXT)))
        val frame = weekDao.frame(user, closing.toString())
        frame?.let { weekDao.upsertFrame(it.copy(revealedAt = it.revealedAt ?: clock.millis())) }
        // the answer of the closing week's experiment is kept as words; the next one is taken or put aside
        val result = ritual.experimentResult
        weekDao.experiment(user, closing.toString())?.let { row ->
            if (result != null && row.outcome == null) weekDao.upsertExperiment(row.copy(outcome = result.outcome.name, resultText = result.text, points = result.points))
        }
        val next = answers.experiment
        when {
            next != null -> weekDao.upsertExperiment(next.toRow(target, ExperimentStatus.TAKEN))
            ritual.experimentOffers.isNotEmpty() -> weekDao.upsertExperiment(ritual.experimentOffers.first().toRow(target, ExperimentStatus.SKIPPED))
        }
        val days = frame?.daysInSupport ?: 0
        revealedEvents.tryEmit(SculptureRevealed(closing, days, lights = days, experiment = result))
        bump()
    }

    /** One push a week, on Sunday evening (A52 §2.7). Returns true when it went out. */
    suspend fun pushIfDue(notifier: WeekNotifier): Boolean = mutex.withLock {
        val now = now()
        val monday = CalendarWeek.start(now.toLocalDate())
        val row = weekDao.plan(user, monday.toString()) ?: return@withLock false
        val pushedFor = row.pushedAt?.let { CalendarWeek.start(Instant.ofEpochMilli(it).atZone(clock.zone).toLocalDate()) }
        if (!WeekPush.due(now, pushedFor, ritualDone = row.ritualDoneAt != null, crisis = signals.crisis())) return@withLock false
        val shown = notifier.showWeek(WeekCopy.PUSH_TITLE, WeekCopy.PUSH_TEXT)
        if (shown) weekDao.upsertPlan(row.copy(pushedAt = clock.millis()))
        shown
    }

    companion object {
        const val TICK_MS = 60_000L
        const val SLEEP_FALLBACK_H = 7.0
        const val MINUTES_IN_DAY = 24 * 60
        const val MAX_TITLE = 60
        const val MAX_TEXT = 280
        private val TASK_ITEMS = setOf(DayEngine.Ids.TRAINING, DayEngine.Ids.FOCUS)
    }
}

/** «Пол для расчётов» of the profile as the HUNT model reads it; «не указывать» gives no sex. */
private fun Flow<PathWeek>.catchToNoGoal(): Flow<PathWeek> = kotlinx.coroutines.flow.flow {
    try {
        collect { emit(it) }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        emit(PathWeek.NoGoal)
    }
}

private fun SexForCalc.toSex(): Sex? = when (this) {
    SexForCalc.MALE -> Sex.MALE
    SexForCalc.FEMALE -> Sex.FEMALE
    SexForCalc.UNSPECIFIED -> null
}
