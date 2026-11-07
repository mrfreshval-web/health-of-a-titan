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
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.HotTopBar
import com.healthofatitan.app.core.ui.component.StatusRings
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.engine.Body
import com.healthofatitan.app.domain.engine.DayClose
import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.FiredRule
import com.healthofatitan.app.domain.engine.FoodClose
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.engine.RuleRef
import com.healthofatitan.app.domain.engine.TrainingClose
import com.healthofatitan.app.feature.today.DayUiState
import com.healthofatitan.app.feature.today.TodayScreen
import com.healthofatitan.app.feature.today.TodayUiState
import com.healthofatitan.app.feature.today.WelcomeBackScreen
import com.healthofatitan.app.feature.today.mode
import com.healthofatitan.app.integration.healthconnect.PhoneSteps
import com.healthofatitan.app.integration.whoop.FacetValue
import com.healthofatitan.app.integration.whoop.TodayMetrics
import com.healthofatitan.app.integration.whoop.WhoopStatus
import com.healthofatitan.app.integration.whoop.day.DayItemView
import com.healthofatitan.app.integration.whoop.day.DayView
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
 * Stream A screens for the review (A37 «Титаниум»): «Закрыть день», the closed day, «С
 * возвращением», the return day, phone steps and the header ring with steps. Opt-in like
 * [TitaniumScreenshots] (HOT_SCREENSHOTS), Pixel 9 Pro XL size, numbers illustrative.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w448dp-h998dp-xxhdpi")
class StreamAScreenshots {

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
    private val morning = LocalDateTime.now().withHour(10).withMinute(40)
    private val evening = LocalDateTime.now().withHour(20).withMinute(30)

    private fun facet(v: Double) = FacetValue(value = v, z = 0.2, level = 0, coverage = 1.0)

    private val metrics = TodayMetrics(today, charge = facet(58.0), balance = facet(0.1), recovery = facet(0.3), sleepHours = facet(7.0), sleepDebt = facet(0.4))
    private val whoop = WhoopStatus(configured = true, connected = true, whoopUserId = "1", lastSyncAt = System.currentTimeMillis(), baselineNights = 21)

    private fun plan(training: ItemStatus = ItemStatus.OPEN, returning: Boolean = false) = DayView(
        dayKey = today,
        regime = Regime.STEADY,
        flags = if (returning) setOf(DayFlag.RETURN) else emptySet(),
        headline = if (returning) "Рад тебя видеть" else Regime.STEADY.ru,
        summary = if (returning) "Сегодня — минимальная версия: короткая прогулка и один блок." else "Сон в норме, Заряд у твоей нормы.",
        fired = listOf(
            FiredRule(RuleRef("REG-02", 2), "Заряд у твоей нормы"),
            FiredRule(RuleRef(if (returning) "RET-01" else "ACT-01", 1), if (returning) "Возвращение после паузы" else "Прогулка по плану"),
        ),
        items = listOf(
            DayItemView("light", ItemKind.LIGHT, "Свет утром", "10 минут на улице", 8 * 60, 8 * 60 + 10, ItemStatus.DONE_SELF),
            DayItemView("work", ItemKind.WORK, "Глубокая работа", if (returning) "45 минут" else "90 минут без уведомлений", 10 * 60, if (returning) 10 * 60 + 45 else 11 * 60 + 30, ItemStatus.DONE_SELF),
            DayItemView(
                "training", ItemKind.TRAINING, "Прогулка", if (returning) "минимальная версия" else "спокойный темп, 30 минут",
                18 * 60, 18 * 60 + if (returning) 10 else 30, training, RuleRef(if (returning) "RET-01" else "ACT-01", 1),
            ),
            DayItemView("sleep", ItemKind.SLEEP, "Отбой", null, 23 * 60, null, ItemStatus.OPEN),
        ),
        bedtimeMin = 23 * 60,
        caffeineCutoffMin = 14 * 60,
        windDownMin = 22 * 60,
    )

    private val steps = PhoneSteps.Ready(today = 6_420, goal = 8_900, inSlot = 2_150)
    private val rings = StatusRings(strain = 9.1, strainCeiling = 12.0, steps = steps.today, stepsGoal = steps.goal, untilBedMin = 2 * 60 + 30, dayFraction = 0.85f, bedtimeMin = 23 * 60, mode = HotMode.ROVNO)

    @Composable
    private fun Today(day: DayUiState, ringsNow: StatusRings = rings) {
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                HotTopBar(title = null, rings = ringsNow.copy(mode = day.plan?.regime?.mode()))
                TodayScreen(TodayUiState(whoop = whoop, metrics = metrics), onConnectWhoop = {}, day = day)
            }
        }
    }

    @Test
    fun closeDay() = shoot("a01-evening-close-day") {
        Today(DayUiState(plan = plan(), now = evening, loaded = true, steps = steps))
    }

    @Test
    fun closeDayLight() = shoot("a02-evening-close-day-light", dark = false) {
        Today(DayUiState(plan = plan(), now = evening, loaded = true, steps = steps))
    }

    @Test
    fun dayClosed() = shoot("a03-day-closed") {
        Today(
            DayUiState(
                plan = plan(training = ItemStatus.DONE_SELF),
                now = evening,
                loaded = true,
                eveningAnswered = today,
                closed = DayClose(TrainingClose.DONE, FoodClose.PARTLY, Body.FINE, alcohol = 0, planHelped = true),
                steps = steps,
            ),
        )
    }

    @Test
    fun dayClosedNotToday() = shoot("a04-day-closed-not-today") {
        Today(
            DayUiState(
                plan = plan(training = ItemStatus.OPEN),
                now = evening,
                loaded = true,
                eveningAnswered = today,
                closed = DayClose(TrainingClose.NOT_TODAY, FoodClose.JUST_ATE, null, alcohol = null, planHelped = null),
                steps = steps,
            ),
        )
    }

    @Test
    fun welcomeBack() = shoot("a05-welcome-back") {
        HotBackground { WelcomeBackScreen(today = today, onStart = {}) }
    }

    @Test
    fun returnDay() = shoot("a06-return-day") {
        Today(DayUiState(plan = plan(returning = true), now = morning, loaded = true, steps = PhoneSteps.Ready(1_250, 8_900)), rings.copy(steps = 1_250))
    }

    @Test
    fun stepsAsk() = shoot("a07-steps-ask") {
        Today(DayUiState(plan = plan(), now = morning, loaded = true, steps = PhoneSteps.NeedsPermission), rings.copy(steps = null, stepsGoal = null))
    }

    @Test
    fun stepsWalkConfirmed() = shoot("a08-steps-walk-confirmed") {
        Today(DayUiState(plan = plan(), now = evening.withHour(19), loaded = true, steps = steps))
    }
}
