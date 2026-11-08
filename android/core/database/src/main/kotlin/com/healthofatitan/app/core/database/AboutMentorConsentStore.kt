package com.healthofatitan.app.core.database

import com.healthofatitan.app.core.database.dao.ConsentDao
import com.healthofatitan.app.core.database.entity.ConsentRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.TimeZone

/**
 * The consent `about_mentor` (A65 §5.2, A62): «О себе» may go to Винс — the whole text, into the
 * `ContextPack` of the current conversation only. Off by default; without it the text goes
 * nowhere. One more row of the consent journal (`ConsentRecord(about_mentor, version, time)`), so
 * granting and withdrawing are equally simple and both are kept as history.
 *
 * This slice only keeps the flag. Reading it and passing the text is the work of Винс (the mentor
 * module). The text shown is `AccountProfileTexts.ABOUT_MENTOR_CONSENT_BODY` (v1, draft for Agent 7 and the lawyer).
 */
class AboutMentorConsentStore(
    private val consentDao: ConsentDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val user = HotDatabase.LOCAL_USER

    /** True only while the latest record of `about_mentor` grants it. */
    val granted: Flow<Boolean> = consentDao.observeCurrent(user)
        .map { rows -> rows.any { it.consentId == CONSENT_ID && it.granted && it.consentVersion >= CONSENT_VERSION } }
        .distinctUntilChanged()

    suspend fun set(granted: Boolean) {
        if ((consentDao.latest(user, CONSENT_ID)?.granted == true) == granted) return
        val t = now()
        consentDao.insert(
            ConsentRecordEntity(
                userId = user,
                consentId = CONSENT_ID,
                consentVersion = CONSENT_VERSION,
                granted = granted,
                tsUtcMs = t,
                tzOffsetMin = TimeZone.getDefault().getOffset(t) / 60_000,
            ),
        )
    }

    companion object {
        /** A65 §5.2: «О себе» → Винс. */
        const val CONSENT_ID = "about_mentor"
        const val CONSENT_VERSION = 1
    }
}
