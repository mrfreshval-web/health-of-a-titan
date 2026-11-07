package com.healthofatitan.app.feature.path

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.PathColors
import com.healthofatitan.app.domain.path.ChangeProposal
import com.healthofatitan.app.domain.path.CrossroadsOption
import com.healthofatitan.app.domain.path.CrossroadsRules
import com.healthofatitan.app.domain.path.GoalChange
import com.healthofatitan.app.domain.path.GoalMode
import com.healthofatitan.app.domain.path.GoalStatus
import com.healthofatitan.app.domain.path.LeverReason
import com.healthofatitan.app.domain.path.MarkRules
import com.healthofatitan.app.domain.path.PartState
import com.healthofatitan.app.domain.path.PathCopy
import com.healthofatitan.app.domain.path.PathLexicon
import com.healthofatitan.app.domain.path.PauseReason
import com.healthofatitan.app.domain.path.PracticalGoal
import com.healthofatitan.app.domain.path.SlicePart
import com.healthofatitan.app.feature.path.logic.Diff

const val PATH_DIFF_TAG = "path_diff"
const val PATH_APPLY_TAG = "path_apply"
const val PATH_CROSSROADS_TAG = "path_crossroads"
const val PATH_PAUSE_TAG = "path_pause"
const val PATH_RELEASE_TAG = "path_release"
const val PATH_MARK_TAG = "path_mark"

/** The sheets of the path (A56 §3): every change goes through «было → станет». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PathSheets(ui: PathUi, actions: PathActions, sheet: PathSheet?, onSheet: (PathSheet?) -> Unit) {
    val goal = ui.data.overview.goal
    val proposal = ui.proposal
    if (proposal != null) {
        Sheet(onDismiss = actions.onKeep) { DiffCard(proposal, ui.refusal, actions) }
        return
    }
    if (sheet == null || goal == null) return
    Sheet(onDismiss = { onSheet(null) }) {
        when (sheet) {
            PathSheet.Menu -> GoalMenu(goal, ui.data.overview.paused, onSheet, propose = { onSheet(null); actions.onPropose(it) })
            PathSheet.Pause -> PauseSheet(propose = { onSheet(null); actions.onPropose(it) }, today = ui.data.today)
            PathSheet.Release -> ReleaseSheet(propose = { onSheet(null); actions.onPropose(it) })
            PathSheet.Crossroads -> CrossroadsSheet(goal, after = true, propose = { onSheet(null); actions.onPropose(it) }, onLater = { onSheet(null) })
            is PathSheet.Part -> PartSheet(
                part = ui.data.overview.parts.firstOrNull { it.key == sheet.key } ?: SlicePart(sheet.key, PartState.OFF),
                goal = goal,
                onMark = { m -> onSheet(null); actions.onMark(sheet.key, m) },
                propose = { onSheet(null); actions.onPropose(it) },
            )
        }
        ui.refusal?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = PathText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            content = content,
        )
    }
}

@Composable
private fun MenuRow(text: String, tag: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .let { if (tag != null) it.testTag(tag) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = PathText.body, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** «Цель»: the person's levers over the goal (A56 §3.1: the goal, its date and parts are theirs). */
@Composable
private fun GoalMenu(goal: PracticalGoal, paused: Boolean, onSheet: (PathSheet?) -> Unit, propose: (GoalChange) -> Unit) {
    HotOverline(PathCopy.MENU)
    Spacer(Modifier.height(8.dp))
    MenuRow(PathCopy.MOVE_DEADLINE) { propose(GoalChange.MoveDeadline(goal.deadline.plusWeeks(4))) }
    MenuRow(PathCopy.SWITCH_MODE) { propose(GoalChange.SetMode(if (goal.mode == GoalMode.BY_DATE) GoalMode.FULL else GoalMode.BY_DATE)) }
    if (paused) {
        MenuRow(PathCopy.RESUME, PATH_PAUSE_TAG) { propose(GoalChange.Resume) }
    } else {
        MenuRow(PathCopy.PAUSE, PATH_PAUSE_TAG) { onSheet(PathSheet.Pause) }
    }
    if (goal.status == GoalStatus.ACTIVE && goal.pausedDays > 0) {
        MenuRow(PathCopy.CROSSROADS, PATH_CROSSROADS_TAG) { onSheet(PathSheet.Crossroads) }
    }
    MenuRow(PathCopy.RELEASE, PATH_RELEASE_TAG) { onSheet(PathSheet.Release) }
}

/** A part of the slice: its role, mark and safe version; the mark taken by a measurement; the lever. */
@Composable
private fun PartSheet(part: SlicePart, goal: PracticalGoal, onMark: (Int) -> Unit, propose: (GoalChange) -> Unit) {
    val material = materialOf(part.key.element)
    HotOverline(PathLexicon.element(part.key.element), color = material)
    Text(PathLexicon.name(part.key), style = PathText.sheetTitle, color = MaterialTheme.colorScheme.onSurface)
    Text(PathLexicon.modelName(part.key), style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    val line = when (part.state) {
        PartState.OFF -> PathCopy.OUTSIDE
        PartState.EXCLUDED -> PathCopy.A11Y_EXCLUDED
        PartState.PRACTICE -> (part.roleWord()?.let { "$it · " } ?: "") + PathCopy.PRACTICE
        PartState.MARKS -> (part.roleWord()?.let { "$it · " } ?: "") + PathCopy.markLine(part.mark.takeIf { it > 0 }, part.target)
    }
    Text(line, style = PathText.body, color = MaterialTheme.colorScheme.onSurface)
    part.safeVersion?.let { Text("${PathCopy.SAFE_BADGE}: $it", style = PathText.caption, color = PathColors.current.dano) }
    if (part.state == PartState.MARKS) {
        Spacer(Modifier.height(16.dp))
        Text(PathCopy.MARK_PICK, style = PathText.cardTitle, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        PathChips((MarkRules.MIN..MarkRules.MAX).map(PathLexicon::mark), selected = part.mark.takeIf { it > 0 }?.minus(1), onPick = { onMark(it + 1) }, tag = PATH_MARK_TAG)
    }
    if (part.role != null && !part.lever && goal.lever?.key != part.key) {
        Spacer(Modifier.height(8.dp))
        HotTextButton(PathCopy.lever(PathLexicon.name(part.key)), onClick = { propose(GoalChange.SetLever(part.key, LeverReason.PERSON)) })
    }
}

/** «Что происходит?» (A56 §3): the reason, then the term; in grief no term is asked. Черновик, ревью 10. */
@Composable
private fun PauseSheet(propose: (GoalChange) -> Unit, today: java.time.LocalDate) {
    var reason by remember { mutableStateOf<PauseReason?>(null) }
    var term by remember { mutableIntStateOf(2) }
    Text(PathCopy.PAUSE_TITLE, style = PathText.sheetTitle, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(12.dp))
    PathChips(PauseReason.entries.map(PathCopy::pauseReason), PauseReason.entries.indexOf(reason).takeIf { it >= 0 }, { reason = PauseReason.entries[it] }, tag = "path_pause_reason")
    val r = reason
    if (r != null && r.asksTerm) {
        Spacer(Modifier.height(16.dp))
        Text(PathCopy.PAUSE_TERM, style = PathText.cardTitle, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        PathChips(PathCopy.PAUSE_TERMS, term, { term = it }, tag = "path_pause_term")
    } else if (r != null) {
        Spacer(Modifier.height(12.dp))
        Text(PathCopy.PAUSE_NO_TERM, style = PathText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(20.dp))
    HotPrimaryButton(
        PathCopy.PAUSE_ON,
        enabled = r != null,
        onClick = {
            val until = when {
                r == null || !r.asksTerm -> null
                term == 0 -> today.plusWeeks(1)
                term == 1 -> today.plusWeeks(2)
                else -> null
            }
            r?.let { propose(GoalChange.Pause(it, until)) }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    ReviewNote()
}

/** «Отпустить цель» (A56 §3): what is taken stays taken. Черновик, ревью 10. */
@Composable
private fun ReleaseSheet(propose: (GoalChange) -> Unit) {
    var toQueue by remember { mutableStateOf(true) }
    Text(PathCopy.RELEASE_TITLE, style = PathText.sheetTitle, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(8.dp))
    Text(PathCopy.RELEASE_KEEP, style = PathText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { toQueue = !toQueue }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = toQueue, onCheckedChange = { toQueue = it })
        Text(PathCopy.RELEASE_TO_QUEUE, style = PathText.body, color = MaterialTheme.colorScheme.onSurface)
    }
    Text(PathCopy.RELEASE_UNDO, style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
    HotPrimaryButton(PathCopy.RELEASE_ON, onClick = { propose(GoalChange.Release(toQueue)) }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    ReviewNote()
}

/**
 * «Перекрёсток» (A58-VP §5.4): options without pre-selection, no numbers of a «shortfall», no
 * percent, no distance to the Titan of the horizon. Черновик, ревью 10, 3.
 */
@Composable
fun CrossroadsSheet(goal: PracticalGoal, after: Boolean, propose: (GoalChange) -> Unit, onLater: () -> Unit) {
    var chosen by remember { mutableStateOf<CrossroadsOption?>(null) }
    val options = if (after) CrossroadsRules.AFTER_PAUSE else CrossroadsRules.LAG
    Column(Modifier.testTag(PATH_CROSSROADS_TAG)) {
        Text(PathCopy.CROSSROADS, style = PathText.ritualH1, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(if (after) PathCopy.CROSSROADS_RETURN else PathCopy.CROSSROADS_LAG, style = PathText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                OptionCard(PathCopy.crossroads(o), crossroadsSub(goal, o), chosen == o, onClick = { chosen = o })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(PathCopy.CROSSROADS_UNDO, style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        HotPrimaryButton(
            PathCopy.CHOOSE,
            enabled = chosen != null,
            onClick = { chosen?.let { o -> CrossroadsRules.change(goal, o)?.let(propose) ?: onLater() } },
            modifier = Modifier.fillMaxWidth(),
        )
        HotTextButton(PathCopy.TALK_SUNDAY, onClick = onLater)
        ReviewNote()
    }
}

/** The consequence of an option in one line: «Срок → 28.02, планка та же». */
private fun crossroadsSub(goal: PracticalGoal, o: CrossroadsOption): String? {
    val change = (CrossroadsRules.change(goal, o) as? GoalChange.Crossroads)?.inner
    return when (change) {
        is GoalChange.MoveDeadline -> "${PathCopy.DIFF_DEADLINE} → ${PathCopy.date(change.deadline)}"
        is GoalChange.SetCriteria -> change.criteria.firstOrNull()?.let(PathCopy::criterion)
        GoalChange.RestartFromToday -> PathCopy.EFFECT_WEEK_NEXT
        is GoalChange.SetTargetMark -> change.mark?.let { "${PathLexicon.name(change.key)} → «${PathLexicon.mark(it)}»" }
        is GoalChange.SetLever -> PathCopy.lever(PathLexicon.name(change.key))
        else -> null
    }
}

/** The card «было → станет» (A58-VP §5.3): no green, no red, no strike-through. */
@Composable
fun DiffCard(proposal: ChangeProposal, refusal: String?, actions: PathActions) {
    val tokens = HotThemeTokens.colors
    Column(Modifier.fillMaxWidth().testTag(PATH_DIFF_TAG)) {
        HotOverline(PathCopy.CHANGE_OVERLINE)
        Text(PathCopy.changeTitle(proposal.change), style = PathText.sheetTitle, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Diff.rows(proposal.before, proposal.after).forEach { r ->
            Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp)) {
                Text(r.what, style = PathText.caption, color = tokens.textTertiary)
                Row(verticalAlignment = Alignment.Top) {
                    Text(r.was, style = PathText.body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("→", style = PathText.body, color = tokens.textTertiary, modifier = Modifier.padding(horizontal = 8.dp))
                    Text(r.will, style = PathText.body.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HotOverline(PathCopy.WHAT_CHANGES)
        Spacer(Modifier.height(6.dp))
        listOf(proposal.effects.week, proposal.effects.today, proposal.effects.experiment).forEach {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text("·", style = PathText.body, color = tokens.textTertiary)
                Spacer(Modifier.width(8.dp))
                Text(it, style = PathText.body, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (proposal.suggestTalk) {
            Spacer(Modifier.height(8.dp))
            Text(PathCopy.TALK, style = PathText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (refusal != null) {
            Spacer(Modifier.height(8.dp))
            Text(refusal, style = PathText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
        if (proposal.soft) {
            Text(PathCopy.SOFT_LIMIT, style = PathText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            HotPrimaryButton(PathCopy.SOFT_OVERRIDE, onClick = { actions.onApply(true) }, modifier = Modifier.fillMaxWidth().testTag(PATH_APPLY_TAG))
            HotTextButton(PathCopy.LOOK_SUNDAY, onClick = actions.onKeep)
        } else {
            HotPrimaryButton(PathCopy.APPLY, onClick = { actions.onApply(false) }, modifier = Modifier.fillMaxWidth().testTag(PATH_APPLY_TAG))
        }
        HotTextButton(PathCopy.KEEP, onClick = actions.onKeep)
    }
}
