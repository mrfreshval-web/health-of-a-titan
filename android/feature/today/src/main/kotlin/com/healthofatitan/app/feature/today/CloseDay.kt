package com.healthofatitan.app.feature.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.component.TitanLights
import com.healthofatitan.app.core.ui.component.TitanScene
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.core.ui.theme.Titanium
import com.healthofatitan.app.domain.engine.Body
import com.healthofatitan.app.domain.engine.CrisisAssessment
import com.healthofatitan.app.domain.engine.CrisisGuard
import com.healthofatitan.app.domain.engine.CrisisLevel
import com.healthofatitan.app.domain.engine.DayClose
import com.healthofatitan.app.domain.engine.DayEngine
import com.healthofatitan.app.domain.engine.Evening
import com.healthofatitan.app.domain.engine.FoodClose
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.TrainingClose
import com.healthofatitan.app.integration.whoop.day.DayView
import java.time.LocalDate

const val CLOSE_DAY_TAG = "today_close_day"
const val CLOSE_DAY_HELPED_TAG = "today_close_day_helped"
const val CLOSE_DAY_NOTE_TAG = "today_close_day_note"
const val DAY_CLOSED_TAG = "today_day_closed"

/**
 * «Закрыть день» (A18 И4.1, A37 s5, D-027): the day's summary as lit nodes, then one-tap rows —
 * training (only while WHOOP has not seen it), food, body, alcohol — and «План помог?», whose tap
 * closes the day. Every row may stay empty: nothing is a failure, nothing turns red. An optional
 * note (pen) is read by CrisisGuard on the phone and never stored (A07 §4.1, §4.7).
 *
 * @param onCrisis CrisisGuard found level ≥ 1 in the note; level ≥ 2 opens the crisis card by itself
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloseDayCard(
    plan: DayView,
    day: LocalDate,
    mode: HotMode,
    onClose: (LocalDate, DayClose) -> Unit,
    onCrisis: (CrisisAssessment) -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trainingItem = plan.items.firstOrNull { it.id == DayEngine.Ids.TRAINING }
    val trainingAsked = Evening.trainingAsked(trainingItem?.status)
    var training by rememberSaveable(day) { mutableStateOf<TrainingClose?>(null) }
    var food by rememberSaveable(day) { mutableStateOf<FoodClose?>(null) }
    var body by rememberSaveable(day) { mutableStateOf<Body?>(null) }
    var alcohol by rememberSaveable(day) { mutableStateOf<Int?>(null) }
    var noteOpen by rememberSaveable(day) { mutableStateOf(false) }
    var note by rememberSaveable(day) { mutableStateOf("") }
    var reported by rememberSaveable(day) { mutableStateOf(0) }
    var noteLevel by rememberSaveable(day) { mutableStateOf(0) }

    fun checkNote(text: String) {
        val a = CrisisGuard.assess(text)
        noteLevel = a.level.value
        // each level is reported once; a level ≥ 2 opens the crisis card at once (offline too)
        if (a.level.value > reported) {
            reported = a.level.value
            onCrisis(a)
        }
    }

    fun close(helped: Boolean?) {
        if (note.isNotBlank()) checkNote(note)
        onClose(day, DayClose(training.takeIf { trainingAsked }, food, body, alcohol, helped))
    }

    val answers = DayClose(training, food, body, alcohol)
    val total = if (trainingAsked) 4 else 3
    HotCard(cornice = true, modifier = modifier.fillMaxWidth().testTag(CLOSE_DAY_TAG)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CloseDayGlyph(DailyTexts.CLOSE_TITLE, mode.color())
            Spacer(Modifier.width(14.dp))
            Text(DailyTexts.CLOSE_TITLE, style = HotType.headline, modifier = Modifier.weight(1f).semantics { heading() })
        }
        Spacer(Modifier.height(14.dp))
        DayLights(plan, mode)
        Spacer(Modifier.height(12.dp))
        Progress(Evening.answered(answers, trainingAsked), total)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (trainingItem != null) {
                EveningRow(DailyTexts.TRAINING, { KindBadge(ItemKind.TRAINING, mode.color(), sizeDp = 40) }) {
                    if (trainingAsked) {
                        Choices(DailyTexts.TRAINING_OPTIONS, training, { training = it }) { value, color -> TrainingChoiceGlyph(value, color) }
                    } else {
                        StatusLine(trainingItem.detail ?: trainingItem.title, trainingItem.status)
                    }
                }
            }
            EveningRow(DailyTexts.FOOD, { KindBadge(ItemKind.NUTRITION, mode.color(), sizeDp = 40) }) {
                Choices(DailyTexts.FOOD_OPTIONS, food, { food = it }) { value, color -> PlateGlyph(value, "", color) }
            }
            EveningRow(DailyTexts.BODY, { BodyGlyph(body ?: Body.FINE, DailyTexts.BODY, mode.color(), size = 40.dp) }) {
                Choices(DailyTexts.BODY_OPTIONS, body, { body = it }) { value, color -> BodyGlyph(value, "", color, size = 22.dp) }
            }
            EveningRow(DailyTexts.ALCOHOL, null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DailyTexts.ALCOHOL_OPTIONS.forEach { (value, label) ->
                        Chip(label, selected = alcohol == value, onClick = { alcohol = if (alcohol == value) null else value })
                    }
                }
            }
            HelpedRow(onHelped = { close(it) })
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = { noteOpen = !noteOpen },
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, HotThemeTokens.colors.rule),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { PenGlyph(DailyTexts.NOTE, MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.width(10.dp))
            HotOverline(DailyTexts.LAST_TAP, modifier = Modifier.weight(1f))
        }
        AnimatedVisibility(noteOpen) {
            Column(Modifier.padding(top = 10.dp)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = {
                        note = it
                        checkNote(it)
                    },
                    placeholder = { Text(DailyTexts.NOTE_HINT, style = MaterialTheme.typography.bodyMedium) },
                    minLines = 2,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().testTag(CLOSE_DAY_NOTE_TAG),
                )
            }
        }
        // level 1 (A07 §4.3): help stays one tap away, quietly — no wording beyond «Помощь»
        if (noteLevel >= CrisisLevel.CLARIFY.value) {
            HotTextButton(DailyTexts.HELP, onClick = onOpenHelp)
        }
        HotTextButton(DailyTexts.CLOSE_WITHOUT, onClick = { close(null) })
    }
}

/** The day as nodes (A37 §10 «линия дня»): done items are lit by the mode, the rest stay quiet — never crossed. */
@Composable
private fun DayLights(plan: DayView, mode: HotMode) {
    val items = plan.items.sortedWith(compareBy(nullsLast()) { it.startMin })
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).clearAndSetSemantics {
            contentDescription = DailyTexts.DAY_LIGHT + ": " + items.count { it.status != ItemStatus.OPEN } + " из " + items.size
        },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { item ->
            val done = item.status != ItemStatus.OPEN
            KindBadge(item.kind, if (done) mode.color() else HotThemeTokens.colors.textTertiary, sizeDp = 36, filled = done)
        }
    }
}

@Composable
private fun Progress(answered: Int, total: Int) {
    val tokens = HotThemeTokens.colors
    val dawn = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics { contentDescription = DailyTexts.progress(answered, total) }) {
        repeat(total) { i ->
            Spacer(
                Modifier
                    .padding(end = 6.dp)
                    .size(width = 22.dp, height = 4.dp)
                    .background(if (i < answered) dawn else tokens.rule, CircleShape),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(DailyTexts.progress(answered, total), style = MaterialTheme.typography.labelSmall, color = tokens.textTertiary)
    }
}

/** One row of the evening (A37 s5): a pictogram, the word, the one-tap choices under it. */
@Composable
private fun EveningRow(title: String, glyph: (@Composable () -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val tokens = HotThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, tokens.hairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (glyph != null) {
                    glyph()
                    Spacer(Modifier.width(12.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** The training row when WHOOP (or the user) already marked it: the detail and the A35 status, no tap. */
@Composable
private fun StatusLine(detail: String, status: ItemStatus) {
    val tokens = HotThemeTokens.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        StatusGlyph(status, tokens.modeRovno, Titanium.Depth, size = 28.dp)
    }
}

/** Three one-tap choices with a pictogram each; a second tap on the chosen one clears it. */
@Composable
private fun <T> Choices(options: List<Pair<T, String>>, selected: T?, onSelect: (T?) -> Unit, glyph: @Composable (T, Color) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = selected == value
            Choice(label, on, onClick = { onSelect(if (on) null else value) }, modifier = Modifier.weight(1f)) { color -> glyph(value, color) }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, glyph: @Composable (Color) -> Unit) {
    val tokens = HotThemeTokens.colors
    val ink = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else tokens.rule),
        modifier = modifier.heightIn(min = 72.dp).semantics { this.selected = selected; role = Role.RadioButton },
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.clearAndSetSemantics { }) { glyph(ink) }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = ink, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = HotThemeTokens.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else tokens.rule),
        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 56.dp).semantics { this.selected = selected; role = Role.RadioButton },
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** «План помог?» — the last tap closes the day (A37 s5). */
@Composable
private fun HelpedRow(onHelped: (Boolean) -> Unit) {
    val tokens = HotThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().testTag(CLOSE_DAY_HELPED_TAG),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(tokens.ruleSoft, CircleShape), contentAlignment = Alignment.Center) {
                    HelpedGlyph(true, "", MaterialTheme.colorScheme.onSurface, size = 22.dp)
                }
                Spacer(Modifier.width(12.dp))
                Text(DailyTexts.PLAN_HELPED, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(true to DailyTexts.YES, false to DailyTexts.NOT_REALLY).forEach { (value, label) ->
                    Choice(label, selected = false, onClick = { onHelped(value) }, modifier = Modifier.weight(1f)) { color -> HelpedGlyph(value, "", color) }
                }
            }
        }
    }
}

@Composable
private fun TrainingChoiceGlyph(value: TrainingClose, color: Color) {
    when (value) {
        TrainingClose.DONE -> StatusGlyph(ItemStatus.DONE_SELF, color, Titanium.Depth, size = 20.dp)
        TrainingClose.CHANGED -> StatusGlyph(ItemStatus.MODIFIED, color, Titanium.Depth, size = 20.dp)
        TrainingClose.NOT_TODAY -> HelpedGlyph(false, "", color)
    }
}

/**
 * The closed day (A37 §3 «стремление без стыда»): the Titan glows — at least the floor light for
 * showing up, brighter with what was done — and one quiet line. «Не сегодня» gets «Понял. Завтра
 * подстрою план.» and nothing else.
 */
@Composable
fun DayClosedCard(plan: DayView, close: DayClose?, mode: HotMode, modifier: Modifier = Modifier) {
    val light = Evening.light(plan.items.map { it.status })
    HotCard(modifier = modifier.fillMaxWidth().testTag(DAY_CLOSED_TAG)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            TitanScene(
                modifier = Modifier.widthIn(max = 180.dp).fillMaxWidth().aspectRatio(1f).clearAndSetSemantics { },
                light = TitanLights.of(mode),
                ring = { light },
                ringColor = mode.color(),
                ringTrack = HotThemeTokens.colors.hairline,
                ringStroke = 8f,
                ringFit = 0.9f,
            )
            Spacer(Modifier.height(8.dp))
            HotOverline(DailyTexts.CLOSED)
            Spacer(Modifier.height(6.dp))
            Text(
                if (close?.training == TrainingClose.NOT_TODAY) DailyTexts.NOT_TODAY_LINE else DailyTexts.CLOSED_LINE,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}
