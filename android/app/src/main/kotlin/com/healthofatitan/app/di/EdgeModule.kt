package com.healthofatitan.app.di

import com.healthofatitan.app.BuildConfig
import com.healthofatitan.app.core.network.EdgeConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Address of the Worker `hot-edge` (A14 §5). Not a secret: it comes from the repository variable
 * EDGE_BASE_URL (CI) or local.properties. Empty means «не настроено»: the WHOOP screen says so.
 */
@Module
@InstallIn(SingletonComponent::class)
object EdgeModule {
    @Provides
    @Singleton
    fun provideEdgeConfig(): EdgeConfig = EdgeConfig(BuildConfig.EDGE_BASE_URL)
}
