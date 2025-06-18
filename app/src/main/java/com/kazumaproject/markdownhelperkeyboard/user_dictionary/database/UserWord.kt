package com.kazumaproject.markdownhelperkeyboard.user_dictionary.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ユーザーが登録した単語を格納するテーブル
 *
 * @param id 主キー
 * @param word 単語
 * @param reading 読み
 * @param posIndex 品詞インデックス
 * @param posScore 品詞スコア
 */
@Entity(
    tableName = "user_word",
    indices = [Index(value = ["reading"])]
)
data class UserWord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val word: String,
    val reading: String,
    val posIndex: Int,
    val posScore: Int,
)
