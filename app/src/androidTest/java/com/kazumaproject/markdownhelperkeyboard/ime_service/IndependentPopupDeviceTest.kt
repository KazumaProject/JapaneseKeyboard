package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.os.ParcelFileDescriptor
import android.graphics.Bitmap
import java.io.File
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.preference.PreferenceManager
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.key_window.KeyWindowLayout
import com.kazumaproject.core.ui.skin.PopupDirection
import com.kazumaproject.core.ui.skin.SkinGuidePopup
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import com.kazumaproject.tenkey.TenKey
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Exercises real IME windows, including floating and both split bodies, on ART. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class IndependentPopupDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context = ins.targetContext
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        ins.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    private fun main(action: () -> Unit) = ins.runOnMainSync(action)
    private fun field(owner: Any, name: String): Any? =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    @Suppress("UNCHECKED_CAST")
    private fun states(keyboard: TenKey): Map<Int, Any> = field(keyboard, "pointerPopups") as Map<Int, Any>
    private fun descendants(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) yieldAll(descendants(root.getChildAt(i)))
    }
    private fun keyboards(): List<TenKey> = WindowInspector.getGlobalWindowViews().asSequence()
        .flatMap(::descendants).filterIsInstance<TenKey>().filter { view ->
            view.isAttachedToWindow && view.isShown && view.width > 100 && view.height > 100 && generateSequence(view as View?) { it.parent as? View }
                .all { it.alpha > 0.9f }
        }.distinct().sortedBy { center(it).first }.toList()
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        error(message)
    }
    private fun center(view: View): Pair<Float, Float> {
        val point = IntArray(2).also(view::getLocationOnScreen)
        return point[0] + view.width / 2f to point[1] + view.height / 2f
    }
    private fun popupCenter(state: Any): View {
        val guide = field(state, "guide") as? SkinGuidePopup
        if (guide == null) return field(state, "bubbleViewActive") as View
        @Suppress("UNCHECKED_CAST")
        val cells = field(guide, "cells") as Map<PopupDirection, KeyWindowLayout>
        return cells.getValue(PopupDirection.CENTER)
    }
    private fun visible(state: Any): Boolean = (field(state, "guide") as? SkinGuidePopup)?.isShowing
        ?: (field(state, "popupWindowActive") as android.widget.PopupWindow).isShowing

    @Test fun independentFlickAndLongPressPopupsStayAtBothKeysAcrossSkinsAndImeWindows() {
        check(context.packageName.endsWith(".lite")) { "Run with the separate Lite debug APK" }
        val saved = prefs.all.toMap()
        val oldIme = shell("settings get secure default_input_method")
        val targetIme = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val wasEnabled = shell("ime list -s").lines().contains(targetIme)
        var host: FastInputHostActivity? = null
        try {
            check(prefs.edit().clear()
                .putBoolean("independent_multi_touch_preference", true)
                .putInt("long_press_timeout_preference", 300)
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("live_conversion_preference", false)
                .putBoolean("clipboard_preview_enable_preference", false)
                .putBoolean("clipboard_history_preference", false)
                .putBoolean("landscape_force_qwerty_preference", false)
                .putBoolean("landscape_force_qwerty_romaji_preference", false)
                .putString("keyboard_order_preference", "[\"TENKEY\"]")
                .putString("split_keyboard_main_type", "TENKEY")
                .putString("split_keyboard_sub_type", "TENKEY")
                .putFloat("split_keyboard_main_portrait_width", 180f)
                .putFloat("split_keyboard_sub_portrait_width", 180f).commit())
            ins.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_0)
            shell("ime enable $targetIme")
            shell("ime set $targetIme")
            // Launch through the system and await the lifecycle directly. Some device builds
            // never signal Instrumentation.waitForIdleSync while the IME is rendering.
            shell("am start -n ${context.packageName}/com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity")
            await("Editor activity not resumed") {
                host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<FastInputHostActivity>().firstOrNull()
                host != null
            }
            for (skin in listOf(KeyboardSkinId.DEFAULT, KeyboardSkinId.CUPERTINO_LIGHT,
                KeyboardSkinId.CUPERTINO_DARK, KeyboardSkinId.CUPERTINO_CLASSIC)) {
                for (mode in listOf("normal", "floating", "split")) {
                    android.util.Log.i("IndependentPopupDevice", "$skin $mode")
                    check(prefs.edit().putString(KeyboardSkinId.PREFERENCE_KEY, skin.preferenceValue)
                        .putBoolean("keyboard_floating_preference", mode == "floating")
                        .putString("keyboard_order_preference", if (mode == "split") "[\"SPLIT\"]" else "[\"TENKEY\"]")
                        .commit())
                    main { host!!.restartEditorInput(true) }
                    await("$skin $mode keyboards not ready") {
                        val views = keyboards()
                        views.size == (if (mode == "split") 2 else 1) && views.all {
                            field(it, "keyboardSkinId") == skin && field(it, "independentMultiTouchEnabled") == true
                        }
                    }
                    // Let the editor restart and IME layout transitions finish before DOWN.
                    SystemClock.sleep(750)
                    var views = emptyList<TenKey>()
                    main { views = keyboards() }
                    for (bodyIndex in views.indices) for (releaseFirst in listOf(0, 1)) for (longPress in listOf(false, true)) {
                        main { host!!.restartEditorInput(true) }
                        SystemClock.sleep(500)
                        await("$skin $mode restarted keyboards not ready") {
                            keyboards().size == views.size
                        }
                        // Split panes are rebuilt on editor restart; never dispatch to a detached body.
                        lateinit var keyboard: TenKey
                        main { keyboard = keyboards()[bodyIndex] }
                        var keys = emptyList<View>()
                        main { keys = listOf(keyboard.findViewById(com.kazumaproject.tenkey.R.id.key_1),
                            keyboard.findViewById(com.kazumaproject.tenkey.R.id.key_2)) }
                        val downTime = SystemClock.uptimeMillis()
                        fun send(action: Int, index: Int, ids: List<Int>, offsets: Map<Int, Float> = emptyMap()) {
                            main {
                                val properties = Array(ids.size) { i -> MotionEvent.PointerProperties().apply {
                                    id = ids[i]; toolType = MotionEvent.TOOL_TYPE_FINGER
                                } }
                                val coords = Array(ids.size) { i -> MotionEvent.PointerCoords().apply {
                                    val position = center(keys[if (ids[i] == 3) 0 else 1])
                                    x = position.first; y = position.second + (offsets[ids[i]] ?: 0f)
                                    pressure = 1f; size = 1f
                                } }
                                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                                    action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), ids.size,
                                    properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
                                val origin = IntArray(2).also(keyboard::getLocationOnScreen)
                                event.offsetLocation(-origin[0].toFloat(), -origin[1].toFloat())
                                try {
                                    keyboard.dispatchTouchEvent(event)
                                } finally { event.recycle() }
                            }
                        }
                        send(MotionEvent.ACTION_DOWN, 0, listOf(3))
                        send(MotionEvent.ACTION_POINTER_DOWN, 1, listOf(3, 19))
                        main { assertEquals("$skin $mode both pointer sessions started", setOf(3, 19), states(keyboard).keys) }
                        val delta = -keys[0].height.toFloat()
                        if (!longPress) send(MotionEvent.ACTION_MOVE, 0, listOf(3, 19), mapOf(3 to delta, 19 to delta))
                        await("$skin $mode longPress=$longPress both popups") {
                            states(keyboard).size == 2 && states(keyboard).values.all(::visible)
                        }
                        SystemClock.sleep(150)
                        var owners = emptyList<Any>()
                        main {
                            owners = listOf(states(keyboard).getValue(3), states(keyboard).getValue(19))
                            owners.forEachIndexed { index, state ->
                                val actual = center(popupCenter(state))
                                val expected = center(keys[index])
                                assertTrue("$skin $mode popup $index at its own key: $actual vs $expected",
                                    abs(actual.first - expected.first) < 12 * context.resources.displayMetrics.density)
                                val text = if (field(state, "guide") != null)
                                    (popupCenter(state) as ViewGroup).getChildAt(0) as TextView
                                    else field(state, "popTextActive") as TextView
                                assertEquals(if (longPress) { if (index == 0) "あ" else "か" }
                                    else { if (index == 0) "う" else "く" }, text.text.toString())
                            }
                        }
                        if (longPress && releaseFirst == 0 && bodyIndex == 0) {
                            ins.uiAutomation.takeScreenshot()?.let { bitmap ->
                                File(context.cacheDir, "independent_${skin.preferenceValue}_$mode.png")
                                    .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                bitmap.recycle()
                            }
                        }
                        // Moving one finger must not select or reposition the other popup.
                        val offsets = if (longPress) mapOf(3 to delta) else mapOf(3 to delta * 1.2f, 19 to delta)
                        send(MotionEvent.ACTION_MOVE, 0, listOf(3, 19), offsets)
                        send(MotionEvent.ACTION_POINTER_UP, releaseFirst, listOf(3, 19), offsets)
                        val surviving = if (releaseFirst == 0) 19 else 3
                        main {
                            assertFalse("Released owner's guide", visible(owners[releaseFirst]))
                            assertTrue("Remaining owner's guide", visible(owners[1 - releaseFirst]))
                            assertEquals(setOf(surviving), states(keyboard).keys)
                        }
                        send(MotionEvent.ACTION_UP, 0, listOf(surviving), offsets)
                        await("$skin $mode guides dismissed") { owners.none(::visible) && states(keyboard).isEmpty() }
                        await("$skin $mode input committed") { host!!.editText.text.toString() ==
                            if (longPress) { if (releaseFirst == 0) "うか" else "かう" }
                            else { if (releaseFirst == 0) "うく" else "くう" } }
                    }
                }
            }
        } finally {
            main { host?.finish() }
            shell("ime set $oldIme")
            val edit = prefs.edit().clear()
            saved.forEach { (key, value) -> when (value) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(key, value as Set<String>)
            } }
            check(edit.commit())
            if (!wasEnabled) shell("ime disable $targetIme")
            ins.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
        }
    }
}
