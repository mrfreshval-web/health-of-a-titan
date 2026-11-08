package com.healthofatitan.app.feature.today

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSecondaryButton
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.component.StubScreen
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.domain.today.FoodMeal
import com.healthofatitan.app.domain.today.SleepPrep
import com.healthofatitan.app.domain.today.SupportId
import com.healthofatitan.app.domain.today.SupportNorms
import com.healthofatitan.app.domain.today.SupportView
import com.healthofatitan.app.domain.today.Supports
import com.healthofatitan.app.domain.today.TodayCopy
import com.healthofatitan.app.domain.today.TodayFood
import com.healthofatitan.app.integration.healthconnect.HealthConnectSteps
import com.healthofatitan.app.integration.healthconnect.PhoneSteps
import java.time.LocalDate

const val SUPPORT_SCREEN_TAG = "screen_support"
const val SUPPORT_MEAL_TAG = "support_meal_"
const val SUPPORT_MEAL_OPEN_TAG = "support_meal_open_"
const val SUPPORT_PLATE_TAG = "support_plate"
const val SUPPORT_WATER_REMOVE_TAG = "support_water_remove"
const val SUPPORT_STEPS_ASK_TAG = "support_steps_ask"
const val BREATHER_SCREEN_TAG = "screen_breather"
const val BREATHER_HELP_TAG = "breather_help"
const val ARSENAL_SCREEN_TAG = "screen_arsenal"

/** What a support screen can do: water ±, a meal mark, the steps permission, a route (a meal card, the plate). */
class SupportActions(
    val onWater: (LocalDate, Int) -> Unit = { _, _ -> },
    val onMeal: (LocalDate, String, Boolean) -> Unit = { _, _, _ -> },
    val onStepsPermission: () -> Unit = {},
    val onOpen: (String) -> Unit = {},
)

@Composable
fun SupportRoute(id: String?, stateful: Boolean, onOpen: (String) -> Unit = {}) {
    val support = SupportId.of(id) ?: SupportId.EFFORT
    if (!stateful) {
        SupportScreen(todayView(TodayUiState(), null).supports.first { it.id == support }, null, SupportActions(onOpen = onOpen))
        return
    }
    val today: TodayViewModel = hiltViewModel()
    val dayModel: DayViewModel = hiltViewModel()
    val state by today.state.collectAsStateWithLifecycle()
    val day by dayModel.state.collectAsStateWithLifecycle()
    val view = todayView(state, day.takeIf { it.loaded })
    SupportScreen(
        view.supports.first { it.id == support },
        day.takeIf { it.loaded },
        SupportActions(onWater = dayModel::water, onMeal = dayModel::meal, onStepsPermission = dayModel::refreshSteps, onOpen = onOpen),
    )
}

/**
 * One support in full (A46 §2.3): the number, where it comes from (T-11), the norm and, for water
 * and food, the marks. The norms are a draft (ждёт Агентов 12 и 3) and say «оценка» where they are.
 */
@Composable
fun SupportScreen(support: SupportView, day: DayUiState?, actions: SupportActions, modifier: Modifier = Modifier) {
    val today = day?.today ?: LocalDate.now()
    val food = day?.food ?: TodayFood()
    val prep = sleepPrep(day?.plan?.takeIf { day.planToday }, food) ?: SleepPrep(foodWindows = !food.hideTimes)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 4.dp, bottom = HotSpacing.sectionGap)
            .testTag(SUPPORT_SCREEN_TAG),
        verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
    ) {
        HotCard(cornice = true, modifier = Modifier.fillMaxWidth()) {
            HotOverline(support.label)
            Spacer(Modifier.height(6.dp))
            Text(if (support.pending) TodayCopy.NO_DATA else support.value, style = HotType.numeral, color = MaterialTheme.colorScheme.onSurface)
            support.caption?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(12.dp))
            HotOverline("${TodayCopy.SUPPORT_SOURCE}: ${support.source}" + if (support.estimate) " · ${TodayCopy.ESTIMATE}" else "")
            norm(support.id, food, prep)?.let {
                Spacer(Modifier.height(4.dp))
                HotOverline("${TodayCopy.SUPPORT_NORM}: $it")
            }
        }
        when (support.id) {
            SupportId.WATER -> HotCard(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HotSecondaryButton(
                        TodayCopy.WATER_REMOVE,
                        onClick = { actions.onWater(today, -1) },
                        modifier = Modifier.weight(1f).testTag(SUPPORT_WATER_REMOVE_TAG),
                    )
                    HotPrimaryButton(TodayCopy.WATER_ADD, onClick = { actions.onWater(today, 1) }, modifier = Modifier.weight(1f).testTag(WATER_ADD_TAG))
                }
            }
            SupportId.NUTRITION -> HotCard(modifier = Modifier.fillMaxWidth()) {
                if (food.quiet) {
                    // HIDE_FOOD_TALK (A60 §3.4): no list, no marks, no plate — one quiet line
                    Text(TodayCopy.NUTRITION_QUIET, style = MaterialTheme.typography.bodyLarge)
                } else {
                    val eaten = day?.marks?.meals.orEmpty()
                    // the meals of the food plan (S3); the old four only while there is no plan
                    val meals = food.meals.ifEmpty { TodayCopy.MEALS.map { (slot, title) -> FoodMeal(slot, title) } }
                    meals.forEach { meal ->
                        val marked = meal.slot in eaten
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                meal.title + meal.timeMin?.takeIf { !food.hideTimes }?.let { " · " + Supports.clock(it) }.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(SUPPORT_MEAL_OPEN_TAG + meal.slot)
                                    .clickable(role = Role.Button) { actions.onOpen(HotRoute.nutritionMeal(meal.slot)) }
                                    .padding(vertical = 10.dp),
                            )
                            HotTextButton(
                                if (marked) TodayCopy.MEAL_UNMARK else TodayCopy.MEAL_MARK,
                                onClick = { actions.onMeal(today, meal.slot, !marked) },
                                modifier = Modifier.testTag(SUPPORT_MEAL_TAG + meal.slot),
                            )
                        }
                    }
                    HotTextButton(TodayCopy.PLATE_OF_DAY, onClick = { actions.onOpen(HotRoute.NUTRITION_DAY) }, modifier = Modifier.testTag(SUPPORT_PLATE_TAG))
                }
            }
            SupportId.SLEEP_TONIGHT -> HotCard(modifier = Modifier.fillMaxWidth()) {
                listOfNotNull(
                    TodayCopy.COND_CAFFEINE to prep.caffeineUntilMin,
                    TodayCopy.COND_TRAINING to prep.trainingUntilMin,
                    (TodayCopy.COND_DINNER to prep.dinnerUntilMin).takeIf { prep.foodWindows },
                    TodayCopy.COND_SCREENS to prep.screensFromMin,
                ).forEach { (word, min) -> Text("$word ${Supports.clock(min)}", style = MaterialTheme.typography.bodyLarge) }
            }
            SupportId.MOVEMENT -> if (day?.steps == PhoneSteps.NeedsPermission) StepsAsk(actions.onStepsPermission)
            SupportId.EFFORT -> Unit
        }
    }
}

/** The norm line of a support, from [SupportNorms] and the food plan only; no count of meals under ED_CARE. */
private fun norm(id: SupportId, food: TodayFood, prep: SleepPrep): String? = when (id) {
    SupportId.EFFORT -> null // the corridor of the mode stands under the number
    SupportId.MOVEMENT -> "${TodayCopy.STEPS_GOAL} ${SupportNorms.stepsGoal(null)}"
    SupportId.WATER -> TodayCopy.ESTIMATE
    SupportId.NUTRITION -> if (food.hideCounts) null else "${food.mealsPlanned ?: SupportNorms.MEALS_DEFAULT}"
    SupportId.SLEEP_TONIGHT -> "${prep.total}"
}

/** Health Connect asks for the phone's steps here, where they are used (A46 §2.3 «движение»). */
@Composable
private fun StepsAsk(onPermission: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { onPermission() }
    HotCard(modifier = Modifier.fillMaxWidth().testTag(SUPPORT_STEPS_ASK_TAG)) {
        Text(DailyTexts.STEPS_ASK, style = MaterialTheme.typography.bodyMedium)
        HotTextButton(DailyTexts.STEPS_ALLOW, onClick = { launcher.launch(HealthConnectSteps.PERMISSIONS) })
    }
}

/**
 * «Передышка» (D-043, A49): a pause on demand. Three ways in, none of them asks for an answer;
 * «Помощь» is always one tap away (T-06). The full ritual comes with Agent 17's graphics.
 */
@Composable
fun BreatherScreen(onHelp: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = HotSpacing.edge, vertical = 24.dp)
            .testTag(BREATHER_SCREEN_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(TodayCopy.BREATHER_LINE, style = HotType.numeral, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        val picked = chosen
        if (picked == null) {
            listOf(TodayCopy.BREATHER_REST, TodayCopy.BREATHER_OFF, TodayCopy.BREATHER_HIDE).forEach { option ->
                HotSecondaryButton(option, onClick = { chosen = option }, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Text(
                TodayCopy.BREATHER_EXHALE,
                style = HotType.quote,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            HotTextButton(TodayCopy.BREATHER_BACK, onClick = onClose)
        }
        Spacer(Modifier.height(8.dp))
        HotTextButton(TodayCopy.TASK_HELP, onClick = onHelp, modifier = Modifier.testTag(BREATHER_HELP_TAG))
    }
}

/** «Арсенал» (A47): what the person carries along. A placeholder until its slice. */
@Composable
fun ArsenalScreen(modifier: Modifier = Modifier) {
    StubScreen(lead = TodayCopy.ARSENAL_SOON, blocks = emptyList(), testTag = ARSENAL_SCREEN_TAG, modifier = modifier)
}
