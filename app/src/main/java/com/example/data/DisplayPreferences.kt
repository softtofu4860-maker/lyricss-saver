package com.example.data

import android.content.Context
import android.content.SharedPreferences

/** The legacy file remains excluded from backup because it may still contain a plaintext key. */
object DisplayPreferences {
    const val FILE = "display_settings"
    const val DIM_DREAM = "dim_dream"
    private const val MIGRATED = "legacy_display_settings_migrated"
    private val keys = setOf("screensaver_theme", "layout_mode", "aura_theme", "font_scale",
        "lyric_font_scale", "controls_pinned", "performance_mode")

    fun get(context: Context): SharedPreferences = synchronized(this) {
        val app = context.applicationContext
        val target = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (!target.getBoolean(MIGRATED, false)) {
            val legacy = app.getSharedPreferences("screensaver_prefs", Context.MODE_PRIVATE)
            val edit = target.edit()
            for (key in keys) {
                if (target.contains(key)) continue
                when (val value = legacy.all[key]) {
                    is String -> edit.putString(key, value)
                    is Boolean -> edit.putBoolean(key, value)
                    is Float -> edit.putFloat(key, value)
                    is Int -> edit.putInt(key, value)
                    is Long -> edit.putLong(key, value)
                }
            }
            check(edit.putBoolean(MIGRATED, true).commit())
        }
        target
    }
}
