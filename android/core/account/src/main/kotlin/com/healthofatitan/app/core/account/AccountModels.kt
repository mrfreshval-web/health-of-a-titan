package com.healthofatitan.app.core.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Build settings of the account (A65 §8). The Web client id is not a secret; blank = Google off. */
data class AccountConfig(val googleServerClientId: String) {
    val googleConfigured: Boolean get() = googleServerClientId.isNotBlank()
}

/** One way to sign in to the account: Google `sub` or WHOOP `user_id` (A65 §2). Never an e-mail. */
@Serializable
data class IdentityDto(
    val provider: String,
    val subject: String,
    @SerialName("linked_at") val linkedAt: Long = 0,
    @SerialName("last_login_at") val lastLoginAt: Long = 0,
    /** «Привязан Google · 08.11 13:40 · Pixel 9 Pro XL» (A65 §2.3); null for the first identity. */
    @SerialName("linked_device") val linkedDevice: String? = null,
)

/** One signed-in device of the account (A65 §3.4). [id] is derived from the session, not the session id. */
@Serializable
data class DeviceDto(
    val id: String,
    val name: String,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("last_used_at") val lastUsedAt: Long = 0,
    val current: Boolean = false,
)

/** «Я › Аккаунт» (A65 §2.3, §3.4): the ways in, the devices and until when this session counts as fresh. */
@Serializable
data class AccountView(
    @SerialName("account_id") val accountId: String,
    val identities: List<IdentityDto> = emptyList(),
    val devices: List<DeviceDto> = emptyList(),
    /** Linking, unlinking and deletion need a proof of the person until this time (step-up, 10 min). */
    @SerialName("step_up_until") val stepUpUntil: Long = 0,
) {
    fun has(provider: String): Boolean = identities.any { it.provider == provider }
}

/** What the Worker answers on every sign-in and refresh (A65 §2.1 step 6). */
@Serializable
data class AccountTokens(
    val access: String,
    @SerialName("access_expires_at") val accessExpiresAt: Long,
    val refresh: String,
    @SerialName("account_id") val accountId: String,
    val identities: List<IdentityDto> = emptyList(),
)

/** Proof that the request comes from this phone's Keystore key (A65 §3.3). */
data class DeviceProof(val devicePub: String, val deviceSig: String, val deviceName: String)

/**
 * The account on the phone (class SEC, A14 §3.1): only the refresh token and what the screens
 * need to know. The access token is never stored. No name, e-mail or picture here.
 */
@Serializable
data class AccountCredentials(
    val refresh: String,
    val accountId: String,
    val providers: List<String> = emptyList(),
    val whoopUserId: String? = null,
) {
    /** The same session with the identities the Worker answered after a link or an unlink. */
    fun withIdentities(identities: List<IdentityDto>) = copy(
        providers = identities.map { it.provider }.distinct(),
        whoopUserId = identities.firstOrNull { it.provider == PROVIDER_WHOOP }?.subject,
    )

    companion object {
        fun of(tokens: AccountTokens) = AccountCredentials(
            refresh = tokens.refresh,
            accountId = tokens.accountId,
        ).withIdentities(tokens.identities)

        const val PROVIDER_GOOGLE = "google"
        const val PROVIDER_WHOOP = "whoop"
    }
}

/**
 * Whether the app must show «Вход» now (after the gate 18+):
 *  - [NOT_REQUIRED]: a build without the Worker (no EDGE_BASE_URL) stays local-first, no login;
 *  - [MIGRATING]: the phone still holds the old WHOOP session `hs1` and turns it into an account
 *    by itself (A65 §3.5) — no login screen for it.
 */
enum class AccountGate {
    LOADING,
    SIGNED_OUT,
    SIGNED_IN,
    MIGRATING,
    NOT_REQUIRED,
    ;

    /** Only [SIGNED_OUT] puts «Вход» over the app. */
    val showsSignIn: Boolean get() = this == SIGNED_OUT

    companion object {
        fun of(edgeConfigured: Boolean, signedIn: Boolean, legacyWhoopSession: Boolean): AccountGate = when {
            !edgeConfigured -> NOT_REQUIRED
            signedIn -> SIGNED_IN
            legacyWhoopSession -> MIGRATING
            else -> SIGNED_OUT
        }
    }
}
