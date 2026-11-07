package com.healthofatitan.app.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.datastore.CheckinPrefs
import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotTopBar
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.checkin.Answer
import com.healthofatitan.app.domain.checkin.BodyArea
import com.healthofatitan.app.domain.checkin.CardId
import com.healthofatitan.app.domain.checkin.DeckContext
import com.healthofatitan.app.domain.checkin.DeckEngine
import com.healthofatitan.app.domain.checkin.DeckSettings
import com.healthofatitan.app.domain.checkin.DeckState
import com.healthofatitan.app.domain.checkin.Discovery
import com.healthofatitan.app.domain.checkin.Link
import com.healthofatitan.app.domain.checkin.Pain
import com.healthofatitan.app.domain.checkin.Phase
import com.healthofatitan.app.domain.checkin.PrivateLayer
import com.healthofatitan.app.domain.checkin.Tile
import com.healthofatitan.app.domain.engine.Body
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.engine.RuleRef
import com.healthofatitan.app.feature.checkin.BodyMap
import com.healthofatitan.app.feature.checkin.CareScreen
import com.healthofatitan.app.feature.checkin.CheckinNav
import com.healthofatitan.app.feature.checkin.CheckinSettingsScreen
import com.healthofatitan.app.feature.checkin.CheckinTexts
import com.healthofatitan.app.feature.checkin.CheckinTodayContent
import com.healthofatitan.app.feature.checkin.DeckScreen
import com.healthofatitan.app.feature.checkin.DeckUi
import com.healthofatitan.app.feature.checkin.DiscoveryScreen
import com.healthofatitan.app.feature.checkin.EveningKnownLine
import com.healthofatitan.app.feature.checkin.EveningRowFrame
import com.healthofatitan.app.feature.checkin.EveningWeatherContent
import com.healthofatitan.app.feature.checkin.MorningState
import com.healthofatitan.app.feature.checkin.PainAnswer
import com.healthofatitan.app.feature.checkin.RevealFacts
import com.healthofatitan.app.feature.checkin.RevealScreen
import com.healthofatitan.app.feature.checkin.RevealUi
import com.healthofatitan.app.feature.checkin.SettingsUi
import com.healthofatitan.app.feature.checkin.TodayCheckin
import com.healthofatitan.app.feature.today.CloseDayExtras
import com.healthofatitan.app.feature.today.DayUiState
import com.healthofatitan.app.feature.today.TodayScreen
import com.healthofatitan.app.feature.today.TodaySlots
import com.healthofatitan.app.feature.today.TodayUiState
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * State cards (A44 v0.2, D-036) for the review: the blind wake deck, the reveal «ты ↔ WHOOP»,
 * «После ритуала» and its delta, «Только ты», the discovery and the collection, the evening inside
 * «Закрыть день», the pain answer, the storm net screen, «Что спрашивать» and the «Сегодня» cards.
 * Opt-in (HOT_SCREENSHOTS), Pixel 9 Pro XL size; numbers illustrative.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w448dp-h998dp-xxhdpi")
class StateCardsScreenshots {

    @get:Rule
    val compose = createComposeRule()

    private val out = File("build/screenshots")

    @Before
    fun optIn() {
        assumeTrue(System.getenv("HOT_SCREENSHOTS") != null)
        out.mkdirs()
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { HotTheme(darkTheme = true) { HotBackground { content() } } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_000)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val today = LocalDate.now()
    private val wakeAt = LocalDateTime.now().withHour(7).withMinute(12)
    private val persona = MentorPersona.SENSEI

    private val settings = DeckSettings(privateLayer = PrivateLayer(intimacy = true, pulls = true), stack = listOf("Витамин D", "Омега-3"))
    private val ctx = DeckContext(day = today, whoopSawNight = true, eveningAlcoholMarked = false)
    private val wakeCards = DeckEngine.wake(settings, ctx)

    private fun deck(
        at: CardId,
        answers: Map<CardId, Answer> = emptyMap(),
        unlocked: Boolean = false,
        phase: Phase = Phase.WAKE,
        cards: List<CardId> = wakeCards,
    ) = DeckUi(
        phase = phase,
        day = today,
        deck = DeckState(cards = cards, index = cards.indexOf(at).coerceAtLeast(0), answers = answers),
        settings = settings,
        context = ctx,
        unlocked = unlocked,
    )

    private val earlyAnswers = mapOf(
        CardId.CHARGE to Answer(CardId.CHARGE, level = 2),
        CardId.WEATHER to Answer(CardId.WEATHER, level = 3, choices = setOf("TIRED")),
    )

    @Test
    fun wakeCharge() = shoot("sc01-wake-charge") { DeckScreen(deck(CardId.CHARGE), persona) }

    @Test
    fun wakeWeather() = shoot("sc02-wake-weather") {
        DeckScreen(deck(CardId.WEATHER, mapOf(CardId.CHARGE to Answer(CardId.CHARGE, level = 2))), persona)
    }

    @Test
    fun wakeBody() = shoot("sc03-wake-body-map") {
        DeckScreen(
            deck(CardId.BODY, earlyAnswers + (CardId.BODY to Answer(CardId.BODY, level = 2, choices = setOf("SHOULDERS", "LOWER_BACK")))),
            persona,
        )
    }

    @Test
    fun wakeHead() = shoot("sc04-wake-head") { DeckScreen(deck(CardId.HEAD, earlyAnswers), persona) }

    @Test
    fun wakeYesterday() = shoot("sc05-wake-yesterday") {
        DeckScreen(deck(CardId.YESTERDAY, earlyAnswers + (CardId.HEAD to Answer(CardId.HEAD, level = 2))), persona)
    }

    @Test
    fun wakeOnlyYouLocked() = shoot("sc06-wake-only-you-locked") { DeckScreen(deck(CardId.ONLY_YOU, earlyAnswers), persona) }

    @Test
    fun wakeOnlyYou() = shoot("sc07-wake-only-you") { DeckScreen(deck(CardId.ONLY_YOU, earlyAnswers, unlocked = true), persona) }

    private fun reveal(you: Int?, data: Int?, regime: Regime?) = RevealUi(
        day = today,
        you = you,
        data = data,
        quick = false,
        gentle = regime == Regime.GENTLE,
        whoopConnected = true,
        regime = regime,
        facts = RevealFacts(matched = 9, total = 14, mornings = 14),
        ritualLater = true,
    )

    @Test
    fun revealMatch() = shoot("sc08-reveal-match") { RevealScreen(reveal(4, 4, Regime.STEADY), persona, onDone = {}) }

    @Test
    fun revealDiverge() = shoot("sc09-reveal-diverge") { RevealScreen(reveal(2, 5, Regime.BOOST), MentorPersona.BRO, onDone = {}) }

    @Test
    fun revealWaiting() = shoot("sc10-reveal-waiting") { RevealScreen(reveal(3, null, null), persona, onDone = {}) }

    @Test
    fun ritual() = shoot("sc11-ritual") {
        val cards = DeckEngine.ritual(settings)
        DeckScreen(deck(CardId.RITUAL, phase = Phase.RITUAL, cards = cards).copy(prefill = setOf("COLD_SHOWER", "LIGHT", "stack:Витамин D")), persona)
    }

    @Test
    fun ritualDelta() = shoot("sc12-ritual-delta") {
        val cards = DeckEngine.ritual(settings)
        val answers = mapOf(
            CardId.RITUAL to Answer(CardId.RITUAL, choices = setOf("COLD_SHOWER", "LIGHT")),
            CardId.CHARGE_AFTER to Answer(CardId.CHARGE_AFTER, level = 4),
        )
        DeckScreen(
            DeckUi(Phase.RITUAL, today, DeckState(cards, cards.size, answers), settings, ctx, wakeCharge = 2, wakeAt = wakeAt, finished = true),
            persona,
        )
    }

    private val tiles: List<Tile> = Link.entries.mapIndexed { i, l ->
        when {
            l.s5 -> Tile.Locked(l)
            i == 0 -> Tile.Open(l, Discovery(l, 11, 9, 1.0, 0.5, 1.5), Instant.now())
            i == 4 -> Tile.Open(l, Discovery(l, 9, 12, 0.6, 0.2, 0.9), Instant.now())
            else -> Tile.Progress(l, n1 = (i * 2) % 9, n0 = 8, left = 8 - (i * 2) % 9)
        }
    }

    @Test
    fun discovery() = shoot("sc13-discovery") {
        DiscoveryScreen(Discovery(Link.SHOWER_CHARGE, 11, 9, 1.0, 0.5, 1.5), tiles, persona, onClose = {})
    }

    @Test
    fun collection() = shoot("sc14-collection") { DiscoveryScreen(null, tiles, persona, onClose = {}) }

    private val metrics = TodayMetrics(
        today,
        charge = FacetValue(58.0, 0.2, 0, 1.0),
        balance = FacetValue(0.1, 0.2, 0, 1.0),
        recovery = FacetValue(0.3, 0.2, 0, 1.0),
        sleepHours = FacetValue(7.0, 0.2, 0, 1.0),
        sleepDebt = FacetValue(0.4, 0.2, 0, 1.0),
    )
    private val whoop = WhoopStatus(configured = true, connected = true, whoopUserId = "1", lastSyncAt = System.currentTimeMillis(), baselineNights = 21)
    private val plan = DayView(
        dayKey = today,
        regime = Regime.STEADY,
        flags = emptySet(),
        headline = Regime.STEADY.ru,
        summary = "Сон в норме, Заряд у твоей нормы.",
        fired = emptyList(),
        items = listOf(
            DayItemView("light", ItemKind.LIGHT, "Свет утром", "10 минут на улице", 8 * 60, 8 * 60 + 10, ItemStatus.DONE_SELF),
            DayItemView("training", ItemKind.TRAINING, "Прогулка", "спокойный темп, 30 минут", 18 * 60, 18 * 60 + 30, ItemStatus.OPEN, RuleRef("ACT-01", 1)),
            DayItemView("sleep", ItemKind.SLEEP, "Отбой", null, 23 * 60, null, ItemStatus.OPEN),
        ),
        bedtimeMin = 23 * 60,
        caffeineCutoffMin = 14 * 60,
        windDownMin = 22 * 60,
    )

    @Test
    fun eveningCloseDay() = shoot("sc15-evening-close-day") {
        val sunday = today.with(java.time.DayOfWeek.SUNDAY)
        val extras = CloseDayExtras(
            top = { EveningKnownLine(Modifier.padding(bottom = 10.dp)) },
            afterBody = { _, body ->
                if (body == Body.SORE) {
                    EveningRowFrame(CheckinTexts.WHERE) { BodyMap(setOf(BodyArea.CALVES), onToggle = {}, onPain = {}, compact = true) }
                }
            },
            afterAlcohol = { _ ->
                EveningWeatherContent(
                    day = sunday,
                    cards = DeckEngine.evening(settings, sunday),
                    saved = mapOf(CardId.EVENING_WEATHER to Answer(CardId.EVENING_WEATHER, level = 4)),
                    persona = persona,
                )
            },
        )
        Column(Modifier.fillMaxSize()) {
            HotTopBar(title = null)
            TodayScreen(
                TodayUiState(whoop = whoop, metrics = metrics),
                onConnectWhoop = {},
                day = DayUiState(plan = plan, now = LocalDateTime.now().withHour(20).withMinute(30), loaded = true),
                slots = TodaySlots(closeDay = extras),
            )
        }
    }

    @Test
    fun eveningZones() = shoot("sc16-evening-zones") {
        Column(Modifier.padding(HotSpacing.edge)) {
            EveningRowFrame(CheckinTexts.WHERE) { BodyMap(setOf(BodyArea.CALVES, BodyArea.LOWER_BACK), onToggle = {}, onPain = {}, compact = false) }
        }
    }

    @Test
    fun painEmergency() = shoot("sc17-pain-chest") {
        Column(Modifier.padding(HotSpacing.edge), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PainAnswer(Pain.CHEST)
            PainAnswer(Pain.JOINT)
        }
    }

    @Test
    fun care() = shoot("sc18-care") {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            CareScreen(onHelp = {}, onTalk = {}, onClose = {})
        }
    }

    @Test
    fun settingsScreen() = shoot("sc19-settings") {
        Column(Modifier.fillMaxSize()) {
            HotTopBar(title = CheckinTexts.SETTINGS)
            CheckinSettingsScreen(
                SettingsUi(
                    prefs = CheckinPrefs(intimacy = true, stack = listOf("Витамин D", "Омега-3"), privateAsked = true),
                    wakeSec = 64,
                    ritualSec = 14,
                    skips = listOf(CardId.SLEPT to 0.6),
                ),
                onChange = {},
                onDeletePrivate = {},
                onOpenCollection = {},
            )
        }
    }

    private fun morning(now: LocalDateTime, prefs: CheckinPrefs) = MorningState(
        now = now,
        settings = DeckSettings(),
        prefs = prefs,
        day = null,
        planToday = false,
        regime = null,
        whoopConnected = true,
        dataLevel = null,
    )

    @Test
    fun todayCards() = shoot("sc20-today-cards") {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(HotSpacing.edge),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HotTopBar(title = null)
            CheckinTodayContent(
                TodayCheckin(
                    morning = morning(LocalDateTime.now().withHour(7).withMinute(5), CheckinPrefs()),
                    discovery = Discovery(Link.SHOWER_CHARGE, 11, 9, 1.0, 0.5, 1.5),
                    removal = CardId.SLEPT,
                    specialist = false,
                ),
                CheckinNav(),
            )
        }
    }
}
