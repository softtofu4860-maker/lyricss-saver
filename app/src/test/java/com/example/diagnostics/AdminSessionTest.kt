package com.example.diagnostics

import org.junit.Assert.*
import org.junit.Test

class AdminSessionTest {
    @Test fun `only successful device authentication on a secured device unlocks logs`() {
        val session = AdminSession { 0L }
        assertFalse(session.isUnlocked())
        session.completeDeviceAuthentication(false, true)
        assertFalse(session.isUnlocked())
        session.completeDeviceAuthentication(true, false)
        assertFalse(session.isUnlocked())
        session.completeDeviceAuthentication(true, true)
        assertTrue(session.isUnlocked())
        session.lock()
        assertThrows(SecurityException::class.java) { session.requireUnlocked() }
    }

    @Test fun `authentication expires after five minutes and is never shared by a new session`() {
        var time = 100L
        val session = AdminSession { time }
        session.completeDeviceAuthentication(true, true)
        time += AdminSession.SESSION_MS - 1
        assertTrue(session.isUnlocked())
        time++
        assertFalse(session.isUnlocked())
        session.completeDeviceAuthentication(true, true)
        assertTrue(session.isUnlocked())
        assertFalse(AdminSession { time }.isUnlocked())
    }
}
