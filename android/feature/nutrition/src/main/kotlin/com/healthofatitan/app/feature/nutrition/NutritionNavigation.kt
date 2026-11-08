package com.healthofatitan.app.feature.nutrition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.healthofatitan.app.core.model.nav.HotRoute

/**
 * The food screens inside a tab (A41 §10): «Я › Еда и тело», the level and the meal card. No new
 * tabs (the tab bar belongs to stream Г). [stateful] false draws defaults without view models.
 */
fun NavGraphBuilder.nutritionScreens(onOpenLevel: () -> Unit, stateful: Boolean = true) {
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
