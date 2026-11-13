package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.content.Context
import android.widget.LinearLayout
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

/** The real Sumire renderer, placed in selection-only mode before any keys are created. */
class FreeFlickKeyPicker(context: Context) : LinearLayout(context) {
    val keyboard = FlickKeyboardView(context)
    var onKeySelected: (String) -> Unit = {}
    init {
        orientation=VERTICAL
        contentDescription=context.getString(R.string.free_flick_choose_key)
        keyboard.setKeySelectionMode(SumirePopupDefaults.rows.keys) { onKeySelected(it) }
        addView(keyboard,LayoutParams(LayoutParams.MATCH_PARENT,(208*resources.displayMetrics.density).toInt()))
    }
    fun showSelection(keys: List<PopupKeyConfig>, selected: String) {
        val p=AppPreference
        keyboard.applyKeyboardTheme(p.theme_mode,resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK,
            false,p.custom_theme_bg_color,p.custom_theme_key_color,p.custom_theme_special_key_color,
            p.custom_theme_key_text_color,p.custom_theme_special_key_text_color,false,p.custom_theme_border_enable,
            p.custom_theme_border_color,255,p.custom_theme_border_width,p.keyboardSkin)
        keyboard.setDefaultTextSize(p.flick_key_text_size_sp ?: 16f)
        val layout=KeyboardDefaultLayouts.createFinalLayout(KeyboardInputMode.HIRAGANA,emptyMap(),
            p.sumire_input_method ?: "toggle",SumirePopupDefaults.STYLE,highlightSelectedMode=false)
        keyboard.setKeyboard(layout.copyWithKeys(layout.keys.map { key ->
            key.popupMapConfig?.let { original -> key.copy(popupMapConfig=keys.first { it.keyId==original.keyId }) } ?: key
        }),origin=KeyboardLayoutOrigin.BUILT_IN)
        keyboard.selectPreviewKey(selected)
    }
}
