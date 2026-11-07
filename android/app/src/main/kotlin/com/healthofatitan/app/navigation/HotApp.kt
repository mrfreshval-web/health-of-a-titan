package com.healthofatitan.app.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotTopBar
import com.healthofatitan.app.core.ui.component.StatusRings
import com.healthofatitan.app.core.ui.component.SyncMarkPhase
import com.healthofatitan.app.core.ui.component.SyncMarkState
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.feature.me.meScreen
import com.healthofatitan.app.feature.me.whoopScreen
import com.healthofatitan.app.feature.mentor.mentorScreen
import com.healthofatitan.app.feature.sos.sosScreen
import com.healthofatitan.app.feature.today.dawnScreen
import com.healthofatitan.app.feature.today.todayScreen
import com.healthofatitan.app.feature.training.TrainingDayCardRoute
import com.healthofatitan.app.feature.training.TrainingTexts
import com.healthofatitan.app.feature.training.trainingScreens
import com.healthofatitan.app.feature.week.weekScreen
import com.healthofatitan.app.integration.whoop.SyncIndicator
import com.healthofatitan.app.integration.whoop.SyncIndicators
import com.healthofatitan.app.integration.whoop.SyncLight
import com.healthofatitan.app.integration.whoop.SyncProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Bottom tabs «Сегодня · Наставник · Неделя · Я» (A11 §1.1). */
enum class HotTab(val route: String, val label: String, @param:DrawableRes val icon: Int) {
    TODAY(HotRoute.TODAY, "Сегодня", R.drawable.core_ui_ic_today),
    MENTOR(HotRoute.MENTOR, "Наставник", R.drawable.core_ui_ic_mentor),
    WEEK(HotRoute.WEEK, "Неделя", R.drawable.core_ui_ic_week),
    ME(HotRoute.ME, "Я", R.drawable.core_ui_ic_me),
    ;

    val testTag: String get() = "tab_$route"
}

/**
 * App shell: one Activity, NavHost with 4 tabs + `sos` and `dawn` outside the tabs (A14 §7, ADR-01).
 * D-029: the header of every tab is the Titan glyph and three rings; help is «Я › Помощь» and
 * «⋮ › Помощь» in the mentor chat (≤ 2 taps), the quick settings tile and the shortcut open it in one.
 *
 * @param pendingRoute route requested from outside (tile, shortcut, deep link), consumed once.
 * @param stateful false draws every screen from its default state without ViewModels: the
 *   navigation tests run without Hilt.
 * @param syncProgress live WHOOP sync: the mark in every tab header turns into the compact sync
 *   indicator, and a sync the user asked for ends with a haptic tick (success or failure).
 * @param rings the three header rings (D-029), from data only.
 * @param dawnDue «Рассвет» is due (first open of the day after 05:00, plan ready): shown once.
 */
@Composable
fun HotApp(
    pendingRoute: String? = null,
    onPendingRouteConsumed: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    stateful: Boolean = true,
    syncProgress: SyncProgress = SyncProgress.Idle,
    rings: StatusRings = StatusRings.Empty,
    dawnDue: Boolean = false,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val inSos = currentRoute == HotRoute.SOS
    val fullScreen = inSos || currentRoute == HotRoute.DAWN || currentRoute == HotRoute.TRAINING_PLAYER
    val nested = NESTED[currentRoute]
    val currentTab = nested?.tab ?: HotTab.entries.firstOrNull { it.route == currentRoute } ?: HotTab.TODAY

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            navController.currentBackStackEntryFlow.first() // wait until NavHost has set the graph
            when (pendingRoute) {
                HotRoute.SOS -> navController.openSos()
                HotRoute.TRAINING_PLAYER -> navController.navigate(HotRoute.TRAINING_PLAYER) { launchSingleTop = true }
                else -> navController.openTab(pendingRoute)
            }
            onPendingRouteConsumed()
        }
    }

    // «Рассвет» once a day, only over «Сегодня» and never over help.
    LaunchedEffect(dawnDue, currentRoute) {
        if (dawnDue && pendingRoute == null && currentRoute == HotRoute.TODAY) {
            navController.navigate(HotRoute.DAWN) { launchSingleTop = true }
        }
    }

    val sync = rememberTopBarSync(syncProgress)
    SyncHaptics(sync)

    // «Ночное небо» behind every tab (A36 §4); SOS draws its own sober background on top.
    HotBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (!fullScreen) {
                    HotTopBar(
                        // «Сегодня» has no title: the hero is the screen (A37 s2)
                        title = nested?.title ?: currentTab.label.takeIf { currentTab != HotTab.TODAY },
                        overline = nested?.backLabel ?: TabHeaders.overline(currentTab, LocalDate.now()),
                        onBack = if (nested != null) ({ if (!navController.popBackStack()) navController.openTab(nested.tab.route) }) else null,
                        // The WHOOP screen has the big mark itself; one animated titan per screen.
                        sync = if (currentRoute == HotRoute.WHOOP) null else sync.toMark(),
                        rings = rings,
                    )
                }
            },
            bottomBar = {
                if (!fullScreen) HotBottomBar(currentTab = currentTab, onTabClick = { navController.openTab(it.route) })
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = HotRoute.TODAY,
                modifier = if (fullScreen) Modifier else Modifier.padding(padding),
            ) {
                todayScreen(
                    onConnectWhoop = { navController.navigate(HotRoute.WHOOP) { launchSingleTop = true } },
                    stateful = stateful,
                    // Stream B: «Тренировка дня», its own card from :feature:training.
                    trainingCard = {
                        if (stateful) TrainingDayCardRoute(onOpen = { navController.navigate(HotRoute.TRAINING) { launchSingleTop = true } })
                    },
                )
                mentorScreen(onOpenHelp = { navController.openSos() })
                weekScreen()
                meScreen(
                    onOpenWhoop = { navController.navigate(HotRoute.WHOOP) { launchSingleTop = true } },
                    onOpenHelp = { navController.openSos() },
                    stateful = stateful,
                )
                whoopScreen(stateful = stateful)
                dawnScreen(onStart = { if (!navController.popBackStack()) navController.openTab(HotRoute.TODAY) })
                sosScreen(onClose = { if (!navController.popBackStack()) navController.openTab(HotRoute.TODAY) })
                trainingScreens(
                    onOpenPlayer = { navController.navigate(HotRoute.TRAINING_PLAYER) { launchSingleTop = true } },
                    onFeelBad = { navController.openSos() },
                    onClosePlayer = { if (!navController.popBackStack(HotRoute.TODAY, inclusive = false)) navController.openTab(HotRoute.TODAY) },
                    stateful = stateful,
                )
            }
        }
    }
}

/** How long a failed sync stays visible in the header before the mark rests again. */
private const val TOP_BAR_ERROR_MS = 8_000L

@Composable
private fun rememberTopBarSync(progress: SyncProgress): SyncIndicator {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(progress) {
        now = System.currentTimeMillis()
        val next = SyncIndicators.nextChangeAt(progress, now, TOP_BAR_ERROR_MS) ?: return@LaunchedEffect
        delay(next - now + 50)
        now = System.currentTimeMillis()
    }
    return SyncIndicators.of(progress, now, TOP_BAR_ERROR_MS)
}

/**
 * A sync the user started ends with a tick: on success «Титан приходит» in the header gives the
 * ticks itself (palms, push), so only a failure is ticked here («Reject»).
 */
@Composable
private fun SyncHaptics(sync: SyncIndicator) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(sync.key, sync.light) {
        if (sync.haptic && sync.light == SyncLight.ERROR) haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }
}

private fun SyncIndicator.toMark(): SyncMarkState = SyncMarkState(SyncMarkPhase.valueOf(light.name), progress, key, celebrate)

/** A screen inside a tab (mockup 09 «← Я · Настройки»): its tab stays selected, the bar shows the way back. */
private class NestedScreen(val tab: HotTab, val title: String, val backLabel: String)

private val NESTED = mapOf(
    HotRoute.WHOOP to NestedScreen(HotTab.ME, "WHOOP", "Я · Подключения"),
    HotRoute.TRAINING to NestedScreen(HotTab.TODAY, TrainingTexts.SCREEN_TITLE, TrainingTexts.BACK_LABEL),
)

/**
 * Bottom tabs in «Титаниум» (A37 §6): a floating glass pill over the content, line icons; the
 * active tab is the dawn icon on a soft light. No shadow in the dark theme.
 */
@Composable
private fun HotBottomBar(currentTab: HotTab, onTabClick: (HotTab) -> Unit) {
    val tokens = HotThemeTokens.colors
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(tokens.glassTop, tokens.glassBottom)), shape)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f), shape)
                .border(1.dp, tokens.glassEdge, shape),
        ) {
            HotTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = tab == currentTab,
                    onClick = { onTabClick(tab) },
                    icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = tokens.ruleSoft,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.testTag(tab.testTag),
                )
            }
        }
    }
}

private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.openSos() {
    navigate(HotRoute.SOS) { launchSingleTop = true }
}
