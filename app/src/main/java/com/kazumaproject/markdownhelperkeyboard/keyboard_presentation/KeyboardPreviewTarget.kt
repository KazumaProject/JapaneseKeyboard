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

/** Last real selection in this process. Trial typing never publishes here. */
internal object KeyboardPreviewSelection {
    private val state = kotlinx.coroutines.flow.MutableStateFlow<KeyboardPreviewTarget?>(null)
    val current: kotlinx.coroutines.flow.StateFlow<KeyboardPreviewTarget?> = state
    fun publish(target: KeyboardPreviewTarget?) { state.value = target }
}

internal fun resolveHomeKeyboardPreviewTarget(
    order: List<KeyboardType>, layouts: List<CustomKeyboardLayout>,
    rememberFamily: Boolean, savedPosition: Int, rememberCustom: Boolean, savedCustomId: String?,
    live: KeyboardPreviewTarget? = KeyboardPreviewSelection.current.value,
): KeyboardPreviewTarget {
    val enabled = order.ifEmpty { listOf(KeyboardType.TENKEY) }
    if (live != null && live.type in enabled &&
        (live.type != KeyboardType.CUSTOM || layouts.any(live::matches))) return live
    val type = if (rememberFamily) enabled.getOrNull(savedPosition) ?: enabled.first() else enabled.first()
    val custom = if (type == KeyboardType.CUSTOM)
        com.kazumaproject.markdownhelperkeyboard.ime_service.resolveInitialCustomKeyboardSelection(
            layouts, rememberCustom, savedCustomId)?.let { layouts[it.index] } else null
    return KeyboardPreviewTarget(type, custom?.stableId?.takeIf(String::isNotBlank), custom?.layoutId)
}

internal suspend fun currentKeyboardPreviewTarget(context: Context): KeyboardPreviewTarget {
    val dependencies = dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, KeyboardPreviewDependencies::class.java)
    val layouts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dependencies.keyboards().getLayoutsNotFlow() }
    val prefs = com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
    return resolveHomeKeyboardPreviewTarget(prefs.keyboard_order, layouts,
        prefs.save_last_used_keyboard_enable_preference == true, prefs.save_last_used_keyboard_position_preference ?: 0,
        prefs.remember_last_custom_keyboard_preference == true, prefs.last_used_custom_keyboard_stable_id)
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
