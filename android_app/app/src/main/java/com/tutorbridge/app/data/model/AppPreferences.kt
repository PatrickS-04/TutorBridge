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

    private companion object {
        const val LAST_ROLE_KEY = "last_role"
        const val BIOMETRIC_ENABLED_KEY = "biometric_enabled"
    }
}