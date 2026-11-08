package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.core.account.AccountProfile
import com.healthofatitan.app.core.account.AccountProfileStore

/**
 * After WHOOP connects (A65 §2.4, A61 «сначала данные, потом вопрос»): the first name becomes a
 * suggestion «…, верно?» (a typed name wins), height and max pulse go to the profile on the phone.
 * The phone asks WHOOP directly; the Worker never sees these values.
 */
class WhoopProfileImport(
    private val api: WhoopApi,
    private val profile: AccountProfileStore,
) {
    @Volatile
    private var triedThisRun = false

    suspend fun import() {
        triedThisRun = true
        val facts = api.profileFacts()
        profile.suggestName(facts.firstName, AccountProfile.SOURCE_WHOOP)
        profile.setBody(facts.heightCm, facts.maxPulse)
    }

    /** On app open: once per process while the body data was never imported. */
    suspend fun importIfMissing() {
        if (triedThisRun || profile.profile.value.bodyAt != null) return
        import()
    }
}
