package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

object AppPreference {

    private lateinit var preferences: SharedPreferences

    private val VIBRATION_PREFERENCE = Pair("vibration_preference", true)
    private val LEARN_DICTIONARY_PREFERENCE = Pair("learn_dictionary_preference", true)
    private val N_BEST_PREFERENCE = Pair("n_best_preference", 8)
    private val CANDIDATE_CACHE = Pair("candidates_cache", false)
    private val MOZCUT_PERSON_NAME = Pair("mozc_ut_person_name_preference", false)
    private val MOZCUT_PLACES = Pair("mozc_ut_places_preference", false)
    private val MOZCUT_WIKI = Pair("mozc_ut_wiki_preference", false)
    private val MOZCUT_NEOLOGD = Pair("mozc_ut_neologd_preference", false)
    private val MOZCUT_WEB = Pair("mozc_ut_web_preference", false)

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

    var learn_dictionary_preference: Boolean?
        get() = preferences.getBoolean(
            LEARN_DICTIONARY_PREFERENCE.first, LEARN_DICTIONARY_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(LEARN_DICTIONARY_PREFERENCE.first, value ?: true)
        }

    var n_best_preference: Int?
        get() = preferences.getInt(
            N_BEST_PREFERENCE.first, N_BEST_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putInt(N_BEST_PREFERENCE.first, value ?: 4)
        }

    var candidate_cache_preference: Boolean?
        get() = preferences.getBoolean(CANDIDATE_CACHE.first, CANDIDATE_CACHE.second)
        set(value) = preferences.edit {
            it.putBoolean(CANDIDATE_CACHE.first, value ?: false)
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
}
