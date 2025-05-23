package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

object AppPreference {

    private lateinit var preferences: SharedPreferences

    private val TIME_SAME_PRONOUNCE_TYPING = Pair("time_same_pronounce_typing_preference", 1000)
    private val VIBRATION_PREFERENCE = Pair("vibration_preference", true)
    private val VIBRATION_TIMING_PREFERENCE = Pair("vibration_timing", "both")
    private val LEARN_DICTIONARY_PREFERENCE = Pair("learn_dictionary_preference", true)
    private val N_BEST_PREFERENCE = Pair("n_best_preference", 8)
    private val MOZCUT_PERSON_NAME = Pair("mozc_ut_person_name_preference", false)
    private val MOZCUT_PLACES = Pair("mozc_ut_places_preference", false)
    private val MOZCUT_WIKI = Pair("mozc_ut_wiki_preference", false)
    private val MOZCUT_NEOLOGD = Pair("mozc_ut_neologd_preference", false)
    private val MOZCUT_WEB = Pair("mozc_ut_web_preference", false)

    private val KEYBOARD_HEIGHT = Pair("keyboard_height_preference", 280)
    private val KEYBOARD_WIDTH = Pair("keyboard_width_preference", 100)
    private val KEYBOARD_POSITION = Pair("keyboard_position_preference", true)
    private val FLICK_INPUT_ONLY = Pair("flick_input_only_preference", false)

    fun init(context: Context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
    }

    private inline fun SharedPreferences.edit(operation: (SharedPreferences.Editor) -> Unit) {
        val editor = edit()
        operation(editor)
        editor.apply()
    }

    var vibration_preference: Boolean?
        get() = preferences.getBoolean(VIBRATION_PREFERENCE.first, VIBRATION_PREFERENCE.second)
        set(value) = preferences.edit {
            it.putBoolean(VIBRATION_PREFERENCE.first, value ?: true)
        }

    var vibration_timing_preference: String?
        get() = preferences.getString(
            VIBRATION_TIMING_PREFERENCE.first,
            VIBRATION_TIMING_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putString(VIBRATION_TIMING_PREFERENCE.first, value ?: "both")
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
}
