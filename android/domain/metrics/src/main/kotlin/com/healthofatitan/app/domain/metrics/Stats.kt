package com.healthofatitan.app.domain.metrics

import kotlin.math.abs
import kotlin.math.sign

/**
 * Robust statistics of the personal norm (A25 P1, P2). Pure functions, no Android.
 */
object Stats {

    /** Consistency constant: 1.4826 · MAD estimates the SD of a normal distribution. */
    const val MAD_SCALE = 1.4826

    /** Fallback when MAD = 0 (e.g. most days without a workout): 1.2533 · mean |x − median|. */
    const val MEAN_AD_SCALE = 1.2533

    const val Z_LIMIT = 3.0

    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2.0
    }

    /** Median absolute deviation (unscaled). */
    fun mad(values: List<Double>): Double? {
        val m = median(values) ?: return null
        return median(values.map { abs(it - m) })
    }

    /**
     * Robust z of [x] against [baseline]: `(x − median) / (1.4826 · MAD)`, clamped to ±3.
     * Null when the baseline has fewer than [minN] values (the norm is still being learned).
     * If the baseline has no spread at all, any difference from the median counts as ±3.
     */
    fun robustZ(x: Double, baseline: List<Double>, minN: Int): Double? {
        if (baseline.size < minN) return null
        val m = median(baseline) ?: return null
        var scale = MAD_SCALE * (mad(baseline) ?: 0.0)
        if (scale == 0.0) scale = MEAN_AD_SCALE * baseline.map { abs(it - m) }.average()
        if (scale == 0.0) return if (x == m) 0.0 else sign(x - m) * Z_LIMIT
        return ((x - m) / scale).coerceIn(-Z_LIMIT, Z_LIMIT)
    }

    /**
     * Exponentially weighted moving average with span [span] days (α = 2 / (span + 1)), over a
     * chronological series. Gaps (null) keep the previous average. Returns the running average
     * after each element, null until the first value.
     */
    fun ewmaSeries(values: List<Double?>, span: Int = 7): List<Double?> {
        val alpha = 2.0 / (span + 1)
        var acc: Double? = null
        return values.map { v ->
            if (v != null) acc = acc?.let { alpha * v + (1 - alpha) * it } ?: v
            acc
        }
    }
}
