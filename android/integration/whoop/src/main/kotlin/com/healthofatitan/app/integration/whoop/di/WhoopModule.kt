package com.healthofatitan.app.integration.whoop.di

import android.content.Context
import com.healthofatitan.app.core.database.dao.MetricDao
import com.healthofatitan.app.core.network.EdgeConfig
import com.healthofatitan.app.core.security.FieldCipher
import com.healthofatitan.app.integration.whoop.EdgeClient
import com.healthofatitan.app.integration.whoop.EncryptedWhoopCredentialStore
import com.healthofatitan.app.integration.whoop.WhoopAccessTokens
import com.healthofatitan.app.integration.whoop.WhoopApi
import com.healthofatitan.app.integration.whoop.WhoopConnection
import com.healthofatitan.app.integration.whoop.WhoopCredentialStore
import com.healthofatitan.app.integration.whoop.WhoopSyncScheduler
import com.healthofatitan.app.integration.whoop.WhoopSyncer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WhoopModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

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
    fun provideSyncer(api: WhoopApi, dao: MetricDao, store: WhoopCredentialStore, clock: Clock): WhoopSyncer =
        WhoopSyncer(api, dao, store, clock)

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
    ): WhoopConnection = WhoopConnection(config, edge, store, tokens, syncer, scheduler, dao, clock)
}
