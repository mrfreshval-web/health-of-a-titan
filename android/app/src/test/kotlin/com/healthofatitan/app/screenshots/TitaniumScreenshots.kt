package com.healthofatitan.app.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.HotSyncMark
import com.healthofatitan.app.core.ui.component.HotTopBar
import com.healthofatitan.app.core.ui.component.StatusRings
import com.healthofatitan.app.core.ui.component.SyncMarkPhase
import com.healthofatitan.app.core.ui.component.SyncMarkState
import com.healthofatitan.app.core.ui.component.TitanSeal
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.engine.FiredRule
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.engine.RuleRef
import com.healthofatitan.app.feature.today.DayUiState
import com.healthofatitan.app.feature.today.DawnScreen
import com.healthofatitan.app.feature.today.TodayScreen
import com.healthofatitan.app.feature.today.TodayUiState
import com.healthofatitan.app.feature.today.mode
import com.healthofatitan.app.integration.whoop.FacetValue
import com.healthofatitan.app.integration.whoop.TodayMetrics
import com.healthofatitan.app.integration.whoop.WhoopStatus
import com.healthofatitan.app.integration.whoop.day.DawnView
import com.healthofatitan.app.integration.whoop.day.DayItemView
import com.healthofatitan.app.integration.whoop.day.DayView
import com.healthofatitan.app.integration.whoop.day.Haiku
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
 * Screenshots of «Титаниум» for the design review (A37), rendered by Robolectric's native
 * graphics on a Pixel 9 Pro XL sized screen. Opt-in: they run only with HOT_SCREENSHOTS set
 * (workflow «Screenshots»), and write PNGs to app/build/screenshots. Numbers are illustrative.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w448dp-h998dp-xxhdpi")
class TitaniumScreenshots {

    @get:Rule
    val compose = createComposeRule()

    private val out = File("build/screenshots")

    @Before
    fun optIn() {
        assumeTrue(System.getenv("HOT_SCREENSHOTS") != null)
        out.mkdirs()
    }

    private fun shoot(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent { HotTheme(darkTheme = dark) { content() } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_000)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val today = LocalDate.now()
    private val now = LocalDateTime.now().withHour(10).withMinute(40)

    private fun facet(v: Double) = FacetValue(value = v, z = 0.2, level = 0, coverage = 1.0)

    private val metrics = TodayMetrics(today, charge = facet(64.0), balance = facet(0.1), recovery = facet(0.3), sleepHours = facet(7.2), sleepDebt = facet(0.4))
    private val whoop = WhoopStatus(configured = true, connected = true, whoopUserId = "1", lastSyncAt = System.currentTimeMillis(), baselineNights = 21)

    private fun plan(regime: Regime) = DayView(
        dayKey = today,
        regime = regime,
        flags = emptySet(),
        headline = regime.ru,
        summary = "Сон в норме, Заряд у твоей нормы.",
        fired = listOf(
            FiredRule(RuleRef("REG-02", 2), "Заряд у твоей нормы"),
            FiredRule(RuleRef("REG-05", 2), "Сон 7 ч 12 мин"),
            FiredRule(RuleRef("CTX-03", 2), "Вчера — лёгкий день"),
        ),
        items = listOf(
            DayItemView("light", ItemKind.LIGHT, "Свет утром", "10 минут на улице", 8 * 60, 8 * 60 + 10, ItemStatus.DONE_SELF),
            DayItemView("work", ItemKind.WORK, "Глубокая работа", "90 минут без уведомлений", 10 * 60, 11 * 60 + 30, ItemStatus.OPEN),
            DayItemView("training", ItemKind.TRAINING, "Тренировка", "Зона 2, 45 минут", 18 * 60, 18 * 60 + 45, ItemStatus.OPEN),
            DayItemView("sleep", ItemKind.SLEEP, "Отбой", null, 23 * 60, null, ItemStatus.OPEN),
        ),
        bedtimeMin = 23 * 60,
        caffeineCutoffMin = 14 * 60,
        windDownMin = 22 * 60,
    )

    private val rings = StatusRings(strain = 8.4, strainCeiling = 12.0, untilBedMin = 12 * 60 + 20, dayFraction = 0.28f, bedtimeMin = 23 * 60, mode = HotMode.ROVNO)

    @Composable
    private fun TodayWithHeader(regime: Regime) {
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                HotTopBar(title = null, rings = rings.copy(mode = regime.mode()))
                TodayScreen(
                    TodayUiState(whoop = whoop, metrics = metrics),
                    onConnectWhoop = {},
                    day = DayUiState(plan = plan(regime), now = now, loaded = true),
                )
            }
        }
    }

    @Test
    fun todayDarkSteady() = shoot("01-today-dark-rovno") { TodayWithHeader(Regime.STEADY) }

    @Test
    fun todayDarkBoost() = shoot("02-today-dark-razgon") { TodayWithHeader(Regime.BOOST) }

    @Test
    fun todayDarkGentle() = shoot("03-today-dark-berezhno") { TodayWithHeader(Regime.GENTLE) }

    @Test
    fun todayLight() = shoot("04-today-light-farfor", dark = false) { TodayWithHeader(Regime.STEADY) }

    @Test
    fun todayNotConnected() = shoot("05-today-not-connected") {
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                HotTopBar(title = null)
                TodayScreen(TodayUiState(), onConnectWhoop = {})
            }
        }
    }

    private fun dawn(regime: Regime, haiku: Haiku?, sun: Float, charge: Int) = DawnView(
        day = today,
        regime = regime,
        haiku = haiku,
        sleepHours = 7.2,
        charge = charge,
        strainCeiling = 12.0,
        syncedAt = System.currentTimeMillis(),
        sunHeight = sun,
    )

    @Test
    fun dawnBoost() = shoot("06-dawn-razgon") {
        DawnScreen(dawn(Regime.BOOST, Haiku("hk-razgon-001", Regime.BOOST, setOf("sleep_good"), "dawn", "Сон был глубоким.\nСвет идёт по ступеням —\nиди с ним рядом."), 0.9f, 78), onStart = {})
    }

    @Test
    fun dawnSteady() = shoot("07-dawn-rovno") {
        DawnScreen(dawn(Regime.STEADY, Haiku("hk-ravno-001", Regime.STEADY, setOf("any"), "oar", "Весло за веслом —\nлодка тихо, но плывёт.\nДержи этот ритм."), 0.55f, 56), onStart = {})
    }

    @Test
    fun dawnGentle() = shoot("08-dawn-berezhno") {
        DawnScreen(dawn(Regime.GENTLE, Haiku("hk-berezhno-001", Regime.GENTLE, setOf("any"), "titan", "И даже Титан\nсадится на берегу.\nПосиди и ты."), 0.1f, 31), onStart = {})
    }

    @Test
    fun dawnLight() = shoot("09-dawn-rovno-light", dark = false) {
        DawnScreen(dawn(Regime.STEADY, Haiku("hk-ravno-001", Regime.STEADY, setOf("any"), "oar", "Весло за веслом —\nлодка тихо, но плывёт.\nДержи этот ритм."), 0.55f, 56), onStart = {})
    }

    @Test
    fun seal() = shoot("10-seal-whoop") {
        HotBackground {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                TitanSeal(key = "shot", modifier = Modifier.size(360.dp))
            }
        }
    }

    /** «Титан приходит», frames of the storyboard (A37 §5) on a paused clock. */
    @Test
    fun syncArrival() {
        val frames = listOf(0L, 180L, 420L, 680L, 900L, 1150L, 1500L)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HotTheme {
                HotBackground {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        HotSyncMark(SyncMarkState(SyncMarkPhase.RUNNING, progress = 0.8f, key = 1L), size = 360.dp)
                    }
                }
            }
        }
        var at = 0L
        frames.forEachIndexed { i, t ->
            compose.mainClock.advanceTimeBy(t - at)
            at = t
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(out, "11-sync-frame-0${i + 1}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
