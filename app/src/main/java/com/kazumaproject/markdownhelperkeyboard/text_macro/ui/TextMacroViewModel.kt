package com.kazumaproject.markdownhelperkeyboard.text_macro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.repository.TextMacroImportPlan
import com.kazumaproject.markdownhelperkeyboard.repository.TextMacroRepository
import com.kazumaproject.markdownhelperkeyboard.text_macro.database.TextMacro
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TextMacroViewModel @Inject constructor(
    private val repository: TextMacroRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")

    private val reload = MutableStateFlow(0)
    private val initialResult = MutableStateFlow<Result<Unit>?>(null)

    internal fun retryInitialLoad() {
        initialResult.value = null
        reload.value++
    }

    internal suspend fun awaitInitialLoad() {
        initialResult.first { it != null }!!.getOrThrow()
    }

    val macros = combine(query, reload) { value, _ -> value }.flatMapLatest { value ->
        (if (value.isBlank()) repository.observeAll() else repository.search(value))
            .onEach { initialResult.value = Result.success(Unit) }
            .catch { error ->
                if (error is CancellationException) throw error
                initialResult.value = Result.failure(error)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        query.value = value
    }

    suspend fun save(macro: TextMacro): Long = repository.save(macro)

    suspend fun setEnabled(id: Long, enabled: Boolean) = repository.setEnabled(id, enabled)

    suspend fun delete(id: Long) = repository.deleteById(id)

    suspend fun deleteAll() = repository.deleteAll()

    suspend fun exportJson(): String = repository.exportJson()

    suspend fun prepareImport(json: String): TextMacroImportPlan = repository.prepareImport(json)

    suspend fun applyImport(plan: TextMacroImportPlan) = repository.applyImport(plan)
}
