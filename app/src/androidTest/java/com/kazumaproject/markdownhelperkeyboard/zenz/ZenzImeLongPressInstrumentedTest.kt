package com.kazumaproject.markdownhelperkeyboard.zenz

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PointF
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZenzImeLongPressInstrumentedTest {
    private val instrumentation: Instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val uiAutomation: UiAutomation
        get() = instrumentation.uiAutomation

    @Test(timeout = 120_000)
    fun conversionKeyLongPressStartsZenzWhenAutomaticConversionIsDisabled() {
        assumeTrue("Zenz is only available in the full edition", AppVariantConfig.hasZenz)
        val context = instrumentation.targetContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val originalPreferences = preferences.all.toMap()
        var scenario: ActivityScenario<FastInputHostActivity>? = null

        try {
            applyLongPressScenario(preferences)
            uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            shell("ime set ${context.packageName}/.ime_service.IMEService")
            SystemClock.sleep(700L)

            scenario = ActivityScenario.launch(
                Intent(context, FastInputHostActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            restartInput(scenario)
            SystemClock.sleep(1_200L)

            val screenshot = uiAutomation.takeScreenshot()
            val width = screenshot.width
            val height = screenshot.height
            screenshot.recycle()

            injectTouch(
                point = PointF(width * 0.30f, height * 0.712f),
                holdMillis = 50L,
            )
            injectTouch(
                point = PointF(width * 0.50f, height * 0.712f),
                holdMillis = 50L,
            )
            SystemClock.sleep(700L)
            val composedText = readText(scenario)
            assertTrue("Ten-key input did not reach the editor", composedText.isNotEmpty())
            assertTrue("Automatic Zenz unexpectedly started", zenzProcessId().isEmpty())

            injectLongPress(
                point = PointF(width * 0.90f, height * 0.792f),
                holdMillis = 650L,
            )
            SystemClock.sleep(4_000L)

            assertTrue(
                "Zenz process did not start from conversion-key long press",
                zenzProcessId().isNotEmpty(),
            )
            assertEquals(composedText, readText(scenario))
        } finally {
            scenario?.close()
            restorePreferences(preferences, originalPreferences)
            uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }

    private fun applyLongPressScenario(preferences: SharedPreferences) {
        check(
            preferences.edit()
                .putString("keyboard_order_preference", """["TENKEY"]""")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("keyboard_floating_preference", false)
                .putString("physical_keyboard_input_mode_preference", "romaji")
                .putBoolean("candidate_tab_visibility_preference", false)
                .putString("candidate_column_preference", "1")
                .putInt("candidate_view_height_dp_preference", 110)
                .putInt("candidate_view_empty_height_dp_preference", 110)
                .putInt("keyboard_height_preference", 280)
                .putBoolean("live_conversion_preference", false)
                .putBoolean("enable_ai_conversion_zenz_preference", false)
                .putBoolean("conversion_key_long_press_ai_conversion_preference", true)
                .putBoolean("enable_ai_conversion_zenzai_preference", false)
                .putBoolean("enable_zenz_rerank_preference", false)
                .putInt("zenz_debounce_time_preference", 0)
                .putInt("long_press_timeout_preference", 100)
                .commit()
        ) {
            "Could not prepare Zenz long-press preferences"
        }
    }

    private fun restartInput(scenario: ActivityScenario<FastInputHostActivity>) {
        scenario.onActivity { activity ->
            activity.editText.setText("")
            activity.editText.requestFocus()
            val inputMethodManager = activity.getSystemService(InputMethodManager::class.java)
            inputMethodManager.restartInput(activity.editText)
            inputMethodManager.showSoftInput(
                activity.editText,
                InputMethodManager.SHOW_IMPLICIT,
            )
        }
    }

    private fun readText(scenario: ActivityScenario<FastInputHostActivity>): String {
        var text = ""
        scenario.onActivity { activity ->
            text = activity.editText.text?.toString().orEmpty()
        }
        return text
    }

    private fun injectLongPress(point: PointF, holdMillis: Long): Boolean {
        return injectTouch(point = point, holdMillis = holdMillis)
    }

    private fun injectTouch(point: PointF, holdMillis: Long): Boolean {
        val downTime = SystemClock.uptimeMillis()
        val down = motionEvent(
            downTime = downTime,
            eventTime = downTime,
            action = MotionEvent.ACTION_DOWN,
            point = point,
        )
        val downInjected = uiAutomation.injectInputEvent(down, true)
        down.recycle()
        SystemClock.sleep(holdMillis)

        val upTime = SystemClock.uptimeMillis()
        val up = motionEvent(
            downTime = downTime,
            eventTime = upTime,
            action = MotionEvent.ACTION_UP,
            point = point,
        )
        val upInjected = uiAutomation.injectInputEvent(up, true)
        up.recycle()
        return downInjected && upInjected
    }

    private fun motionEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        point: PointF,
    ): MotionEvent {
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        )
        val coordinates = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = point.x
                y = point.y
                pressure = 1f
                size = 1f
            }
        )
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
    }

    private fun zenzProcessId(): String {
        return shell("pidof ${instrumentation.targetContext.packageName}:zenz").trim()
    }

    private fun shell(command: String): String {
        val descriptor = uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            input.bufferedReader().readText()
        }
    }

    private fun restorePreferences(
        preferences: SharedPreferences,
        original: Map<String, *>,
    ) {
        val editor = preferences.edit().clear()
        original.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                }
            }
        }
        check(editor.commit()) { "Failed to restore preferences after Zenz long-press test" }
    }
}
