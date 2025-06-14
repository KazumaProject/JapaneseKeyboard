package com.kazumaproject.custom_keyboard.data

import androidx.annotation.DrawableRes

sealed class FlickAction {
    /**
     * 文字入力を表現する
     * @param char 入力する文字
     */
    data class Input(val char: String) : FlickAction()

    /**
     * 特殊なアクションの実行を表現する
     * @param action 実行するKeyAction
     * @param drawableResId ポップアップに表示するアイコンのリソースID (nullの場合はラベルが使われることを想定)
     */
    data class Action(
        val action: KeyAction,
        val label: String? = null,
        @DrawableRes val drawableResId: Int? = null
    ) : FlickAction()
}
