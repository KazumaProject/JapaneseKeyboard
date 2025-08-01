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

    private val CUSTOM_KEYBOARD_TWO_WORDS_OUTPUTS =
        Pair("custom_keyboard_two_words_preference", true)

    private val KEYBOARD_HEIGHT = Pair("keyboard_height_preference", 220)
    private val KEYBOARD_WIDTH = Pair("keyboard_width_preference", 100)
    private val KEYBOARD_POSITION = Pair("keyboard_position_preference", true)
    private val FLICK_INPUT_ONLY = Pair("flick_input_only_preference", false)
    private val OMISSION_SEARCH = Pair("omission_search_preference", false)
    private val UNDO_ENABLE = Pair("undo_enable_preference", false)
    private val SPACE_HANKAKU_ENABLE = Pair("space_key_preference", false)
    private val LIVE_CONVERSION_ENABLE = Pair("live_conversion_preference", false)
    private val SUMIRE_INPUT_SELECTION_PREFERENCE =
        Pair("sumire_keyboard_input_type_preference", "flick-default")

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
            CLIPBOARD_HISTORY_ENABLE.first,
            CLIPBOARD_HISTORY_ENABLE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CLIPBOARD_HISTORY_ENABLE.first, value ?: false)
        }

    var custom_keyboard_two_words_output: Boolean?
        get() = preferences.getBoolean(
            CUSTOM_KEYBOARD_TWO_WORDS_OUTPUTS.first,
            CUSTOM_KEYBOARD_TWO_WORDS_OUTPUTS.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CUSTOM_KEYBOARD_TWO_WORDS_OUTPUTS.first, value ?: false)
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
                SYMBOL_MODE_PREFERENCE.first,
                SYMBOL_MODE_PREFERENCE.second
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
            NG_WORD_ENABLE_PREFERENCE.first,
            NG_WORD_ENABLE_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(NG_WORD_ENABLE_PREFERENCE.first, value ?: true)
        }

    var vibration_timing_preference: String?
        get() = preferences.getString(
            VIBRATION_TIMING_PREFERENCE.first,
            VIBRATION_TIMING_PREFERENCE.second
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

    var keyboard_position: Boolean?
        get() = preferences.getBoolean(KEYBOARD_POSITION.first, KEYBOARD_POSITION.second)
        set(value) = preferences.edit {
            it.putBoolean(KEYBOARD_POSITION.first, value ?: true)
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

    var sumire_input_selection_preference: String?
        get() = preferences.getString(
            SUMIRE_INPUT_SELECTION_PREFERENCE.first,
            SUMIRE_INPUT_SELECTION_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putString(SUMIRE_INPUT_SELECTION_PREFERENCE.first, value ?: "flick-default")
        }
}
