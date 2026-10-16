package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.dictionary_browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class DictionaryBrowserUiState(
    val category: DictionaryCategory,
    val query: String,
    val rows: List<DictionaryBrowserRow> = emptyList(),
    val version: DictionaryBrowserVersion? = null,
    val loading: Boolean = true,
    val hasMore: Boolean = false,
    val failed: Boolean = false,
    val generation: Int = 0,
)

@HiltViewModel
class DictionaryBrowserViewModel @Inject constructor(
    private val repository: DictionaryBrowserRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val initialCategory = DictionaryBrowserCatalog.all.firstOrNull {
        it.category.name == saved.get<String>("category")
    }?.category ?: DictionaryCategory.SYSTEM
    private val mutableState = MutableStateFlow(
        DictionaryBrowserUiState(initialCategory, saved["query"] ?: ""),
    )
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var cursor: DictionaryBrowserCursor? = null
    private var generation = 0
    var scrollPosition: Int
        get() = saved["scroll_position"] ?: 0
        private set(value) { saved["scroll_position"] = value }
    var scrollOffset: Int
        get() = saved["scroll_offset"] ?: 0
        private set(value) { saved["scroll_offset"] = value }

    init { reload(restoreCount = saved["loaded_count"] ?: 0) }

    fun select(category: DictionaryCategory) {
        if (category == state.value.category) return
        saved["category"] = category.name
        mutableState.value = state.value.copy(category = category)
        resetScroll()
        reload()
    }

    fun search(query: String) {
        if (query == state.value.query) return
        saved["query"] = query
        mutableState.value = state.value.copy(query = query)
        resetScroll()
        reload(debounce = true)
    }

    fun rememberScroll(position: Int, offset: Int) {
        scrollPosition = position.coerceAtLeast(0)
        scrollOffset = offset
    }

    private fun resetScroll() {
        rememberScroll(0, 0)
        saved["loaded_count"] = 0
    }

    fun refreshIfChanged() {
        val version = state.value.version ?: return
        if (version != repository.version(state.value.category)) {
            resetScroll()
            reload()
        }
    }

    fun retry() {
        resetScroll()
        reload()
    }

    private fun reload(debounce: Boolean = false, restoreCount: Int = 0) {
        val previous = job
        previous?.cancel()
        val request = state.value.copy(
            rows = emptyList(), version = null, loading = true, failed = false,
            hasMore = false, generation = ++generation,
        )
        mutableState.value = request
        job = viewModelScope.launch {
            previous?.cancelAndJoin()
            cursor = null
            if (debounce) delay(300)
            try {
                val opened = repository.open(request.category, request.query)
                cursor = opened
                val rows = mutableListOf<DictionaryBrowserRow>()
                var more: Boolean
                do {
                    val page = opened.nextPage()
                    rows.addAll(page.rows)
                    more = page.hasMore
                } while (more && rows.size < restoreCount)
                publish(request.copy(rows = rows.toList(), version = opened.version, loading = false, hasMore = more))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportFailure(request, error)
            }
        }
    }

    fun loadMore() {
        val current = state.value
        val opened = cursor ?: return
        if (current.loading || current.failed || !current.hasMore) return
        mutableState.value = current.copy(loading = true)
        job = viewModelScope.launch {
            try {
                if (opened.version != repository.version(current.category)) {
                    resetScroll()
                    reload()
                    return@launch
                }
                val page = opened.nextPage()
                publish(current.copy(rows = current.rows + page.rows, hasMore = page.hasMore, loading = false))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportFailure(current, error)
            }
        }
    }

    private fun publish(result: DictionaryBrowserUiState) {
        if (result.generation != generation) return
        // A replacement/import during the background scan must not publish stale rows.
        if (result.version != repository.version(result.category)) {
            resetScroll()
            reload()
            return
        }
        saved["loaded_count"] = result.rows.size
        mutableState.value = result
    }

    private fun reportFailure(request: DictionaryBrowserUiState, error: Exception) {
        if (request.generation != generation) return
        Timber.w(error, "Dictionary browser failed: %s", request.category)
        cursor = null
        mutableState.value = request.copy(loading = false, failed = true, hasMore = false)
    }
}
