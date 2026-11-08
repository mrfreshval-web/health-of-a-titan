package com.healthofatitan.app.core.account

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** What the Google sheet gave back. Name and picture stay on the phone (A65 §2.1, §2.4). */
sealed interface GoogleIdResult {
    data class Ok(val idToken: String, val displayName: String?, val pictureUri: String?) : GoogleIdResult

    data object Cancelled : GoogleIdResult

    /** No Google account on the phone, or Play services cannot offer one. */
    data object NoGoogleAccount : GoogleIdResult

    data object Failed : GoogleIdResult

    /** The build has no GOOGLE_WEB_CLIENT_ID: the button says so instead of failing. */
    data object NotConfigured : GoogleIdResult
}

/** The Credential Manager step, behind an interface so screens and tests can replace it. */
fun interface GoogleIdSource {
    /** [activityContext] must be an Activity: Credential Manager shows its sheet over it. */
    suspend fun idToken(activityContext: Context, nonce: String): GoogleIdResult
}

/**
 * «Войти через Google» with Credential Manager (A65 §2.1 step 2): the «Sign in with Google» button
 * flow, `serverClientId` = our Web client id (the `aud` the Worker checks), `nonce` from the
 * Worker. Only `openid profile`: the e-mail is never sent to the server.
 */
class CredentialManagerGoogleIdSource(private val config: AccountConfig) : GoogleIdSource {

    override suspend fun idToken(activityContext: Context, nonce: String): GoogleIdResult {
        if (!config.googleConfigured) return GoogleIdResult.NotConfigured
        val option = GetSignInWithGoogleOption.Builder(config.googleServerClientId)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleIdResult.Ok(google.idToken, google.displayName, google.profilePictureUri?.toString())
            } else {
                GoogleIdResult.Failed
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleIdResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleIdResult.NoGoogleAccount
        } catch (_: GetCredentialException) {
            GoogleIdResult.Failed
        } catch (_: GoogleIdTokenParsingException) {
            GoogleIdResult.Failed
        }
    }
}
