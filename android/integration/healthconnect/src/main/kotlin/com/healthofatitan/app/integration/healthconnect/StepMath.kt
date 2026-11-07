package com.healthofatitan.app.integration.healthconnect

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One step record as Health Connect gives it, already known to come from a phone. */
data class StepChunk(val origin: String, val start: Instant, val end: Instant, val count: Long)

/**
 * Pure arithmetic of the phone's steps (A35 §2): pick one source so two apps counting the same
 * phone sensor are not added up, sum a window with a proportional share of records that cross
 * its edges, and split days by local date. In memory only (R-11).
 */
object StepMath {

    /** The phone source with the most steps: Android's own counter and, say, Fit never double. */
    fun pickOrigin(chunks: List<StepChunk>): String? =
        chunks.groupBy { it.origin }.maxByOrNull { (_, list) -> list.sumOf { it.count } }?.key

    fun onlyOrigin(chunks: List<StepChunk>): List<StepChunk> {
        val origin = pickOrigin(chunks) ?: return emptyList()
        return chunks.filter { it.origin == origin }
    }

    /** Steps inside [from]–[to]; a record that crosses an edge counts by its overlapping share. */
    fun within(chunks: List<StepChunk>, from: Instant, to: Instant): Int {
        if (!to.isAfter(from)) return 0
        var total = 0.0
        for (c in chunks) {
            if (!c.end.isAfter(c.start)) {
                // an instant record counts if it falls inside the window
                if (!c.start.isBefore(from) && c.start.isBefore(to)) total += c.count
                continue
            }
            val start = maxOf(c.start, from)
            val end = minOf(c.end, to)
            if (!end.isAfter(start)) continue
            total += c.count * Duration.between(start, end).toMillis().toDouble() / Duration.between(c.start, c.end).toMillis()
        }
        return total.toInt()
    }

    /**
     * The most steps in any [minutes]-long stretch between [from] and [to], sliding by 5 minutes:
     * «a walk of N minutes happened» rather than «steps somewhere around the slot» (A35 §4).
     */
    fun bestWindow(chunks: List<StepChunk>, from: Instant, to: Instant, minutes: Int): Int {
        val length = Duration.ofMinutes(minutes.toLong())
        if (minutes <= 0 || Duration.between(from, to) <= length) return within(chunks, from, to)
        var best = 0
        var start = from
        while (!start.plus(length).isAfter(to)) {
            best = maxOf(best, within(chunks, start, start.plus(length)))
            start = start.plus(STEP)
        }
        return best
    }

    private val STEP: Duration = Duration.ofMinutes(5)

    /** Steps per local day (the day of each record's start), for the personal goal. */
    /** Steps in each of the 24 hours from [dayStart] (A46 «часы с движением»), shares of overlapping records. */
    fun hourly(chunks: List<StepChunk>, dayStart: Instant): List<Int> =
        (0 until 24).map { h -> within(chunks, dayStart.plusSeconds(h * 3_600L), dayStart.plusSeconds((h + 1) * 3_600L)) }

    fun daily(chunks: List<StepChunk>, zone: ZoneId): Map<LocalDate, Int> =
        chunks.groupBy { it.start.atZone(zone).toLocalDate() }.mapValues { (_, list) -> list.sumOf { it.count }.toInt() }
}
