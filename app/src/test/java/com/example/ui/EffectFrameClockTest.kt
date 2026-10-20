package com.example.ui

import org.junit.Assert.*
import org.junit.Test

class EffectFrameClockTest {
    @Test fun `caps effect updates on common refresh rates without slowing animation time`() {
        for (displayHz in listOf(60, 90, 120, 144)) {
            for (target in listOf(30, 60)) {
                val clock = EffectFrameClock(target)
                var frames = 0
                var elapsed = 0f
                for (i in 0..displayHz) {
                    val step = clock.step(i * 1_000_000_000L / displayHz)
                    if (step > 0) frames++
                    elapsed += step
                }
                assertTrue("$displayHz Hz / $target fps produced $frames updates", frames in (target - 1)..target)
                assertEquals(1f, elapsed, 0.035f)
            }
        }
    }

    @Test fun `long frame stalls are clamped instead of jumping animation forward`() {
        val clock = EffectFrameClock(60)
        assertEquals(0f, clock.step(0L), 0f)
        assertEquals(0.1f, clock.step(10_000_000_000L), 0f)
        assertEquals(0f, clock.step(10_000_000_001L), 0f)
    }
}
