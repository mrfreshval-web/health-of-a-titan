package com.healthofatitan.app.integration.whoop.di

import android.app.AlarmManager
import android.content.Context
import com.healthofatitan.app.core.database.ProfileSettingsStore
import com.healthofatitan.app.core.database.dao.DailyLoopDao
import com.healthofatitan.app.core.database.dao.DawnDao
import com.healthofatitan.app.core.database.dao.DayDao
import com.healthofatitan.app.core.database.dao.EventLogDao
import com.healthofatitan.app.core.database.dao.MetricDao
import com.healthofatitan.app.core.network.EdgeConfig
import com.healthofatitan.app.core.security.FieldCipher
import com.healthofatitan.app.integration.whoop.EdgeClient
import com.healthofatitan.app.integration.whoop.safety.SafetyBus
import com.healthofatitan.app.integration.whoop.EncryptedWhoopCredentialStore
import com.healthofatitan.app.integration.whoop.FileSyncLogStore
import com.healthofatitan.app.integration.whoop.SyncLogStore
import com.healthofatitan.app.integration.whoop.WhoopAccessTokens
import com.healthofatitan.app.integration.whoop.WhoopApi
import com.healthofatitan.app.integration.whoop.WhoopConnection
import com.healthofatitan.app.integration.whoop.WhoopCredentialStore
import com.healthofatitan.app.integration.whoop.WhoopSyncScheduler
import com.healthofatitan.app.integration.whoop.WhoopSyncer
import com.healthofatitan.app.integration.whoop.day.CrisisSignals
import com.healthofatitan.app.integration.whoop.day.DailyLoopRepository
import com.healthofatitan.app.integration.whoop.day.DawnRepository
import com.healthofatitan.app.integration.whoop.day.DayNotifier
import com.healthofatitan.app.integration.whoop.day.HaikuLibrary
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import com.healthofatitan.app.integration.whoop.day.CheckinMorningSelf
import com.healthofatitan.app.core.database.dao.CheckinDao
import com.healthofatitan.app.integration.whoop.day.MorningCoordinator
import com.healthofatitan.app.integration.whoop.day.NextAlarmSource
import com.healthofatitan.app.integration.whoop.day.SystemZoneClock
import com.healthofatitan.app.core.database.dao.WeekDao
import com.healthofatitan.app.integration.whoop.week.WeekPathBridge
import com.healthofatitan.app.integration.whoop.week.WeekRepository
import com.healthofatitan.app.integration.whoop.week.WeekSignals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.healthofatitan.app.integration.whoop.day.TodayMarksRepository
import com.healthofatitan.app.integration.whoop.day.TodayMorning
import com.healthofatitan.app.integration.whoop.day.WeekPlanSource
import com.healthofatitan.app.core.database.dao.TodayDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import java.io.File
import okhttp3.OkHttpClient
import java.time.Clock
import java.time.Instant
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WhoopModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemZoneClock

    @Provides
    @Singleton
    fun provideCredentialStore(@ApplicationContext context: Context, cipher: FieldCipher): WhoopCredentialStore =
        EncryptedWhoopCredentialStore(context, cipher)

    @Provides
    @Singleton
    fun provideEdgeClient(config: EdgeConfig, http: OkHttpClient, json: Json): EdgeClient = EdgeClient(config, http, json)

    @Provides
    @Singleton
    fun provideAccessTokens(edge: EdgeClient, store: WhoopCredentialStore, clock: Clock): WhoopAccessTokens =
        WhoopAccessTokens(edge, store, clock)

    @Provides
    @Singleton
    fun provideWhoopApi(http: OkHttpClient, json: Json, tokens: WhoopAccessTokens): WhoopApi = WhoopApi(http, json, tokens)

    @Provides
    @Singleton
    fun provideSyncLog(@ApplicationContext context: Context, json: Json): SyncLogStore =
        FileSyncLogStore(File(context.noBackupFilesDir, "whoop_sync_log.json"), json)

    @Provides
    @Singleton
    fun provideSyncer(api: WhoopApi, dao: MetricDao, store: WhoopCredentialStore, clock: Clock, log: SyncLogStore): WhoopSyncer =
        WhoopSyncer(api, dao, store, clock, log)

    @Provides
    @Singleton
    fun provideScheduler(@ApplicationContext context: Context): WhoopSyncScheduler = WhoopSyncScheduler(context)

    @Provides
    @Singleton
    fun provideConnection(
        config: EdgeConfig,
        edge: EdgeClient,
        store: WhoopCredentialStore,
        tokens: WhoopAccessTokens,
        syncer: WhoopSyncer,
        scheduler: WhoopSyncScheduler,
        dao: MetricDao,
        clock: Clock,
    ): WhoopConnection = WhoopConnection(
        config, edge, store, tokens, syncer, scheduler, dao, clock,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    /** A09 §7a: tomorrow's alarm sets tonight's bedtime; reading it needs no permission. */
    @Provides
    @Singleton
    fun provideNextAlarm(@ApplicationContext context: Context): NextAlarmSource = NextAlarmSource {
        context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime?.let(Instant::ofEpochMilli)
    }

    @Provides
    @Singleton
    fun provideDayPlans(
        metricDao: MetricDao,
        dayDao: DayDao,
        alarms: NextAlarmSource,
        loop: DailyLoopRepository,
        checkin: CheckinDao,
        profile: ProfileSettingsStore,
        store: WhoopCredentialStore,
    ): DayPlanRepository = DayPlanRepository(
        metricDao, dayDao, alarms, SystemZoneClock, loop, CheckinMorningSelf(checkin),
        address = { profile.current().addressForm },
        whoopConnected = { store.credentials.value != null },
    )

    /** Return after a pause (A18 И4.4) and the crisis log without text (A07 §4.7). */
    @Provides
    @Singleton
    fun provideDailyLoop(dao: DailyLoopDao, bus: SafetyBus): DailyLoopRepository = DailyLoopRepository(dao, SystemZoneClock, bus)

    /** The safety bus (A60 §3, D-051): one SafetyState for «Сегодня», «Неделя» and «Путь». */
    @Provides
    @Singleton
    fun provideSafetyBus(dao: EventLogDao): SafetyBus = SafetyBus(dao, SystemZoneClock)

    /** CrisisGuard (A07 §4.1): a level ≥ 2 in the last 72 h silences the haiku of «Рассвет». */
    @Provides
    @Singleton
    fun provideCrisisSignals(loop: DailyLoopRepository): CrisisSignals = loop

    /** «Рассвет»: the haiku library comes from the content pipeline (assets/content/haiku). */
    @Provides
    @Singleton
    fun provideDawn(
        @ApplicationContext context: Context,
        metricDao: MetricDao,
        dawnDao: DawnDao,
        plans: DayPlanRepository,
        crisis: CrisisSignals,
    ): DawnRepository {
        val library by lazy {
            runCatching { context.assets.open(HaikuLibrary.ASSET).bufferedReader().use { HaikuLibrary.parse(it.readText()) } }
                .getOrDefault(emptyList())
        }
        return DawnRepository(metricDao, dawnDao, plans, { library }, crisis, SystemZoneClock)
    }

    @Provides
    @Singleton
    fun provideMorning(syncer: WhoopSyncer, plans: DayPlanRepository, notifier: DayNotifier): MorningCoordinator =
        MorningCoordinator(syncer, plans, notifier, SystemZoneClock)

    /** «Сегодня» v3 (A46): water, meals and tasks marked by hand (Room 10). */
    @Provides
    @Singleton
    fun provideTodayMarks(dao: TodayDao): TodayMarksRepository = TodayMarksRepository(dao, SystemZoneClock)

    /**
     * «Неделя» (A52, D-044): the goals of the week in Room 11, their progress from the plan, WHOOP
     * sessions and memory, the day scores and the Titan of the week. [WeekSignals] is bound in :app.
     */
    @Provides
    @Singleton
    fun provideWeek(
        weekDao: WeekDao,
        todayDao: TodayDao,
        dayDao: DayDao,
        metricDao: MetricDao,
        checkinDao: CheckinDao,
        plans: DayPlanRepository,
        syncer: WhoopSyncer,
        signals: WeekSignals,
        store: WhoopCredentialStore,
        dawn: DawnRepository,
        path: java.util.Optional<WeekPathBridge>,
        profile: ProfileSettingsStore,
    ): WeekRepository = WeekRepository(
        weekDao, todayDao, dayDao, metricDao, checkinDao, plans, syncer.glance, signals, SystemZoneClock,
        whoopConnected = store.credentials.map { it != null },
        haiku = { day -> runCatching { dawn.shownHaiku(day).first() }.getOrNull() },
        path = path.orElse(WeekPathBridge.None),
        profile = profile,
    )

    /** Tasks of the day come from the week (A52 §2.2): the real source replaces the legacy one. */
    @Provides
    @Singleton
    fun provideWeekPlan(week: WeekRepository): WeekPlanSource = week

    @Provides
    @Singleton
    fun provideTodayMorning(checkin: CheckinDao, dawn: DawnRepository): TodayMorning = TodayMorning(CheckinMorningSelf(checkin), dawn)
}

/** «Путь» (claude/path-v1) may bind a [WeekPathBridge]; until then the week runs without it (A57 §4.3). */
@Module
@InstallIn(SingletonComponent::class)
abstract class WeekPathBridgeModule {
    @dagger.BindsOptionalOf
    abstract fun pathBridge(): WeekPathBridge
}
