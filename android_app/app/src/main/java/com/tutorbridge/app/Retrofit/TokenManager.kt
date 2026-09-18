package com.tutorbridge.app.Retrofit

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores TutorBridge's own backend access/refresh tokens (NOT Firebase
 * tokens - the backend is the authoritative session per the design doc).
 * Backed by EncryptedSharedPreferences so the tokens are never written to
 * plain SharedPreferences, logs, or the Room DB.
 */
class TokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "tutorbridge_auth_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    @Synchronized
    fun saveTokens(accessToken: String, refreshToken: String, expiresInSeconds: Int) {
        val expiresAtMillis = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAtMillis)
            .apply()
    }

    @Synchronized
    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    @Synchronized
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    /** True once a refresh token has been stored - used to resume a session on cold start. */
    fun hasSession(): Boolean = getRefreshToken() != null

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
