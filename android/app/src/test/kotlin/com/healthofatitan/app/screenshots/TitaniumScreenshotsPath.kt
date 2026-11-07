package com.healthofatitan.app.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.datastore.MentorPrefs
import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.HotTopBar
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.engine.FiredRule
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.engine.RuleRef
import com.healthofatitan.app.feature.me.MeScreen
import com.healthofatitan.app.feature.path.PersonaScreen
import com.healthofatitan.app.feature.today.DayUiState
import com.healthofatitan.app.feature.today.TodayScreen
import com.healthofatitan.app.feature.today.TodaySlots
import com.healthofatitan.app.feature.today.TodayUiState
import com.healthofatitan.app.feature.today.mode
import com.healthofatitan.app.integration.whoop.FacetValue
import com.healthofatitan.app.integration.whoop.TodayMetrics
import com.healthofatitan.app.integration.whoop.WhoopStatus
import com.healthofatitan.app.integration.whoop.day.DayItemView
import com.healthofatitan.app.integration.whoop.day.DayView
import com.healthofatitan.app.navigation.AvatarMenu
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Stream Г screenshots (D-033, D-030): the mentor's character, settings and the step in «Сегодня»; «Путь» v1 lives in [PathV1Screenshots]. Same rig as [TitaniumScreenshots]: Robolectric
 * native graphics on a Pixel 9 Pro XL screen, opt-in with HOT_SCREENSHOTS. Numbers are illustrative.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w448dp-h998dp-xxhdpi")
class TitaniumScreenshotsPath {

    @get:Rule
    val compose = createComposeRule()

    private val out = File("build/screenshots")

    @Before
    fun optIn() {
        assumeTrue(System.getenv("HOT_SCREENSHOTS") != null)
        out.mkdirs()
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { HotTheme { content() } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_000)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Header(title: String? = null, back: Boolean = false, mode: HotMode = HotMode.ROVNO, persona: MentorPersona = MentorPersona.SENSEI) {
        HotTopBar(
            title = title,
            overline = if (back) null else "Путь",
            onBack = if (back) ({}) else null,
            avatar = { AvatarMenu(persona, onHelp = {}, onMentor = {}, onSettings = {}) },
        )
    }

    private val today = LocalDate.now()

    private val whoop = WhoopStatus(configured = true, connected = true, whoopUserId = "1", lastSyncAt = System.currentTimeMillis(), baselineNights = 21)

    private fun facet(v: Double) = FacetValue(value = v, z = 0.2, level = 0, coverage = 1.0)

    private fun plan(regime: Regime) = DayView(
        dayKey = today,
        regime = regime,
        flags = emptySet(),
        headline = regime.ru,
        summary = "Ночь короче обычной, бережём силы.",
        fired = listOf(FiredRule(RuleRef("REG-02", 2), "Заряд ниже твоей нормы")),
        items = listOf(
            DayItemView("walk", ItemKind.TRAINING, "Прогулка", "40 минут", 18 * 60, 18 * 60 + 40, ItemStatus.OPEN),
            DayItemView("sleep", ItemKind.SLEEP, "Отбой", null, 22 * 60 + 30, null, ItemStatus.OPEN),
        ),
        bedtimeMin = 22 * 60 + 30,
        caffeineCutoffMin = 13 * 60,
        windDownMin = 21 * 60 + 30,
    )

    private fun shootToday(regime: Regime) = shoot("22-today-step-${regime.name.lowercase()}") {
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                HotTopBar(title = null, avatar = { AvatarMenu(MentorPersona.SENSEI, {}, {}, {}) })
                val metrics = TodayMetrics(today, charge = facet(31.0), balance = facet(0.1), recovery = facet(-1.2), sleepHours = facet(5.2), sleepDebt = facet(2.4))
                TodayScreen(
                    TodayUiState(whoop = whoop, metrics = metrics),
                    onConnectWhoop = {},
                    day = DayUiState(plan = plan(regime), now = LocalDateTime.now().withHour(20).withMinute(5), loaded = true),
                )
            }
        }
    }

    @Test
    fun todayStepGentle() = shootToday(Regime.GENTLE)

    @Test
    fun personaChoice() = shoot("23-mentor-persona") {
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                Header(title = "Наставник", back = true, persona = MentorPersona.BUTLER)
                PersonaScreen(MentorPrefs(persona = MentorPersona.BUTLER), onPick = {})
            }
        }
    }

    @Test
    fun personaBro() = shoot("24-mentor-persona-bro") {
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                Header(title = "Наставник", back = true, persona = MentorPersona.BRO)
                PersonaScreen(MentorPrefs(persona = MentorPersona.BRO), onPick = {})
            }
        }
    }

    @Test
    fun settings() = shoot("25-settings") {
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                Header(title = "Настройки", back = true)
                MeScreen(whoop = whoop, onOpenWhoop = {})
            }
        }
    }
}
