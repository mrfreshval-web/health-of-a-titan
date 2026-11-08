package com.healthofatitan.app.integration.whoop.day

import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.DayDao
import com.healthofatitan.app.core.database.dao.MetricDao
import com.healthofatitan.app.core.database.entity.DayPlanEntity
import com.healthofatitan.app.core.database.entity.EveningCheckEntity
import com.healthofatitan.app.core.database.entity.MetricDailyEntity
import com.healthofatitan.app.core.database.entity.PlanItemEntity
import com.healthofatitan.app.core.model.profile.AddressForm
import com.healthofatitan.app.domain.engine.Anchors
import com.healthofatitan.app.domain.engine.Body
import com.healthofatitan.app.domain.engine.ContextFacts
import com.healthofatitan.app.domain.engine.DataFacts
import com.healthofatitan.app.domain.engine.DayClose
import com.healthofatitan.app.domain.engine.DayEngine
import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.DayInput
import com.healthofatitan.app.domain.engine.DayPlan
import com.healthofatitan.app.domain.engine.Evening
import com.healthofatitan.app.domain.engine.EveningFacts
import com.healthofatitan.app.domain.engine.FiredRule
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.NightFacts
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.engine.RuleRef
import com.healthofatitan.app.domain.engine.SelfReport
import com.healthofatitan.app.domain.engine.WorkoutMatcher
import com.healthofatitan.app.domain.engine.YesterdayFacts
import com.healthofatitan.app.domain.metrics.MetricRef
import com.healthofatitan.app.domain.metrics.Metrics
import com.healthofatitan.app.integration.whoop.RoutineStore
import com.healthofatitan.app.integration.whoop.WhoopSyncer
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    /** `ruleId@ver` that made the item (A09 §6): ACT-01 / RET-01 mark a walk (A35 §4 steps). */
    val rule: RuleRef? = null,
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
    /** RET-01: silence before today (A18 И4.4); null in tests that do not need it. */
    private val loop: DailyLoopRepository? = null,
    /** A44 §5: the wake deck (Заряд → SG-SELF energy, morning body → SAF-03 / EVE-01). */
    private val morningSelf: MorningSelfSource? = null,
    /** A61 §4.1, D-055: the address form of the profile «Я»; neutral until the person chose. */
    private val address: suspend () -> AddressForm = { AddressForm.NEUTRAL },
    /** S2: WHOOP is connected. Without it there is no night to wait for: the MANUAL day comes at once. */
    private val whoopConnected: suspend () -> Boolean = { true },
) {
    private val user = HotDatabase.LOCAL_USER
    private val mutex = Mutex()

    /**
     * Builds or rebuilds today's plan. Until [MANUAL_FROM] it waits (returns null) while WHOOP
     * has not scored last night, or has scored the sleep but not yet the recovery: the plan and
     * the push are made from data, never from a guess (D-027). From 09:30 a night that is still
     * missing gives a default day with the MANUAL flag (A09 REG-05), rebuilt once WHOOP catches up.
     * Without WHOOP (S2) nothing is awaited: the MANUAL day (Ровно at most) is built at once.
     */
    suspend fun refresh(): DayPlan? = mutex.withLock {
        val now = clock.instant()
        val today = LocalDate.now(clock)
        val yesterday = today.minusDays(1)
        val whoop = runCatching { whoopConnected() }.getOrDefault(true)
        val early = whoop && LocalTime.now(clock).isBefore(MANUAL_FROM)
        val rows = metricDao.metricsForDays(user, listOf(today.toString(), yesterday.toString()))
        fun row(day: LocalDate, ref: MetricRef): MetricDailyEntity? =
            rows.firstOrNull { it.dayKey == day.toString() && it.metricId == ref.id && it.metricVersion == ref.version }

        val sleep = row(today, Metrics.SLP)?.value
        if (sleep == null && early) return@withLock null
        val ors = row(today, Metrics.ORS)
        val baselineNights = metricDao.syncState(WhoopSyncer.SOURCE)?.baselineNights ?: 0
        // Sleep scored, recovery not yet: wait for it rather than push a «data limited» day.
        if (sleep != null && early && baselineNights >= MIN_BASELINE && (ors?.value == null)) return@withLock null
        val lrbSeries = metricDao.series(user, Metrics.LRB.id, Metrics.LRB.version, today.minusDays(14).toString())
        var lrbAbove = 0
        for (r in lrbSeries.sortedByDescending { it.dayKey }) {
            if ((r.value ?: break) > 1.0) lrbAbove++ else break
        }
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
        val sickRun = streak { DayFlag.SICK.name in it.flags.split(',') }
        // SAF-03 see_doctor: nights in a row with resting HR ≥ +2 SD (only the z is stored).
        val rhrSeries = metricDao.series(user, Metrics.RHR_Z.id, Metrics.RHR_Z.version, today.minusDays(7).toString())
        var rhrHigh = 0
        for (r in rhrSeries.sortedByDescending { it.dayKey }) {
            if ((r.value ?: break) >= 2.0) rhrHigh++ else break
        }

        val self = morningSelf?.let { runCatching { it.of(today) }.getOrNull() }
        val evening = dayDao.evening(user, yesterday.toString())?.let { e ->
            EveningFacts(e.alcohol, e.body?.let { b -> Body.entries.firstOrNull { it.name == b } })
        }.withMorningBody(self?.body)
        val anchors = RoutineStore.fromRows(dayDao.anchors(user))
        val alarm = alarms.nextAlarm()?.atZone(clock.zone)?.toLocalDateTime()
        val schedule = Anchors.resolve(anchors, today, alarm)
        val phoneOffset = clock.zone.rules.getOffset(now).totalSeconds / 60
        val tzShift = anchors.homeTzOffsetMin?.let { (phoneOffset - it) / 60.0 } ?: 0.0
        val tzDays = Anchors.daysSinceShift(anchors, phoneOffset)
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
                rhrZ = row(today, Metrics.RHR_Z)?.value,
                rrZ = row(today, Metrics.RR_Z)?.value,
                rhrZStreakGe2 = rhrHigh,
            ),
            data = DataFacts(baselineNights = baselineNights, historyDays = historyDays, coverage = ors?.coverage ?: 0.0, whoopToday = sleep != null, whoopConnected = whoop),
            yesterday = YesterdayFacts(
                regime = yPlan?.let { p -> Regime.entries.firstOrNull { it.name == p.regime } },
                signals = yPlan?.signals?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty(),
                gentleStreak = streak { it.regime == Regime.GENTLE.name },
                lightWeekDay = if (lightRun in 1..6) lightRun else 0,
                sickStreak = sickRun,
            ),
            evening = evening,
            self = SelfReport(energy = self?.energy),
            context = ContextFacts(tzShift, tzDays),
            schedule = schedule,
            history = sessions.filter { it.dayKey < today },
            silenceDays = loop?.silenceDays(today) ?: 0,
            address = runCatching { address() }.getOrDefault(AddressForm.NEUTRAL),
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

    /** Today's stored plan (built from data, or the MANUAL default from 09:30). */
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

    /**
     * «Закрыть день» (A18 И4.1): every answer may be empty; the training answer lights the plan
     * item («сделал» → отмечено тобой, «изменил» → изменено), never undoes what WHOOP confirmed.
     */
    suspend fun closeDay(day: LocalDate, close: DayClose) {
        val key = day.toString()
        dayDao.upsertEvening(
            EveningCheckEntity(
                userId = user,
                dayKey = key,
                alcohol = close.alcohol,
                body = close.body?.name,
                answeredAt = clock.millis(),
                training = close.training?.name,
                food = close.food?.name,
                planHelped = close.planHelped,
            ),
        )
        val item = dayDao.items(user, key).firstOrNull { it.itemId == DayEngine.Ids.TRAINING } ?: return
        val current = ItemStatus.entries.firstOrNull { it.name == item.status } ?: ItemStatus.OPEN
        val next = Evening.trainingStatus(current, close.training)
        if (next != current) dayDao.setItemStatus(user, key, item.itemId, next.name, clock.millis())
    }

    companion object {
        /** A09 REG-05: no scored night by this time → the default day (MANUAL). */
        val MANUAL_FROM: LocalTime = LocalTime.of(9, 30)
        const val MIN_BASELINE = 14
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
                rule = RuleRef.parse(e.ruleRef),
            )
        },
        bedtimeMin = p.bedtimeMin,
        caffeineCutoffMin = p.caffeineCutoffMin,
        windDownMin = p.windDownMin,
    )
}

/**
 * What the wake deck of today tells the plan (A44 §5): `charge@1` → `SelfReport.energy`
 * (2 / 4 / 6 / 8 / 10) and the morning body, used only when the evening did not mark it.
 */
data class MorningSelf(val energy: Int? = null, val body: Body? = null)

fun interface MorningSelfSource {
    suspend fun of(day: LocalDate): MorningSelf?
}

/** The evening answer wins; the morning «простуда» / «забито» fills an empty evening (A44 §5 П3). */
internal fun EveningFacts?.withMorningBody(morning: Body?): EveningFacts? = when {
    morning == null -> this
    this == null -> EveningFacts(body = morning)
    body == null -> copy(body = morning)
    else -> this
}
