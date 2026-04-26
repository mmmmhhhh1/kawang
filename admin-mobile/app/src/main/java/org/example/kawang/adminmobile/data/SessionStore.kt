package org.example.kawang.adminmobile.data

import android.content.Context

class SessionStore(context: Context) {

    private val prefs = context.getSharedPreferences("kawang_admin_mobile", Context.MODE_PRIVATE)

    fun getBaseUrl(): String = prefs.getString("base_url", "") ?: ""

    fun getToken(): String = prefs.getString("token", "") ?: ""

    fun getDisplayName(): String = prefs.getString("display_name", "") ?: ""

    fun saveSession(baseUrl: String, response: AdminLoginResponse) {
        prefs.edit()
            .putString("base_url", normalizeBaseUrl(baseUrl))
            .putString("token", response.token)
            .putString("display_name", response.profile.displayName)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun normalizeBaseUrl(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
