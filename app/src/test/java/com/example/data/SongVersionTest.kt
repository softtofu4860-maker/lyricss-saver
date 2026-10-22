package com.example.data

import org.junit.Assert.*
import org.junit.Test

class SongVersionTest {
    @Test fun `provider id album and duration separate otherwise identical recordings`() {
        val studio = SongDescriptor("song", "artist", "studio", 180000)
        val live = SongDescriptor("song", "artist", "live", 240000)
        assertNotEquals(studio.id, live.id)
        assertNotEquals(studio.id, studio.copy(durationMs = 190000).id)
        val provider = studio.copy(provider = "player", providerId = "one")
        assertNotEquals(provider.id, provider.copy(providerId = "two").id)
        assertEquals(provider.id, provider.copy(durationMs = 190000, album = "later metadata").id)
        assertEquals(SongIdentity.id("song", "artist"), SongDescriptor("song", "artist").id)
    }
}
