package com.kazumaproject.markdownhelperkeyboard.user_dictionary

object PosMapper {
    // 名詞のIDをデフォルト値として定義
    private const val DEFAULT_CONTEXT_ID = (1851).toShort()

    /**
     * ユーザー辞書の品詞インデックス(posIndex)に対応する文脈ID(l/r)を返す。
     * @param posIndex ユーザーが単語登録時に選択した品詞のインデックス
     * @return 対応する文脈ID
     */
    fun getContextIdForPos(posIndex: Int): Short {
        return when (posIndex) {
            0 -> 1851   // 名詞
            1 -> 578    // 動詞
            2 -> 2194   // 形容詞
            3 -> 12     // 副詞
            4 -> 29     // 助動詞
            5 -> 433   // 助詞
            6 -> 2589   // 感動詞
            7 -> 2591   // 接続詞
            8 -> 2594   // 接頭詞
            9 -> 2642   // 記号
            10 -> 2657  // 連体詞
            11 -> 1     //11 (その他)
            else -> DEFAULT_CONTEXT_ID
        }

    }
}
