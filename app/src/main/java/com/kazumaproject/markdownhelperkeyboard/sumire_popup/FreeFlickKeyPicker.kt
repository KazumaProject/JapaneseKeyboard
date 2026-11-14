package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.content.Context
import android.content.res.Configuration
import com.google.android.material.color.DynamicColors
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.ModeKeySizing
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
        addView(keyboard,LayoutParams(LayoutParams.MATCH_PARENT,0))
    }
    fun showSelection(keys: List<PopupKeyConfig>, selected: String) {
        val p=AppPreference
        keyboard.applyKeyboardTheme(
            themeMode=p.theme_mode,
            currentNightMode=resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
            isDynamicColorEnabled=DynamicColors.isDynamicColorAvailable(),
            customBgColor=p.custom_theme_bg_color,customKeyColor=p.custom_theme_key_color,
            customSpecialKeyColor=p.custom_theme_special_key_color,customKeyTextColor=p.custom_theme_key_text_color,
            customSpecialKeyTextColor=p.custom_theme_special_key_text_color,
            liquidGlassEnable=p.liquid_glass_preference,customBorderEnable=p.custom_theme_border_enable,
            customBorderColor=p.custom_theme_border_color,liquidGlassKeyAlphaEnable=p.liquid_glass_key_alpha,
            borderWidth=p.custom_theme_border_width,skinId=p.keyboardSkin)
        ModeKeySizing.applySumire(keyboard,KeyboardInputMode.HIRAGANA)
        keyboard.setFlickGuideEnabled(p.sumire_keymap_guide_japanese,allowMultiCharacterLabels=true)
        keyboard.setFlickGuideMaxCodePoints(p.flick_guide_max_characters_preference ?: 1)
        val heightDp=if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE)
            p.keyboard_height_landscape ?: 220 else p.keyboard_height ?: 220
        keyboard.layoutParams=keyboard.layoutParams.apply {height=(heightDp*resources.displayMetrics.density).toInt()}
        val layout=KeyboardDefaultLayouts.createFinalLayout(KeyboardInputMode.HIRAGANA,emptyMap(),
            p.sumire_input_method,SumirePopupDefaults.STYLE,
            KeyboardDefaultLayouts.DeleteKeyFlickSettings(p.delete_key_left_flick_preference,p.delete_key_up_flick_preference,p.delete_key_down_flick_preference),
            p.sumire_flick_rotation_preference,p.sumire_flick_input_mode_preference,p.sumire_mode_key_highlight_preference)
        keyboard.setKeyboard(layout.copyWithKeys(layout.keys.map { key ->
            key.popupMapConfig?.let { original -> key.copy(popupMapConfig=keys.first { it.keyId==original.keyId }) } ?: key
        }),origin=KeyboardLayoutOrigin.BUILT_IN)
        // Selection callbacks still accept only editable labels; keep function keys' normal colors.
        for(index in 0 until keyboard.childCount) keyboard.getChildAt(index).apply {isEnabled=true;alpha=1f}
        keyboard.selectPreviewKey(selected)
    }
}
