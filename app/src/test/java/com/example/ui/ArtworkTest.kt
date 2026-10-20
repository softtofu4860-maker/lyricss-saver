package com.example.ui

import android.graphics.Bitmap
import com.example.service.MediaStateHolder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArtworkTest {
    @Test fun `resizing preserves aspect ratio and bounds both modes without recycling shared source`() {
        val source = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888)
        val low = boundedArtwork(source, 512)!!
        val high = boundedArtwork(source, 1024)!!
        assertEquals(512, low.width)
        assertEquals(256, low.height)
        assertEquals(1024, high.width)
        assertEquals(512, high.height)
        assertFalse(source.isRecycled)
        assertSame(low, boundedArtwork(low, 512))
        source.recycle()
        assertNull(boundedArtwork(source, 512))
    }

    @Test fun `very narrow artwork never produces zero sized bitmap`() {
        val source = Bitmap.createBitmap(1, 4000, Bitmap.Config.ARGB_8888)
        val result = boundedArtwork(source, 96)!!
        assertEquals(1, result.width)
        assertEquals(96, result.height)
    }

    @Test fun `media changes never recycle bitmaps still owned by the renderer`() {
        val previous = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val next = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        MediaStateHolder.updateState("old", "artist", null, false, 0, 1000, "player", albumArt = previous)
        MediaStateHolder.updateState("new", "artist", null, false, 0, 1000, "player", albumArt = next)
        assertFalse(previous.isRecycled)
        MediaStateHolder.clear()
        assertFalse(next.isRecycled)
    }
}
