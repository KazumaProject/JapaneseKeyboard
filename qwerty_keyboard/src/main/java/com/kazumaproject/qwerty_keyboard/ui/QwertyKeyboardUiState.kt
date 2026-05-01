package com.kazumaproject.qwerty_keyboard.ui

import com.kazumaproject.core.data.qwerty.CapsLockState
import com.kazumaproject.core.domain.state.QWERTYMode

/**
 * QWERTYKeyboardView の現在の表示状態を表す不変スナップショット。
 *
 * 通常モードと Floating mode のように、2 つの QWERTYKeyboardView
 * インスタンス間で状態を非破壊的にコピーするために利用する。
 *
 * @property qwertyMode 現在の QWERTY 表示モード (Default / Number / Symbol)
 * @property capsLockState Shift / CapsLock の状態
 * @property romajiMode ローマ字入力モードかどうか
 * @property enterKeyText Return キーに表示するラベル
 * @property spaceKeyText Space キーに表示するラベル
 * @property showRomajiEnglishSwitchKey ローマ字 / 英語 切替キーの可視状態
 */
data class QwertyKeyboardUiState(
    val qwertyMode: QWERTYMode,
    val capsLockState: CapsLockState,
    val romajiMode: Boolean,
    val enterKeyText: CharSequence,
    val spaceKeyText: CharSequence,
    val showRomajiEnglishSwitchKey: Boolean
)
