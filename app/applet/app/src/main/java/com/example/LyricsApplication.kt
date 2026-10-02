package com.example

import android.app.Application
import android.util.Log

class LyricsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("LyricsApplication", "LyricsApplication initialized successfully")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            System.gc()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        System.gc()
    }
}
