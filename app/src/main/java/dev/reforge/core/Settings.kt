package dev.reforge.core

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("reforge", Context.MODE_PRIVATE)

    var deepSeekKey: String
        get() = prefs.getString(KEY_DS, "") ?: ""
        set(v) = prefs.edit().putString(KEY_DS, v).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, "deepseek-chat") ?: "deepseek-chat"
        set(v) = prefs.edit().putString(KEY_MODEL, v).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE, DEFAULT_BASE)?.ifBlank { DEFAULT_BASE } ?: DEFAULT_BASE
        set(v) = prefs.edit().putString(KEY_BASE, v).apply()

    var customHeaders: String
        get() = prefs.getString(KEY_HEADERS, "") ?: ""
        set(v) = prefs.edit().putString(KEY_HEADERS, v).apply()

    var extraBody: String
        get() = prefs.getString(KEY_EXTRA, "") ?: ""
        set(v) = prefs.edit().putString(KEY_EXTRA, v).apply()

    companion object {
        const val DEFAULT_BASE = "https://api.deepseek.com"
        private const val KEY_DS = "deepseek_key"
        private const val KEY_MODEL = "model"
        private const val KEY_BASE = "base_url"
        private const val KEY_HEADERS = "custom_headers"
        private const val KEY_EXTRA = "extra_body"
    }
}
