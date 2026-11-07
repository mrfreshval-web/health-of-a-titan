package com.healthofatitan.app.navigation

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.STATUS_RINGS_TAG
import com.healthofatitan.app.core.ui.component.TOP_BAR_BACK_TAG
import com.healthofatitan.app.core.ui.component.TOP_BAR_GLYPH_TAG
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
import com.healthofatitan.app.feature.path.PATH_HERO_TAG
import com.healthofatitan.app.feature.path.PATH_PROPOSAL_TAG
import com.healthofatitan.app.feature.path.PATH_SCREEN_TAG
import com.healthofatitan.app.feature.path.PATH_TITAN_TAG
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

/**
 * A14 §9 step 6 acceptance with D-029 and D-033: 4 tabs open («Я» is now «Путь»); the header is
 * the glyph, three rings and the avatar, no SOS button; help is at most 2 taps from every tab
 * («аватар › Помощь», «⋮ › Помощь» in the mentor); the tile and the shortcut open it at once;
 * 112 and 911 open the dialer (the user confirms), the same numbers in every country.
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
        HotTab.MENTOR to MENTOR_SCREEN_TAG,
        HotTab.WEEK to WEEK_SCREEN_TAG,
        HotTab.PATH to PATH_SCREEN_TAG,
    )

    private fun launch(pendingRoute: String? = null) {
        // stateful = false: screens render their default state, no Hilt ViewModels in this test.
        compose.setContent { HotTheme { HotApp(pendingRoute = pendingRoute, stateful = false) } }
    }

    @Test
    fun `starts on Today and all four tabs open`() {
        launch()
        compose.onNodeWithTag(TODAY_SCREEN_TAG).assertExists()
        screens.forEach { (tab, screenTag) ->
            compose.onNodeWithTag(tab.testTag).performClick()
            compose.onNodeWithTag(tab.testTag).assertIsSelected()
            compose.onNodeWithTag(screenTag).assertExists()
        }
    }

    @Test
    fun `header is the glyph and the rings, without an sos button`() {
        launch()
        screens.keys.forEach { tab ->
            compose.onNodeWithTag(tab.testTag).performClick()
            compose.onNodeWithTag(TOP_BAR_GLYPH_TAG).assertExists()
            compose.onNodeWithTag(STATUS_RINGS_TAG).assertExists()
            compose.onNodeWithTag(AVATAR_TAG).assertExists()
            compose.onNodeWithTag("sos_button").assertDoesNotExist()
        }
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
        compose.onNodeWithTag(HotTab.MENTOR.testTag).performClick()
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
        compose.onNodeWithTag(STATUS_RINGS_TAG).assertExists()
        compose.onNodeWithTag(TOP_BAR_BACK_TAG).performClick()
        compose.onNodeWithTag(ME_SCREEN_TAG).assertExists()
    }

    @Test
    fun `path tab shows the titan, a goal from data and your titan`() {
        launch()
        compose.onNodeWithTag(HotTab.PATH.testTag).performClick()
        compose.onNodeWithTag(PATH_HERO_TAG).assertExists()
        compose.onNodeWithTag(PATH_PROPOSAL_TAG).assertExists()
        compose.onNodeWithTag(PATH_TITAN_TAG).assertExists()
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
        compose.onNodeWithTag(TODAY_CONNECT_TAG).performClick()
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
