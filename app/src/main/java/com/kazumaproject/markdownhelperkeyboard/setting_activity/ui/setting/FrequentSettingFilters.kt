package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import com.kazumaproject.markdownhelperkeyboard.R

internal data class FrequentCategoryFilter(
    val title: String,
    val category: SettingCategory?,
)

internal object FrequentSettingFilters {

    fun filters(
        context: Context,
        candidates: List<SettingDestination>,
    ): List<FrequentCategoryFilter> {
        val availableCategories = candidates.map { categoryOf(it) }.toSet()
        val desiredCategories = listOf(
            SettingCategory.KEYBOARD_DISPLAY,
            SettingCategory.INPUT_METHOD,
            SettingCategory.CANDIDATE_CONVERSION,
            SettingCategory.CONVERSION_ENGINE,
            SettingCategory.DICTIONARY,
            SettingCategory.AI_CONVERSION,
            SettingCategory.CLIPBOARD_SHORTCUT,
            SettingCategory.OPERATION_FEEDBACK,
            SettingCategory.MANAGEMENT,
        )
        return buildList {
            add(FrequentCategoryFilter(context.getString(R.string.setting_frequent_category_all), null))
            desiredCategories
                .filter { it in availableCategories }
                .forEach { category ->
                    add(
                        FrequentCategoryFilter(
                            title = SettingDestinations.categoryTitle(context, category),
                            category = category,
                        )
                    )
                }
        }
    }

    fun filter(
        context: Context,
        candidates: List<SettingDestination>,
        selectedCategory: SettingCategory?,
        query: String,
    ): List<SettingDestination> {
        val categoryFiltered = candidates.filter { destination ->
            selectedCategory == null || categoryOf(destination) == selectedCategory
        }
        return SettingSearchIndex.filter(
            context = context,
            destinations = categoryFiltered,
            query = query,
        )
    }

    fun categoryOf(destination: SettingDestination): SettingCategory {
        if (destination.category != SettingCategory.FREQUENT) return destination.category
        val destinationId = SettingDestinations.destinationId(destination.destination)
        return when (destinationId) {
            R.id.keyboardDisplayPreferenceFragment,
            R.id.keyboardSettingFragment,
            R.id.keyboardSizeLandscapeFragment,
            R.id.keyboardThemeFragment,
            R.id.candidateViewHeightSettingFragment,
            R.id.candidateHeightLandscapeSettingFragment,
            R.id.keyCandidateLetterSizeFragment,
                -> SettingCategory.KEYBOARD_DISPLAY

            R.id.inputMethodPreferenceFragment,
            R.id.keyboardSelectionFragment,
            R.id.kanaPreferenceFragment,
            R.id.qwertyPreferenceFragment,
            R.id.sumirePreferenceFragment,
            R.id.customKeyboardPreferenceFragment,
            R.id.tabletPreferenceFragment,
            R.id.hardwareKeyboardPreferenceFragment,
            R.id.tenKeyCandidateLetterSizeFragment,
            R.id.tenKeyPopupStyleSettingFragment,
            R.id.qwertyMarginSettingFragment,
            R.id.qwertyNumberKeyFlickSettingFragment,
            R.id.qwertyPopupStyleSettingFragment,
            R.id.flickKeyboardPopupStyleListFragment,
            R.id.flickKeyboardSizeSettingsFragment,
            R.id.circularFlickSettingsFragment,
            R.id.hierarchicalFlickAngleMarginFragment,
            R.id.circularSlotActionSettingFragment,
            R.id.sumireSpecialKeyEditorFragment,
                -> SettingCategory.INPUT_METHOD

            R.id.candidateConversionPreferenceFragment,
            R.id.candidateTabOrderFragment,
            R.id.candidateOrderOverrideFragment,
                -> SettingCategory.CANDIDATE_CONVERSION

            R.id.conversionEnginePreferenceFragment,
                -> SettingCategory.CONVERSION_ENGINE

            R.id.dictionaryPreferenceFragment,
            R.id.systemUserDictionaryBuilderFragment,
            R.id.externalDictionarySettingsFragment,
            R.id.ngramRuleFragment,
            R.id.ngWordFragment,
                -> SettingCategory.DICTIONARY

            R.id.aiConversionPreferenceFragment,
            R.id.zenzPreferenceFragment,
            R.id.gemmaPreferenceFragment,
            R.id.gemmaPromptTemplateFragment,
                -> SettingCategory.AI_CONVERSION

            R.id.clipboardShortcutPreferenceFragment,
            R.id.clipboardHistoryFragment,
            R.id.shortcutSettingFragment,
                -> SettingCategory.CLIPBOARD_SHORTCUT

            R.id.operationFeedbackPreferenceFragment,
            R.id.deleteKeyFlickTargetsFragment,
            R.id.cursorMoveTargetPairsFragment,
            R.id.physicalKeyboardShortcutListFragment,
                -> SettingCategory.OPERATION_FEEDBACK

            R.id.navigation_learn_dictionary,
            R.id.navigation_user_dictionary,
            R.id.userTemplateFragment,
            R.id.keyboardListFragment,
                -> SettingCategory.MANAGEMENT

            else -> SettingCategory.ADVANCED
        }
    }
}
