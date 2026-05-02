package com.kazumaproject.custom_keyboard.data

/**
 * `KeyVisualStyle` から、描画に使える解決済み余白(dp) を表す。
 *
 * dp 値のまま返すことに注意。
 * - 実機側 (`FlickKeyboardView`) ではキーボードのスケール（keyWidthScalePercent / keyHeightScalePercent）を
 *   かけてから px に変換する必要がある。
 * - エディタ側 (`EditableFlickKeyboardView`) ではそのまま `dpToPx()` で px に変換する。
 *
 * dp のまま返すのは、各画面の「dp→px」変換規則をリゾルバ側に持たせないため。
 */
data class ResolvedKeyMarginDp(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/**
 * キーごとの見た目用情報 (`KeyVisualStyle`) を、描画コードが扱える形に解決するためのリゾルバ。
 *
 * 実機描画 (`FlickKeyboardView`) とエディタ描画 (`EditableFlickKeyboardView`) で
 * 同じ解決ロジックを共有させるためにここに集約している。
 *
 * 既存の default margin 決定ロジック（`STANDARD_FLICK` / 特殊キー / 通常キー など）は
 * 各ビューの責務として残し、リゾルバ側ではあくまで
 * 「`KeyData.visualStyle.margin` が指定された軸だけ override する」役割だけを持つ。
 */
object KeyVisualStyleResolver {

    /**
     * `keyData.visualStyle.margin` を、各方向の default 値（dp）にフォールバックさせて解決する。
     *
     * - 軸ごとに `null` の場合は default にフォールバックする。
     * - すべての軸が `null` の場合は、完全に default 値だけを返す（既存挙動と一致）。
     *
     * @param keyData              対象キー。
     * @param defaultHorizontalDp  左右方向の default 余白 (dp)。
     * @param defaultVerticalDp    上下方向の default 余白 (dp)。
     */
    fun resolveMarginDp(
        keyData: KeyData,
        defaultHorizontalDp: Int,
        defaultVerticalDp: Int
    ): ResolvedKeyMarginDp {
        val margin = keyData.visualStyle.margin
        return ResolvedKeyMarginDp(
            left = margin.leftDp ?: defaultHorizontalDp,
            top = margin.topDp ?: defaultVerticalDp,
            right = margin.rightDp ?: defaultHorizontalDp,
            bottom = margin.bottomDp ?: defaultVerticalDp
        )
    }
}
