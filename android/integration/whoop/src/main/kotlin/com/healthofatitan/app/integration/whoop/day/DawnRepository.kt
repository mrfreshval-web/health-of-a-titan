package com.healthofatitan.app.integration.whoop.day

import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.DawnDao
import com.healthofatitan.app.core.database.dao.MetricDao
import com.healthofatitan.app.core.database.entity.DawnLogEntity
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.metrics.MetricRef
import com.healthofatitan.app.domain.metrics.Metrics
import com.healthofatitan.app.integration.whoop.WhoopSyncer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Crisis signals of the day (A07 CrisisGuard: CARE / EMERGENCY flags). While they are on, the
 * morning has no haiku (A37 §4, STYLE.md §6.3): the crisis card speaks instead. The source is
 * [DailyLoopRepository]: a CrisisGuard level ≥ 2 in the last 72 hours.
 */
fun interface CrisisSignals {
    fun active(): Boolean

    companion object {
        val None = CrisisSignals { false }
    }
}

/** What the «Рассвет» screen shows. Numbers are HoT-derived values, never raw WHOOP data. */
data class DawnView(
    val day: LocalDate,
    val regime: Regime,
    val haiku: Haiku?,
    val sleepHours: Double?,
    val charge: Int?,
    val strainCeiling: Double,
    /** Time of the last WHOOP sync, for «WHOOP · 06:41». */
    val syncedAt: Long?,
    /** 0…1: how high the sun-ring stands, from the night's recovery (Заряд), within the mode. */
    val sunHeight: Float,
)

/**
 * «Рассвет» (A37 §4, wow 2): once a day, at the first open after 05:00 when today's plan is
 * ready. The first show is logged in `dawn_log` together with the haiku, so the screen never
 * comes back the same day and a haiku never repeats.
 */
class DawnRepository(
    private val metricDao: MetricDao,
    private val dawnDao: DawnDao,
    private val plans: DayPlanRepository,
    private val library: () -> List<Haiku>,
    private val crisis: CrisisSignals,
    private val clock: Clock,
) {
    private val user = HotDatabase.LOCAL_USER
    private val mutex = Mutex()
    private var cache: Pair<LocalDate, DawnView>? = null

    /** True while today's dawn is due: after 05:00, the plan for today exists, not shown yet. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val due: Flow<Boolean> = minuteTicks().flatMapLatest { now ->
        val today = now.toLocalDate()
        combine(plans.latest, dawnDao.observeDay(user, today.toString())) { plan, shown ->
            DawnPolicy.due(now, plan?.dayKey, shown != null)
        }
    }

    /** «Рассвет» was passed (or offered) on [day]: its row exists in `dawn_log`. */
    fun shown(day: LocalDate): Flow<Boolean> = dawnDao.observeDay(user, day.toString()).map { it != null }

    /**
     * The haiku of the day for «Я сегодня» (A46 §2.2: all day after «Рассвет»), read only: it never
     * picks or logs a haiku. Null before the dawn and when none fitted.
     */
    fun shownHaiku(day: LocalDate): Flow<List<String>?> = dawnDao.observeDay(user, day.toString()).map { row ->
        row?.haikuId?.let { id -> library().firstOrNull { it.id == id }?.lines }
    }

    /** Today's dawn: the haiku is chosen once and logged right away (the first show wins). */
    suspend fun today(): DawnView? = mutex.withLock {
        val today = LocalDate.now(clock)
        cache?.takeIf { it.first == today }?.let { return@withLock it.second }
        val plan = plans.planFor(today) ?: return@withLock null
        val regime = Regime.entries.firstOrNull { it.name == plan.regime } ?: Regime.STEADY
        val rows = metricDao.metricsForDays(user, listOf(today.toString()))
        fun value(ref: MetricRef) = rows.firstOrNull { it.metricId == ref.id && it.metricVersion == ref.version }?.value
        val flags = plan.flags.split(',').mapNotNull { f -> com.healthofatitan.app.domain.engine.DayFlag.entries.firstOrNull { it.name == f } }.toSet()
        val facts = DawnFacts(
            day = today,
            regime = regime,
            flags = flags,
            sleepHours = value(Metrics.SLP),
            orsZ = value(Metrics.ORS),
            lrb = value(Metrics.LRB),
            crisis = crisis.active(),
        )
        val logged = dawnDao.day(user, today.toString())
        val haiku = if (logged != null) {
            logged.haikuId?.let { id -> library().firstOrNull { it.id == id } }
        } else {
            val history = dawnDao.history(user).map {
                ShownHaiku(LocalDate.parse(it.dayKey), it.haikuId, it.textKey, it.image)
            }
            HaikuPicker.pick(library(), facts, history, user).also { h ->
                dawnDao.insert(
                    DawnLogEntity(user, today.toString(), h?.id, h?.text?.let(HaikuPicker::textKey), h?.image, regime.name, clock.millis()),
                )
            }
        }
        val charge = value(Metrics.ECAP)
        val view = DawnView(
            day = today,
            regime = regime,
            haiku = if (facts.crisis) null else haiku,
            sleepHours = facts.sleepHours,
            charge = charge?.let { Math.round(it).toInt() },
            strainCeiling = StrainCeiling.of(regime),
            syncedAt = metricDao.syncState(WhoopSyncer.SOURCE)?.lastSuccessAt,
            sunHeight = DawnPolicy.sunHeight(regime, charge),
        )
        cache = today to view
        view
    }

    private fun minuteTicks(): Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now(clock).withSecond(0).withNano(0))
            delay(60_000L)
        }
    }
}

/** When the dawn shows and how high its sun stands. Pure, unit-tested. */
object DawnPolicy {
    const val FROM_HOUR = 5

    fun due(now: LocalDateTime, planDay: LocalDate?, shownToday: Boolean): Boolean =
        now.hour >= FROM_HOUR && planDay == now.toLocalDate() && !shownToday

    /**
     * The sun-ring's height: the mode sets the band (Разгон above the horizon, Ровно half,
     * Бережно at it, A37 §4) and Заряд (5–95, 50 = your norm) moves it inside the band.
     */
    fun sunHeight(regime: Regime, charge: Double?): Float {
        val (lo, hi) = when (regime) {
            Regime.BOOST -> 0.75f to 1f
            Regime.STEADY -> 0.4f to 0.7f
            Regime.GENTLE -> 0f to 0.3f
        }
        val k = (((charge ?: 50.0) - 5.0) / 90.0).toFloat().coerceIn(0f, 1f)
        return lo + (hi - lo) * k
    }
}
