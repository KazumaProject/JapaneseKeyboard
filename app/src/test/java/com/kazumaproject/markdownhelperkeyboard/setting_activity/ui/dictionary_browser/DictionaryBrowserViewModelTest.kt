package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.dictionary_browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DictionaryBrowserViewModelTest {
    private val version = DictionaryBrowserVersion(1, true, false)
    private val rows = (0 until 251).map { DictionaryBrowserRow("reading$it", "word$it") }

    @Test fun switchingDictionaryShowsLoadingUntilTheNewRowsArrive() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = mock<DictionaryBrowserRepository>()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        whenever(repo.version(any())).thenReturn(version)
        whenever(repo.open(DictionaryCategory.SYSTEM, ""))
            .thenReturn(DictionaryBrowserCursor(version, rows.iterator()))
        // Suspend the replacement load so its intermediate state is observable.
        whenever(repo.open(DictionaryCategory.ENGLISH, "")).doSuspendableAnswer {
            gate.await()
            DictionaryBrowserCursor(version, rows.take(1).iterator())
        }
        val model = DictionaryBrowserViewModel(repo, SavedStateHandle())
        try {
            runCurrent()
            withContext(Dispatchers.Default) { withTimeout(10000) { model.state.first { !it.loading } } }
            model.select(DictionaryCategory.ENGLISH)
            runCurrent()
            assertTrue(model.state.value.loading)
            assertTrue(model.state.value.rows.isEmpty())
            assertEquals(DictionaryCategory.ENGLISH, model.state.value.category)
            gate.complete(Unit)
            runCurrent()
            withContext(Dispatchers.Default) { withTimeout(10000) { model.state.first { !it.loading } } }
            assertEquals(rows.take(1), model.state.value.rows)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test fun rapidQueriesPublishOnlyTheLatestRequestAndPaginationIsNotDuplicated() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = mock<DictionaryBrowserRepository>()
        whenever(repo.version(any())).thenReturn(version)
        whenever(repo.open(DictionaryCategory.SYSTEM, "")).thenReturn(DictionaryBrowserCursor(version, rows.iterator()))
        whenever(repo.open(DictionaryCategory.SYSTEM, "word20")).thenReturn(DictionaryBrowserCursor(version, rows.iterator(), "word20"))
        val model = DictionaryBrowserViewModel(repo, SavedStateHandle())
        try {
            runCurrent()
            withContext(Dispatchers.Default) { withTimeout(10000) { model.state.first { !it.loading } } }
            assertEquals(100, model.state.value.rows.size)
            model.loadMore()
            model.loadMore()
            runCurrent()
            withContext(Dispatchers.Default) { withTimeout(10000) { model.state.first { !it.loading } } }
            assertEquals(rows.take(200), model.state.value.rows)
            model.search("abandoned")
            model.search("word20")
            advanceTimeBy(301)
            runCurrent()
            withContext(Dispatchers.Default) { withTimeout(10000) { model.state.first { !it.loading } } }
            assertEquals(rows.filter { it.word.contains("word20") }, model.state.value.rows)
            verify(repo, never()).open(DictionaryCategory.SYSTEM, "abandoned")
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test fun savedSelectionQueryRowsAndScrollAreRestoredAfterProcessRecreation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = mock<DictionaryBrowserRepository>()
        whenever(repo.version(any())).thenReturn(version)
        whenever(repo.open(DictionaryCategory.ENGLISH, "word")).thenReturn(DictionaryBrowserCursor(version, rows.iterator(), "word"))
        val saved = SavedStateHandle(mapOf("category" to "ENGLISH", "query" to "word", "loaded_count" to 200,
            "scroll_position" to 140, "scroll_offset" to -10))
        val model = DictionaryBrowserViewModel(repo, saved)
        try {
            runCurrent()
            withContext(Dispatchers.Default) { withTimeout(10000) { model.state.first { !it.loading } } }
            assertEquals(DictionaryCategory.ENGLISH, model.state.value.category)
            assertEquals("word", model.state.value.query)
            assertEquals(rows.take(200), model.state.value.rows)
            assertEquals(140, model.scrollPosition)
            assertEquals(-10, model.scrollOffset)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test fun errorsCanBeRetriedAndChangedSourcesReplaceDisplayedRows() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = mock<DictionaryBrowserRepository>()
        whenever(repo.version(any())).thenReturn(version)
        whenever(repo.open(DictionaryCategory.SYSTEM, "")).thenThrow(IllegalStateException("broken"))
            .thenReturn(DictionaryBrowserCursor(version, rows.iterator()))
        val model = DictionaryBrowserViewModel(repo, SavedStateHandle())
        try {
            runCurrent()
            withContext(Dispatchers.Default) { withTimeout(10000) { model.state.first { !it.loading } } }
            assertTrue(model.state.value.failed)
            model.retry()
            runCurrent()
            withContext(Dispatchers.Default) { withTimeout(10000) { model.state.first { !it.loading } } }
            assertFalse(model.state.value.failed)
            val changed = version.copy(revision = 2, external = true)
            whenever(repo.version(any())).thenReturn(changed)
            whenever(repo.open(DictionaryCategory.SYSTEM, "")).thenReturn(DictionaryBrowserCursor(changed, rows.takeLast(1).iterator()))
            model.refreshIfChanged()
            runCurrent()
            withContext(Dispatchers.Default) { withTimeout(10000) { model.state.first { !it.loading } } }
            assertEquals(changed, model.state.value.version)
            assertEquals(rows.takeLast(1), model.state.value.rows)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
