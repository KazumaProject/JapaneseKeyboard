package com.kazumaproject.markdownhelperkeyboard.user_template.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.repository.UserTemplateRepository
import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserTemplateViewModel @Inject constructor(
    private val repository: UserTemplateRepository,
    application: Application // Add Application to constructor
) : AndroidViewModel(application) { // Extend AndroidViewModel

    val allTemplates: LiveData<List<UserTemplate>> = repository.allTemplates

    // [ADD] Load the Part-of-Speech list from resources
    val posList: List<String> =
        application.resources.getStringArray(com.kazumaproject.core.R.array.parts_of_speech)
            .toList()

    // [ADD] Define constants for default values
    companion object {
        const val DEFAULT_POS = "名詞"
        const val DEFAULT_SCORE = 4000
    }

    // [ADD] Get the index for the default Part-of-Speech
    val defaultPosIndex: Int = posList.indexOf(DEFAULT_POS).takeIf { it != -1 } ?: 0

    fun insert(userTemplate: UserTemplate) = viewModelScope.launch {
        repository.insert(userTemplate)
    }

    fun insertAll(templates: List<UserTemplate>) = viewModelScope.launch {
        repository.insertAll(templates)
    }

    fun update(userTemplate: UserTemplate) = viewModelScope.launch {
        repository.update(userTemplate)
    }

    fun delete(id: Int) = viewModelScope.launch {
        repository.delete(id)
    }

    fun deleteAll() = viewModelScope.launch {
        repository.deleteAll()
    }
}
