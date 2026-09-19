package com.tutorbridge.app.data.model

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.tutorbridge.app.R

object GoogleAuthentication {
    suspend fun idTokenRequest(context: Context, webClientId: String): String {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()

        val tokenIdRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = CredentialManager.create(context).getCredential(context, tokenIdRequest)
        val credential = result.credential

        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            return googleIdTokenCredential.idToken
        } else {
            Log.w(TAG, "Credential is not of type Google ID!")
            throw Exception("Credential is not of type Google ID!")
        }


    }

    /** Signing in only proves who the user is - writing their calendar is a
     * separate grant, which is why this scope is asked for on its own. */
    private const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.events"

    /**
     * Asks Google to authorise calendar writes and hands back the one-time
     * server auth code for POST /calendar/link. First time round Google has no
     * answer yet and returns a consent screen to show, so the result comes back
     * through an activity result rather than straight from the call.
     *
     * Returns the lambda that starts the flow.
     */
    @Composable
    fun rememberCalendarAuthorizer(
        onAuthCode: (String) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit {
        val context = LocalContext.current

        val consent = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            val code = runCatching {
                Identity.getAuthorizationClient(context)
                    .getAuthorizationResultFromIntent(result.data)
                    .serverAuthCode
            }.getOrNull()

            if (code != null) onAuthCode(code) else onError("Calendar access was not granted")
        }

        return {
            val request = AuthorizationRequest.Builder()
                //"email" rides along so the backend can name the account that
                //was linked - the user may well pick a different Google account
                //here than the one they signed in with.
                .setRequestedScopes(listOf(Scope(CALENDAR_SCOPE), Scope("email")))
                //The backend stores a refresh token, so insist on a code even
                //when this user has granted the scope before.
                .requestOfflineAccess(context.getString(R.string.google_web_client_id), true)
                .build()

            Identity.getAuthorizationClient(context)
                .authorize(request)
                .addOnSuccessListener { result ->
                    val pendingIntent = result.pendingIntent
                    if (result.hasResolution() && pendingIntent != null) {
                        consent.launch(IntentSenderRequest.Builder(pendingIntent).build())
                    } else {
                        val code = result.serverAuthCode
                        if (code != null) onAuthCode(code) else onError("Google returned no authorisation code")
                    }
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Calendar authorisation failed", error)
                    onError(error.message ?: "Could not reach Google")
                }
        }
    }
}