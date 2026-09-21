package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.content.SharedPreferences
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType

enum class SplitSlot { MAIN, SUB }
enum class SplitEditPlacement { MAIN, BOTH;
    fun shows(slot: SplitSlot) = this == BOTH || slot == SplitSlot.MAIN
}
enum class SplitCandidatePlacement { BOTH, MAIN, SUB;
    fun shows(slot: SplitSlot) = this == BOTH || name == slot.name
}
data class SplitKeyboardSelection(val type: KeyboardType = KeyboardType.TENKEY, val customStableId: String = "") {
    fun resolved(availableIds: Set<String>): SplitKeyboardSelection =
        if (type == KeyboardType.SPLIT || (type == KeyboardType.CUSTOM && customStableId !in availableIds))
            SplitKeyboardSelection() else this
}
/** Width/height describe the keyboard body; candidate and editing chrome are separate. */
data class SplitPlacement(val x: Float, val y: Float, val widthDp: Float, val heightDp: Float) {
    fun normalized() = copy(x = x.finite(0f).coerceIn(0f, 1f), y = y.finite(1f).coerceIn(0f, 1f),
        widthDp = widthDp.finite(280f).coerceIn(120f, 1200f), heightDp = heightDp.finite(240f).coerceIn(80f, 800f))
    private fun Float.finite(default: Float) = if (isFinite()) this else default
}
class SplitKeyboardSettings(private val prefs: SharedPreferences) {
    fun selection(slot: SplitSlot): SplitKeyboardSelection {
        val type = runCatching { KeyboardType.valueOf(prefs.getString(typeKey(slot), "TENKEY")!!) }.getOrDefault(KeyboardType.TENKEY)
        return SplitKeyboardSelection(if (type == KeyboardType.SPLIT) KeyboardType.TENKEY else type,
            prefs.getString(customKey(slot), "").orEmpty())
    }
    fun saveSelection(slot: SplitSlot, value: SplitKeyboardSelection) {
        require(value.type != KeyboardType.SPLIT)
        prefs.edit().putString(typeKey(slot), value.type.name).putString(customKey(slot), value.customStableId).apply()
    }
    val candidates: SplitCandidatePlacement get() = runCatching {
        SplitCandidatePlacement.valueOf(prefs.getString(CANDIDATES, "BOTH")!!)
    }.getOrDefault(SplitCandidatePlacement.BOTH)
    val editPlacement: SplitEditPlacement get() = runCatching {
        SplitEditPlacement.valueOf(prefs.getString(EDIT_PLACEMENT, "MAIN")!!)
    }.getOrDefault(SplitEditPlacement.MAIN)
    fun hasPlacement(slot: SplitSlot, landscape: Boolean) = prefs.contains("${geometryKey(slot, landscape)}_width")
    fun placement(slot: SplitSlot, landscape: Boolean): SplitPlacement {
        val key = geometryKey(slot, landscape)
        return SplitPlacement(prefs.getFloat("${key}_x", if (slot == SplitSlot.MAIN) 0f else 1f),
            prefs.getFloat("${key}_y", 1f), prefs.getFloat("${key}_width", 280f),
            prefs.getFloat("${key}_height", 240f)).normalized()
    }
    fun savePlacement(slot: SplitSlot, landscape: Boolean, value: SplitPlacement) {
        val key = geometryKey(slot, landscape)
        val safe = value.normalized()
        prefs.edit().putFloat("${key}_x", safe.x).putFloat("${key}_y", safe.y)
            .putFloat("${key}_width", safe.widthDp).putFloat("${key}_height", safe.heightDp).apply()
    }
    companion object {
        const val EDIT_PLACEMENT = "split_keyboard_edit_placement"
        const val CANDIDATES = "split_keyboard_candidates"
        fun typeKey(slot: SplitSlot) = "split_keyboard_${slot.name.lowercase()}_type"
        fun customKey(slot: SplitSlot) = "split_keyboard_${slot.name.lowercase()}_custom"
        private fun geometryKey(slot: SplitSlot, landscape: Boolean) =
            "split_keyboard_${slot.name.lowercase()}_${if (landscape) "landscape" else "portrait"}"
    }
}
