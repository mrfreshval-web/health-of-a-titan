package com.healthofatitan.app.feature.nutrition

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.HotSpacing

const val PLATE_SCREEN_TAG = "screen_nutrition_plate"

/**
 * The food screens inside a tab (A41 §10): «Я › Еда и тело», the level and the meal card. No new
 * tabs (the tab bar belongs to stream Г). [stateful] false draws defaults without view models.
 * [onOpen] opens a route of :app: «Тарелка дня» leads to the meal card and the profile.
 */
fun NavGraphBuilder.nutritionScreens(onOpenLevel: () -> Unit, stateful: Boolean = true, onOpen: (String) -> Unit = {}) {
    composable(HotRoute.NUTRITION_DAY) {
        PlateScreen(
            stateful = stateful,
            onOpenMeal = { slot -> onOpen(HotRoute.nutritionMeal(slot)) },
            onOpenProfile = { onOpen(HotRoute.NUTRITION_PROFILE) },
            onOpenLevel = onOpenLevel,
        )
    }
    composable(HotRoute.NUTRITION_PROFILE) {
        if (stateful) ProfileRoute(onOpenLevel) else NutritionProfileScreen(NutritionState(), ProfileActions())
    }
    composable(HotRoute.NUTRITION_LEVEL) {
        if (stateful) LevelRoute() else NutritionLevelScreen(NutritionState(), onPick = {})
    }
    composable(
        HotRoute.NUTRITION_MEAL,
        arguments = listOf(navArgument(HotRoute.NUTRITION_MEAL_ARG) { type = NavType.StringType }),
    ) { entry ->
        val slot = entry.arguments?.getString(HotRoute.NUTRITION_MEAL_ARG).orEmpty()
        if (stateful) MealRoute(slot) else MealCardScreen(NutritionState(), slot, onReplace = {})
    }
}

/** «Тарелка дня» whole (A41 §10), reached from «Сегодня» (audit 2026-11-08 S3). */
@Composable
private fun PlateScreen(stateful: Boolean, onOpenMeal: (String) -> Unit, onOpenProfile: () -> Unit, onOpenLevel: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 4.dp, bottom = HotSpacing.sectionGap)
            .testTag(PLATE_SCREEN_TAG),
    ) {
        if (stateful) {
            PlateOfDay(onOpenMeal = onOpenMeal, onOpenProfile = onOpenProfile, onOpenLevel = onOpenLevel)
        } else {
            PlateOfDayCard(NutritionState(), PlateActions(onOpenMeal = onOpenMeal, onOpenProfile = onOpenProfile, onOpenLevel = onOpenLevel))
        }
    }
}

@Composable
private fun ProfileRoute(onOpenLevel: () -> Unit, vm: NutritionViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    NutritionProfileScreen(
        state = state,
        actions = ProfileActions(
            onConsent = vm::consent,
            onWithdraw = vm::withdrawConsent,
            onAllergen = vm::cycleAllergen,
            onDiet = vm::toggleDiet,
            onIntolerance = vm::toggleIntolerance,
            onLifeStage = vm::toggleLifeStage,
            onMedical = vm::toggleMedical,
            onInjury = vm::toggleInjury,
            onDelete = vm::delete,
            onDeleteAll = vm::deleteAll,
            onOpenLevel = onOpenLevel,
        ),
    )
}

@Composable
private fun LevelRoute(vm: NutritionViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    NutritionLevelScreen(state, onPick = vm::setLevel)
}

@Composable
private fun MealRoute(slot: String, vm: NutritionViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    MealCardScreen(state, slot, onReplace = vm::replace)
}
