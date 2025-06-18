package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.user_dictionary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWordDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserDictionaryViewModel @Inject constructor(
    private val userWordDao: UserWordDao,
    application: Application
) : AndroidViewModel(application) {

    val allWords: LiveData<List<UserWord>> = userWordDao.getAll()

    // 品詞リストをリソースから読み込む
    val posList: List<String> =
        application.resources.getStringArray(com.kazumaproject.core.R.array.parts_of_speech)
            .toList()

    // デフォルト値の定数を定義
    companion object {
        const val DEFAULT_POS = "名詞"
        const val DEFAULT_SCORE = 4000
    }

    // デフォルト品詞のインデックスを取得
    val defaultPosIndex: Int = posList.indexOf(DEFAULT_POS).takeIf { it != -1 } ?: 0


    fun insert(userWord: UserWord) = viewModelScope.launch {
        userWordDao.insert(userWord)
    }

    fun update(userWord: UserWord) = viewModelScope.launch {
        userWordDao.update(userWord)
    }

    fun delete(id: Int) = viewModelScope.launch {
        userWordDao.delete(id)
    }
}
