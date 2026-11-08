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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotTopBar
import com.healthofatitan.app.core.ui.component.StubScreen
import com.healthofatitan.app.core.ui.component.SyncMarkPhase
import com.healthofatitan.app.core.ui.component.SyncMarkState
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.domain.today.AutoOpen
import com.healthofatitan.app.domain.today.AutoOpenReason
import com.healthofatitan.app.domain.today.RassvetStep
import com.healthofatitan.app.domain.today.Ritual
import com.healthofatitan.app.domain.today.TodayCopy
import com.healthofatitan.app.feature.checkin.CHECKIN_FULL_SCREEN
import com.healthofatitan.app.feature.checkin.CheckinNav
import com.healthofatitan.app.feature.checkin.CheckinSettingsRow
import com.healthofatitan.app.feature.checkin.CheckinTexts
import com.healthofatitan.app.feature.checkin.CheckinTodayCards
import com.healthofatitan.app.feature.checkin.EveningAreas
import com.healthofatitan.app.feature.checkin.EveningKnownLine
import com.healthofatitan.app.feature.checkin.EveningWeather
import com.healthofatitan.app.feature.checkin.checkinScreens
import com.healthofatitan.app.core.account.AccountGate
import com.healthofatitan.app.core.database.AgeGate
import com.healthofatitan.app.feature.me.consent.AgeGateScreen
import com.healthofatitan.app.feature.me.account.AccountTexts
import com.healthofatitan.app.feature.me.account.accountScreen
import com.healthofatitan.app.feature.me.meScreen
import com.healthofatitan.app.feature.me.profile.ProfileTexts
import com.healthofatitan.app.feature.me.profile.profileScreen
import com.healthofatitan.app.feature.me.whoopScreen
import com.healthofatitan.app.feature.mentor.mentorScreen
import com.healthofatitan.app.feature.nutrition.NutritionTexts
import com.healthofatitan.app.feature.nutrition.nutritionScreens
import com.healthofatitan.app.feature.path.mentorPersonaScreen
import com.healthofatitan.app.feature.path.pathRitualScreen
import com.healthofatitan.app.feature.path.pathScreen
import com.healthofatitan.app.feature.sos.sosScreen
import com.healthofatitan.app.feature.today.CloseDayExtras
import com.healthofatitan.app.feature.today.dawnScreen
import com.healthofatitan.app.feature.today.todayScreen
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

/**
 * Bottom modules «Сегодня · Неделя · Путь» (D-051; «За плечами» opens from «Путь»). The mentor left the bar: Винс is
 * the circle on the left of every header.
 */
enum class HotTab(val route: String, val label: String, @param:DrawableRes val icon: Int) {
    TODAY(HotRoute.TODAY, "Сегодня", R.drawable.core_ui_ic_today),
    WEEK(HotRoute.WEEK, "Неделя", R.drawable.core_ui_ic_week),
    PATH(HotRoute.PATH, "Путь", R.drawable.core_ui_ic_path),
    ;

    val testTag: String get() = "tab_$route"
}

const val BEHIND_SCREEN_TAG = "screen_behind"

/** The screen a step of «Рассвет» opens (D-040: one ritual, three steps). */
fun RassvetStep.route(): String = when (this) {
    RassvetStep.DECK -> HotRoute.CHECKIN
    RassvetStep.REVEAL -> HotRoute.CHECKIN_REVEAL
    RassvetStep.DAWN -> HotRoute.DAWN
}

/**
 * App shell: one Activity, NavHost with 4 modules + `sos`, `dawn` and the screens under them (A14
 * §7, ADR-01, D-038). The header (A46 §2.1): Винс on the left, the date of «Сегодня» in the
 * middle, the profile on the right; help is «профиль › Помощь» (≤ 2 taps from any tab, T-06), the
 * quick settings tile and the shortcut open it in one.
 *
 * Nothing opens by itself (T-05) except «Рассвет» — once a day, after the night is scored — and
 * the crisis card (CrisisGuard ≥ 2, opened by the screen that detects it).
 *
 * @param pendingRoute route requested from outside (tile, shortcut, deep link), consumed once.
 * @param stateful false draws every screen from its default state without ViewModels: the
 *   navigation tests run without Hilt.
 * @param syncProgress live WHOOP sync: the Titan glyph by the date turns into the sync indicator.
 * @param rassvetDue the step «Рассвет» opens at by itself now ([com.healthofatitan.app.domain.today.Rassvet.trigger]);
 *   null when the night is not scored, the ritual is passed or it was already offered today.
 * @param rassvetNext the next step not passed today, for the chain deck → reveal → dawn and a tap
 *   on the «Рассвет» point.
 * @param onRassvetOffered the ritual was opened by itself today: it is not offered again.
 * @param persona the mentor's character for Винс's circle (D-030); null = not chosen yet.
 * @param ageGate the gate 18+ (R-13, A61 §1.2): until [AgeGate.CONFIRMED] the gate lies over the
 *   whole app and nothing of it can be seen or tapped; SOS is never behind it — the tile, the
 *   shortcut and «Помощь» on the gate open it over the gate. Tests start confirmed.
 * @param onAdultConfirmed «Мне есть 18 лет» was confirmed (written as `ConsentRecord age18`).
 * @param ageGateSaveFailed writing `age18` failed: the gate asks to try again.
 * @param accountGate the HoT account (A65, D-062): [AccountGate.SIGNED_OUT] puts «Вход» over the
 *   app, after the gate 18+ and never over SOS. Tests start signed in.
 * @param signIn draws «Вход»; its argument opens SOS over it.
 */
@Composable
fun HotApp(
    pendingRoute: String? = null,
    onPendingRouteConsumed: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    stateful: Boolean = true,
    syncProgress: SyncProgress = SyncProgress.Idle,
    rassvetDue: RassvetStep? = null,
    rassvetNext: RassvetStep? = null,
    onRassvetOffered: () -> Unit = {},
    persona: MentorPersona? = null,
    ageGate: AgeGate = AgeGate.CONFIRMED,
    onAdultConfirmed: () -> Unit = {},
    ageGateSaveFailed: Boolean = false,
    accountGate: AccountGate = AccountGate.SIGNED_IN,
    signIn: @Composable (onHelp: () -> Unit) -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val inSos = currentRoute == HotRoute.SOS
    val fullScreen = inSos || currentRoute == HotRoute.DAWN || currentRoute == HotRoute.TRAINING_PLAYER ||
        currentRoute == HotRoute.BREATHER || currentRoute == HotRoute.PATH_RITUAL || currentRoute in CHECKIN_FULL_SCREEN
    val nested = NESTED[currentRoute]
    val currentTab = nested?.tab ?: HotTab.entries.firstOrNull { it.route == currentRoute } ?: HotTab.TODAY
    val ageGated = ageGate != AgeGate.CONFIRMED && !inSos
    // «Вход» comes after the gate 18+ (R-13 stays first) and never lies over SOS.
    val signInGated = !ageGated && accountGate.showsSignIn && !inSos
    val gated = ageGated || signInGated
    var underage by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            navController.currentBackStackEntryFlow.first() // wait until NavHost has set the graph
            when (pendingRoute) {
                HotRoute.SOS -> navController.openSos()
                HotRoute.TRAINING_PLAYER, HotRoute.CHECKIN, HotRoute.BREATHER, HotRoute.MENTOR, HotRoute.BEHIND ->
                    navController.navigate(pendingRoute) { launchSingleTop = true }
                else -> navController.openTab(pendingRoute)
            }
            onPendingRouteConsumed()
        }
    }

    // «Рассвет» (D-040): one trigger a day, only over «Сегодня», never over help; then its steps
    // follow one another while the person goes on (the deck, the reveal, the dawn).
    var chained by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(rassvetDue, currentRoute, gated) {
        val step = rassvetDue ?: return@LaunchedEffect
        if (!gated && pendingRoute == null && currentRoute == HotRoute.TODAY && AutoOpen.reason(0, step) == AutoOpenReason.RASSVET) {
            onRassvetOffered()
            chained = "${LocalDate.now()}:${step.name}"
            navController.navigate(step.route()) { launchSingleTop = true }
        }
    }
    LaunchedEffect(rassvetNext, currentRoute) {
        val last = chained ?: return@LaunchedEffect
        val next = rassvetNext
        if (next == null || !last.startsWith(LocalDate.now().toString())) {
            chained = null
            return@LaunchedEffect
        }
        // back on «Сегодня» after a step that is now passed: open the next one; a step closed
        // without passing it ends the chain (the ritual is never pushed twice).
        if (currentRoute == HotRoute.TODAY && navController.currentDestination?.route == HotRoute.TODAY) {
            if (last.endsWith(next.name)) {
                chained = null
            } else {
                chained = "${LocalDate.now()}:${next.name}"
                navController.navigate(next.route()) { launchSingleTop = true }
            }
        }
    }

    val sync = rememberTopBarSync(syncProgress)
    SyncHaptics(sync)

    // «Ночное небо» behind every tab (A36 §4); SOS draws its own sober background on top.
    HotBackground {
        Scaffold(
            // Behind the gate 18+ the app is not in the tree of accessibility either (R-13).
            modifier = if (gated) Modifier.clearAndSetSemantics {} else Modifier,
            containerColor = Color.Transparent,
            topBar = {
                if (!fullScreen) {
                    HotTopBar(
                        // «Сегодня» and «Путь» have no title: the date and the hero speak there
                        title = nested?.title ?: currentTab.label.takeIf { currentTab != HotTab.TODAY && currentTab != HotTab.PATH },
                        overline = if (nested != null) nested.backLabel else TabHeaders.overline(currentTab, LocalDate.now()),
                        onBack = if (nested != null) ({ if (!navController.popBackStack()) navController.openTab(nested.tab.route) }) else null,
                        // The WHOOP screen has the big mark itself; one animated titan per screen.
                        sync = if (currentRoute == HotRoute.WHOOP) null else sync.toMark(),
                        mentor = { MentorCircle(persona) { navController.navigate(HotRoute.MENTOR) { launchSingleTop = true } } },
                        center = if (nested == null && currentTab == HotTab.TODAY) TabHeaders.today(LocalDate.now()) else null,
                        avatar = {
                            AvatarMenu(
                                persona = persona,
                                onHelp = { navController.openSos() },
                                onMentor = { navController.navigate(HotRoute.MENTOR_PERSONA) { launchSingleTop = true } },
                                onSettings = { navController.navigate(HotRoute.ME) { launchSingleTop = true } },
                            )
                        },
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
                val open: (String) -> Unit = { route -> navController.navigate(route) { launchSingleTop = true } }
                val back: () -> Unit = { if (!navController.popBackStack()) navController.openTab(HotRoute.TODAY) }
                todayScreen(
                    onConnectWhoop = { open(HotRoute.WHOOP) },
                    // CrisisGuard level ≥ 2 in the evening note opens the crisis card (A07 §4.1, D-029)
                    onOpenHelp = { navController.openSos() },
                    stateful = stateful,
                    // State cards (A44, D-036) in «Ритм дня»: the morning, the reveal, the ritual, a discovery.
                    stateCards = {
                        if (stateful) {
                            CheckinTodayCards(
                                CheckinNav(
                                    onWake = { open(HotRoute.CHECKIN) },
                                    onReveal = { open(HotRoute.CHECKIN_REVEAL) },
                                    onRitual = { open(HotRoute.CHECKIN_RITUAL) },
                                    onDiscovery = { open(HotRoute.CHECKIN_DISCOVERY) },
                                ),
                            )
                        }
                    },
                    // The evening of A44 inside «Закрыть день»: one evening, not a second survey.
                    closeDay = CloseDayExtras(
                        top = { if (stateful) EveningKnownLine(Modifier.padding(bottom = 10.dp)) },
                        afterBody = { day, body -> if (stateful) EveningAreas(day, body) },
                        afterAlcohol = { day -> if (stateful) EveningWeather(day, onCare = { open(HotRoute.CHECKIN_CARE) }) },
                    ),
                    onOpenRoute = { route ->
                        when (route) {
                            HotRoute.SOS -> navController.openSos()
                            in HotRoute.tabs -> navController.openTab(route)
                            else -> open(route)
                        }
                    },
                    onRitual = { ritual ->
                        when (ritual) {
                            Ritual.DAWN -> open((rassvetNext ?: RassvetStep.DAWN).route())
                            Ritual.WEEK_ASSEMBLY -> navController.openTab(HotRoute.WEEK)
                            // Переход and Приземление are off by default (D-043); their screens come later
                            else -> Unit
                        }
                    },
                    onBack = back,
                    onAskMentor = { open(HotRoute.MENTOR) },
                )
                mentorScreen(stateful = stateful, onOpenHelp = { navController.openSos() })
                weekScreen(stateful = stateful, onOpenHelp = { navController.openSos() })
                pathScreen(
                    stateful = stateful,
                    onOpenRitual = { open(HotRoute.PATH_RITUAL) },
                    onLookBack = { open(HotRoute.BEHIND) },
                    onHelp = { navController.openSos() },
                )
                pathRitualScreen(onClose = back, onHelp = { navController.openSos() }, stateful = stateful)
                behindScreen()
                meScreen(
                    onOpenWhoop = { open(HotRoute.WHOOP) },
                    onOpenHelp = { navController.openSos() },
                    onOpenMentor = { open(HotRoute.MENTOR_PERSONA) },
                    stateful = stateful,
                    onOpenNutrition = { open(HotRoute.NUTRITION_PROFILE) },
                    rows = { CheckinSettingsRow(onOpen = { open(HotRoute.CHECKIN_SETTINGS) }) },
                    onOpenProfile = { open(HotRoute.PROFILE) },
                    onOpenAccount = { open(HotRoute.ACCOUNT) },
                )
                profileScreen(stateful = stateful, onOpenHelp = { navController.openSos() })
                accountScreen(stateful = stateful)
                mentorPersonaScreen(stateful = stateful)
                nutritionScreens(onOpenLevel = { open(HotRoute.NUTRITION_LEVEL) }, stateful = stateful)
                whoopScreen(stateful = stateful)
                dawnScreen(onStart = back)
                sosScreen(onClose = back)
                checkinScreens(
                    onBack = back,
                    onGo = { route, replace ->
                        val from = navController.currentDestination?.route
                        navController.navigate(route) {
                            launchSingleTop = true
                            if (replace && from != null) popUpTo(from) { inclusive = true }
                        }
                    },
                    onHelp = { navController.openSos() },
                    onTalk = { open(HotRoute.MENTOR) },
                    stateful = stateful,
                )
                trainingScreens(
                    onOpenPlayer = { open(HotRoute.TRAINING_PLAYER) },
                    onFeelBad = { navController.openSos() },
                    onClosePlayer = { if (!navController.popBackStack(HotRoute.TODAY, inclusive = false)) navController.openTab(HotRoute.TODAY) },
                    stateful = stateful,
                )
            }
        }
        // The gate 18+ (R-13): over everything, and «Помощь» on it opens SOS over the gate.
        if (signInGated) signIn { navController.openSos() }
        if (ageGated) {
            AgeGateScreen(
                gate = ageGate,
                under = underage,
                onUnderChange = { underage = it },
                onConfirm = onAdultConfirmed,
                onHelp = { navController.openSos() },
                saveFailed = ageGateSaveFailed,
            )
        }
    }
}

/** «За плечами» (D-038, A47): Взято · Несу · Дано. A placeholder until its slice. */
private fun NavGraphBuilder.behindScreen() {
    composable(route = HotRoute.BEHIND) {
        StubScreen(lead = TodayCopy.BEHIND_SOON, blocks = emptyList(), testTag = BEHIND_SCREEN_TAG)
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

/**
 * A screen under a module (D-033, D-038): its module stays selected in the bar, the header shows
 * the way back. [backLabel] null: the arrow alone.
 */
private class NestedScreen(val tab: HotTab, val title: String, val backLabel: String?)

private val NESTED = mapOf(
    HotRoute.ME to NestedScreen(HotTab.PATH, "Настройки", null),
    // D-051: «За плечами» is the reverse plan of the Path, not a tab.
    HotRoute.BEHIND to NestedScreen(HotTab.PATH, "За плечами", "Путь"),
    HotRoute.MENTOR_PERSONA to NestedScreen(HotTab.PATH, "Наставник", null),
    HotRoute.PROFILE to NestedScreen(HotTab.PATH, ProfileTexts.TITLE, "Настройки"),
    HotRoute.ACCOUNT to NestedScreen(HotTab.PATH, AccountTexts.TITLE.n, "Настройки"),
    HotRoute.WHOOP to NestedScreen(HotTab.PATH, "WHOOP", "Настройки · Подключения"),
    HotRoute.NUTRITION_PROFILE to NestedScreen(HotTab.PATH, NutritionTexts.SET_UP, NutritionTexts.PROFILE_BACK),
    HotRoute.NUTRITION_LEVEL to NestedScreen(HotTab.PATH, NutritionTexts.LEVEL_TITLE, NutritionTexts.LEVEL_BACK),
    HotRoute.NUTRITION_MEAL to NestedScreen(HotTab.TODAY, NutritionTexts.PLATE_OF_DAY, NutritionTexts.MEAL_BACK),
    HotRoute.TRAINING to NestedScreen(HotTab.TODAY, TrainingTexts.SCREEN_TITLE, TrainingTexts.BACK_LABEL),
    HotRoute.CHECKIN_SETTINGS to NestedScreen(HotTab.PATH, CheckinTexts.SETTINGS, null),
    HotRoute.CHECKIN_COLLECTION to NestedScreen(HotTab.PATH, CheckinTexts.COLLECTION, CheckinTexts.SETTINGS),
    HotRoute.MENTOR to NestedScreen(HotTab.TODAY, "Винс", null),
    HotRoute.WHY to NestedScreen(HotTab.TODAY, TodayCopy.WHY_TITLE, TodayCopy.BLOCK_ME),
    HotRoute.SUPPORT to NestedScreen(HotTab.TODAY, TodayCopy.BLOCK_SUPPORTS, TodayCopy.BLOCK_SUPPORTS),
    HotRoute.ARSENAL to NestedScreen(HotTab.TODAY, TodayCopy.ARSENAL, null),
)

/**
 * Bottom modules in «Титаниум» (A37 §6): a floating glass pill over the content, line icons; the
 * active module is the dawn icon on a soft light. No shadow in the dark theme.
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
                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
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
