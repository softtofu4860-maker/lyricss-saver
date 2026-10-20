package com.example.diagnostics

import org.junit.Assert.*
import org.junit.Test

class AdminEntryGestureTest {
    @Test fun `seven taps followed by a long press unlock only one authentication attempt`() {
        var now = 0L
        val gesture = AdminEntryGesture { now }
        repeat(7) { gesture.tap(); now += 200 }
        assertTrue(gesture.longPress())
        assertFalse(gesture.longPress())
    }

    @Test fun `slow taps and late long presses cannot expose the entry`() {
        var now = 0L
        val gesture = AdminEntryGesture { now }
        repeat(7) { gesture.tap(); now += 1000 }
        assertFalse(gesture.longPress())
        repeat(7) { gesture.tap() }
        now += 3001
        assertFalse(gesture.longPress())
    }

    @Test fun `incomplete gesture and background reset erase progress`() {
        val gesture = AdminEntryGesture { 0L }
        repeat(6) { gesture.tap() }
        assertFalse(gesture.longPress())
        gesture.tap()
        assertFalse(gesture.longPress())
        repeat(7) { gesture.tap() }
        gesture.reset()
        assertFalse(gesture.longPress())
    }

    @Test fun `extra taps require starting the sequence again`() {
        val gesture = AdminEntryGesture { 0L }
        repeat(8) { gesture.tap() }
        assertFalse(gesture.longPress())
    }
}
