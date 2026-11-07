package com.healthofatitan.app.integration.whoop.day

import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.integration.whoop.WhoopGlance
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The day strain the mode allows (D-029 «нагрузка против потолка режима»), on WHOOP's 0–21 scale.
 * [Оценка] of the designer from the A37 mockups (Ровно 12, Разгон 16, Бережно 7): A09 sets the
 * training ceiling by regime, not a strain number — Agents 12 and 3 confirm or change these
 * values; until then they are a guide, never a limit the app enforces.
 */
object StrainCeiling {
    const val BOOST = 16.0
    const val STEADY = 12.0
    const val GENTLE = 7.0

    fun of(regime: Regime): Double = when (regime) {
        Regime.BOOST -> BOOST
        Regime.STEADY -> STEADY
        Regime.GENTLE -> GENTLE
    }
}

/**
 * What the three header rings show (D-029), derived without any input (D-027):
 * - load: today's WHOOP day strain (memory only) against the mode's [StrainCeiling];
 * - steps: no step source yet → null (the ring is dashed, never 0); WHOOP's API v2 cycle has no
 *   step count in the fields HoT reads, Health Connect comes with the Pixel Watch slice;
 * - bedtime: minutes left until today's bedtime anchor (A09 §7a) and the share of the waking day
 *   already passed (from WHOOP's wake-up, or bedtime − 16 h when the night is unknown).
 */
data class HeaderRings(
    val strain: Double? = null,
    val strainCeiling: Double? = null,
    val steps: Int? = null,
    val stepsGoal: Int? = null,
    val untilBedMin: Int? = null,
    val dayFraction: Float? = null,
    val bedtimeMin: Int? = null,
    val regime: Regime? = null,
)

object HeaderRingsMapper {
    private const val DEFAULT_DAY_MIN = 16 * 60

    fun of(plan: DayView?, glance: WhoopGlance?, now: LocalDateTime, zone: ZoneId): HeaderRings {
        val today = now.toLocalDate()
        val planToday = plan?.takeIf { it.dayKey == today || (it.dayKey == today.minusDays(1) && now.hour < 5) }
        val strain = glance?.dayStrain?.takeIf { it.cycleStart.atZone(zone).toLocalDate() >= today.minusDays(1) }?.strain
        val ceiling = planToday?.regime?.let(StrainCeiling::of)
        if (planToday == null) return HeaderRings(strain = strain)

        val dayStart = planToday.dayKey.atStartOfDay()
        val nowMin = Duration.between(dayStart, now).toMinutes().toInt()
        val bed = planToday.bedtimeMin
        val wake = glance?.sleep?.end?.atZone(zone)?.toLocalDateTime()
            ?.takeIf { it.toLocalDate() == planToday.dayKey }
            ?.let { it.hour * 60 + it.minute }
            ?: (bed - DEFAULT_DAY_MIN)
        val span = (bed - wake).coerceAtLeast(60)
        return HeaderRings(
            strain = strain,
            strainCeiling = ceiling,
            untilBedMin = (bed - nowMin).coerceAtLeast(0),
            dayFraction = ((nowMin - wake).toFloat() / span).coerceIn(0f, 1f),
            bedtimeMin = bed,
            regime = planToday.regime,
        )
    }
}
