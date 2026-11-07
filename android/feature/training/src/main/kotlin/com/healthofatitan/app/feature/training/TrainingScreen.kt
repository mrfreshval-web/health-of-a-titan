package com.healthofatitan.app.feature.training

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.ModeWord
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.domain.training.PoseLibrary
import com.healthofatitan.app.domain.training.SessionBlock
import com.healthofatitan.app.domain.training.SoundLevel
import com.healthofatitan.app.domain.training.TrainingDecision
import com.healthofatitan.app.domain.training.TrainingEngine
import com.healthofatitan.app.domain.training.TrainingLibrary
import com.healthofatitan.app.domain.training.TrainingPlace
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** What the card on «Сегодня» and the training screen show. */
data class TrainingUiState(
    val loaded: Boolean = false,
    val day: TrainingDay? = null,
    val poses: PoseLibrary = PoseLibrary.EMPTY,
    val reviewed: Boolean = false,
    val place: TrainingPlace = TrainingPlace.HOME,
    val sound: SoundLevel = SoundLevel.DEFAULT,
    /** A workout is between start and summary. */
    val running: Boolean = false,
)

@HiltViewModel
class TrainingViewModel @Inject constructor(
    private val content: TrainingContent,
    private val prefs: TrainingPrefs,
    private val controller: WorkoutController,
    days: DayPlanRepository,
    private val clock: Clock,
) : ViewModel() {

    private val library = flow<TrainingLibrary?> { emit(content.library()) }.catch { emit(null) }

    val state: StateFlow<TrainingUiState> =
        combine(library, days.latest, prefs.place, prefs.sound, controller.run) { lib, view, place, sound, run ->
            if (lib == null) return@combine TrainingUiState(loaded = true, place = place, sound = sound)
            TrainingUiState(
                loaded = true,
                day = TrainingRequests.day(TrainingEngine(lib), view, LocalDate.now(clock), place, sound),
                poses = lib.poses,
                reviewed = lib.reviewed,
                place = place,
                sound = sound,
                running = run != null && !run.state.done,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingUiState())

    fun setPlace(place: TrainingPlace) = prefs.setPlace(place)

    fun setSound(level: SoundLevel) = prefs.setSound(level)

    /** Starts the session of the day; true when the player should open. */
    suspend fun start(): Boolean {
        if (controller.active) return true
        val day = state.value.day ?: return false
        val session = (day.decision as? TrainingDecision.Planned)?.session ?: return false
        controller.start(session, day.day, content.library(), state.value.sound)
        return true
    }
}

/**
 * «Тренировка дня» on «Сегодня» (stream B): one card, its own composable, put into the plan of
 * the day by :app. The Titan in the key pose of the first main exercise, the template, minutes
 * and the mode. A tap opens the session.
 */
@Composable
fun TrainingDayCard(state: TrainingUiState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val day = state.day ?: return
    val decision = day.decision
    val mode = day.request.mode.hotMode()
    HotCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = TrainingTexts.SCREEN_TITLE, onClick = onOpen)
            .testTag(TRAINING_CARD_TAG),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val session = (decision as? TrainingDecision.Planned)?.session
            val hero = session?.heroItem()?.exercise
            TitanFigure(
                pose = timeline(hero, state.poses).still,
                front = hero?.frontView() == true,
                light = figureLight(mode),
                glow = null,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                HotOverline(TrainingTexts.CARD_OVERLINE, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                val title = session?.template?.nameRu ?: TrainingTexts.HIDDEN_TITLE
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val caption = when {
                    state.running -> TrainingTexts.RUNNING
                    session == null -> decision.fired.firstOrNull()?.why
                    else -> listOfNotNull(
                        TrainingTexts.minutes(session.minutes),
                        day.request.mode.ru,
                        TrainingTexts.exercises(session.items.map { it.exercise.id }.distinct().size),
                        TrainingTexts.DONE_TODAY.takeIf { day.done },
                    ).joinToString(" · ")
                }
                caption?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * The session of the day (A40 §7.1 «Обзор»): mode, template, «Почему так», where, the blocks with
 * their exercises and doses, «Звук тренировки», and «Начать». Mobility and stretching are in
 * every version (M-ALL), so the warm-up and the cool-down are always on the list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrainingScreen(
    state: TrainingUiState,
    onStart: () -> Unit,
    onPlace: (TrainingPlace) -> Unit,
    onSound: (SoundLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 4.dp, bottom = HotSpacing.sectionGap)
            .testTag(TRAINING_SCREEN_TAG),
        verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
    ) {
        val day = state.day ?: return@Column
        val mode = day.request.mode.hotMode()
        val decision = day.decision
        val session = (decision as? TrainingDecision.Planned)?.session

        HotOverline(TrainingTexts.CARD_OVERLINE)
        ModeWord(mode, style = MaterialTheme.typography.headlineMedium, glyphSize = 28.dp, gap = 10.dp)
        if (session != null) {
            val hero = session.heroItem()
            TitanExercise(
                timeline = timeline(hero?.exercise, state.poses, hero?.tempo),
                front = hero?.exercise?.frontView() == true,
                light = figureLight(mode),
                glow = mode.color(),
                modifier = Modifier.fillMaxWidth().height(220.dp),
                animate = false,
                description = hero?.exercise?.nameRu,
            )
            Text(session.template.nameRu, style = MaterialTheme.typography.headlineSmall)
            Text(
                listOf(TrainingTexts.minutes(session.minutes), TrainingTexts.exercises(session.items.map { it.exercise.id }.distinct().size)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(TrainingTexts.HIDDEN_TITLE, style = MaterialTheme.typography.headlineSmall)
        }
        if (!day.fromPlan) {
            Text(TrainingTexts.NO_PLAN, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
        }

        if (decision.fired.isNotEmpty()) {
            HotCard(modifier = Modifier.fillMaxWidth()) {
                HotOverline(TrainingTexts.WHY)
                Spacer(Modifier.height(8.dp))
                decision.fired.distinctBy { it.why }.forEach {
                    Text(it.why, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }

        if (session != null) {
            HotOverline(TrainingTexts.PLACE)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PLACES.forEach { p -> TrainingChip(p.ru, selected = state.place == p, onClick = { onPlace(p) }) }
            }
            session.blocks.forEach { BlockCard(it) }

            HotOverline(TrainingTexts.SOUND)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundLevel.entries.forEach { l -> TrainingChip(l.ru, selected = state.sound == l, onClick = { onSound(l) }) }
            }
            Text(
                if (state.sound == SoundLevel.COACH) TrainingTexts.COACH_LATER else TrainingTexts.SOUND_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = HotThemeTokens.colors.textTertiary,
            )
            Spacer(Modifier.height(4.dp))
            HotPrimaryButton(
                text = if (state.running) TrainingTexts.CONTINUE else TrainingTexts.START,
                onClick = onStart,
                trailingArrow = true,
                modifier = Modifier.fillMaxWidth().testTag(TRAINING_START_TAG),
            )
        }
    }
}

@Composable
private fun BlockCard(block: SessionBlock) {
    HotCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        val head = listOfNotNull(
            block.type.ru,
            TrainingTexts.minutes(block.minutes).takeIf { block.minutes > 0 },
            "${block.rounds} ${TrainingTexts.plural(block.rounds, "круг", "круга", "кругов")}".takeIf { block.rounds > 1 },
        ).joinToString(" · ")
        HotOverline(head, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        block.items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = HotThemeTokens.colors.hairline)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.exercise.nameRu, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(12.dp))
                Text(TrainingTexts.dose(item), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val PLACES = listOf(TrainingPlace.HOME, TrainingPlace.GYM, TrainingPlace.OUTDOOR)
