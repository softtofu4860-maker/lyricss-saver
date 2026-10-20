package com.example.data

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import java.io.Closeable

data class RenderProfile(
    val effectFps: Int,
    val particleCount: Int,
    val barCount: Int,
    val waveCount: Int,
    val waveStepPx: Int,
    val artworkMaxEdge: Int,
    val ambientMaxEdge: Int,
    val progressIntervalMs: Long,
    val richEffects: Boolean
)

enum class PerformanceMode(val label: String, val description: String, val profile: RenderProfile) {
    LOW("저사양", "효과 최대 30fps · 가벼운 배경과 효과 · 발열·배터리 부담 감소",
        RenderProfile(30, 8, 18, 1, 36, 512, 96, 250L, false)),
    HIGH("고사양", "효과 최대 60fps · 풍부한 배경과 효과 · 부드러운 움직임",
        RenderProfile(60, 18, 36, 3, 18, 1024, 256, 100L, true));

    companion object {
        fun defaultForDevice(lowRam: Boolean, totalMemoryBytes: Long): PerformanceMode =
            if (lowRam || totalMemoryBytes in 1..(3L * 1024 * 1024 * 1024)) LOW else HIGH

        fun fromStored(value: String?, fallback: PerformanceMode): PerformanceMode =
            entries.firstOrNull { it.name == value } ?: fallback
    }
}

class PerformanceSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("screensaver_prefs", Context.MODE_PRIVATE)
    private val fallback: PerformanceMode by lazy {
        val manager = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        PerformanceMode.defaultForDevice(manager.isLowRamDevice, memory.totalMem)
    }

    fun mode(): PerformanceMode = PerformanceMode.fromStored(
        runCatching { preferences.getString(KEY, null) }.getOrNull(), fallback)

    fun setMode(mode: PerformanceMode) {
        preferences.edit().putString(KEY, mode.name).apply()
    }

    fun observe(onChange: (PerformanceMode) -> Unit): Closeable {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY || key == null) onChange(mode())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onChange(mode())
        return Closeable { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object { const val KEY = "performance_mode" }
}
