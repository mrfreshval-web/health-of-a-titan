package com.healthofatitan.app.feature.me.profile

import com.healthofatitan.app.core.account.AccountProfileStore
import com.healthofatitan.app.domain.engine.CrisisAssessment
import com.healthofatitan.app.domain.engine.CrisisGuard
import com.healthofatitan.app.domain.engine.CrisisLevel
import com.healthofatitan.app.domain.engine.CrisisSource

/**
 * Saving «О себе» (A65 §5.2, review 2026-11-08 А-7). The text is saved whatever it says;
 * CrisisGuard reads it and only the level goes to the safety journal, with the source
 * [CrisisSource.PROFILE] — never the words. A standing text must not punish honesty: the event is
 * written (and «Помощь» opens by itself at level ≥ 2) only when the level rose above the one of
 * the text saved before, or a day has passed since the last event from «О себе». The quiet line
 * under the field stays while the saved level is ≥ 1. Nothing here touches ED_CARE.
 */
class AboutSaver(
    private val store: AccountProfileStore,
    /** The safety journal: `DailyLoopRepository.logSafety` in the app. */
    private val log: suspend (CrisisAssessment, CrisisSource) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class Result(val assessment: CrisisAssessment, val openHelp: Boolean)

    suspend fun save(text: String): Result {
        val before = store.profile.value
        store.setAbout(text)
        val assessment = CrisisGuard.assess(store.about.value)
        val level = assessment.level
        val at = now()
        val rose = level.value > before.aboutLevel
        val dayPassed = before.aboutAlertAt?.let { at - it >= REPEAT_AFTER_MS } ?: true
        val record = level != CrisisLevel.NONE && (rose || dayPassed)
        if (record) runCatching { log(assessment, CrisisSource.PROFILE) }
        store.setAboutLevel(level.value, if (record) at else before.aboutAlertAt)
        return Result(assessment, openHelp = record && level.autoShowHelp)
    }

    companion object {
        const val REPEAT_AFTER_MS = 24 * 60 * 60 * 1000L
    }
}
