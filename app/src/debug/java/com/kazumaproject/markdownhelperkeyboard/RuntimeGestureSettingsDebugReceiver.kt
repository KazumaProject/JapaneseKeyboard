package com.kazumaproject.markdownhelperkeyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager
import com.kazumaproject.core.domain.flick.RuntimeGestureSettings
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

/**
 * Debug-build-only device-test entry point.
 *
 * It writes the existing production preference keys so the real SharedPreferences listener and
 * IME runtime path are exercised while the keyboard remains visible. No production component or
 * additional preference store is introduced.
 */
class RuntimeGestureSettingsDebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_RUNTIME_GESTURE) return

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = preferences.edit()
        val changed = mutableListOf<String>()

        if (intent.hasExtra(EXTRA_FLICK_SENSITIVITY)) {
            val sensitivity = intent.getIntExtra(
                EXTRA_FLICK_SENSITIVITY,
                RuntimeGestureSettings.DEFAULT_FLICK_SENSITIVITY
            ).coerceIn(
                RuntimeGestureSettings.MIN_FLICK_SENSITIVITY,
                RuntimeGestureSettings.MAX_FLICK_SENSITIVITY
            )
            editor.putInt(AppPreference.FLICK_SENSITIVITY_KEY, sensitivity)
            changed += "sensitivity=$sensitivity"
        }

        if (intent.hasExtra(EXTRA_LONG_PRESS_TIMEOUT_MILLIS)) {
            val timeoutMillis = intent.getLongExtra(
                EXTRA_LONG_PRESS_TIMEOUT_MILLIS,
                RuntimeGestureSettings.DEFAULT_LONG_PRESS_TIMEOUT_MILLIS
            ).coerceIn(
                RuntimeGestureSettings.MIN_LONG_PRESS_TIMEOUT_MILLIS,
                RuntimeGestureSettings.MAX_LONG_PRESS_TIMEOUT_MILLIS
            )
            editor.putInt(AppPreference.LONG_PRESS_TIMEOUT_KEY, timeoutMillis.toInt())
            changed += "longPress=${timeoutMillis}ms"
        }

        check(editor.commit()) { "Failed to persist debug runtime gesture settings" }
        Log.i(TAG, "Published existing preferences: ${changed.joinToString()}")
    }

    companion object {
        const val ACTION_SET_RUNTIME_GESTURE =
            "com.kazumaproject.markdownhelperkeyboard.DEBUG_SET_RUNTIME_GESTURE"
        const val EXTRA_FLICK_SENSITIVITY = "flick_sensitivity"
        const val EXTRA_LONG_PRESS_TIMEOUT_MILLIS = "long_press_timeout_millis"
        private const val TAG = "RuntimeGestureDebug"
    }
}
