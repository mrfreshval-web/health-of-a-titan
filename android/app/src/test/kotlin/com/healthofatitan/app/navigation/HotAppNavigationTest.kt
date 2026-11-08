package com.healthofatitan.app.navigation

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.healthofatitan.app.domain.today.RassvetStep
import com.healthofatitan.app.domain.today.TodayBlock
import com.healthofatitan.app.feature.today.BREATHER_HELP_TAG
import com.healthofatitan.app.feature.today.BREATHER_SCREEN_TAG
import com.healthofatitan.app.feature.today.BREATHER_TAG
import com.healthofatitan.app.feature.today.ME_WHY_TAG
import com.healthofatitan.app.feature.today.SUPPORT_SCREEN_TAG
import com.healthofatitan.app.feature.today.SUPPORT_TAG
import com.healthofatitan.app.feature.today.WHY_SCREEN_TAG
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.TOP_BAR_CENTER_TAG
import com.healthofatitan.app.core.ui.component.TOP_BAR_BACK_TAG
import com.healthofatitan.app.core.ui.component.TOP_BAR_TITLE_TAG
import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.core.account.AccountGate
import com.healthofatitan.app.core.database.AgeGate
import com.healthofatitan.app.feature.me.account.SIGN_IN_GOOGLE_TAG
import com.healthofatitan.app.feature.me.account.SIGN_IN_HELP_TAG
import com.healthofatitan.app.feature.me.account.SIGN_IN_TAG
import com.healthofatitan.app.feature.me.account.SIGN_IN_WHOOP_TAG
import com.healthofatitan.app.feature.me.account.SignInActions
import com.healthofatitan.app.feature.me.account.SignInScreen
import com.healthofatitan.app.feature.me.account.SignInUiState
import com.healthofatitan.app.feature.me.ME_HELP_ROW_TAG
import com.healthofatitan.app.feature.me.consent.AGE_GATE_ADULTS_ONLY_TAG
import com.healthofatitan.app.feature.me.consent.AGE_GATE_BACK_TAG
import com.healthofatitan.app.feature.me.consent.AGE_GATE_CHECK_TAG
import com.healthofatitan.app.feature.me.consent.AGE_GATE_CONTINUE_TAG
import com.healthofatitan.app.feature.me.consent.AGE_GATE_HELP_TAG
import com.healthofatitan.app.feature.me.consent.AGE_GATE_SAVE_FAILED_TAG
import com.healthofatitan.app.feature.me.consent.LOADING_TIMEOUT_MS
import com.healthofatitan.app.feature.me.consent.AGE_GATE_TAG
import com.healthofatitan.app.feature.me.consent.AGE_GATE_UNDER_TAG
import com.healthofatitan.app.feature.me.ME_MENTOR_ROW_TAG
import com.healthofatitan.app.feature.me.ME_SCREEN_TAG
import com.healthofatitan.app.feature.me.ME_NUTRITION_ROW_TAG
import com.healthofatitan.app.feature.me.ME_PROFILE_ROW_TAG
import com.healthofatitan.app.feature.me.profile.PROFILE_ME_SCREEN_TAG
import com.healthofatitan.app.feature.me.profile.ProfileTexts
import com.healthofatitan.app.feature.me.profile.addressTag
import com.healthofatitan.app.feature.me.profile.sexTag
import com.healthofatitan.app.core.model.profile.AddressForm
import com.healthofatitan.app.core.model.profile.SexForCalc
import com.healthofatitan.app.feature.me.ME_WHOOP_ROW_TAG
import com.healthofatitan.app.feature.nutrition.NutritionTexts
import com.healthofatitan.app.feature.nutrition.PROFILE_SCREEN_TAG
import com.healthofatitan.app.feature.me.whoop.WHOOP_CONNECT_TAG
import com.healthofatitan.app.feature.me.whoop.WHOOP_SCREEN_TAG
import com.healthofatitan.app.feature.mentor.MENTOR_HELP_TAG
import com.healthofatitan.app.feature.mentor.MENTOR_MENU_TAG
import com.healthofatitan.app.feature.mentor.MENTOR_SCREEN_TAG
import com.healthofatitan.app.feature.path.PATH_EMPTY_TAG
import com.healthofatitan.app.feature.path.PATH_LOOK_BACK_TAG
import com.healthofatitan.app.feature.path.PATH_NAME_GOAL_TAG
import com.healthofatitan.app.feature.path.PATH_SCREEN_TAG
import com.healthofatitan.app.feature.path.PATH_SLICE_TAG
import com.healthofatitan.app.feature.path.RITUAL_HELP_TAG
import com.healthofatitan.app.feature.path.RITUAL_SCREEN_TAG
import com.healthofatitan.app.feature.path.PERSONA_SCREEN_TAG
import com.healthofatitan.app.feature.path.PERSONA_STRONG_WORDS_TAG
import com.healthofatitan.app.feature.path.personaTag
import com.healthofatitan.app.feature.sos.SOS_CALL_112_TAG
import com.healthofatitan.app.feature.sos.SOS_CALL_911_TAG
import com.healthofatitan.app.feature.sos.SOS_SCREEN_TAG
import com.healthofatitan.app.feature.today.TODAY_CONNECT_TAG
import com.healthofatitan.app.feature.today.TODAY_SCREEN_TAG
import com.healthofatitan.app.feature.week.WEEK_SCREEN_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A14 §9 step 6 acceptance with D-029, D-033 and D-038: 4 modules open (Сегодня, Неделя, Путь, За
 * плечами); the header is Винс, the date and the profile, no SOS button and no rings (A46 §2.1);
 * help is at most 2 taps from every module and from «Передышка» (T-06); nothing opens by itself
 * except «Рассвет» (T-05); the tile and the shortcut open help at once; 112 and 911 open the
 * dialer (the user confirms), the same numbers in every country.
 */
@RunWith(AndroidJUnit4::class)
// Pixel 9 Pro XL in dp: the floating tab bar and the hero leave the default 320 × 470 dp screen
// too short for the rows below the fold (a click off screen goes nowhere).
@Config(sdk = [35], application = Application::class, qualifiers = "w448dp-h998dp-xxhdpi")
class HotAppNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private val screens = mapOf(
        HotTab.TODAY to TODAY_SCREEN_TAG,
        HotTab.WEEK to WEEK_SCREEN_TAG,
        HotTab.PATH to PATH_SCREEN_TAG,
    )

    private lateinit var nav: NavHostController

    private fun launch(pendingRoute: String? = null, rassvetDue: RassvetStep? = null, offered: MutableList<Unit> = mutableListOf()) {
        // stateful = false: screens render their default state, no Hilt ViewModels in this test.
        compose.setContent {
            nav = rememberNavController()
            HotTheme {
                HotApp(
                    pendingRoute = pendingRoute,
                    navController = nav,
                    stateful = false,
                    rassvetDue = rassvetDue,
                    rassvetNext = rassvetDue,
                    onRassvetOffered = { offered += Unit },
                )
            }
        }
    }

    @Test
    fun `the three modules are Today, Week and Path`() {
        assertEquals(listOf(HotTab.TODAY, HotTab.WEEK, HotTab.PATH), HotTab.entries.toList())
    }

    @Test
    fun `behind opens from the path by look back, the path stays selected`() {
        launch()
        compose.onNodeWithTag(HotTab.PATH.testTag).performClick()
        compose.onNodeWithTag(PATH_LOOK_BACK_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(BEHIND_SCREEN_TAG).assertExists()
        compose.onNodeWithTag(HotTab.PATH.testTag).assertIsSelected()
    }

    @Test
    fun `starts on Today and all three tabs open`() {
        launch()
        compose.onNodeWithTag(TODAY_SCREEN_TAG).assertExists()
        screens.forEach { (tab, screenTag) ->
            compose.onNodeWithTag(tab.testTag).performClick()
            compose.onNodeWithTag(tab.testTag).assertIsSelected()
            compose.onNodeWithTag(screenTag).assertExists()
        }
    }

    @Test
    fun `header is the mentor, the date and the profile, without an sos button or rings`() {
        launch()
        compose.onNodeWithTag(TOP_BAR_CENTER_TAG).assertExists()
        screens.keys.forEach { tab ->
            compose.onNodeWithTag(tab.testTag).performClick()
            compose.onNodeWithTag(MENTOR_CIRCLE_TAG).assertExists()
            compose.onNodeWithTag(AVATAR_TAG).assertExists()
            compose.onNodeWithTag("sos_button").assertDoesNotExist()
            compose.onNodeWithTag("status_rings").assertDoesNotExist()
        }
    }

    @Test
    fun `today shows the four blocks in their order`() {
        launch()
        TodayBlock.entries.forEach { compose.onNodeWithTag(it.tag).assertExists() }
    }

    @Test
    fun `T-05 nothing leaves Today by itself without the dawn`() {
        val offered = mutableListOf<Unit>()
        launch(offered = offered)
        compose.waitForIdle()
        assertEquals(HotRoute.TODAY, nav.currentDestination?.route)
        assertTrue(offered.isEmpty())
    }

    @Test
    fun `T-05 the dawn opens once at its first step`() {
        val offered = mutableListOf<Unit>()
        launch(rassvetDue = RassvetStep.DECK, offered = offered)
        compose.waitForIdle()
        assertEquals(HotRoute.CHECKIN, nav.currentDestination?.route)
        assertEquals(1, offered.size)
    }

    @Test
    fun `the breather keeps help one tap away`() {
        launch()
        compose.onNodeWithTag(BREATHER_TAG).performScrollTo().performClick() // tap 1
        compose.onNodeWithTag(BREATHER_SCREEN_TAG).assertExists()
        compose.onNodeWithTag(BREATHER_HELP_TAG).performClick() // tap 2
        compose.onNodeWithTag(SOS_SCREEN_TAG).assertExists()
    }

    @Test
    fun `why and a support open from Today and go back`() {
        launch()
        compose.onNodeWithTag(ME_WHY_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(WHY_SCREEN_TAG).assertExists()
        compose.onNodeWithTag(HotTab.TODAY.testTag).assertIsSelected()
        compose.onNodeWithTag(TOP_BAR_BACK_TAG).performClick()
        compose.onNodeWithTag(TODAY_SCREEN_TAG).assertExists()
        compose.onNodeWithTag(SUPPORT_TAG + "water").performScrollTo().performClick()
        compose.onNodeWithTag(SUPPORT_SCREEN_TAG).assertExists()
    }

    @Test
    fun `help is at most two taps from every tab and closes back`() {
        launch()
        screens.forEach { (tab, screenTag) ->
            compose.onNodeWithTag(tab.testTag).performClick()
            compose.onNodeWithTag(AVATAR_TAG).performClick() // tap 1
            compose.onNodeWithTag(AVATAR_HELP_TAG).performClick() // tap 2
            compose.onNodeWithTag(SOS_SCREEN_TAG).assertExists()
            compose.onNodeWithTag(SOS_CALL_112_TAG).assertExists()
            compose.onNodeWithTag(SOS_CALL_911_TAG).assertExists()
            compose.onNodeWithContentDescription("Закрыть").performClick()
            compose.onNodeWithTag(screenTag).assertExists()
        }
    }

    @Test
    fun `mentor menu opens help in two taps`() {
        launch()
        compose.onNodeWithTag(MENTOR_CIRCLE_TAG).performClick()
        compose.onNodeWithTag(MENTOR_MENU_TAG).performClick() // tap 1
        compose.onNodeWithTag(MENTOR_HELP_TAG).performClick() // tap 2
        compose.onNodeWithTag(SOS_SCREEN_TAG).assertExists()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithTag(MENTOR_SCREEN_TAG).assertExists()
    }

    @Test
    fun `sos deep link from tile or shortcut opens sos`() {
        launch(pendingRoute = HotRoute.SOS)
        compose.onNodeWithTag(SOS_SCREEN_TAG).assertExists()
    }

    @Test
    fun `112 and 911 buttons open the dialer`() {
        launch(pendingRoute = HotRoute.SOS)
        val app = shadowOf(ApplicationProvider.getApplicationContext<Application>())

        compose.onNodeWithTag(SOS_CALL_112_TAG).performClick()
        val first = app.nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, first.action)
        assertEquals("tel:112", first.dataString)

        compose.onNodeWithTag(SOS_CALL_911_TAG).performClick()
        val second = app.nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, second.action)
        assertEquals("tel:911", second.dataString)
    }

    @Test
    fun `settings under the avatar open whoop connections and back returns to settings`() {
        launch()
        compose.onNodeWithTag(AVATAR_TAG).performClick()
        compose.onNodeWithTag(AVATAR_SETTINGS_TAG).performClick()
        compose.onNodeWithTag(ME_SCREEN_TAG).assertExists()
        compose.onNodeWithTag(ME_HELP_ROW_TAG).assertExists() // help stays the first row of settings
        compose.onNodeWithTag(ME_WHOOP_ROW_TAG).performClick()
        compose.onNodeWithTag(WHOOP_SCREEN_TAG).assertExists()
        compose.onNodeWithTag(WHOOP_CONNECT_TAG).assertExists()
        compose.onNodeWithTag(TOP_BAR_TITLE_TAG).assertTextEquals("WHOOP")
        compose.onNodeWithTag(HotTab.PATH.testTag).assertIsSelected()
        compose.onNodeWithTag(TOP_BAR_BACK_TAG).performClick()
        compose.onNodeWithTag(ME_SCREEN_TAG).assertExists()
    }

    @Test
    fun `path tab shows the slice of the world and names a goal in the ritual, help in one tap`() {
        launch()
        compose.onNodeWithTag(HotTab.PATH.testTag).performClick()
        compose.onNodeWithTag(PATH_SLICE_TAG).assertExists()
        compose.onNodeWithTag(PATH_EMPTY_TAG).assertExists()
        compose.onNodeWithTag(PATH_NAME_GOAL_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(RITUAL_SCREEN_TAG).assertExists()
        compose.onNodeWithTag(RITUAL_HELP_TAG).performClick()
        compose.onNodeWithTag(SOS_SCREEN_TAG).assertExists()
    }

    @Test
    fun `avatar opens the mentor character, strong words stay off`() {
        launch()
        compose.onNodeWithTag(AVATAR_TAG).performClick()
        compose.onNodeWithTag(AVATAR_MENTOR_TAG).performClick()
        compose.onNodeWithTag(PERSONA_SCREEN_TAG).assertExists()
        compose.onNodeWithTag(TOP_BAR_TITLE_TAG).assertTextEquals("Наставник")
        compose.onNodeWithTag(personaTag(MentorPersona.SENSEI)).assertIsSelected()
        compose.onNodeWithTag(personaTag(MentorPersona.BRO)).assertExists()
        compose.onNodeWithTag(personaTag(MentorPersona.BUTLER)).assertExists()
        compose.onNodeWithTag(PERSONA_STRONG_WORDS_TAG).assertDoesNotExist() // only shown for Бро
    }

    @Test
    fun `settings lead to the mentor character too`() {
        launch()
        compose.onNodeWithTag(AVATAR_TAG).performClick()
        compose.onNodeWithTag(AVATAR_SETTINGS_TAG).performClick()
        compose.onNodeWithTag(ME_MENTOR_ROW_TAG).performClick()
        compose.onNodeWithTag(PERSONA_SCREEN_TAG).assertExists()
    }

    @Test
    fun `today without whoop offers to connect it`() {
        launch()
        compose.onNodeWithTag(TODAY_CONNECT_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(WHOOP_SCREEN_TAG).assertExists()
    }

    @Test
    fun `settings open the food profile inside the path tab and back returns to settings`() {
        launch()
        compose.onNodeWithTag(AVATAR_TAG).performClick()
        compose.onNodeWithTag(AVATAR_SETTINGS_TAG).performClick()
        compose.onNodeWithTag(ME_NUTRITION_ROW_TAG).performClick()
        compose.onNodeWithTag(PROFILE_SCREEN_TAG).assertExists()
        compose.onNodeWithTag(TOP_BAR_TITLE_TAG).assertTextEquals(NutritionTexts.SET_UP)
        compose.onNodeWithTag(HotTab.PATH.testTag).assertIsSelected()
        compose.onNodeWithTag(TOP_BAR_BACK_TAG).performClick()
        compose.onNodeWithTag(ME_SCREEN_TAG).assertExists()
    }

    @Test
    fun `settings open the profile with the address form, neutral until chosen`() {
        // A61 §5, D-055: the card «Ты» on top of the settings, help right under it.
        launch()
        compose.onNodeWithTag(AVATAR_TAG).performClick()
        compose.onNodeWithTag(AVATAR_SETTINGS_TAG).performClick()
        compose.onNodeWithTag(ME_HELP_ROW_TAG).assertExists()
        compose.onNodeWithTag(ME_PROFILE_ROW_TAG).performClick()
        compose.onNodeWithTag(PROFILE_ME_SCREEN_TAG).assertExists()
        compose.onNodeWithTag(TOP_BAR_TITLE_TAG).assertTextEquals(ProfileTexts.TITLE)
        compose.onNodeWithTag(HotTab.PATH.testTag).assertIsSelected()
        compose.onNodeWithTag(addressTag(AddressForm.NEUTRAL)).assertIsSelected()
        compose.onNodeWithTag(addressTag(AddressForm.FEMININE)).performClick()
        compose.onNodeWithTag(addressTag(AddressForm.FEMININE)).assertIsSelected()
        compose.onNodeWithTag(addressTag(AddressForm.NEUTRAL)).assertIsNotSelected()
        // the sex for calculations is a separate field: still not given
        compose.onNodeWithTag(sexTag(SexForCalc.UNSPECIFIED)).performScrollTo().assertIsSelected()
        compose.onNodeWithTag(TOP_BAR_BACK_TAG).performClick()
        compose.onNodeWithTag(ME_SCREEN_TAG).assertExists()
    }

    // ── The gate 18+ (R-13, A61 §1.2) ───────────────────────────────────────────────────────────

    private var gate by mutableStateOf(AgeGate.ASK)

    private fun launchGated(pendingRoute: String? = null, confirmed: MutableList<Unit> = mutableListOf()) {
        gate = AgeGate.ASK
        compose.setContent {
            nav = rememberNavController()
            HotTheme {
                HotApp(
                    pendingRoute = pendingRoute,
                    navController = nav,
                    stateful = false,
                    ageGate = gate,
                    onAdultConfirmed = {
                        confirmed += Unit
                        gate = AgeGate.CONFIRMED
                    },
                )
            }
        }
    }

    @Test
    fun `R-13 without age18 the app does not go on until the person confirms 18 or more`() {
        val confirmed = mutableListOf<Unit>()
        launchGated(confirmed = confirmed)
        compose.onNodeWithTag(AGE_GATE_TAG).assertExists()
        // nothing of the app is reachable behind the gate, and «Рассвет» does not open over it
        compose.onNodeWithTag(TODAY_SCREEN_TAG).assertDoesNotExist()
        compose.onNodeWithTag(HotTab.WEEK.testTag).assertDoesNotExist()
        assertEquals(HotRoute.TODAY, nav.currentDestination?.route)

        compose.onNodeWithTag(AGE_GATE_CONTINUE_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(AGE_GATE_CHECK_TAG).performClick()
        compose.onNodeWithTag(AGE_GATE_CONTINUE_TAG).assertIsEnabled().performClick()
        assertEquals(1, confirmed.size)

        compose.onNodeWithTag(AGE_GATE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(TODAY_SCREEN_TAG).assertExists()
        compose.onNodeWithTag(HotTab.WEEK.testTag).assertExists()
    }

    @Test
    fun `R-13 the dawn does not open over the gate, only after it`() {
        val offered = mutableListOf<Unit>()
        gate = AgeGate.ASK
        compose.setContent {
            nav = rememberNavController()
            HotTheme {
                HotApp(
                    navController = nav,
                    stateful = false,
                    rassvetDue = RassvetStep.DECK,
                    rassvetNext = RassvetStep.DECK,
                    onRassvetOffered = { offered += Unit },
                    ageGate = gate,
                    onAdultConfirmed = { gate = AgeGate.CONFIRMED },
                )
            }
        }
        compose.waitForIdle()
        assertEquals(HotRoute.TODAY, nav.currentDestination?.route)
        assertTrue(offered.isEmpty())
        compose.onNodeWithTag(AGE_GATE_CHECK_TAG).performClick()
        compose.onNodeWithTag(AGE_GATE_CONTINUE_TAG).performClick()
        compose.waitForIdle()
        assertEquals(HotRoute.CHECKIN, nav.currentDestination?.route)
        assertEquals(1, offered.size)
    }

    @Test
    fun `R-13 under 18 sees adults only with help one tap away, and nothing is stored`() {
        val confirmed = mutableListOf<Unit>()
        launchGated(confirmed = confirmed)
        compose.onNodeWithTag(AGE_GATE_UNDER_TAG).performClick()
        compose.onNodeWithTag(AGE_GATE_ADULTS_ONLY_TAG).assertExists()
        compose.onNodeWithTag(AGE_GATE_HELP_TAG).performClick()
        compose.onNodeWithTag(SOS_SCREEN_TAG).assertExists()
        compose.onNodeWithTag(SOS_CALL_112_TAG).assertExists()
        compose.onNodeWithTag(SOS_CALL_911_TAG).assertExists()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithTag(AGE_GATE_TAG).assertExists()
        compose.onNodeWithTag(TODAY_SCREEN_TAG).assertDoesNotExist()
        assertTrue(confirmed.isEmpty())
        compose.onNodeWithTag(AGE_GATE_BACK_TAG).performClick()
        compose.onNodeWithTag(AGE_GATE_CHECK_TAG).assertExists()
    }

    @Test
    fun `R-13 help is never behind the gate, the tile and the shortcut open sos at once`() {
        launchGated(pendingRoute = HotRoute.SOS)
        compose.onNodeWithTag(SOS_SCREEN_TAG).assertExists()
        compose.onNodeWithTag(AGE_GATE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SOS_CALL_112_TAG).assertExists()
    }

    @Test
    fun `R-13 while the consent journal loads help is on screen, then the question comes`() {
        gate = AgeGate.LOADING
        compose.mainClock.autoAdvance = false
        compose.setContent {
            nav = rememberNavController()
            HotTheme { HotApp(navController = nav, stateful = false, ageGate = gate) }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(AGE_GATE_HELP_TAG).assertExists()
        compose.onNodeWithTag(AGE_GATE_CHECK_TAG).assertDoesNotExist()
        compose.onNodeWithTag(TODAY_SCREEN_TAG).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(LOADING_TIMEOUT_MS + 500)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag(AGE_GATE_CHECK_TAG).assertExists()
    }

    @Test
    fun `R-13 a failed save says so`() {
        compose.setContent {
            nav = rememberNavController()
            HotTheme { HotApp(navController = nav, stateful = false, ageGate = AgeGate.ASK, ageGateSaveFailed = true) }
        }
        compose.onNodeWithTag(AGE_GATE_SAVE_FAILED_TAG).assertExists()
    }

    @Test
    fun `R-13 help from the question itself opens sos`() {
        launchGated()
        compose.onNodeWithTag(AGE_GATE_HELP_TAG).performClick()
        compose.onNodeWithTag(SOS_SCREEN_TAG).assertExists()
    }

    // ── «Вход» (A65, D-062): after the gate 18+, never over SOS ─────────────────────────────────

    private var account by mutableStateOf(AccountGate.SIGNED_OUT)

    private fun launchSignIn(ageGate: AgeGate) {
        gate = ageGate
        compose.setContent {
            nav = rememberNavController()
            HotTheme {
                HotApp(
                    navController = nav,
                    stateful = false,
                    ageGate = gate,
                    onAdultConfirmed = { gate = AgeGate.CONFIRMED },
                    accountGate = account,
                    signIn = { onHelp -> SignInScreen(SignInUiState(), SignInActions(help = onHelp)) },
                )
            }
        }
    }

    @Test
    fun `sign-in comes after the gate 18+ and closes when the account appears`() {
        account = AccountGate.SIGNED_OUT
        launchSignIn(AgeGate.ASK)
        compose.onNodeWithTag(AGE_GATE_TAG).assertExists()
        compose.onNodeWithTag(SIGN_IN_TAG).assertDoesNotExist()
        compose.onNodeWithTag(AGE_GATE_CHECK_TAG).performClick()
        compose.onNodeWithTag(AGE_GATE_CONTINUE_TAG).performClick()
        compose.onNodeWithTag(SIGN_IN_TAG).assertExists()
        compose.onNodeWithTag(SIGN_IN_GOOGLE_TAG).assertExists()
        compose.onNodeWithTag(SIGN_IN_WHOOP_TAG).assertExists()
        compose.onNodeWithTag(TODAY_SCREEN_TAG).assertDoesNotExist()
        account = AccountGate.SIGNED_IN
        compose.waitForIdle()
        compose.onNodeWithTag(SIGN_IN_TAG).assertDoesNotExist()
        compose.onNodeWithTag(TODAY_SCREEN_TAG).assertExists()
    }

    @Test
    fun `help on sign-in opens sos over it`() {
        account = AccountGate.SIGNED_OUT
        launchSignIn(AgeGate.CONFIRMED)
        compose.onNodeWithTag(SIGN_IN_HELP_TAG).performClick()
        compose.onNodeWithTag(SOS_SCREEN_TAG).assertExists()
        compose.onNodeWithTag(SOS_CALL_112_TAG).assertExists()
        compose.onNodeWithTag(SIGN_IN_TAG).assertDoesNotExist()
    }

    @Test
    fun `no sign-in while migrating hs1 or in a build without the Worker`() {
        account = AccountGate.MIGRATING
        launchSignIn(AgeGate.CONFIRMED)
        for (state in listOf(AccountGate.MIGRATING, AccountGate.NOT_REQUIRED, AccountGate.LOADING)) {
            account = state
            compose.waitForIdle()
            compose.onNodeWithTag(SIGN_IN_TAG).assertDoesNotExist()
            compose.onNodeWithTag(TODAY_SCREEN_TAG).assertExists()
        }
    }
}
