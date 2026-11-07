package com.healthofatitan.app.integration.whoop

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException

/** Steps of one sync, in order; [fraction] is where the progress ring stands when the step starts. */
enum class SyncStep(val fraction: Float) {
    START(0.04f),
    CYCLES(0.12f),
    RECOVERY(0.32f),
    SLEEP(0.52f),
    WORKOUTS(0.72f),
    COMPUTE(0.9f),
}

/** Who started a sync: the log shows it, and only syncs the user asked for end with a haptic tick. */
enum class SyncTrigger { MANUAL, LOGIN, APP_OPEN, BACKGROUND }

/**
 * Why a sync failed, as the UI and the sync log name it. [status] is what goes to `sync_state`
 * (S1, no health values); [retryable] failures are retried by WorkManager by themselves.
 */
enum class SyncFailure(val status: SyncStatus, val retryable: Boolean) {
    /** No network or the connection broke mid-way. */
    OFFLINE(SyncStatus.OFFLINE, true),

    /** The WHOOP grant is gone (session unknown to the Worker, WHOOP revoked it). */
    REAUTH(SyncStatus.REAUTH, false),

    /** The HoT server's allowlist (ALLOWED_WHOOP_USER_IDS) does not have this WHOOP user. */
    NOT_ALLOWED(SyncStatus.NOT_ALLOWED, false),

    /** WHOOP answered 403: the app lacks a scope or the account has no API access. */
    WHOOP_DENIED(SyncStatus.DENIED, false),

    /** WHOOP answered 429. */
    RATE_LIMITED(SyncStatus.RATE_LIMITED, true),

    /** WHOOP answered 5xx. */
    WHOOP_DOWN(SyncStatus.UNAVAILABLE, true),

    /** WHOOP answered another 4xx (e.g. 400, 404 on a path). */
    WHOOP_REJECTED(SyncStatus.ERROR, false),

    /** The HoT server (Worker) did not hand out a token or is not configured. */
    SERVER(SyncStatus.UNAVAILABLE, true),

    /** WHOOP's answer could not be read (format changed). */
    PARSE(SyncStatus.ERROR, false),

    /** Anything else; the log keeps the exception class. */
    UNKNOWN(SyncStatus.ERROR, false),
}

/** What came in with a successful sync (counts only — no health values). */
data class SyncCounts(val days: Int, val nights: Int, val workouts: Int, val baselineNights: Int)

/** Live state of WHOOP sync for the UI: the sync mark, the status line and the haptic tick. */
sealed interface SyncProgress {
    data object Idle : SyncProgress

    data class Running(val step: SyncStep, val startedAt: Long, val trigger: SyncTrigger) : SyncProgress

    data class Succeeded(val at: Long, val counts: SyncCounts, val trigger: SyncTrigger) : SyncProgress

    data class Failed(
        val at: Long,
        val failure: SyncFailure,
        val httpCode: Int? = null,
        val trigger: SyncTrigger,
    ) : SyncProgress {
        val willRetry: Boolean get() = failure.retryable
    }
}

/** A failure classified for the UI and the log. [detail] is a short technical hint (class, path), never data. */
data class ClassifiedFailure(val failure: SyncFailure, val httpCode: Int? = null, val detail: String? = null)

object SyncFailures {

    /**
     * Maps what a sync threw to a [ClassifiedFailure]; null means «not connected» (nothing to
     * sync, nothing to report). Cancellation is never swallowed.
     */
    fun classify(e: Throwable): ClassifiedFailure? = when (e) {
        is CancellationException -> throw e
        is WhoopApiException.NotConnected -> null
        is WhoopApiException.ReauthRequired -> ClassifiedFailure(SyncFailure.REAUTH, 401)
        is WhoopApiException.NotAllowed -> ClassifiedFailure(SyncFailure.NOT_ALLOWED, 403, "hot-edge")
        is WhoopApiException.RateLimited -> ClassifiedFailure(SyncFailure.RATE_LIMITED, 429)
        is WhoopApiException.Unavailable -> ClassifiedFailure(SyncFailure.SERVER, e.code, "hot-edge")
        is WhoopApiException.Http -> ClassifiedFailure(
            failure = when (e.code) {
                401 -> SyncFailure.REAUTH
                403 -> SyncFailure.WHOOP_DENIED
                429 -> SyncFailure.RATE_LIMITED
                in 500..599 -> SyncFailure.WHOOP_DOWN
                else -> SyncFailure.WHOOP_REJECTED
            },
            httpCode = e.code,
            detail = e.path.removePrefix("/developer"),
        )
        is EdgeNotConfiguredException -> ClassifiedFailure(SyncFailure.SERVER, null, "edge not configured")
        is SerializationException, is IllegalArgumentException -> ClassifiedFailure(SyncFailure.PARSE, null, e.javaClass.simpleName)
        is IOException -> ClassifiedFailure(SyncFailure.OFFLINE, null, e.javaClass.simpleName)
        else -> ClassifiedFailure(SyncFailure.UNKNOWN, null, e.javaClass.simpleName)
    }
}
