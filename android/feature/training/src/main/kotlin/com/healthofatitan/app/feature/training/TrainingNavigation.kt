package com.healthofatitan.app.feature.training

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.platform.LocalView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.rememberReduceMotion
import com.healthofatitan.app.domain.training.PlayerPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Stream B screens: [HotRoute.TRAINING] (the session of the day, under «Сегодня») and
 * [HotRoute.TRAINING_PLAYER] (full screen). «Мне плохо» opens the existing help route through
 * [onFeelBad]; its texts are not touched here.
 *
 * @param stateful false renders nothing stateful (navigation tests without Hilt)
 */
fun NavGraphBuilder.trainingScreens(
    onOpenPlayer: () -> Unit,
    onFeelBad: () -> Unit,
    onClosePlayer: () -> Unit,
    stateful: Boolean = true,
) {
    composable(route = HotRoute.TRAINING) {
        if (stateful) TrainingRoute(onOpenPlayer) else TrainingScreen(TrainingUiState(), onStart = {}, onPlace = {}, onSound = {})
    }
    composable(route = HotRoute.TRAINING_PLAYER) {
        if (stateful) {
            PlayerRoute(onFeelBad, onClosePlayer)
        } else {
            LaunchedEffect(Unit) { onClosePlayer() }
        }
    }
}

/** «Тренировка дня» for the slot on «Сегодня». */
@Composable
fun TrainingDayCardRoute(onOpen: () -> Unit, viewModel: TrainingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.loaded) TrainingDayCard(state, onOpen)
}

@Composable
private fun TrainingRoute(onOpenPlayer: () -> Unit, viewModel: TrainingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    TrainingScreen(
        state = state,
        onStart = { scope.launch { if (viewModel.start()) onOpenPlayer() } },
        onPlace = viewModel::setPlace,
        onSound = viewModel::setSound,
    )
}

@HiltViewModel
class PlayerViewModel @Inject constructor(private val controller: WorkoutController) : ViewModel() {
    val run: StateFlow<WorkoutRun?> = controller.run

    fun now(): Long = controller.now()

    val actions = PlayerActions(
        onDone = { controller.done() },
        onSkip = controller::skip,
        onExtend = controller::extendRest,
        onPause = controller::pause,
        onResume = controller::resume,
        onFinish = controller::finish,
        onFeelBad = controller::feelBad,
        onRpe = controller::answerRpe,
        onClose = controller::close,
    )

    fun pause() = controller.pause()
}

@Composable
private fun PlayerRoute(onFeelBad: () -> Unit, onClose: () -> Unit, viewModel: PlayerViewModel = hiltViewModel()) {
    val run by viewModel.run.collectAsStateWithLifecycle()
    val current = run
    if (current == null) {
        // Nothing runs (closed, or the process was restarted): back to where the player came from.
        LaunchedEffect(Unit) { onClose() }
        return
    }
    var now by remember { mutableLongStateOf(viewModel.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { }
            now = viewModel.now()
        }
    }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val done = current.state.phase == PlayerPhase.DONE
    // Back during a workout pauses it (the pause screen offers «Завершить»).
    BackHandler(enabled = !done && !current.state.paused) { viewModel.pause() }
    // After it, back closes the summary; the run turns null and the effect above leaves the screen.
    BackHandler(enabled = done) { viewModel.actions.onClose() }
    val a = viewModel.actions
    PlayerScreen(
        run = current,
        now = now,
        animate = !rememberReduceMotion(),
        actions = a.copy(
            onFeelBad = {
                a.onFeelBad()
                onFeelBad()
            },
        ),
    )
}
