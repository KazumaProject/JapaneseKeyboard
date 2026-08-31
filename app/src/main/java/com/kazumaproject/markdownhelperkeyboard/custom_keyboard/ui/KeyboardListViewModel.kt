package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.repository.CustomKeyboardDeleteImpact
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 削除フローで UI に通知するイベント。
 *
 * - [BlockedInUse]      : カスタムキーボードが入力方法として選択中で削除不可。
 * - [ConfirmReferenced] : 削除対象が他キーから参照されている。警告ダイアログを出す。
 * - [Deleted]           : 削除完了。
 */
sealed class KeyboardDeleteEvent {
    data class BlockedInUse(val layoutId: Long) : KeyboardDeleteEvent()
    data class ConfirmReferenced(val impact: CustomKeyboardDeleteImpact) : KeyboardDeleteEvent()
    data class Deleted(val layoutId: Long) : KeyboardDeleteEvent()
}

data class KeyboardLayoutListItem(
    val layout: CustomKeyboardLayout,
    val isInUse: Boolean,
)

@HiltViewModel
class KeyboardListViewModel @Inject constructor(
    private val repository: KeyboardRepository,
    private val appPreference: AppPreference,
) : ViewModel() {

    private val isCustomKeyboardSelected = MutableStateFlow(isCustomKeyboardInUse())

    val layoutItems: StateFlow<List<KeyboardLayoutListItem>> = combine(
        repository.getLayouts(),
        isCustomKeyboardSelected,
    ) { layouts, isInUse ->
        layouts.map { layout -> KeyboardLayoutListItem(layout, isInUse) }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _deleteEvents = Channel<KeyboardDeleteEvent>(Channel.BUFFERED)
    val deleteEvents: Flow<KeyboardDeleteEvent> = _deleteEvents.receiveAsFlow()

    /**
     * ユーザーが削除ボタンを押したときの起点。
     * - カスタムキーボードが入力方法として選択中なら [KeyboardDeleteEvent.BlockedInUse] を流す。
     * - 参照が無ければそのまま削除し、[KeyboardDeleteEvent.Deleted] を流す。
     * - 参照があれば [KeyboardDeleteEvent.ConfirmReferenced] を流して、UI に
     *   警告ダイアログを表示させる。ユーザーが了承した場合のみ
     *   [confirmDeleteWithReferences] が呼ばれる。
     */
    fun requestDeleteLayout(id: Long) {
        viewModelScope.launch {
            if (blockDeletionWhenInUse(id)) return@launch

            val impact = runCatching { repository.getDeleteImpactForLayout(id) }
                .onFailure { Timber.e(it, "getDeleteImpactForLayout(%s) failed", id) }
                .getOrNull()
            if (impact == null || !impact.hasReferences) {
                repository.deleteLayoutConfirmed(id)
                _deleteEvents.send(KeyboardDeleteEvent.Deleted(id))
            } else {
                Timber.w(
                    "requestDeleteLayout: layoutId=%s has %s MoveToCustomKeyboard references; awaiting user confirmation",
                    id, impact.references.size
                )
                _deleteEvents.send(KeyboardDeleteEvent.ConfirmReferenced(impact))
            }
        }
    }

    /**
     * 警告ダイアログでユーザーが「それでも削除」を選んだあとに呼ぶ。
     */
    fun confirmDeleteWithReferences(id: Long) {
        viewModelScope.launch {
            if (blockDeletionWhenInUse(id)) return@launch

            repository.deleteLayoutConfirmed(id)
            _deleteEvents.send(KeyboardDeleteEvent.Deleted(id))
        }
    }

    /**
     * @deprecated 参照チェックを行わず無条件に削除する。互換のために残しているが、
     * 通常は [requestDeleteLayout] を使うこと。
     */
    @Deprecated("Use requestDeleteLayout to enforce reference check.")
    fun deleteLayout(id: Long) {
        viewModelScope.launch {
            if (blockDeletionWhenInUse(id)) return@launch

            repository.deleteLayoutConfirmed(id)
        }
    }

    fun refreshCustomKeyboardUsage() {
        isCustomKeyboardSelected.value = isCustomKeyboardInUse()
    }

    private fun isCustomKeyboardInUse(): Boolean =
        KeyboardType.CUSTOM in appPreference.keyboard_order

    private suspend fun blockDeletionWhenInUse(layoutId: Long): Boolean {
        if (!isCustomKeyboardInUse()) return false

        isCustomKeyboardSelected.value = true
        _deleteEvents.send(KeyboardDeleteEvent.BlockedInUse(layoutId))
        return true
    }

    fun duplicateLayout(id: Long) {
        viewModelScope.launch {
            repository.duplicateLayout(id)
        }
    }

    // ★追加: 並び順を永続化
    fun updateLayoutOrder(layoutIdsInDisplayOrder: List<Long>) {
        viewModelScope.launch {
            repository.updateLayoutOrder(layoutIdsInDisplayOrder)
        }
    }
}
