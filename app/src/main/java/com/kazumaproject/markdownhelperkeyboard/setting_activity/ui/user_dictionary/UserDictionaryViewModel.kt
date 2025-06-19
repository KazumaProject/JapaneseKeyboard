package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.user_dictionary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserDictionaryViewModel @Inject constructor(
    private val repository: UserDictionaryRepository, application: Application
) : AndroidViewModel(application) {

    val allWords: LiveData<List<UserWord>> = repository.allWords

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
        repository.insert(userWord)
    }

    /**
     * インポート機能のために複数の単語を一度に登録する
     * @param words 登録する単語のリスト
     */
    fun insertAll(words: List<UserWord>) = viewModelScope.launch {
        repository.insertAll(words)
    }

    fun update(userWord: UserWord) = viewModelScope.launch {
        repository.update(userWord)
    }

    fun delete(id: Int) = viewModelScope.launch {
        repository.delete(id)
    }

    fun deleteAll() = viewModelScope.launch {
        repository.deleteAll()
    }

    /**
     * readingの前方一致で単語を検索する
     * @param prefix 検索したい読みの文字列
     * @return 検索結果のLiveData。UI層でこれをobserveしてリストを更新する
     */
    fun searchByReadingPrefix(prefix: String): LiveData<List<UserWord>> {
        return repository.searchByReadingPrefix(prefix)
    }
}

