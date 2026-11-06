package com.healthofatitan.app.navigation

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.SOS_BUTTON_TAG
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.feature.me.ME_SCREEN_TAG
import com.healthofatitan.app.feature.mentor.MENTOR_SCREEN_TAG
import com.healthofatitan.app.feature.sos.SOS_CALL_911_TAG
import com.healthofatitan.app.feature.sos.SOS_CALL_988_TAG
import com.healthofatitan.app.feature.sos.SOS_SCREEN_TAG
import com.healthofatitan.app.feature.today.TODAY_SCREEN_TAG
import com.healthofatitan.app.feature.week.WEEK_SCREEN_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * A14 §9 step 6 acceptance: 4 tabs open; SOS is 1 tap away from every tab; the SOS buttons
 * open the dialer with 988 / 911 (the user confirms the call).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class HotAppNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private val screens = mapOf(
        HotTab.TODAY to TODAY_SCREEN_TAG,
        HotTab.MENTOR to MENTOR_SCREEN_TAG,
        HotTab.WEEK to WEEK_SCREEN_TAG,
        HotTab.ME to ME_SCREEN_TAG,
    )

    private fun launch(pendingRoute: String? = null) {
        compose.setContent { HotTheme { HotApp(pendingRoute = pendingRoute) } }
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
    fun `sos is one tap away from every tab and closes back`() {
        launch()
        screens.forEach { (tab, screenTag) ->
            compose.onNodeWithTag(tab.testTag).performClick()
            compose.onNodeWithTag(SOS_BUTTON_TAG).performClick() // the single tap
            compose.onNodeWithTag(SOS_SCREEN_TAG).assertExists()
            compose.onNodeWithTag(SOS_CALL_988_TAG).assertExists()
            compose.onNodeWithContentDescription("Закрыть").performClick()
            compose.onNodeWithTag(screenTag).assertExists()
        }
    }

    @Test
    fun `sos deep link from tile or shortcut opens sos`() {
        launch(pendingRoute = HotRoute.SOS)
        compose.onNodeWithTag(SOS_SCREEN_TAG).assertExists()
    }

    @Test
    fun `988 and 911 buttons open the dialer`() {
        launch(pendingRoute = HotRoute.SOS)
        val app = shadowOf(ApplicationProvider.getApplicationContext<Application>())

        compose.onNodeWithTag(SOS_CALL_988_TAG).performClick()
        val crisis = app.nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, crisis.action)
        assertEquals("tel:988", crisis.dataString)

        compose.onNodeWithTag(SOS_CALL_911_TAG).performClick()
        val emergency = app.nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, emergency.action)
        assertEquals("tel:911", emergency.dataString)
    }
}
