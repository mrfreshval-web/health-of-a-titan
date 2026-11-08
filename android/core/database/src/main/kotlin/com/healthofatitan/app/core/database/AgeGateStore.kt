package com.healthofatitan.app.core.database

import com.healthofatitan.app.core.database.dao.ConsentDao
import com.healthofatitan.app.core.database.entity.ConsentRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.TimeZone

/** Where the gate 18+ stands for this phone. */
enum class AgeGate {
    /** The consent journal is not read yet: nothing of the app is shown, help still opens. */
    LOADING,

    /** No `age18` yet: the app does not go on until the person confirms. */
    ASK,

    /** `age18` is granted. */
    CONFIRMED,
}

/**
 * The gate 18+ (R-13, A61 §1.2 «Ч0 · 1а», §1.3): a self-confirmation «Мне есть 18 лет», stored as
 * `ConsentRecord(age18, version, time)` in the consent journal. HoT is for adults (A58 §6; Agent 3:
 * `AGE_U18` is a block of entry, not a mode). An answer «мне нет 18» is not stored: HoT keeps no
 * data about a minor.
 *
 * Агенту 7: для основателя и v1 флажка достаточно; до внешних тестеров — правила Google Play
 * (Families, health apps) и финальные тексты у практикующего юриста.
 */
class AgeGateStore(
    private val consentDao: ConsentDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val user = HotDatabase.LOCAL_USER

    val gate: Flow<AgeGate> = consentDao.observeCurrent(user)
        .map { rows -> if (rows.any { it.consentId == CONSENT_ID && it.granted && it.consentVersion >= CONSENT_VERSION }) AgeGate.CONFIRMED else AgeGate.ASK }
        .distinctUntilChanged()

    /** «Мне есть 18 лет» — the person's own confirmation. */
    suspend fun confirmAdult() {
        val t = now()
        consentDao.insert(
            ConsentRecordEntity(
                userId = user,
                consentId = CONSENT_ID,
                consentVersion = CONSENT_VERSION,
                granted = true,
                tsUtcMs = t,
                tzOffsetMin = TimeZone.getDefault().getOffset(t) / 60_000,
            ),
        )
    }

    companion object {
        /** A61 §1.3. */
        const val CONSENT_ID = "age18"
        const val CONSENT_VERSION = 1
    }
}
