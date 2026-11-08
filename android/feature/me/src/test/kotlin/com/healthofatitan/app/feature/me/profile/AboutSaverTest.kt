package com.healthofatitan.app.feature.me.profile

import com.healthofatitan.app.core.account.AccountProfileStore
import com.healthofatitan.app.core.account.InMemoryProfileBlobs
import com.healthofatitan.app.core.network.HotJson
import com.healthofatitan.app.core.security.FieldCipher
import com.healthofatitan.app.domain.engine.CrisisAssessment
import com.healthofatitan.app.domain.engine.CrisisLevel
import com.healthofatitan.app.domain.engine.CrisisSource
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CrisisGuard in «О себе» (A65 §5.2, review 2026-11-08 А-7): «Помощь» opens by itself once, not
 * on every save; level 1 stays a quiet line; the event carries the source PROFILE and no words.
 */
class AboutSaverTest {

    private object PlainCipher : FieldCipher {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray) = plaintext
        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray) = ciphertext
    }

    private val logged = mutableListOf<Pair<CrisisAssessment, CrisisSource>>()
    private var now = 1_000_000L
    private val store = AccountProfileStore(InMemoryProfileBlobs(), PlainCipher, HotJson)
    private val saver = AboutSaver(store, { a, s -> logged += a to s }, now = { now })

    @Test
    fun `saving the same level-2 text again does not open help twice within a day`() = runBlocking {
        assertTrue(saver.save(PASSIVE).openHelp)
        now += 60_000
        assertFalse(saver.save("$PASSIVE.").openHelp, "a fixed comma is not a new alarm")
        assertEquals(1, logged.size)
        assertEquals(2, store.profile.value.aboutLevel, "the quiet line stays")
        now += AboutSaver.REPEAT_AFTER_MS
        assertTrue(saver.save(PASSIVE).openHelp, "after a day it may open again")
    }

    @Test
    fun `a rise from 1 to 2 opens help, level 1 alone only shows the quiet line`() = runBlocking {
        val first = saver.save(CLARIFY)
        assertEquals(CrisisLevel.CLARIFY, first.assessment.level)
        assertFalse(first.openHelp)
        assertEquals(1, store.profile.value.aboutLevel)
        now += 60_000
        assertTrue(saver.save(PASSIVE).openHelp)
    }

    @Test
    fun `the event has the source PROFILE and no text, the text is saved whatever it says`() = runBlocking {
        saver.save(PASSIVE)
        val (assessment, source) = logged.single()
        assertEquals(CrisisSource.PROFILE, source)
        assertEquals(CrisisAssessment(assessment.level, assessment.reason), assessment, "only the level and the reason class")
        assertFalse(assessment.toString().contains("жить"), "the words never reach the journal")
        assertEquals(PASSIVE, store.about.value)
    }

    @Test
    fun `nothing but the safety journal is touched - no ED_CARE, a clean text clears the line`() = runBlocking {
        saver.save(PASSIVE)
        saver.save("Работаю посменно, по утрам бегаю.")
        // AboutSaver has the profile store and the safety log only: ED_CARE cannot be switched from here.
        assertEquals(listOf(CrisisSource.PROFILE), logged.map { it.second })
        assertEquals(0, store.profile.value.aboutLevel)
    }

    private companion object {
        const val PASSIVE = "Не хочу больше жить"
        const val CLARIFY = "Выпилюсь, если не сдам отчёт, сил больше нет"
    }
}
