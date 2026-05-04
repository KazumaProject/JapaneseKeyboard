package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.repository.CustomKeyboardDeleteImpact
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 削除フローで UI に通知するイベント。
 *
 * - [ConfirmReferenced] : 削除対象が他キーから参照されている。警告ダイアログを出す。
 * - [Deleted]           : 削除完了。
 */
sealed class KeyboardDeleteEvent {
    data class ConfirmReferenced(val impact: CustomKeyboardDeleteImpact) : KeyboardDeleteEvent()
    data class Deleted(val layoutId: Long) : KeyboardDeleteEvent()
}

@HiltViewModel
class KeyboardListViewModel @Inject constructor(
    private val repository: KeyboardRepository
) : ViewModel() {

    val layouts: StateFlow<List<CustomKeyboardLayout>> = repository.getLayouts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _deleteEvents = Channel<KeyboardDeleteEvent>(Channel.BUFFERED)
    val deleteEvents: Flow<KeyboardDeleteEvent> = _deleteEvents.receiveAsFlow()

    /**
     * ユーザーが削除ボタンを押したときの起点。
     * - 参照が無ければそのまま削除し、[KeyboardDeleteEvent.Deleted] を流す。
     * - 参照があれば [KeyboardDeleteEvent.ConfirmReferenced] を流して、UI に
     *   警告ダイアログを表示させる。ユーザーが了承した場合のみ
     *   [confirmDeleteWithReferences] が呼ばれる。
     */
    fun requestDeleteLayout(id: Long) {
        viewModelScope.launch {
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
            repository.deleteLayoutConfirmed(id)
        }
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
