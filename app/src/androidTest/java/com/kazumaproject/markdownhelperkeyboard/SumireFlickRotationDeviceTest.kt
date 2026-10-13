package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.SharedPreferences
import android.graphics.PointF
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inspector.WindowInspector
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kazumaproject.custom_keyboard.data.TfbiFlickRotation
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SumireFlickInputMode
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run only with the isolated sumirerotation535 application ID supplied by a Gradle init script. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class SumireFlickRotationDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext

    @Test
    fun secondAndThirdFlickUseRotationChangesWhileTheImeIsOpen() {
        assumeTrue("Use the isolated sumirerotation535 verification package",
            context.packageName.startsWith("com.kazumaproject.sumirerotation535"))
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val originalPreferences = preferences.all.toMap()
        val targetIme = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val originalIme = shell("settings get secure default_input_method").trim()
        val wasEnabled = shell("ime list -s").lineSequence().any { it.trim() == targetIme }
        val originalAccessibilityFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        try {
            check(preferences.edit().clear()
                .putString("keyboard_order_preference", "[\"SUMIRE\"]")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("gojuon_keyboard_type_migrated_v1", true)
                .putBoolean("keyboard_floating_preference", false)
                .putString("sumire_input_method_preference", "flick")
                .putString("sumire_keyboard_style_preference", "second-flick")
                .putString(AppPreference.SUMIRE_FLICK_ROTATION_KEY, "default")
                .putString(AppPreference.SUMIRE_FLICK_INPUT_MODE_KEY, "all")
                .putString(AppPreference.FLICK_TFBI_POPUP_PRESENTATION_KEY, "legacy_grid")
                .putString(AppPreference.TFBI_DIAGONAL_RECOGNITION_MODE_KEY, "legacy")
                .putInt(AppPreference.FLICK_SENSITIVITY_KEY, 1)
                .putInt(AppPreference.LONG_PRESS_TIMEOUT_KEY, 2000)
                .putBoolean("sumire_restore_input_mode_on_restart_preference", false)
                .putBoolean("landscape_force_qwerty_preference", false)
                .putBoolean("live_conversion_preference", false)
                .putBoolean("learn_dictionary_preference", false)
                .putBoolean("flick_editor_preview_preference", false)
                .putBoolean("flick_input_only_preference", true)
                .putBoolean("key_sound_preference", false)
                .putBoolean("vibration_preference", false)
                .commit())
            shell("ime enable $targetIme")
            shell("ime set $targetIme")
            shell("am start -n ${context.packageName}/${FastInputHostActivity::class.java.name}")
            for (style in listOf("second-flick", "third-flick")) {
                check(preferences.edit().putString("sumire_keyboard_style_preference", style)
                    .putString(AppPreference.SUMIRE_FLICK_ROTATION_KEY, "default").commit())
                withHost { it.restartEditorInput(clearText = true) }
                awaitLayout(style, TfbiFlickRotation.DEFAULT)
                key("か")
                var expected = ""
                for (rotation in listOf(TfbiFlickRotation.CLOCKWISE, TfbiFlickRotation.COUNTERCLOCKWISE)) {
                    // Keep the same served editor and visible IME. This must refresh via the
                    // production preference listener, without onStartInput() reloading settings.
                    instrumentation.runOnMainSync {
                        check(preferences.edit().putString(AppPreference.SUMIRE_FLICK_ROTATION_KEY,
                            rotation.preferenceValue).commit())
                    }
                    awaitLayout(style, rotation)
                    val paths = listOf(
                        Triple(LEFT, if (rotation == TfbiFlickRotation.CLOCKWISE) UP_LEFT else DOWN_LEFT, "ぎ"),
                        Triple(UP, if (rotation == TfbiFlickRotation.CLOCKWISE) UP_RIGHT else UP_LEFT, "ぐ"),
                        Triple(RIGHT, if (rotation == TfbiFlickRotation.COUNTERCLOCKWISE) UP_RIGHT else DOWN_RIGHT, "げ"),
                        Triple(DOWN, if (rotation == TfbiFlickRotation.CLOCKWISE) DOWN_LEFT else DOWN_RIGHT, "ご")
                    )
                    for ((first, second, output) in paths) {
                        flick(key("か"), style, first, second)
                        expected += output
                        awaitText(expected, "$style $rotation $first/$second")
                    }
                }
                if (style == "third-flick") {
                    for (mode in listOf(SumireFlickInputMode.CONTRACTED, SumireFlickInputMode.VOICED,
                        SumireFlickInputMode.ALL)) {
                        instrumentation.runOnMainSync {
                            check(preferences.edit().putString(AppPreference.SUMIRE_FLICK_INPUT_MODE_KEY,
                                mode.preferenceValue).commit())
                        }
                        awaitLayout(style, TfbiFlickRotation.COUNTERCLOCKWISE, mode)
                        val output = if (mode == SumireFlickInputMode.VOICED) {
                            flick(key("か"), style, LEFT, DOWN_LEFT)
                            "ぎ"
                        } else {
                            flick(key("か"), style, LEFT, UP, UP_LEFT)
                            "きゅう"
                        }
                        expected += output
                        awaitText(expected, "Active three-step input mode $mode")
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<FastInputHostActivity>().forEach { it.finish() }
            }
            try {
                restorePreferences(preferences, originalPreferences)
            } finally {
                try {
                    if (originalIme.isNotBlank() && originalIme != "null") shell("ime set $originalIme")
                    else shell("settings delete secure default_input_method")
                } finally {
                    if (!wasEnabled) shell("ime disable $targetIme")
                    automation.serviceInfo = automation.serviceInfo.apply { flags = originalAccessibilityFlags }
                }
            }
        }
    }

    private fun flick(bounds: Rect, style: String, first: TfbiFlickDirection, second: TfbiFlickDirection,
        third: TfbiFlickDirection? = null) {
        val radius = 60f * context.resources.displayMetrics.density
        val start = PointF(bounds.exactCenterX(), bounds.exactCenterY())
        val firstPoint = point(start, first, radius)
        val secondPoint = point(if (style == "second-flick") firstPoint else start, second, radius)
        val downTime = SystemClock.uptimeMillis()
        val thirdPoint = third?.let { point(start, it, radius) }
        (listOf(MotionEvent.ACTION_DOWN to start, MotionEvent.ACTION_MOVE to firstPoint,
            MotionEvent.ACTION_MOVE to secondPoint) +
            listOfNotNull(thirdPoint?.let { MotionEvent.ACTION_MOVE to it }) +
            (MotionEvent.ACTION_UP to (thirdPoint ?: secondPoint)))
            .forEach { (action, point) ->
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                    point.x, point.y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                try { check(automation.injectInputEvent(event, true)) }
                finally { event.recycle() }
                SystemClock.sleep(30)
            }
    }

    private fun point(origin: PointF, direction: TfbiFlickDirection, radius: Float): PointF {
        val angle = Math.toRadians(when (direction) {
            RIGHT -> 0.0; DOWN_RIGHT -> 35.0; DOWN -> 90.0; DOWN_LEFT -> 125.0
            LEFT -> 180.0; UP_LEFT -> -125.0; UP -> -90.0; UP_RIGHT -> -35.0
            TAP -> error("This test uses directional flicks")
        })
        return PointF(origin.x + cos(angle).toFloat() * radius,
            origin.y + sin(angle).toFloat() * radius)
    }

    private fun key(label: String): Rect {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            fun visit(node: AccessibilityNodeInfo) {
                nodes += node
                repeat(node.childCount) { node.getChild(it)?.let(::visit) }
            }
            automation.windows.forEach { it.root?.let(::visit) }
            nodes.firstOrNull { it.packageName?.toString() == context.packageName &&
                    it.isVisibleToUser && it.isClickable &&
                    (it.text?.toString()?.lineSequence()?.firstOrNull()?.trim() == label ||
                        it.contentDescription?.toString() == label)
            }?.let { node ->
                val bounds = Rect().also(node::getBoundsInScreen)
                if (!bounds.isEmpty) return bounds
            }
            SystemClock.sleep(100)
        }
        error("Sumire key $label was not visible")
    }

    private fun awaitLayout(style: String, rotation: TfbiFlickRotation,
        mode: SumireFlickInputMode = SumireFlickInputMode.ALL) {
        val deadline = SystemClock.uptimeMillis() + 15000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                val keyboard = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull {
                    it.findViewById<FlickKeyboardView>(R.id.custom_layout_default)?.takeIf { view -> view.isShown }
                } ?: return@runOnMainSync
                val layout = FlickKeyboardView::class.java.getDeclaredField("currentLayout")
                    .apply { isAccessible = true }.get(keyboard) as? KeyboardLayout ?: return@runOnMainSync
                val expectedType = if (style == "second-flick") KeyType.TWO_STEP_FLICK else KeyType.HIERARCHICAL_FLICK
                if (layout.keys.firstOrNull { it.label == "か" }?.keyType != expectedType) return@runOnMainSync
                fun output(first: TfbiFlickDirection, second: TfbiFlickDirection): String? =
                    if (style == "second-flick") layout.twoStepFlickKeyMaps["か"]?.get(first)?.get(second)
                    else ((layout.hierarchicalFlickMaps["か"]?.normalMap?.get(first)
                        as? TfbiFlickNode.SubMenu)?.nextMap?.get(second) as? TfbiFlickNode.Input)?.char
                val hasGi = output(LEFT, if (rotation == TfbiFlickRotation.CLOCKWISE) UP_LEFT else DOWN_LEFT) == "ぎ"
                val hasGe = output(RIGHT, if (rotation == TfbiFlickRotation.COUNTERCLOCKWISE) UP_RIGHT else DOWN_RIGHT) == "げ"
                val ka = layout.hierarchicalFlickMaps["か"]
                val iStage = (ka?.normalMap?.get(LEFT) as? TfbiFlickNode.SubMenu)?.nextMap
                val yoonStage = (iStage?.get(UP) as? TfbiFlickNode.SubMenu)?.nextMap
                val hasLongYoon = (yoonStage?.get(UP_LEFT) as? TfbiFlickNode.Input)?.char == "きゅう"
                ready = if (style == "second-flick") hasGi && hasGe else when (mode) {
                    SumireFlickInputMode.ALL -> hasGi && hasGe && hasLongYoon
                    SumireFlickInputMode.CONTRACTED -> !hasGi && !hasGe && hasLongYoon && ka?.dakutenMap == null
                    SumireFlickInputMode.VOICED -> hasGi && hasGe && !hasLongYoon
                }
            }
            if (!ready) SystemClock.sleep(50)
        }
        assertTrue("Visible IME did not load $style / $rotation / $mode", ready)
    }

    private fun withHost(action: (FastInputHostActivity) -> Unit) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            var found = false
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<FastInputHostActivity>().firstOrNull()?.let {
                        found = true
                        action(it)
                    }
            }
            if (found) return
            SystemClock.sleep(50)
        }
        error("FastInputHostActivity did not resume")
    }

    private fun awaitText(expected: String, description: String) {
        val deadline = SystemClock.uptimeMillis() + 5000
        var actual = ""
        while (SystemClock.uptimeMillis() < deadline) {
            withHost { actual = it.editText.text.toString() }
            if (actual == expected) return
            SystemClock.sleep(50)
        }
        assertEquals(description, expected, actual)
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun restorePreferences(preferences: SharedPreferences, original: Map<String, *>) {
        val editor = preferences.edit().clear()
        original.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> error("Unsupported preference type for $key")
            }
        }
        check(editor.commit())
    }
}
