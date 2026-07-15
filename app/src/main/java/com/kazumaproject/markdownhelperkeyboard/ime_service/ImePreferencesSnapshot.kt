package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.CandidateTab
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategoryLoadState
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionarySourceResolver
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig

data class ImePreferencesSnapshot(
    val keyboardOrder: List<KeyboardType>,
    val candidateTabOrder: List<CandidateTab>,
    val mozcUTPersonName: Boolean,
    val mozcUTPlaces: Boolean,
    val mozcUTWiki: Boolean,
    val mozcUTNeologd: Boolean,
    val mozcUTWeb: Boolean,
    val isFlickOnlyMode: Boolean,
    val isOmissionSearchEnable: Boolean,
    val delayTime: Int,
    val isLearnDictionaryMode: Boolean,
    val incognitoModeDetectionPreference: Boolean,
    val showLearnedCandidatesInIncognitoPreference: Boolean,
    val isUserDictionaryEnable: Boolean,
    val isUserTemplateEnable: Boolean,
    val systemNgramDictionaryEnabled: Boolean,
    val customNgramDictionaryEnabled: Boolean,
    val showDictionaryCandidateLabels: Boolean,
    val suppressHentaiganaCandidates: Boolean,
    val zeroQuerySuggestionPreference: Boolean,
    val hankakuPreference: Boolean,
    val customDirectModeSpaceHankakuPreference: Boolean,
    val isLiveConversionEnable: Boolean,
    val liveConversionStartLength: Int,
    val showLiveConversionCandidateYomi: Boolean,
    val nBest: Int,
    val conversionBeamWidth: Int,
    val flickSensitivityPreferenceValue: Int,
    val longPressTimeoutPreferenceValue: Int,
    val qwertyShowIMEButtonPreference: Boolean,
    val qwertyShowEmojiButtonPreference: Boolean,
    val tenkeyShowIMEButtonPreference: Boolean,
    val qwertyShowCursorButtonsPreference: Boolean,
    val qwertyShowNumberButtonsPreference: Boolean,
    val qwertyShowSwitchRomajiEnglishPreference: Boolean,
    val qwertyEnglishDirectInputPreference: Boolean,
    val qwertyGlideInputPreference: Boolean,
    val qwertyGlideCommitPreviousCandidateOnNewGlidePreference: Boolean,
    val qwertyGlideInsertSpaceAfterCommittingPreviousCandidatePreference: Boolean,
    val qwertyShowPopupWindowPreference: Boolean,
    val qwertyEnableFlickUpPreference: Boolean,
    val qwertyEnableFlickDownPreference: Boolean,
    val qwertyNumberKeyFlickUpChars: Map<String, String>,
    val qwertyNumberKeyFlickDownChars: Map<String, String>,
    val qwertyEnableZenkakuSpacePreference: Boolean,
    val qwertyRomajiHankakuNumberPreference: Boolean,
    val qwertyRomajiHankakuSymbolPreference: Boolean,
    val qwertyShowKutoutenButtonsPreference: Boolean,
    val showCandidateInPasswordPreference: Boolean,
    val qwertyShowKeymapSymbolsPreference: Boolean,
    val qwertyRomajiShiftConversionPreference: Boolean,
    val tabletGojuonLayoutPreference: Boolean,
    val isNgWordEnable: Boolean,
    val deleteKeyHighLight: Boolean,
    val customKeyboardSuggestionPreference: Boolean,
    val userDictionaryPrefixMatchNumber: Int,
    val isVibration: Boolean,
    val vibrationTimingStr: String,
    val isKeySoundEnabled: Boolean,
    val keySoundVolumePercent: Int,
    val sumireInputKeyType: String,
    val sumireInputKeyLayoutType: String,
    val sumireInputStyle: String,
    val candidateColumns: String,
    val candidateColumnsLandscape: String,
    val candidateTabVisibility: Boolean,
    val symbolKeyboardFirstItem: SymbolMode,
    val defaultEmojiSkinTone: String,
    val isCustomKeyboardTwoWordsOutputEnable: Boolean,
    val tenkeyQWERTYSwitchNumber: Boolean,
    val tenkeyUseThreeStateKeyboard: Boolean,
    val tenkeySwitchNumberToQwertyNumberPreference: Boolean,
    val tenkeyRestoreInputModeOnRestart: Boolean,
    val sumireRestoreInputModeOnRestart: Boolean,
    val tenkeyRestoreInputModeOnlyWithinTime: Boolean,
    val tenkeyRestoreInputModeTimeoutMinutes: Int,
    val tenkeyLastInputModeSavedAtEpochMillis: Long,
    val sumireRestoreInputModeOnlyWithinTime: Boolean,
    val sumireRestoreInputModeTimeoutMinutes: Int,
    val sumireLastInputModeSavedAtEpochMillis: Long,
    val tenkeyLastInputModePreference: String,
    val tenkeyLastInputModePresentationPreference: String,
    val tenkeyLastQwertyNumberReturnTargetPreference: String,
    val sumireLastInputModePreference: String,
    val sumireLastInputModePresentationPreference: String,
    val tabletTenkeyQwertySwitchEnglish: Boolean,
    val tenkeyQKeymapGuide: Boolean,
    val flickKeymapGuide: Boolean,
    val flickGuideTextSizeSp: Int,
    val flickGuideMaxCharacters: Int,
    val isKeyboardFloatingMode: Boolean,
    val isKeyboardRounded: Boolean,
    val keyboardCornerRadiusDp: Int,
    val keyboardCornerTopLeft: Boolean,
    val keyboardCornerTopRight: Boolean,
    val keyboardCornerBottomLeft: Boolean,
    val keyboardCornerBottomRight: Boolean,
    val bunsetsuSeparation: Boolean,
    val bunsetsuCursorMove: Boolean,
    val reconversionEnabled: Boolean,
    val conversionKeySwipePreference: Boolean,
    val physicalKeyboardInputMode: String,
    val switchQWERTYPassword: Boolean,
    val landscapeForceQwertyPreference: Boolean,
    val landscapeForceQwertyRomajiPreference: Boolean,
    val shortcutTollbarVisibility: Boolean,
    val shortcutToolbarIntegratedInSuggestion: Boolean,
    val shortcutToolbarHeightDp: Int,
    val shortcutToolbarIconSizeDp: Int,
    val isDeleteLeftFlickPreference: Boolean,
    val isDeleteUpFlickPreference: Boolean,
    val isDeleteDownFlickPreference: Boolean,
    val zenzDebounceTimePreference: Int,
    val zenzMaximumLetterSizePreference: Int,
    val zenzMaximumContextSizePreference: Int,
    val zenzMaximumThreadSizePreference: Int,
    val clipboardPreviewVisibility: Boolean,
    val clipboardPreviewTapToDelete: Boolean,
    val tenkeyHeightPreferenceValue: Int,
    val tenkeyWidthPreferenceValue: Int,
    val qwertyHeightPreferenceValue: Int,
    val qwertyWidthPreferenceValue: Int,
    val candidateViewHeightPreferenceValue: Int,
    val candidateViewHeightEmptyPreferenceValue: Int,
    val tenkeyPositionPreferenceValue: Boolean,
    val tenkeyBottomMarginPreferenceValue: Int,
    val qwertyPositionPreferenceValue: Boolean,
    val qwertyBottomMarginPreferenceValue: Int,
    val tenkeyStartMarginPreferenceValue: Int,
    val tenkeyEndMarginPreferenceValue: Int,
    val qwertyStartMarginPreferenceValue: Int,
    val qwertyEndMarginPreferenceValue: Int,
    val tenkeyLandscapeStartMarginPreferenceValue: Int,
    val tenkeyLandscapeEndMarginPreferenceValue: Int,
    val qwertyLandscapeStartMarginPreferenceValue: Int,
    val qwertyLandscapeEndMarginPreferenceValue: Int,
    val enableShowLastShownKeyboardInRestart: Boolean,
    val lastSavedKeyboardPosition: Int,
    val tenkeyHeightLandscapePreferenceValue: Int,
    val tenkeyWidthLandscapePreferenceValue: Int,
    val qwertyHeightLandscapePreferenceValue: Int,
    val qwertyWidthLandscapePreferenceValue: Int,
    val candidateViewLandscapeHeightPreferenceValue: Int,
    val candidateViewLandscapeHeightEmptyPreferenceValue: Int,
    val tenkeyLandscapePositionPreferenceValue: Boolean,
    val tenkeyLandscapeBottomMarginPreferenceValue: Int,
    val qwertyLandscapePositionPreferenceValue: Boolean,
    val qwertyLandscapeBottomMarginPreferenceValue: Int,
    val zenzEnableStatePreference: Boolean,
    val zenzaiEnableStatePreference: Boolean,
    val zenzProfilePreference: String,
    val zenzEnableLongPressConversionPreference: Boolean,
    val zenzRerankPreference: Boolean,
    val qwertyKeyVerticalMargin: Float,
    val qwertyKeyHorizontalGap: Float,
    val qwertyKeyIndentLarge: Float,
    val qwertyKeyIndentSmall: Float,
    val qwertyKeySideMargin: Float,
    val qwertyKeyTextSize: Float,
    val qwertySymbolKeymapTextSize: Float,
    val qwertySpecialKeyTextSize: Float,
    val qwertySpecialKeyIconSize: Float,
    val keyboardThemeMode: String,
    val customThemeBgColor: Int,
    val customThemeKeyColor: Int,
    val customThemeSpecialKeyColor: Int,
    val customThemeKeyTextColor: Int,
    val customThemeSpecialKeyTextColor: Int,
    val customThemeCandidateTextColor: Int,
    val customThemeCandidateItemBgColor: Int,
    val customThemeCandidateItemPressedBgColor: Int,
    val customThemeCandidateEmptyPopupBgColor: Int,
    val customThemeCandidateEmptyPopupTextColor: Int,
    val customThemeShortcutIconColor: Int,
    val liquidGlassThemePreference: Boolean,
    val liquidGlassBlurRadiousPreference: Int,
    val liquidGlassKeyBlurRadiousPreference: Int,
    val keyboardTouchEffectTypePreference: String,
    val keyboardTouchEffectQualityPreference: String,
    val suminagashiInkEffectPreference: Boolean,
    val suminagashiInkColorModePreference: String,
    val suminagashiInkColorPreference: Int,
    val keyboardTouchEffectColorModePreference: String,
    val keyboardTouchEffectColorPreference: Int,
    val keyboardTouchEffectPalettePreference: String,
    val liquidInkDensityPreference: Int,
    val auroraInkDensityPreference: Int,
    val cinematicWaveColorModePreference: String,
    val cinematicWavePrimaryColorPreference: Int,
    val cinematicWaveSecondaryColorPreference: Int,
    val cinematicWaveSecondaryColorAutoPreference: Boolean,
    val cinematicWaveTypePreference: String,
    val cinematicWaveOpacityPercentPreference: Int,
    val cinematicWaveIntensityPercentPreference: Int,
    val cinematicWaveMotionPreference: String,
    val cinematicWaveTouchResponsePreference: String,
    val cinematicWaveQualityPreference: String,
    val customKeyBorderEnablePreference: Boolean,
    val customKeyBorderEnableColor: Int,
    val customComposingTextPreference: Boolean,
    val inputCompositionBackgroundColor: Int,
    val inputCompositionTextColor: Int,
    val inputConversionBackgroundColor: Int,
    val inputConversionTextColor: Int,
    val sumireEnglishQwertyPreference: Boolean,
    val conversionCandidatesRomajiEnablePreference: Boolean,
    val enableZenzRightContextPreference: Boolean,
    val learnFirstCandidateDictionaryPreference: Boolean,
    val enablePredictionSearchLearnDictionaryPreference: Boolean,
    val learnPredictionPreference: Int,
    val circularFlickWindowScale: Float,
    val circularFlickDirectionCount: Int,
    val hierarchicalFlickModeSwitchAngleMargin: Int,
    val customKeyBorderWidth: Int,
    val qwertySwitchNumberKeyWithoutNumberPreference: Boolean,
    val customRomajiZenkakuConversionEnablePreference: Boolean,
    val omissionSearchOffsetScorePreference: Int,
    val enableTypoCorrectionJapaneseFlickKeyboardOffsetScorePreference: Int,
    val enableTypoCorrectionJapaneseFlickKeyboardPreference: Boolean,
    val enableTypoCorrectionQwertyEnglishKeyboardPreference: Boolean,
    val enableGemmaTranslationPreference: Boolean,
) {
    companion object {
        fun from(
            appPreference: AppPreference,
            dictionarySourceResolver: DictionarySourceResolver? = null,
            customThemeCandidateItemPressedBgColorDefault: Int =
                AppPreference.DEFAULT_CUSTOM_THEME_CANDIDATE_ITEM_PRESSED_BG_COLOR
        ): ImePreferencesSnapshot {
            fun optionalEnabled(category: DictionaryCategory, legacyValue: Boolean?): Boolean {
                val state = dictionarySourceResolver?.resolveCategoryLoadState(category)
                return when (state) {
                    DictionaryCategoryLoadState.User,
                    DictionaryCategoryLoadState.Bundled -> true
                    DictionaryCategoryLoadState.Disabled,
                    DictionaryCategoryLoadState.Partial,
                    DictionaryCategoryLoadState.Invalid,
                    DictionaryCategoryLoadState.Missing -> false
                    null -> legacyValue ?: false
                }
            }
            return ImePreferencesSnapshot(
                keyboardOrder = appPreference.keyboard_order,
                candidateTabOrder = appPreference.candidate_tab_order,
                mozcUTPersonName = optionalEnabled(
                    DictionaryCategory.PERSON_NAME,
                    appPreference.mozc_ut_person_names_preference,
                ),
                mozcUTPlaces = optionalEnabled(
                    DictionaryCategory.PLACES,
                    appPreference.mozc_ut_places_preference,
                ),
                mozcUTWiki = optionalEnabled(
                    DictionaryCategory.WIKI,
                    appPreference.mozc_ut_wiki_preference,
                ),
                mozcUTNeologd = optionalEnabled(
                    DictionaryCategory.NEOLOGD,
                    appPreference.mozc_ut_neologd_preference,
                ),
                mozcUTWeb = optionalEnabled(
                    DictionaryCategory.WEB,
                    appPreference.mozc_ut_web_preference,
                ),
                isFlickOnlyMode = appPreference.flick_input_only_preference ?: false,
                isOmissionSearchEnable = appPreference.omission_search_preference ?: false,
                delayTime = appPreference.time_same_pronounce_typing_preference ?: 1000,
                isLearnDictionaryMode = appPreference.learn_dictionary_preference ?: true,
                incognitoModeDetectionPreference =
                    appPreference.incognito_mode_detection_preference,
                showLearnedCandidatesInIncognitoPreference =
                    appPreference.show_learned_candidates_in_incognito_preference,
                isUserDictionaryEnable = appPreference.user_dictionary_preference ?: true,
                isUserTemplateEnable = appPreference.user_template_preference ?: true,
                systemNgramDictionaryEnabled =
                    appPreference.system_ngram_dictionary_enable_preference,
                customNgramDictionaryEnabled =
                    appPreference.custom_ngram_dictionary_enable_preference,
                showDictionaryCandidateLabels =
                    appPreference.show_dictionary_candidate_labels_preference,
                suppressHentaiganaCandidates =
                    appPreference.suppress_hentaigana_candidates_preference,
                zeroQuerySuggestionPreference = appPreference.zero_query_suggestion_preference,
                hankakuPreference = appPreference.space_hankaku_preference ?: false,
                customDirectModeSpaceHankakuPreference =
                    appPreference.custom_direct_mode_space_hankaku_preference ?: true,
                isLiveConversionEnable = appPreference.live_conversion_preference ?: false,
                liveConversionStartLength =
                    appPreference.live_conversion_start_length_preference ?: 1,
                showLiveConversionCandidateYomi =
                    appPreference.live_conversion_candidate_yomi_preference ?: false,
                nBest = appPreference.n_best_preference ?: 4,
                conversionBeamWidth = appPreference.conversion_beam_width_preference,
                flickSensitivityPreferenceValue = appPreference.flick_sensitivity_preference ?: 100,
                longPressTimeoutPreferenceValue =
                    appPreference.long_press_timeout_preference ?: 300,
                qwertyShowIMEButtonPreference = appPreference.qwerty_show_ime_button ?: true,
                qwertyShowEmojiButtonPreference = appPreference.qwerty_show_emoji_button ?: false,
                tenkeyShowIMEButtonPreference =
                    appPreference.tenkey_show_language_button_preference,
                qwertyShowCursorButtonsPreference = appPreference.qwerty_show_cursor_buttons
                    ?: false,
                qwertyShowNumberButtonsPreference = appPreference.qwerty_show_number_buttons
                    ?: false,
                qwertyShowSwitchRomajiEnglishPreference =
                    appPreference.qwerty_show_switch_romaji_english_button ?: true,
                qwertyEnglishDirectInputPreference =
                    appPreference.qwerty_english_direct_input_preference,
                qwertyGlideInputPreference = appPreference.qwerty_glide_input_preference,
                qwertyGlideCommitPreviousCandidateOnNewGlidePreference =
                    appPreference.qwerty_glide_commit_previous_candidate_on_new_glide_preference,
                qwertyGlideInsertSpaceAfterCommittingPreviousCandidatePreference =
                    appPreference.qwerty_glide_insert_space_after_committing_previous_candidate_preference,
                qwertyShowPopupWindowPreference = appPreference.qwerty_show_popup_window ?: true,
                qwertyEnableFlickUpPreference =
                    appPreference.qwerty_enable_flick_up_preference ?: false,
                qwertyEnableFlickDownPreference =
                    appPreference.qwerty_enable_flick_down_preference ?: false,
                qwertyNumberKeyFlickUpChars = appPreference.getQwertyNumberKeyFlickUpChars(),
                qwertyNumberKeyFlickDownChars = appPreference.getQwertyNumberKeyFlickDownChars(),
                qwertyEnableZenkakuSpacePreference =
                    appPreference.qwerty_enable_zenkaku_space_preference ?: false,
                qwertyRomajiHankakuNumberPreference =
                    appPreference.qwerty_romaji_hankaku_number_preference ?: false,
                qwertyRomajiHankakuSymbolPreference =
                    appPreference.qwerty_romaji_hankaku_symbol_preference ?: false,
                qwertyShowKutoutenButtonsPreference =
                    appPreference.qwerty_show_kutouten_buttons ?: false,
                showCandidateInPasswordPreference = appPreference.show_candidates_password ?: true,
                qwertyShowKeymapSymbolsPreference =
                    appPreference.qwerty_show_keymap_symbols ?: false,
                qwertyRomajiShiftConversionPreference =
                    appPreference.qwerty_romaji_shift_conversion_preference,
                tabletGojuonLayoutPreference =
                    appPreference.tablet_gojuon_layout_preference,
                isNgWordEnable = appPreference.ng_word_preference ?: true,
                deleteKeyHighLight = appPreference.delete_key_high_light_preference ?: true,
                customKeyboardSuggestionPreference =
                    appPreference.custom_keyboard_suggestion_preference ?: true,
                userDictionaryPrefixMatchNumber =
                    appPreference.user_dictionary_prefix_match_number_preference ?: 2,
                isVibration = appPreference.vibration_preference ?: true,
                vibrationTimingStr = appPreference.vibration_timing_preference ?: "both",
                isKeySoundEnabled = appPreference.key_sound_preference ?: false,
                keySoundVolumePercent = appPreference.key_sound_volume_percent_preference ?: 0,
                sumireInputKeyType =
                    appPreference.sumire_input_selection_preference ?: "flick-default",
                sumireInputKeyLayoutType = appPreference.sumire_input_method,
                sumireInputStyle = appPreference.sumire_keyboard_style,
                candidateColumns = appPreference.candidate_column_preference,
                candidateColumnsLandscape = appPreference.candidate_column_landscape_preference,
                candidateTabVisibility = appPreference.candidate_tab_preference,
                symbolKeyboardFirstItem = appPreference.symbol_mode_preference,
                defaultEmojiSkinTone = appPreference.default_emoji_skin_tone_preference,
                isCustomKeyboardTwoWordsOutputEnable =
                    appPreference.custom_keyboard_two_words_output ?: true,
                tenkeyQWERTYSwitchNumber =
                    appPreference.tenkey_qwerty_switch_number_layout ?: false,
                tenkeyUseThreeStateKeyboard =
                    appPreference.tenkey_use_three_state_keyboard_preference,
                tenkeySwitchNumberToQwertyNumberPreference =
                    appPreference.tenkey_switch_number_to_qwerty_number_preference,
                tenkeyRestoreInputModeOnRestart =
                    appPreference.tenkey_restore_input_mode_on_restart_preference,
                sumireRestoreInputModeOnRestart =
                    appPreference.sumire_restore_input_mode_on_restart_preference,
                tenkeyRestoreInputModeOnlyWithinTime =
                    appPreference.tenkey_restore_input_mode_only_within_time_preference,
                tenkeyRestoreInputModeTimeoutMinutes =
                    appPreference.tenkey_restore_input_mode_timeout_minutes_preference,
                tenkeyLastInputModeSavedAtEpochMillis =
                    appPreference.tenkey_last_input_mode_saved_at_epoch_millis_preference,
                sumireRestoreInputModeOnlyWithinTime =
                    appPreference.sumire_restore_input_mode_only_within_time_preference,
                sumireRestoreInputModeTimeoutMinutes =
                    appPreference.sumire_restore_input_mode_timeout_minutes_preference,
                sumireLastInputModeSavedAtEpochMillis =
                    appPreference.sumire_last_input_mode_saved_at_epoch_millis_preference,
                tenkeyLastInputModePreference =
                    appPreference.tenkey_last_input_mode_preference,
                tenkeyLastInputModePresentationPreference =
                    appPreference.tenkey_last_input_mode_presentation_preference,
                tenkeyLastQwertyNumberReturnTargetPreference =
                    appPreference.tenkey_last_qwerty_number_return_target_preference,
                sumireLastInputModePreference =
                    appPreference.sumire_last_input_mode_preference,
                sumireLastInputModePresentationPreference =
                    appPreference.sumire_last_input_mode_presentation_preference,
                tabletTenkeyQwertySwitchEnglish =
                    appPreference.tablet_tenkey_qwerty_switch_english_layout,
                tenkeyQKeymapGuide = appPreference.tenkey_keymap_guide_layout ?: false,
                flickKeymapGuide = appPreference.flick_keymap_guide_layout ?: false,
                flickGuideTextSizeSp = appPreference.flick_guide_text_size_sp_preference ?: 9,
                flickGuideMaxCharacters =
                    appPreference.flick_guide_max_characters_preference ?: 1,
                isKeyboardFloatingMode = appPreference.is_floating_mode ?: false,
                isKeyboardRounded = appPreference.keyboard_corner_round_preference,
                keyboardCornerRadiusDp =
                    appPreference.keyboard_corner_radius_dp_preference.coerceIn(0, 64),
                keyboardCornerTopLeft = appPreference.keyboard_corner_top_left_preference,
                keyboardCornerTopRight = appPreference.keyboard_corner_top_right_preference,
                keyboardCornerBottomLeft = appPreference.keyboard_corner_bottom_left_preference,
                keyboardCornerBottomRight = appPreference.keyboard_corner_bottom_right_preference,
                bunsetsuSeparation = appPreference.bunsetsu_separation_preference,
                bunsetsuCursorMove = appPreference.bunsetsu_cursor_move_preference,
                reconversionEnabled = appPreference.reconversion_preference,
                conversionKeySwipePreference =
                    appPreference.conversion_key_swipe_cursor_move_preference,
                physicalKeyboardInputMode = appPreference.physical_keyboard_input_mode_preference,
                switchQWERTYPassword = appPreference.switch_qwerty_password ?: false,
                landscapeForceQwertyPreference =
                    appPreference.landscape_force_qwerty_preference,
                landscapeForceQwertyRomajiPreference =
                    appPreference.landscape_force_qwerty_romaji_preference,
                shortcutTollbarVisibility =
                    appPreference.shortcut_toolbar_visibility_preference,
                shortcutToolbarIntegratedInSuggestion =
                    appPreference.shortcut_toolbar_integrated_in_suggestion_preference,
                shortcutToolbarHeightDp =
                    appPreference.shortcut_toolbar_height_dp_preference,
                shortcutToolbarIconSizeDp =
                    appPreference.shortcut_toolbar_icon_size_dp_preference,
                isDeleteLeftFlickPreference = appPreference.delete_key_left_flick_preference,
                isDeleteUpFlickPreference = appPreference.delete_key_up_flick_preference,
                isDeleteDownFlickPreference = appPreference.delete_key_down_flick_preference,
                zenzDebounceTimePreference =
                    appPreference.zenz_debounce_time_preference ?: 300,
                zenzMaximumLetterSizePreference =
                    appPreference.zenz_maximum_letter_size_preference ?: 32,
                zenzMaximumContextSizePreference =
                    appPreference.zenz_maximum_context_size_preference ?: 512,
                zenzMaximumThreadSizePreference =
                    appPreference.zenz_maximum_thread_size_preference ?: 4,
                clipboardPreviewVisibility = appPreference.clipboard_preview_preference,
                clipboardPreviewTapToDelete = appPreference.clipboard_preview_tap_delete_preference,
                tenkeyHeightPreferenceValue = appPreference.keyboard_height ?: 280,
                tenkeyWidthPreferenceValue = appPreference.keyboard_width ?: 100,
                qwertyHeightPreferenceValue = appPreference.qwerty_keyboard_height ?: 280,
                qwertyWidthPreferenceValue = appPreference.qwerty_keyboard_width ?: 100,
                candidateViewHeightPreferenceValue = appPreference.candidate_view_height_dp ?: 110,
                candidateViewHeightEmptyPreferenceValue =
                    appPreference.candidate_view_empty_height_dp ?: 110,
                tenkeyPositionPreferenceValue = appPreference.keyboard_position ?: true,
                tenkeyBottomMarginPreferenceValue =
                    appPreference.keyboard_vertical_margin_bottom ?: 0,
                qwertyPositionPreferenceValue =
                    appPreference.qwerty_keyboard_position ?: true,
                qwertyBottomMarginPreferenceValue =
                    appPreference.qwerty_keyboard_vertical_margin_bottom ?: 0,
                tenkeyStartMarginPreferenceValue =
                    appPreference.keyboard_margin_start_dp ?: 0,
                tenkeyEndMarginPreferenceValue = appPreference.keyboard_margin_end_dp ?: 0,
                qwertyStartMarginPreferenceValue =
                    appPreference.qwerty_keyboard_margin_start_dp ?: 0,
                qwertyEndMarginPreferenceValue =
                    appPreference.qwerty_keyboard_margin_end_dp ?: 0,
                tenkeyLandscapeStartMarginPreferenceValue =
                    appPreference.keyboard_margin_start_dp_landscape ?: 0,
                tenkeyLandscapeEndMarginPreferenceValue =
                    appPreference.keyboard_margin_end_dp_landscape ?: 0,
                qwertyLandscapeStartMarginPreferenceValue =
                    appPreference.qwerty_keyboard_margin_start_dp_landscape ?: 0,
                qwertyLandscapeEndMarginPreferenceValue =
                    appPreference.qwerty_keyboard_margin_end_dp_landscape ?: 0,
                enableShowLastShownKeyboardInRestart =
                    appPreference.save_last_used_keyboard_enable_preference,
                lastSavedKeyboardPosition =
                    appPreference.save_last_used_keyboard_position_preference,
                tenkeyHeightLandscapePreferenceValue =
                    appPreference.keyboard_height_landscape ?: 280,
                tenkeyWidthLandscapePreferenceValue =
                    appPreference.keyboard_width_landscape ?: 100,
                qwertyHeightLandscapePreferenceValue =
                    appPreference.qwerty_keyboard_height_landscape ?: 280,
                qwertyWidthLandscapePreferenceValue =
                    appPreference.qwerty_keyboard_width_landscape ?: 100,
                candidateViewLandscapeHeightPreferenceValue =
                    appPreference.candidate_view_height_dp_landscape ?: 110,
                candidateViewLandscapeHeightEmptyPreferenceValue =
                    appPreference.candidate_view_empty_height_dp_landscape ?: 110,
                tenkeyLandscapePositionPreferenceValue =
                    appPreference.keyboard_position_landscape ?: true,
                tenkeyLandscapeBottomMarginPreferenceValue =
                    appPreference.keyboard_vertical_margin_bottom_landscape ?: 0,
                qwertyLandscapePositionPreferenceValue =
                    appPreference.qwerty_keyboard_position_landscape ?: true,
                qwertyLandscapeBottomMarginPreferenceValue =
                    appPreference.qwerty_keyboard_vertical_margin_bottom_landscape ?: 0,
                zenzEnableStatePreference =
                    AppVariantConfig.hasZenz && appPreference.enable_zenz_preference,
                zenzaiEnableStatePreference =
                    AppVariantConfig.hasZenz && appPreference.enable_zenzai_preference,
                zenzProfilePreference = appPreference.zenz_profile_preference,
                zenzEnableLongPressConversionPreference =
                    AppVariantConfig.hasZenz && appPreference.enable_zenz_long_press_preference,
                zenzRerankPreference =
                    AppVariantConfig.hasZenz && appPreference.enable_zenz_rerank_preference,
                qwertyKeyVerticalMargin = appPreference.qwerty_key_vertical_margin ?: 5.0f,
                qwertyKeyHorizontalGap = appPreference.qwerty_key_horizontal_gap ?: 2.0f,
                qwertyKeyIndentLarge = appPreference.qwerty_key_indent_large ?: 23.0f,
                qwertyKeyIndentSmall = appPreference.qwerty_key_indent_small ?: 9.0f,
                qwertyKeySideMargin = appPreference.qwerty_key_side_margin ?: 4.0f,
                qwertyKeyTextSize = appPreference.qwerty_key_text_size ?: 18.0f,
                qwertySymbolKeymapTextSize =
                    appPreference.qwerty_symbol_keymap_text_size ?: 9.0f,
                qwertySpecialKeyTextSize = appPreference.qwerty_special_key_text_size ?: 12.0f,
                qwertySpecialKeyIconSize = appPreference.qwerty_special_key_icon_size ?: 18.0f,
                keyboardThemeMode = appPreference.theme_mode,
                customThemeBgColor = appPreference.custom_theme_bg_color,
                customThemeKeyColor = appPreference.custom_theme_key_color,
                customThemeSpecialKeyColor = appPreference.custom_theme_special_key_color,
                customThemeKeyTextColor = appPreference.custom_theme_key_text_color,
                customThemeSpecialKeyTextColor = appPreference.custom_theme_special_key_text_color,
                customThemeCandidateTextColor = appPreference.getCustomThemeCandidateTextColor(
                    appPreference.custom_theme_key_text_color
                ),
                customThemeCandidateItemBgColor = appPreference.getCustomThemeCandidateItemBgColor(
                    AppPreference.DEFAULT_CUSTOM_THEME_CANDIDATE_ITEM_BG_COLOR
                ),
                customThemeCandidateItemPressedBgColor =
                    appPreference.getCustomThemeCandidateItemPressedBgColor(
                        customThemeCandidateItemPressedBgColorDefault
                    ),
                customThemeCandidateEmptyPopupBgColor =
                    appPreference.getCustomThemeCandidateEmptyPopupBgColor(
                        appPreference.custom_theme_special_key_color
                    ),
                customThemeCandidateEmptyPopupTextColor =
                    appPreference.getCustomThemeCandidateEmptyPopupTextColor(
                        appPreference.custom_theme_special_key_text_color
                    ),
                customThemeShortcutIconColor = appPreference.getCustomThemeShortcutIconColor(
                    appPreference.custom_theme_special_key_text_color
                ),
                liquidGlassThemePreference = appPreference.liquid_glass_preference,
                liquidGlassBlurRadiousPreference = appPreference.liquid_glass_blur_radius,
                liquidGlassKeyBlurRadiousPreference = appPreference.liquid_glass_key_alpha,
                keyboardTouchEffectTypePreference =
                    appPreference.keyboard_touch_effect_type_preference,
                keyboardTouchEffectQualityPreference =
                    appPreference.keyboard_touch_effect_quality_preference,
                suminagashiInkEffectPreference = appPreference.suminagashi_ink_effect_preference,
                suminagashiInkColorModePreference =
                    appPreference.keyboard_touch_effect_color_mode_preference,
                suminagashiInkColorPreference = appPreference.keyboard_touch_effect_color_preference,
                keyboardTouchEffectColorModePreference =
                    appPreference.keyboard_touch_effect_color_mode_preference,
                keyboardTouchEffectColorPreference = appPreference.keyboard_touch_effect_color_preference,
                keyboardTouchEffectPalettePreference =
                    appPreference.keyboard_touch_effect_palette_preference,
                liquidInkDensityPreference =
                    appPreference.keyboard_touch_effect_liquid_ink_density_preference,
                auroraInkDensityPreference =
                    appPreference.keyboard_touch_effect_aurora_ink_density_preference,
                cinematicWaveColorModePreference =
                    appPreference.keyboard_touch_effect_cinematic_wave_color_mode_preference,
                cinematicWavePrimaryColorPreference =
                    appPreference.keyboard_touch_effect_cinematic_wave_primary_color_preference,
                cinematicWaveSecondaryColorPreference =
                    appPreference.keyboard_touch_effect_cinematic_wave_secondary_color_preference,
                cinematicWaveSecondaryColorAutoPreference =
                    appPreference
                        .keyboard_touch_effect_cinematic_wave_secondary_color_auto_preference,
                cinematicWaveTypePreference =
                    appPreference.keyboard_touch_effect_cinematic_wave_type_preference,
                cinematicWaveOpacityPercentPreference =
                    appPreference.keyboard_touch_effect_cinematic_wave_opacity_percent_preference,
                cinematicWaveIntensityPercentPreference =
                    appPreference.keyboard_touch_effect_cinematic_wave_intensity_percent_preference,
                cinematicWaveMotionPreference =
                    appPreference.keyboard_touch_effect_cinematic_wave_motion_preference,
                cinematicWaveTouchResponsePreference =
                    appPreference.keyboard_touch_effect_cinematic_wave_touch_response_preference,
                cinematicWaveQualityPreference =
                    appPreference.keyboard_touch_effect_cinematic_wave_quality_preference,
                customKeyBorderEnablePreference = appPreference.custom_theme_border_enable,
                customKeyBorderEnableColor = appPreference.custom_theme_border_color,
                customComposingTextPreference = appPreference.custom_theme_input_color_enable,
                inputCompositionBackgroundColor = appPreference.custom_theme_pre_edit_bg_color,
                inputCompositionTextColor = appPreference.custom_theme_pre_edit_text_color,
                inputConversionBackgroundColor = appPreference.custom_theme_post_edit_bg_color,
                inputConversionTextColor = appPreference.custom_theme_post_edit_text_color,
                sumireEnglishQwertyPreference =
                    appPreference.sumire_english_qwerty_preference,
                conversionCandidatesRomajiEnablePreference =
                    appPreference.conversion_candidates_romaji_enable_preference,
                enableZenzRightContextPreference =
                    AppVariantConfig.hasZenz && appPreference.enable_zenz_right_context_preference,
                learnFirstCandidateDictionaryPreference =
                    appPreference.learn_first_candidate_dictionary_preference,
                enablePredictionSearchLearnDictionaryPreference =
                    appPreference.enable_prediction_search_learn_dictionary_preference,
                learnPredictionPreference = appPreference.learn_prediction_preference,
                circularFlickWindowScale = appPreference.circular_flickWindow_scale,
                circularFlickDirectionCount = appPreference.circularFlickDirectionCount,
                hierarchicalFlickModeSwitchAngleMargin =
                    appPreference.hierarchical_flick_mode_switch_angle_margin_preference,
                customKeyBorderWidth = appPreference.custom_theme_border_width,
                qwertySwitchNumberKeyWithoutNumberPreference =
                    appPreference.qwerty_switch_number_key_without_number_preference,
                customRomajiZenkakuConversionEnablePreference =
                    appPreference.custom_romaji_zenkaku_conversion_enable_preference,
                omissionSearchOffsetScorePreference =
                    appPreference.omission_search_offset_score_preference,
                enableTypoCorrectionJapaneseFlickKeyboardOffsetScorePreference =
                    appPreference.enable_typo_correction_japanese_flick_keyboard_offset_score_preference,
                enableTypoCorrectionJapaneseFlickKeyboardPreference =
                    appPreference.enable_typo_correction_japanese_flick_keyboard_preference,
                enableTypoCorrectionQwertyEnglishKeyboardPreference =
                    appPreference.enable_typo_correction_qwerty_english_keyboard_preference,
                enableGemmaTranslationPreference =
                    AppVariantConfig.hasGemma && appPreference.enable_gemma_translation_preference
            )
        }
    }
}
