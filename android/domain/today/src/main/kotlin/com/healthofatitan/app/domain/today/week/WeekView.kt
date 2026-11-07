package com.healthofatitan.app.domain.today.week

import com.healthofatitan.app.domain.today.CalendarWeek
import com.healthofatitan.app.domain.today.WeekPlan
import java.time.LocalDate
import java.time.LocalDateTime

/** The ritual «Итог и сборка» as the week knows it (A52 §2.6). */
data class RitualState(
    /** The ritual that closes this week is done. */
    val doneAt: Long? = null,
    val pride: String? = null,
    /** The ritual of the past week is still open (Monday, before the deadline). */
    val pastWeekOpen: Boolean = false,
)

/** Everything «Неделя» needs; pure data, built by the repository from Room and WHOOP memory. */
data class WeekInput(
    val now: LocalDateTime,
    val facts: List<DayFact>,
    val plan: WeekPlan,
    val planStatus: PlanStatus,
    val kind: WeekKind = WeekKind.NORMAL,
    val stormNetWithin7Days: Boolean = false,
    val crisis: Boolean = false,
    val whoopConnected: Boolean = true,
    val supports: SupportsWeekInput = SupportsWeekInput(),
    /** Mean fill of each of the five supports over the days with data, for the Titan's columns. */
    val supportFills: List<Float> = emptyList(),
    val body: List<BodySeries> = emptyList(),
    val patterns: List<Pattern> = emptyList(),
    val weight: WeightInput = WeightInput(),
    val fitnessGate: FitnessGate = FitnessGate(),
    val fitness: FitnessInput? = null,
    val fitnessFourWeeksAgo: Double? = null,
    /** The estimate of the last milestone (A53 §3.3): the number moves only every 4 weeks. */
    val fitnessAtMilestone: Double? = null,
    val fitnessMilestone: Int? = null,
    val fitnessLever: String? = null,
    /** «Эксперимент недели» running this week (A53 §3.2) and the days its action was done. */
    val experiment: Experiment? = null,
    val experimentDays: Set<LocalDate> = emptySet(),
    val edCare: Boolean = false,
    val waistAgeDays: Int? = null,
    val ritual: RitualState = RitualState(),
    /** «Дни в опоре» of the previous week, for «было → стало». */
    val previousDaysInSupport: Int? = null,
    val pathSteps: Int = 0,
    /** Tasks sent back to the week today (W-05): shown as «вернулось в неделю». */
    val returnedToday: Int = 0,
    val sickNow: Boolean = false,
    val focusRecord: Boolean = false,
)

data class WeekView(
    val weekStart: LocalDate,
    val number: Int,
    val columns: List<DayColumn>,
    val daysInSupport: Int,
    val mark: Int,
    val goalsDone: Int,
    val goalsTotal: Int,
    val goals: List<GoalLine>,
    /** «Собрал сам — поправь, если нужно» (W-06), «Предложение…», a first week, a pause. */
    val goalsNote: String?,
    val supports: List<SupportWeekRow>,
    /** A52 §3.4: the phrase of a week of recovery when skews are hidden. */
    val shelterLine: String?,
    val body: BodyWeek,
    val patterns: List<PatternView>,
    val patternsEmpty: String?,
    /** Sunday (and until the deadline for the past week): the card «Итог и сборка · 4 мин». */
    val ritualCard: Boolean,
    val ritualDone: Boolean,
    val titan: TitanWeekLook,
    val fitness: FitnessView?,
    val weekInSupport: Boolean,
    val returnedToday: Int,
    /** The experiment of the week with its scale, or null. */
    val experiment: ExperimentWeek? = null,
    /** «Эксперимента на этой неделе нет — неделя для восстановления» (Бережно, StormNet). */
    val experimentNote: String? = null,
    /** The opt-in card for the fitness age may be offered (flag on, nothing closes it, not chosen yet). */
    val fitnessOffer: Boolean = false,
)

/** The experiment as «Неделя» draws it. */
data class ExperimentWeek(val seed: ExperimentSeed, val scale: ExperimentScale, val doneToday: Boolean)

object WeekBuilder {

    fun build(i: WeekInput): WeekView {
        val today = i.now.toLocalDate()
        val monday = CalendarWeek.start(today)
        val columns = WeekScoring.columns(today, i.facts)
        val days = WeekScoring.daysInSupport(columns)
        val (done, total) = WeekGoalRules.totals(i.plan.goals)
        val gentle = WeekScoring.gentleWeek(columns)
        val shelter = Shelter(gentleWeek = gentle, returnWeek = i.kind == WeekKind.RETURN, stormNetWithin7Days = i.stormNetWithin7Days)
        val daysSoFar = columns.count { !it.future }
        val status = WeekGoalRules.status(i.planStatus, monday, i.now)
        val goalsNote = when {
            i.plan.goals.isEmpty() -> WeekCopy.GOAL_EMPTY
            i.sickNow -> WeekCopy.GOAL_PAUSED
            i.kind == WeekKind.FIRST -> WeekCopy.GOAL_FIRST_WEEK
            status == PlanStatus.AUTO -> WeekCopy.GOAL_AUTO
            status == PlanStatus.PROPOSED -> WeekCopy.GOAL_PROPOSED
            else -> null
        }
        val patterns = PatternRules.select(i.patterns)
        val ritualOpen = WeekGoalRules.ritualFor(i.now) != null
        val expGate = ExperimentGate(gentleWeek = gentle, stormNet = i.stormNetWithin7Days, edCare = i.edCare, crisis = i.crisis)
        val sunday = today.dayOfWeek == java.time.DayOfWeek.SUNDAY
        val titan = TitanOfWeek.look(
            TitanWeekInput(
                weekStart = monday,
                today = today,
                columns = columns,
                supports = i.supportFills,
                sleepDebtHours = i.supports.sleepDebtHours,
                regularityPct = i.supports.regularityPct,
                goalsAllDone = i.plan.goals.isNotEmpty() && i.plan.goals.all { it.progress >= it.target },
                focusRecord = i.focusRecord,
                zoneMinutes = i.supports.zoneMinutes,
                pride = !i.ritual.pride.isNullOrBlank(),
                ritualDone = sunday && i.ritual.doneAt != null,
            ),
        )
        return WeekView(
            weekStart = monday,
            number = WeekScoring.number(monday),
            columns = columns,
            daysInSupport = days,
            mark = WeekScoring.mark(i.kind),
            goalsDone = done,
            goalsTotal = total,
            goals = i.plan.goals.map(WeekGoalRules::line),
            goalsNote = goalsNote,
            supports = WeekSupports.rows(i.supports, shelter, i.weight, daysSoFar),
            shelterLine = if (gentle) WeekCopy.SKEW_HIDDEN else null,
            body = if (i.whoopConnected) BodyRules.build(i.body) else BodyWeek(emptyList(), redFlag = false, empty = true),
            patterns = patterns,
            patternsEmpty = if (patterns.isEmpty()) WeekCopy.PATTERN_EMPTY else null,
            ritualCard = ritualOpen && ((sunday && i.ritual.doneAt == null) || (!sunday && i.ritual.pastWeekOpen)),
            ritualDone = sunday && i.ritual.doneAt != null,
            titan = titan,
            fitness = i.fitness?.let {
                FitnessAge.view(i.fitnessGate, it, i.fitnessFourWeeksAgo, i.waistAgeDays, i.fitnessAtMilestone, i.fitnessMilestone, i.fitnessLever)
            },
            weekInSupport = WeekScoring.weekInSupport(days, i.pathSteps),
            returnedToday = i.returnedToday,
            experiment = i.experiment?.takeIf { it.status == ExperimentStatus.TAKEN && expGate.open && !(i.edCare && it.seed.food) }?.let {
                ExperimentWeek(it.seed, ExperimentRules.scale(monday, today, i.experimentDays), today in i.experimentDays)
            },
            experimentNote = if (!expGate.open && i.experiment?.status == ExperimentStatus.TAKEN) WeekCopy.EXP_NONE else null,
            fitnessOffer = i.fitnessGate.offerable && !i.fitnessGate.optedIn,
        )
    }
}
