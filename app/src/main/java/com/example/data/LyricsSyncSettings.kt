package com.example.data

import android.content.Context
import android.content.SharedPreferences
import java.io.Closeable

class LyricsSyncSettings(context: Context, title: String, artist: String, songId: String = SongIdentity.id(title, artist)) {
    private val preferences = context.applicationContext.getSharedPreferences("lyrics_sync", Context.MODE_PRIVATE)
    private val key = songId
    init {
        val legacy = SongIdentity.id(title, artist)
        synchronized(LyricsSyncSettings::class.java) {
            if (key != legacy && !preferences.contains(key) && preferences.contains(legacy) &&
                preferences.getString("binding_$legacy", null).let { it == null || it == key }) {
                check(preferences.edit().putLong(key, preferences.getLong(legacy, 0)).putString("binding_$legacy", key).commit())
            }
        }
    }
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
