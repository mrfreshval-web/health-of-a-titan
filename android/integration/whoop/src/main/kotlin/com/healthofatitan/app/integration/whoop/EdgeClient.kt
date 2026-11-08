package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.core.account.AccountTokens
import com.healthofatitan.app.core.account.DeviceProof
import com.healthofatitan.app.core.network.EdgeConfig
import com.healthofatitan.app.core.network.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE pair (RFC 7636, S256) made on the phone. Only the challenge goes to the Worker before
 * login; the verifier proves at `/whoop/token` that the code came back to this phone.
 */
data class Pkce(val verifier: String, val challenge: String) {
    companion object {
        private val b64 = Base64.getUrlEncoder().withoutPadding()

        fun create(random: SecureRandom = SecureRandom()): Pkce {
            val bytes = ByteArray(32).also(random::nextBytes)
            val verifier = b64.encodeToString(bytes) // 43 chars of [A-Za-z0-9_-]
            return Pkce(verifier, challengeOf(verifier))
        }

        fun challengeOf(verifier: String): String =
            b64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    }
}

/** What the Worker says the authorization request must look like (`POST /whoop/authorize`). */
@Serializable
data class AuthorizeConfig(
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("redirect_uri") val redirectUri: String,
    val scope: String,
    val state: String,
)

sealed interface TokenResult {
    /** «Войти через WHOOP» (A65 §2.2): an account session plus the first WHOOP access token. */
    data class Connected(val account: AccountTokens, val accessToken: String, val expiresAt: Long, val whoopUserId: String) : TokenResult

    /**
     * Logged in fine at WHOOP, but there is no HoT account for this WHOOP user and no (valid)
     * invite: nothing was stored, the grant was revoked (A65 §2.5).
     */
    data class NotAllowed(val whoopUserId: String, val inviteInvalid: Boolean = false) : TokenResult

    /** The code or state was rejected (expired login, retry from the start). */
    data object Rejected : TokenResult
}

sealed interface AccessResult {
    data class Token(val accessToken: String, val expiresAt: Long) : AccessResult

    /** WHOOP revoked the grant (or the legacy session is unknown): connect WHOOP again. */
    data object ReauthRequired : AccessResult

    /** The account access token was refused: refresh it and ask once more. */
    data object Unauthorized : AccessResult

    /** The account has no WHOOP identity (a Google-only account, A65 §2.3). */
    data object NotLinked : AccessResult

    data object NotAllowed : AccessResult

    /** Worker or WHOOP is down; keep the session and retry later. [code] goes to the sync log. */
    data class Unavailable(val code: Int) : AccessResult
}

/** The Worker is not configured (secrets missing) — a founder action, not a user error. */
class EdgeNotConfiguredException : IOException("edge not configured")

/**
 * Client of the WHOOP routes of the Worker hot-edge (A14 §5). Network failures surface as
 * [IOException] so callers can tell «no connection» from a refusal. Nothing is logged.
 */
class EdgeClient(
    private val config: EdgeConfig,
    private val http: OkHttpClient,
    private val json: Json,
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    @Serializable
    private data class AuthorizeBody(@SerialName("code_challenge") val codeChallenge: String)

    @Serializable
    private data class TokenBody(
        val code: String,
        val state: String,
        @SerialName("code_verifier") val codeVerifier: String,
        @SerialName("device_pub") val devicePub: String,
        @SerialName("device_sig") val deviceSig: String,
        @SerialName("device_name") val deviceName: String,
        val invite: String? = null,
    )

    @Serializable
    private data class TokenOk(
        @SerialName("whoop_access_token") val accessToken: String,
        @SerialName("whoop_expires_at") val expiresAt: Long,
        @SerialName("whoop_user_id") val whoopUserId: String,
    )

    @Serializable
    private data class ErrorBody(
        val error: String? = null,
        @SerialName("whoop_user_id") val whoopUserId: String? = null,
    )

    @Serializable
    private data class AccessOk(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_at") val expiresAt: Long,
    )

    private fun post(path: String, body: String?, bearer: String? = null): Request {
        if (!config.isConfigured) throw EdgeNotConfiguredException()
        return Request.Builder()
            .url(config.url(path))
            .post((body ?: "{}").toRequestBody(jsonType))
            .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
            .build()
    }

    private fun error(text: String): ErrorBody? = runCatching { json.decodeFromString(ErrorBody.serializer(), text) }.getOrNull()

    suspend fun authorize(challenge: String): AuthorizeConfig {
        val body = json.encodeToString(AuthorizeBody.serializer(), AuthorizeBody(challenge))
        http.newCall(post("/whoop/authorize", body)).await().use { res ->
            if (res.code == 503) throw EdgeNotConfiguredException()
            if (!res.isSuccessful) throw IOException("authorize ${res.code}")
            return json.decodeFromString(AuthorizeConfig.serializer(), res.body.string())
        }
    }

    /** [proof] is the device key's signature over [state] (A65 §3.3); [invite] only for a new account. */
    suspend fun token(code: String, state: String, verifier: String, proof: DeviceProof, invite: String? = null): TokenResult {
        val body = json.encodeToString(
            TokenBody.serializer(),
            TokenBody(code, state, verifier, proof.devicePub, proof.deviceSig, proof.deviceName, invite?.takeIf { it.isNotBlank() }),
        )
        http.newCall(post("/whoop/token", body)).await().use { res ->
            val text = res.body.string()
            return when {
                res.isSuccessful -> json.decodeFromString(TokenOk.serializer(), text).let {
                    TokenResult.Connected(json.decodeFromString(AccountTokens.serializer(), text), it.accessToken, it.expiresAt, it.whoopUserId)
                }
                res.code == 403 -> {
                    val e = error(text)
                    val id = e?.whoopUserId
                    if (id.isNullOrBlank()) throw IOException("token 403") else TokenResult.NotAllowed(id, inviteInvalid = e?.error == "invite_invalid")
                }
                res.code == 400 -> TokenResult.Rejected
                res.code == 503 -> throw EdgeNotConfiguredException()
                else -> throw IOException("token ${res.code}")
            }
        }
    }

    /** [bearer] is the account access token, or the legacy `hs1` session before migration. */
    suspend fun access(bearer: String): AccessResult {
        http.newCall(post("/whoop/access", null, bearer)).await().use { res ->
            val text = res.body.string()
            return when {
                res.isSuccessful -> json.decodeFromString(AccessOk.serializer(), text).let {
                    AccessResult.Token(it.accessToken, it.expiresAt)
                }
                res.code == 401 -> if (error(text)?.error == "reauth_required") AccessResult.ReauthRequired else AccessResult.Unauthorized
                res.code == 409 -> AccessResult.NotLinked
                res.code == 403 -> AccessResult.NotAllowed
                else -> AccessResult.Unavailable(res.code)
            }
        }
    }

    /** True when the Worker wiped the vault (or the session was already gone). */
    suspend fun disconnect(bearer: String): Boolean {
        http.newCall(post("/whoop/disconnect", null, bearer)).await().use { res ->
            return res.code == 204 || res.code == 401
        }
    }
}
