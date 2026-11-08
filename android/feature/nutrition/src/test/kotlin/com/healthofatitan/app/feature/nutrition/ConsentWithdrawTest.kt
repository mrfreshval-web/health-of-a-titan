package com.healthofatitan.app.feature.nutrition

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.ProfileStore
import com.healthofatitan.app.core.model.profile.ProfileCategory
import com.healthofatitan.app.core.model.profile.UserConstraints
import com.healthofatitan.app.core.model.safety.SafetyEventKinds
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.nutrition.FoldedProfile
import com.healthofatitan.app.domain.nutrition.Level
import com.healthofatitan.app.domain.nutrition.ProfileMode
import com.healthofatitan.app.domain.safety.EdCareReason
import com.healthofatitan.app.domain.safety.Restriction
import com.healthofatitan.app.domain.safety.SafetyConsumer
import com.healthofatitan.app.domain.safety.SafetyFlag
import com.healthofatitan.app.integration.whoop.safety.SafetyBus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-058: withdrawing S4 while `ED_CARE` is on asks once «Оставить бережный режим еды?». «Да» keeps
 * one mark «режим включён» on the consent `ed_care_keep` and deletes the reasons of entry and the
 * history; «Нет» (or a closed question) deletes everything and the bus shows no `ED_CARE`. Without
 * `ED_CARE` it is the usual withdrawal with no question. Crisis events are not S4 and stay.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class ConsentWithdrawTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(context, HotDatabase::class.java).allowMainThreadQueries().build()
    private val now = Instant.parse("2026-11-08T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val store = ProfileStore(db.nutritionDao(), db.consentDao(), db.eventLogDao()) { now.toEpochMilli() }
    private val bus = SafetyBus(db.eventLogDao(), clock)
    private val user = HotDatabase.LOCAL_USER

    @After
    fun close() = db.close()

    /** A consented profile with `underweight_reported` (the reason `body`), steps of the history and a crisis. */
    private suspend fun edCareOn() {
        store.setConsent(true)
        store.save(
            UserConstraints.fromCodes(
                mapOf(ProfileCategory.MEDICAL to listOf("underweight_reported"), ProfileCategory.DIETS to listOf("vegetarian")),
            ),
        )
        bus.chooseEdCareStep(1) // a row of the history (ignored by the projector while on, but stored)
        bus.crisis(level = 2, source = "EVENING")
        assertTrue(bus.current().edCare.active)
        assertEquals(EdCareReason.BODY, bus.current().edCare.reason)
    }

    private suspend fun edCareRows() = db.eventLogDao().list(user, SafetyEventKinds.ED_CARE_PREFIX)

    private suspend fun consent(id: String) = db.consentDao().latest(user, id)

    @Test
    fun `yes keeps one mark on ed_care_keep and no reason of entry`() = runTest {
        edCareOn()
        store.withdrawConsent(keepEdCare = true)

        val rows = edCareRows()
        assertEquals(1, rows.size, "one mark «режим включён»")
        val mark = rows.single()
        assertEquals(SafetyEventKinds.ED_CARE_ENTER, mark.kind)
        assertEquals(SafetyEventKinds.REASON_SELF_CHOICE, mark.payload)
        assertEquals(SafetyEventKinds.SOURCE_CONSENT_KEEP, mark.source)
        val all = db.eventLogDao().list(user, SafetyEventKinds.PREFIX)
        assertTrue(all.none { it.payload == SafetyEventKinds.REASON_BODY || it.payload == SafetyEventKinds.REASON_SELF_REPORT }, "no reason of entry")
        assertTrue(all.none { it.source == ProfileStore.SOURCE_PROFILE }, "no entry from the profile")
        assertEquals(1, db.eventLogDao().count(user, SafetyEventKinds.CRISIS), "the crisis is not S4 and stays")

        assertEquals(false, consent(ProfileStore.CONSENT_ID)?.granted)
        assertEquals(true, consent(ProfileStore.KEEP_CONSENT_ID)?.granted)
        assertEquals(ProfileStore.KEEP_CONSENT_VERSION, consent(ProfileStore.KEEP_CONSENT_ID)?.consentVersion)
        assertTrue(db.nutritionDao().profile(user).none { it.category != ProfileStore.LEVEL_KEY }, "the profile is deleted")
        assertFalse(store.profile.first().consented)

        // the bus reads the mark: ED_CARE stays on, calories, weight and body photo stay hidden
        val state = bus.current()
        assertTrue(SafetyFlag.ED_CARE in state)
        assertEquals(EdCareReason.SELF_CHOICE, state.edCare.reason)
        val today = state.restrictions(SafetyConsumer.TODAY)
        listOf(Restriction.HIDE_CALORIES, Restriction.HIDE_WEIGHT, Restriction.HIDE_BODY_PHOTO).forEach { assertTrue(it in today, "$it") }
    }

    @Test
    fun `no deletes everything and the bus shows no ED_CARE`() = runTest {
        edCareOn()
        store.withdrawConsent(keepEdCare = false)

        assertTrue(edCareRows().isEmpty())
        assertEquals(1, db.eventLogDao().count(user, SafetyEventKinds.CRISIS))
        assertNull(consent(ProfileStore.KEEP_CONSENT_ID), "no narrow consent is asked for on «Нет»")
        val state = bus.current()
        assertFalse(SafetyFlag.ED_CARE in state)
        assertFalse(state.edCare.active)
        assertFalse(state.edCare.history)
        assertTrue(Restriction.HIDE_CALORIES !in state.restrictions(SafetyConsumer.TODAY))
    }

    @Test
    fun `without ED_CARE the usual withdrawal deletes the profile and asks for nothing`() = runTest {
        store.setConsent(true)
        store.save(UserConstraints.fromCodes(mapOf(ProfileCategory.DIETS to listOf("vegetarian"))))
        store.setConsent(false)

        assertTrue(edCareRows().isEmpty())
        assertNull(consent(ProfileStore.KEEP_CONSENT_ID))
        assertEquals(false, consent(ProfileStore.CONSENT_ID)?.granted)
        assertTrue(db.nutritionDao().profile(user).isEmpty())
        assertFalse(bus.current().edCare.active)
    }

    @Test
    fun `a later withdrawal with no ends the kept mark and its consent`() = runTest {
        edCareOn()
        store.withdrawConsent(keepEdCare = true)
        store.setConsent(true)
        store.withdrawConsent(keepEdCare = false)

        assertTrue(edCareRows().isEmpty())
        assertEquals(false, consent(ProfileStore.KEEP_CONSENT_ID)?.granted)
        assertFalse(SafetyFlag.ED_CARE in bus.current())
    }

    @Test
    fun `the food of the day folds the kept ED_CARE without the profile`() {
        val none = FoldedProfile.none(consented = false)
        assertEquals(none, none.underEdCare(edCareActive = false))
        val kept = none.underEdCare(edCareActive = true)
        assertEquals(ProfileMode.ED_CARE, kept.mode)
        assertEquals(Level.BASE, kept.levelCap)
        assertTrue(kept.hideNumbers && kept.photoFeedbackOff)
    }

    private fun showWithdraw(edCare: Boolean): MutableList<Boolean> {
        val answers = mutableListOf<Boolean>()
        compose.setContent { HotTheme { ConsentWithdraw(edCareActive = edCare, onWithdraw = { answers += it }) } }
        compose.onNodeWithTag(WITHDRAW_BUTTON_TAG).performClick()
        compose.onNodeWithTag(WITHDRAW_CONFIRM_TAG).performClick()
        return answers
    }

    @Test
    fun `with ED_CARE the question is asked once and yes keeps the mode`() {
        val answers = showWithdraw(edCare = true)
        assertTrue(answers.isEmpty(), "the question comes before the withdrawal")
        compose.onNodeWithTag(KEEP_ED_CARE_YES_TAG).performClick()
        assertEquals(listOf(true), answers)
        compose.onNodeWithTag(KEEP_ED_CARE_YES_TAG).assertDoesNotExist()
    }

    @Test
    fun `with ED_CARE no deletes everything`() {
        val answers = showWithdraw(edCare = true)
        compose.onNodeWithTag(KEEP_ED_CARE_NO_TAG).performClick()
        assertEquals(listOf(false), answers)
    }

    @Test
    fun `without ED_CARE there is no question`() {
        val answers = showWithdraw(edCare = false)
        assertEquals(listOf(false), answers)
        compose.onNodeWithTag(KEEP_ED_CARE_YES_TAG).assertDoesNotExist()
    }

    @Test
    fun `cancel withdraws nothing`() {
        val answers = mutableListOf<Boolean>()
        compose.setContent { HotTheme { ConsentWithdraw(edCareActive = true, onWithdraw = { answers += it }) } }
        compose.onNodeWithTag(WITHDRAW_BUTTON_TAG).performClick()
        compose.onNodeWithTag(WITHDRAW_CANCEL_TAG).performClick()
        assertTrue(answers.isEmpty())
        compose.onNodeWithTag(WITHDRAW_CONFIRM_TAG).assertDoesNotExist()
    }
}
