package com.example

import android.app.Application
import android.util.Log
import kotlinx.coroutines.launch

class LyricsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.example.diagnostics.LyricsDiagnostics.initialize(this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            try { com.example.data.PersonalApiKeyStore(this@LyricsApplication).get() }
            catch (error: Exception) {
                com.example.diagnostics.LyricsDiagnostics.record(com.example.diagnostics.DiagnosticStage.FAILURE,
                    "key_migration", fields = mapOf("error" to error.javaClass.simpleName))
            }
        }
        com.example.diagnostics.LyricsDiagnostics.record(com.example.diagnostics.DiagnosticStage.APP_START,
            fields = mapOf("sdk" to android.os.Build.VERSION.SDK_INT))
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
