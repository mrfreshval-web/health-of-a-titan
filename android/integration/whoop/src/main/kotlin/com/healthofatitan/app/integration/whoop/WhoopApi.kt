package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.core.network.await
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant

/** WHOOP refused the data request for a reason that a retry will not fix by itself. */
sealed class WhoopApiException(message: String) : IOException(message) {
    class NotConnected : WhoopApiException("not connected")

    class ReauthRequired : WhoopApiException("reauth required")

    class NotAllowed : WhoopApiException("not allowed")

    /** The HoT Worker could not hand out a token (outage); [code] is its HTTP status. */
    class Unavailable(val code: Int? = null) : WhoopApiException("edge unavailable")

    class RateLimited : WhoopApiException("rate limited")

    /** WHOOP answered a data request with a non-2xx status (403 scope, 404 path, 5xx outage…). */
    class Http(val code: Int, val path: String) : WhoopApiException("whoop http $code")
}

/**
 * Reads the WHOOP window straight from the WHOOP API v2 with the in-memory access token
 * (A14 §1: the phone talks to WHOOP directly; the Worker never sees health data).
 *
 * [Факт: developer.whoop.com/api] collections take `limit` ≤ 25, `start`, `end`, `nextToken`
 * and answer `{records, next_token}`. Paths are the v2 ones; [проверить на первом входе].
 * Rate limits: 100/min, 10 000/day — a 35-day window is ~10 requests.
 */
class WhoopApi(
    private val http: OkHttpClient,
    private val json: Json,
    private val tokens: WhoopAccessTokens,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
) {

    /** Loads the window collection by collection; [onStep] reports which one is on its way. */
    internal suspend fun window(from: Instant, to: Instant, onStep: (SyncStep) -> Unit = {}): WhoopWindow {
        onStep(SyncStep.CYCLES)
        val cycles = collection("cycle", from, to, WhoopCycle.serializer())
        onStep(SyncStep.RECOVERY)
        val recoveries = collection("recovery", from, to, WhoopRecovery.serializer())
        onStep(SyncStep.SLEEP)
        val sleeps = collection("activity/sleep", from, to, WhoopSleep.serializer())
        onStep(SyncStep.WORKOUTS)
        val workouts = collection("activity/workout", from, to, WhoopWorkout.serializer())
        return WhoopWindow(cycles, recoveries, sleeps, workouts)
    }

    private suspend fun <T> collection(path: String, from: Instant, to: Instant, item: KSerializer<T>): List<T> {
        val pageSerializer = WhoopPage.serializer(item)
        val out = mutableListOf<T>()
        var next: String? = null
        var pages = 0
        do {
            val url = baseUrl.newBuilder()
                .addPathSegments("developer/v2/$path")
                .addQueryParameter("limit", "25")
                .addQueryParameter("start", from.toString())
                .addQueryParameter("end", to.toString())
                .apply { if (next != null) addQueryParameter("nextToken", next) }
                .build()
            val page = json.decodeFromString(pageSerializer, get(url))
            out += page.records
            next = page.nextToken?.takeIf { it.isNotBlank() }
        } while (next != null && ++pages < MAX_PAGES)
        return out
    }

    /** GET with the access token; one retry with a fresh token after a 401. */
    private suspend fun get(url: HttpUrl): String {
        var stale: String? = null
        repeat(2) {
            val token = when (val t = tokens.get(staleToken = stale)) {
                is TokenOutcome.Ok -> t.accessToken
                TokenOutcome.NotConnected -> throw WhoopApiException.NotConnected()
                TokenOutcome.ReauthRequired -> throw WhoopApiException.ReauthRequired()
                TokenOutcome.NotAllowed -> throw WhoopApiException.NotAllowed()
                is TokenOutcome.Unavailable -> throw WhoopApiException.Unavailable(t.code)
            }
            val request = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", "application/json").build()
            http.newCall(request).await().use { res ->
                when {
                    res.isSuccessful -> return res.body.string()
                    res.code == 401 -> stale = token
                    res.code == 429 -> throw WhoopApiException.RateLimited()
                    else -> throw WhoopApiException.Http(res.code, url.encodedPath)
                }
            }
        }
        throw WhoopApiException.ReauthRequired()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.prod.whoop.com/"
        private const val MAX_PAGES = 40
    }
}
