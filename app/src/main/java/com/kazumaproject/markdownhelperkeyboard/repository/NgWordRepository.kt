package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWordDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NgWordRepository @Inject constructor(
    private val dao: NgWordDao
) {
    /** 全件取得（降順） */
    suspend fun getAllNgWords(): List<NgWord> =
        dao.getAll()

    /** yomi による検索 */
    suspend fun getNgWordsByYomi(yomi: String): List<NgWord> =
        dao.getByYomi(yomi)

    /**
     * 新規追加。
     * @return 挿入に成功したら true（既存と同一 yomi＋tango があれば false）
     */
    suspend fun addNgWord(yomi: String, tango: String): Boolean {
        val rowId = dao.insert(NgWord(yomi = yomi, tango = tango))
        return rowId != -1L
    }

    /** 削除 */
    suspend fun removeNgWord(ngWord: NgWord) =
        dao.delete(ngWord)

    /** 全削除 */
    suspend fun clearAll() =
        dao.deleteAll()

    /** yomi＋tango の組み合わせが既に存在するか */
    suspend fun exists(yomi: String, tango: String): Boolean =
        dao.find(yomi, tango) != null

    suspend fun getCommonPrefixes(searchYomi: String): List<NgWord> =
        dao.findCommonPrefixes(searchYomi)
}
