package com.healthofatitan.app.integration.whoop

import android.content.Context
import android.content.Intent
import android.net.Uri
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

/** Result of the browser step of the login. */
sealed interface AuthRedirect {
    data class Code(val code: String, val state: String) : AuthRedirect

    data object Cancelled : AuthRedirect

    data object Failed : AuthRedirect
}

/**
 * AppAuth for the browser step (A14 ADR-03): Custom Tab → WHOOP login → redirect to
 * `com.healthofatitan.app://oauth/whoop` (AppAuth's RedirectUriReceiverActivity, scheme from the
 * `appAuthRedirectScheme` placeholder). AppAuth checks that the returned `state` is ours.
 *
 * The token exchange is **not** done by AppAuth: the code goes to the Worker, which holds
 * `client_secret` (A18 И1.1 ④). PKCE is done between the phone and the Worker (see the Worker's
 * oauth-state.ts), so AppAuth's own code_challenge to WHOOP is switched off.
 */
class WhoopAuthFlow(context: Context) {

    private val service = AuthorizationService(context)

    fun intent(config: AuthorizeConfig): Intent {
        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse(config.authorizationEndpoint),
            Uri.parse(TOKEN_ENDPOINT_UNUSED),
        )
        val request = AuthorizationRequest.Builder(serviceConfig, config.clientId, ResponseTypeValues.CODE, Uri.parse(config.redirectUri))
            .setScope(config.scope)
            .setState(config.state)
            .setCodeVerifier(null)
            .setNonce(null)
            .build()
        return service.getAuthorizationRequestIntent(request)
    }

    fun dispose() = service.dispose()

    companion object {
        /** AppAuth requires a token endpoint in the configuration; the phone never calls it. */
        private const val TOKEN_ENDPOINT_UNUSED = "https://api.prod.whoop.com/oauth/oauth2/token"

        fun parse(data: Intent?): AuthRedirect {
            if (data == null) return AuthRedirect.Cancelled
            val error = AuthorizationException.fromIntent(data)
            if (error != null) {
                return if (error == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW ||
                    error == AuthorizationException.GeneralErrors.PROGRAM_CANCELED_AUTH_FLOW
                ) {
                    AuthRedirect.Cancelled
                } else {
                    AuthRedirect.Failed
                }
            }
            val response = AuthorizationResponse.fromIntent(data) ?: return AuthRedirect.Cancelled
            val code = response.authorizationCode ?: return AuthRedirect.Failed
            val state = response.state ?: return AuthRedirect.Failed
            return AuthRedirect.Code(code, state)
        }
    }
}
