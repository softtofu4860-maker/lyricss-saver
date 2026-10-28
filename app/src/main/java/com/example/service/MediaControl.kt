package com.example.service

import android.content.Context
import android.media.session.MediaController
import android.widget.Toast
import com.example.diagnostics.DiagnosticStage
import com.example.diagnostics.LyricsDiagnostics
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

object MediaControl {
    private val lastFailureNotice = AtomicLong(-2000L)
    fun perform(context: Context, action: (MediaController.TransportControls) -> Unit): Boolean {
        return try {
            val controls = MusicNotificationListener.activeController?.transportControls
                ?: throw IllegalStateException("media session unavailable")
            action(controls)
            true
        } catch (error: Exception) {
            val now = SystemClock.elapsedRealtime()
            val previous = lastFailureNotice.get()
            if ((now < previous || now - previous >= 2000) && lastFailureNotice.compareAndSet(previous, now)) {
                LyricsDiagnostics.record(DiagnosticStage.FAILURE, "media_control", fields = mapOf("error" to error.javaClass.simpleName))
                Toast.makeText(context, "음악 앱을 제어하지 못했습니다. 알림 접근과 재생 상태를 확인해 주세요.", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }
}
