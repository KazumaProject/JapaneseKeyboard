package com.kazumaproject.custom_keyboard.data

import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

sealed class TfbiFlickNode {
    /**
     * 終端ノード（文字入力を表す）
     * @param char 入力される文字
     */
    data class Input(
        val char: String) : TfbiFlickNode()

    /**
     * 中間ノード（サブメニューを表す）
     * @param nextMap このノードがトリガーされた時に展開される新しいマップ
     * @param label このサブメニューをUIに表示する際のテキスト（省略時はnextMapのTAPから推論）
     */
    data class SubMenu(
        val nextMap: Map<TfbiFlickDirection, TfbiFlickNode>,
        val label: String? = null
    ) : TfbiFlickNode()
}
