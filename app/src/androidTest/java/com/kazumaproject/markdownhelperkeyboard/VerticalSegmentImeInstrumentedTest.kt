package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.view.accessibility.AccessibilityNodeInfo
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.inputmethod.BaseInputConnection
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.BeforeClass
import org.junit.AfterClass
import org.junit.runner.RunWith

/** Exercises the shipped IME, its composing spans, native touch routing and candidate window. */
@androidx.test.filters.SdkSuppress(minSdkVersion = 29)
@RunWith(AndroidJUnit4::class)
class VerticalSegmentImeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private lateinit var host: EditorHost

    @Test fun liveBoundaryEditKeepsComposingAndSurvivesFurtherInput() = withKeyboard(live = true) {
        typeSentence()
        await("live converted text") { text().contains("明日") }
        assertComposing()
        await("first segment") { rangeNode(0, 4) != null }
        assertBarWithinKeyboard(R.id.keyboard_view)
        dragFirstBoundaryLeft()
        await("changed boundary") { rangeNode(0, 3) != null }
        await("new candidate applied") { segmentLabels().isNotEmpty() && text() == segmentLabels().joinToString("") }
        assertComposing()
        softKey(com.kazumaproject.tenkey.R.id.key_1)
        await("boundary retained after typing") { rangeNode(0, 3) != null && segmentReadings().joinToString("").endsWith("あ") }
        await("first candidate after typing") { text() == segmentLabels().joinToString("") }
        assertComposing()
        val expected = text()
        softKey(com.kazumaproject.tenkey.R.id.key_enter)
        await { text() == expected }
        host.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
    }

    @Test fun enterImmediatelyAfterBoundaryEditCommitsTheWholeSentence() = withKeyboard(live = true) {
        typeSentence()
        await("first segment") { rangeNode(0, 4) != null }
        assertTrue(rangeNode(0, 4)!!.performAction(R.id.segment_end_left))
        softKey(com.kazumaproject.tenkey.R.id.key_enter)
        await("whole sentence committed") {
            var start = 0
            host.onActivity { start = BaseInputConnection.getComposingSpanStart(it.editText.text) }
            text().isNotEmpty() && start == -1
        }
        assertFalse(text().contains("\n"))
    }

    @Test fun candidateAndSegmentSelectionRemainUncommitted() = withKeyboard {
        typeSentence()
        await("segment preview") { segmentLabels().isNotEmpty() }
        val label = segmentLabels().joinToString("")
        instrumentation.runOnMainSync {
            val recycler = android.view.inspector.WindowInspector.getGlobalWindowViews()
                .mapNotNull { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestion_recycler_view) }
                .first { it.isShown && it.childCount > 0 }
            assertTrue("whole candidate not clickable", recycler.getChildAt(0).performClick())
        }
        awaitText(label)
        assertComposing()
        val first = allNodes().firstOrNull { it.contentDescription?.toString()?.startsWith("文節「") == true && it.isClickable }
            ?: allNodes().firstOrNull { it.contentDescription?.toString()?.startsWith("Select segment") == true && it.isClickable }
        assertNotNull(first)
        val before = segmentLabels()
        assertTrue(first!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        // A tap during the transition must not commit a stale whole-sentence candidate.
        instrumentation.runOnMainSync {
            val recycler = android.view.inspector.WindowInspector.getGlobalWindowViews()
                .mapNotNull { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestion_recycler_view) }
                .first { it.isShown && it.childCount > 0 }
            recycler.getChildAt(0).performClick()
        }
        assertComposing()
        await("focused segment candidates") {
            var ready = false
            instrumentation.runOnMainSync {
                val recycler = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .mapNotNull { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestion_recycler_view) }
                    .firstOrNull { it.isShown }
                val adapter = recycler?.adapter as? com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
                val firstText = recycler?.findViewHolderForAdapterPosition(0)?.itemView
                    ?.findViewById<android.widget.TextView>(R.id.suggestion_item_text_view)
                ready = adapter?.suggestions?.firstOrNull()?.string == before.first() &&
                    firstText?.contentDescription?.toString() == before.first()
            }
            ready
        }
        instrumentation.runOnMainSync {
            val recycler = android.view.inspector.WindowInspector.getGlobalWindowViews()
                .mapNotNull { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestion_recycler_view) }
                .first { it.isShown }
            recycler.scrollToPosition(1)
        }
        await("segment alternatives") {
            var ready = false
            instrumentation.runOnMainSync {
                val recycler = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .mapNotNull { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestion_recycler_view) }
                    .firstOrNull { it.isShown && (it.adapter?.itemCount ?: 0) > 1 }
                ready = recycler?.findViewHolderForAdapterPosition(1) != null
            }
            ready
        }
        instrumentation.runOnMainSync {
            val recycler = android.view.inspector.WindowInspector.getGlobalWindowViews()
                .mapNotNull { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestion_recycler_view) }
                .first { it.isShown && it.findViewHolderForAdapterPosition(1) != null }
            assertTrue(recycler.findViewHolderForAdapterPosition(1)!!.itemView.performClick())
        }
        await("only selected segment changed") {
            val after = segmentLabels()
            after.size == before.size && after.first() != before.first() && after.drop(1) == before.drop(1)
        }
        assertEquals(segmentLabels().joinToString(""), text())
        assertComposing()
        softKey(com.kazumaproject.tenkey.R.id.key_enter)
        host.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
    }

    @Test fun draggingSegmentMovesItsReadingAndTextWithoutCommitting() = withKeyboard {
        typeSentence()
        await("segments available") { segmentLabels().size >= 2 }
        val before = segmentLabels()
        fun cards() = allNodes().filter {
            it.isLongClickable && (it.contentDescription?.toString()?.startsWith("文節「") == true ||
                it.contentDescription?.toString()?.startsWith("Select segment") == true)
        }
        val nodes = cards()
        val source = android.graphics.Rect().also(nodes[1]::getBoundsInScreen)
        val target = android.graphics.Rect().also(nodes[0]::getBoundsInScreen)
        drag(source.centerX().toFloat(), source.centerY().toFloat(), target.centerX().toFloat(),
            target.centerY().toFloat(), android.view.ViewConfiguration.getLongPressTimeout().toLong() + 200)
        await("segment reordered") { segmentLabels().take(2) == listOf(before[1], before[0]) }
        assertComposing()
        val expected = segmentLabels().joinToString("")
        assertEquals(expected, text())
        softKey(com.kazumaproject.tenkey.R.id.key_enter)
        host.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
    }

    @Test fun disabledEditorLeavesVerticalCandidatesInTheirOriginalMode() = withKeyboard(show = false) {
        typeSentence()
        SystemClock.sleep(1000)
        assertFalse(segmentBarVisible())
        instrumentation.runOnMainSync {
            val recycler = android.view.inspector.WindowInspector.getGlobalWindowViews()
                .mapNotNull { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestion_recycler_view) }
                .first { it.isShown && it.childCount > 0 }
            assertTrue(recycler.getChildAt(0).performClick())
        }
        await("candidate committed") {
            var start = 0
            host.onActivity { start = BaseInputConnection.getComposingSpanStart(it.editText.text) }
            start == -1
        }
    }

    @Test fun floatingKeyboardAndSumireShowEditor() {
        withKeyboard(floating = true, live = true) {
            typeSentence()
            await("floating segment bar") { segmentBarVisible() }
            assertBarWithinKeyboard(R.id.keyboard_view_floating)
            assertComposing()
        }
        withKeyboard(sumire = true, live = true) {
            tapSumireA()
            await("sumire segment bar") { segmentBarVisible() }
            assertBarWithinKeyboard(R.id.custom_layout_default)
            assertComposing()
        }
    }

    @Test fun rightSideLandscapeCandidatesShowEditor() = withKeyboard(live = true, landscape = true, side = "RIGHT") {
        softKey(com.kazumaproject.tenkey.R.id.key_1)
        if (text().isEmpty()) {
            host.onActivity { it.requestImeForEditor() }
            SystemClock.sleep(500)
            softKey(com.kazumaproject.tenkey.R.id.key_1)
        }
        await("landscape input") { text().isNotEmpty() }
        await("landscape segment bar") { segmentBarVisible() }
        assertBarWithinKeyboard(R.id.keyboard_view)
        assertComposing()
    }

    @Test fun wrappedLongCandidateScrollsAndCommitsItsFullText() = withKeyboard(show = false, wrapLongText = true) {
        softKey(com.kazumaproject.tenkey.R.id.key_1)
        await("side candidates") {
            var ready = false
            instrumentation.runOnMainSync {
                ready = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .mapNotNull { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestion_recycler_view) }
                    .any { it.isShown && it.layoutManager is com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.SideCandidateFlowLayoutManager && it.childCount > 0 }
            }
            ready
        }
        val expected = "長い変換候補を省略せずに表示します".repeat(5)
        val candidate = com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(expected, 1, 1u, 0)
        instrumentation.runOnMainSync {
            val recycler = android.view.inspector.WindowInspector.getGlobalWindowViews()
                .mapNotNull { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestion_recycler_view) }
                .first { it.isShown && it.layoutManager is com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.SideCandidateFlowLayoutManager }
            (recycler.adapter as com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter).suggestions = listOf(candidate)
        }
        await("wrapped candidate") {
            var ready = false
            instrumentation.runOnMainSync {
                val recycler = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .mapNotNull { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestion_recycler_view) }
                    .firstOrNull { it.isShown && it.layoutManager is com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.SideCandidateFlowLayoutManager }
                val item = recycler?.findViewHolderForAdapterPosition(0)?.itemView
                val text = item?.findViewById<android.widget.TextView>(R.id.suggestion_item_text_view)
                val columns = text?.parent as? android.widget.LinearLayout
                ready = columns != null && columns.childCount > 1 && text.contentDescription == expected &&
                    item.findViewById<android.view.View>(R.id.candidate_divider).visibility == android.view.View.GONE
            }
            ready
        }
        instrumentation.runOnMainSync {
            val recycler = android.view.inspector.WindowInspector.getGlobalWindowViews()
                .mapNotNull { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestion_recycler_view) }
                .first { it.isShown && it.layoutManager is com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.SideCandidateFlowLayoutManager }
            val item = recycler.findViewHolderForAdapterPosition(0)!!.itemView
            val columns = item.findViewById<android.widget.TextView>(R.id.suggestion_item_text_view).parent as android.widget.LinearLayout
            for (i in 0 until columns.childCount) {
                val column = columns.getChildAt(i) as android.widget.TextView
                val requiredHeight = column.layout.height + column.compoundPaddingTop + column.compoundPaddingBottom
                assertTrue("Column $i clips its last line: needs $requiredHeight px, has ${column.height} px",
                    requiredHeight <= column.height)
            }
        }
        fun visibleColumnWidth(): Int {
            var width = 0
            instrumentation.runOnMainSync {
                val recycler = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .mapNotNull { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestion_recycler_view) }
                    .firstOrNull { it.isShown && it.layoutManager is com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.SideCandidateFlowLayoutManager }
                val text = recycler?.findViewHolderForAdapterPosition(0)?.itemView
                    ?.findViewById<android.widget.TextView>(R.id.suggestion_item_text_view)
                val columns = text?.parent as? android.widget.LinearLayout
                if (text?.contentDescription == expected && columns != null && columns.childCount > 1) {
                    width = columns.getChildAt(0).layoutParams.width
                }
            }
            return width
        }
        val initialColumnWidth = visibleColumnWidth()
        val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        check(prefs.edit().putInt("keyboard_geometry_tenkey_portrait_candidate_spacing_dp", 20).commit())
        await("candidate spacing updated") { visibleColumnWidth() > initialColumnWidth }
        check(prefs.edit().putInt("keyboard_geometry_tenkey_portrait_candidate_spacing_dp", 4).commit())
        await("candidate spacing restored") { visibleColumnWidth() == initialColumnWidth }
        var lastVisible: android.graphics.Rect? = null
        repeat(20) {
            instrumentation.runOnMainSync {
                val recycler = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .mapNotNull { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestion_recycler_view) }
                    .first { it.isShown && it.layoutManager is com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.SideCandidateFlowLayoutManager }
                val item = recycler.findViewHolderForAdapterPosition(0)!!.itemView
                val columns = item.findViewById<android.view.View>(R.id.suggestion_item_text_view).parent as android.widget.LinearLayout
                val last = columns.getChildAt(columns.childCount - 1)
                val visible = android.graphics.Rect()
                if (last.getLocalVisibleRect(visible) && visible.width() > 0) {
                    val location = IntArray(2).also(last::getLocationOnScreen)
                    visible.offset(location[0], location[1])
                    lastVisible = visible
                }
                else recycler.scrollBy(recycler.width / 2, 0)
            }
            if (lastVisible != null) return@repeat
            SystemClock.sleep(100)
        }
        assertNotNull("The last column must be reachable by scrolling", lastVisible)
        val visible = lastVisible!!
        tapAt(visible.centerX().toFloat(), visible.centerY().toFloat())
        awaitText(expected)
        host.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
    }

    private fun softKey(id: Int, dx: Int = 0, dy: Int = 0) {
        var bounds: android.graphics.Rect? = null
        await("keyboard key $id") {
            instrumentation.runOnMainSync {
                val view = android.view.inspector.WindowInspector.getGlobalWindowViews().asReversed()
                    .mapNotNull { it.findViewById<android.view.View>(id) }.firstOrNull { it.isShown }
                bounds = view?.let {
                    val location = IntArray(2).also(it::getLocationOnScreen)
                    android.graphics.Rect(location[0], location[1], location[0] + it.width, location[1] + it.height)
                }
            }
            bounds != null && !bounds!!.isEmpty
        }
        val area = bounds!!
        val distance = 45 * instrumentation.targetContext.resources.displayMetrics.density
        drag(area.centerX().toFloat(), area.centerY().toFloat(),
            area.centerX() + dx * distance, area.centerY() + dy * distance)
        SystemClock.sleep(180)
    }

    private fun typeSentence() {
        softKey(com.kazumaproject.tenkey.R.id.key_1)       // あ
        softKey(com.kazumaproject.tenkey.R.id.key_3, -1)  // し
        softKey(com.kazumaproject.tenkey.R.id.key_4)       // た
        softKey(com.kazumaproject.tenkey.R.id.key_6)       // は
        softKey(com.kazumaproject.tenkey.R.id.key_6)       // は
        softKey(com.kazumaproject.tenkey.R.id.key_9, 1)   // れ
        softKey(com.kazumaproject.tenkey.R.id.key_9, 0, -1) // る
    }

    private fun tapSumireA() {
        var bounds: android.graphics.Rect? = null
        await("Sumire あ key") {
            instrumentation.runOnMainSync {
                fun find(view: android.view.View): android.view.View? {
                    if (view is android.widget.TextView && view.text.toString() == "あ") return view
                    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) {
                        find(view.getChildAt(i))?.let { return it }
                    }
                    return null
                }
                bounds = android.view.inspector.WindowInspector.getGlobalWindowViews().asReversed()
                    .mapNotNull { it.findViewById<android.view.View>(R.id.custom_layout_default) }
                    .filter { it.isShown }.mapNotNull(::find).firstOrNull()
                    ?.let {
                        val location = IntArray(2).also(it::getLocationOnScreen)
                        android.graphics.Rect(location[0], location[1], location[0] + it.width, location[1] + it.height)
                    }
            }
            bounds != null && !bounds!!.isEmpty
        }
        val area = bounds!!
        drag(area.centerX().toFloat(), area.centerY().toFloat(), area.centerX().toFloat(), area.centerY().toFloat())
        SystemClock.sleep(250)
    }

    private fun dragFirstBoundaryLeft() {
        var location: IntArray? = null
        instrumentation.runOnMainSync {
            val description = instrumentation.targetContext.getString(R.string.segment_range_description, 0, 4)
            fun find(view: android.view.View): android.view.View? {
                if (view.contentDescription?.toString() == description) return view
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) {
                    find(view.getChildAt(i))?.let { return it }
                }
                return null
            }
            val rail = android.view.inspector.WindowInspector.getGlobalWindowViews().asReversed()
                .mapNotNull(::find).first { it.isShown }
            location = IntArray(2).also(rail::getLocationOnScreen)
        }
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val x = location!![0] + (16 + 4 * 26) * density
        val y = location!![1] + 20 * density
        drag(x, y, x - 26 * density, y)
    }

    private fun assertComposing() {
        host.onActivity { assertEquals(0, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
    }

    private fun segmentBar(): android.view.View? {
        var result: android.view.View? = null
        instrumentation.runOnMainSync {
            result = android.view.inspector.WindowInspector.getGlobalWindowViews()
                .mapNotNull { it.findViewById<android.view.View>(R.id.segment_candidate_bar) }
                .firstOrNull { it.isShown }
        }
        return result
    }
    private fun segmentBarVisible() = segmentBar() != null

    private fun assertBarWithinKeyboard(keyboardId: Int) {
        await("segment bar aligned with keyboard") {
            var aligned = false
            instrumentation.runOnMainSync {
                val windows = android.view.inspector.WindowInspector.getGlobalWindowViews()
                val bar = windows.mapNotNull { it.findViewById<android.view.View>(R.id.segment_candidate_bar) }
                    .firstOrNull { it.isShown }
                val keyboard = windows.mapNotNull { it.findViewById<android.view.View>(keyboardId) }
                    .firstOrNull { it.isShown }
                val barRect = android.graphics.Rect()
                val keyboardRect = android.graphics.Rect()
                if (bar != null && keyboard != null && bar.getGlobalVisibleRect(barRect) && keyboard.getGlobalVisibleRect(keyboardRect)) {
                    val tolerance = 2 * instrumentation.targetContext.resources.displayMetrics.density
                    aligned = barRect.left >= keyboardRect.left - tolerance && barRect.right <= keyboardRect.right + tolerance
                }
            }
            aligned
        }
    }

    private fun segmentLabels(): List<String> {
        val result = mutableListOf<String>()
        instrumentation.runOnMainSync {
            fun visit(view: android.view.View) {
                if (view is android.widget.TextView && view.isLongClickable) result.add(view.text.toString())
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            android.view.inspector.WindowInspector.getGlobalWindowViews()
                .mapNotNull { it.findViewById<android.view.View>(R.id.segment_candidate_bar) }
                .firstOrNull { it.isShown }?.let(::visit)
        }
        return result
    }

    private fun segmentReadings(): List<String> {
        val result = mutableListOf<String>()
        instrumentation.runOnMainSync {
            fun visit(view: android.view.View) {
                if (view is android.widget.TextView && view.isLongClickable) {
                    val description = view.contentDescription?.toString().orEmpty()
                    val match = Regex("読み「(.+?)」|reading (.+)").find(description)
                    match?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.let(result::add)
                }
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            android.view.inspector.WindowInspector.getGlobalWindowViews()
                .mapNotNull { it.findViewById<android.view.View>(R.id.segment_candidate_bar) }
                .firstOrNull { it.isShown }?.let(::visit)
        }
        return result
    }

    private fun rangeNode(start: Int, end: Int) =
        findDescription(instrumentation.targetContext.getString(R.string.segment_range_description, start, end))

    private fun allNodes(): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            nodes.add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        automation.windows.mapNotNull { it.root }.forEach(::visit)
        return nodes
    }

    private fun findDescription(value: String) = allNodes().firstOrNull { it.contentDescription?.toString() == value }
    private fun clickDescription(value: String) {
        await("content description [$value]") { findDescription(value) != null }
        assertTrue("Could not click $value", findDescription(value)!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun drag(x: Float, y: Float, endX: Float, endY: Float, holdMillis: Long = 0) {
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, atX: Float, atY: Float) {
            val motion = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, atX, atY, 0)
            motion.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(automation.injectInputEvent(motion, true))
            motion.recycle()
        }
        event(android.view.MotionEvent.ACTION_DOWN, x, y)
        if (holdMillis > 0) SystemClock.sleep(holdMillis)
        repeat(8) { step ->
            SystemClock.sleep(25)
            event(android.view.MotionEvent.ACTION_MOVE, x + (endX - x) * (step + 1) / 8, y + (endY - y) * (step + 1) / 8)
        }
        event(android.view.MotionEvent.ACTION_UP, endX, endY)
    }

    private fun tapAt(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(automation.injectInputEvent(event, true))
            event.recycle()
        }
    }

    private fun screenshot(name: String) {
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "segment-candidates-$name")
        val bitmap = automation.takeScreenshot() ?: error("No screenshot")
        java.io.FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    // IME windows can animate continuously. Wait for activity lifecycle instead of global idle.
    private inner class EditorHost : AutoCloseable {
        private val application = instrumentation.targetContext.applicationContext as android.app.Application
        private val current = java.util.concurrent.atomic.AtomicReference<FastInputHostActivity>()
        private val callbacks = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                if (activity is FastInputHostActivity) current.set(activity)
            }
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        }
        fun launch() {
            instrumentation.runOnMainSync { application.registerActivityLifecycleCallbacks(callbacks) }
            instrumentation.targetContext.startActivity(android.content.Intent(instrumentation.targetContext, FastInputHostActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            val deadline = SystemClock.uptimeMillis() + 30000
            while (current.get() == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            check(current.get() != null) { "Editor activity did not resume" }
        }
        fun onActivity(action: (FastInputHostActivity) -> Unit) {
            instrumentation.runOnMainSync { action(checkNotNull(current.get())) }
        }
        override fun close() {
            instrumentation.runOnMainSync {
                application.unregisterActivityLifecycleCallbacks(callbacks)
                current.get()?.finish()
            }
        }
    }

    private fun text(): String {
        var value = ""
        host.onActivity { value = it.editText.text.toString() }
        return value
    }

    private fun awaitText(expected: String) = await { text() == expected }

    private fun await(description: String = "", condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        screenshot("failure-${SystemClock.uptimeMillis()}.png")
        val labels = allNodes().map { listOfNotNull(it.text?.toString(), it.contentDescription?.toString()).joinToString(" / ") }.filter { it.isNotEmpty() }.distinct()
        fail("Timed out $description; editor=[${text()}]; visible labels=$labels")
    }

    companion object {
        private lateinit var originalIme: String
        private lateinit var targetIme: String
        private lateinit var originalShowWithHardware: String
        private var wasEnabled = false

        private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ).bufferedReader().use { it.readText().trim() }

        private fun sameIme(first: String, second: String): Boolean =
            ComponentName.unflattenFromString(first) == ComponentName.unflattenFromString(second)

        @JvmStatic @BeforeClass fun selectIme() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val component = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
            targetIme = shell("ime list -a -s").lines().first { sameIme(it, component) }
            originalIme = shell("settings get secure default_input_method")
            originalShowWithHardware = shell("settings get secure show_ime_with_hard_keyboard")
            wasEnabled = shell("ime list -s").lines().any { sameIme(it, targetIme) }
            shell("settings put secure show_ime_with_hard_keyboard 1")
            shell("ime enable $targetIme")
            shell("ime set $targetIme")
            assertTrue("Test IME was not selected", sameIme(shell("settings get secure default_input_method"), targetIme))
        }

        @JvmStatic @AfterClass fun restoreIme() {
            if (originalIme.isNotEmpty() && originalIme != "null") shell("ime set $originalIme")
            if (!wasEnabled && !sameIme(originalIme, targetIme)) shell("ime disable $targetIme")
            if (originalShowWithHardware == "null") {
                shell("settings delete secure show_ime_with_hard_keyboard")
            } else {
                shell("settings put secure show_ime_with_hard_keyboard $originalShowWithHardware")
            }
        }
    }

    private fun withKeyboard(
        floating: Boolean = false, live: Boolean = false, show: Boolean = true, sumire: Boolean = false,
        landscape: Boolean = false, side: String = "LEFT", wrapLongText: Boolean = false, block: () -> Unit,
    ) {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val context = instrumentation.targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val geometry = if (sumire) "sumire" else "tenkey"
        val orientation = if (landscape) "landscape" else "portrait"
        val values = mapOf<String, Any?>(
            "conversion_bunsetsu_separation_preference" to false,
            "conversion_bunsetsu_cursor_move_preference" to false,
            "candidate_order_override_enable_preference" to false,
            "keyboard_order_preference" to if (sumire) "[\"SUMIRE\"]" else "[\"TENKEY\"]",
            "keyboard_geometry_${geometry}_${orientation}_candidates" to side,
            "keyboard_geometry_${geometry}_${orientation}_candidate_vertical_text" to true,
            "keyboard_geometry_${geometry}_${orientation}_candidate_wrap_long_text" to wrapLongText,
            "keyboard_geometry_${geometry}_${orientation}_candidate_spacing_dp" to 4,
            "keyboard_geometry_${geometry}_${orientation}_segment_editor" to show,
            "keyboard_floating_preference" to floating,
            "allow_fullscreen_mode_preference" to false,
            "keyboard_skin_id" to "default",
            "composing_guide_enabled" to false,
            "composing_guide_text_enabled" to false,
            "learn_dictionary_preference" to false,
            "physical_keyboard_input_mode_preference" to "romaji",
            "physical_toolbar_style" to "floating",
            "save_last_used_keyboard" to false,
            "live_conversion_preference" to live,
            "flick_input_only_preference" to true,
            "zero_query_suggestion_preference" to false,
        )
        val previous = values.keys.associateWith { prefs.all[it] }
        try {
            prefs.edit().apply {
                values.forEach { (key, value) ->
                    when (value) { is Boolean -> putBoolean(key, value); is String -> putString(key, value); is Int -> putInt(key, value); null -> remove(key) }
                }
            }.commit()
            host = EditorHost()
            host.launch()
            host.onActivity {
                it.requestedOrientation = if (landscape) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            await("screen orientation") {
                var actual = 0
                host.onActivity { actual = it.resources.configuration.orientation }
                actual == if (landscape) android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    else android.content.res.Configuration.ORIENTATION_PORTRAIT
            }
            host.onActivity {
                it.editText.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
                it.restartEditorInput(true)
            }
            host.onActivity { it.requestImeForEditor() }
            await("software keyboard") {
                allNodes().any { it.contentDescription?.toString() == "delete_key" && it.isVisibleToUser } ||
                    allNodes().any { it.text?.toString() == "あ" && it.isVisibleToUser }
            }
            block()
        } finally {
            if (::host.isInitialized) host.close()
            prefs.edit().apply {
                previous.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                        is Float -> putFloat(key, value)
                        is Int -> putInt(key, value)
                        null -> remove(key)
                    }
                }
            }.commit()
        }
    }
}
