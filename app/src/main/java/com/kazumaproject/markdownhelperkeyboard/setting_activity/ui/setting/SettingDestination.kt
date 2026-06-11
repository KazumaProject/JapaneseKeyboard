package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import com.kazumaproject.core.R as CoreR

enum class SettingCategory {
    FREQUENT,
    KEYBOARD_DISPLAY,
    INPUT_METHOD,
    CANDIDATE_CONVERSION,
    DICTIONARY,
    AI_CONVERSION,
    CLIPBOARD_SHORTCUT,
    OPERATION_FEEDBACK,
    MANAGEMENT,
    BACKUP,
    APP_INFO,
    ADVANCED,
}

sealed class SettingDestinationType {
    data class NavDestination(
        @IdRes val destinationId: Int,
        val highlightPreferenceKey: String? = null,
    ) : SettingDestinationType()

    data class ManagementDestination(
        @IdRes val destinationId: Int,
        val highlightPreferenceKey: String? = null,
    ) : SettingDestinationType()

    data class SwitchPreference(
        val preferenceKey: String,
        val defaultValue: Boolean,
        @IdRes val destinationId: Int,
        val highlightPreferenceKey: String? = preferenceKey,
    ) : SettingDestinationType()

    data class ListPreference(
        val preferenceKey: String,
        @ArrayRes val entriesResId: Int,
        @ArrayRes val entryValuesResId: Int,
        val defaultValue: String,
        @IdRes val destinationId: Int,
        val highlightPreferenceKey: String? = preferenceKey,
    ) : SettingDestinationType()

    data class SeekBarPreference(
        val preferenceKey: String,
        val min: Int,
        val max: Int,
        val increment: Int,
        val defaultValue: Int,
        @IdRes val destinationId: Int,
        val highlightPreferenceKey: String? = preferenceKey,
    ) : SettingDestinationType()

    data class EditTextPreference(
        val preferenceKey: String,
        val defaultValue: String,
        val obscureValue: Boolean,
        @IdRes val destinationId: Int,
        val highlightPreferenceKey: String? = preferenceKey,
    ) : SettingDestinationType()

    data class IntPreferenceDialog(
        val preferenceKey: String,
        val min: Int,
        val max: Int,
        val step: Int,
        val defaultValue: Int,
        val unit: String,
        @IdRes val destinationId: Int,
        val highlightPreferenceKey: String? = preferenceKey,
    ) : SettingDestinationType()
}

data class LegacySettingTarget(
    val tabKey: String,
    @androidx.annotation.XmlRes val xmlRes: Int?,
    @IdRes val destinationId: Int,
    val preferenceKey: String,
    val relatedPreferenceKeys: List<String> = emptyList(),
    val filterResultMode: Boolean = true,
)

data class SettingDestination(
    val key: String,
    val title: String,
    val summary: String,
    val category: SettingCategory,
    val keywords: List<String>,
    val destination: SettingDestinationType,
    @DrawableRes val iconRes: Int,
    val legacyTarget: LegacySettingTarget? = null,
    val searchScope: SettingSearchScope = SettingSearchScope.NEW_HOME,
    val location: String? = null,
)

object SettingDestinations {

    private val managementDestinationKeys = setOf(
        "setting_management_learn_dictionary",
        "setting_management_user_dictionary",
        "setting_management_user_template",
        "setting_management_custom_keyboard",
        "user_dictionary_preference",
        "user_template_preference",
        "custom_romaji_preference",
        "shortcut_toolbar_item_preference",
        "candidate_tab_order_preference",
        "candidate_order_override_preference",
        "ng_word_preference",
        "physical_keyboard_shortcut_setting_preference",
        "sumire_special_key_editor_preference",
        "gemma_prompt_template_management_preference",
        "system_user_dictionary_builder_preference",
        "external_dictionary_settings_preference",
        "n_gram_rule_preference",
        "clipboard_history_preference_fragment",
        "delete_key_flick_left_targets_preference",
        "cursor_move_after_commit_target_pairs_preference",
        "preference_open_source",
    )

    private val plainPreferenceInlineEditExceptionKeys = setOf(
        "long_press_timeout_preference",
    )

    val inlineEditExceptionKeysForTesting: Set<String>
        get() = plainPreferenceInlineEditExceptionKeys

    fun frequent(context: Context): List<SettingDestination> = defaultFrequent(context)

    fun defaultFrequent(context: Context): List<SettingDestination> {
        val defaultKeys = listOf(
            "keyboard_screen_preference",
            "setting_route_keyboard_theme",
            "candidate_view_height_setting_fragment_preference",
            "candidate_view_height_landscape_setting_fragment_preference",
            "setting_route_input_method",
            "setting_route_dictionary",
            "clipboard_history_preference_fragment",
            "setting_route_zenz_preferences",
            "setting_route_custom_keyboard_preferences",
        )
        val candidates = frequentCandidates(context).associateBy { it.key }
        return defaultKeys.mapNotNull { candidates[it] }
    }

    fun frequentCandidates(context: Context): List<SettingDestination> {
        val manualDestinations = buildList {
        add(
            destination(
                key = "keyboard_screen_preference",
                title = context.getString(R.string.keyboard_size_setting_fragment_title),
                summary = context.getString(R.string.keyboard_screen_preference_summary),
                category = SettingCategory.FREQUENT,
                keywords = listOf("size", "height", "width", "keyboard"),
                destinationId = R.id.keyboardSettingFragment,
                iconRes = CoreR.drawable.keyboard_24px,
            )
        )
        add(
            destination(
                key = "keyboard_screen_landscape_preference",
                title = context.getString(R.string.keyboard_size_landscape_setting_fragment_title),
                summary = context.getString(R.string.keyboard_screen_landscape_preference_summary),
                category = SettingCategory.FREQUENT,
                keywords = listOf("landscape", "size", "height", "width", "keyboard"),
                destinationId = R.id.keyboardSizeLandscapeFragment,
                iconRes = CoreR.drawable.keyboard_24px,
            )
        )
        add(
            destination(
                key = "setting_route_keyboard_theme",
                title = context.getString(R.string.keyboardthemefragment),
                summary = context.getString(R.string.setting_route_keyboard_theme_summary),
                category = SettingCategory.FREQUENT,
                keywords = listOf("theme", "color", "dark", "light"),
                destinationId = R.id.keyboardThemeFragment,
                iconRes = CoreR.drawable.table_lamp_24px,
            )
        )
        add(
            destination(
                key = "theme_custom_candidate_empty_popup_bg_color",
                title = context.getString(R.string.theme_custom_candidate_empty_popup_bg_color),
                summary = context.getString(R.string.theme_custom_candidate_empty_popup_bg_summary),
                category = SettingCategory.KEYBOARD_DISPLAY,
                keywords = listOf(
                    "theme",
                    "custom",
                    "candidate",
                    "empty",
                    "popup",
                    "background",
                    "color",
                    "undo",
                    "redo",
                    "reconvert",
                    "paste",
                ),
                destinationId = R.id.keyboardThemeFragment,
                iconRes = CoreR.drawable.outline_border_color_24,
                highlightPreferenceKey = "theme_custom_candidate_empty_popup_bg_color",
            )
        )
        add(
            destination(
                key = "theme_custom_candidate_empty_popup_text_color",
                title = context.getString(R.string.theme_custom_candidate_empty_popup_text_color),
                summary = context.getString(R.string.theme_custom_candidate_empty_popup_text_summary),
                category = SettingCategory.KEYBOARD_DISPLAY,
                keywords = listOf(
                    "theme",
                    "custom",
                    "candidate",
                    "empty",
                    "popup",
                    "text",
                    "icon",
                    "color",
                    "undo",
                    "redo",
                    "reconvert",
                    "paste",
                ),
                destinationId = R.id.keyboardThemeFragment,
                iconRes = CoreR.drawable.outline_border_color_24,
                highlightPreferenceKey = "theme_custom_candidate_empty_popup_text_color",
            )
        )
        add(
            destination(
                key = "candidate_view_height_setting_fragment_preference",
                title = context.getString(R.string.candidate_height_portrait_preference_title),
                summary = context.getString(R.string.candidate_height_preference_sumary),
                category = SettingCategory.FREQUENT,
                keywords = listOf("candidate", "height", "suggestion", "portrait"),
                destinationId = R.id.candidateViewHeightSettingFragment,
                iconRes = CoreR.drawable.keyboard_tab_24px,
            )
        )
        add(
            destination(
                key = "candidate_view_height_landscape_setting_fragment_preference",
                title = context.getString(R.string.candidate_height_landscape_preference_title),
                summary = context.getString(R.string.candidate_height_preference_sumary),
                category = SettingCategory.FREQUENT,
                keywords = listOf("candidate", "height", "suggestion", "landscape"),
                destinationId = R.id.candidateHeightLandscapeSettingFragment,
                iconRes = CoreR.drawable.keyboard_tab_24px,
            )
        )
        add(
            destination(
                key = "setting_route_input_method",
                title = context.getString(R.string.setting_category_input_method_title),
                summary = context.getString(R.string.setting_category_input_method_summary),
                category = SettingCategory.FREQUENT,
                keywords = listOf("sumire", "kana", "qwerty", "input"),
                destinationId = R.id.inputMethodPreferenceFragment,
                iconRes = CoreR.drawable.input_24px,
            )
        )
        add(
            destination(
                key = "setting_route_dictionary",
                title = context.getString(R.string.category_dictionary),
                summary = context.getString(R.string.setting_category_dictionary_summary),
                category = SettingCategory.FREQUENT,
                keywords = listOf("dictionary", "learn", "user", "ng"),
                destinationId = R.id.dictionaryPreferenceFragment,
                iconRes = CoreR.drawable.dictionary_24px,
            )
        )
        add(
            destination(
                key = "clipboard_history_preference_fragment",
                title = context.getString(R.string.clipboard_history_fragment_title),
                summary = context.getString(R.string.clipboard_history_fragment_summary),
                category = SettingCategory.FREQUENT,
                keywords = listOf("clipboard", "history", "paste"),
                destinationId = R.id.clipboardHistoryFragment,
                iconRes = CoreR.drawable.content_paste_24px,
            )
        )
        add(
            destination(
                key = "setting_route_clipboard_shortcut",
                title = context.getString(R.string.setting_category_clipboard_shortcut_title),
                summary = context.getString(R.string.setting_category_clipboard_shortcut_summary),
                category = SettingCategory.FREQUENT,
                keywords = listOf("clipboard", "shortcut", "symbol", "toolbar"),
                destinationId = R.id.clipboardShortcutPreferenceFragment,
                iconRes = CoreR.drawable.content_paste_24px,
            )
        )
        add(
            destination(
                key = "setting_route_ai_conversion",
                title = context.getString(R.string.setting_category_ai_conversion_title),
                summary = context.getString(R.string.setting_category_ai_conversion_summary),
                category = SettingCategory.FREQUENT,
                keywords = listOf("ai", "zenz", "gemma"),
                destinationId = R.id.aiConversionPreferenceFragment,
                iconRes = CoreR.drawable.lightbulb_24dp,
            )
        )
        if (AppVariantConfig.hasZenz) {
            add(
                destination(
                    key = "setting_route_zenz_preferences",
                    title = "zenz",
                    summary = context.getString(R.string.setting_route_zenz_summary),
                    category = SettingCategory.FREQUENT,
                    keywords = listOf("zenz", "ai", "conversion", "neural"),
                    destinationId = R.id.zenzPreferenceFragment,
                    iconRes = CoreR.drawable.lightbulb_24dp,
                )
            )
        }
        if (AppVariantConfig.hasGemma) {
            add(
                destination(
                    key = "setting_route_gemma_preferences",
                    title = "Gemma",
                    summary = context.getString(R.string.setting_route_gemma_summary),
                    category = SettingCategory.FREQUENT,
                    keywords = listOf("gemma", "ai", "conversion", "translation"),
                    destinationId = R.id.gemmaPreferenceFragment,
                    iconRes = CoreR.drawable.lightbulb_24dp,
                )
            )
        }
        add(
            destination(
                key = "setting_route_custom_keyboard_preferences",
                title = context.getString(R.string.category_custom_keyboard_title),
                summary = context.getString(R.string.setting_route_custom_keyboard_summary),
                category = SettingCategory.FREQUENT,
                keywords = listOf("custom", "layout", "keyboard"),
                destinationId = R.id.customKeyboardPreferenceFragment,
                iconRes = CoreR.drawable.ic_custom_icon,
            )
        )
        add(
            destination(
                key = "keyboard_selection_preference",
                title = context.getString(R.string.keyboard_selection_fragment_title),
                summary = context.getString(R.string.keyboard_selection_fragment_summary),
                category = SettingCategory.FREQUENT,
                keywords = listOf("keyboard", "order", "input method"),
                destinationId = R.id.keyboardSelectionFragment,
                iconRes = CoreR.drawable.input_24px,
            )
        )
        add(
            destination(
                key = "setting_route_operation_feedback",
                title = context.getString(R.string.setting_category_operation_feedback_title),
                summary = context.getString(R.string.setting_category_operation_feedback_summary),
                category = SettingCategory.FREQUENT,
                keywords = listOf("long press", "cursor", "delete", "vibration", "sound"),
                destinationId = R.id.operationFeedbackPreferenceFragment,
                iconRes = CoreR.drawable.baseline_settings_24,
            )
        )
        add(
            destination(
                key = "setting_route_advanced",
                title = context.getString(R.string.setting_category_advanced_title),
                summary = context.getString(R.string.setting_category_advanced_summary),
                category = SettingCategory.FREQUENT,
                keywords = listOf("advanced", "detail", "romaji", "legacy"),
                destinationId = R.id.advancedPreferenceFragment,
                iconRes = CoreR.drawable.more_vert_24px,
            )
        )
        }
        val xmlDestinations = SettingSearchIndex.destinationsForKeys(
            context = context,
            keys = frequentCandidatePreferenceKeys,
        )
        return (manualDestinations + xmlDestinations).distinctBy { it.key }
    }

    private val frequentCandidatePreferenceKeys = listOf(
        "keyboard_floating_preference",
        "landscape_force_qwerty_preference",
        "keyboard_key_letter_size_fragment_preference",
        "keyboard_background_image_select_preference",
        "keyboard_background_video_select_preference",
        "round_corner_keyboard_preference",
        "keyboard_corner_radius_dp_preference",
        "candidate_column_preference",
        "candidate_tab_visibility_preference",
        "candidate_tab_order_preference",
        "live_conversion_preference",
        "live_conversion_start_length_preference",
        "enable_typo_correction_japanese_flick_keyboard_preference",
        "enable_typo_correction_qwerty_english_keyboard_preference",
        "omission_search_preference",
        "reconversion_preference",
        "keyboard_selection_preference",
        "setting_route_sumire_preferences",
        "setting_route_kana_preferences",
        "setting_route_qwerty_preferences",
        "setting_route_custom_keyboard_preferences",
        "setting_route_tablet_preferences",
        "flick_input_only_preference",
        "flick_sensitivity_preference",
        "long_press_timeout_preference",
        "delete_key_flick_left_preference",
        "undo_enable_preference",
        "vibration_preference",
        "key_sound_preference",
        "space_key_preference",
        "clipboard_history_preference_fragment",
        "clipboard_preview_enable_preference",
        "shortcut_toolbar_visibility_preference",
        "shortcut_toolbar_integrated_in_suggestion_preference",
        "shortcut_toolbar_item_preference",
        "symbol_mode_preference",
        "default_emoji_skin_tone_preference",
        "system_user_dictionary_builder_preference",
        "external_dictionary_settings_preference",
        "n_gram_rule_preference",
        "candidate_order_override_preference",
        "ng_word_preference",
        "learn_dictionary_preference",
        "user_dictionary_preference",
        "user_template_preference",
        "enable_ai_conversion_zenz_preference",
        "enable_zenz_rerank_preference",
        "zenz_model_select_preference",
        "enable_zenz_right_context_preference",
        "zenz_debounce_time_preference",
        "gemma_translation_enable_preference",
        "gemma_translation_target_language_preference",
        "gemma_prompt_template_management_preference",
    )

    fun categories(context: Context): List<SettingDestination> = listOf(
        destination(
            key = "setting_route_keyboard_display",
            title = context.getString(R.string.setting_category_keyboard_display_title),
            summary = context.getString(R.string.setting_category_keyboard_display_summary),
            category = SettingCategory.KEYBOARD_DISPLAY,
            keywords = listOf("size", "theme", "background", "appearance"),
            destinationId = R.id.keyboardDisplayPreferenceFragment,
            iconRes = CoreR.drawable.keyboard_24px,
        ),
        destination(
            key = "setting_route_input_method",
            title = context.getString(R.string.setting_category_input_method_title),
            summary = context.getString(R.string.setting_category_input_method_summary),
            category = SettingCategory.INPUT_METHOD,
            keywords = listOf("sumire", "kana", "qwerty", "custom"),
            destinationId = R.id.inputMethodPreferenceFragment,
            iconRes = CoreR.drawable.input_24px,
        ),
        destination(
            key = "setting_route_candidate_conversion",
            title = context.getString(R.string.setting_category_candidate_conversion_title),
            summary = context.getString(R.string.setting_category_candidate_conversion_summary),
            category = SettingCategory.CANDIDATE_CONVERSION,
            keywords = listOf("candidate", "conversion", "reconversion", "typo"),
            destinationId = R.id.candidateConversionPreferenceFragment,
            iconRes = CoreR.drawable.arrows_output_24px,
        ),
        destination(
            key = "setting_route_dictionary",
            title = context.getString(R.string.category_dictionary),
            summary = context.getString(R.string.setting_category_dictionary_summary),
            category = SettingCategory.DICTIONARY,
            keywords = listOf("dictionary", "learn", "ng", "template"),
            destinationId = R.id.dictionaryPreferenceFragment,
            iconRes = CoreR.drawable.dictionary_24px,
        ),
        destination(
            key = "setting_route_ai_conversion",
            title = context.getString(R.string.setting_category_ai_conversion_title),
            summary = context.getString(R.string.setting_category_ai_conversion_summary),
            category = SettingCategory.AI_CONVERSION,
            keywords = listOf("ai", "zenz", "gemma"),
            destinationId = R.id.aiConversionPreferenceFragment,
            iconRes = CoreR.drawable.lightbulb_24dp,
        ),
        destination(
            key = "setting_route_clipboard_shortcut",
            title = context.getString(R.string.setting_category_clipboard_shortcut_title),
            summary = context.getString(R.string.setting_category_clipboard_shortcut_summary),
            category = SettingCategory.CLIPBOARD_SHORTCUT,
            keywords = listOf("clipboard", "shortcut", "symbol", "toolbar"),
            destinationId = R.id.clipboardShortcutPreferenceFragment,
            iconRes = CoreR.drawable.content_paste_24px,
        ),
        destination(
            key = "setting_route_operation_feedback",
            title = context.getString(R.string.setting_category_operation_feedback_title),
            summary = context.getString(R.string.setting_category_operation_feedback_summary),
            category = SettingCategory.OPERATION_FEEDBACK,
            keywords = listOf("long press", "cursor", "delete", "vibration", "sound"),
            destinationId = R.id.operationFeedbackPreferenceFragment,
            iconRes = CoreR.drawable.baseline_settings_24,
        ),
        destination(
            key = "setting_route_backup",
            title = context.getString(R.string.back_up),
            summary = context.getString(R.string.setting_category_backup_summary),
            category = SettingCategory.BACKUP,
            keywords = listOf("backup", "export", "import"),
            destinationId = R.id.generalInfoPreferenceFragment,
            iconRes = CoreR.drawable.baseline_save_24,
        ),
        destination(
            key = "setting_route_app_info",
            title = context.getString(R.string.category_about_app_title),
            summary = context.getString(R.string.setting_category_app_info_summary),
            category = SettingCategory.APP_INFO,
            keywords = listOf("version", "license", "open source"),
            destinationId = R.id.generalInfoPreferenceFragment,
            iconRes = CoreR.drawable.question_mark_24dp,
        ),
        destination(
            key = "setting_route_advanced",
            title = context.getString(R.string.setting_category_advanced_title),
            summary = context.getString(R.string.setting_category_advanced_summary),
            category = SettingCategory.ADVANCED,
            keywords = listOf("advanced", "detail", "romaji", "legacy"),
            destinationId = R.id.advancedPreferenceFragment,
            iconRes = CoreR.drawable.more_vert_24px,
        ),
    )

    fun management(context: Context): List<SettingDestination> = listOf(
        destination(
            key = "setting_management_learn_dictionary",
            title = context.getString(R.string.learn_dictionary_fragment_title),
            summary = context.getString(R.string.setting_management_learn_dictionary_summary),
            category = SettingCategory.MANAGEMENT,
            keywords = listOf("learn", "dictionary", "history"),
            destinationId = R.id.navigation_learn_dictionary,
            iconRes = CoreR.drawable.table_lamp_24px,
            destinationType = SettingDestinationType.ManagementDestination(
                destinationId = R.id.navigation_learn_dictionary,
            ),
        ),
        destination(
            key = "setting_management_user_dictionary",
            title = context.getString(R.string.user_dictionary_title),
            summary = context.getString(R.string.setting_management_user_dictionary_summary),
            category = SettingCategory.MANAGEMENT,
            keywords = listOf("user", "dictionary", "word"),
            destinationId = R.id.navigation_user_dictionary,
            iconRes = CoreR.drawable.dictionary_24px,
            destinationType = SettingDestinationType.ManagementDestination(
                destinationId = R.id.navigation_user_dictionary,
            ),
        ),
        destination(
            key = "setting_management_user_template",
            title = context.getString(R.string.user_template_fragment_title),
            summary = context.getString(R.string.setting_management_user_template_summary),
            category = SettingCategory.MANAGEMENT,
            keywords = listOf("template", "snippet"),
            destinationId = R.id.userTemplateFragment,
            iconRes = CoreR.drawable.book_3_24px,
            destinationType = SettingDestinationType.ManagementDestination(
                destinationId = R.id.userTemplateFragment,
            ),
        ),
        destination(
            key = "setting_management_custom_keyboard",
            title = context.getString(R.string.custom_keyboard_fragment_label),
            summary = context.getString(R.string.setting_management_custom_keyboard_summary),
            category = SettingCategory.MANAGEMENT,
            keywords = listOf("custom", "keyboard", "layout"),
            destinationId = R.id.keyboardListFragment,
            iconRes = CoreR.drawable.keyboard_24px,
            destinationType = SettingDestinationType.ManagementDestination(
                destinationId = R.id.keyboardListFragment,
            ),
        ),
    )

    fun all(context: Context): List<SettingDestination> =
        defaultFrequent(context) + categories(context) + management(context)

    fun categoryTitle(context: Context, category: SettingCategory): String =
        when (category) {
            SettingCategory.FREQUENT -> context.getString(R.string.setting_home_frequent_title)
            SettingCategory.KEYBOARD_DISPLAY -> context.getString(R.string.setting_category_keyboard_display_title)
            SettingCategory.INPUT_METHOD -> context.getString(R.string.setting_category_input_method_title)
            SettingCategory.CANDIDATE_CONVERSION -> context.getString(R.string.setting_category_candidate_conversion_title)
            SettingCategory.DICTIONARY -> context.getString(R.string.category_dictionary)
            SettingCategory.AI_CONVERSION -> context.getString(R.string.setting_category_ai_conversion_title)
            SettingCategory.CLIPBOARD_SHORTCUT -> context.getString(R.string.setting_category_clipboard_shortcut_title)
            SettingCategory.OPERATION_FEEDBACK -> context.getString(R.string.setting_category_operation_feedback_title)
            SettingCategory.MANAGEMENT -> context.getString(R.string.setting_category_management_title)
            SettingCategory.BACKUP -> context.getString(R.string.back_up)
            SettingCategory.APP_INFO -> context.getString(R.string.category_about_app_title)
            SettingCategory.ADVANCED -> context.getString(R.string.setting_category_advanced_title)
        }

    @DrawableRes
    fun defaultIconForCategory(category: SettingCategory): Int =
        when (category) {
            SettingCategory.FREQUENT -> CoreR.drawable.baseline_check_24
            SettingCategory.KEYBOARD_DISPLAY -> CoreR.drawable.keyboard_24px
            SettingCategory.INPUT_METHOD -> CoreR.drawable.input_24px
            SettingCategory.CANDIDATE_CONVERSION -> CoreR.drawable.arrows_output_24px
            SettingCategory.DICTIONARY -> CoreR.drawable.dictionary_24px
            SettingCategory.AI_CONVERSION -> CoreR.drawable.lightbulb_24dp
            SettingCategory.CLIPBOARD_SHORTCUT -> CoreR.drawable.content_paste_24px
            SettingCategory.OPERATION_FEEDBACK -> CoreR.drawable.baseline_settings_24
            SettingCategory.MANAGEMENT -> CoreR.drawable.baseline_settings_24
            SettingCategory.BACKUP -> CoreR.drawable.baseline_save_24
            SettingCategory.APP_INFO -> CoreR.drawable.question_mark_24dp
            SettingCategory.ADVANCED -> CoreR.drawable.more_vert_24px
        }

    @IdRes
    fun routeDestinationId(key: String): Int? =
        when (key) {
            "setting_route_keyboard_display" -> R.id.keyboardDisplayPreferenceFragment
            "setting_route_input_method" -> R.id.inputMethodPreferenceFragment
            "setting_route_candidate_conversion" -> R.id.candidateConversionPreferenceFragment
            "setting_route_dictionary" -> R.id.dictionaryPreferenceFragment
            "setting_route_ai_conversion" -> R.id.aiConversionPreferenceFragment
            "setting_route_clipboard_shortcut" -> R.id.clipboardShortcutPreferenceFragment
            "setting_route_operation_feedback" -> R.id.operationFeedbackPreferenceFragment
            "setting_route_general_info" -> R.id.generalInfoPreferenceFragment
            "setting_route_backup" -> R.id.generalInfoPreferenceFragment
            "setting_route_app_info" -> R.id.generalInfoPreferenceFragment
            "setting_route_advanced" -> R.id.advancedPreferenceFragment
            "setting_management_learn_dictionary" -> R.id.navigation_learn_dictionary
            "setting_management_user_dictionary" -> R.id.navigation_user_dictionary
            "setting_management_user_template" -> R.id.userTemplateFragment
            "setting_management_custom_keyboard" -> R.id.keyboardListFragment
            "setting_route_legacy_settings" -> R.id.settingMainFragment
            "setting_route_keyboard_theme" -> R.id.keyboardThemeFragment
            "theme_custom_candidate_empty_popup_bg_color" -> R.id.keyboardThemeFragment
            "theme_custom_candidate_empty_popup_text_color" -> R.id.keyboardThemeFragment
            "setting_route_kana_preferences" -> R.id.kanaPreferenceFragment
            "setting_route_qwerty_preferences" -> R.id.qwertyPreferenceFragment
            "setting_route_sumire_preferences" -> R.id.sumirePreferenceFragment
            "setting_route_custom_keyboard_preferences" -> R.id.customKeyboardPreferenceFragment
            "setting_route_tablet_preferences" -> R.id.tabletPreferenceFragment
            "setting_route_hardware_keyboard_preferences" -> R.id.hardwareKeyboardPreferenceFragment
            "setting_route_common_preferences" -> R.id.commonPreferenceFragment
            "setting_route_zenz_preferences" -> R.id.zenzPreferenceFragment.takeIf { AppVariantConfig.hasZenz }
            "setting_route_gemma_preferences" -> R.id.gemmaPreferenceFragment.takeIf { AppVariantConfig.hasGemma }
            "custom_romaji_preference" -> R.id.romajiMapFragment
            "shortcut_toolbar_item_preference" -> R.id.shortcutSettingFragment
            "candidate_tab_order_preference" -> R.id.candidateTabOrderFragment
            "keyboard_selection_preference" -> R.id.keyboardSelectionFragment
            "keyboard_key_letter_size_fragment_preference" -> R.id.keyCandidateLetterSizeFragment
            "keyboard_screen_landscape_preference" -> R.id.keyboardSizeLandscapeFragment
            "candidate_view_height_setting_fragment_preference" -> R.id.candidateViewHeightSettingFragment
            "candidate_view_height_landscape_setting_fragment_preference" -> R.id.candidateHeightLandscapeSettingFragment
            "clipboard_history_preference_fragment" -> R.id.clipboardHistoryFragment
            "delete_key_flick_left_targets_preference" -> R.id.deleteKeyFlickTargetsFragment
            "cursor_move_after_commit_target_pairs_preference" -> R.id.cursorMoveTargetPairsFragment
            "keyboard_screen_preference" -> R.id.keyboardSettingFragment
            "preference_open_source" -> R.id.openSourceFragment
            "system_user_dictionary_builder_preference" -> R.id.systemUserDictionaryBuilderFragment
            "external_dictionary_settings_preference" -> R.id.externalDictionarySettingsFragment
            "n_gram_rule_preference" -> R.id.ngramRuleFragment
            "candidate_order_override_preference" -> R.id.candidateOrderOverrideFragment
            "ng_word_preference" -> R.id.ngWordFragment
            "gemma_prompt_template_management_preference" -> R.id.gemmaPromptTemplateFragment
            "kana_keyboard_letter_size_preference" -> R.id.tenKeyCandidateLetterSizeFragment
            "tenkey_popup_view_style_preference" -> R.id.tenKeyPopupStyleSettingFragment
            "qwerty_button_size_preference" -> R.id.qwertyMarginSettingFragment
            "qwerty_popup_view_style_preference" -> R.id.qwertyPopupStyleSettingFragment
            "qwerty_number_key_flick_setting_preference" -> R.id.qwertyNumberKeyFlickSettingFragment
            "sumire_keyboard_size_preference" -> R.id.flickKeyboardSizeSettingsFragment
            "custom_keyboard_size_preference" -> R.id.flickKeyboardSizeSettingsFragment
            "flick_keyboard_popup_view_style_preference" -> R.id.flickKeyboardPopupStyleListFragment
            "sumire_custom_angle_preference" -> R.id.circularFlickSettingsFragment
            "circular_slot_action_setting_preference" -> R.id.circularSlotActionSettingFragment
            "sumire_special_key_editor_preference" -> R.id.sumireSpecialKeyEditorFragment
            "physical_keyboard_shortcut_setting_preference" -> R.id.physicalKeyboardShortcutListFragment
            else -> null
        }

    fun isManagementDestinationKey(key: String): Boolean =
        key in managementDestinationKeys

    fun plainPreferenceInlineEditException(
        key: String,
        @IdRes destinationId: Int,
        highlightPreferenceKey: String?,
    ): SettingDestinationType? =
        when (key) {
            "long_press_timeout_preference" -> SettingDestinationType.IntPreferenceDialog(
                preferenceKey = key,
                min = 100,
                max = 2000,
                step = 1,
                defaultValue = 300,
                unit = "ms",
                destinationId = destinationId,
                highlightPreferenceKey = highlightPreferenceKey ?: key,
            )

            else -> null
        }

    @IdRes
    fun destinationId(destinationType: SettingDestinationType): Int? =
        when (destinationType) {
            is SettingDestinationType.NavDestination -> destinationType.destinationId
            is SettingDestinationType.ManagementDestination -> destinationType.destinationId
            is SettingDestinationType.SwitchPreference -> destinationType.destinationId
            is SettingDestinationType.ListPreference -> destinationType.destinationId
            is SettingDestinationType.SeekBarPreference -> destinationType.destinationId
            is SettingDestinationType.EditTextPreference -> destinationType.destinationId
            is SettingDestinationType.IntPreferenceDialog -> destinationType.destinationId
        }

    fun highlightPreferenceKey(destinationType: SettingDestinationType): String? =
        when (destinationType) {
            is SettingDestinationType.NavDestination -> destinationType.highlightPreferenceKey
            is SettingDestinationType.ManagementDestination -> destinationType.highlightPreferenceKey
            is SettingDestinationType.SwitchPreference -> destinationType.highlightPreferenceKey
            is SettingDestinationType.ListPreference -> destinationType.highlightPreferenceKey
            is SettingDestinationType.SeekBarPreference -> destinationType.highlightPreferenceKey
            is SettingDestinationType.EditTextPreference -> destinationType.highlightPreferenceKey
            is SettingDestinationType.IntPreferenceDialog -> destinationType.highlightPreferenceKey
        }

    fun destination(
        key: String,
        title: String,
        summary: String,
        category: SettingCategory,
        keywords: List<String>,
        @IdRes destinationId: Int,
        @DrawableRes iconRes: Int,
        highlightPreferenceKey: String? = null,
        destinationType: SettingDestinationType? = null,
        legacyTarget: LegacySettingTarget? = null,
        searchScope: SettingSearchScope = SettingSearchScope.NEW_HOME,
        location: String? = null,
    ): SettingDestination {
        val localizedKeywords = buildList {
            add(title)
            add(summary)
            add(key)
            addAll(keywords)
        }
        return SettingDestination(
            key = key,
            title = title,
            summary = summary,
            category = category,
            keywords = localizedKeywords,
            destination = destinationType ?: SettingDestinationType.NavDestination(
                destinationId = destinationId,
                highlightPreferenceKey = highlightPreferenceKey,
            ),
            iconRes = iconRes,
            legacyTarget = legacyTarget,
            searchScope = searchScope,
            location = location,
        )
    }
}
