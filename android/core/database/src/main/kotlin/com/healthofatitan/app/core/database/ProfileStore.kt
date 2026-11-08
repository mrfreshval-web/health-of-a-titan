package com.healthofatitan.app.core.database

import com.healthofatitan.app.core.database.dao.ConsentDao
import com.healthofatitan.app.core.database.dao.EventLogDao
import com.healthofatitan.app.core.database.dao.NutritionDao
import com.healthofatitan.app.core.database.entity.ConsentRecordEntity
import com.healthofatitan.app.core.database.entity.EventLogEntity
import com.healthofatitan.app.core.database.entity.NutritionProfileEntity
import com.healthofatitan.app.core.model.profile.ProfileCategory
import com.healthofatitan.app.core.model.profile.UserConstraints
import com.healthofatitan.app.core.model.safety.SafetyEventKinds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

/**
 * The shared profile of specifics as the app reads it (nutrition A41, training A40).
 *
 * @property consented the separate explicit S4 consent (A41 §6.5) is granted; without it the
 *   profile is always empty and nutrition runs «Основа» without filters
 * @property consentAtMs when it was granted, for «согласие 07.11»
 * @property nutritionLevel the chosen nutrition level code (`base | structured | precise`), null = not chosen
 */
data class ProfileSnapshot(
    val consented: Boolean = false,
    val consentAtMs: Long? = null,
    val constraints: UserConstraints = UserConstraints.NONE,
    val nutritionLevel: String? = null,
)

/**
 * Read and write the profile of specifics (S4). One instance for the app; inject it where the
 * profile is needed (`ProfileStore.profile`). Revoking consent deletes every category.
 *
 * @param events the event journal (A58 §8): a newly marked `ed_history`, `ed_signals` or
 *   `underweight_reported` writes the entry of `ED_CARE` for the safety bus (A60 §3.3 «входы — в
 *   один словарь»). Unmarking writes nothing: only the person's choice ends `ED_CARE` (D-057).
 * @param transaction runs a withdrawal of consent as one transaction (`HotDatabase.withTransaction`);
 *   it stands before [now] so that a trailing lambda stays the clock
 */
class ProfileStore(
    private val nutritionDao: NutritionDao,
    private val consentDao: ConsentDao,
    private val events: EventLogDao? = null,
    private val transaction: suspend (suspend () -> Unit) -> Unit = { it() },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val user = HotDatabase.LOCAL_USER

    val profile: Flow<ProfileSnapshot> = combine(consentDao.observeCurrent(user), nutritionDao.observeProfile(user)) { consents, rows ->
        val consent = consents.firstOrNull { it.consentId == CONSENT_ID }?.takeIf { it.granted }
        val byCategory = rows.associateBy { it.category }
        ProfileSnapshot(
            consented = consent != null,
            consentAtMs = consent?.tsUtcMs,
            constraints = if (consent == null) {
                UserConstraints.NONE
            } else {
                UserConstraints.fromCodes(
                    ProfileCategory.entries.associateWith { c -> byCategory[c.key]?.codes?.split(SEP).orEmpty() },
                )
            },
            nutritionLevel = byCategory[LEVEL_KEY]?.codes?.takeIf { it.isNotBlank() },
        )
    }.distinctUntilChanged()

    /** Grants the S4 consent; `false` is [withdrawConsent] without keeping `ED_CARE`. */
    suspend fun setConsent(granted: Boolean) {
        if (granted) record(CONSENT_ID, CONSENT_VERSION, true, now()) else withdrawConsent(keepEdCare = false)
    }

    /**
     * Withdraws the S4 consent (D-058): every category (the level stays), the evening food marks
     * and every event of `ED_CARE` in the journal — the reasons of entry and the history — are
     * deleted in one transaction. Crisis events are not S4 and stay (A07).
     *
     * @param keepEdCare the person answered «Да» to «Оставить бережный режим еды?» (asked only while
     *   `ED_CARE` is on): one mark «режим включён» stays — an entry `self_choice` resting on the
     *   narrow consent [KEEP_CONSENT_ID]. «Нет» or a closed question: nothing stays.
     */
    suspend fun withdrawConsent(keepEdCare: Boolean) = transaction {
        val t = now()
        // «Выключить бережный режим еды» without S4 (Ю-3) does not write a second revoke of S4
        if (consentDao.latest(user, CONSENT_ID)?.granted == true) record(CONSENT_ID, CONSENT_VERSION, false, t)
        nutritionDao.deleteAllExcept(user, LEVEL_KEY)
        nutritionDao.deleteEvenings(user)
        val journal = events
        journal?.deleteByPrefix(user, SafetyEventKinds.ED_CARE_PREFIX)
        if (keepEdCare && journal != null) {
            record(KEEP_CONSENT_ID, KEEP_CONSENT_VERSION, true, t)
            journal.append(
                EventLogEntity(
                    userId = user,
                    kind = SafetyEventKinds.ED_CARE_ENTER,
                    payload = SafetyEventKinds.REASON_SELF_CHOICE,
                    source = SafetyEventKinds.SOURCE_CONSENT_KEEP,
                    at = t,
                    dayKey = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                ),
            )
        } else if (consentDao.latest(user, KEEP_CONSENT_ID)?.granted == true) {
            // the mark of an earlier «Да» is gone with the events: its consent ends too
            record(KEEP_CONSENT_ID, KEEP_CONSENT_VERSION, false, t)
        }
    }

    private suspend fun record(consentId: String, version: Int, granted: Boolean, t: Long) {
        consentDao.insert(
            ConsentRecordEntity(
                userId = user,
                consentId = consentId,
                consentVersion = version,
                granted = granted,
                tsUtcMs = t,
                tzOffsetMin = TimeZone.getDefault().getOffset(t) / 60_000,
            ),
        )
    }

    /** Stores the whole profile: changed categories are written, emptied ones deleted. */
    suspend fun save(constraints: UserConstraints) {
        val t = now()
        val (filled, empty) = ProfileCategory.entries.partition { constraints.has(it) }
        val before = events?.let { medicalCodes() }
        nutritionDao.replace(
            user,
            filled.map { NutritionProfileEntity(user, it.key, constraints.codes(it).joinToString(SEP), t) },
            empty.map { it.key },
        )
        if (before != null) recordEdCare(before, constraints.codes(ProfileCategory.MEDICAL).toSet(), t)
    }

    private suspend fun medicalCodes(): Set<String> =
        nutritionDao.profile(user).firstOrNull { it.category == ProfileCategory.MEDICAL.key }?.codes?.split(SEP)?.toSet().orEmpty()

    /** A code of `ED_CARE` that was not marked before starts the mode; of several, `body` (the longer cooldown) wins. */
    private suspend fun recordEdCare(before: Set<String>, after: Set<String>, t: Long) {
        val journal = events ?: return
        val reasons = (after - before).mapNotNull { SafetyEventKinds.ED_PROFILE_CODES[it] }
        if (reasons.isEmpty()) return
        val reason = if (SafetyEventKinds.REASON_BODY in reasons) SafetyEventKinds.REASON_BODY else reasons.first()
        journal.append(
            EventLogEntity(
                userId = user,
                kind = SafetyEventKinds.ED_CARE_ENTER,
                payload = reason,
                source = SOURCE_PROFILE,
                at = t,
                dayKey = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
            ),
        )
    }

    /** «Удалить» one category (Agent 7: deletion per category and in full). */
    suspend fun delete(category: ProfileCategory) = nutritionDao.deleteCategory(user, category.key)

    /** Deletes every category and the evening food marks; the consent record stays as history. */
    suspend fun deleteAll() {
        nutritionDao.deleteAllExcept(user, LEVEL_KEY)
        nutritionDao.deleteEvenings(user)
    }

    suspend fun setNutritionLevel(code: String) {
        nutritionDao.upsert(listOf(NutritionProfileEntity(user, LEVEL_KEY, code, now())))
    }

    companion object {
        /** Consent id of the profile of specifics (texts: Agent 7). */
        const val CONSENT_ID = "profile_s4"
        const val CONSENT_VERSION = 1

        /**
         * The narrow consent to keep only the mark «бережный режим еды включён» after S4 is
         * withdrawn (D-058). Text: черновик, ждёт ревью Агентов 7 и 10.
         */
        const val KEEP_CONSENT_ID = "ed_care_keep"
        const val KEEP_CONSENT_VERSION = 1
        const val LEVEL_KEY = "level"
        const val SOURCE_PROFILE = "profile"
        private const val SEP = ";"
    }
}
