package com.example.diagnostics

import android.os.SystemClock

/** Process-memory only; never persisted or restored after activity recreation. */
internal class AdminSession(private val now: () -> Long = SystemClock::elapsedRealtime) {
    private var authenticatedAt: Long? = null

    @Synchronized
    fun completeDeviceAuthentication(success: Boolean, deviceSecure: Boolean) {
        authenticatedAt = if (success && deviceSecure) now() else null
    }

    @Synchronized
    fun lock() { authenticatedAt = null }

    @Synchronized
    fun isUnlocked(): Boolean = authenticatedAt?.let { now() - it in 0 until SESSION_MS } == true

    fun requireUnlocked() {
        checkAccess(isUnlocked())
    }

    private fun checkAccess(allowed: Boolean) {
        if (!allowed) throw SecurityException("관리자 인증이 필요합니다")
    }

    companion object { const val SESSION_MS = 5 * 60 * 1000L }
}
