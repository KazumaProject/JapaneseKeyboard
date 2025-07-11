package com.kazumaproject.markdownhelperkeyboard.ng_word.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord
import com.kazumaproject.markdownhelperkeyboard.repository.NgWordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NgWordViewModel @Inject constructor(
    private val repo: NgWordRepository
) : ViewModel() {

    private val _allNgWords = MutableLiveData<List<NgWord>>()
    val allNgWords: LiveData<List<NgWord>> = _allNgWords

    init {
        loadAll()
    }

    private fun loadAll() {
        viewModelScope.launch {
            _allNgWords.value = repo.getAllNgWords()
        }
    }

    fun insert(yomi: String, tango: String) {
        viewModelScope.launch {
            repo.addNgWord(yomi, tango)
            loadAll()
        }
    }

    fun insertAll(list: List<NgWord>) {
        viewModelScope.launch {
            list.forEach { repo.addNgWord(it.yomi, it.tango) }
            loadAll()
        }
    }

    fun update(item: NgWord) {
        viewModelScope.launch {
            repo.removeNgWord(item)
            repo.addNgWord(item.yomi, item.tango)
            loadAll()
        }
    }

    fun delete(item: NgWord) {
        viewModelScope.launch {
            repo.removeNgWord(item)
            loadAll()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            repo.clearAll()
            loadAll()
        }
    }
}
