package com.healthofatitan.app.integration.whoop.day

import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.CheckinDao
import com.healthofatitan.app.domain.checkin.BodyFeel
import com.healthofatitan.app.domain.checkin.CardId
import com.healthofatitan.app.domain.checkin.Charge
import com.healthofatitan.app.domain.engine.Body
import java.time.LocalDate

/**
 * Reads today's wake deck for the plan (A44 §5): П1 Заряд → SG-SELF energy, П3 «простуда» →
 * SAF-03@2 and «забито» → EVE-01 when the evening did not say it. Skips give nothing (never 0).
 * S5 is never read here.
 */
class CheckinMorningSelf(private val dao: CheckinDao) : MorningSelfSource {
    override suspend fun of(day: LocalDate): MorningSelf? {
        val rows = dao.answers(HotDatabase.LOCAL_USER, day.toString()).filter { !it.skipped }.associateBy { it.card }
        val charge = rows[CardId.CHARGE.ref]?.level
        val body = when (BodyFeel.of(rows[CardId.BODY.ref]?.level)) {
            BodyFeel.SICK -> Body.SICK
            BodyFeel.SORE -> Body.SORE
            BodyFeel.LIGHT -> Body.FINE
            null -> null
        }
        if (charge == null && body == null) return null
        return MorningSelf(energy = charge?.let(Charge::energy), body = body)
    }
}
