package com.healthofatitan.app.domain.metrics

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/**
 * Slice 1 of the HoT state language (A25 v0.2 §1, §4; A14 §9 slice 1): the personal norm and
 * the metrics ORS, SLP, SLPDEBT, TRL, ECAP and LRB, computed from a window of WHOOP days that
 * lives only in memory. Output is what may be stored: `MetricDaily` and `BaselineSnapshot`.
 *
 * Fixed by A25: robust z with 1.4826·MAD over 28 days, clamp ±3, minimum 14 values (P1); EWMA
 * 7 days (P2); «no data» below 60 % of input weight (P5); weights of ORS, ECAP and LRB; scale of
 * the facets 50 + 15·Z, clamped 5–95; no Recovery %, SpO₂, skin temperature or stages (P7).
 *
 * Assumptions of slice 1 (marked for review by Agent 3, ⚑3):
 *  - level −2…+2: |z| < 0.5 → 0 (within the smallest worthwhile change), < 1.5 → ±1, else ±2;
 *  - missing inputs are renormalised over the available weight (if coverage ≥ 60 %);
 *  - SLP = hours of sleep against the personal norm; the 7 h target lives in SLPDEBT;
 *  - TRL = Edwards TRIMP (Σ minutes in HR zone k · k, zones 1–5); a worn day without workouts = 0;
 *  - z₇ in LRB = EWMA over 7 days of the daily load and recovery composites; until cards and
 *    labels exist (slice 2) WLD, SWB and CLR are absent, so ECAP rests on ORS + SLP (60 %).
 */
object MetricEngine {

    const val BASELINE_DAYS = 28L
    const val MIN_BASELINE_N = 14
    const val MIN_COVERAGE = 0.6
    const val SLEEP_TARGET_H = 7.0
    const val SLEEP_DEBT_CAP_H = 10.0
    const val EWMA_SPAN = 7
    private const val EPS = 1e-9

    fun compute(days: List<DayInputs>): MetricResult {
        val sorted = days.sortedBy { it.dayKey }.distinctBy { it.dayKey }
        if (sorted.isEmpty()) return MetricResult(emptyList(), emptyList(), 0)

        fun window(day: LocalDate, pick: (DayInputs) -> Double?): List<Double> {
            val from = day.minusDays(BASELINE_DAYS)
            return sorted.asSequence()
                .filter { it.dayKey >= from && it.dayKey < day }
                .mapNotNull(pick)
                .toList()
        }

        val inputs: Map<BaselineInput, (DayInputs) -> Double?> = mapOf(
            BaselineInput.LN_RMSSD to { it.lnRmssd },
            BaselineInput.RHR to { it.rhr },
            BaselineInput.RR to { it.rr },
            BaselineInput.SLEEP_HOURS to { it.sleepHours },
            BaselineInput.SLEEP_MID to { it.sleepMidMin },
            BaselineInput.TRL to { it.trimp },
        )

        val baselines = mutableListOf<BaselineValue>()
        val ors = ArrayList<Pair<Double?, Double>>() // value, coverage
        val slp = ArrayList<Pair<Double?, Double?>>() // hours, z
        val trl = ArrayList<Pair<Double?, Double?>>() // trimp, z
        val debt = ArrayList<Pair<Double?, Double>>()
        val rhrZ = ArrayList<Double?>() // A09 SAF-04: z of resting HR, derived only
        val rrZ = ArrayList<Double?>() // A09 SAF-04: z of respiratory rate, derived only

        sorted.forEach { d ->
            val windows = inputs.mapValues { (_, pick) -> window(d.dayKey, pick) }
            windows.forEach { (input, values) ->
                baselines += BaselineValue(d.dayKey, input, Stats.median(values), Stats.mad(values), values.size)
            }
            fun z(input: BaselineInput, x: Double?) = x?.let { Stats.robustZ(it, windows.getValue(input), MIN_BASELINE_N) }

            // ORS: 0.5·z(lnRMSSD) − 0.3·z(RHR) − 0.2·z(RR)
            ors += weighted(listOf(0.5 to z(BaselineInput.LN_RMSSD, d.lnRmssd), 0.3 to z(BaselineInput.RHR, d.rhr)?.unaryMinus(), 0.2 to z(BaselineInput.RR, d.rr)?.unaryMinus()), total = 1.0)
            slp += d.sleepHours to z(BaselineInput.SLEEP_HOURS, d.sleepHours)
            rhrZ += z(BaselineInput.RHR, d.rhr)
            rrZ += z(BaselineInput.RR, d.rr)
            trl += d.trimp to z(BaselineInput.TRL, d.trimp)

            val week = sorted.filter { it.dayKey > d.dayKey.minusDays(7) && it.dayKey <= d.dayKey }.mapNotNull { it.sleepHours }
            val weekCoverage = week.size / 7.0
            debt += (if (weekCoverage + EPS >= MIN_COVERAGE) min(SLEEP_DEBT_CAP_H, week.sumOf { max(0.0, SLEEP_TARGET_H - it) }) else null) to weekCoverage
        }

        // ECAP: 0.35·ORS + 0.25·SLP + 0.25·SWB + 0.15·CLR (SWB, CLR arrive with the cards).
        val ecap = sorted.indices.map { i ->
            weighted(listOf(0.35 to ors[i].first, 0.25 to slp[i].second), total = 1.0)
        }

        // LRB: z₇(load) − z₇(recovery); load = 0.5·TRL + 0.5·WLD, recovery = 0.6·ORS + 0.4·SLP.
        val load = sorted.indices.map { i -> weighted(listOf(0.5 to trl[i].second), total = 0.5, minCoverage = 0.0).first }
        val recovery = sorted.indices.map { i -> weighted(listOf(0.6 to ors[i].first, 0.4 to slp[i].second), total = 1.0, minCoverage = 0.0).first }
        val load7 = Stats.ewmaSeries(load, EWMA_SPAN)
        val recovery7 = Stats.ewmaSeries(recovery, EWMA_SPAN)
        val lrb = sorted.indices.map { i ->
            // Today's input weight: TRL 0.25, WLD 0.25, ORS 0.30, SLP 0.20.
            val coverage = (if (trl[i].second != null) 0.25 else 0.0) +
                (if (ors[i].first != null) 0.3 else 0.0) +
                (if (slp[i].second != null) 0.2 else 0.0)
            val l = load7[i]
            val r = recovery7[i]
            (if (l != null && r != null && coverage + EPS >= MIN_COVERAGE) l - r else null) to coverage
        }

        val out = mutableListOf<MetricValue>()
        fun emit(metric: MetricRef, values: List<Double?>, zs: List<Double?>, coverage: List<Double>, level: (Double?) -> Int?) {
            val ewma = Stats.ewmaSeries(values, EWMA_SPAN)
            sorted.forEachIndexed { i, d ->
                out += MetricValue(d.dayKey, metric, values[i], zs[i], level(zs[i]), coverage[i], if (values[i] != null) ewma[i] else null)
            }
        }
        emit(Metrics.ORS, ors.map { it.first }, ors.map { it.first }, ors.map { it.second }, ::levelOf)
        emit(Metrics.SLP, slp.map { it.first }, slp.map { it.second }, slp.map { if (it.first != null) 1.0 else 0.0 }, ::levelOf)
        emit(Metrics.SLP_DEBT, debt.map { it.first }, debt.map { null }, debt.map { it.second }) { null }
        emit(Metrics.TRL, trl.map { it.first }, trl.map { it.second }, trl.map { if (it.first != null) 1.0 else 0.0 }, ::levelOf)
        emit(Metrics.ECAP, ecap.map { it.first?.let(::facetScale) }, ecap.map { it.first }, ecap.map { it.second }, ::levelOf)
        emit(Metrics.LRB, lrb.map { it.first }, lrb.map { it.first }, lrb.map { it.second }, ::balanceLevel)
        // RHRZ / RRZ: the night's z only (no bpm, no breaths/min are stored — R-11, D-009).
        emit(Metrics.RHR_Z, rhrZ, rhrZ, rhrZ.map { if (it != null) 1.0 else 0.0 }, ::levelOf)
        emit(Metrics.RR_Z, rrZ, rrZ, rrZ.map { if (it != null) 1.0 else 0.0 }, ::levelOf)

        val latest = sorted.last().dayKey
        val nights = baselines.firstOrNull { it.dayKey == latest && it.input == BaselineInput.LN_RMSSD }?.n ?: 0
        return MetricResult(out, baselines, nights)
    }

    /**
     * Weighted sum of the available parts, renormalised by their weight. Coverage is the share
     * of [total] that is available; below [minCoverage] the value is null (A25 P5).
     */
    private fun weighted(parts: List<Pair<Double, Double?>>, total: Double, minCoverage: Double = MIN_COVERAGE): Pair<Double?, Double> {
        val present = parts.filter { it.second != null }
        val weight = present.sumOf { it.first }
        val coverage = weight / total
        if (present.isEmpty() || coverage + EPS < minCoverage) return null to coverage
        return present.sumOf { it.first * it.second!! } / weight to coverage
    }

    /** Facet scale: 50 = your norm, 50 + 15·Z, clamped 5–95 (A25 §4.2). */
    fun facetScale(z: Double): Double = (50.0 + 15.0 * z).coerceIn(5.0, 95.0)

    /** Five levels −2…+2 of a z value. [Допущение ⚑3] thresholds 0.5 (SWC) and 1.5. */
    fun levelOf(z: Double?): Int? = when {
        z == null -> null
        z <= -1.5 -> -2
        z <= -0.5 -> -1
        z < 0.5 -> 0
        z < 1.5 -> 1
        else -> 2
    }

    /** Весы zones (A25 §4.2): < −1 underload, −1…+1 balance, > +1 load accumulates. */
    fun balanceLevel(v: Double?): Int? = when {
        v == null -> null
        v <= -2.0 -> -2
        v < -1.0 -> -1
        v <= 1.0 -> 0
        v < 2.0 -> 1
        else -> 2
    }
}
