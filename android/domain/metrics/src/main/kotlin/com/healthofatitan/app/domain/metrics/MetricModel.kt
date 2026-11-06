package com.healthofatitan.app.domain.metrics

import java.time.LocalDate

/**
 * A metric formula version, `metricId@ver` (A25 P6, D-023). A new formula gets a new version;
 * stored values of old versions are never rewritten.
 */
data class MetricRef(val id: String, val version: Int) {
    override fun toString(): String = "$id@$version"
}

/** Metrics computed in slice 1 (A14 §9: ORS, SLP, ECAP, LRB; TRL and the sleep debt feed them). */
object Metrics {
    /** Ночное восстановление: 0.5·z(lnRMSSD) − 0.3·z(RHR) − 0.2·z(ЧД) (A25 §4.1). */
    val ORS = MetricRef("ORS", 1)

    /** Достаточность сна: hours of sleep vs the personal norm (A25 §4.1). */
    val SLP = MetricRef("SLP", 1)

    /** Долг сна за 7 дней: Σ max(0, 7 ч − сон), не больше 10 ч (A25 §4.1, A09 `sleep.debt7_h`). */
    val SLP_DEBT = MetricRef("SLPDEBT", 1)

    /** Нагрузка: Edwards TRIMP of the day's workouts from heart-rate zones (A25 §4.1, A19 §5.2). */
    val TRL = MetricRef("TRL", 1)

    /** Заряд: 0.35·ORS + 0.25·SLP + 0.25·SWB + 0.15·CLR; scale 50 + 15·Z (A25 §4.2). */
    val ECAP = MetricRef("ECAP", 1)

    /** Весы: z₇(0.5·TRL + 0.5·WLD) − z₇(0.6·ORS + 0.4·SLP) (A25 §4.2). */
    val LRB = MetricRef("LRB", 1)

    val all = listOf(ORS, SLP, SLP_DEBT, TRL, ECAP, LRB)
}

/** Inputs of the personal baseline (A14 `BaselineSnapshot.inputId`). */
enum class BaselineInput(val id: String) {
    LN_RMSSD("lnRMSSD"),
    RHR("RHR"),
    RR("RR"),
    SLEEP_HOURS("sleepHours"),
    SLEEP_MID("sleepMid"),
    TRL("TRL"),
}

/**
 * One WHOOP day (wake → wake, A14 §3.2) after mapping, **in memory only** (D-009). Physiology
 * is in physiological units; nothing here is persisted as such.
 *
 * @param lnRmssd ln of nightly rMSSD in ms
 * @param rhr resting heart rate, bpm
 * @param rr nightly respiratory rate, breaths/min
 * @param sleepHours sleep without awake time, hours (main sleep only)
 * @param sleepMidMin middle of the sleep, minutes from local midnight in −720…720
 * @param trimp training load of the day; 0.0 if the strap was worn and there was no workout,
 *   null if wear is unknown
 */
data class DayInputs(
    val dayKey: LocalDate,
    val lnRmssd: Double? = null,
    val rhr: Double? = null,
    val rr: Double? = null,
    val sleepHours: Double? = null,
    val sleepMidMin: Double? = null,
    val trimp: Double? = null,
)

/** A derived value as stored in `MetricDaily` (S3). */
data class MetricValue(
    val dayKey: LocalDate,
    val metric: MetricRef,
    val value: Double?,
    val z: Double?,
    val level: Int?,
    val coverage: Double,
    val ewma7: Double?,
)

/** A personal norm snapshot as stored in `BaselineSnapshot` (aggregate, R-11 grey zone). */
data class BaselineValue(
    val dayKey: LocalDate,
    val input: BaselineInput,
    val median28: Double?,
    val mad28: Double?,
    val n: Int,
)

data class MetricResult(
    val daily: List<MetricValue>,
    val baselines: List<BaselineValue>,
    /** Nights with HRV in the 28-day baseline of the latest day (A09 `data.baseline_n`). */
    val baselineNights: Int,
)
