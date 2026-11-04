package dev.reforge.core

import android.content.Context

/** Simple on-device settings store (SharedPreferences). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("reforge", Context.MODE_PRIVATE)

    var deepSeekKey: String
        get() = prefs.getString(KEY_DS, "") ?: ""
        set(v) = prefs.edit().putString(KEY_DS, v).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, "deepseek-chat") ?: "deepseek-chat"
        set(v) = prefs.edit().putString(KEY_MODEL, v).apply()

    companion object {
        private const val KEY_DS = "deepseek_key"
        private const val KEY_MODEL = "model"
    }
}
