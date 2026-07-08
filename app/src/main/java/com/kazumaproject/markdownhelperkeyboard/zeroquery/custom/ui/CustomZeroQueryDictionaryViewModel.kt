package com.kazumaproject.markdownhelperkeyboard.zeroquery.custom.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.repository.CustomZeroQueryBackupEntry
import com.kazumaproject.markdownhelperkeyboard.repository.CustomZeroQueryGroup
import com.kazumaproject.markdownhelperkeyboard.repository.CustomZeroQueryImportMode
import com.kazumaproject.markdownhelperkeyboard.repository.CustomZeroQueryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.CustomZeroQuerySaveResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class CustomZeroQueryFilter {
    All,
    Enabled,
    Disabled,
}

enum class CustomZeroQuerySort {
    Key,
    Updated,
    CandidateCount,
}

data class CustomZeroQueryDictionaryUiState(
    val query: String = "",
    val filter: CustomZeroQueryFilter = CustomZeroQueryFilter.All,
    val sort: CustomZeroQuerySort = CustomZeroQuerySort.Key,
    val groups: List<CustomZeroQueryGroup> = emptyList(),
    val totalGroupCount: Int = 0,
)

@HiltViewModel
class CustomZeroQueryDictionaryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CustomZeroQueryRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(CustomZeroQueryFilter.All)
    private val sort = MutableStateFlow(CustomZeroQuerySort.Key)
    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val uiState: StateFlow<CustomZeroQueryDictionaryUiState> =
        combine(
            repository.observeGroups(),
            query,
            filter,
            sort,
        ) { groups, currentQuery, currentFilter, currentSort ->
            val filtered = groups
                .filter { group -> group.matchesFilter(currentFilter) }
                .filter { group -> group.matchesQuery(currentQuery) }
                .sortBy(currentSort)
            CustomZeroQueryDictionaryUiState(
                query = currentQuery,
                filter = currentFilter,
                sort = currentSort,
                groups = filtered,
                totalGroupCount = groups.size,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CustomZeroQueryDictionaryUiState(),
        )

    fun updateQuery(value: String) {
        query.value = value
    }

    fun updateFilter(value: CustomZeroQueryFilter) {
        filter.value = value
    }

    fun updateSort(value: CustomZeroQuerySort) {
        sort.value = value
    }

    fun addEntry(
        displayKey: String,
        candidate: String,
        enabled: Boolean,
    ) {
        saveEntry(
            id = 0,
            displayKey = displayKey,
            candidate = candidate,
            enabled = enabled,
        )
    }

    fun deleteGroup(lookupKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteGroup(lookupKey)
            _messages.emit(context.getString(R.string.custom_zero_query_group_deleted))
        }
    }

    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAll()
            _messages.emit(context.getString(R.string.custom_zero_query_all_deleted))
        }
    }

    suspend fun exportEntries(): List<CustomZeroQueryBackupEntry> =
        withContext(Dispatchers.IO) {
            repository.exportEntries()
        }

    fun importEntries(
        entries: List<CustomZeroQueryBackupEntry>,
        mode: CustomZeroQueryImportMode,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.importEntries(entries, mode)
            _messages.emit(
                context.getString(
                    R.string.custom_zero_query_import_result,
                    result.added,
                    result.updated,
                    result.skipped,
                )
            )
        }
    }

    private fun saveEntry(
        id: Long,
        displayKey: String,
        candidate: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.saveEntry(
                id = id,
                rawDisplayKey = displayKey,
                rawCandidate = candidate,
                enabled = enabled,
            )
            _messages.emit(result.toMessage())
        }
    }

    private fun CustomZeroQueryGroup.matchesFilter(filter: CustomZeroQueryFilter): Boolean =
        when (filter) {
            CustomZeroQueryFilter.All -> true
            CustomZeroQueryFilter.Enabled -> entries.any { it.enabled }
            CustomZeroQueryFilter.Disabled -> entries.all { !it.enabled }
        }

    private fun CustomZeroQueryGroup.matchesQuery(rawQuery: String): Boolean {
        val normalizedQuery = rawQuery.trim()
        if (normalizedQuery.isEmpty()) return true

        return displayKey.contains(normalizedQuery, ignoreCase = true) ||
            lookupKey.contains(normalizedQuery, ignoreCase = true) ||
            entries.any { it.candidate.contains(normalizedQuery, ignoreCase = true) }
    }

    private fun List<CustomZeroQueryGroup>.sortBy(
        sort: CustomZeroQuerySort,
    ): List<CustomZeroQueryGroup> =
        when (sort) {
            CustomZeroQuerySort.Key -> sortedWith(
                compareBy<CustomZeroQueryGroup> { it.displayKey.lowercase() }
                    .thenBy { it.displayKey }
            )
            CustomZeroQuerySort.Updated -> sortedByDescending { it.updatedAt }
            CustomZeroQuerySort.CandidateCount -> sortedWith(
                compareByDescending<CustomZeroQueryGroup> { it.entries.size }
                    .thenBy { it.displayKey.lowercase() }
            )
        }

    private fun CustomZeroQuerySaveResult.toMessage(): String =
        when (this) {
            CustomZeroQuerySaveResult.Saved ->
                context.getString(R.string.custom_zero_query_saved)
            CustomZeroQuerySaveResult.EmptyKey ->
                context.getString(R.string.custom_zero_query_error_empty_key)
            CustomZeroQuerySaveResult.EmptyCandidate ->
                context.getString(R.string.custom_zero_query_error_empty_candidate)
            CustomZeroQuerySaveResult.Duplicate ->
                context.getString(R.string.custom_zero_query_error_duplicate)
        }
}
