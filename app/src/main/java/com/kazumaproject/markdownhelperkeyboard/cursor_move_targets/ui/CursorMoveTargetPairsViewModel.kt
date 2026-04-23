package com.kazumaproject.markdownhelperkeyboard.cursor_move_targets.ui

import androidx.lifecycle.ViewModel
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CursorMoveTargetPairsViewModel : ViewModel() {

    private val _targets = MutableStateFlow(
        AppPreference.cursor_move_after_commit_target_pairs_preference
    )
    val targets: StateFlow<List<String>> = _targets.asStateFlow()

    fun addTargetPair(value: String, onResult: (Boolean) -> Unit) {
        val normalized = normalizeTargetPair(value) ?: run {
            onResult(false)
            return
        }
        val current = _targets.value
        if (normalized in current) {
            onResult(false)
            return
        }
        persist(current + normalized)
        onResult(true)
    }

    fun updateTargetPair(currentValue: String, newValue: String, onResult: (Boolean) -> Unit) {
        val normalized = normalizeTargetPair(newValue) ?: run {
            onResult(false)
            return
        }
        val current = _targets.value
        if (normalized != currentValue && normalized in current) {
            onResult(false)
            return
        }
        persist(current.map { if (it == currentValue) normalized else it })
        onResult(true)
    }

    fun deleteTargetPair(target: String) {
        persist(_targets.value.filterNot { it == target })
    }

    fun resetToDefault() {
        persist(AppPreference.defaultCursorMoveAfterCommitTargetPairs())
    }

    private fun persist(values: List<String>) {
        val normalized = values.mapNotNull(::normalizeTargetPair).distinct()
        AppPreference.cursor_move_after_commit_target_pairs_preference = normalized
        _targets.value = normalized
    }

    private fun normalizeTargetPair(value: String): String? {
        val trimmed = value.trim()
        return if (trimmed.length == 2) trimmed else null
    }
}
