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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotTopBar
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.today.Corridor
import com.healthofatitan.app.domain.today.DayTask
import com.healthofatitan.app.domain.today.Safety
import com.healthofatitan.app.domain.today.SupportId
import com.healthofatitan.app.domain.today.TaskKind
import com.healthofatitan.app.domain.today.TodayCopy
import com.healthofatitan.app.domain.today.WhyBuilder
import com.healthofatitan.app.feature.today.BreatherScreen
import com.healthofatitan.app.feature.today.DayUiState
import com.healthofatitan.app.feature.today.RITUAL_TAG
import com.healthofatitan.app.feature.today.SupportActions
import com.healthofatitan.app.feature.today.SupportScreen
import com.healthofatitan.app.feature.today.TodayScreen
import com.healthofatitan.app.feature.today.TodayUiState
import com.healthofatitan.app.feature.today.WhyScreen
import com.healthofatitan.app.feature.today.todayView
import com.healthofatitan.app.feature.today.whyInput
import com.healthofatitan.app.integration.healthconnect.PhoneSteps
import com.healthofatitan.app.integration.whoop.BodyCorridors
import com.healthofatitan.app.integration.whoop.CycleGlance
import com.healthofatitan.app.integration.whoop.FacetValue
import com.healthofatitan.app.integration.whoop.RecoveryGlance
import com.healthofatitan.app.integration.whoop.SleepGlance
import com.healthofatitan.app.integration.whoop.SleepStages
import com.healthofatitan.app.integration.whoop.TodayMetrics
import com.healthofatitan.app.integration.whoop.WhoopGlance
import com.healthofatitan.app.integration.whoop.WhoopStatus
import com.healthofatitan.app.integration.whoop.WorkoutGlance
import com.healthofatitan.app.integration.whoop.day.DayItemView
import com.healthofatitan.app.integration.whoop.day.DayView
import com.healthofatitan.app.integration.whoop.day.TodayMarks
import com.healthofatitan.app.navigation.AvatarMenu
import com.healthofatitan.app.navigation.MentorCircle
import com.healthofatitan.app.navigation.TabHeaders
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
import java.time.ZoneId

/**
 * «Сегодня» v3 (A46 v0.3 §4; the top by mockup 05b, A66 §3): the nine scenarios 4.1–4.9, «Почему так», a support and «Передышка»
 * on the Pixel 9 Pro XL width; a tall screen so the whole scroll fits one picture. Placeholder
 * graphics until Agents 4 and 17. Opt-in (HOT_SCREENSHOTS).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h1500dp-xxhdpi")
class TodayV3Screenshots {

    @get:Rule
    val compose = createComposeRule()

    private val out = File("build/screenshots")

    @Before
    fun optIn() {
        assumeTrue(System.getenv("HOT_SCREENSHOTS") != null)
        out.mkdirs()
    }

    private fun shoot(name: String, before: () -> Unit = {}, content: @Composable () -> Unit) {
        compose.setContent { HotTheme { content() } }
        compose.waitForIdle()
        before()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_000)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val today = LocalDate.now()
    private val zone = ZoneId.systemDefault()
    private fun at(h: Int, m: Int = 0): LocalDateTime = today.atTime(h, m)

    private fun facet(v: Double) = FacetValue(value = v, z = 0.2, level = 0, coverage = 1.0)

    private val metrics = TodayMetrics(today, charge = facet(56.0), balance = facet(0.1), recovery = facet(0.3), sleepHours = facet(6.8), sleepDebt = facet(0.6))
    private fun whoop(nights: Int = 28) = WhoopStatus(configured = true, connected = true, whoopUserId = "1", lastSyncAt = System.currentTimeMillis(), baselineNights = nights)

    private val haiku = listOf("Весло за веслом —", "лодка тихо, но плывёт.", "Держи этот ритм.")

    private fun corridor(mean: Double, sd: Double) = Corridor(mean, sd, 28)

    private fun glance(workout: Boolean = false) = WhoopGlance(
        fetchedAt = System.currentTimeMillis(),
        sleep = SleepGlance(
            today, at(6, 40).atZone(zone).toInstant(), 6.8, 7.6, SleepStages(lightMin = 230, deepMin = 88, remMin = 90, awakeMin = 35),
            efficiency = 91.0, consistency = 77.0, disturbances = 7, respiratoryRate = 14.6,
        ),
        recovery = RecoveryGlance(hrvMs = 48.0, restingHr = 57.0, spo2 = 96.5, skinTempC = 33.4),
        workout = if (workout) WorkoutGlance(at(15, 0).atZone(zone).toInstant(), 50, "Weightlifting", 80.0) else null,
        cycle = CycleGlance(at(6, 40).atZone(zone).toInstant(), strain = if (workout) 13.2 else 8.4, kilojoule = 7_800.0, steps = 6_420),
        corridors = BodyCorridors(
            restingHr = corridor(54.0, 2.0),
            hrv = corridor(52.0, 6.0),
            respiratoryRate = corridor(14.4, 0.4),
            spo2 = corridor(96.0, 0.8),
            skinTemp = corridor(33.3, 0.3),
            restingHr7 = 55.0,
        ),
    )

    private fun plan(regime: Regime = Regime.STEADY, training: ItemStatus = ItemStatus.OPEN, flags: Set<DayFlag> = emptySet()) = DayView(
        dayKey = today,
        regime = regime,
        flags = flags,
        headline = regime.ru,
        summary = "",
        fired = emptyList(),
        items = listOf(
            DayItemView("training", ItemKind.TRAINING, "Силовая · 45 мин", "RPE ≤ 7, без отказа", 15 * 60, 15 * 60 + 45, training),
            DayItemView("focus", ItemKind.WORK, "Главная задача", "2 блока по 60 мин", 10 * 60, 12 * 60, ItemStatus.OPEN),
            DayItemView("dinner", ItemKind.NUTRITION, "Ужин", null, 19 * 60, 19 * 60 + 40, ItemStatus.OPEN),
        ),
        bedtimeMin = 23 * 60,
        caffeineCutoffMin = 14 * 60,
        windDownMin = 22 * 60,
    )

    private fun tasks(training: Boolean = false) = listOf(
        DayTask("training", TaskKind.TRAINING, "Силовая · 45 мин", why = "держит неделю тела", weekGoalId = "body-training", week = TodayCopy.week(1, 3), hasMini = true, done = training),
        DayTask("focus", TaskKind.FOCUS, "Главная задача", why = "2 блока по 60 мин", weekGoalId = "work-focus", week = TodayCopy.week(2, 4), hasMini = true),
    )

    private val hourly = List(24) { h -> if (h in 7..14) 700 else 0 }

    private fun day(
        now: LocalDateTime,
        plan: DayView? = plan(),
        marks: TodayMarks = TodayMarks(water = 3, meals = setOf("breakfast")),
        safety: Safety = Safety.NONE,
        returning: Boolean = false,
        dawn: Boolean = false,
        training: Boolean = false,
    ) = DayUiState(
        plan = plan,
        now = now,
        loaded = true,
        steps = PhoneSteps.Ready(6_420, 9_000, hourly = hourly),
        marks = marks,
        candidates = tasks(training),
        safety = safety,
        self = 4,
        haiku = if (dawn) haiku else null,
        dawnShown = dawn,
        returning = returning,
    )

    @Composable
    private fun Screen(state: TodayUiState, day: DayUiState?) {
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                HotTopBar(
                    title = null,
                    mentor = { MentorCircle(MentorPersona.SENSEI) {} },
                    center = TabHeaders.today(day?.today ?: today),
                    avatar = { AvatarMenu(MentorPersona.SENSEI, onHelp = {}, onMentor = {}, onSettings = {}) },
                )
                TodayScreen(state, onConnectWhoop = {}, day = day)
            }
        }
    }

    @Test
    fun noWhoop() = shoot("4.1-no-whoop") { Screen(TodayUiState(), null) }

    /** Mockup 05b (A66 §3.2): Разгон, Заряд 68 above the norm, the haiku, «Сейчас» with its link. */
    @Test
    fun sunAxis05b() = shoot("05b-today-sun-axis") {
        Screen(
            TodayUiState(whoop = whoop(), metrics = metrics.copy(charge = facet(68.0)), glance = glance()),
            day(at(9, 41), plan = plan(Regime.BOOST), marks = TodayMarks(water = 3, meals = setOf("breakfast")), dawn = true),
        )
    }

    @Test
    fun nightPending() = shoot("4.2-night-pending") {
        Screen(TodayUiState(whoop = whoop()), day(at(6, 20), plan = null, marks = TodayMarks()))
    }

    @Test
    fun afterDawn() = shoot("4.3-after-dawn") {
        Screen(TodayUiState(whoop = whoop(), metrics = metrics, glance = glance()), day(at(8, 10), marks = TodayMarks(water = 1), dawn = true))
    }

    @Test
    fun midday() = shoot("4.4-day") {
        Screen(TodayUiState(whoop = whoop(), metrics = metrics, glance = glance()), day(at(13, 5), marks = TodayMarks(water = 4, meals = setOf("breakfast", "lunch")), dawn = true))
    }

    @Test
    fun afterWorkout() = shoot("4.5-after-workout") {
        Screen(
            TodayUiState(whoop = whoop(), metrics = metrics, glance = glance(workout = true)),
            day(at(16, 40), plan = plan(training = ItemStatus.DONE_WHOOP), marks = TodayMarks(water = 6, meals = setOf("breakfast", "lunch")), dawn = true, training = true),
        )
    }

    @Test
    fun evening() = shoot("4.6-evening", before = { compose.onNodeWithTag(RITUAL_TAG + "close_day").performScrollTo().performClick() }) {
        Screen(
            TodayUiState(whoop = whoop(), metrics = metrics, glance = glance()),
            day(at(20, 30), marks = TodayMarks(water = 7, meals = setOf("breakfast", "lunch", "dinner")), dawn = true),
        )
    }

    @Test
    fun gentle() = shoot("4.7-gentle") {
        Screen(
            TodayUiState(whoop = whoop(), metrics = metrics.copy(charge = facet(24.0)), glance = glance()),
            day(at(11, 0), plan = plan(Regime.GENTLE), dawn = true),
        )
    }

    @Test
    fun safety() = shoot("4.8-safety") {
        Screen(TodayUiState(whoop = whoop(), metrics = metrics, glance = glance()), day(at(11, 0), safety = Safety.CRISIS, dawn = true))
    }

    @Test
    fun returning() = shoot("4.9-return") {
        Screen(TodayUiState(whoop = whoop(), metrics = metrics, glance = glance()), day(at(9, 30), returning = true, marks = TodayMarks()))
    }

    @Test
    fun calibration() = shoot("4.x-calibration") {
        Screen(TodayUiState(whoop = whoop(nights = 9), metrics = metrics, glance = glance()), day(at(9, 30), dawn = true))
    }

    @Test
    fun why() = shoot("why-pochemu-tak") {
        val state = TodayUiState(whoop = whoop(), metrics = metrics, glance = glance())
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                HotTopBar(title = TodayCopy.WHY_TITLE, overline = TodayCopy.BLOCK_ME, onBack = {})
                WhyScreen(WhyBuilder.build(whyInput(state, day(at(9, 0), dawn = true))), onAsk = {})
            }
        }
    }

    @Test
    fun supportWater() = shoot("support-water") {
        val state = TodayUiState(whoop = whoop(), metrics = metrics, glance = glance())
        val d = day(at(13, 5))
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                HotTopBar(title = TodayCopy.BLOCK_SUPPORTS, overline = TodayCopy.BLOCK_SUPPORTS, onBack = {})
                SupportScreen(todayView(state, d).supports.first { it.id == SupportId.WATER }, d, SupportActions())
            }
        }
    }

    @Test
    fun breather() = shoot("breather") {
        HotBackground { BreatherScreen(onHelp = {}, onClose = {}) }
    }
}
