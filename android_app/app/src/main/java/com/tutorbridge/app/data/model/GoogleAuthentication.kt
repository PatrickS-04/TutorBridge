package com.tutorbridge.app.data.model

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.ClearCredentialStateRequest
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
import androidx.credentials.exceptions.ClearCredentialException
import kotlinx.coroutines.withTimeoutOrNull

object GoogleAuthentication {
    // Reference: Android Developers. 2026. About Sign in with Google. [Online].
    //   Available at: https://developer.android.com/identity/sign-in/credential-manager-siwg [Accessed 21 September 2026].
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

    /** Forgets the Google account chosen at sign-in, so the next sign-in offers the choice again. Never blocks signing out. */
    suspend fun clearCredentials(context: Context) {
        try {
            withTimeoutOrNull(3_000) { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        } catch (error: ClearCredentialException) {
            Log.w(TAG, "Could not clear the credential state", error)
        }
    }

    /** Signing in only proves who the user is - writing their calendar is a
     * separate grant, which is why this scope is asked for on its own. */
    private const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.events"

    /**
     * Links a Google Calendar and hands back the one-time server auth code for
     * POST /calendar/link.
     *
     * Step one is the system account chooser. Left to itself Play Services
     * picks a device account and then silently reuses that grant on every later
     * attempt, so a user with two Google accounts can never move the link to
     * the other one. Signing in cannot answer this either: the TutorBridge
     * login is often a password account Google has never heard of.
     *
     * Step two authorises calendar writes for the account they chose. The first
     * time there is no answer yet and Google returns a consent screen to show,
     * so the code comes back through an activity result.
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

            if (code != null) onAuthCode(code) else onError(context.getString(R.string.calendar_not_granted))
        }

        // Reference: Android Developers. 2025. Authorize access to Google user data. [Online].
        //   Available at: https://developer.android.com/identity/authorization [Accessed 21 September 2026].
        val authorize = { account: Account ->
            val request = AuthorizationRequest.Builder()
                //"email" rides along so the backend can name the account that
                //was linked.
                .setRequestedScopes(listOf(Scope(CALENDAR_SCOPE), Scope("email")))
                //The backend stores a refresh token, so insist on a code even
                //when this user has granted the scope before.
                .requestOfflineAccess(context.getString(R.string.google_web_client_id), true)
                //Naming the account is what makes the choice above stick, and
                //what forces a fresh consent when it is not the one Google
                //already has a grant for.
                .setAccount(account)
                .build()

            Identity.getAuthorizationClient(context)
                .authorize(request)
                .addOnSuccessListener { result ->
                    val pendingIntent = result.pendingIntent
                    if (result.hasResolution() && pendingIntent != null) {
                        consent.launch(IntentSenderRequest.Builder(pendingIntent).build())
                    } else {
                        val code = result.serverAuthCode
                        if (code != null) onAuthCode(code) else onError(context.getString(R.string.calendar_no_code))
                    }
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Calendar authorisation failed", error)
                    onError(context.getString(R.string.calendar_unreachable))
                }
            Unit
        }

        val chooser = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val name = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            //The chooser offers "Add account" when the one they want is not on
            //the phone yet, so coming back empty is a real cancel.
            if (name != null) authorize(Account(name, "com.google"))
            else onError(context.getString(R.string.calendar_no_account))
        }

        return {
            chooser.launch(
                AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null)
            )
        }
    }
}