package com.healthofatitan.app.feature.path

import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.DayDao
import com.healthofatitan.app.core.database.dao.PathDao
import com.healthofatitan.app.core.database.dao.TrainingLogDao
import com.healthofatitan.app.core.database.dao.WeekDao
import com.healthofatitan.app.core.database.entity.PathGoalEntity
import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.path.GoalStatus
import com.healthofatitan.app.domain.path.PracticalGoal
import com.healthofatitan.app.domain.path.StageRules
import com.healthofatitan.app.domain.safety.SafetyState
import com.healthofatitan.app.domain.today.CalendarWeek
import com.healthofatitan.app.domain.today.DayMode
import com.healthofatitan.app.feature.path.logic.BehindCopy
import com.healthofatitan.app.feature.path.logic.BehindRecord
import com.healthofatitan.app.feature.path.logic.BehindSafety
import com.healthofatitan.app.feature.path.logic.DayRecord
import com.healthofatitan.app.feature.path.logic.PathStage
import com.healthofatitan.app.feature.path.logic.RecordKind
import com.healthofatitan.app.feature.path.logic.RuWords
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import com.healthofatitan.app.integration.whoop.safety.SafetyBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Everything «За плечами» reads (A66 §6). [days] are the lived days by their mode; [records] the
 * archive the search and the filters go through; [since] the first day the archive holds.
 */
data class BehindData(
    val today: LocalDate,
    val days: Map<LocalDate, DayRecord> = emptyMap(),
    val records: List<BehindRecord> = emptyList(),
    val since: LocalDate? = null,
    /** «Трудные периоды» may be offered now (§6.5 п. 7). */
    val hardAvailable: Boolean = false,
    /** `ED_CARE`: no count of workouts in the hero (§6.5 п. 7). */
    val edCare: Boolean = false,
) {
    companion object {
        fun empty(today: LocalDate) = BehindData(today)
    }
}

/**
 * «За плечами» from what HoT already stores (A66 §6, D-061), with no Room migration: the modes of
 * the days (`day_plan`, `week_day_score`), the workouts (WHOOP sessions and HoT workouts), the weeks
 * with their goals and the person's words of the Sunday ritual (`week_plan`, `week_goal`), the
 * chapters and milestones of the Path (`path_goal`). WHOOP's own rows are kept 28–35 days
 * (D-057 п. 5): what is older is honestly empty. Safety comes from the one bus, consumer PATH.
 */
class BehindRepository @Inject constructor(
    private val dayDao: DayDao,
    private val weekDao: WeekDao,
    private val trainingDao: TrainingLogDao,
    private val pathDao: PathDao,
    private val plans: DayPlanRepository,
    private val bus: SafetyBus,
    private val clock: Clock,
) {
    private val user = HotDatabase.LOCAL_USER

    private data class Inputs(val goals: List<Pair<PracticalGoal, PathGoalEntity>>, val todayMode: DayMode?, val safety: SafetyState)

    @OptIn(ExperimentalCoroutinesApi::class)
    val data: Flow<BehindData> = combine(
        combine(pathDao.observeGoals(user), pathDao.observeComponents(user)) { goals, parts ->
            val byGoal = parts.groupBy { it.goalId }
            goals.mapNotNull { e -> toGoal(e, byGoal[e.id].orEmpty())?.let { it to e } }
        },
        // a revealed Sunday brings a new week into the archive
        weekDao.observeFrames(user),
        plans.latest,
        bus.state,
    ) { goals, _, plan, safety ->
        val today = LocalDate.now(clock)
        Inputs(goals, plan?.takeIf { it.dayKey == today }?.regime?.toDayMode(), safety)
    }.mapLatest { load(it) }

    private suspend fun load(i: Inputs): BehindData {
        val today = LocalDate.now(clock)
        val days = days(today)
        val records = buildList {
            days.values.filter { it.mode != null }.forEach { d ->
                add(BehindRecord(RecordKind.DAY, d.day, "${RuWords.dated(d.day)} · ${d.mode!!.word}", words = d.mode.word))
            }
            addAll(trainings())
            addAll(weeks(today))
            i.goals.forEach { (g, e) -> addAll(chapter(g, e, today)) }
            addAll(hardWeeks(days.values.toList(), today))
        }
        val since = (days.keys + records.filter { it.kind != RecordKind.CHAPTER && it.kind != RecordKind.MILESTONE }.map { it.day }).minOrNull()
        return BehindData(
            today = today,
            days = days,
            records = records,
            since = since,
            hardAvailable = BehindSafety.hardAvailable(i.safety, i.todayMode),
            edCare = BehindSafety.edCare(i.safety),
        )
    }

    /** The modes of the days: the day plan first, the stored score of the week otherwise. */
    private suspend fun days(today: LocalDate): Map<LocalDate, DayRecord> {
        val plansRows = runCatching { dayDao.plansBefore(user, today.plusDays(1).toString(), MAX_DAYS) }.getOrDefault(emptyList())
        val scores = runCatching { weekDao.scores(user, FIRST_KEY, today.toString()) }.getOrDefault(emptyList())
        val trainingDays = (
            runCatching { dayDao.sessions(user, FIRST_KEY) }.getOrDefault(emptyList()).map { it.dayKey } +
                runCatching { trainingDao.recent(user, MAX_WORKOUTS) }.getOrDefault(emptyList()).map { it.dayKey }
            ).mapNotNull(::parse).toSet()
        val out = mutableMapOf<LocalDate, DayRecord>()
        scores.forEach { s ->
            val d = parse(s.dayKey) ?: return@forEach
            out[d] = DayRecord(d, s.mode?.let { m -> runCatching { DayMode.valueOf(m) }.getOrNull() }, pause = s.sick)
        }
        plansRows.forEach { p ->
            val d = parse(p.dayKey) ?: return@forEach
            val mode = runCatching { Regime.valueOf(p.regime) }.getOrNull()?.toDayMode()
            val sick = DayFlag.SICK.name in p.flags.split(',').map { it.trim() }
            val prev = out[d]
            out[d] = DayRecord(d, mode ?: prev?.mode, pause = sick || prev?.pause == true)
        }
        trainingDays.forEach { d -> out[d] = (out[d] ?: DayRecord(d, null)).copy(training = true) }
        return out.filterKeys { !it.isAfter(today) }
    }

    /** One record per workout; a day with a HoT workout and a WHOOP session of it is counted once. */
    private suspend fun trainings(): List<BehindRecord> {
        val sessions = runCatching { dayDao.sessions(user, FIRST_KEY) }.getOrDefault(emptyList())
        val workouts = runCatching { trainingDao.recent(user, MAX_WORKOUTS) }.getOrDefault(emptyList())
        val own = workouts.mapNotNull { w ->
            val d = parse(w.dayKey) ?: return@mapNotNull null
            BehindRecord(RecordKind.TRAINING, d, BehindCopy.TRAINING_HOT, "${(w.activeS / 60).coerceAtLeast(1)} мин", words = "${w.templateRef} ${w.mode}")
        }
        val ownPerDay = own.groupingBy { it.day }.eachCount()
        val whoop = sessions.groupBy { it.dayKey }.flatMap { (key, list) ->
            val d = parse(key) ?: return@flatMap emptyList()
            list.drop(ownPerDay[d] ?: 0).map { s -> BehindRecord(RecordKind.TRAINING, d, BehindCopy.TRAINING_WHOOP, "${s.durationMin} мин", words = "${s.kind} whoop") }
        }
        return own + whoop
    }

    /** The weeks with goals and the person's own words of their Sunday (notes). */
    private suspend fun weeks(today: LocalDate): List<BehindRecord> {
        val first = runCatching { weekDao.firstPlan(user) }.getOrNull()?.weekStart?.let(::parse) ?: return emptyList()
        val last = CalendarWeek.start(today)
        val out = mutableListOf<BehindRecord>()
        var m = CalendarWeek.start(first)
        var guard = 0
        while (!m.isAfter(last) && guard++ < MAX_WEEKS) {
            val key = m.toString()
            val plan = runCatching { weekDao.plan(user, key) }.getOrNull()
            if (plan != null) {
                val goals = runCatching { weekDao.goals(user, key) }.getOrDefault(emptyList())
                if (goals.isNotEmpty()) {
                    val titles = goals.joinToString(" · ") { it.title }
                    out += BehindRecord(RecordKind.WEEK, m, BehindCopy.week(BehindSafety.weekNumber(m), m), titles, words = titles)
                }
                plan.pride?.trim()?.takeIf { it.isNotEmpty() }?.let { out += BehindRecord(RecordKind.NOTE, m.plusDays(6), BehindCopy.NOTE_PRIDE, it) }
                plan.carry?.trim()?.takeIf { it.isNotEmpty() }?.let { out += BehindRecord(RecordKind.NOTE, m.plusDays(6), BehindCopy.NOTE_CARRY, it) }
            }
            m = m.plusWeeks(1)
        }
        return out
    }

    /** A goal of the Path is a chapter; its milestones already behind are records of their own. */
    private fun chapter(g: PracticalGoal, e: PathGoalEntity, today: LocalDate): List<BehindRecord> {
        if (g.status == GoalStatus.DRAFT || g.status == GoalStatus.QUEUED) return emptyList()
        val ended = e.endedDay?.let(::parse)
        val state = when (g.status) {
            GoalStatus.TAKEN -> BehindCopy.CHAPTER_TAKEN
            GoalStatus.RELEASED -> BehindCopy.released((ChronoUnit.DAYS.between(g.start, ended ?: today) / 7).toInt())
            else -> BehindCopy.CHAPTER_STARTED
        }
        val chapter = BehindRecord(RecordKind.CHAPTER, g.start, BehindCopy.chapter(g.title), state, words = g.why.orEmpty())
        val milestones = StageRules.MILESTONES.mapNotNull { w ->
            val date = StageRules.milestone(g, w, today)
            if (date.isAfter(today) || (ended != null && date.isAfter(ended))) return@mapNotNull null
            BehindRecord(RecordKind.MILESTONE, date, BehindCopy.milestone(PathStage.milestoneNumber(w), g.title), g.criteria.firstOrNull()?.label)
        }
        return listOf(chapter) + milestones
    }

    private fun hardWeeks(days: List<DayRecord>, today: LocalDate): List<BehindRecord> {
        val first = days.minOfOrNull { it.day } ?: return emptyList()
        val out = mutableListOf<BehindRecord>()
        var m = CalendarWeek.start(first)
        while (!m.isAfter(today)) {
            BehindSafety.hardWeek(m, days)?.let(out::add)
            m = m.plusWeeks(1)
        }
        return out
    }

    private fun parse(key: String): LocalDate? = runCatching { LocalDate.parse(key) }.getOrNull()

    private companion object {
        const val FIRST_KEY = "0000-01-01"
        const val MAX_DAYS = 3 * 366
        const val MAX_WORKOUTS = 2_000
        const val MAX_WEEKS = 3 * 53
    }
}

fun Regime.toDayMode(): DayMode = when (this) {
    Regime.BOOST -> DayMode.RAZGON
    Regime.STEADY -> DayMode.ROVNO
    Regime.GENTLE -> DayMode.BEREZHNO
}
