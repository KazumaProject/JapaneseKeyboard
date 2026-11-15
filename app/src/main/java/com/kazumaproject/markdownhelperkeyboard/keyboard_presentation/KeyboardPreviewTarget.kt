package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import android.content.Context
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_selection.getKeyboardDisplayName

/** Identity is independent of display names and ordering. Viewing a target never saves it. */
internal data class KeyboardPreviewTarget(val type: KeyboardType, val customStableId: String? = null, val customLayoutId: Long? = null) {
    val id: String get() = if (type == KeyboardType.CUSTOM) "CUSTOM:${customStableId ?: customLayoutId}" else type.name
    fun name(context: Context, layouts: List<CustomKeyboardLayout>): String =
        if (type == KeyboardType.CUSTOM) layouts.firstOrNull { matches(it) }?.name
            ?: context.getKeyboardDisplayName(type) else context.getKeyboardDisplayName(type)
    fun matches(layout: CustomKeyboardLayout) = if (!customStableId.isNullOrBlank())
        layout.stableId == customStableId else layout.layoutId == customLayoutId
}

internal fun keyboardPreviewTargets(order: List<KeyboardType>, layouts: List<CustomKeyboardLayout>): List<KeyboardPreviewTarget> =
    order.ifEmpty { listOf(KeyboardType.TENKEY) }.distinct().flatMap { type ->
        if (type == KeyboardType.CUSTOM && layouts.isNotEmpty()) layouts.map {
            KeyboardPreviewTarget(type, it.stableId.takeIf(String::isNotBlank), it.layoutId)
        } else listOf(KeyboardPreviewTarget(type))
    }

internal suspend fun currentKeyboardPreviewTarget(context: Context): KeyboardPreviewTarget {
    val dependencies = dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, KeyboardPreviewDependencies::class.java)
    val layouts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dependencies.keyboards().getLayoutsNotFlow() }
    val prefs = com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
    val order = prefs.keyboard_order
    val type = if (prefs.save_last_used_keyboard_enable_preference == true)
        order.getOrNull(prefs.save_last_used_keyboard_position_preference ?: 0) ?: order.firstOrNull()
        else order.firstOrNull()
    val resolved = type ?: KeyboardType.TENKEY
    val custom = if (resolved == KeyboardType.CUSTOM)
        com.kazumaproject.markdownhelperkeyboard.ime_service.resolveInitialCustomKeyboardSelection(layouts,
            prefs.remember_last_custom_keyboard_preference == true, prefs.last_used_custom_keyboard_stable_id)
            ?.let { layouts[it.index] } else null
    return KeyboardPreviewTarget(resolved, custom?.stableId, custom?.layoutId)
}

internal suspend fun keyboardPreviewTargetForFamily(context: Context, type: KeyboardType): KeyboardPreviewTarget {
    if (type != KeyboardType.CUSTOM) return KeyboardPreviewTarget(type)
    val dependencies = dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, KeyboardPreviewDependencies::class.java)
    val layouts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dependencies.keyboards().getLayoutsNotFlow() }
    val prefs = com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
    val custom = com.kazumaproject.markdownhelperkeyboard.ime_service.resolveInitialCustomKeyboardSelection(layouts,
        prefs.remember_last_custom_keyboard_preference == true, prefs.last_used_custom_keyboard_stable_id)?.let { layouts[it.index] }
    return KeyboardPreviewTarget(type, custom?.stableId, custom?.layoutId)
}
