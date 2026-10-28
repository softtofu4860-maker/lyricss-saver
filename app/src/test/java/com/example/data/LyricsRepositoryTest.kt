package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.api.GeminiLyricsService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LyricsRepositoryTest {
    private lateinit var database: LyricsDatabase
    private lateinit var dao: LyricsDao

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
            LyricsDatabase::class.java).allowMainThreadQueries().build()
        dao = database.lyricsDao()
    }
    @After fun tearDown() { database.close() }

    private fun song(title: String = "song", text: String = "original") = CachedLyrics(
        SongIdentity.id(title, "artist"), title, "artist",
        GeminiLyricsService.lyricAdapter.toJson(listOf(LyricLine(1f, text))))

    @Test fun `provider identity upgrade preserves the old scoped manual cache without copying to other tracks`() = runTest {
        val descriptor = SongDescriptor("song", "artist", provider = "player", providerId = "track-1")
        val previous = descriptor.previousIds.single()
        val original = song().copy(id = previous, isUserSaved = true, identityBinding = previous)
        dao.insertLyrics(original)
        val repo = LyricsRepository(dao, { song(text = "different") }, StandardTestDispatcher(testScheduler))
        val upgraded = repo.getLyrics("song", "artist", descriptor = descriptor)
        assertEquals(descriptor.id, upgraded.id)
        assertEquals(original.lyricsJson, upgraded.lyricsJson)
        assertNotNull(dao.getLyricsById(previous))
        val another = repo.getLyrics("song", "artist", descriptor = descriptor.copy(providerId = "track-2"))
        assertEquals("different", GeminiLyricsService.parseJsonLyrics(another.lyricsJson).single().text)
    }

    @Test fun `album metadata gaining duration keeps protected edits and first-claim prevents cross-version copying`() = runTest {
        val incomplete = SongDescriptor("song", "artist", album = "studio")
        val complete = incomplete.copy(durationMs = 180000)
        val original = song().copy(id = incomplete.id, isUserSaved = true, identityBinding = incomplete.id)
        dao.insertLyrics(original)
        val repo = LyricsRepository(dao, { song(text = "other version") }, StandardTestDispatcher(testScheduler))
        assertEquals(original.lyricsJson, repo.getLyrics("song", "artist", descriptor = complete).lyricsJson)
        assertNotNull(dao.getLyricsById(incomplete.id))
        val other = repo.getLyrics("song", "artist", descriptor = complete.copy(durationMs = 240000))
        assertEquals("other version", GeminiLyricsService.parseJsonLyrics(other.lyricsJson).single().text)
    }

    @Test fun `legacy protected translations bind once without leaking into another recording`() = runTest {
        val original = song().copy(isUserSaved = true,
            lyricsJson = GeminiLyricsService.lyricAdapter.toJson(listOf(LyricLine(1f, "edited", translation = "보존"))))
        dao.insertLyrics(original)
        var fetches = 0
        val repository = LyricsRepository(dao, { fetches++; song(text = "other recording") }, StandardTestDispatcher(testScheduler))
        val studio = SongDescriptor("song", "artist", "studio", 180000)
        val live = studio.copy(album = "live", durationMs = 240000)
        val claimed = repository.getLyrics("song", "artist", descriptor = studio)
        assertEquals(studio.id, claimed.id)
        assertEquals("보존", GeminiLyricsService.parseJsonLyrics(claimed.lyricsJson).single().translation)
        assertEquals(0, fetches)
        val other = repository.getLyrics("song", "artist", descriptor = live)
        assertEquals(live.id, other.id)
        assertEquals("other recording", GeminiLyricsService.parseJsonLyrics(other.lyricsJson).single().text)
        assertEquals(1, fetches)
        assertEquals(original.lyricsJson, dao.getLyricsById(original.id)!!.lyricsJson)
        assertEquals(studio.id, dao.getLyricsById(original.id)!!.identityBinding)
    }

    @Test fun `automatic lookup retries network failure without manual input and then caches success`() = runTest {
        var calls = 0
        val repository = LyricsRepository(dao, { request ->
            assertNull(request.customQuery)
            if (++calls == 1) throw java.io.IOException("offline")
            song()
        }, StandardTestDispatcher(testScheduler))
        val results = repository.observeAutomaticLyrics("song", "artist").toList()
        assertEquals(2, calls)
        assertEquals(listOf(true, false), results.map { it.isFallback })
        assertNotNull(dao.getLyricsById(song().id))
    }

    @Test fun `automatic missing-song retries stop after three attempts without caching fallback`() = runTest {
        var calls = 0
        val repository = LyricsRepository(dao, { calls++; song().copy(isFallback = true) },
            StandardTestDispatcher(testScheduler))
        val results = repository.observeAutomaticLyrics("song", "artist").toList()
        assertEquals(3, calls)
        assertTrue(results.all { it.isFallback })
        assertNull(dao.getLyricsById(song().id))
    }

    @Test fun `manual search bypasses cache and metadata then protects correction from cache clear`() = runTest {
        dao.insertLyrics(song())
        var received: LyricsRequest? = null
        val repository = LyricsRepository(dao, { request -> received = request; song(text = "corrected") },
            StandardTestDispatcher(testScheduler))
        val corrected = repository.getLyrics("song", "artist", "old metadata", customQuery = " new query ")
        assertEquals("new query", received?.customQuery)
        assertNull(received?.metadataLyrics)
        assertTrue(corrected.isUserSaved)
        repository.clearCache()
        assertEquals(corrected, dao.getLyricsById(corrected.id))
    }

    @Test fun `fallback is never persisted and next attempt can succeed`() = runTest {
        var calls = 0
        val repository = LyricsRepository(dao, { request ->
            if (++calls == 1) GeminiLyricsService.generateSmartFallback(
                SongIdentity.id(request.title, request.artist), request.title, request.artist)
            else song()
        }, StandardTestDispatcher(testScheduler))
        assertTrue(repository.getLyrics("song", "artist").isFallback)
        assertNull(dao.getLyricsById(song().id))
        assertFalse(repository.getLyrics("song", "artist").isFallback)
        assertEquals(2, calls)
        assertNotNull(dao.getLyricsById(song().id))
    }

    @Test fun `failed correction preserves existing edited lyrics`() = runTest {
        val original = song().copy(isUserSaved = true)
        dao.insertLyrics(original)
        val repository = LyricsRepository(dao, { original.copy(isFallback = true) },
            StandardTestDispatcher(testScheduler))
        repository.getLyrics("song", "artist", customQuery = "missing")
        assertEquals(original, dao.getLyricsById(original.id))
    }

    @Test fun `cache clear retains manual edits and translations with their original lyrics`() = runTest {
        val repository = LyricsRepository(dao, { error("unexpected network request") },
            StandardTestDispatcher(testScheduler))
        dao.insertLyrics(song("automatic"))
        val manual = repository.saveManualLyrics("manual", "artist", listOf(LyricLine(1f, "edited")))
        val original = song("translated")
        dao.insertLyrics(original)
        val translated = repository.saveTranslation(original,
            listOf(LyricLine(1f, "original", translation = "번역")))!!
        repository.clearCache()
        assertNull(dao.getLyricsById(song("automatic").id))
        assertEquals(manual, dao.getLyricsById(manual.id))
        assertEquals(translated, dao.getLyricsById(translated.id))
        assertEquals("original", GeminiLyricsService.parseJsonLyrics(translated.lyricsJson).single().text)
        assertEquals("번역", GeminiLyricsService.parseJsonLyrics(translated.lyricsJson).single().translation)
    }

    @Test fun `late translation does not overwrite edits or resurrect deleted entries`() = runTest {
        val repository = LyricsRepository(dao, { error("unexpected fetch") }, StandardTestDispatcher(testScheduler))
        val original = song()
        dao.insertLyrics(original)
        val edited = repository.saveManualLyrics("song", "artist", listOf(LyricLine(1f, "new edit")))
        assertNull(repository.saveTranslation(original, listOf(LyricLine(1f, "old", "번역"))))
        assertEquals(edited, dao.getLyricsById(original.id))
        repository.deleteLyrics(original.id)
        assertNull(repository.saveTranslation(edited, emptyList()))
        assertNull(dao.getLyricsById(original.id))
    }

    @Test fun `concurrent activity and screensaver requests share a single successful fetch`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val source: suspend (LyricsRequest) -> CachedLyrics = {
            calls++; started.complete(Unit); release.await(); song()
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val activity = LyricsRepository(dao, source, dispatcher)
        val dream = LyricsRepository(dao, source, dispatcher)
        val first = async { activity.getLyrics("song", "artist") }
        started.await()
        val second = async { dream.getLyrics("song", "artist") }
        runCurrent()
        release.complete(Unit)
        assertEquals(first.await(), second.await())
        assertEquals(1, calls)
    }

    @Test fun `cancelled fetch releases song lock and writes no cache`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = LyricsRepository(dao, { started.complete(Unit); release.await(); song() }, dispatcher)
        val request = launch { repository.getLyrics("song", "artist") }
        started.await()
        request.cancelAndJoin()
        assertNull(dao.getLyricsById(song().id))
        val next = LyricsRepository(dao, { song() }, dispatcher).getLyrics("song", "artist")
        assertEquals(song().lyricsJson, next.lyricsJson)
    }

    @Test fun `cache clear prevents in-flight automatic fetch from repopulating old cache`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repository = LyricsRepository(dao, { started.complete(Unit); release.await(); song() },
            StandardTestDispatcher(testScheduler))
        val request = async { repository.getLyrics("song", "artist") }
        started.await()
        repository.clearCache()
        release.complete(Unit)
        assertFalse(request.await().isFallback)
        assertNull(dao.getLyricsById(song().id))
    }

    @Test fun `new metadata upgrades automatic cache but never overwrites a manual edit`() = runTest {
        var calls = 0
        val repository = LyricsRepository(dao, { calls++; song(text = "metadata") },
            StandardTestDispatcher(testScheduler))
        dao.insertLyrics(song())
        val upgraded = repository.getLyrics("song", "artist", metadataLyrics = "[00:01] metadata")
        assertEquals("metadata", GeminiLyricsService.parseJsonLyrics(upgraded.lyricsJson).single().text)
        val manual = repository.saveManualLyrics("song", "artist", listOf(LyricLine(1f, "user edit")))
        assertEquals(manual, repository.getLyrics("song", "artist", metadataLyrics = "[00:01] other"))
        assertEquals(1, calls)
    }

}
