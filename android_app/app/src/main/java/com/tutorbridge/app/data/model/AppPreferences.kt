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

    private companion object {
        const val LAST_ROLE_KEY = "last_role"
    }
}