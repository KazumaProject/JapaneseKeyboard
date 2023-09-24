package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

object AppPreference {

    private lateinit var preferences: SharedPreferences

    private val VIBRATION_PREFERENCE = Pair("vibration_preference",false)

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
}