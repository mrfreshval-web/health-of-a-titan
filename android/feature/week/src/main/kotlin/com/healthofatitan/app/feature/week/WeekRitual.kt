package com.healthofatitan.app.feature.week

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSecondaryButton
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.domain.today.WeekGoal
import com.healthofatitan.app.domain.today.week.RitualAnswers
import com.healthofatitan.app.domain.today.week.RitualStep
import com.healthofatitan.app.domain.today.week.SkewChoice
import com.healthofatitan.app.domain.today.week.TitanWeekLook
import com.healthofatitan.app.domain.today.week.WeekCopy
import com.healthofatitan.app.domain.today.week.WeekGoalRules
import com.healthofatitan.app.integration.whoop.week.WeekScreenState

const val WEEK_RITUAL_TAG = "week_ritual"
const val WEEK_RITUAL_NEXT_TAG = "week_ritual_next"

/**
 * «Итог и сборка» (A52 §2.6), 3–5 minutes, one step at a time: the week in one frame (the Titan
 * opens), what worked, work (pride and what drags on, Agent 8), one skew → one offer, the goals of
 * the next week (one tap each), forward. Nothing is asked twice and nothing is graded.
 */
@Composable
fun WeekRitualScreen(
    week: WeekScreenState,
    tried: List<WeekGoal>,
    onDone: (RitualAnswers) -> Unit,
    onClose: () -> Unit,
    onShare: (TitanWeekLook) -> Unit,
    startAt: RitualStep? = null,
) {
    val ritual = week.ritual ?: return
    val steps = ritual.steps
    var index by remember { mutableStateOf(startAt?.let { steps.indexOf(it).coerceAtLeast(0) } ?: 0) }
    var pride by remember { mutableStateOf("") }
    var carry by remember { mutableStateOf("") }
    var carryToGoal by remember { mutableStateOf(false) }
    var skew by remember { mutableStateOf(SkewChoice.AS_IS) }
    val targets = remember { mutableStateMapOf<String, Int>() }
    val step = steps[index]
    val look = week.view.titan
    Column(
        Modifier
            .fillMaxSize()
            .testTag(WEEK_RITUAL_TAG)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HotSpacing.edge, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HotOverline("${WeekCopy.BLOCK_RITUAL} · ${index + 1} / ${steps.size}", modifier = Modifier.weight(1f))
            HotTextButton(WeekCopy.CANCEL, onClick = onClose)
        }
        when (step) {
            RitualStep.FRAME -> {
                Title(WeekCopy.R_FRAME)
                // the reveal of the hero moment (Agent 17 owns the final one): the last light comes on
                val reveal = remember { Animatable(look.reveal) }
                LaunchedEffect(Unit) { reveal.animateTo(1f, tween(1_500)) }
                WeekTitanFigure(look, Modifier.fillMaxWidth().aspectRatio(1.1f), revealOverride = reveal.value)
                Text(look.spoken, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${WeekCopy.DAYS_IN_SUPPORT} · ${WeekCopy.ofTotal(week.view.daysInSupport, 7)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                ritual.beforeAfter?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            RitualStep.WORKED -> {
                Title(WeekCopy.R_WORKED)
                Text(ritual.worked, style = HotType.quote, color = MaterialTheme.colorScheme.onSurface)
            }
            RitualStep.WORK -> {
                Title(WeekCopy.R_WORK)
                OutlinedTextField(pride, { pride = it.take(280) }, label = { Text(WeekCopy.R_PRIDE_Q) }, supportingText = { Text(WeekCopy.R_PRIDE_HINT) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(carry, { carry = it.take(280) }, label = { Text(WeekCopy.R_CARRY_Q) }, supportingText = { Text(WeekCopy.R_CARRY_HINT) }, modifier = Modifier.fillMaxWidth())
                if (carry.isNotBlank()) {
                    Row {
                        HotTextButton((if (carryToGoal) "● " else "○ ") + WeekCopy.R_CARRY_TO_GOAL, onClick = { carryToGoal = true })
                        HotTextButton((if (!carryToGoal) "● " else "○ ") + WeekCopy.R_CARRY_KEEP, onClick = { carryToGoal = false })
                    }
                }
            }
            RitualStep.SKEW -> {
                Title(WeekCopy.R_SKEW)
                ritual.skew?.let { Text(it.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(SkewChoice.TAKE to WeekCopy.R_TAKE, SkewChoice.AS_IS to WeekCopy.R_AS_IS, SkewChoice.EASIER to WeekCopy.R_EASIER).forEach { (c, label) ->
                        HotTextButton((if (skew == c) "● " else "○ ") + label, onClick = { skew = c })
                    }
                }
            }
            RitualStep.NEXT -> {
                Title(WeekCopy.R_NEXT)
                ritual.proposal.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                (ritual.proposal.plan.goals + tried).filter { (targets[it.id] ?: it.target) != 0 }.forEach { g ->
                    val target = targets[g.id] ?: g.target
                    NextGoal(
                        g, target, up = g.id in week.suggestUp,
                        onLess = { targets[g.id] = WeekGoalRules.lighter(g.copy(target = target)).target },
                        onMore = { targets[g.id] = WeekGoalRules.heavier(g.copy(target = target)).target },
                        onRemove = { targets[g.id] = 0 },
                    )
                }
            }
            RitualStep.FORWARD -> {
                Title(WeekCopy.R_FORWARD)
                WeekTitanFigure(look, Modifier.fillMaxWidth().aspectRatio(1.1f), revealOverride = 1f)
                week.dayDetails.values.lastOrNull { it.haiku != null }?.haiku?.forEach {
                    Text(it, style = HotType.quote, color = MaterialTheme.colorScheme.onSurface)
                }
                HotSecondaryButton(WeekCopy.TITAN_SHARE, onClick = { onShare(look.copy(reveal = 1f, revealed = true)) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (index < steps.lastIndex) {
            HotPrimaryButton(WeekCopy.R_NEXT_STEP, onClick = { index++ }, modifier = Modifier.fillMaxWidth().testTag(WEEK_RITUAL_NEXT_TAG), trailingArrow = true)
        } else {
            HotPrimaryButton(
                WeekCopy.R_APPROVE,
                onClick = {
                    onDone(
                        RitualAnswers(
                            pride = pride.takeIf { it.isNotBlank() },
                            carry = carry.takeIf { it.isNotBlank() },
                            carryToGoal = carryToGoal,
                            skew = skew,
                            targets = targets.toMap(),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag(WEEK_RITUAL_NEXT_TAG),
            )
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun NextGoal(g: WeekGoal, target: Int, up: Boolean, onLess: () -> Unit, onMore: () -> Unit, onRemove: () -> Unit) {
    HotCard(modifier = Modifier.fillMaxWidth().testTag("week_next_${g.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text("$target ${g.unit}", style = HotType.ringValue, color = MaterialTheme.colorScheme.onSurface)
        }
        if (up) Text(WeekCopy.GOAL_SUGGEST_UP, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            HotTextButton("−", onClick = onLess)
            HotTextButton("+", onClick = onMore)
            HotTextButton(WeekCopy.GOAL_REMOVE, onClick = onRemove)
        }
    }
}
