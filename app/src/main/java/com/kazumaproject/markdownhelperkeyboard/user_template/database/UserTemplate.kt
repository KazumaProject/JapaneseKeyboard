package com.kazumaproject.markdownhelperkeyboard.user_template.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ユーザーが登録した定型文を格納するテーブル
 */
@Entity(
    tableName = "user_template",
    indices = [Index(value = ["reading"]),
        Index(
            value = ["word", "reading"],
            unique = true
        )]
)
data class UserTemplate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val word: String, // 定型文そのもの
    val reading: String, // 定型文を呼び出すための「読み」
    val posIndex: Int,
    val posScore: Int,
)
