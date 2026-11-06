package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.MetricDao
import com.healthofatitan.app.core.database.entity.MetricDailyEntity
import com.healthofatitan.app.domain.metrics.MetricRef
import com.healthofatitan.app.domain.metrics.Metrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/** A stored derived value, as «Сегодня» shows it. */
data class FacetValue(val value: Double?, val z: Double?, val level: Int?, val coverage: Double)

/** The latest HoT day: only derived values (S3), never raw WHOOP. */
data class TodayMetrics(
    val dayKey: LocalDate?,
    val charge: FacetValue?, // ECAP «Заряд», 0–100, 50 = your norm
    val balance: FacetValue?, // LRB «Весы», z units
    val recovery: FacetValue?, // ORS
    val sleepHours: FacetValue?, // SLP
    val sleepDebt: FacetValue?, // SLPDEBT, hours
) {
    companion object {
        val EMPTY = TodayMetrics(null, null, null, null, null, null)
    }
}

class TodayMetricsRepository @Inject constructor(private val dao: MetricDao) {

    val latest: Flow<TodayMetrics> = dao.observeLatestDay(HotDatabase.LOCAL_USER, Metrics.SLP.id).map { rows ->
        if (rows.isEmpty()) return@map TodayMetrics.EMPTY
        fun pick(ref: MetricRef): FacetValue? = rows
            .firstOrNull { it.metricId == ref.id && it.metricVersion == ref.version }
            ?.toFacet()
        TodayMetrics(
            dayKey = runCatching { LocalDate.parse(rows.first().dayKey) }.getOrNull(),
            charge = pick(Metrics.ECAP),
            balance = pick(Metrics.LRB),
            recovery = pick(Metrics.ORS),
            sleepHours = pick(Metrics.SLP),
            sleepDebt = pick(Metrics.SLP_DEBT),
        )
    }

    private fun MetricDailyEntity.toFacet() = FacetValue(value, z, level, coverage)
}
