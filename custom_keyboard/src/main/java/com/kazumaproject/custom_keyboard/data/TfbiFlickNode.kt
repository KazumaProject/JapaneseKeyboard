package com.kazumaproject.custom_keyboard.data

import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

/**
 * ★ 新規追加
 * コントローラーが内部で保持する状態。
 * Inputノードは、この状態に遷移するよう要求できる。
 */
enum class KeyMode {
    NORMAL,
    DAKUTEN,
    HANDAKUTEN
}

sealed class TfbiFlickNode {
    /**
     * 終端ノード（文字入力を表す）
     * @param char 入力される文字
     * @param triggersMode この文字が入力された後、
     * コントローラーが遷移すべき次の状態（nullなら状態維持）
     */
    data class Input(
        val char: String,
        val triggersMode: KeyMode? = null // ★ 変更点
    ) : TfbiFlickNode()

    /**
     * 中間ノード（サブメニューを表す）
     */
    data class SubMenu(
        val nextMap: Map<TfbiFlickDirection, TfbiFlickNode>,
        val label: String? = null,
        val cancelOnTap: Boolean = false
    ) : TfbiFlickNode()

    /**
     * 複数の状態を持つキーを定義するノード。
     */
    data class StatefulKey(
        val normalMap: Map<TfbiFlickDirection, TfbiFlickNode>,
        val dakutenMap: Map<TfbiFlickDirection, TfbiFlickNode>? = null,
        val handakutenMap: Map<TfbiFlickDirection, TfbiFlickNode>? = null,
        val label: String // "か" など（通常時のラベル）
    ) : TfbiFlickNode()
}
