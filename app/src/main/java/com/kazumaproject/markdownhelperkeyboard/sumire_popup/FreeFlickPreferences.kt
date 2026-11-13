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
    fun gestureConfig(context: Context): com.kazumaproject.core.domain.flick.GestureSessionConfig {
        val p=com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
        val sensitivity=p.flick_sensitivity_preference ?: 100
        return com.kazumaproject.core.domain.flick.GestureSessionConfig(0L,sensitivity,
            com.kazumaproject.core.domain.flick.FlickGestureMath.thresholdPxForSensitivity(sensitivity,
                android.view.ViewConfiguration.get(context).scaledTouchSlop,1.5f,3.5f,4.25f),
            (p.long_press_timeout_preference ?: 300).toLong().coerceIn(100L,2000L),
            com.kazumaproject.core.domain.flick.FlickThresholdShape.fromPreferenceValue(p.flick_threshold_shape_preference),
            showFlickPopup=p.sumire_show_flick_popup_preference,
            showLongPressPopup=p.sumire_show_long_press_popup_preference)
    }
    fun read(context: Context): PopupMapInputOptions {
        val preferences=PreferenceManager.getDefaultSharedPreferences(context)
        return PopupMapInputOptions(
            preferences.getBoolean(NEAREST,true),preferences.getBoolean(TEXT,true),
            preferences.getBoolean(SWITCH,false),preferences.getBoolean(BACK,false),
            PopupMapPresentation.fromPreference(preferences.getString(PRESENTATION,null))
        )
    }
}
