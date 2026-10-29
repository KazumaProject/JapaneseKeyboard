package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_geometry

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kazumaproject.core.ui.geometry.KeyboardCurveHand
import com.kazumaproject.core.ui.geometry.KeyboardCurveDirection
import com.kazumaproject.core.ui.geometry.KeyboardGeometry
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.GeometryCandidatePlacement
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.GeometryKeyboard
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.KeyboardGeometryPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
import com.kazumaproject.tenkey.TenKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class GeometryPreviewDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun qwertyPreviewMatchesTheActualSplitKeyboard() = verify(GeometryKeyboard.QWERTY)
    @Test fun tenkeyPreviewMatchesTheActualCurvedKeyboard() = verify(GeometryKeyboard.TENKEY)
    @Test fun sumirePreviewMatchesTheActualCurvedKeyboard() = verify(GeometryKeyboard.SUMIRE)
    @Test fun alternateSumireLayoutMatchesTheActualKeyboard() = verify(GeometryKeyboard.SUMIRE, sumireMethod = "switch-mode-effective")
    @Test fun leftHandTenkeyPreviewMatchesTheActualKeyboard() = verify(GeometryKeyboard.TENKEY, hand = KeyboardCurveHand.LEFT)
    @Test fun fixedHeightPreviewMatchesTheActualKeyboard() = verify(GeometryKeyboard.QWERTY, autoHeight = false)
    @Test fun reverseQwertyPreviewMatchesTheActualSplitKeyboard() = verify(GeometryKeyboard.QWERTY, direction = KeyboardCurveDirection.UP)
    @Test fun reverseTenkeyPreviewMatchesTheActualKeyboard() = verify(GeometryKeyboard.TENKEY, hand = KeyboardCurveHand.LEFT, direction = KeyboardCurveDirection.UP)
    @Test fun reverseSumirePreviewMatchesTheActualKeyboard() = verify(GeometryKeyboard.SUMIRE, autoHeight = false, direction = KeyboardCurveDirection.UP)

    @Test fun landscapePreviewMatchesTheActualCustomSize() {
        val oldAuto = shell("settings get system accelerometer_rotation")
        val oldRotation = shell("settings get system user_rotation")
        try {
            shell("settings put system accelerometer_rotation 0")
            shell("settings put system user_rotation 1")
            SystemClock.sleep(1500)
            verify(GeometryKeyboard.QWERTY, landscape = true, widthPercent = 70, heightDp = 180)
        } finally {
            if (oldRotation == "null") shell("settings delete system user_rotation") else shell("settings put system user_rotation $oldRotation")
            if (oldAuto == "null") shell("settings delete system accelerometer_rotation") else shell("settings put system accelerometer_rotation $oldAuto")
        }
    }

    private fun verify(type: GeometryKeyboard, landscape: Boolean = false, widthPercent: Int = 100,
        heightDp: Int = 220, hand: KeyboardCurveHand = KeyboardCurveHand.RIGHT, autoHeight: Boolean = true,
        sumireMethod: String = "toggle", direction: KeyboardCurveDirection = KeyboardCurveDirection.DOWN) {
        AppPreference.init(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.all.toMap()
        val originalIme = shell("settings get secure default_input_method")
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val wasEnabled = shell("ime list -s").lineSequence().any { it.trim() == ime }
        var host: FastInputHostActivity? = null
        try {
            val qwerty = type == GeometryKeyboard.QWERTY
            val side = if (qwerty) "CENTER" else if (hand == KeyboardCurveHand.LEFT) "RIGHT" else "LEFT"
            val name = type.name.lowercase()
            val orientation = if (landscape) "landscape" else "portrait"
            check(prefs.edit().clear()
                .putString("keyboard_order_preference", "[\"${type.name}\"]")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("keyboard_floating_preference", false)
                .putString("sumire_input_method_preference", sumireMethod)
                .putString("sumire_keyboard_style_preference", "default")
                .putInt(if (landscape) "keyboard_height_landscape_preference" else "keyboard_height_preference", heightDp)
                .putInt(if (landscape) "qwerty_keyboard_height_landscape_preference" else "qwerty_keyboard_height_preference", heightDp)
                .putInt(if (landscape) "keyboard_width_landscape_preference" else "keyboard_width_preference", widthPercent)
                .putInt(if (landscape) "qwerty_keyboard_width_landscape_preference" else "qwerty_keyboard_width_preference", widthPercent)
                .putInt("keyboard_geometry_${name}_${orientation}_curve", 25)
                .putInt("keyboard_geometry_${name}_${orientation}_split", if (qwerty) 40 else 0)
                .putString("keyboard_geometry_${name}_${orientation}_candidates", side)
                .putString("keyboard_geometry_${name}_${orientation}_hand", hand.name)
                .putString("keyboard_geometry_${name}_${orientation}_curve_direction", direction.name)
                .putBoolean("keyboard_geometry_${name}_${orientation}_auto_height", autoHeight)
                .commit())
            shell("ime enable $ime")
            shell("am start -n ${context.packageName}/${FastInputHostActivity::class.java.name}")
            await {
                instrumentation.runOnMainSync {
                    host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<FastInputHostActivity>().firstOrNull()?.takeIf { it.hasWindowFocus() }
                }
                host != null
            }
            shell("ime set $ime")
            instrumentation.runOnMainSync { requireNotNull(host).restartEditorInput(clearText = true) }
            val keyboard = awaitKeyboard(type)
            // Let the auto-height callback and the candidate-side resize settle.
            var last = -1
            var stableAt = SystemClock.uptimeMillis()
            await {
                var current = 0
                instrumentation.runOnMainSync { current = keyboard.height }
                if (current != last) { last = current; stableAt = SystemClock.uptimeMillis() }
                current > 0 && SystemClock.uptimeMillis() - stableAt >= 600
            }
            instrumentation.runOnMainSync {
                val preview = GeometryPreview(requireNotNull(host), type)
                assertFalse(preview.isClickable)
                val config = KeyboardGeometryPreference(
                    KeyboardGeometry(splitPercent = if (qwerty) 40 else 0, curveDegrees = 25,
                        hand = hand, autoHeight = autoHeight, curveDirection = direction),
                    if (qwerty) GeometryCandidatePlacement.CENTER else if (hand == KeyboardCurveHand.LEFT) GeometryCandidatePlacement.RIGHT else GeometryCandidatePlacement.LEFT)
                preview.update(config, landscape = landscape, widthPercent = widthPercent, alignEnd = true)
                val screenWidth = context.resources.displayMetrics.widthPixels
                preview.measure(View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                preview.layout(0, 0, preview.measuredWidth, preview.measuredHeight)
                val snapshot = preview.snapshotForTesting()
                val tolerance = 4f * context.resources.displayMetrics.density
                assertEquals("keyboard width", keyboard.width.toFloat(), snapshot.bodyWidth, tolerance)
                assertEquals("keyboard height", keyboard.height.toFloat(), snapshot.bodyHeight, tolerance)
                if (type == GeometryKeyboard.SUMIRE) assertEquals("Sumire key count", (keyboard as ViewGroup).childCount, snapshot.keys.size)
                val references: List<Pair<View, GeometryPreviewKeyFrame>> = when (type) {
                    GeometryKeyboard.QWERTY -> listOf(com.kazumaproject.qwerty_keyboard.R.id.key_q,
                        com.kazumaproject.qwerty_keyboard.R.id.key_t, com.kazumaproject.qwerty_keyboard.R.id.key_y,
                        com.kazumaproject.qwerty_keyboard.R.id.key_p).map { id ->
                            keyboard.findViewById<View>(id) to snapshot.keys.first { it.id == id }
                        }
                    GeometryKeyboard.TENKEY -> listOf(com.kazumaproject.tenkey.R.id.key_1,
                        com.kazumaproject.tenkey.R.id.key_3, com.kazumaproject.tenkey.R.id.key_11,
                        com.kazumaproject.tenkey.R.id.key_enter).map { id ->
                            keyboard.findViewById<View>(id) to snapshot.keys.first { it.id == id }
                        }
                    GeometryKeyboard.SUMIRE -> listOf("あ", "か", "さ", "わ").map { label ->
                        findTextView(keyboard, label) to snapshot.keys.first { it.text == label }
                    }
                }
                references.forEach { (actual, frame) ->
                    val point = floatArrayOf(actual.width / 2f, actual.height / 2f)
                    actual.matrix.mapPoints(point)
                    assertEquals("${frame.text ?: frame.id} x", actual.left + point[0], frame.x, tolerance)
                    assertEquals("${frame.text ?: frame.id} y", actual.top + point[1], frame.y, tolerance)
                    assertEquals("${frame.text ?: frame.id} rotation", actual.rotation, frame.angle, 1f)
                }
                snapshot.keys.forEach { key ->
                    assertTrue("key extends above preview", key.y - key.halfHeight >= -tolerance)
                    assertTrue("key extends below preview", key.y + key.halfHeight <= snapshot.bodyHeight + tolerance)
                }
                // Background corners are configurable independently of the key geometry.
                AppPreference.keyboard_corner_round_preference = false
                val square = draw(preview)
                AppPreference.keyboard_corner_round_preference = true
                AppPreference.keyboard_corner_radius_dp_preference = 64
                preview.update(config, landscape, widthPercent, true)
                preview.measure(View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                preview.layout(0, 0, preview.measuredWidth, preview.measuredHeight)
                val rounded = draw(preview)
                assertTrue("square background should reach corner", (square.getPixel(1, 1) ushr 24) != 0)
                assertEquals("rounded background should leave corner transparent", 0, rounded.getPixel(1, 1) ushr 24)
                AppPreference.keyboard_corner_top_right_preference = false
                preview.update(config, landscape, widthPercent, true)
                preview.measure(View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                preview.layout(0, 0, preview.measuredWidth, preview.measuredHeight)
                val asymmetric = draw(preview)
                assertEquals("top-left remains rounded", 0, asymmetric.getPixel(1, 1) ushr 24)
                assertTrue("top-right can remain square", (asymmetric.getPixel(asymmetric.width - 2, 1) ushr 24) != 0)
            }
        } finally {
            instrumentation.runOnMainSync { host?.finish() }
            restore(prefs, original)
            if (originalIme.isNotBlank() && originalIme != "null") shell("ime set $originalIme")
            if (!wasEnabled) shell("ime disable $ime")
        }
    }

    @Test fun guidePreferencesAreVisibleInTheDisplayOnlyPreview() {
        AppPreference.init(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.all.toMap()
        try {
            check(prefs.edit().clear().commit())
            instrumentation.runOnMainSync {
                for (type in listOf(GeometryKeyboard.QWERTY, GeometryKeyboard.SUMIRE)) {
                    val preview = GeometryPreview(context, type)
                    fun capture(): Bitmap {
                        preview.update(KeyboardGeometryPreference(), false, 100, true)
                        val width = context.resources.displayMetrics.widthPixels
                        preview.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                        preview.layout(0, 0, preview.measuredWidth, preview.measuredHeight)
                        return draw(preview)
                    }
                    val without = capture()
                    if (type == GeometryKeyboard.QWERTY) AppPreference.qwerty_show_keymap_symbols = true
                    else AppPreference.sumire_keymap_guide_japanese = true
                    val with = capture()
                    var changed = 0
                    for (y in 0 until with.height) for (x in 0 until with.width)
                        if (with.getPixel(x, y) != without.getPixel(x, y)) changed++
                    assertTrue("$type guide labels should change the rendered keys", changed > 50)
                }
            }
        } finally { restore(prefs, original) }
    }

    private fun draw(view: View): Bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
        view.draw(Canvas(it))
    }

    private fun findTextView(root: View, text: String): View {
        if (root is TextView && root.text.toString() == text) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) {
            val found = runCatching { findTextView(root.getChildAt(i), text) }.getOrNull()
            if (found != null) return found
        }
        error("No visible key labeled $text")
    }

    private fun awaitKeyboard(type: GeometryKeyboard): View {
        var found: View? = null
        await {
            instrumentation.runOnMainSync {
                found = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { root ->
                    val id = when (type) { GeometryKeyboard.QWERTY -> R.id.qwerty_view; GeometryKeyboard.TENKEY -> R.id.keyboard_view; GeometryKeyboard.SUMIRE -> R.id.custom_layout_default }
                    root.findViewById<View>(id)?.takeIf { view ->
                        view.isShown && view.width > 0 && view.height > 0 && when (view) {
                            is QWERTYKeyboardView -> view.effectiveSplitGapPx() > 0
                            is TenKey -> abs(view.findViewById<View>(com.kazumaproject.tenkey.R.id.key_1).rotation) > 0
                            is FlickKeyboardView -> runCatching { abs(findTextView(view, "あ").rotation) > 0 }.getOrDefault(false)
                            else -> false
                        }
                    }
                }
            }
            found != null
        }
        return requireNotNull(found)
    }

    private fun await(predicate: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < until) { if (predicate()) return; SystemClock.sleep(100) }
        error("Timed out waiting for the keyboard")
    }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    private fun restore(prefs: SharedPreferences, values: Map<String, *>) {
        val edit = prefs.edit().clear()
        values.forEach { (key, value) -> when (value) {
            is String -> edit.putString(key, value); is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value); is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value); is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }
        check(edit.commit())
    }
}
