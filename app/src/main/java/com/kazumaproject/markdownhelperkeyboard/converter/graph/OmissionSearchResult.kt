package com.kazumaproject.markdownhelperkeyboard.converter.graph

/**
 * 省略検索の結果を格納するデータクラス。
 * @param yomi 辞書で見つかった読み
 * @param omissionOccurred 文字変化（例: 'か' -> 'が'）が発生したか
 */
data class OmissionSearchResult(val yomi: String, val omissionOccurred: Boolean)
