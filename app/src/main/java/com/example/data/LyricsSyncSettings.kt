package com.example.data

import android.content.Context
import android.content.SharedPreferences
import java.io.Closeable

class LyricsSyncSettings(context: Context, title: String, artist: String) {
    private val preferences = context.applicationContext.getSharedPreferences("lyrics_sync", Context.MODE_PRIVATE)
    private val key = SongIdentity.id(title, artist)
    fun get(): Long = preferences.getLong(key, 0L).coerceIn(-10000L, 10000L)
    fun set(offsetMs: Long) { preferences.edit().putLong(key, offsetMs.coerceIn(-10000L, 10000L)).apply() }
    fun observe(onChange: (Long) -> Unit): Closeable {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key || changed == null) onChange(get())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onChange(get())
        return Closeable { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
