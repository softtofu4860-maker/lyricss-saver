package com.example

import com.example.api.*
import com.example.data.LyricsIdentity
import org.junit.Assert.*
import org.junit.Test

class LyricsAccuracyTest {
    private val query = SongQuery("A Beautiful Song", "Artist", durationMs = 180000)
    private fun candidate(provider: String = "lrclib", id: String = "1", title: String = query.title,
        artists: List<String> = listOf("Artist"), album: String? = null,
        duration: Long? = 180000, synced: Boolean = true, boundId: String? = null) =
        LyricsCandidate(provider, id, title, artists, album, duration,
            if (synced) "[00:01.00]One\n[00:02.00]Two" else "One\nTwo",
            synced = synced, boundTrackId = boundId)

    @Test fun syncedCandidateBeatsEarlierPlainCandidate() {
        val plain = candidate(provider = "spotify", synced = false)
        val synced = candidate(provider = "lrclib", duration = 181000)
        assertEquals("lrclib", LyricsMatching.choose(query, listOf(plain, synced))!!.candidate.provider)
    }
    @Test fun wrongSongWithTimestampsNeverBeatsCorrectPlainLyrics() {
        assertFalse(LyricsMatching.choose(query, listOf(candidate(title = "Another Song"), candidate(synced = false)))!!.candidate.synced)
    }
    @Test fun plainLyricsRemainAvailableWhenNoTimedCandidateMatches() {
        assertNotNull(LyricsMatching.choose(query, listOf(candidate(synced = false))))
    }
    @Test fun claimedSyncWithoutTimestampsIsRejected() {
        val invalid = candidate().copy(rawLyrics = "Not actually timed")
        assertNull(LyricsMatching.choose(query, listOf(invalid)))
    }
    @Test fun malformedOutOfRangeSyncIsRejected() {
        assertNull(LyricsMatching.choose(query, listOf(candidate().copy(rawLyrics = "[04:00.00]Too late"))))
    }
    @Test fun exactSpotifyIdBindsLyricsToCurrentTrack() {
        val id = "4Q0qVhFQa7j6jRKzo3HDmP"
        assertEquals(100.0, LyricsMatching.score(query.copy(trackId = "spotify:track:$id"),
            candidate(provider = "spotify", boundId = id))!!, 0.0)
    }
    @Test fun differentSpotifyTrackCannotMatchEvenWhenNamesMatch() {
        assertNull(LyricsMatching.score(query.copy(trackId = "4Q0qVhFQa7j6jRKzo3HDmP"),
            candidate(boundId = "5Q0qVhFQa7j6jRKzo3HDmP")))
    }
    @Test fun unrequestedSpotifyIdCannotBypassMetadataChecks() {
        assertNull(LyricsMatching.score(query, candidate(boundId = "4Q0qVhFQa7j6jRKzo3HDmP")))
    }
    @Test fun collaborationArtistsCanUseDifferentSeparators() {
        assertNotNull(LyricsMatching.choose(query.copy(artist = "Artist & Guest"),
            listOf(candidate(artists = listOf("Artist", "Guest")))))
    }
    @Test fun missingCollaboratorIsRejected() {
        assertNull(LyricsMatching.choose(query.copy(artist = "Artist & Guest"), listOf(candidate())))
    }
    @Test fun featuringTitleCanMatchWhenGuestAppearsInArtists() {
        val q = query.copy(title = "A Beautiful Song (feat. Guest)")
        assertNotNull(LyricsMatching.choose(q, listOf(candidate(artists = listOf("Artist", "Guest")))))
    }
    @Test fun minorLongTitleTypoCanMatchWithCorrectArtistAndDuration() {
        assertNotNull(LyricsMatching.choose(query, listOf(candidate(title = "A Beautifull Song"))))
    }
    @Test fun shortTitleTypoDoesNotMatchAnotherSong() {
        assertNull(LyricsMatching.choose(query.copy(title = "Love"), listOf(candidate(title = "Lose"))))
    }
    @Test fun remixNamesRemainDistinct() {
        assertNull(LyricsMatching.choose(query.copy(title = "A Beautiful Song (Alpha Remix)"),
            listOf(candidate(title = "A Beautiful Song (Beta Remix)"))))
    }
    @Test fun durationUnknownIsNotEnoughWhenCurrentDurationIsKnown() {
        assertNull(LyricsMatching.choose(query, listOf(candidate(duration = null))))
    }
    @Test fun ambiguousSameProviderRecordingsRequireManualSelection() {
        assertNull(LyricsMatching.choose(query,
            listOf(candidate(id = "1", album = "First"), candidate(id = "2", album = "Second"))))
    }
    @Test fun albumMetadataResolvesAnOtherwiseAmbiguousRecording() {
        val selected = LyricsMatching.choose(query.copy(album = "Second"),
            listOf(candidate(id = "1", album = "First"), candidate(id = "2", album = "Second")))!!
        assertEquals("2", selected.candidate.providerId)
    }
    @Test fun lrcRepeatingLineWithMultipleTimestampsIsExpanded() {
        val lines = LrcParser.parseSynced("[00:10.00][00:30.00]Chorus")
        assertEquals(listOf(10f, 30f), lines.map { it.timeSec })
        assertTrue(lines.all { it.text == "Chorus" })
    }
    @Test fun fastAdjacentLinesAreNeverConvertedIntoTranslations() {
        val lines = LrcParser.parse("[00:01.100]One\n[00:01.200]Two")
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.translation == null })
    }
    @Test fun plainLyricsNeverGetInventedTimings() {
        val lines = LrcParser.parse("One\nTwo")
        assertEquals(listOf(0f, 0f), lines.map { it.timeSec })
        assertFalse(LrcParser.hasValidSync("One\nTwo"))
    }
    @Test fun invalidSecondsAreRejected() {
        assertFalse(LrcParser.hasValidSync("[00:99.00]Bad"))
    }
    @Test fun spotifyUrisAndUrlsHaveOneRecordingIdentity() {
        val id = "4Q0qVhFQa7j6jRKzo3HDmP"
        assertEquals(LyricsIdentity.recordingId("A", "B", id),
            LyricsIdentity.recordingId("Changed label", "B", "https://open.spotify.com/track/$id?si=test"))
    }
    @Test fun differentRecordingsHaveDifferentCacheIdentities() {
        assertNotEquals(LyricsIdentity.recordingId("Song", "Artist", album = "Studio", durationMs = 180000),
            LyricsIdentity.recordingId("Song", "Artist", album = "Live", durationMs = 190000))
    }
    @Test fun nonTrackSpotifyUrisAreNotAccepted() {
        assertNull(LyricsIdentity.spotifyTrackId("spotify:episode:4Q0qVhFQa7j6jRKzo3HDmP"))
        assertNull(LyricsIdentity.spotifyTrackId("https://evil.example/track/4Q0qVhFQa7j6jRKzo3HDmP"))
    }
    @Test fun studioSongTitleContainingLiveIsNotALiveEdition() {
        assertNull(LyricsMatching.choose(query.copy(title = "Live Forever"),
            listOf(candidate(title = "Live Forever (Live)"))))
    }
    @Test fun uniformZeroTimestampsAreNotRealSync() {
        assertFalse(LrcParser.hasValidSync("[00:00.00]One\n[00:00.00]Two"))
    }
    @Test fun fortyVersionFixturesRejectAllWrongEditions() {
        // Synthetic regression fixtures, not measured success rates on real songs.
        val editions = listOf("Live", "Remix", "Instrumental", "Karaoke", "Acoustic")
        for (i in 1..8) for (edition in editions) {
            val q = SongQuery("Fixture Song $i", "Artist", durationMs = 180000)
            val wrong = candidate(title = "Fixture Song $i ($edition)")
            assertNull("Fixture $i / $edition", LyricsMatching.choose(q, listOf(wrong)))
        }
    }
}
