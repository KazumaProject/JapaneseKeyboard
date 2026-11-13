package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.content.Context
import androidx.preference.PreferenceManager
import com.kazumaproject.custom_keyboard.data.PopupMapInputOptions
import com.kazumaproject.custom_keyboard.data.PopupMapPresentation

object FreeFlickPreferences {
    const val PREFIX = "sumire_free_flick_"
    const val NEAREST = PREFIX + "nearest"
    const val TEXT = PREFIX + "nearest_text"
    const val SWITCH = PREFIX + "nearest_switch"
    const val BACK = PREFIX + "nearest_back"
    const val PRESENTATION = PREFIX + "presentation"
    fun read(context: Context): PopupMapInputOptions {
        val preferences=PreferenceManager.getDefaultSharedPreferences(context)
        return PopupMapInputOptions(
            preferences.getBoolean(NEAREST,true),preferences.getBoolean(TEXT,true),
            preferences.getBoolean(SWITCH,false),preferences.getBoolean(BACK,false),
            PopupMapPresentation.fromPreference(preferences.getString(PRESENTATION,null))
        )
    }
}
