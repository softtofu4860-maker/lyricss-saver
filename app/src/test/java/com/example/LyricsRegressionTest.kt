package com.example

import com.example.api.LyricsMatching
import com.example.data.CachedLyrics
import com.example.data.LyricLine
import com.example.data.LyricsIdentity
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class LyricsRegressionTest {
    @Test fun differentJapaneseSongsHaveDifferentIds() {
        assertNotEquals(LyricsIdentity.songId("夜に駆ける", "ヨアソビ"), LyricsIdentity.songId("群青", "ヨアソビ"))
    }
    @Test fun differentChineseSongsHaveDifferentIds() {
        assertNotEquals(LyricsIdentity.songId("晴天", "周杰倫"), LyricsIdentity.songId("夜曲", "周杰倫"))
    }
    @Test fun separatorsCannotMakeTwoSongsCollide() {
        assertNotEquals(LyricsIdentity.songId("a_b", "c"), LyricsIdentity.songId("a", "b_c"))
    }
    @Test fun punctuationIsPreservedInIdentity() {
        assertNotEquals(LyricsIdentity.songId("AB", "Artist"), LyricsIdentity.songId("A B", "Artist"))
    }
    @Test fun unicodeCompositionAndCaseAreNormalized() {
        assertEquals(LyricsIdentity.songId(" Café ", "ARTIST"), LyricsIdentity.songId("Cafe\u0301", "artist"))
    }
    @Test fun identityDoesNotDependOnDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val turkish = LyricsIdentity.songId("I", "ARTIST")
            Locale.setDefault(Locale.US)
            assertEquals(turkish, LyricsIdentity.songId("I", "ARTIST"))
        } finally { Locale.setDefault(previous) }
    }
    @Test fun collidingLegacyRecordMustHaveMatchingMetadata() {
        val cached = CachedLyrics("_", "群青", "ヨアソビ", "[]")
        assertFalse(LyricsIdentity.matches(cached, "夜に駆ける", "ヨアソビ"))
        assertTrue(LyricsIdentity.matches(cached, "群青", "ヨアソビ"))
    }
    @Test fun correctCandidateWithinDurationToleranceIsAccepted() {
        assertTrue(LyricsMatching.matchesSong("Hello!", "Artist", "hello", listOf("artist"), 180000, 181000))
    }
    @Test fun liveVersionIsRejected() {
        assertFalse(LyricsMatching.matchesSong("Hello", "Artist", "Hello (Live)", listOf("Artist"), 180000, 180000))
    }
    @Test fun wrongArtistIsRejected() {
        assertFalse(LyricsMatching.matchesSong("Hello", "Artist", "Hello", listOf("Cover Artist")))
    }
    @Test fun wrongDurationIsRejected() {
        assertFalse(LyricsMatching.matchesSong("Hello", "Artist", "Hello", listOf("Artist"), 180000, 210000))
    }
    @Test fun emptyTitleCannotMatch() {
        assertFalse(LyricsMatching.matchesSong("♪", "Artist", "♪", listOf("Artist")))
    }
    @Test fun translationPreservesTextPronunciationAndTime() {
        val original = listOf(LyricLine(1.123f, "Original", romanization = "Pronunciation"))
        val translated = LyricsMatching.parseTranslation("[00:01.123]번역")
        val merged = LyricsMatching.mergeTranslation(original, translated)!!
        assertEquals(original[0].text, merged[0].text)
        assertEquals(original[0].romanization, merged[0].romanization)
        assertEquals(original[0].timeSec, merged[0].timeSec)
        assertEquals("번역", merged[0].translation)
    }
    @Test fun incompleteTranslationIsRejected() {
        assertNull(LyricsMatching.mergeTranslation(listOf(LyricLine(1f, "One"), LyricLine(2f, "Two")), listOf(LyricLine(1f, "하나"))))
    }
    @Test fun shiftedTranslationIsRejected() {
        assertNull(LyricsMatching.mergeTranslation(listOf(LyricLine(1f, "One")), listOf(LyricLine(2f, "하나"))))
    }
    @Test fun closeTimestampsRemainSeparateTranslationLines() {
        assertEquals(2, LyricsMatching.parseTranslation("[00:01.100]하나\n[00:01.200]둘").size)
    }
    @Test fun untimedOrMalformedTranslationIsRejected() {
        assertTrue(LyricsMatching.parseTranslation("No timestamps").isEmpty())
        assertTrue(LyricsMatching.parseTranslation("[00:99.100]Invalid").isEmpty())
    }
}
