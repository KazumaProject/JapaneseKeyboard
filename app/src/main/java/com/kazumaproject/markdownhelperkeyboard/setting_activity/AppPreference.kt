package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType

object AppPreference {

    private lateinit var preferences: SharedPreferences
    private val gson = Gson()

    private val CLIPBOARD_HISTORY_ENABLE = Pair("clipboard_history_preference", false)
    private val TIME_SAME_PRONOUNCE_TYPING = Pair("time_same_pronounce_typing_preference", 1000)
    private val FLICK_SENSITIVITY = Pair("flick_sensitivity_preference", 100)
    private val VIBRATION_PREFERENCE = Pair("vibration_preference", true)
    private val VIBRATION_TIMING_PREFERENCE = Pair("vibration_timing", "both")
    private val LEARN_DICTIONARY_PREFERENCE = Pair("learn_dictionary_preference", true)
    private val USER_DICTIONARY_PREFERENCE = Pair("user_dictionary_preference", true)
    private val USER_DICTIONARY_PREFIX_PREFERENCE = Pair("user_dictionary_prefix_match_number", 2)
    private val USER_TEMPLATE_PREFERENCE = Pair("user_template_preference", true)
    private val NG_WORD_ENABLE_PREFERENCE = Pair("ng_word_enable_preference", true)
    private val N_BEST_PREFERENCE = Pair("n_best_preference", 4)
    private val MOZCUT_PERSON_NAME = Pair("mozc_ut_person_name_preference", false)
    private val MOZCUT_PLACES = Pair("mozc_ut_places_preference", false)
    private val MOZCUT_WIKI = Pair("mozc_ut_wiki_preference", false)
    private val MOZCUT_NEOLOGD = Pair("mozc_ut_neologd_preference", false)
    private val MOZCUT_WEB = Pair("mozc_ut_web_preference", false)

    private val SWITCH_QWERTY_PASSWORD = Pair("switch_qwerty_keyboard_password_preference", false)

    private val TENKEY_SWITCH_QWERTY_PREFERENCE =
        Pair("tenkey_kana_english_qwerty_preference", false)

    private val CUSTOM_KEYBOARD_TWO_WORDS_OUTPUTS =
        Pair("custom_keyboard_two_words_preference", true)

    private val QWERTY_SHOW_IME_SWITCH_BUTTON =
        Pair("qwerty_show_switch_ime_button_preference", true)

    private val QWERTY_SHOW_CURSOR_BUTTONS = Pair("qwerty_show_cursor_buttons_preference", false)

    private val QWERTY_SHOW_KEYMAP_SYMBOLS =
        Pair("qwerty_show_keymap_symbols_romaji_preference", false)

    private val QWERTY_SHOW_NUMBER_BUTTONS =
        Pair("qwerty_show_number_keys_buttons_preference", false)

    private val QWERTY_SHOW_SWITCH_ROMAJI_ENGLISH =
        Pair("qwerty_show_switch_romaji_english_preference", true)

    private val QWERTY_SHOW_POPUP_WINDOW = Pair("qwerty_show_popup_window_preference", true)

    private val CANDIDATE_IN_PASSWORD = Pair("hide_candidate_password_preference", true)

    private val CANDIDATE_IN_PASSWORD_COMPOSE = Pair("password_compose_preference", false)

    private val QWERTY_SHOW_KUTOUTEN_BUTTONS =
        Pair("qwerty_show_kutouten_buttons_preference", false)

    private val KEYBOARD_HEIGHT = Pair("keyboard_height_preference", 220)
    private val KEYBOARD_WIDTH = Pair("keyboard_width_preference", 100)
    private val KEYBOARD_POSITION = Pair("keyboard_position_preference", true)
    private val KEYBOARD_VERTICAL_MARGIN_BOTTOM =
        Pair("keyboard_vertical_margin_bottom_preference", 0)
    private val KEYBOARD_FLOATING_PREFERENCE = Pair("keyboard_floating_preference", false)
    private val QWERTY_KEYBOARD_HEIGHT = Pair("qwerty_keyboard_height_preference", 220)
    private val QWERTY_KEYBOARD_WIDTH = Pair("qwerty_keyboard_width_preference", 100)
    private val QWERTY_KEYBOARD_VERTICAL_MARGIN_BOTTOM =
        Pair("qwerty_keyboard_vertical_margin_bottom_preference", 0)
    private val QWERTY_KEYBOARD_POSITION = Pair("qwerty_keyboard_position_preference", true)

    private val KEYBOARD_HEIGHT_LANDSCAPE = Pair("keyboard_height_landscape_preference", 220)
    private val KEYBOARD_WIDTH_LANDSCAPE = Pair("keyboard_width_landscape_preference", 100)
    private val KEYBOARD_POSITION_LANDSCAPE = Pair("keyboard_position_landscape_preference", true)
    private val KEYBOARD_VERTICAL_MARGIN_BOTTOM_LANDSCAPE =
        Pair("keyboard_vertical_margin_bottom_landscape_preference", 0)
    private val QWERTY_KEYBOARD_HEIGHT_LANDSCAPE =
        Pair("qwerty_keyboard_height_landscape_preference", 220)
    private val QWERTY_KEYBOARD_WIDTH_LANDSCAPE =
        Pair("qwerty_keyboard_width_landscape_preference", 100)
    private val QWERTY_KEYBOARD_VERTICAL_MARGIN_BOTTOM_LANDSCAPE =
        Pair("qwerty_keyboard_vertical_margin_bottom_landscape_preference", 0)
    private val QWERTY_KEYBOARD_POSITION_LANDSCAPE =
        Pair("qwerty_keyboard_position_landscape_preference", true)

    private val CANDIDATE_VIEW_HEIGHT_DP_LANDSCAPE =
        Pair("candidate_view_height_dp_landscape_preference", 110)
    private val CANDIDATE_VIEW_EMPTY_HEIGHT_DP_LANDSCAPE =
        Pair("candidate_view_empty_height_dp_landscape_preference", 110)

    private val FLICK_INPUT_ONLY = Pair("flick_input_only_preference", false)
    private val OMISSION_SEARCH = Pair("omission_search_preference", false)
    private val UNDO_ENABLE = Pair("undo_enable_preference", false)
    private val SPACE_HANKAKU_ENABLE = Pair("space_key_preference", false)
    private val LIVE_CONVERSION_ENABLE = Pair("live_conversion_preference", false)
    private const val OLD_SUMIRE_PREFERENCE_KEY = "sumire_keyboard_input_type_preference"
    private const val NEW_SUMIRE_STYLE_KEY = "sumire_keyboard_style_preference"
    private const val NEW_SUMIRE_METHOD_KEY = "sumire_input_method_preference"
    private val SUMIRE_INPUT_SELECTION_PREFERENCE =
        Pair("sumire_keyboard_input_type_preference", "toggle-default")

    private val DELETE_KEY_HIGH_LIGHT = Pair("henkan_delete_key_action_preference", true)
    private val CUSTOM_KEYBOARD_SUGGESTION_PREFERENCE =
        Pair("custom_keyboard_suggestion_preference", true)

    private val KEYBOARD_FLOATING_POSITION_X = Pair("keyboard_floating_position_x", -1)
    private val KEYBOARD_FLOATING_POSITION_Y = Pair("keyboard_floating_position_y", -1)

    private val defaultKeyboardOrderJson = gson.toJson(
        listOf(
            KeyboardType.TENKEY,
            KeyboardType.SUMIRE,
            KeyboardType.QWERTY,
            KeyboardType.ROMAJI,
            KeyboardType.CUSTOM
        )
    )
    private val KEYBOARD_ORDER = Pair("keyboard_order_preference", defaultKeyboardOrderJson)
    private val SYMBOL_MODE_PREFERENCE = Pair("symbol_mode_preference", "EMOJI")

    private val CANDIDATE_COLUMN_PREFERENCE = Pair("candidate_column_preference", "1")
    private val CANDIDATE_COLUMN_LANDSCAPE_PREFERENCE =
        Pair("candidate_column_landscape_preference", "1")

    private val CANDIDATE_TAB_PREFERENCE = Pair("candidate_tab_visibility_preference", false)

    private val SHORTCUT_TOOLBAR_VISIBILITY_PREFERENCE =
        Pair("shortcut_toolbar_visibility_preference", false)

    private val APP_THEME_SEED_COLOR = Pair("app_theme_seed_color_preference", 0x00000000)

    private val DELETE_KEY_LEFT_FLICK_PREFERENCE = Pair("delete_key_flick_left_preference", true)

    private val KEY_LETTER_SIZE = Pair("key_letter_size_preference", 0.0f)

    private val CANDIDATE_LETTER_SIZE = Pair("candidate_letter_size_preference", 14.0f)

    private val CANDIDATE_VIEW_HEIGHT_DP = Pair("candidate_view_height_dp_preference", 110)
    private val CANDIDATE_VIEW_EMPTY_HEIGHT_DP =
        Pair("candidate_view_empty_height_dp_preference", 110)

    private val CLIP_BOARD_PREVIEW_PREFERENCE =
        Pair("clipboard_preview_enable_preference", true)

    private val ROUND_KEYBOARD_CORNER_PREFERENCE =
        Pair("round_corner_keyboard_preference", false)

    private val BUNSETSU_SEPARATION_PREFERENCE =
        Pair("conversion_bunsetsu_separation_preference", false)

    private val CONVERSION_KEY_SWIPE_CURSOR_MOVE_PREFERENCE =
        Pair("conversion_key_swipe_cursor_move_preference", false)

    fun init(context: Context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
    }

    private inline fun SharedPreferences.edit(operation: (SharedPreferences.Editor) -> Unit) {
        val editor = edit()
        operation(editor)
        editor.apply()
    }

    var clipboard_history_enable: Boolean?
        get() = preferences.getBoolean(
            CLIPBOARD_HISTORY_ENABLE.first, CLIPBOARD_HISTORY_ENABLE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CLIPBOARD_HISTORY_ENABLE.first, value ?: false)
        }

    var custom_keyboard_two_words_output: Boolean?
        get() = preferences.getBoolean(
            CUSTOM_KEYBOARD_TWO_WORDS_OUTPUTS.first, CUSTOM_KEYBOARD_TWO_WORDS_OUTPUTS.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CUSTOM_KEYBOARD_TWO_WORDS_OUTPUTS.first, value ?: false)
        }

    var tenkey_qwerty_switch_number_layout: Boolean?
        get() = preferences.getBoolean(
            TENKEY_SWITCH_QWERTY_PREFERENCE.first, TENKEY_SWITCH_QWERTY_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(TENKEY_SWITCH_QWERTY_PREFERENCE.first, value ?: false)
        }

    var qwerty_show_ime_button: Boolean?
        get() = preferences.getBoolean(
            QWERTY_SHOW_IME_SWITCH_BUTTON.first, QWERTY_SHOW_IME_SWITCH_BUTTON.second
        )
        set(value) = preferences.edit {
            it.putBoolean(QWERTY_SHOW_IME_SWITCH_BUTTON.first, value ?: true)
        }

    var qwerty_show_cursor_buttons: Boolean?
        get() = preferences.getBoolean(
            QWERTY_SHOW_CURSOR_BUTTONS.first, QWERTY_SHOW_CURSOR_BUTTONS.second
        )
        set(value) = preferences.edit {
            it.putBoolean(QWERTY_SHOW_CURSOR_BUTTONS.first, value ?: false)
        }

    var qwerty_show_keymap_symbols: Boolean?
        get() = preferences.getBoolean(
            QWERTY_SHOW_KEYMAP_SYMBOLS.first, QWERTY_SHOW_KEYMAP_SYMBOLS.second
        )
        set(value) = preferences.edit {
            it.putBoolean(QWERTY_SHOW_KEYMAP_SYMBOLS.first, value ?: false)
        }

    var qwerty_show_number_buttons: Boolean?
        get() = preferences.getBoolean(
            QWERTY_SHOW_NUMBER_BUTTONS.first, QWERTY_SHOW_NUMBER_BUTTONS.second
        )
        set(value) = preferences.edit {
            it.putBoolean(QWERTY_SHOW_NUMBER_BUTTONS.first, value ?: false)
        }

    var qwerty_show_switch_romaji_english_button: Boolean?
        get() = preferences.getBoolean(
            QWERTY_SHOW_SWITCH_ROMAJI_ENGLISH.first, QWERTY_SHOW_SWITCH_ROMAJI_ENGLISH.second
        )
        set(value) = preferences.edit {
            it.putBoolean(QWERTY_SHOW_SWITCH_ROMAJI_ENGLISH.first, value ?: true)
        }

    var switch_qwerty_password: Boolean?
        get() = preferences.getBoolean(
            SWITCH_QWERTY_PASSWORD.first, SWITCH_QWERTY_PASSWORD.second
        )
        set(value) = preferences.edit {
            it.putBoolean(SWITCH_QWERTY_PASSWORD.first, value ?: false)
        }

    var qwerty_show_popup_window: Boolean?
        get() = preferences.getBoolean(
            QWERTY_SHOW_POPUP_WINDOW.first, QWERTY_SHOW_POPUP_WINDOW.second
        )
        set(value) = preferences.edit {
            it.putBoolean(QWERTY_SHOW_POPUP_WINDOW.first, value ?: true)
        }

    var show_candidates_password: Boolean?
        get() = preferences.getBoolean(
            CANDIDATE_IN_PASSWORD.first, CANDIDATE_IN_PASSWORD.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CANDIDATE_IN_PASSWORD.first, value ?: true)
        }

    var show_candidates_password_compose: Boolean?
        get() = preferences.getBoolean(
            CANDIDATE_IN_PASSWORD_COMPOSE.first, CANDIDATE_IN_PASSWORD_COMPOSE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CANDIDATE_IN_PASSWORD_COMPOSE.first, value ?: false)
        }

    var qwerty_show_kutouten_buttons: Boolean?
        get() = preferences.getBoolean(
            QWERTY_SHOW_KUTOUTEN_BUTTONS.first, QWERTY_SHOW_KUTOUTEN_BUTTONS.second
        )
        set(value) = preferences.edit {
            it.putBoolean(QWERTY_SHOW_KUTOUTEN_BUTTONS.first, value ?: false)
        }

    var keyboard_order: List<KeyboardType>
        get() {
            val json = preferences.getString(KEYBOARD_ORDER.first, KEYBOARD_ORDER.second)
            val type = object : TypeToken<List<KeyboardType>>() {}.type
            return gson.fromJson(json, type)
        }
        set(value) = preferences.edit {
            val json = gson.toJson(value)
            it.putString(KEYBOARD_ORDER.first, json)
        }

    var symbol_mode_preference: SymbolMode
        get() {
            val modeString = preferences.getString(
                SYMBOL_MODE_PREFERENCE.first, SYMBOL_MODE_PREFERENCE.second
            )
            return try {
                // 保存されている文字列からEnumを復元
                SymbolMode.valueOf(modeString ?: SYMBOL_MODE_PREFERENCE.second)
            } catch (e: IllegalArgumentException) {
                // 不正な値が保存されていた場合はデフォルト値を返す
                SymbolMode.valueOf(SYMBOL_MODE_PREFERENCE.second)
            }
        }
        set(value) = preferences.edit {
            // Enumを文字列として保存
            it.putString(SYMBOL_MODE_PREFERENCE.first, value.name)
        }

    var vibration_preference: Boolean?
        get() = preferences.getBoolean(VIBRATION_PREFERENCE.first, VIBRATION_PREFERENCE.second)
        set(value) = preferences.edit {
            it.putBoolean(VIBRATION_PREFERENCE.first, value ?: true)
        }

    var ng_word_preference: Boolean?
        get() = preferences.getBoolean(
            NG_WORD_ENABLE_PREFERENCE.first, NG_WORD_ENABLE_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(NG_WORD_ENABLE_PREFERENCE.first, value ?: true)
        }

    var vibration_timing_preference: String?
        get() = preferences.getString(
            VIBRATION_TIMING_PREFERENCE.first, VIBRATION_TIMING_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putString(VIBRATION_TIMING_PREFERENCE.first, value ?: "both")
        }

    var flick_sensitivity_preference: Int?
        get() = preferences.getInt(
            FLICK_SENSITIVITY.first, FLICK_SENSITIVITY.second
        )
        set(value) = preferences.edit {
            it.putInt(FLICK_SENSITIVITY.first, value ?: 100)
        }

    var n_best_preference: Int?
        get() = preferences.getInt(
            N_BEST_PREFERENCE.first, N_BEST_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putInt(N_BEST_PREFERENCE.first, value ?: 4)
        }

    var learn_dictionary_preference: Boolean?
        get() = preferences.getBoolean(
            LEARN_DICTIONARY_PREFERENCE.first, LEARN_DICTIONARY_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(LEARN_DICTIONARY_PREFERENCE.first, value ?: true)
        }

    var user_dictionary_preference: Boolean?
        get() = preferences.getBoolean(
            USER_DICTIONARY_PREFERENCE.first, USER_DICTIONARY_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(USER_DICTIONARY_PREFERENCE.first, value ?: true)
        }

    var user_dictionary_prefix_match_number_preference: Int?
        get() = preferences.getInt(
            USER_DICTIONARY_PREFIX_PREFERENCE.first, USER_DICTIONARY_PREFIX_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putInt(USER_DICTIONARY_PREFIX_PREFERENCE.first, value ?: 2)
        }

    var user_template_preference: Boolean?
        get() = preferences.getBoolean(
            USER_TEMPLATE_PREFERENCE.first, USER_TEMPLATE_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(USER_TEMPLATE_PREFERENCE.first, value ?: true)
        }

    var time_same_pronounce_typing_preference: Int?
        get() = preferences.getInt(
            TIME_SAME_PRONOUNCE_TYPING.first, TIME_SAME_PRONOUNCE_TYPING.second
        )
        set(value) = preferences.edit {
            it.putInt(TIME_SAME_PRONOUNCE_TYPING.first, value ?: 1000)
        }

    var mozc_ut_person_names_preference: Boolean?
        get() = preferences.getBoolean(MOZCUT_PERSON_NAME.first, MOZCUT_PERSON_NAME.second)
        set(value) = preferences.edit {
            it.putBoolean(MOZCUT_PERSON_NAME.first, value ?: false)
        }

    var mozc_ut_places_preference: Boolean?
        get() = preferences.getBoolean(MOZCUT_PLACES.first, MOZCUT_PLACES.second)
        set(value) = preferences.edit {
            it.putBoolean(MOZCUT_PLACES.first, value ?: false)
        }

    var mozc_ut_wiki_preference: Boolean?
        get() = preferences.getBoolean(MOZCUT_WIKI.first, MOZCUT_WIKI.second)
        set(value) = preferences.edit {
            it.putBoolean(MOZCUT_WIKI.first, value ?: false)
        }

    var mozc_ut_neologd_preference: Boolean?
        get() = preferences.getBoolean(MOZCUT_NEOLOGD.first, MOZCUT_NEOLOGD.second)
        set(value) = preferences.edit {
            it.putBoolean(MOZCUT_NEOLOGD.first, value ?: false)
        }

    var mozc_ut_web_preference: Boolean?
        get() = preferences.getBoolean(MOZCUT_WEB.first, MOZCUT_WEB.second)
        set(value) = preferences.edit {
            it.putBoolean(MOZCUT_WEB.first, value ?: false)
        }

    var keyboard_height: Int?
        get() = preferences.getInt(
            KEYBOARD_HEIGHT.first, KEYBOARD_HEIGHT.second
        )
        set(value) = preferences.edit {
            it.putInt(KEYBOARD_HEIGHT.first, value ?: 280)
        }

    var keyboard_width: Int?
        get() = preferences.getInt(
            KEYBOARD_WIDTH.first, KEYBOARD_WIDTH.second
        )
        set(value) = preferences.edit {
            it.putInt(KEYBOARD_WIDTH.first, value ?: 100)
        }

    var qwerty_keyboard_height: Int?
        get() = preferences.getInt(
            QWERTY_KEYBOARD_HEIGHT.first, QWERTY_KEYBOARD_HEIGHT.second
        )
        set(value) = preferences.edit {
            it.putInt(QWERTY_KEYBOARD_HEIGHT.first, value ?: 220)
        }

    var qwerty_keyboard_width: Int?
        get() = preferences.getInt(
            QWERTY_KEYBOARD_WIDTH.first, QWERTY_KEYBOARD_WIDTH.second
        )
        set(value) = preferences.edit {
            it.putInt(QWERTY_KEYBOARD_WIDTH.first, value ?: 100)
        }

    var qwerty_keyboard_vertical_margin_bottom: Int?
        get() = preferences.getInt(
            QWERTY_KEYBOARD_VERTICAL_MARGIN_BOTTOM.first,
            QWERTY_KEYBOARD_VERTICAL_MARGIN_BOTTOM.second
        )
        set(value) = preferences.edit {
            it.putInt(QWERTY_KEYBOARD_VERTICAL_MARGIN_BOTTOM.first, value ?: 0)
        }

    var keyboard_position: Boolean?
        get() = preferences.getBoolean(KEYBOARD_POSITION.first, KEYBOARD_POSITION.second)
        set(value) = preferences.edit {
            it.putBoolean(KEYBOARD_POSITION.first, value ?: true)
        }

    var qwerty_keyboard_position: Boolean?
        get() = preferences.getBoolean(
            QWERTY_KEYBOARD_POSITION.first,
            QWERTY_KEYBOARD_POSITION.second
        )
        set(value) = preferences.edit {
            it.putBoolean(QWERTY_KEYBOARD_POSITION.first, value ?: true)
        }

    var keyboard_vertical_margin_bottom: Int?
        get() = preferences.getInt(
            KEYBOARD_VERTICAL_MARGIN_BOTTOM.first, KEYBOARD_VERTICAL_MARGIN_BOTTOM.second
        )
        set(value) = preferences.edit {
            it.putInt(KEYBOARD_VERTICAL_MARGIN_BOTTOM.first, value ?: 0)
        }

    var keyboard_height_landscape: Int?
        get() = preferences.getInt(
            KEYBOARD_HEIGHT_LANDSCAPE.first, KEYBOARD_HEIGHT_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putInt(KEYBOARD_HEIGHT_LANDSCAPE.first, value ?: 220)
        }

    var keyboard_width_landscape: Int?
        get() = preferences.getInt(
            KEYBOARD_WIDTH_LANDSCAPE.first, KEYBOARD_WIDTH_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putInt(KEYBOARD_WIDTH_LANDSCAPE.first, value ?: 100)
        }

    var keyboard_position_landscape: Boolean?
        get() = preferences.getBoolean(
            KEYBOARD_POSITION_LANDSCAPE.first, KEYBOARD_POSITION_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(KEYBOARD_POSITION_LANDSCAPE.first, value ?: true)
        }

    var keyboard_vertical_margin_bottom_landscape: Int?
        get() = preferences.getInt(
            KEYBOARD_VERTICAL_MARGIN_BOTTOM_LANDSCAPE.first,
            KEYBOARD_VERTICAL_MARGIN_BOTTOM_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putInt(KEYBOARD_VERTICAL_MARGIN_BOTTOM_LANDSCAPE.first, value ?: 0)
        }

    var qwerty_keyboard_height_landscape: Int?
        get() = preferences.getInt(
            QWERTY_KEYBOARD_HEIGHT_LANDSCAPE.first, QWERTY_KEYBOARD_HEIGHT_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putInt(QWERTY_KEYBOARD_HEIGHT_LANDSCAPE.first, value ?: 220)
        }

    var qwerty_keyboard_width_landscape: Int?
        get() = preferences.getInt(
            QWERTY_KEYBOARD_WIDTH_LANDSCAPE.first, QWERTY_KEYBOARD_WIDTH_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putInt(QWERTY_KEYBOARD_WIDTH_LANDSCAPE.first, value ?: 100)
        }

    var qwerty_keyboard_vertical_margin_bottom_landscape: Int?
        get() = preferences.getInt(
            QWERTY_KEYBOARD_VERTICAL_MARGIN_BOTTOM_LANDSCAPE.first,
            QWERTY_KEYBOARD_VERTICAL_MARGIN_BOTTOM_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putInt(QWERTY_KEYBOARD_VERTICAL_MARGIN_BOTTOM_LANDSCAPE.first, value ?: 0)
        }

    var qwerty_keyboard_position_landscape: Boolean?
        get() = preferences.getBoolean(
            QWERTY_KEYBOARD_POSITION_LANDSCAPE.first,
            QWERTY_KEYBOARD_POSITION_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(QWERTY_KEYBOARD_POSITION_LANDSCAPE.first, value ?: true)
        }

    var candidate_view_height_dp_landscape: Int?
        get() = preferences.getInt(
            CANDIDATE_VIEW_HEIGHT_DP_LANDSCAPE.first, CANDIDATE_VIEW_HEIGHT_DP_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putInt(
                CANDIDATE_VIEW_HEIGHT_DP_LANDSCAPE.first,
                value ?: CANDIDATE_VIEW_HEIGHT_DP_LANDSCAPE.second
            )
        }

    var candidate_view_empty_height_dp_landscape: Int?
        get() = preferences.getInt(
            CANDIDATE_VIEW_EMPTY_HEIGHT_DP_LANDSCAPE.first,
            CANDIDATE_VIEW_EMPTY_HEIGHT_DP_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putInt(
                CANDIDATE_VIEW_EMPTY_HEIGHT_DP_LANDSCAPE.first,
                value ?: CANDIDATE_VIEW_EMPTY_HEIGHT_DP_LANDSCAPE.second
            )
        }

    var flick_input_only_preference: Boolean?
        get() = preferences.getBoolean(FLICK_INPUT_ONLY.first, FLICK_INPUT_ONLY.second)
        set(value) = preferences.edit {
            it.putBoolean(FLICK_INPUT_ONLY.first, value ?: false)
        }

    var undo_enable_preference: Boolean?
        get() = preferences.getBoolean(UNDO_ENABLE.first, UNDO_ENABLE.second)
        set(value) = preferences.edit {
            it.putBoolean(UNDO_ENABLE.first, value ?: false)
        }

    var omission_search_preference: Boolean?
        get() = preferences.getBoolean(OMISSION_SEARCH.first, OMISSION_SEARCH.second)
        set(value) = preferences.edit {
            it.putBoolean(OMISSION_SEARCH.first, value ?: false)
        }

    var space_hankaku_preference: Boolean?
        get() = preferences.getBoolean(SPACE_HANKAKU_ENABLE.first, SPACE_HANKAKU_ENABLE.second)
        set(value) = preferences.edit {
            it.putBoolean(SPACE_HANKAKU_ENABLE.first, value ?: false)
        }

    var live_conversion_preference: Boolean?
        get() = preferences.getBoolean(LIVE_CONVERSION_ENABLE.first, LIVE_CONVERSION_ENABLE.second)
        set(value) = preferences.edit {
            it.putBoolean(LIVE_CONVERSION_ENABLE.first, value ?: false)
        }

    var delete_key_high_light_preference: Boolean?
        get() = preferences.getBoolean(DELETE_KEY_HIGH_LIGHT.first, DELETE_KEY_HIGH_LIGHT.second)
        set(value) = preferences.edit {
            it.putBoolean(DELETE_KEY_HIGH_LIGHT.first, value ?: true)
        }

    var custom_keyboard_suggestion_preference: Boolean?
        get() = preferences.getBoolean(
            CUSTOM_KEYBOARD_SUGGESTION_PREFERENCE.first,
            CUSTOM_KEYBOARD_SUGGESTION_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CUSTOM_KEYBOARD_SUGGESTION_PREFERENCE.first, value ?: true)
        }

    var sumire_input_selection_preference: String?
        get() = preferences.getString(
            SUMIRE_INPUT_SELECTION_PREFERENCE.first, SUMIRE_INPUT_SELECTION_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putString(SUMIRE_INPUT_SELECTION_PREFERENCE.first, value ?: "toggle-default")
        }

    var sumire_keyboard_style: String
        get() = preferences.getString(NEW_SUMIRE_STYLE_KEY, "default") ?: "default"
        set(value) = preferences.edit {
            it.putString(NEW_SUMIRE_STYLE_KEY, value)
        }

    var sumire_input_method: String
        get() = preferences.getString(NEW_SUMIRE_METHOD_KEY, "toggle") ?: "toggle"
        set(value) = preferences.edit {
            it.putString(NEW_SUMIRE_METHOD_KEY, value)
        }

    var is_floating_mode: Boolean?
        get() = preferences.getBoolean(
            KEYBOARD_FLOATING_PREFERENCE.first, KEYBOARD_FLOATING_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(KEYBOARD_FLOATING_PREFERENCE.first, value ?: false)
        }

    var keyboard_floating_position_x: Int
        get() = preferences.getInt(
            KEYBOARD_FLOATING_POSITION_X.first, KEYBOARD_FLOATING_POSITION_X.second
        )
        set(value) = preferences.edit {
            it.putInt(KEYBOARD_FLOATING_POSITION_X.first, value)
        }

    var keyboard_floating_position_y: Int
        get() = preferences.getInt(
            KEYBOARD_FLOATING_POSITION_Y.first, KEYBOARD_FLOATING_POSITION_Y.second
        )
        set(value) = preferences.edit {
            it.putInt(KEYBOARD_FLOATING_POSITION_Y.first, value)
        }

    var candidate_view_height_dp: Int?
        get() = preferences.getInt(
            CANDIDATE_VIEW_HEIGHT_DP.first, CANDIDATE_VIEW_HEIGHT_DP.second
        )
        set(value) = preferences.edit {
            it.putInt(CANDIDATE_VIEW_HEIGHT_DP.first, value ?: CANDIDATE_VIEW_HEIGHT_DP.second)
        }

    var candidate_view_empty_height_dp: Int?
        get() = preferences.getInt(
            CANDIDATE_VIEW_EMPTY_HEIGHT_DP.first, CANDIDATE_VIEW_EMPTY_HEIGHT_DP.second
        )
        set(value) = preferences.edit {
            it.putInt(
                CANDIDATE_VIEW_EMPTY_HEIGHT_DP.first,
                value ?: CANDIDATE_VIEW_EMPTY_HEIGHT_DP.second
            )
        }

    var candidate_column_preference: String
        get() = preferences.getString(
            CANDIDATE_COLUMN_PREFERENCE.first, CANDIDATE_COLUMN_PREFERENCE.second
        ) ?: "1"
        set(value) = preferences.edit {
            it.putString(CANDIDATE_COLUMN_PREFERENCE.first, value)
        }

    var candidate_column_landscape_preference: String
        get() = preferences.getString(
            CANDIDATE_COLUMN_LANDSCAPE_PREFERENCE.first,
            CANDIDATE_COLUMN_LANDSCAPE_PREFERENCE.second
        ) ?: "1"
        set(value) = preferences.edit {
            it.putString(CANDIDATE_COLUMN_LANDSCAPE_PREFERENCE.first, value)
        }

    var candidate_tab_preference: Boolean
        get() = preferences.getBoolean(
            CANDIDATE_TAB_PREFERENCE.first, CANDIDATE_TAB_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CANDIDATE_TAB_PREFERENCE.first, value)
        }

    var shortcut_toolbar_visibility_preference: Boolean
        get() = preferences.getBoolean(
            SHORTCUT_TOOLBAR_VISIBILITY_PREFERENCE.first,
            SHORTCUT_TOOLBAR_VISIBILITY_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(SHORTCUT_TOOLBAR_VISIBILITY_PREFERENCE.first, value)
        }

    var seedColor: Int
        get() = preferences.getInt(
            APP_THEME_SEED_COLOR.first, APP_THEME_SEED_COLOR.second
        )
        set(value) = preferences.edit {
            it.putInt(APP_THEME_SEED_COLOR.first, value)
        }

    var delete_key_left_flick_preference: Boolean
        get() = preferences.getBoolean(
            DELETE_KEY_LEFT_FLICK_PREFERENCE.first,
            DELETE_KEY_LEFT_FLICK_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(DELETE_KEY_LEFT_FLICK_PREFERENCE.first, value)
        }

    var key_letter_size: Float?
        get() = preferences.getFloat(KEY_LETTER_SIZE.first, KEY_LETTER_SIZE.second)
        set(value) = preferences.edit {
            it.putFloat(KEY_LETTER_SIZE.first, value ?: KEY_LETTER_SIZE.second)
        }

    var candidate_letter_size: Float?
        get() = preferences.getFloat(CANDIDATE_LETTER_SIZE.first, CANDIDATE_LETTER_SIZE.second)
        set(value) = preferences.edit {
            it.putFloat(CANDIDATE_LETTER_SIZE.first, value ?: CANDIDATE_LETTER_SIZE.second)
        }

    var clipboard_preview_preference: Boolean
        get() = preferences.getBoolean(
            CLIP_BOARD_PREVIEW_PREFERENCE.first,
            CLIP_BOARD_PREVIEW_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CLIP_BOARD_PREVIEW_PREFERENCE.first, value)
        }

    var keyboard_corner_round_preference: Boolean
        get() = preferences.getBoolean(
            ROUND_KEYBOARD_CORNER_PREFERENCE.first,
            ROUND_KEYBOARD_CORNER_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(ROUND_KEYBOARD_CORNER_PREFERENCE.first, value)
        }

    var bunsetsu_separation_preference: Boolean
        get() = preferences.getBoolean(
            BUNSETSU_SEPARATION_PREFERENCE.first,
            BUNSETSU_SEPARATION_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(BUNSETSU_SEPARATION_PREFERENCE.first, value)
        }

    var conversion_key_swipe_cursor_move_preference: Boolean
        get() = preferences.getBoolean(
            CONVERSION_KEY_SWIPE_CURSOR_MOVE_PREFERENCE.first,
            CONVERSION_KEY_SWIPE_CURSOR_MOVE_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CONVERSION_KEY_SWIPE_CURSOR_MOVE_PREFERENCE.first, value)
        }

    fun migrateSumirePreferenceIfNeeded() {
        // 古いキーが存在する場合のみ移行処理を実行
        if (preferences.contains(OLD_SUMIRE_PREFERENCE_KEY)) {
            val oldValue = preferences.getString(OLD_SUMIRE_PREFERENCE_KEY, "toggle-default")

            val (newStyle, newMethod) = when (oldValue) {
                "toggle-default" -> "default" to "toggle"
                "flick-default" -> "default" to "flick"
                "flick-circle" -> "circle" to "toggle"
                "flick-circle-flick" -> "circle" to "flick"
                "second-flick" -> "second-flick" to "toggle"
                "second-flick-flick" -> "second-flick" to "flick"
                "flick-sumire" -> "sumire" to "flick"
                else -> "default" to "toggle"
            }

            preferences.edit {
                // 新しいキーで値を保存
                it.putString(NEW_SUMIRE_STYLE_KEY, newStyle)
                it.putString(NEW_SUMIRE_METHOD_KEY, newMethod)
                // 移行が完了したので古いキーを削除
                it.remove(OLD_SUMIRE_PREFERENCE_KEY)
            }
        }
    }
}
