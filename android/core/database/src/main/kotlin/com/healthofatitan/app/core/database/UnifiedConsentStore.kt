package com.healthofatitan.app.core.database

import com.healthofatitan.app.core.database.dao.ConsentDao
import com.healthofatitan.app.core.database.entity.ConsentRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.TimeZone

/** Where the unified consent stands for this phone. */
enum class ConsentGate {
    /** The consent journal is not read yet. */
    LOADING,

    /** No `unified` of the current version: the screen of A68 lies over the app. */
    ASK,

    /** `unified` of the current version is granted. */
    GRANTED,
}

/**
 * The unified consent (A68 v1, the founder's decision of 08.11: «одно общее согласие, дать один
 * раз»): one tick «Принимаю» writes `unified` and, in the same transaction, the parts it absorbs
 * (A68 §2) with the same time — `profile_s4` (health from the person's words) and `mentor_ai`
 * (the talk and codes to Anthropic). Each part is withdrawn on its own in «Я › Данные и
 * приватность» (D-058). `metrics_ai`, `ed_care_keep` and `roots` stay separate. A «не принимаю»
 * is not stored. A new text is a new [VERSION] and asks again.
 */
class UnifiedConsentStore(
    private val consentDao: ConsentDao,
    private val transaction: suspend (suspend () -> Unit) -> Unit = { it() },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val user = HotDatabase.LOCAL_USER

    val gate: Flow<ConsentGate> = consentDao.observeCurrent(user)
        .map { rows -> if (rows.any { it.consentId == CONSENT_ID && it.granted && it.consentVersion >= VERSION }) ConsentGate.GRANTED else ConsentGate.ASK }
        .distinctUntilChanged()

    /** «Принимаю»: the person's own tick. */
    suspend fun accept() {
        val t = now()
        val tz = TimeZone.getDefault().getOffset(t) / 60_000
        transaction {
            consentDao.insert(ConsentRecordEntity(userId = user, consentId = CONSENT_ID, consentVersion = VERSION, granted = true, tsUtcMs = t, tzOffsetMin = tz))
            PARTS.forEach { (id, version) ->
                if (consentDao.latest(user, id)?.granted != true) {
                    consentDao.insert(ConsentRecordEntity(userId = user, consentId = id, consentVersion = version, granted = true, tsUtcMs = t, tzOffsetMin = tz))
                }
            }
        }
    }

    companion object {
        const val CONSENT_ID = "unified"
        const val VERSION = 1

        /** The id of the AI transfer (A68 §2, condition 7 of the review of PR #58). */
        const val MENTOR_AI_ID = "mentor_ai"

        /** The parts the unified text absorbs and that are withdrawn one by one. */
        val PARTS: List<Pair<String, Int>> = listOf(
            ProfileStore.CONSENT_ID to ProfileStore.CONSENT_VERSION,
            MENTOR_AI_ID to 1,
        )
    }
}
