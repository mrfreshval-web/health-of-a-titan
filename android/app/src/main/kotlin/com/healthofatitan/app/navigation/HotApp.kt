package com.healthofatitan.app.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.healthofatitan.app.core.ui.component.SyncMarkPhase
import com.healthofatitan.app.core.ui.component.SyncMarkState
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.feature.me.meScreen
import com.healthofatitan.app.feature.me.whoopScreen
import com.healthofatitan.app.feature.mentor.mentorScreen
import com.healthofatitan.app.feature.sos.sosScreen
import com.healthofatitan.app.feature.today.todayScreen
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
 * App shell: one Activity, NavHost with 4 tabs + `sos` outside the tabs (A14 §7, ADR-01).
 * The SOS button sits in the top bar of every tab screen: 1 tap to 988/911 (A11 §1.3).
 *
 * @param pendingRoute route requested from outside (tile, shortcut, deep link), consumed once.
 * @param stateful false draws every screen from its default state without ViewModels: the
 *   navigation tests run without Hilt.
 * @param syncProgress live WHOOP sync: the mark in every tab header turns into the compact sync
 *   indicator, and a sync the user asked for ends with a haptic tick (success or failure).
 */
@Composable
fun HotApp(
    pendingRoute: String? = null,
    onPendingRouteConsumed: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    stateful: Boolean = true,
    syncProgress: SyncProgress = SyncProgress.Idle,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val inSos = currentRoute == HotRoute.SOS
    val nested = NESTED[currentRoute]
    val currentTab = nested?.tab ?: HotTab.entries.firstOrNull { it.route == currentRoute } ?: HotTab.TODAY

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            navController.currentBackStackEntryFlow.first() // wait until NavHost has set the graph
            if (pendingRoute == HotRoute.SOS) navController.openSos() else navController.openTab(pendingRoute)
            onPendingRouteConsumed()
        }
    }

    val sync = rememberTopBarSync(syncProgress)
    SyncHaptics(sync)

    // «Ночное небо» behind every tab (A36 §4); SOS draws its own sober background on top.
    HotBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (!inSos) {
                    HotTopBar(
                        title = nested?.title ?: currentTab.label,
                        overline = nested?.backLabel ?: TabHeaders.overline(currentTab, LocalDate.now()),
                        onSosClick = { navController.openSos() },
                        onBack = if (nested != null) ({ if (!navController.popBackStack()) navController.openTab(nested.tab.route) }) else null,
                        // The WHOOP screen has the big mark itself; one animated titan per screen.
                        sync = if (currentRoute == HotRoute.WHOOP) null else sync.toMark(),
                    )
                }
            },
            bottomBar = {
                if (!inSos) HotBottomBar(currentTab = currentTab, onTabClick = { navController.openTab(it.route) })
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = HotRoute.TODAY,
                modifier = if (inSos) Modifier else Modifier.padding(padding),
            ) {
                todayScreen(onConnectWhoop = { navController.navigate(HotRoute.WHOOP) { launchSingleTop = true } }, stateful = stateful)
                mentorScreen()
                weekScreen()
                meScreen(onOpenWhoop = { navController.navigate(HotRoute.WHOOP) { launchSingleTop = true } }, stateful = stateful)
                whoopScreen(stateful = stateful)
                sosScreen(onClose = { if (!navController.popBackStack()) navController.openTab(HotRoute.TODAY) })
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

/** One haptic tick per sync the user started: «Confirm» on success, «Reject» on failure. */
@Composable
private fun SyncHaptics(sync: SyncIndicator) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(sync.key, sync.light) {
        if (!sync.haptic) return@LaunchedEffect
        when (sync.light) {
            SyncLight.SUCCESS -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            SyncLight.ERROR -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
            else -> Unit
        }
    }
}

private fun SyncIndicator.toMark(): SyncMarkState = SyncMarkState(SyncMarkPhase.valueOf(light.name), progress, key, celebrate)

/** A screen inside a tab (mockup 09 «← Я · Настройки»): its tab stays selected, the bar shows the way back. */
private class NestedScreen(val tab: HotTab, val title: String, val backLabel: String)

private val NESTED = mapOf(
    HotRoute.WHOOP to NestedScreen(HotTab.ME, "WHOOP", "Я · Подключения"),
)

/**
 * Bottom tabs in «Колоннада» (A36 §5): line icons; the active tab is a gold icon on a «gold 14 %»
 * pill; labels in Manrope. A hairline separates the bar from the content — no shadow.
 */
@Composable
private fun HotBottomBar(currentTab: HotTab, onTabClick: (HotTab) -> Unit) {
    val hairline = HotThemeTokens.colors.hairline
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        modifier = Modifier.drawBehind {
            drawLine(hairline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
        },
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
                    indicatorColor = HotThemeTokens.colors.ruleSoft,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.testTag(tab.testTag),
            )
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
