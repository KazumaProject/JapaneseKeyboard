package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.core.os.bundleOf
import androidx.core.content.ContextCompat
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.CommonPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.CollapsiblePreferenceCategory
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.LegacyPreferenceResultFilter
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.conditionVisible
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.navigateSafely
import androidx.preference.PreferenceScreen
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.color.DynamicColors
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class KeyboardThemeFragment : CommonPreferenceFragment() {

    companion object {
        // System Theme Keys
        private const val PREF_KEY_DEFAULT = "theme_default"
        private const val PREF_KEY_ROUND_CORNER = "round_corner_keyboard_preference"
        private const val PREF_KEY_POPUP_USE_CUSTOM_COLOR =
            "key_popup_use_custom_color_preference"
        private const val PREF_KEY_POPUP_BACKGROUND_COLOR =
            "key_popup_background_color_preference"
        private const val PREF_KEY_POPUP_TEXT_COLOR =
            "key_popup_text_color_preference"

        // Liquid Glass Keys
        private const val PREF_KEY_LIQUID_GLASS = "liquid_glass_preference"
        private const val PREF_KEY_LIQUID_GLASS_BLUR = "liquid_glass_blur_preference"
        private const val PREF_KEY_LIQUID_GLASS_KEY_ALPHA = "liquid_glass_key_alpha_preference"

        // Custom Theme Keys
        private const val PREF_KEY_CUSTOM = "theme_custom"
        private const val PREF_KEY_CUSTOM_BG = "theme_custom_bg_color"
        private const val PREF_KEY_CUSTOM_KEY = "theme_custom_key_color"
        private const val PREF_KEY_CUSTOM_SPECIAL_KEY = "theme_custom_special_key_color"
        private const val PREF_KEY_CUSTOM_TEXT = "theme_custom_key_text_color"
        private const val PREF_KEY_CUSTOM_SPECIAL_TEXT = "theme_custom_special_key_text_color"
        private const val PREF_KEY_CUSTOM_SHORTCUT_ICON =
            "theme_custom_shortcut_icon_color"

        // Custom Border Keys
        private const val PREF_KEY_CUSTOM_BORDER_ENABLE = "theme_custom_border_enable"
        private const val PREF_KEY_CUSTOM_BORDER_COLOR = "theme_custom_border_color"
        private const val PREF_KEY_CUSTOM_BORDER_WIDTH = "theme_custom_border_width"

        // Custom Input Text Keys
        private const val CATEGORY_KEY_CUSTOM_INPUT = "category_custom_input"
        private const val PREF_KEY_CUSTOM_INPUT_ENABLE = "theme_custom_input_color_enable"
        private const val PREF_KEY_CUSTOM_PRE_EDIT_BG = "theme_custom_pre_edit_bg_color"
        private const val PREF_KEY_CUSTOM_PRE_EDIT_TEXT = "theme_custom_pre_edit_text_color"
        private const val PREF_KEY_CUSTOM_POST_EDIT_BG = "theme_custom_post_edit_bg_color"
        private const val PREF_KEY_CUSTOM_POST_EDIT_TEXT = "theme_custom_post_edit_text_color"
        private const val PREF_KEY_LOCAL_FONT = "local_font_settings_preference"

        // Modes
        private const val MODE_DEFAULT = "default"
        private const val MODE_CUSTOM = "custom"
    }

    override fun createPreferenceHierarchy(context: Context): PreferenceScreen {
        val screen = preferenceManager.createPreferenceScreen(context)

        // -------------------------------------------------------
        // System Category
        // -------------------------------------------------------
        val systemCategory = PreferenceCategory(context).apply {
            title = context.getString(R.string.theme_category_system)
        }
        screen.addPreference(systemCategory)

        // Round Corner Preference
        val roundCornerPref = SwitchPreferenceCompat(context).apply {
            key = PREF_KEY_ROUND_CORNER
            title = context.getString(R.string.pref_round_corner_keyboard_title)
            summary = context.getString(R.string.pref_round_corner_keyboard_summary)
            setDefaultValue(false)
        }
        systemCategory.addPreference(roundCornerPref)

        // Liquid Glass Settings
        val liquidGlassSwitch = SwitchPreferenceCompat(context).apply {
            key = PREF_KEY_LIQUID_GLASS
            title = context.getString(R.string.liquid_glass_effect)
            summary = context.getString(R.string.enable_glass_blur_effect)
            setDefaultValue(false)
        }
        systemCategory.addPreference(liquidGlassSwitch)

        val liquidGlassBlurPref = SeekBarPreference(context).apply {
            key = PREF_KEY_LIQUID_GLASS_BLUR
            title = context.getString(R.string.blur_radius)
            min = 0
            max = 255
            setDefaultValue(220)
            showSeekBarValue = true
        }
        systemCategory.addPreference(liquidGlassBlurPref)

        val liquidGlassKeyAlphaPref = SeekBarPreference(context).apply {
            key = PREF_KEY_LIQUID_GLASS_KEY_ALPHA
            title = context.getString(R.string.key_transparency)
            min = 0
            max = 255
            setDefaultValue(255)
            showSeekBarValue = true
        }
        systemCategory.addPreference(liquidGlassKeyAlphaPref)

        // Liquid Glass Dependency Logic
        val isLiquidGlassEnabled = liquidGlassSwitch.isChecked
        liquidGlassBlurPref.isEnabled = isLiquidGlassEnabled
        liquidGlassKeyAlphaPref.isEnabled = isLiquidGlassEnabled

        liquidGlassSwitch.configureChangeListener { _, newValue ->
            val isEnabled = newValue as Boolean
            liquidGlassBlurPref.isEnabled = isEnabled
            liquidGlassKeyAlphaPref.isEnabled = isEnabled
            true
        }

        // Default Theme Checkbox
        val defaultPref = CheckBoxPreference(context).apply {
            key = PREF_KEY_DEFAULT
            title = context.getString(R.string.theme_default)
            summary = context.getString(R.string.keyboard_theme_summary)
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                handleThemeSelection(MODE_DEFAULT)
                if (DynamicColors.isDynamicColorAvailable()) {
                    showColorPickerDialog(
                        initialColor = appPreference.seedColor,
                        onColorSelected = { color ->
                            appPreference.seedColor = color
                            requireActivity().recreate()
                        }
                    )
                }
                true
            }
        }
        systemCategory.addPreference(defaultPref)

        // -------------------------------------------------------
        // Custom Category (Keyboard Appearance)
        // -------------------------------------------------------
        val customCategory = PreferenceCategory(context).apply {
            title = context.getString(R.string.theme_category_custom)
        }
        screen.addPreference(customCategory)

        // Custom Theme Mode Selection
        val customPref = CheckBoxPreference(context).apply {
            key = PREF_KEY_CUSTOM
            title = context.getString(R.string.theme_custom)
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                handleThemeSelection(MODE_CUSTOM)
                true
            }
        }
        customCategory.addPreference(customPref)

        // Custom Background Color
        val customBgPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_BG,
            context.getString(R.string.theme_custom_bg_color)
        ) { appPreference.custom_theme_bg_color }
        customCategory.addPreference(customBgPref)

        // Custom Key Color
        val customKeyPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_KEY,
            context.getString(R.string.theme_custom_key_color)
        ) { appPreference.custom_theme_key_color }
        customCategory.addPreference(customKeyPref)

        // Custom Special Key Color
        val customSpecialKeyPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_SPECIAL_KEY,
            context.getString(R.string.theme_custom_special_key_color)
        ) { appPreference.custom_theme_special_key_color }
        customCategory.addPreference(customSpecialKeyPref)

        // Custom Text Color
        val customTextPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_TEXT,
            context.getString(R.string.theme_custom_key_text_color)
        ) { appPreference.custom_theme_key_text_color }
        customCategory.addPreference(customTextPref)

        // Custom Special Text Color
        val customSpecialTextPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_SPECIAL_TEXT,
            context.getString(R.string.theme_custom_special_key_text_color)
        ) { appPreference.custom_theme_special_key_text_color }
        customCategory.addPreference(customSpecialTextPref)

        val popupUseCustomColorPref = SwitchPreferenceCompat(context).apply {
            key = PREF_KEY_POPUP_USE_CUSTOM_COLOR
            title = context.getString(R.string.key_popup_use_custom_color_title)
            summaryOn = context.getString(R.string.key_popup_use_custom_color_summary_on)
            summaryOff = context.getString(R.string.key_popup_use_custom_color_summary_off)
            setDefaultValue(false)
        }
        customCategory.addPreference(popupUseCustomColorPref)

        val popupBackgroundColorPref = createColorPreference(
            context,
            PREF_KEY_POPUP_BACKGROUND_COLOR,
            context.getString(R.string.key_popup_background_color_title)
        ) { appPreference.key_popup_background_color }
        customCategory.addPreference(popupBackgroundColorPref)

        val popupTextColorPref = createColorPreference(
            context,
            PREF_KEY_POPUP_TEXT_COLOR,
            context.getString(R.string.key_popup_text_color_title)
        ) { appPreference.key_popup_text_color }
        customCategory.addPreference(popupTextColorPref)

        fun updatePopupColorPrefsState(enabled: Boolean) {
            popupBackgroundColorPref.isEnabled = enabled
            popupTextColorPref.isEnabled = enabled
        }

        updatePopupColorPrefsState(appPreference.key_popup_use_custom_color)

        popupUseCustomColorPref.configureChangeListener { _, newValue ->
            updatePopupColorPrefsState(newValue as Boolean)
            true
        }

        val customShortcutIconPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_SHORTCUT_ICON,
            context.getString(R.string.theme_custom_shortcut_icon_color)
        ) { appPreference.custom_theme_shortcut_icon_color }
        customCategory.addPreference(customShortcutIconPref)

        // Custom Border Settings
        val customBorderEnablePref = SwitchPreferenceCompat(context).apply {
            key = PREF_KEY_CUSTOM_BORDER_ENABLE
            title = context.getString(R.string.custom_border_enable)
            setDefaultValue(false)
        }
        customCategory.addPreference(customBorderEnablePref)

        val customBorderColorPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_BORDER_COLOR,
            context.getString(R.string.custom_border_color)
        ) { appPreference.custom_theme_border_color }
        customCategory.addPreference(customBorderColorPref)

        // Custom Border Width (修正: 初期値の設定とリスナーの追加)
        val customBorderWidthPref = SeekBarPreference(context).apply {
            key = PREF_KEY_CUSTOM_BORDER_WIDTH
            title = context.getString(R.string.custom_border_width)
            min = 1
            max = 8
            showSeekBarValue = true

            // AppPreferenceの現在の値をUIにセット
            value = appPreference.custom_theme_border_width

            // 値が変更されたときにAppPreferenceを更新するリスナー
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val width = newValue as Int
                appPreference.custom_theme_border_width = width
                true
            }
        }
        customCategory.addPreference(customBorderWidthPref)

        // Link Border Color & Width capability to switch
        val isBorderEnabled = customBorderEnablePref.isChecked
        customBorderColorPref.isEnabled = isBorderEnabled
        customBorderWidthPref.isEnabled = isBorderEnabled

        customBorderEnablePref.configureChangeListener { _, newValue ->
            val isEnabled = newValue as Boolean
            customBorderColorPref.isEnabled = isEnabled
            customBorderWidthPref.isEnabled = isEnabled
            true
        }

        // -------------------------------------------------------
        // Input Text Category
        // -------------------------------------------------------
        val inputCategory = PreferenceCategory(context).apply {
            key = CATEGORY_KEY_CUSTOM_INPUT
            title = context.getString(R.string.composing_text)
        }
        screen.addPreference(inputCategory)

        // 0. Enable Custom Input Colors
        val inputColorEnablePref = SwitchPreferenceCompat(context).apply {
            key = PREF_KEY_CUSTOM_INPUT_ENABLE
            title = context.getString(R.string.custom_input_color_enable_title)
            setDefaultValue(false)
        }
        inputCategory.addPreference(inputColorEnablePref)

        // 1. Pre-Edit Background
        val preEditBgPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_PRE_EDIT_BG,
            context.getString(R.string.pre_edit)
        ) { appPreference.custom_theme_pre_edit_bg_color }
        inputCategory.addPreference(preEditBgPref)

        // 2. Pre-Edit Text Color
        val preEditTextPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_PRE_EDIT_TEXT,
            context.getString(R.string.pre_edit_text)
        ) { appPreference.custom_theme_pre_edit_text_color }
        inputCategory.addPreference(preEditTextPref)

        // 3. Post-Edit/Highlight Background
        val postEditBgPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_POST_EDIT_BG,
            context.getString(R.string.conversion_text_color)
        ) { appPreference.custom_theme_post_edit_bg_color }
        inputCategory.addPreference(postEditBgPref)

        // 4. Post-Edit/Highlight Text Color
        val postEditTextPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_POST_EDIT_TEXT,
            context.getString(R.string.conversion_text_color_)
        ) { appPreference.custom_theme_post_edit_text_color }
        inputCategory.addPreference(postEditTextPref)

        // Link Color Preferences to Enable Switch
        fun updateInputColorPrefsState(enabled: Boolean) {
            preEditBgPref.isEnabled = enabled
            preEditTextPref.isEnabled = enabled
            postEditBgPref.isEnabled = enabled
            postEditTextPref.isEnabled = enabled
        }

        // Initialize state
        updateInputColorPrefsState(inputColorEnablePref.isChecked)

        // Listener
        inputColorEnablePref.configureChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            updateInputColorPrefsState(enabled)
            true
        }

        val skinPreference = Preference(context).apply {
            key = KeyboardSkinId.PREFERENCE_KEY
            title = context.getString(R.string.keyboard_skin_choose)
            order = -2
            configureClickListener {
                findNavController().navigate(R.id.keyboardSkinSelectionFragment)
                true
            }
        }
        screen.addPreference(skinPreference)
        val localFontPreference = Preference(context).apply {
            key = PREF_KEY_LOCAL_FONT
            title = context.getString(R.string.local_font_title)
            summary = context.getString(R.string.local_font_settings_summary)
            order = -1
            configureClickListener {
                findNavController().navigate(R.id.localFontSettingsFragment)
                true
            }
        }
        screen.addPreference(localFontPreference)
        // The XML is the search catalog as well as the canonical category structure.
        // Replace its metadata entries with the existing programmatic controls so their
        // color pickers, defaults and callbacks retain their original behavior.
        val canonical = preferenceManager.inflateFromResource(context, R.xml.pref_theme, null)
        fun transfer(preference: Preference) {
            val placeholder = canonical.findPreference<Preference>(preference.key) ?: return
            val parent = placeholder.parent ?: return
            parent.removePreference(placeholder)
            preference.parent?.removePreference(preference)
            parent.addPreference(preference)
        }
        listOf(systemCategory, customCategory, inputCategory).forEach { group ->
            val children = (0 until group.preferenceCount).map(group::getPreference)
            children.filterNot { it.key == PREF_KEY_ROUND_CORNER }.forEach(::transfer)
        }
        transfer(skinPreference)
        transfer(localFontPreference)
        return canonical
    }

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        super.onPreferencesReady(savedInstanceState, rootKey)
        updateCheckStates(appPreference.theme_mode)
        updateCustomColorsVisibility(appPreference.theme_mode == MODE_CUSTOM)
        findPreference<Preference>("settings_theme_enable_composing_guide_link")?.setOnPreferenceClickListener {
            navigateSafely(R.id.legacyCommonPreferenceFragment, bundleOf(ARG_HIGHLIGHT_PREFERENCE_KEY to "composing_guide_text_enabled"))
            true
        }
        syncCrossScreenDependencies()
        syncSearchAvailability()
    }

    override fun onPreferencesResumed() {
        super.onPreferencesResumed()
        val selected = appPreference.keyboardSkin
        findPreference<Preference>(KeyboardSkinId.PREFERENCE_KEY)?.summary =
            getString(KeyboardThemeCatalog.find(selected).titleRes)
        val compatible = selected == KeyboardSkinId.DEFAULT
        val skinDependent = listOf(PREF_KEY_DEFAULT, PREF_KEY_CUSTOM, PREF_KEY_LIQUID_GLASS,
            PREF_KEY_LIQUID_GLASS_BLUR, PREF_KEY_LIQUID_GLASS_KEY_ALPHA, PREF_KEY_CUSTOM_BG,
            PREF_KEY_CUSTOM_KEY, PREF_KEY_CUSTOM_SPECIAL_KEY, PREF_KEY_CUSTOM_TEXT,
            PREF_KEY_CUSTOM_SPECIAL_TEXT, PREF_KEY_POPUP_USE_CUSTOM_COLOR, PREF_KEY_POPUP_BACKGROUND_COLOR,
            PREF_KEY_POPUP_TEXT_COLOR, PREF_KEY_CUSTOM_SHORTCUT_ICON,
            PREF_KEY_CUSTOM_BORDER_ENABLE, PREF_KEY_CUSTOM_BORDER_COLOR, PREF_KEY_CUSTOM_BORDER_WIDTH)
        for (key in skinDependent) {
            val enabledByParent = when (key) {
                PREF_KEY_LIQUID_GLASS_BLUR, PREF_KEY_LIQUID_GLASS_KEY_ALPHA -> findPreference<SwitchPreferenceCompat>(PREF_KEY_LIQUID_GLASS)?.isChecked == true
                PREF_KEY_POPUP_BACKGROUND_COLOR, PREF_KEY_POPUP_TEXT_COLOR -> findPreference<SwitchPreferenceCompat>(PREF_KEY_POPUP_USE_CUSTOM_COLOR)?.isChecked == true
                PREF_KEY_CUSTOM_BORDER_COLOR, PREF_KEY_CUSTOM_BORDER_WIDTH -> findPreference<SwitchPreferenceCompat>(PREF_KEY_CUSTOM_BORDER_ENABLE)?.isChecked == true
                else -> true
            }
            findPreference<Preference>(key)?.isEnabled = compatible && enabledByParent
        }
        syncCrossScreenDependencies()
        syncSearchAvailability()
    }

    private val dependencyListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "composing_guide_text_enabled") syncCrossScreenDependencies()
        if (key != null && (key.startsWith("keyboard_touch_effect_") || key.startsWith("theme_") || key.startsWith("liquid_glass_") || key.startsWith("key_popup_"))) syncSearchAvailability()
    }

    override fun onStart() {
        super.onStart()
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext()).registerOnSharedPreferenceChangeListener(dependencyListener)
    }

    override fun onStop() {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext()).unregisterOnSharedPreferenceChangeListener(dependencyListener)
        super.onStop()
    }

    private fun syncCrossScreenDependencies() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        val composingEnabled = prefs.getBoolean("composing_guide_text_enabled", false)
        findPreference<Preference>("composing_guide_show_live_reading")?.isEnabled = composingEnabled
        findPreference<Preference>("composing_guide_text_size_setting")?.isEnabled = composingEnabled
        findPreference<Preference>("settings_theme_enable_composing_guide_link")?.conditionVisible = !composingEnabled
    }

    private fun syncSearchAvailability() {
        val linkKeys = listOf("settings_theme_unavailable_colors", "settings_theme_unavailable_corners", "settings_theme_unavailable_effects")
        linkKeys.forEach { findPreference<Preference>(it)?.conditionVisible = false }
        val key = arguments?.getString(ARG_HIGHLIGHT_PREFERENCE_KEY)
            ?: arguments?.getString(LegacyPreferenceResultFilter.ARG_LEGACY_SEARCH_TARGET_KEY)
            ?: return
        val target = findPreference<Preference>(key) ?: return
        if (target.conditionVisible && target.isEnabled) return
        val group = target.parent as? CollapsiblePreferenceCategory ?: return
        val linkKey = when (group.key) {
            "settings_theme_colors" -> linkKeys[0]
            "settings_theme_corners" -> linkKeys[1]
            "settings_theme_effects" -> linkKeys[2]
            else -> return
        }
        val link = findPreference<Preference>(linkKey) ?: return
        val incompatibleSkin = !target.isEnabled && appPreference.keyboardSkin != KeyboardSkinId.DEFAULT
        val parentKey = when {
            incompatibleSkin -> null
            !target.conditionVisible && group.key != "settings_theme_effects" -> PREF_KEY_CUSTOM
            key == PREF_KEY_CUSTOM_BORDER_COLOR || key == PREF_KEY_CUSTOM_BORDER_WIDTH -> PREF_KEY_CUSTOM_BORDER_ENABLE
            key == PREF_KEY_POPUP_BACKGROUND_COLOR || key == PREF_KEY_POPUP_TEXT_COLOR -> PREF_KEY_POPUP_USE_CUSTOM_COLOR
            key == PREF_KEY_LIQUID_GLASS_BLUR || key == PREF_KEY_LIQUID_GLASS_KEY_ALPHA -> PREF_KEY_LIQUID_GLASS
            key.startsWith("theme_custom_") && key.contains("edit_") -> PREF_KEY_CUSTOM_INPUT_ENABLE
            group.key == "settings_theme_effects" -> "keyboard_touch_effect_type_preference"
            else -> PREF_KEY_CUSTOM
        }
        link.summary = getString(when {
            incompatibleSkin -> R.string.settings_theme_unavailable_skin
            !target.conditionVisible && group.key != "settings_theme_effects" -> R.string.settings_theme_unavailable_custom
            group.key == "settings_theme_effects" && parentKey == "keyboard_touch_effect_type_preference" -> R.string.settings_theme_unavailable_effect
            else -> R.string.settings_theme_unavailable_parent
        })
        link.setOnPreferenceClickListener {
            if (incompatibleSkin) navigateSafely(R.id.keyboardSkinSelectionFragment)
            else parentKey?.let { parent ->
                (findPreference<Preference>(parent)?.parent as? CollapsiblePreferenceCategory)?.setExpanded(true, persist = false)
                scrollToPreference(parent)
            }
            true
        }
        link.conditionVisible = true
    }

    private fun createColorPreference(
        context: Context,
        key: String,
        titleStr: String,
        colorProvider: () -> Int
    ): Preference {
        return Preference(context).apply {
            this.key = key
            title = titleStr
            summary = String.format("#%08X", colorProvider())
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                showColorPickerDialog(
                    initialColor = colorProvider(),
                    onColorSelected = { color ->
                        saveCustomColor(key, color)
                        summary = String.format("#%08X", color)
                    }
                )
                true
            }
        }
    }

    private fun handleThemeSelection(mode: String) {
        appPreference.theme_mode = mode
        updateCheckStates(mode)
        updateCustomColorsVisibility(mode == MODE_CUSTOM)
        syncSearchAvailability()
    }

    private fun updateCheckStates(selectedMode: String) {
        findPreference<CheckBoxPreference>(PREF_KEY_DEFAULT)?.isChecked =
            selectedMode == MODE_DEFAULT
        findPreference<CheckBoxPreference>(PREF_KEY_CUSTOM)?.isChecked = selectedMode == MODE_CUSTOM
    }

    private fun updateCustomColorsVisibility(isVisible: Boolean) {
        // Base Custom Colors
        findPreference<Preference>(PREF_KEY_CUSTOM_BG)?.conditionVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_KEY)?.conditionVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_SPECIAL_KEY)?.conditionVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_TEXT)?.conditionVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_SPECIAL_TEXT)?.conditionVisible = isVisible
        findPreference<Preference>(PREF_KEY_POPUP_USE_CUSTOM_COLOR)?.conditionVisible = isVisible
        findPreference<Preference>(PREF_KEY_POPUP_BACKGROUND_COLOR)?.conditionVisible = isVisible
        findPreference<Preference>(PREF_KEY_POPUP_TEXT_COLOR)?.conditionVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_SHORTCUT_ICON)?.conditionVisible = isVisible

        // Border Settings
        findPreference<Preference>(PREF_KEY_CUSTOM_BORDER_ENABLE)?.conditionVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_BORDER_COLOR)?.conditionVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_BORDER_WIDTH)?.conditionVisible = isVisible

        // Input Category Visibility
        //findPreference<PreferenceCategory>(CATEGORY_KEY_CUSTOM_INPUT)?.conditionVisible = isVisible
    }

    private fun saveCustomColor(key: String, color: Int) {
        when (key) {
            // Base Colors
            PREF_KEY_CUSTOM_BG -> appPreference.custom_theme_bg_color = color
            PREF_KEY_CUSTOM_KEY -> appPreference.custom_theme_key_color = color
            PREF_KEY_CUSTOM_SPECIAL_KEY -> appPreference.custom_theme_special_key_color = color
            PREF_KEY_CUSTOM_TEXT -> appPreference.custom_theme_key_text_color = color
            PREF_KEY_CUSTOM_SPECIAL_TEXT -> appPreference.custom_theme_special_key_text_color =
                color
            PREF_KEY_CUSTOM_SHORTCUT_ICON -> appPreference.custom_theme_shortcut_icon_color =
                color

            // Border Color
            PREF_KEY_CUSTOM_BORDER_COLOR -> appPreference.custom_theme_border_color = color

            // Input Colors
            PREF_KEY_CUSTOM_PRE_EDIT_BG -> appPreference.custom_theme_pre_edit_bg_color = color
            PREF_KEY_CUSTOM_PRE_EDIT_TEXT -> appPreference.custom_theme_pre_edit_text_color = color
            PREF_KEY_CUSTOM_POST_EDIT_BG -> appPreference.custom_theme_post_edit_bg_color = color
            PREF_KEY_CUSTOM_POST_EDIT_TEXT -> appPreference.custom_theme_post_edit_text_color =
                color

            // Tap/Flick PopupView Colors
            PREF_KEY_POPUP_BACKGROUND_COLOR -> appPreference.key_popup_background_color = color
            PREF_KEY_POPUP_TEXT_COLOR -> appPreference.key_popup_text_color = color
        }
    }

    private fun showColorPickerDialog(initialColor: Int, onColorSelected: (Int) -> Unit) =
        showKeyboardThemeColorPicker(requireContext(), initialColor, onColorSelected)
}
