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
)

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
    companion object {
        fun of(tokens: AccountTokens) = AccountCredentials(
            refresh = tokens.refresh,
            accountId = tokens.accountId,
            providers = tokens.identities.map { it.provider }.distinct(),
            whoopUserId = tokens.identities.firstOrNull { it.provider == PROVIDER_WHOOP }?.subject,
        )

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
