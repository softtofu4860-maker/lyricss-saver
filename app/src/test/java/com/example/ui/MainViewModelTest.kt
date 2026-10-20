package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.api.GeminiLyricsService
import com.example.data.*
import com.example.service.MediaStateHolder
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: LyricsDatabase
    private val store = ViewModelStore()
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        MediaStateHolder.clear()
        database = Room.inMemoryDatabaseBuilder(application, LyricsDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun tearDown() {
        store.clear()
        MediaStateHolder.clear()
        database.close()
        Dispatchers.resetMain()
    }

    private fun song(title: String) = CachedLyrics(SongIdentity.id(title, "artist"), title, "artist",
        GeminiLyricsService.lyricAdapter.toJson(listOf(LyricLine(1f, "original $title", romanization = "rom"))))

    private fun viewModel(source: suspend (LyricsRequest) -> CachedLyrics,
        translation: suspend (String) -> String = { it }): MainViewModel {
        val repository = LyricsRepository(database.lyricsDao(), source, dispatcher)
        return MainViewModel(application, repository, translation).also { store.put("vm", it) }
    }

    @Test fun `late old-track result cannot replace new-track lyrics`() = runTest(dispatcher) {
        val oldStarted = CompletableDeferred<Unit>()
        val oldRelease = CompletableDeferred<Unit>()
        val newStarted = CompletableDeferred<Unit>()
        val vm = viewModel({ request ->
            if (request.title == "old") {
                oldStarted.complete(Unit)
                withContext(NonCancellable) { oldRelease.await() }
            } else newStarted.complete(Unit)
            song(request.title)
        })
        runCurrent()
        vm.loadLyrics("old", "artist")
        oldStarted.await()
        vm.loadLyrics("new", "artist")
        newStarted.await()
        vm.lyricsState.firstSuccess { it.cachedLyrics.title == "new" }
        assertEquals("new", (vm.lyricsState.value as LyricsUiState.Success).cachedLyrics.title)
        oldRelease.complete(Unit)
        advanceUntilIdle()
        assertEquals("new", (vm.lyricsState.value as LyricsUiState.Success).cachedLyrics.title)
        assertNull(database.lyricsDao().getLyricsById(song("old").id))
    }

    @Test fun `clearing media cancels outstanding request and retains empty state`() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val vm = viewModel({
            started.complete(Unit)
            try { awaitCancellation() } finally { cancelled = true }
        })
        runCurrent()
        MediaStateHolder.updateState("song", "artist", null, true, 0L, 10000L, "player")
        started.await()
        MediaStateHolder.clear()
        advanceUntilIdle()
        assertTrue(cancelled)
        assertEquals(LyricsUiState.Empty, vm.lyricsState.value)
    }

    @Test fun `translation saves alongside originals and survives cache clear`() = runTest(dispatcher) {
        val original = song("song")
        database.lyricsDao().insertLyrics(original)
        val vm = viewModel({ error("unexpected fetch") }, { "[00:01.00] 번역" })
        runCurrent()
        vm.loadLyricsFromLibrary(original)
        runCurrent()
        vm.translateActiveLyrics()
        // Await the repository operation rather than treating a queued task as a passed test.
        vm.lyricsState.firstSuccess { it.parsedLines.single().translation == "번역" }
        val state = vm.lyricsState.value as LyricsUiState.Success
        assertEquals("original song", state.parsedLines.single().text)
        assertEquals("rom", state.parsedLines.single().romanization)
        database.lyricsDao().clearAutomaticCache()
        assertEquals(state.cachedLyrics, database.lyricsDao().getLyricsById(original.id))
    }

    @Test fun `late translation cannot overwrite next song`() = runTest(dispatcher) {
        val original = song("old")
        database.lyricsDao().insertLyrics(original)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val vm = viewModel({ song(it.title) }, {
            started.complete(Unit)
            withContext(NonCancellable) { release.await() }
            "[00:01.00] 오래된 번역"
        })
        runCurrent()
        vm.loadLyricsFromLibrary(original)
        runCurrent()
        vm.translateActiveLyrics()
        started.await()
        vm.loadLyrics("next", "artist")
        vm.lyricsState.firstSuccess { it.cachedLyrics.title == "next" }
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals("next", (vm.lyricsState.value as LyricsUiState.Success).cachedLyrics.title)
        assertEquals(original, database.lyricsDao().getLyricsById(original.id))
    }

    @Test fun `selecting library song does not trigger a redundant fetch`() = runTest(dispatcher) {
        val vm = viewModel({ error("library selection should not fetch") })
        runCurrent()
        vm.loadLyricsFromLibrary(song("sample"))
        advanceUntilIdle()
        assertEquals("sample", (vm.lyricsState.value as LyricsUiState.Success).cachedLyrics.title)
    }

    @Test fun `media change automatically retries transient failure then stops after success`() = runTest(dispatcher) {
        var calls = 0
        val vm = viewModel({ request ->
            assertNull(request.customQuery)
            if (++calls == 1) song(request.title).copy(isFallback = true) else song(request.title)
        })
        runCurrent()
        MediaStateHolder.updateState("song", "artist", null, true, 0, 10000, "player")
        vm.lyricsState.firstSuccess { it.cachedLyrics.isFallback }
        vm.lyricsState.firstSuccess { !it.cachedLyrics.isFallback }
        advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals("song", (vm.lyricsState.value as LyricsUiState.Success).cachedLyrics.title)
    }

    @Test fun `new song cancels old songs pending automatic retry`() = runTest(dispatcher) {
        val requested = mutableListOf<String>()
        val vm = viewModel({ request ->
            requested.add(request.title)
            song(request.title).copy(isFallback = request.title == "old")
        })
        runCurrent()
        MediaStateHolder.updateState("old", "artist", null, true, 0, 10000, "player")
        vm.lyricsState.firstSuccess { it.cachedLyrics.isFallback }
        MediaStateHolder.updateState("new", "artist", null, true, 0, 10000, "player")
        vm.lyricsState.firstSuccess { it.cachedLyrics.title == "new" }
        advanceUntilIdle()
        assertEquals(listOf("old", "new"), requested)
    }

    @Test fun `title-only media still starts automatic search`() = runTest(dispatcher) {
        var calls = 0
        val vm = viewModel({ request ->
            calls++
            assertEquals("", request.artist)
            song(request.title).copy(isFallback = true)
        })
        runCurrent()
        MediaStateHolder.updateState("song", null, null, true, 0, 10000, "player")
        vm.lyricsState.firstSuccess { it.cachedLyrics.isFallback }
        assertTrue(calls >= 1)
    }

    @Test fun `manual correction stops pending automatic retry`() = runTest(dispatcher) {
        var calls = 0
        val vm = viewModel({ request -> calls++; song(request.title).copy(isFallback = true) })
        runCurrent()
        MediaStateHolder.updateState("song", "artist", null, true, 0, 10000, "player")
        vm.lyricsState.firstSuccess { it.cachedLyrics.isFallback }
        vm.saveManualLyrics("song", "artist", listOf(LyricLine(1f, "my edit")))
        vm.lyricsState.firstSuccess { it.parsedLines.single().text == "my edit" }
        advanceUntilIdle()
        assertEquals(1, calls)
    }
}

private suspend fun kotlinx.coroutines.flow.StateFlow<LyricsUiState>.firstSuccess(
    predicate: (LyricsUiState.Success) -> Boolean
): LyricsUiState = first { it is LyricsUiState.Success && predicate(it) }
