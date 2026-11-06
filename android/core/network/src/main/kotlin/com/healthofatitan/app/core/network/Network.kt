package com.healthofatitan.app.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Base URL of the Worker hot-edge (A14 §5), from `BuildConfig.EDGE_BASE_URL` of :app.
 * Blank in a build without configuration: the WHOOP screen then says so instead of failing.
 */
data class EdgeConfig(
    val baseUrl: String,
    /** Only tests against a local MockWebServer turn this off; the app always requires HTTPS. */
    val requireHttps: Boolean = true,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && (baseUrl.startsWith("https://") || (!requireHttps && baseUrl.startsWith("http://")))

    fun url(path: String): String = baseUrl.trimEnd('/') + path
}

/** Shared JSON: unknown fields are ignored, so only the fields we declare are ever parsed. */
val HotJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** Runs a blocking OkHttp call off the main thread; cancelling the coroutine cancels the call. */
suspend fun Call.await(): Response = withContext(Dispatchers.IO) {
    val call = this@await
    val job = coroutineContext[kotlinx.coroutines.Job]
    val handle = job?.invokeOnCompletion { if (it != null) call.cancel() }
    try {
        call.execute()
    } finally {
        handle?.dispose()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * One OkHttp client for WHOOP and the Worker. No logging interceptor on purpose: requests
     * carry tokens and responses carry health data (A14 §5, CLAUDE.md red lines).
     */
    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    fun provideJson(): Json = HotJson
}
