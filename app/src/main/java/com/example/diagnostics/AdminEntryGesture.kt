package com.example.diagnostics

import android.os.SystemClock

/** A discovery barrier only; successful gestures must still pass device authentication. */
internal class AdminEntryGesture(private val now: () -> Long = SystemClock::elapsedRealtime) {
    private var startedAt: Long? = null
    private var taps = 0
    private var armedAt: Long? = null

    fun tap() {
        val time = now()
        if (armedAt != null) reset()
        if (startedAt == null || time - startedAt!! !in 0..5000) {
            startedAt = time
            taps = 0
        }
        if (++taps == 7) armedAt = time
    }

    fun longPress(): Boolean {
        val accepted = armedAt?.let { now() - it in 0..3000 } == true
        reset()
        return accepted
    }

    fun reset() { startedAt = null; taps = 0; armedAt = null }
}
