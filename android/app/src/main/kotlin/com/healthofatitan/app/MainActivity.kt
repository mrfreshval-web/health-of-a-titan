package com.healthofatitan.app

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.healthofatitan.app.core.account.AccountGate
import com.healthofatitan.app.core.account.AccountSession
import com.healthofatitan.app.core.account.LocalDataWiper
import com.healthofatitan.app.core.database.AgeGate
import com.healthofatitan.app.core.database.AgeGateStore
import com.healthofatitan.app.core.database.ProfileSettingsStore
import com.healthofatitan.app.core.datastore.MentorPrefs
import com.healthofatitan.app.core.datastore.MentorSettings
import com.healthofatitan.app.core.model.profile.AddressForm
import com.healthofatitan.app.core.ui.text.LocalAddressForm
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.navigation.DeepLinks
import com.healthofatitan.app.integration.whoop.WhoopConnection
import com.healthofatitan.app.navigation.HotApp
import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.today.Rassvet
import com.healthofatitan.app.domain.today.RassvetStep
import com.healthofatitan.app.feature.checkin.CheckinRepository
import com.healthofatitan.app.feature.me.account.SignInRoute
import com.healthofatitan.app.integration.whoop.day.DawnRepository
import com.healthofatitan.app.integration.whoop.day.DailyLoopRepository
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import com.healthofatitan.app.integration.healthconnect.StepsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var whoop: WhoopConnection

    /** A65 §6: an account deleted elsewhere (another phone, the web page) wipes this phone too. */
    @Inject
    lateinit var account: AccountSession

    @Inject
    lateinit var wiper: LocalDataWiper

    @Inject
    lateinit var plans: DayPlanRepository

    @Inject
    lateinit var dawn: DawnRepository

    @Inject
    lateinit var dailyLoop: DailyLoopRepository

    @Inject
    lateinit var steps: StepsRepository

    @Inject
    lateinit var mentor: MentorSettings

    /** The profile «Я» (A61, D-055): its address form reaches every text through [LocalAddressForm]. */
    @Inject
    lateinit var profile: ProfileSettingsStore

    /** The gate 18+ (R-13, A61 §1.2): `ConsentRecord age18`. */
    @Inject
    lateinit var ageGate: AgeGateStore

    /** The gate as the screen reads it: a journal that cannot be read means «ask». */
    private val ageGateState: Flow<AgeGate> by lazy { ageGate.gate.catch { emit(AgeGate.ASK) } }

    /** «Вход» over the app when there is no HoT account (A65, D-062); unreadable → no screen. */
    private val accountGate: Flow<AccountGate> by lazy { whoop.accountGate.catch { emit(AccountGate.NOT_REQUIRED) } }

    /** State cards (A44, D-036): which morning screen is due. */
    @Inject
    lateinit var checkin: CheckinRepository

    /** The day «Рассвет» was last opened by itself (a per-device convenience, not health data). */
    private val rassvetPrefs by lazy { getSharedPreferences("rassvet", MODE_PRIVATE) }
    private val rassvetOffered by lazy { MutableStateFlow(rassvetPrefs.getString(KEY_OFFERED, null)) }

    /**
     * «Рассвет» as one ritual (D-040): the night is scored (today's plan is built from data), the
     * steps not yet passed today — the deck, the reveal, the dawn — and whether it was offered.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val rassvet: Flow<Pair<RassvetStep?, RassvetStep?>> by lazy {
        val facts = minuteTicks().map { it.toLocalDate() }.distinctUntilChanged().flatMapLatest { day ->
            combine(checkin.state, plans.latest, dawn.shown(day), rassvetOffered, minuteTicks()) { s, plan, dawnShown, offered, now ->
                val nightReady = plan?.dayKey == now.toLocalDate() && DayFlag.MANUAL !in plan.flags
                val next = if (nightReady && now.hour >= Rassvet.FROM_HOUR) Rassvet.next(s.wakeDone, s.revealReady, dawnShown) else null
                val due = Rassvet.trigger(now, nightReady, offered == now.toLocalDate().toString(), s.wakeDone, s.revealReady, dawnShown)
                due to next
            }
        }
        facts.catch { emit(null to null) }
    }

    private fun markRassvetOffered() {
        val day = LocalDate.now().toString()
        rassvetPrefs.edit().putString(KEY_OFFERED, day).apply()
        rassvetOffered.value = day
    }

    private var pendingRoute by mutableStateOf<String?>(null)

    /** Writing `age18` failed: the gate shows «Не получилось сохранить» (review of Agent 10). */
    private var ageSaveFailed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Cold start from the launcher only: let «пульс оживляет титана» finish (≤ 1000 ms from
        // process start, brand/mark-v2 §7). Never longer, never for SOS (tile, shortcut, deep link
        // must open at once) and not at all when the user turned animations off.
        val holdForIcon = savedInstanceState == null &&
            intent?.action == Intent.ACTION_MAIN &&
            ValueAnimator.areAnimatorsEnabled()
        splash.setKeepOnScreenCondition {
            holdForIcon && SystemClock.uptimeMillis() - Process.getStartUptimeMillis() < SPLASH_ICON_MS
        }
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(SPLASH_FADE_MS)
                .withEndAction { provider.remove() }
                .start()
        }
        // The app is always dark («Ночь»): light status and navigation bar icons in any system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // UX review 119, R6: a tap on the top of «Сегодня» opened the system «Открыть в: Календарь».
        // The app sends no such intent; the likely source is the system reading the screen text
        // (content capture → smart actions on «суббота · 7 ноября», «отбой в 02:50»). Health data
        // should not reach system text analysis anyway: the whole window is excluded from it.
        window.decorView.importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        if (savedInstanceState == null) pendingRoute = routeOf(intent)
        lifecycleScope.launch {
            account.deleted.first { it }
            wiper.wipe()
        }
        setContent {
            HotTheme {
                val syncProgress by whoop.progress.collectAsStateWithLifecycle()
                val ritual by rassvet.collectAsStateWithLifecycle(Pair<RassvetStep?, RassvetStep?>(null, null))
                val mentorPrefs by mentor.prefs.collectAsStateWithLifecycle(MentorPrefs())
                val addressForm by profile.addressForm.collectAsStateWithLifecycle(AddressForm.NEUTRAL)
                val gate by ageGateState.collectAsStateWithLifecycle(AgeGate.LOADING)
                val account by accountGate.collectAsStateWithLifecycle(AccountGate.LOADING)
                CompositionLocalProvider(LocalAddressForm provides addressForm) {
                    HotApp(
                        pendingRoute = pendingRoute,
                        onPendingRouteConsumed = { pendingRoute = null },
                        syncProgress = syncProgress,
                        rassvetDue = ritual.first,
                        rassvetNext = ritual.second,
                        onRassvetOffered = ::markRassvetOffered,
                        persona = mentorPrefs.persona,
                        ageGate = gate,
                        onAdultConfirmed = {
                            ageSaveFailed = false
                            lifecycleScope.launch { ageSaveFailed = runCatching { ageGate.confirmAdult() }.isFailure }
                        },
                        ageGateSaveFailed = ageSaveFailed,
                        accountGate = account,
                        signIn = { onHelp -> SignInRoute(onHelp = onHelp) },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // A18 И1.2 ①: every app start refreshes the 28-day window unless it is fresh (WorkManager).
        // A65 §3.5 first: the old WHOOP session becomes an account, then the sync uses it.
        lifecycleScope.launch {
            runCatching { whoop.migrateLegacySession() }
            runCatching { account.checkAlive() }
            runCatching { whoop.onAppOpen() }
        }
        // A18 И4.4: the date of this open (silence is counted in whole days without one).
        lifecycleScope.launch { runCatching { dailyLoop.recordOpen() } }
        lifecycleScope.launch { runCatching { steps.refresh() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeOf(intent)?.let { pendingRoute = it }
    }

    private fun routeOf(intent: Intent?): String? =
        intent?.data?.let { DeepLinks.routeFor(it.scheme, it.host) }

    private fun minuteTicks() = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    private companion object {
        const val SPLASH_ICON_MS = 1_000L
        const val SPLASH_FADE_MS = 200L
        const val KEY_OFFERED = "offered_on"
    }
}
