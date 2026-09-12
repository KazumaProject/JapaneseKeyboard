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
import org.junit.Assert.assertEquals
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
        val orderOnly = InstrumentationRegistry.getArguments().getString("number_order_only") == "true"
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
                .putString("number_candidate_order_preference", "half_full_kanji")
                .putBoolean("learn_dictionary_preference", false)
                .putBoolean("candidate_order_override_enable_preference", false)
                .putString("candidate_tab_preference", """["PREDICTION","CONVERSION","EISUKANA"]""")
                .commit()
            for (incremental in if (orderOnly) emptyList() else listOf(false, true)) for (bunsetsu in listOf(false, true)) {
                for (tab in listOf("予測", "変換", "英数カナ", null)) {
                    preferences.edit()
                        .putBoolean("incremental_conversion_session_preference", incremental)
                        .putBoolean("conversion_bunsetsu_separation_preference", bunsetsu)
                        .putBoolean("candidate_tab_visibility_preference", tab != null)
                        .commit()
                    val scenario = ActivityScenario.launch<FastInputHostActivity>(
                        Intent(context, FastInputHostActivity::class.java),
                    )
                    try {
                        val positiveCases = listOf(
                            "よじ" to "4時", "さんにん" to "3人",
                            "ごえん" to "5円", "にじゅっぷん" to "20分", "に" to "２",
                        ) + if (tab == "英数カナ") emptyList() else listOf("しちごさん" to "七五三")
                        for ((reading, expected) in positiveCases) {
                            instrumentation.sendStatus(2, Bundle().apply {
                                putString("stream", "Checking incremental=$incremental bunsetsu=$bunsetsu tab=$tab input=$reading\n")
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
                                "Missing $expected: incremental=$incremental bunsetsu=$bunsetsu tab=$tab observed=$observedCandidates"
                            }
                            while (clickable != null && !clickable.isClickable) clickable = clickable.parent
                            assertTrue("Candidate cannot be clicked: $expected",
                                clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)

                            awaitEditorText(scenario, expected, committed = true)
                            instrumentation.sendStatus(2, Bundle().apply {
                                putString("stream", "NUMBER_CANDIDATE_COMMIT incremental=$incremental bunsetsu=$bunsetsu tab=$tab input=$reading PASS\n")
                            })
                        }
                        for (reading in listOf("ごぜん", "ぜんご")) {
                            scenario.onActivity { it.restartEditorInput(true) }
                            instrumentation.waitForIdleSync()
                            SystemClock.sleep(1500)
                            typeNumberReadingWithFlicks(reading)
                            awaitEditorText(scenario, reading, committed = false)
                            if (tab != null) {
                                assertTrue(tap(awaitVisibleText(tab).center()))
                                SystemClock.sleep(250)
                            }
                            val observed = inspectCandidateStrip()
                            assertNoNumericCandidates(reading, observed)
                            instrumentation.sendStatus(2, Bundle().apply {
                                putString("stream", "NUMBER_FORBIDDEN_MODE_UI incremental=$incremental bunsetsu=$bunsetsu tab=$tab input=$reading PASS\n")
                            })
                        }
                    } finally {
                        scenario.close()
                    }
                }
            }
            preferences.edit().putBoolean("candidate_tab_visibility_preference", false)
                .putBoolean("incremental_conversion_session_preference", true)
                .putBoolean("conversion_bunsetsu_separation_preference", true).commit()
            val extra = ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java))
            try {
                for (reading in if (orderOnly) emptyList() else listOf("ごぜん", "ぜんご", "いちぜん", "じゅうよ", "にびゃく", "ごぴゃく", "にぜん", "いっまん", "じゅっおく", "にほんご", "はちみつ", "いちちょう", "はちちょう", "じゅうちょう", "じゅ", "ひゃ", "いっ")) {
                    extra.onActivity { it.restartEditorInput(true) }
                    instrumentation.waitForIdleSync()
                    SystemClock.sleep(1500)
                    typeNumberReadingWithFlicks(reading)
                    awaitEditorText(extra, reading, committed = false)
                    SystemClock.sleep(500)
                    assertNoNumericCandidates(reading, inspectCandidateStrip())
                    instrumentation.sendStatus(2, Bundle().apply { putString("stream", "NUMBER_FORBIDDEN_UI input=$reading PASS\n") })
                }
                for (order in com.kazumaproject.markdownhelperkeyboard.converter.engine.NumberCandidateOrder.entries) {
                    preferences.edit().putString("number_candidate_order_preference", order.preferenceValue)
                        .putBoolean("live_conversion_preference", true).commit()
                    val forms = listOf("3人", "３人", "三人")
                    for (selected in forms) {
                        extra.onActivity { it.restartEditorInput(true) }
                        instrumentation.waitForIdleSync()
                        SystemClock.sleep(1500)
                        typeNumberReadingWithFlicks("さんにん")
                        awaitEditorText(extra, forms[order.indices.first()], committed = false)
                        val displayed = inspectCandidateStrip()
                        assertEquals("Visible order $order", order.indices.map(forms::get), displayed.filter { it in forms })
                        var candidate: AccessibilityNodeInfo? = null
                        repeat(12) {
                            if (candidate == null) {
                                candidate = instrumentation.uiAutomation.windows.mapNotNull { it.root }.firstNotNullOfOrNull { root ->
                                    findDescendant(root) { it.isVisibleToUser && it.text?.toString()?.trim() == selected &&
                                        it.viewIdResourceName?.endsWith(":id/suggestion_item_text_view") == true }
                                }
                                if (candidate == null) findVisibleNodeById("suggestion_recycler_view")?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                                SystemClock.sleep(100)
                            }
                        }
                        var clickable = requireNotNull(candidate) { "$order missing $selected" }
                        while (!clickable.isClickable && clickable.parent != null) clickable = clickable.parent
                        assertTrue(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                        awaitEditorText(extra, selected, committed = true)
                        instrumentation.sendStatus(2, Bundle().apply { putString("stream", "NUMBER_ORDER_LIVE_COMMIT order=$order selected=$selected PASS\n") })
                    }
                }
            } finally {
                extra.close()
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

    private fun inspectCandidateStrip(): List<String> {
        val observed = linkedSetOf<String>()
        repeat(12) {
            instrumentation.uiAutomation.windows.mapNotNull { it.root }.forEach { collectCandidateTexts(it, observed) }
            findVisibleNodeById("suggestion_recycler_view")?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(50)
        }
        // RecyclerView scroll actions animate asynchronously. Without waiting, repeated
        // actions coalesce and leave the first candidates off-screen before selection.
        repeat(24) {
            val moved = findVisibleNodeById("suggestion_recycler_view")
                ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
            if (moved) SystemClock.sleep(100)
        }
        SystemClock.sleep(250)
        assertTrue("Candidate inspection must not pass on an empty strip", observed.isNotEmpty())
        return observed.toList()
    }

    private fun assertNoNumericCandidates(reading: String, observed: List<String>) {
        val numeric = Regex("[0-9０-９⁰¹²³⁴⁵⁶⁷⁸⁹₀₁₂₃₄₅₆₇₈₉①-⑳❶-❿⑴-⒛㉑-㉟㊱-㊿Ⅰ-Ⅻⅰ-ⅻ]|^[〇零一二三四五六七八九十百千万億兆京]+(?:時|分|人|円)?$")
        assertTrue("$reading unexpected visible candidates: $observed", observed.none { numeric.containsMatchIn(it) })
    }

    private fun collectCandidateTexts(node: AccessibilityNodeInfo, output: MutableSet<String>) {
        if (node.isVisibleToUser && node.viewIdResourceName?.endsWith(":id/suggestion_item_text_view") == true) {
            // SuggestionAdapter pads labels with spaces for touch targets.
            node.text?.toString()?.trim()?.let(output::add)
        }
        for (index in 0 until node.childCount) node.getChild(index)?.let { collectCandidateTexts(it, output) }
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
        val voiced = mapOf('が' to 'か', 'ぎ' to 'き', 'ぐ' to 'く', 'げ' to 'け', 'ご' to 'こ',
            'ざ' to 'さ', 'じ' to 'し', 'ず' to 'す', 'ぜ' to 'せ', 'ぞ' to 'そ',
            'ば' to 'は', 'び' to 'ひ', 'ぶ' to 'ふ', 'べ' to 'へ', 'ぼ' to 'ほ')
        val semiVoiced = mapOf('ぱ' to 'は', 'ぴ' to 'ひ', 'ぷ' to 'ふ', 'ぺ' to 'へ', 'ぽ' to 'ほ')
        val small = mapOf('ゃ' to 'や', 'ゅ' to 'ゆ', 'ょ' to 'よ', 'っ' to 'つ')
        val rows = listOf("あいうえお", "かきくけこ", "さしすせそ", "たちつてと", "なにぬねの", "はひふへほ", "まみむめも", "や ゆ よ", "らりるれろ")
        for (char in reading) {
            val base = voiced[char] ?: semiVoiced[char] ?: small[char] ?: char
            if (base == 'ん') flick("key_11", 0f, -0.7f) else {
                val row = rows.indexOfFirst { base in it }
                require(row >= 0) { "Unsupported test character: $char" }
                val key = "key_${row + 1}"
                when (rows[row].indexOf(base)) {
                    0 -> press(key)
                    1 -> flick(key, -0.7f, 0f)
                    2 -> flick(key, 0f, -0.7f)
                    3 -> flick(key, 0.7f, 0f)
                    4 -> flick(key, 0f, 0.7f)
                }
                repeat(if (char in semiVoiced) 2 else if (char in voiced || char in small) 1 else 0) { press("key_small_letter") }
            }
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
