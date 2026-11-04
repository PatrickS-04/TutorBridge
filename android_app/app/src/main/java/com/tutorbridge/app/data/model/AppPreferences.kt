package com.tutorbridge.app.data.model

import android.content.Context

// Reference: Android Developers. 2026. Save simple data with SharedPreferences. [Online].
//   Available at: https://developer.android.com/training/data-storage/shared-preferences [Accessed 21 September 2026].
class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("tutorbridge_preferences", Context.MODE_PRIVATE)

    fun saveLastRole(role: UserRole) {
        preferences.edit().putString(LAST_ROLE_KEY, role.name).apply()
    }

    fun getLastRole(): UserRole? = preferences.getString(
        LAST_ROLE_KEY, null)?.let { runCatching {
            UserRole.valueOf(it) }.getOrNull() }

    /** Everything of this user's goes; whether this phone has been asked about biometrics stays, or the question would return at every sign-in. */
    fun clear() {
        val offered = getBiometricOffered()
        preferences.edit().clear().putBoolean(BIOMETRIC_OFFERED_KEY, offered).apply()
    }

    fun markBiometricOffered() {
        preferences.edit().putBoolean(BIOMETRIC_OFFERED_KEY, true).apply()
    }

    fun getBiometricOffered(): Boolean = preferences.getBoolean(BIOMETRIC_OFFERED_KEY, false)

    fun enableBiometricSaved(enabled: Boolean) {
        preferences.edit().putBoolean(BIOMETRIC_ENABLED_KEY, enabled).apply()
    }

    fun getEnabledBiometric(): Boolean = preferences.getBoolean(BIOMETRIC_ENABLED_KEY, false)

    fun enableMessageNotificationsSaved(enabled: Boolean) {
        preferences.edit().putBoolean(SESSION_REMINDERS_KEY, enabled).apply()
    }

    fun getEnabledMessageNotifications(): Boolean = preferences.getBoolean(SESSION_REMINDERS_KEY, false)

    fun enableBookingUpdatesSaved(enabled: Boolean) {
        preferences.edit().putBoolean(BOOKING_UPDATES_KEY, enabled).apply()
    }

    fun getEnabledBookingUpdates(): Boolean = preferences.getBoolean(BOOKING_UPDATES_KEY, false)

    private companion object {
        const val LAST_ROLE_KEY = "last_role"
        const val BIOMETRIC_ENABLED_KEY = "biometric_enabled"
        const val BIOMETRIC_OFFERED_KEY = "biometric_offered"
        const val SESSION_REMINDERS_KEY = "session_reminders_enabled"
        const val BOOKING_UPDATES_KEY = "booking_updates_enabled"
    }
}