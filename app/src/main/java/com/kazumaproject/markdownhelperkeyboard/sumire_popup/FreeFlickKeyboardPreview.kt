package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.content.Context
import android.content.res.Configuration
import android.view.MotionEvent
import android.widget.FrameLayout
import com.google.android.material.color.DynamicColors
import com.kazumaproject.core.data.popup.FlickPopupViewStyleSet
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts.DeleteKeyFlickSettings
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.ModeKeySizing
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.*
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyPlacementOverrideEntity

/** Runs the actual IME renderer and popup controllers; output stays inside the settings preview. */
internal class FreeFlickKeyboardPreview(context: Context) : FrameLayout(context) {
    val keyboard = FlickKeyboardView(context)
    var onText: (String) -> Unit = {}
    private var actions = emptyList<SumireSpecialKeyActionOverrideEntity>()
    private var placements = emptyList<SumireSpecialKeyPlacementOverrideEntity>()
    var previewLayout: KeyboardLayout? = null
        private set

    init {
        addView(keyboard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        keyboard.setOnKeyboardActionListener(object : FlickKeyboardView.OnKeyboardActionListener {
            override fun onPress(action: KeyAction) = Unit
            override fun onAction(action: KeyAction, isFlick: Boolean) { if (action is KeyAction.Text) onText(action.text) }
            override fun onActionLongPress(action: KeyAction) = Unit
            override fun onActionUpAfterLongPress(action: KeyAction) = Unit
            override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
            override fun onFlickActionLongPress(action: KeyAction) = Unit
            override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
        })
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        return try { super.dispatchTouchEvent(event) } finally {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }

    fun setSpecialKeyOverrides(actionOverrides: List<SumireSpecialKeyActionOverrideEntity>,
        placementOverrides: List<SumireSpecialKeyPlacementOverrideEntity>) {
        if (previewLayout != null && actions == actionOverrides && placements == placementOverrides) return
        actions = actionOverrides; placements = placementOverrides
        refresh()
    }

    fun refresh() {
        val p = AppPreference
        val mode = KeyboardInputMode.HIRAGANA
        val layoutType = p.sumire_input_method
        keyboard.applyKeyboardTheme(
            themeMode = p.theme_mode,
            currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
            isDynamicColorEnabled = DynamicColors.isDynamicColorAvailable(),
            customBgColor = p.custom_theme_bg_color, customKeyColor = p.custom_theme_key_color,
            customSpecialKeyColor = p.custom_theme_special_key_color, customKeyTextColor = p.custom_theme_key_text_color,
            customSpecialKeyTextColor = p.custom_theme_special_key_text_color,
            liquidGlassEnable = p.liquid_glass_preference, customBorderEnable = p.custom_theme_border_enable,
            customBorderColor = p.custom_theme_border_color, liquidGlassKeyAlphaEnable = p.liquid_glass_key_alpha,
            borderWidth = p.custom_theme_border_width, skinId = p.keyboardSkin
        )
        ModeKeySizing.applySumire(keyboard, mode)
        keyboard.setFlickGuideEnabled(p.sumire_keymap_guide_japanese, allowMultiCharacterLabels = true)
        keyboard.setFlickGuideMaxCodePoints(p.flick_guide_max_characters_preference ?: 1)
        keyboard.setPopupMapOptions(FreeFlickPreferences.read(context))
        keyboard.setFlickSensitivityValue(p.flick_sensitivity_preference ?: 100)
        keyboard.setFlickThresholdShape(com.kazumaproject.core.domain.flick.FlickThresholdShape.fromPreferenceValue(p.flick_threshold_shape_preference))
        keyboard.setLongPressTimeout((p.long_press_timeout_preference ?: 300).toLong())
        keyboard.setKeyPopupVisibility(p.sumire_show_flick_popup_preference, p.sumire_show_long_press_popup_preference)
        fun style(scale: Int, size: Float) = PopupViewStyle(
            if (p.keyboardSkin == KeyboardSkinId.DEFAULT) scale else 100, size,
            p.key_popup_background_color.takeIf { p.key_popup_use_custom_color },
            p.key_popup_text_color.takeIf { p.key_popup_use_custom_color }, p.keyboardSkin
        )
        keyboard.applyPopupViewStyleSet(FlickPopupViewStyleSet(
            style(p.flick_directional_popup_size_scale_percent ?: 100, p.flick_directional_popup_text_size_sp ?: 28f),
            style(p.flick_cross_popup_size_scale_percent ?: 100, p.flick_cross_popup_text_size_sp ?: 18f),
            style(p.flick_standard_popup_size_scale_percent ?: 100, p.flick_standard_popup_text_size_sp ?: 19f),
            style(p.flick_tfbi_popup_size_scale_percent ?: 100, p.flick_tfbi_popup_text_size_sp ?: 20f)
        ))
        val repository = SumirePopupRepository(context)
        val base = KeyboardDefaultLayouts.createFinalLayout(mode, emptyMap(), layoutType, SumirePopupDefaults.STYLE,
            DeleteKeyFlickSettings(p.delete_key_left_flick_preference, p.delete_key_up_flick_preference, p.delete_key_down_flick_preference),
            p.sumire_flick_rotation_preference, p.sumire_flick_input_mode_preference, p.sumire_mode_key_highlight_preference)
        val mapped = base.copyWithKeys(base.keys.map { key ->
            key.popupMapConfig?.let { key.copy(popupMapConfig = repository.load(it.keyId)) } ?: key
        })
        val metadata = KeyActionMapper.getDisplayActions(context).map {
            SumireSpecialKeyActionDisplayMetadata(it.action, it.displayName, it.iconResId)
        }
        val displayed = SumireSpecialKeyActionDisplayOverrideApplier.apply(mapped, layoutType, mode.name, actions, metadata)
        val layout = SumireSpecialKeyPlacementOverrideApplier.apply(displayed, layoutType, mode.name, placements)
        keyboard.setSumireSpecialKeyActionResolver(SumireSpecialKeyActionResolver(actions)::resolve, layoutType, mode.name)
        keyboard.setKeyboard(layout, origin = KeyboardLayoutOrigin.BUILT_IN)
        previewLayout = layout
        val heightDp = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            p.keyboard_height_landscape ?: 220 else p.keyboard_height ?: 220
        layoutParams?.let { if (it.height != (heightDp * resources.displayMetrics.density).toInt()) {
            it.height = (heightDp * resources.displayMetrics.density).toInt(); layoutParams = it
        } }
        (parent as? FrameLayout)?.let { frame ->
            frame.layoutParams = frame.layoutParams.apply { height = (heightDp * resources.displayMetrics.density).toInt() }
        }
    }
}
