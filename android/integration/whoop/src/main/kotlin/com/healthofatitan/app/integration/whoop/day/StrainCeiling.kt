package com.healthofatitan.app.integration.whoop.day

import com.healthofatitan.app.domain.engine.Regime

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
