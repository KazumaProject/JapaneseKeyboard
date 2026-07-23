package com.kazumaproject.markdownhelperkeyboard.gemma.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplate
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaInputModality
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaOutputLanguage
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaOutputMode
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaTaskKind
import com.kazumaproject.markdownhelperkeyboard.repository.GemmaPromptTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GemmaPromptTemplateViewModel @Inject constructor(
    private val repository: GemmaPromptTemplateRepository
) : ViewModel() {

    private val _templates = MutableStateFlow<List<GemmaPromptTemplate>>(emptyList())
    val templates: StateFlow<List<GemmaPromptTemplate>> = _templates.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureBuiltIns()
            repository.observeAll().collect { items ->
                _templates.value = items
            }
        }
    }

    fun saveTemplate(
        currentTemplate: GemmaPromptTemplate?,
        title: String,
        prompt: String,
        isEnabled: Boolean,
        inputModality: GemmaInputModality = GemmaInputModality.TEXT,
        taskKind: GemmaTaskKind = GemmaTaskKind.CUSTOM,
        outputMode: GemmaOutputMode = GemmaOutputMode.SINGLE_TEXT,
        outputLanguage: GemmaOutputLanguage = GemmaOutputLanguage.AUTO,
        candidateCount: Int = 1,
        showInActionMenu: Boolean = true,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (currentTemplate == null) {
                repository.insert(
                    GemmaPromptTemplate(
                        title = title,
                        prompt = prompt,
                        isEnabled = isEnabled,
                        sortOrder = repository.nextSortOrder(),
                        createdAt = now,
                        updatedAt = now,
                        inputModality = inputModality.name,
                        taskKind = taskKind.name,
                        outputMode = outputMode.name,
                        outputLanguage = outputLanguage.name,
                        candidateCount = candidateCount,
                        showInActionMenu = showInActionMenu,
                    )
                )
            } else {
                repository.update(
                    currentTemplate.copy(
                        title = title,
                        prompt = prompt,
                        isEnabled = isEnabled,
                        updatedAt = now,
                        inputModality = inputModality.name,
                        taskKind = taskKind.name,
                        outputMode = outputMode.name,
                        outputLanguage = outputLanguage.name,
                        candidateCount = candidateCount,
                        showInActionMenu = showInActionMenu,
                    )
                )
            }
        }
    }

    fun setEnabled(template: GemmaPromptTemplate, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.update(
                template.copy(
                    isEnabled = isEnabled,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteTemplate(template: GemmaPromptTemplate) {
        viewModelScope.launch {
            repository.delete(template)
        }
    }
}
