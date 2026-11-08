package com.healthofatitan.app.core.account

import com.healthofatitan.app.core.network.EdgeConfig
import com.healthofatitan.app.core.network.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** The Worker is not configured (EDGE_BASE_URL or its secrets missing): a founder action. */
class AccountNotConfiguredException : IOException("account not configured")

sealed interface GoogleLoginResult {
    data class Ok(val tokens: AccountTokens) : GoogleLoginResult

    /** Google is fine, but this person has no HoT account: an invite or «Войти через WHOOP». */
    data class NoAccount(val signupTicket: String) : GoogleLoginResult

    data class InviteInvalid(val signupTicket: String) : GoogleLoginResult

    data object Rejected : GoogleLoginResult
}

sealed interface SignupResult {
    data class Ok(val tokens: AccountTokens) : SignupResult

    data object InviteInvalid : SignupResult

    /** The account exists already: sign in with Google again. */
    data object AccountExists : SignupResult

    data object Rejected : SignupResult
}

sealed interface RefreshResult {
    data class Ok(val tokens: AccountTokens) : RefreshResult

    /** The session is gone (logout, «Выйти везде», reuse detected, expired): sign in again. */
    data object SignedOut : RefreshResult

    data class Unavailable(val code: Int) : RefreshResult
}

sealed interface MigrateResult {
    data class Ok(val tokens: AccountTokens, val whoopUserId: String) : MigrateResult

    /** The old `hs1` session is not valid any more: sign in again. */
    data object Rejected : MigrateResult

    data class Unavailable(val code: Int) : MigrateResult
}

/**
 * Client of the account routes of hot-edge (A65 §2–3). Network failures surface as [IOException].
 * Nothing is logged; bodies carry only tokens and the device proof.
 */
class AuthApi(
    private val config: EdgeConfig,
    private val http: OkHttpClient,
    private val json: Json,
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    @Serializable
    private data class NonceOk(val nonce: String)

    @Serializable
    private data class ErrorBody(
        val error: String? = null,
        @SerialName("signup_ticket") val signupTicket: String? = null,
    )

    @Serializable
    private data class MigrateOk(@SerialName("whoop_user_id") val whoopUserId: String)

    private fun request(path: String, body: JsonObject?, bearer: String? = null): Request {
        if (!config.isConfigured) throw AccountNotConfiguredException()
        return Request.Builder()
            .url(config.url(path))
            .post((body?.toString() ?: "{}").toRequestBody(jsonType))
            .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
            .build()
    }

    private fun JsonObject.withProof(proof: DeviceProof): JsonObject = JsonObject(
        this + mapOf(
            "device_pub" to JsonPrimitive(proof.devicePub),
            "device_sig" to JsonPrimitive(proof.deviceSig),
            "device_name" to JsonPrimitive(proof.deviceName),
        ),
    )

    private fun error(text: String): ErrorBody = runCatching { json.decodeFromString(ErrorBody.serializer(), text) }.getOrDefault(ErrorBody())

    private fun tokens(text: String): AccountTokens = json.decodeFromString(AccountTokens.serializer(), text)

    suspend fun nonce(): String {
        http.newCall(request("/auth/nonce", null)).await().use { res ->
            if (res.code == 503) throw AccountNotConfiguredException()
            if (!res.isSuccessful) throw IOException("nonce ${res.code}")
            return json.decodeFromString(NonceOk.serializer(), res.body.string()).nonce
        }
    }

    suspend fun google(idToken: String, proof: DeviceProof, invite: String?): GoogleLoginResult {
        val body = buildJsonObject {
            put("id_token", idToken)
            if (!invite.isNullOrBlank()) put("invite", invite)
        }.withProof(proof)
        http.newCall(request("/auth/google", body)).await().use { res ->
            val text = res.body.string()
            return when {
                res.isSuccessful -> GoogleLoginResult.Ok(tokens(text))
                res.code == 404 || res.code == 403 -> {
                    val e = error(text)
                    val ticket = e.signupTicket ?: return GoogleLoginResult.Rejected
                    if (e.error == "no_account") GoogleLoginResult.NoAccount(ticket) else GoogleLoginResult.InviteInvalid(ticket)
                }
                res.code == 503 -> throw AccountNotConfiguredException()
                res.code in 400..499 -> GoogleLoginResult.Rejected
                else -> throw IOException("google ${res.code}")
            }
        }
    }

    suspend fun signup(ticket: String, invite: String, proof: DeviceProof): SignupResult {
        val body = buildJsonObject {
            put("signup_ticket", ticket)
            put("invite", invite)
        }.withProof(proof)
        http.newCall(request("/auth/signup", body)).await().use { res ->
            val text = res.body.string()
            return when {
                res.isSuccessful -> SignupResult.Ok(tokens(text))
                res.code == 403 -> SignupResult.InviteInvalid
                res.code == 409 -> SignupResult.AccountExists
                res.code == 503 -> throw AccountNotConfiguredException()
                res.code in 400..499 -> SignupResult.Rejected
                else -> throw IOException("signup ${res.code}")
            }
        }
    }

    suspend fun refresh(refresh: String, ts: Long, sig: String): RefreshResult {
        val body = buildJsonObject {
            put("refresh", refresh)
            put("ts", ts)
            put("sig", sig)
        }
        http.newCall(request("/auth/refresh", body)).await().use { res ->
            val text = res.body.string()
            return when {
                res.isSuccessful -> RefreshResult.Ok(tokens(text))
                res.code == 401 -> RefreshResult.SignedOut
                else -> RefreshResult.Unavailable(res.code)
            }
        }
    }

    /** True when the session is gone on the server (or was gone already). */
    suspend fun logout(access: String): Boolean {
        http.newCall(request("/auth/logout", null, access)).await().use { res -> return res.code == 204 || res.code == 401 }
    }

    suspend fun logoutAll(access: String): Boolean {
        http.newCall(request("/auth/logout-all", null, access)).await().use { res -> return res.code == 204 }
    }

    suspend fun migrate(legacySession: String, nonce: String, proof: DeviceProof): MigrateResult {
        val body = buildJsonObject { put("nonce", nonce) }.withProof(proof)
        http.newCall(request("/auth/migrate", body, legacySession)).await().use { res ->
            val text = res.body.string()
            return when {
                res.isSuccessful -> MigrateResult.Ok(tokens(text), json.decodeFromString(MigrateOk.serializer(), text).whoopUserId)
                res.code == 401 -> MigrateResult.Rejected
                else -> MigrateResult.Unavailable(res.code)
            }
        }
    }
}
