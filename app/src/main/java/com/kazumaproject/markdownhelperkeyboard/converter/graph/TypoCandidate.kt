package com.kazumaproject.markdownhelperkeyboard.converter.graph

enum class TypoCategory(val penalty: Int) {
    Exact(0),
    TapKeyInFlick(1),      // 同一キー内で方向ミス（か⇄き⇄く⇄け⇄こ など）
    DistanceNear(1),       // 近距離キー誤タップ（同方向）
    DistanceMiddle(2),     // 中距離キー誤タップ（同方向）
    DistanceFar(7),        // 遠距離キー誤タップ（同方向）
}

data class TypoCandidate(
    val ch: Char,
    val category: TypoCategory,
) {
    val penalty: Int get() = category.penalty
}
