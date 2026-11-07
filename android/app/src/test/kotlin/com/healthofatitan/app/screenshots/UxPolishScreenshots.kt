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
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.healthofatitan.app.feature.me.whoop.WhoopActions
import com.healthofatitan.app.feature.me.whoop.WhoopScreen
import com.healthofatitan.app.feature.me.whoop.WhoopUiState
import com.healthofatitan.app.feature.mentor.MentorScreen
import com.healthofatitan.app.feature.path.PathActions
import com.healthofatitan.app.feature.path.PathData
import com.healthofatitan.app.feature.path.PathScreen
import com.healthofatitan.app.feature.path.PathUi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import com.healthofatitan.app.integration.whoop.SleepStages
import com.healthofatitan.app.integration.whoop.StrainGlance
import com.healthofatitan.app.feature.today.DayUiState
import com.healthofatitan.app.feature.today.TodayScreen
import com.healthofatitan.app.feature.today.TodayUiState
import com.healthofatitan.app.integration.healthconnect.PhoneSteps
import com.healthofatitan.app.integration.whoop.FacetValue
import com.healthofatitan.app.integration.whoop.RecoveryGlance
import com.healthofatitan.app.integration.whoop.SleepGlance
import com.healthofatitan.app.integration.whoop.TodayMetrics
import com.healthofatitan.app.integration.whoop.WhoopGlance
import com.healthofatitan.app.integration.whoop.WhoopStatus
import com.healthofatitan.app.integration.whoop.day.DayItemView
import com.healthofatitan.app.integration.whoop.day.DayView
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * «Было / стало» for the UX review of build 119 (docs/ux-review/2026-11-07): the founder's own day
 * (bedtime after midnight, the longest time slots), «Сегодня» morning / midday / evening, the
 * header after a tap, calibration, «Путь», «Настройки», WHOOP and «Наставник». A tall screen
 * (h1700dp) so the whole scroll of «Сегодня» fits one picture. Opt-in (HOT_SCREENSHOTS).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w448dp-h1700dp-xhdpi")
class UxPolishScreenshots {

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

    private fun facet(v: Double, level: Int = 0) = FacetValue(value = v, z = -0.9, level = level, coverage = 1.0)

    private val metrics = TodayMetrics(today, charge = facet(32.0, -1), balance = facet(0.1), recovery = facet(-0.9, -1), sleepHours = facet(6.8), sleepDebt = facet(1.4))
    private val glance = WhoopGlance(
        fetchedAt = System.currentTimeMillis(),
        sleep = SleepGlance(today, Instant.now(), 6.8, 8.1, SleepStages(lightMin = 232, deepMin = 85, remMin = 91, awakeMin = 41)),
        recovery = RecoveryGlance(48.0, 59.0),
        workout = null,
        dayStrain = StrainGlance(Instant.now(), 0.3),
    )

    private fun whoop(nights: Int = 28) = WhoopStatus(configured = true, connected = true, whoopUserId = "1", lastSyncAt = System.currentTimeMillis(), baselineNights = nights)

    /** The founder's plan of 7 November: bedtime 02:50, the longest slots of the review. */
    private fun plan(light: ItemStatus = ItemStatus.OPEN) = DayView(
        dayKey = today,
        regime = Regime.STEADY,
        flags = emptySet(),
        headline = Regime.STEADY.ru,
        summary = "Сон 6 ч 48 мин · Силовая 60 мин в 10:52 · отбой 02:50",
        fired = listOf(
            FiredRule(RuleRef("REG-02", 2), "Восстановление ниже твоего обычного · неделя ниже обычного"),
            FiredRule(RuleRef("REG-03", 1), "Силовых на неделе 0 из 2 — сегодня силовая"),
        ),
        items = listOf(
            DayItemView("light", ItemKind.LIGHT, "Дневной свет 10–20 мин", null, 10 * 60 + 22, 11 * 60 + 22, light),
            DayItemView("training", ItemKind.TRAINING, "Силовая · 60 мин", "Зал B · RPE ≤ 7, без отказа", 10 * 60 + 52, 11 * 60 + 52, ItemStatus.OPEN),
            DayItemView("focus", ItemKind.WORK, "Главная задача", "2 блока по 60 мин", 12 * 60 + 22, 14 * 60 + 22, ItemStatus.OPEN),
            DayItemView("dinner", ItemKind.NUTRITION, "Ужин", null, 23 * 60 + 5, 23 * 60 + 50, ItemStatus.OPEN),
            DayItemView("bedtime", ItemKind.SLEEP, "Отбой", null, 2 * 60 + 50, null, ItemStatus.OPEN),
        ),
        bedtimeMin = 2 * 60 + 50,
        caffeineCutoffMin = 17 * 60 + 50,
        windDownMin = 60 + 50,
    )

    @Composable
    private fun Header(title: String? = null, back: Boolean = false, overline: String? = null, center: String? = null) {
        HotTopBar(
            title = title,
            overline = overline,
            onBack = if (back) ({}) else null,
            mentor = { MentorCircle(MentorPersona.SENSEI) {} },
            center = center,
            avatar = { AvatarMenu(MentorPersona.SENSEI, onHelp = {}, onMentor = {}, onSettings = {}) },
        )
    }

    @Composable
    private fun Today(at: LocalDateTime, nights: Int = 28, day: DayView = plan()) {
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                Header(center = TabHeaders.today(at.toLocalDate()))
                TodayScreen(
                    TodayUiState(whoop = whoop(nights), metrics = if (nights >= 14) metrics else metrics.copy(charge = null), glance = glance),
                    onConnectWhoop = {},
                    day = DayUiState(plan = day, now = at, loaded = true, steps = PhoneSteps.Ready(6_420, 8_900)),
                )
            }
        }
    }

    @Test
    fun todayMorning() = shoot("u01-today-morning") { Today(LocalDateTime.now().withHour(10).withMinute(40)) }

    @Test
    fun todayMidday() = shoot("u02-today-midday") {
        Today(LocalDateTime.now().withHour(13).withMinute(5), day = plan(light = ItemStatus.DONE_SELF))
    }

    @Test
    fun todayEvening() = shoot("u03-today-evening") {
        Today(LocalDateTime.now().withHour(20).withMinute(30), day = plan(light = ItemStatus.DONE_SELF))
    }

    /** R5 on «Сегодня»: the norm is still being learned (9 nights). */
    @Test
    fun todayCalibration() = shoot("u05-today-calibration") {
        Today(LocalDateTime.now().withHour(10).withMinute(40), nights = 9)
    }

    /** R1: the longest values the header and the plan can show. */
    @Test
    fun longValues() = shoot("u06-long-values") {
        Today(
            LocalDateTime.now().withHour(23).withMinute(1),
            day = plan().copy(
                items = plan().items + DayItemView("nap", ItemKind.NAP, "Короткий сон", "до 20 мин", 13 * 60 + 40, 16 * 60 + 40, ItemStatus.OPEN),
            ),
        )
    }

    @Test
    fun path() = shoot("u07-path") {
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                Header(overline = "Путь")
                PathScreen(PathUi(PathData.empty(today)), PathActions(), still = true)
            }
        }
    }

    @Test
    fun settings() = shoot("u08-settings") {
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                Header(title = "Настройки", back = true)
                MeScreen(whoop = whoop(), onOpenWhoop = {})
            }
        }
    }

    @Test
    fun whoopReady() = shoot("u09-whoop-norm-ready") {
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                Header(title = "WHOOP", back = true, overline = "Настройки · Подключения")
                WhoopScreen(WhoopUiState(status = whoop(28)), WhoopActions())
            }
        }
    }

    @Test
    fun whoopLearning() = shoot("u10-whoop-learning") {
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                Header(title = "WHOOP", back = true, overline = "Настройки · Подключения")
                WhoopScreen(WhoopUiState(status = whoop(14)), WhoopActions())
            }
        }
    }

    @Test
    fun mentor() = shoot("u11-mentor") {
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                Header(title = "Наставник", overline = "Винс · ИИ")
                MentorScreen()
            }
        }
    }
}
