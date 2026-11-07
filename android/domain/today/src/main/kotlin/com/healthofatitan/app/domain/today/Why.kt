package com.healthofatitan.app.domain.today

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** A personal corridor: the mean and the SD over the last ~30 days (A46 §3 п. 5: ± 1 SD). */
data class Corridor(val mean: Double, val sd: Double, val n: Int) {
    val low: Double get() = mean - sd
    val high: Double get() = mean + sd

    fun z(value: Double): Double? = if (sd > 0.0) (value - mean) / sd else null

    fun outside(value: Double): Boolean = sd > 0.0 && n >= MIN_N && (value < low || value > high)

    companion object {
        /** Fewer points than this give no corridor (no deviation is claimed). */
        const val MIN_N = 7

        fun of(values: List<Double>): Corridor? {
            if (values.size < 2) return null
            val mean = values.average()
            val sd = kotlin.math.sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
            return Corridor(mean, sd, values.size)
        }
    }
}

/** Everything «Почему так» may show; every field may be missing («нет данных»). */
data class WhyInput(
    val mode: DayMode? = null,
    val sleepMin: Int? = null,
    val needMin: Int? = null,
    val deepMin: Int? = null,
    val remMin: Int? = null,
    val lightMin: Int? = null,
    val awakeMin: Int? = null,
    val disturbances: Int? = null,
    val efficiency: Double? = null,
    val consistency: Double? = null,
    val debtHours: Double? = null,
    val restingHr: Double? = null,
    val restingHrCorridor: Corridor? = null,
    val restingHr7: Double? = null,
    val hrv: Double? = null,
    val hrvCorridor: Corridor? = null,
    val respiratoryRate: Double? = null,
    val respiratoryCorridor: Corridor? = null,
    val spo2: Double? = null,
    val spo2Corridor: Corridor? = null,
    val skinTemp: Double? = null,
    val skinTempCorridor: Corridor? = null,
    /** Bodrost of the «Рассвет» deck, 1–5. */
    val self: Int? = null,
    /** HoT's data level of the night (Заряд level → 1–5), for «ты ↔ WHOOP». */
    val dataLevel: Int? = null,
)

enum class Arrow(val glyph: String) { UP("↑"), DOWN("↓"), LEVEL("=") }

enum class WhySectionId(val title: String) {
    SUMMARY(TodayCopy.WHY_SUMMARY),
    SLEEP(TodayCopy.WHY_SLEEP),
    HEART(TodayCopy.WHY_HEART),
    BREATH(TodayCopy.WHY_BREATH),
    DEVIATIONS(TodayCopy.WHY_DEVIATIONS),
    YOU_VS_WHOOP(TodayCopy.WHY_YOU),
}

/** One row: value · corridor · arrow to the norm; a missing value reads «нет данных» (T-11: always a source). */
data class WhyMetric(
    val name: String,
    val value: String?,
    val corridor: String?,
    val arrow: Arrow?,
    val source: String,
    val estimate: Boolean = false,
    val outside: Boolean = false,
    /** |z| for ordering the reasons of the summary. */
    val weight: Double = 0.0,
)

data class WhySection(val id: WhySectionId, val metrics: List<WhyMetric>, val text: String? = null)

object WhyBuilder {
    /** A46 §3 п. 5: from this many values outside the corridor the gentle sentence appears. */
    const val MANY = 3

    private fun f(d: Double, digits: Int = 0): String =
        String.format(Locale.ROOT, "%.${digits}f", d).replace('.', ',')

    private fun arrow(value: Double, c: Corridor?): Arrow? = c?.let {
        when {
            !it.outside(value) -> Arrow.LEVEL
            value > it.high -> Arrow.UP
            else -> Arrow.DOWN
        }
    }

    private fun metric(name: String, value: Double?, c: Corridor?, unit: String, source: String, digits: Int = 0): WhyMetric = WhyMetric(
        name = name,
        value = value?.let { f(it, digits) + unit },
        corridor = c?.takeIf { it.n >= Corridor.MIN_N }?.let { f(it.low, digits) + "–" + f(it.high, digits) + unit },
        arrow = value?.let { arrow(it, c) },
        source = source,
        outside = value != null && c != null && c.outside(value),
        weight = value?.let { v -> c?.z(v)?.let(::abs) } ?: 0.0,
    )

    private fun clock(min: Int?): String? = min?.let(MeRules::clock)

    fun build(i: WhyInput): List<WhySection> {
        val sleep = listOf(
            WhyMetric(TodayCopy.M_SLEEP, clock(i.sleepMin), i.needMin?.let { TodayCopy.need(MeRules.clock(it)) }, i.sleepMin?.let { s -> i.needMin?.let { if (s >= it) Arrow.LEVEL else Arrow.DOWN } }, TodayCopy.SRC_SLEEP,
                weight = if (i.sleepMin != null && i.needMin != null && i.needMin > 0) (i.needMin - i.sleepMin).coerceAtLeast(0) / 30.0 else 0.0),
            WhyMetric(TodayCopy.M_DEEP, clock(i.deepMin), null, null, TodayCopy.SRC_SLEEP),
            WhyMetric(TodayCopy.M_REM, clock(i.remMin), null, null, TodayCopy.SRC_SLEEP),
            WhyMetric(TodayCopy.M_LIGHT, clock(i.lightMin), null, null, TodayCopy.SRC_SLEEP),
            WhyMetric(TodayCopy.M_AWAKE, i.disturbances?.toString() ?: clock(i.awakeMin), null, null, TodayCopy.SRC_SLEEP),
            WhyMetric(TodayCopy.M_EFFICIENCY, i.efficiency?.let { f(it) + " %" }, null, null, TodayCopy.SRC_SLEEP),
            WhyMetric(TodayCopy.M_CONSISTENCY, i.consistency?.let { f(it) + " %" }, null, null, TodayCopy.SRC_SLEEP),
            WhyMetric(TodayCopy.M_DEBT, i.debtHours?.let { MeRules.clock((it * 60).roundToInt().coerceAtLeast(0)) }, null, null, "HoT", estimate = true),
        )
        val heart = listOf(
            metric(TodayCopy.M_RHR, i.restingHr, i.restingHrCorridor, "", TodayCopy.SRC_RECOVERY),
            metric(TodayCopy.M_HRV, i.hrv, i.hrvCorridor, " мс", TodayCopy.SRC_RECOVERY),
            WhyMetric(
                TodayCopy.M_RHR_TREND,
                i.restingHr7?.let { f(it) },
                null,
                i.restingHr7?.let { t -> i.restingHr?.let { if (abs(it - t) < 1.0) Arrow.LEVEL else if (it > t) Arrow.UP else Arrow.DOWN } },
                TodayCopy.SRC_RECOVERY,
            ),
        )
        val breath = listOf(
            metric(TodayCopy.M_RESP, i.respiratoryRate, i.respiratoryCorridor, "", TodayCopy.SRC_SLEEP, digits = 1),
            metric(TodayCopy.M_SPO2, i.spo2, i.spo2Corridor, " %", TodayCopy.SRC_RECOVERY),
            metric(TodayCopy.M_SKIN, i.skinTemp, i.skinTempCorridor, " °C", TodayCopy.SRC_RECOVERY, digits = 1),
        )
        val outside = (heart + breath).filter { it.outside }
        val deviations = WhySection(
            WhySectionId.DEVIATIONS,
            outside,
            text = when {
                outside.size >= MANY -> TodayCopy.WHY_MANY_DEVIATIONS + " " + TodayCopy.WHY_HELP_HINT
                outside.isEmpty() -> TodayCopy.WHY_NO_DEVIATIONS
                else -> TodayCopy.deviationsCount(outside.size)
            },
        )
        val self = WhyMetric(TodayCopy.M_SELF, i.self?.let { "$it / 5" }, null, null, TodayCopy.SRC_CHECKIN)
        val you = WhySection(
            WhySectionId.YOU_VS_WHOOP,
            listOf(self),
            text = when {
                i.self == null || i.dataLevel == null -> TodayCopy.WHY_YOU_NONE
                abs(i.self - i.dataLevel) >= 2 -> TodayCopy.WHY_YOU_SCISSORS
                else -> TodayCopy.WHY_YOU_MATCH
            },
        )
        val reasons = (sleep + heart + breath).filter { it.value != null && it.weight > 0.0 }.sortedByDescending { it.weight }.take(3) +
            listOfNotNull(self.takeIf { i.self != null })
        return listOf(
            WhySection(WhySectionId.SUMMARY, reasons.take(3)),
            WhySection(WhySectionId.SLEEP, sleep),
            WhySection(WhySectionId.HEART, heart),
            WhySection(WhySectionId.BREATH, breath),
            deviations,
            you,
        )
    }
}
