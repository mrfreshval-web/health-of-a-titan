package com.healthofatitan.app.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotTopBar
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.training.BlockType
import com.healthofatitan.app.domain.training.DayMode
import com.healthofatitan.app.domain.training.DayTrainingKind
import com.healthofatitan.app.domain.training.PlayerPhase
import com.healthofatitan.app.domain.training.PlayerState
import com.healthofatitan.app.domain.training.SoundLevel
import com.healthofatitan.app.domain.training.TrainingDecision
import com.healthofatitan.app.domain.training.TrainingEngine
import com.healthofatitan.app.domain.training.TrainingLibrary
import com.healthofatitan.app.domain.training.TrainingPlace
import com.healthofatitan.app.domain.training.TrainingRequest
import com.healthofatitan.app.domain.training.TrainingSession
import com.healthofatitan.app.domain.training.WorkoutPlayer
import com.healthofatitan.app.domain.training.WorkoutStep
import com.healthofatitan.app.domain.training.WorkoutSteps
import com.healthofatitan.app.feature.today.DayUiState
import com.healthofatitan.app.feature.today.TodayScreen
import com.healthofatitan.app.feature.today.TodaySlots
import com.healthofatitan.app.feature.today.TodayUiState
import com.healthofatitan.app.feature.training.PlayerActions
import com.healthofatitan.app.feature.training.PlayerScreen
import com.healthofatitan.app.feature.training.TitanFigure
import com.healthofatitan.app.feature.training.TrainingDayCard
import com.healthofatitan.app.feature.training.TrainingRequests
import com.healthofatitan.app.feature.training.TrainingScreen
import com.healthofatitan.app.feature.training.TrainingTexts
import com.healthofatitan.app.feature.training.TrainingUiState
import com.healthofatitan.app.feature.training.WorkoutRun
import com.healthofatitan.app.feature.training.figureLight
import com.healthofatitan.app.feature.training.frontView
import com.healthofatitan.app.feature.training.timeline
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
 * Stream B «Тренировка» for the design review: the card on «Сегодня», the session of the day,
 * the player (set, rest with RPE, summary) and the Titan's key poses. Same opt-in and output
 * as [TitaniumScreenshots] (HOT_SCREENSHOTS, app/build/screenshots), Pixel 9 Pro XL in dp.
 * Sessions come from the real content and configurator; the numbers on screen are illustrative.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w448dp-h998dp-xxhdpi")
class TrainingTitaniumScreenshots {

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

    private val library: TrainingLibrary by lazy {
        val context = ApplicationProvider.getApplicationContext<Application>()
        fun read(path: String): String? =
            runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
                ?: File("build/generated/hot/content/$path").takeIf { it.isFile }?.readText()
        TrainingLibrary.fromJson(
            read(TrainingLibrary.EXERCISES_ASSET)!!,
            read(TrainingLibrary.TEMPLATES_ASSET)!!,
            read(TrainingLibrary.RULES_ASSET)!!,
            read(TrainingLibrary.POSES_ASSET),
        )
    }

    private val today = LocalDate.now()

    private fun dayView(regime: Regime, title: String) = DayView(
        dayKey = today,
        regime = regime,
        flags = emptySet(),
        headline = regime.ru,
        summary = "Сон в норме, Заряд у твоей нормы.",
        fired = emptyList(),
        items = listOf(
            DayItemView("work", ItemKind.WORK, "Главная задача", "90 мин, одна задача", 10 * 60, 11 * 60 + 30, ItemStatus.OPEN),
            DayItemView("training", ItemKind.TRAINING, title, "RPE 7–8", 18 * 60, 18 * 60 + 45, ItemStatus.OPEN),
            DayItemView("bedtime", ItemKind.SLEEP, "Отбой", null, 23 * 60, null, ItemStatus.OPEN),
        ),
        bedtimeMin = 23 * 60,
        caffeineCutoffMin = 14 * 60,
        windDownMin = 22 * 60,
    )

    private fun uiState(regime: Regime = Regime.STEADY, title: String = "Силовая · 45 мин", place: TrainingPlace = TrainingPlace.HOME) = TrainingUiState(
        loaded = true,
        day = TrainingRequests.day(TrainingEngine(library), dayView(regime, title), today, place, SoundLevel.DEFAULT),
        poses = library.poses,
        reviewed = library.reviewed,
        place = place,
    )

    private fun session(mode: DayMode = DayMode.STEADY): TrainingSession {
        val d = TrainingEngine(library).plan(TrainingRequest(mode, DayTrainingKind.STRENGTH, 45, TrainingPlace.HOME))
        return (d as TrainingDecision.Planned).session
    }

    /** Plays the session with «Готово» until [stop] holds; each step takes 40 s. */
    private fun play(mode: DayMode = DayMode.STEADY, rpe: Int? = null, stop: (WorkoutPlayer, PlayerState) -> Boolean): Pair<WorkoutRun, Long> {
        val s = session(mode)
        val player = WorkoutPlayer(WorkoutSteps.of(s, library.rules.player), library.rules.player.countdownS, library.rules.player.extendRestStepS)
        var now = 1_000L
        var state = player.start(now).state
        var guard = 0
        while (!stop(player, state) && !state.done && guard++ < 500) {
            now += 40_000L
            state = player.done(now, state).state
            if (rpe != null && state.pendingRpe != null && !stop(player, state)) state = player.answerRpe(state, rpe).state
        }
        val r = WorkoutRun("shot", today, s, player, library.poses, SoundLevel.DEFAULT, library.reviewed, state, System.currentTimeMillis() - (now - 1_000L))
        return r to now
    }

    private fun mainWork(p: WorkoutPlayer, s: PlayerState): Boolean {
        val step = p.step(s)
        return step is WorkoutStep.Work && step.blockType == BlockType.MAIN && s.phase == PlayerPhase.WORK
    }

    @Test
    fun todayWithTrainingCard() = shoot("b01-today-training-card") {
        val ui = uiState()
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                HotTopBar(title = null)
                TodayScreen(
                    TodayUiState(whoop = WhoopStatus(configured = true, connected = true, whoopUserId = "1", lastSyncAt = System.currentTimeMillis(), baselineNights = 21), metrics = TodayMetrics.EMPTY),
                    onConnectWhoop = {},
                    day = DayUiState(plan = dayView(Regime.STEADY, "Силовая · 45 мин"), now = LocalDateTime.now().withHour(17).withMinute(30), loaded = true),
                    slots = TodaySlots(training = { TrainingDayCard(ui, onOpen = {}) }),
                )
            }
        }
    }

    @Test
    fun trainingSessionSteady() = shoot("b02-training-session-rovno") {
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                HotTopBar(title = TrainingTexts.SCREEN_TITLE, overline = TrainingTexts.BACK_LABEL, onBack = {})
                TrainingScreen(uiState(), onStart = {}, onPlace = {}, onSound = {})
            }
        }
    }

    @Test
    fun trainingSessionGentle() = shoot("b03-training-session-berezhno") {
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                HotTopBar(title = TrainingTexts.SCREEN_TITLE, overline = TrainingTexts.BACK_LABEL, onBack = {})
                TrainingScreen(uiState(Regime.GENTLE), onStart = {}, onPlace = {}, onSound = {})
            }
        }
    }

    @Test
    fun playerSet() {
        val (r, now) = play(stop = ::mainWork)
        shoot("b04-player-set") { PlayerScreen(r, now + 12_000L, PlayerActions(), animate = false) }
    }

    @Test
    fun playerRestWithRpe() {
        val (r, now) = play { p, s -> s.phase == PlayerPhase.REST && s.pendingRpe != null && p.step(s)?.blockType == BlockType.MAIN }
        shoot("b05-player-rest-rpe") { PlayerScreen(r, now + 18_000L, PlayerActions(), animate = true) }
    }

    @Test
    fun playerSummary() {
        val (r, now) = play(rpe = 7) { _, s -> s.done }
        shoot("b06-player-summary") { PlayerScreen(r, now, PlayerActions(), animate = true) }
    }

    @Test
    fun playerSetLight() {
        val (r, now) = play(stop = ::mainWork)
        shoot("b07-player-set-light", dark = false) { PlayerScreen(r, now + 12_000L, PlayerActions(), animate = false) }
    }

    /** The key pose (A40 §6.2 final frame) of every exercise that has keyframes, for Agents 4 and 12. */
    @Test
    fun titanPoses() = shoot("b08-titan-key-poses") {
        val ids = library.poses.exerciseIds.sorted()
        HotBackground {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HotOverline("TitanPose · ${ids.size}")
                ids.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { id ->
                            val ex = library.exercise(id)
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                TitanFigure(
                                    pose = timeline(ex, library.poses).still,
                                    front = ex?.frontView() == true,
                                    light = figureLight(HotMode.ROVNO),
                                    glow = null,
                                    modifier = Modifier.fillMaxWidth().height(96.dp),
                                )
                                Text(
                                    ex?.nameRu ?: id,
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        repeat(4 - row.size) { Column(Modifier.weight(1f)) {} }
                    }
                }
            }
        }
    }
}
