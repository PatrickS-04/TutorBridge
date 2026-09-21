package com.tutorbridge.app.data.model

import android.content.Context

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("tutorbridge_preferences", Context.MODE_PRIVATE)

    fun saveLastRole(role: UserRole) {
        preferences.edit().putString(LAST_ROLE_KEY, role.name).apply()
    }

    fun getLastRole(): UserRole? = preferences.getString(
        LAST_ROLE_KEY, null)?.let { runCatching {
            UserRole.valueOf(it) }.getOrNull() }

    fun clear() {
        preferences.edit().clear().apply()
    }

    fun enableBiometricSaved(enabled: Boolean) {
        preferences.edit().putBoolean(BIOMETRIC_ENABLED_KEY, enabled).apply()
    }

    fun getEnabledBiometric(): Boolean = preferences.getBoolean(BIOMETRIC_ENABLED_KEY, false)

    fun enableSMSFallbackSaved(enabled: Boolean) {
        preferences.edit().putBoolean(SMS_FALLBACK_KEY, enabled).apply()
    }

    fun getEnabledSMSFallback(): Boolean = preferences.getBoolean(SMS_FALLBACK_KEY, false)

    fun enableSessionRemindersSaved(enabled: Boolean) {
        preferences.edit().putBoolean(SESSION_REMINDERS_KEY, enabled).apply()
    }

    fun getEnabledSessionReminders(): Boolean = preferences.getBoolean(SESSION_REMINDERS_KEY, false)

    fun enableBookingUpdatesSaved(enabled: Boolean) {
        preferences.edit().putBoolean(BOOKING_UPDATES_KEY, enabled).apply()
    }

    fun getEnabledBookingUpdates(): Boolean = preferences.getBoolean(BOOKING_UPDATES_KEY, false)

    private companion object {
        const val LAST_ROLE_KEY = "last_role"
        const val BIOMETRIC_ENABLED_KEY = "biometric_enabled"
        const val SMS_FALLBACK_KEY = "sms_fallback_enabled"
        const val SESSION_REMINDERS_KEY = "session_reminders_enabled"
        const val BOOKING_UPDATES_KEY = "booking_updates_enabled"
    }
}