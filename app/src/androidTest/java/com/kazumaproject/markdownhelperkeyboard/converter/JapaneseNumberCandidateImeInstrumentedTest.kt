package com.kazumaproject.markdownhelperkeyboard.converter

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.PointF
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JapaneseNumberCandidateImeInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun numberCandidatesCommitAcrossAllModesAndBothBackendsUsingSoftKeyboard() {
        configureAccessibilityInspection()
        val context = instrumentation.targetContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val originalPreferences = preferences.all
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText().trim() }
        val targetIme = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val originalIme = shell("settings get secure default_input_method")
        val wasEnabled = shell("ime list -s").lines().any {
            ComponentName.unflattenFromString(it) == ComponentName.unflattenFromString(targetIme)
        }
        shell("ime enable $targetIme")
        shell("ime set $targetIme")
        try {
            preferences.edit()
                .putString("keyboard_order_preference", """["TENKEY","SUMIRE","QWERTY","ROMAJI","CUSTOM"]""")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("flick_input_only_preference", true)
                .putBoolean("live_conversion_preference", false)
                .putBoolean("japanese_number_candidates_enable_preference", true)
                .putBoolean("learn_dictionary_preference", false)
                .putBoolean("candidate_order_override_enable_preference", false)
                .putString("candidate_tab_preference", """["PREDICTION","CONVERSION","EISUKANA"]""")
                .commit()
            for (incremental in listOf(false, true)) {
                for (tab in listOf("予測", "変換", "英数カナ", null)) {
                    preferences.edit()
                        .putBoolean("incremental_conversion_session_preference", incremental)
                        .putBoolean("conversion_bunsetsu_separation_preference", incremental)
                        .putBoolean("candidate_tab_visibility_preference", tab != null)
                        .commit()
                    val scenario = ActivityScenario.launch<FastInputHostActivity>(
                        Intent(context, FastInputHostActivity::class.java),
                    )
                    try {
                        for ((reading, expected) in listOf(
                            "よじ" to "4時", "さんにん" to "3人",
                            "ごえん" to "5円", "にじゅっぷん" to "20分",
                        )) {
                            instrumentation.sendStatus(2, Bundle().apply {
                                putString("stream", "Checking incremental=$incremental tab=$tab input=$reading\n")
                            })
                            scenario.onActivity { it.restartEditorInput(true) }
                            instrumentation.waitForIdleSync()
                            SystemClock.sleep(1500)
                            typeNumberReadingWithFlicks(reading)
                            awaitEditorText(scenario, reading, committed = false)
                            if (tab != null) {
                                assertTrue(tap(awaitVisibleText(tab).center()))
                                SystemClock.sleep(250)
                            }
                            val deadline = SystemClock.uptimeMillis() + 8000
                            var candidate: AccessibilityNodeInfo? = null
                            val observedCandidates = linkedSetOf<String>()
                            while (candidate == null && SystemClock.uptimeMillis() < deadline) {
                                candidate = instrumentation.uiAutomation.windows.asSequence()
                                    .mapNotNull { it.root }
                                    .mapNotNull { root -> findDescendant(root) {
                                        if (it.viewIdResourceName?.endsWith(":id/suggestion_item_text_view") == true) {
                                            observedCandidates += it.text?.toString().orEmpty()
                                        }
                                        it.isVisibleToUser && it.text?.toString()?.trim() == expected
                                    } }.firstOrNull()
                                if (candidate == null) {
                                    findVisibleNodeById("suggestion_recycler_view")
                                        ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                                    SystemClock.sleep(250)
                                }
                            }
                            var clickable: AccessibilityNodeInfo? = requireNotNull(candidate) {
                                "Missing $expected: incremental=$incremental tab=$tab observed=$observedCandidates"
                            }
                            while (clickable != null && !clickable.isClickable) clickable = clickable.parent
                            assertTrue("Candidate cannot be clicked: $expected",
                                clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)

                            awaitEditorText(scenario, expected, committed = true)
                            instrumentation.sendStatus(2, Bundle().apply {
                                putString("stream", "NUMBER_CANDIDATE_COMMIT incremental=$incremental tab=$tab input=$reading PASS\n")
                            })
                        }
                    } finally {
                        scenario.close()
                    }
                }
            }
        } finally {
            preferences.edit().clear().also { editor ->
                originalPreferences.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is String -> editor.putString(key, value)
                        is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
            }.commit()
            if (originalIme.isNotEmpty() && originalIme != "null") shell("ime set $originalIme")
            if (!wasEnabled) shell("ime disable $targetIme")
        }
    }

    private fun awaitEditorText(
        scenario: ActivityScenario<FastInputHostActivity>,
        expected: String,
        committed: Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + 8000
        var actual = ""
        var composingStart = -1
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity {
                actual = it.editText.text.toString()
                composingStart = BaseInputConnection.getComposingSpanStart(it.editText.text)
            }
            if (actual == expected && (!committed || composingStart == -1)) return
            SystemClock.sleep(50)
        }
        error("Expected $expected (committed=$committed), got $actual (composingStart=$composingStart)")
    }

    private fun typeNumberReadingWithFlicks(reading: String) {
        fun press(id: String) {
            assertTrue(tap(awaitVisibleBounds(id).center()))
            SystemClock.sleep(100)
        }
        fun flick(id: String, dx: Float, dy: Float) {
            val bounds = awaitVisibleBounds(id)
            val start = bounds.center()
            val end = PointF(start.x + bounds.width() * dx, start.y + bounds.height() * dy)
            val downTime = SystemClock.uptimeMillis()
            assertTrue(inject(downTime, MotionEvent.ACTION_DOWN, start))
            SystemClock.sleep(16)
            assertTrue(inject(downTime, MotionEvent.ACTION_MOVE, end))
            SystemClock.sleep(16)
            assertTrue(inject(downTime, MotionEvent.ACTION_UP, end))
            SystemClock.sleep(100)
        }
        fun n() = flick("key_11", 0f, -0.7f)
        when (reading) {
            "よじ" -> {
                flick("key_8", 0f, 0.7f)
                assertTrue(flickLeft("key_3"))
                press("key_small_letter")
            }
            "さんにん" -> {
                press("key_3")
                n()
                assertTrue(flickLeft("key_5"))
                n()
            }
            "ごえん" -> {
                flick("key_2", 0f, 0.7f)
                press("key_small_letter")
                flick("key_1", 0.7f, 0f)
                n()
            }
            "にじゅっぷん" -> {
                assertTrue(flickLeft("key_5"))
                assertTrue(flickLeft("key_3"))
                press("key_small_letter")
                flick("key_8", 0f, -0.7f)
                press("key_small_letter")
                flick("key_4", 0f, -0.7f)
                press("key_small_letter")
                flick("key_6", 0f, -0.7f)
                press("key_small_letter")
                press("key_small_letter")
                n()
            }
            else -> error("Unsupported test reading: $reading")
        }
    }

    private fun configureAccessibilityInspection() {
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
    }

    private fun flickLeft(keyId: String): Boolean {
        val bounds = awaitVisibleBounds(keyId)
        val start = bounds.center()
        val end = PointF(start.x - bounds.width() * 0.70f, start.y)
        val downTime = SystemClock.uptimeMillis()
        var injected = inject(downTime, MotionEvent.ACTION_DOWN, start)
        SystemClock.sleep(16)
        injected = inject(downTime, MotionEvent.ACTION_MOVE, end) && injected
        SystemClock.sleep(16)
        injected = inject(downTime, MotionEvent.ACTION_UP, end) && injected
        SystemClock.sleep(100)
        return injected
    }

    private fun tap(point: PointF): Boolean {
        val downTime = SystemClock.uptimeMillis()
        var injected = inject(downTime, MotionEvent.ACTION_DOWN, point)
        SystemClock.sleep(32)
        injected = inject(downTime, MotionEvent.ACTION_UP, point) && injected
        return injected
    }

    private fun inject(downTime: Long, action: Int, point: PointF): Boolean {
        val event = MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            point.x,
            point.y,
            0,
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        return instrumentation.uiAutomation.injectInputEvent(event, true).also {
            event.recycle()
        }
    }

    private fun awaitVisibleBounds(idName: String): Rect {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        while (SystemClock.uptimeMillis() < deadline) {
            findVisibleNodeById(idName)?.let { node ->
                return Rect().also(node::getBoundsInScreen)
            }
            SystemClock.sleep(50)
        }
        error("Timed out waiting for visible id=$idName")
    }

    private fun awaitVisibleText(text: String): Rect {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        while (SystemClock.uptimeMillis() < deadline) {
            for (window in instrumentation.uiAutomation.windows) {
                val root = window.root ?: continue
                val found = findDescendant(root) { node ->
                    node.isVisibleToUser && node.text?.toString() == text
                }
                if (found != null) {
                    return Rect().also(found::getBoundsInScreen)
                }
            }
            SystemClock.sleep(50)
        }
        error("Timed out waiting for visible text=$text")
    }

    private fun findVisibleNodeById(idName: String): AccessibilityNodeInfo? {
        for (window in instrumentation.uiAutomation.windows) {
            val root = window.root ?: continue
            val found = findDescendant(root) { node ->
                node.isVisibleToUser &&
                    node.viewIdResourceName?.endsWith(":id/$idName") == true
            }
            if (found != null) return found
        }
        return null
    }

    private fun findDescendant(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (predicate(node)) return node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return null
    }

    private fun Rect.center(): PointF = PointF(exactCenterX(), exactCenterY())
}
