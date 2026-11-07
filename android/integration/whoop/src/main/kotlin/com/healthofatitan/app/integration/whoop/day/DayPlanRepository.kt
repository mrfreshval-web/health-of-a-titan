package com.healthofatitan.app.integration.whoop.day

import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.DayDao
import com.healthofatitan.app.core.database.dao.MetricDao
import com.healthofatitan.app.core.database.entity.DayPlanEntity
import com.healthofatitan.app.core.database.entity.EveningCheckEntity
import com.healthofatitan.app.core.database.entity.MetricDailyEntity
import com.healthofatitan.app.core.database.entity.PlanItemEntity
import com.healthofatitan.app.domain.engine.Anchors
import com.healthofatitan.app.domain.engine.Body
import com.healthofatitan.app.domain.engine.ContextFacts
import com.healthofatitan.app.domain.engine.DataFacts
import com.healthofatitan.app.domain.engine.DayEngine
import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.DayInput
import com.healthofatitan.app.domain.engine.DayPlan
import com.healthofatitan.app.domain.engine.EveningFacts
import com.healthofatitan.app.domain.engine.FiredRule
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.NightFacts
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.engine.RuleRef
import com.healthofatitan.app.domain.engine.WorkoutMatcher
import com.healthofatitan.app.domain.engine.YesterdayFacts
import com.healthofatitan.app.domain.metrics.MetricRef
import com.healthofatitan.app.domain.metrics.Metrics
import com.healthofatitan.app.integration.whoop.RoutineStore
import com.healthofatitan.app.integration.whoop.WhoopSyncer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The system clock in the phone's **current** time zone: after a flight the day is built by
 * local time (CTX-04), not in the zone the process started in.
 */
object SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()
}

/** The phone's next alarm (A09 §7a: `AlarmManager.getNextAlarmClock()`, no permission needed). */
fun interface NextAlarmSource {
    fun nextAlarm(): Instant?
}

/** One plan item as «Сегодня» shows it. */
data class DayItemView(
    val id: String,
    val kind: ItemKind,
    val title: String,
    val detail: String?,
    val startMin: Int?,
    val endMin: Int?,
    val status: ItemStatus,
)

/** The stored day as «Сегодня» shows it (never recomputed for past days, A09 §6). */
data class DayView(
    val dayKey: LocalDate,
    val regime: Regime,
    val flags: Set<DayFlag>,
    val headline: String,
    val summary: String,
    val fired: List<FiredRule>,
    val items: List<DayItemView>,
    val bedtimeMin: Int,
    val caffeineCutoffMin: Int,
    val windDownMin: Int,
)

/**
 * The day plan from stored HoT data (slice 2): reads the derived metrics of today and yesterday,
 * the routine anchors, the WHOOP sessions, yesterday's plan and the evening deck, runs the pure
 * A09 engine ([DayEngine]) and stores `DayState` + `PlanItem`. Only today is ever rebuilt (the
 * phone alarm or a new WHOOP workout may change it); the user's marks are carried over.
 */
class DayPlanRepository(
    private val metricDao: MetricDao,
    private val dayDao: DayDao,
    private val alarms: NextAlarmSource,
    private val clock: Clock,
) {
    private val user = HotDatabase.LOCAL_USER
    private val mutex = Mutex()

    /**
     * Builds or rebuilds today's plan. Returns null while WHOOP has not scored last night: the
     * plan is made from data, never from a guess (D-027).
     */
    suspend fun refresh(): DayPlan? = mutex.withLock {
        val now = clock.instant()
        val today = LocalDate.now(clock)
        val yesterday = today.minusDays(1)
        val rows = metricDao.metricsForDays(user, listOf(today.toString(), yesterday.toString()))
        fun row(day: LocalDate, ref: MetricRef): MetricDailyEntity? =
            rows.firstOrNull { it.dayKey == day.toString() && it.metricId == ref.id && it.metricVersion == ref.version }

        val sleep = row(today, Metrics.SLP)?.value ?: return@withLock null
        val ors = row(today, Metrics.ORS)
        val lrbSeries = metricDao.series(user, Metrics.LRB.id, Metrics.LRB.version, today.minusDays(14).toString())
        var lrbAbove = 0
        for (r in lrbSeries.sortedByDescending { it.dayKey }) {
            if ((r.value ?: break) > 1.0) lrbAbove++ else break
        }
        val baselineNights = metricDao.syncState(WhoopSyncer.SOURCE)?.baselineNights ?: 0
        val firstDay = metricDao.firstDay(user, Metrics.SLP.id)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
        val historyDays = ChronoUnit.DAYS.between(firstDay, today).toInt() + 1

        val previous = dayDao.plansBefore(user, today.toString(), 14)
        fun streak(test: (DayPlanEntity) -> Boolean): Int {
            var n = 0
            for (p in previous) {
                if (p.dayKey != today.minusDays(n + 1L).toString() || !test(p)) break
                n++
            }
            return n
        }
        val yPlan = previous.firstOrNull { it.dayKey == yesterday.toString() }
        val lightRun = streak { DayFlag.LIGHT_WEEK.name in it.flags.split(',') }

        val evening = dayDao.evening(user, yesterday.toString())?.let { e ->
            EveningFacts(e.alcohol, e.body?.let { b -> Body.entries.firstOrNull { it.name == b } })
        }
        val anchors = RoutineStore.fromRows(dayDao.anchors(user))
        val alarm = alarms.nextAlarm()?.atZone(clock.zone)?.toLocalDateTime()
        val schedule = Anchors.resolve(anchors, today, alarm)
        val phoneOffset = clock.zone.rules.getOffset(now).totalSeconds / 60
        val tzShift = anchors.homeTzOffsetMin?.let { (phoneOffset - it) / 60.0 } ?: 0.0
        val sessions = dayDao.sessions(user, today.minusDays(28).toString()).mapNotNull(RoutineStore::fromRow)

        val input = DayInput(
            dayKey = today,
            night = NightFacts(
                orsZ = ors?.value,
                orsEwma7 = ors?.ewma7,
                sleepHours = sleep,
                prevSleepHours = row(yesterday, Metrics.SLP)?.value,
                sleepDebt7h = row(today, Metrics.SLP_DEBT)?.value,
                lrb = row(today, Metrics.LRB)?.value,
                lrbDaysAbove1 = lrbAbove,
                trlZYesterday = row(yesterday, Metrics.TRL)?.z,
                chargeLevel = row(today, Metrics.ECAP)?.level,
            ),
            data = DataFacts(baselineNights = baselineNights, historyDays = historyDays, coverage = ors?.coverage ?: 0.0, whoopToday = true),
            yesterday = YesterdayFacts(
                regime = yPlan?.let { p -> Regime.entries.firstOrNull { it.name == p.regime } },
                signals = yPlan?.signals?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty(),
                gentleStreak = streak { it.regime == Regime.GENTLE.name },
                lightWeekDay = if (lightRun in 1..6) lightRun else 0,
            ),
            evening = evening,
            context = ContextFacts(tzShift),
            schedule = schedule,
            history = sessions.filter { it.dayKey < today },
        )
        val plan = DayEngine.plan(input)

        val verification = WorkoutMatcher.verify(plan.training, sessions.filter { it.dayKey == today })
        val existing = dayDao.items(user, today.toString()).associateBy { it.itemId }
        val stored = dayDao.plan(user, today.toString())
        val at = now.toEpochMilli()
        val items = plan.items.mapIndexed { i, item ->
            val old = existing[item.id]
            val oldStatus = old?.status?.let { s -> ItemStatus.entries.firstOrNull { it.name == s } } ?: ItemStatus.OPEN
            val fromData = if (item.id == DayEngine.Ids.TRAINING) verification else null
            val status = WorkoutMatcher.merge(oldStatus, fromData)
            PlanItemEntity(
                userId = user,
                dayKey = today.toString(),
                itemId = item.id,
                ruleRef = item.rule.toString(),
                kind = item.kind.name,
                slotStart = item.startMin,
                slotEnd = item.endMin,
                title = item.title,
                detail = item.detail,
                sortOrder = i,
                status = status.name,
                statusAt = if (status == oldStatus) old?.statusAt else at,
                sessionRef = fromData?.sessionId ?: old?.sessionRef,
            )
        }
        dayDao.savePlan(
            DayPlanEntity(
                userId = user,
                dayKey = today.toString(),
                regime = plan.regime.name,
                regimeComputed = plan.regimeComputed.name,
                points = plan.points,
                signals = plan.signals.joinToString(","),
                flags = plan.flags.joinToString(",") { it.name },
                firedRules = plan.fired.joinToString("\n") { "${it.ref}\t${it.why}" },
                ruleSet = plan.ruleSet,
                headline = plan.headline,
                summary = plan.summary,
                bedtimeMin = plan.bedtimeMin,
                caffeineCutoffMin = plan.caffeineCutoffMin,
                windDownMin = plan.windDownMin,
                builtAt = at,
                notifiedAt = stored?.notifiedAt,
            ),
            items,
        )
        plan
    }

    /** Today's stored plan, if WHOOP has scored the night. */
    suspend fun planFor(day: LocalDate): DayPlanEntity? = dayDao.plan(user, day.toString())

    suspend fun markNotified(day: LocalDate) = dayDao.markNotified(user, day.toString(), clock.millis())

    @OptIn(ExperimentalCoroutinesApi::class)
    val latest: Flow<DayView?> = dayDao.observeLatestPlan(user).flatMapLatest { p ->
        if (p == null) flowOf(null) else dayDao.observeItems(user, p.dayKey).map { items -> toView(p, items) }
    }

    val latestEvening: Flow<EveningCheckEntity?> = dayDao.observeLatestEvening(user)

    /** A tap: «отмечено тобой» ⇄ open. A status from WHOOP is not undone by a tap (A35 §4). */
    suspend fun toggle(day: LocalDate, itemId: String) {
        val item = dayDao.items(user, day.toString()).firstOrNull { it.itemId == itemId } ?: return
        val next = when (item.status) {
            ItemStatus.OPEN.name -> ItemStatus.DONE_SELF
            ItemStatus.DONE_SELF.name -> ItemStatus.OPEN
            else -> return
        }
        dayDao.setItemStatus(user, day.toString(), itemId, next.name, clock.millis())
    }

    /** The evening deck: a skipped card is null, never 0 (A25 §2.1). */
    suspend fun saveEvening(day: LocalDate, alcohol: Int?, body: Body?) {
        dayDao.upsertEvening(EveningCheckEntity(user, day.toString(), alcohol, body?.name, clock.millis()))
    }

    private fun toView(p: DayPlanEntity, items: List<PlanItemEntity>) = DayView(
        dayKey = LocalDate.parse(p.dayKey),
        regime = Regime.entries.firstOrNull { it.name == p.regime } ?: Regime.STEADY,
        flags = p.flags.split(',').mapNotNull { f -> DayFlag.entries.firstOrNull { it.name == f } }.toSet(),
        headline = p.headline,
        summary = p.summary,
        fired = p.firedRules.lines().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@mapNotNull null
            RuleRef.parse(line.substring(0, tab))?.let { FiredRule(it, line.substring(tab + 1)) }
        },
        items = items.map { e ->
            DayItemView(
                id = e.itemId,
                kind = ItemKind.entries.firstOrNull { it.name == e.kind } ?: ItemKind.WORK,
                title = e.title,
                detail = e.detail,
                startMin = e.slotStart,
                endMin = e.slotEnd,
                status = ItemStatus.entries.firstOrNull { it.name == e.status } ?: ItemStatus.OPEN,
            )
        },
        bedtimeMin = p.bedtimeMin,
        caffeineCutoffMin = p.caffeineCutoffMin,
        windDownMin = p.windDownMin,
    )
}
