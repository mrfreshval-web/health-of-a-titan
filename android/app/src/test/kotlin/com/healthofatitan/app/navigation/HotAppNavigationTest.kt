package com.healthofatitan.app.navigation

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
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
import com.healthofatitan.app.core.ui.component.LocalHelpCountry
import com.healthofatitan.app.core.ui.component.TOP_BAR_CENTER_TAG
import com.healthofatitan.app.core.ui.component.TOP_BAR_BACK_TAG
import com.healthofatitan.app.core.ui.component.TOP_BAR_TITLE_TAG
import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.feature.me.ME_HELP_ROW_TAG
import com.healthofatitan.app.feature.me.ME_MENTOR_ROW_TAG
import com.healthofatitan.app.feature.me.ME_SCREEN_TAG
import com.healthofatitan.app.feature.me.ME_NUTRITION_ROW_TAG
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
import com.healthofatitan.app.feature.sos.SOS_CALL_CRISIS_TAG
import com.healthofatitan.app.feature.sos.SOS_CALL_EMERGENCY_TAG
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
 * except «Рассвет» (T-05); the tile and the shortcut open help at once; the crisis line and the
 * emergency number of the phone's country open the dialer (the user confirms): 988 / 911 in the
 * US, 8-800-2000-122 / 112 in Russia (Y6).
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
            compose.onNodeWithTag(SOS_CALL_CRISIS_TAG).assertExists()
            compose.onNodeWithTag(SOS_CALL_EMERGENCY_TAG).assertExists()
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

    /** SOS with the help numbers of [country]; how the phone finds its country is HelpDirectoryTest's. */
    private fun assertSosDials(country: String, crisisTel: String, emergencyTel: String) {
        compose.setContent {
            CompositionLocalProvider(LocalHelpCountry provides country) {
                HotTheme { HotApp(pendingRoute = HotRoute.SOS, stateful = false) }
            }
        }
        val app = shadowOf(ApplicationProvider.getApplicationContext<Application>())

        compose.onNodeWithTag(SOS_CALL_CRISIS_TAG).performClick()
        val crisis = app.nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, crisis.action)
        assertEquals(crisisTel, crisis.dataString)

        compose.onNodeWithTag(SOS_CALL_EMERGENCY_TAG).performClick()
        val emergency = app.nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, emergency.action)
        assertEquals(emergencyTel, emergency.dataString)
    }

    @Test
    fun `in the us 988 and 911 open the dialer`() {
        assertSosDials("US", "tel:988", "tel:911")
    }

    @Test
    fun `in russia the trust line and 112 open the dialer`() {
        assertSosDials("RU", "tel:88002000122", "tel:112")
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
}
