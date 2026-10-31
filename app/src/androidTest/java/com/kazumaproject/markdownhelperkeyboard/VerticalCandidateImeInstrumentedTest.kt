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
class VerticalCandidateImeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private lateinit var host: EditorHost

    @Test fun horizontalCandidatesScrollInBothDirectionsOnTheLeft() =
        verifyHorizontalSideCandidates(floating = false, side = "LEFT")

    @Test fun horizontalCandidatesScrollInBothDirectionsOnTheRight() =
        verifyHorizontalSideCandidates(floating = false, side = "RIGHT")

    @Test fun floatingHorizontalCandidatesScrollInBothDirections() =
        verifyHorizontalSideCandidates(floating = true, side = "LEFT")

    @Test fun sumireHorizontalCandidatesScrollInBothDirections() =
        verifyHorizontalSideCandidates(floating = false, side = "RIGHT", sumire = true)

    @Test fun landscapeHorizontalCandidatesScrollInBothDirections() =
        verifyHorizontalSideCandidates(floating = false, side = "RIGHT", landscape = true)

    @Test fun qwertyCenterCandidatesScrollInBothDirections() =
        verifyHorizontalSideCandidates(floating = false, side = "CENTER", qwerty = true)

    @Test fun floatingQwertyCenterCandidatesScrollInBothDirections() =
        verifyHorizontalSideCandidates(floating = true, side = "CENTER", qwerty = true)

    private fun verifyHorizontalSideCandidates(
        floating: Boolean, side: String, sumire: Boolean = false, landscape: Boolean = false, qwerty: Boolean = false,
    ) = withKeyboard(floating = floating, side = side, sumire = sumire, landscape = landscape,
        verticalText = false, qwerty = qwerty) {
            if (qwerty) softKey(com.kazumaproject.qwerty_keyboard.R.id.key_h)
            else if (sumire) tapSumireA() else softKey(com.kazumaproject.tenkey.R.id.key_1)
            fun candidates(): androidx.recyclerview.widget.RecyclerView? =
                android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .mapNotNull { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestion_recycler_view) }
                    .firstOrNull { it.isShown && it.parent is android.widget.HorizontalScrollView }

            await("horizontal side candidates") {
                var ready = false
                instrumentation.runOnMainSync { ready = candidates()?.childCount?.let { it > 0 } == true }
                ready
            }
            val values = List(40) { index ->
                val word = when {
                    index == 25 -> "特に長い変換候補".repeat(30)
                    qwerty -> listOf("横にスワイプすると候補の続きを確認できます",
                        "長い変換候補の末尾も表示できます",
                        "候補を選ぶと全文が入力されます",
                        "縦方向にも候補をスクロールできます")[index % 4]
                    else -> "長い変換候補".repeat(12)
                }
                com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate("$index$word", 1, 1u, 0)
            }
            instrumentation.runOnMainSync {
                (candidates()!!.adapter as com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter)
                    .suggestions = values
            }
            await("long candidate") {
                var ready = false
                instrumentation.runOnMainSync {
                    val list = candidates()
                    val text = list?.findViewHolderForAdapterPosition(0)?.itemView
                        ?.findViewById<android.widget.TextView>(R.id.suggestion_item_text_view)
                    ready = text?.text?.toString()?.contains(values[0].string) == true &&
                        (list?.parent as? android.widget.HorizontalScrollView)?.canScrollHorizontally(1) == true
                }
                ready
            }
            if (qwerty) {
                fun rowGap(): Int? {
                    var gap: Int? = null
                    instrumentation.runOnMainSync {
                        val list = candidates()
                        val first = list?.findViewHolderForAdapterPosition(0)?.itemView
                        val second = list?.findViewHolderForAdapterPosition(1)?.itemView
                        if (first != null && second != null) gap = second.top - first.bottom
                    }
                    return gap
                }
                var initialGap = 0
                await("center candidate rows") { rowGap()?.also { initialGap = it } != null }
                val orientation = if (landscape) "landscape" else "portrait"
                check(PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext).edit()
                    .putInt("keyboard_geometry_qwerty_${orientation}_center_candidate_spacing_dp", 20).commit())
                await("center row spacing") { rowGap()?.let { it > initialGap } == true }
            }
            val panel = android.graphics.Rect()
            instrumentation.runOnMainSync {
                val scroll = candidates()!!.parent as android.widget.HorizontalScrollView
                val location = IntArray(2).also(scroll::getLocationOnScreen)
                panel.set(location[0], location[1], location[0] + scroll.width, location[1] + scroll.height)
            }
            drag(panel.right - 8f, panel.centerY().toFloat(), panel.left + 8f, panel.centerY().toFloat())
            await("horizontal swipe") {
                var moved = false
                instrumentation.runOnMainSync { moved = (candidates()!!.parent as android.widget.HorizontalScrollView).scrollX > 0 }
                moved
            }
            if (qwerty && !floating) screenshot("qwerty-center.png")
            // Let the horizontal fling settle before starting a gesture on the other axis.
            SystemClock.sleep(1000)
            instrumentation.runOnMainSync {
                val scroll = candidates()!!.parent as android.widget.HorizontalScrollView
                val location = IntArray(2).also(scroll::getLocationOnScreen)
                panel.set(location[0], location[1], location[0] + scroll.width, location[1] + scroll.height)
            }
            drag(panel.centerX().toFloat(), panel.bottom - 8f, panel.centerX().toFloat(), panel.top + 8f)
            await("vertical swipe") {
                var moved = false
                instrumentation.runOnMainSync {
                    moved = (candidates()!!.layoutManager as androidx.recyclerview.widget.LinearLayoutManager)
                        .findFirstVisibleItemPosition() > 0
                }
                moved
            }
            instrumentation.runOnMainSync { candidates()!!.scrollToPosition(25) }
            await("later long candidate") {
                var ready = false
                instrumentation.runOnMainSync {
                    val text = candidates()?.findViewHolderForAdapterPosition(25)?.itemView
                        ?.findViewById<android.widget.TextView>(R.id.suggestion_item_text_view)
                    ready = text?.text?.toString()?.contains(values[25].string) == true
                }
                ready
            }
            val visible = android.graphics.Rect()
            instrumentation.runOnMainSync {
                val list = candidates()!!
                val scroll = list.parent as android.widget.HorizontalScrollView
                scroll.scrollTo(list.width, 0)
                val item = list.findViewHolderForAdapterPosition(25)!!.itemView
                val text = item.findViewById<android.widget.TextView>(R.id.suggestion_item_text_view)
                assertEquals("The later candidate must not be truncated", text.length(), text.layout.getLineEnd(0))
                assertTrue("The entire later candidate must fit in its text view",
                    text.width >= text.layout.getLineWidth(0))
                val location = IntArray(2).also(text::getLocationOnScreen)
                assertTrue("The end of the later candidate must be visible",
                    text.getLocalVisibleRect(visible))
                visible.offset(location[0], location[1])
                assertTrue("The end of the later candidate is clipped",
                    visible.right >= location[0] + text.width - 2)
            }
            tapAt(visible.centerX().toFloat(), visible.centerY().toFloat())
            awaitText(values[25].string)
            host.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
        }

    @Test fun regularVerticalCandidatesRespondToSpacingAndShowDividers() =
        verifyRegularVerticalCandidates(side = "LEFT", rightToLeft = false)

    @Test fun rightToLeftVerticalCandidatesRespondToSpacingAndShowDividers() =
        verifyRegularVerticalCandidates(side = "RIGHT", rightToLeft = true)

    private fun verifyRegularVerticalCandidates(side: String, rightToLeft: Boolean) =
        withKeyboard(side = side, sideWidthPercent = 50, rightToLeft = rightToLeft) {
            softKey(com.kazumaproject.tenkey.R.id.key_1)
            fun candidates(): androidx.recyclerview.widget.RecyclerView? =
                android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .mapNotNull { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestion_recycler_view) }
                    .firstOrNull { it.isShown && it.layoutManager is
                        com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.SideCandidateFlowLayoutManager }
            await("vertical candidates") {
                var ready = false
                instrumentation.runOnMainSync { ready = candidates()?.childCount?.let { it > 0 } == true }
                ready
            }
            val words = listOf("明日", "明後日", "朝", "晴れ", "今日", "予定", "天気", "気温", "東京", "散歩", "公園", "旅行")
            instrumentation.runOnMainSync {
                val adapter = candidates()!!.adapter as com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
                adapter.suggestions = words.map { com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(it, 1, 1u, 0) }
            }
            val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
            val key = "keyboard_geometry_tenkey_portrait_candidate_spacing_dp"
            fun gaps(): Pair<Int, Int>? {
                var result: Pair<Int, Int>? = null
                instrumentation.runOnMainSync {
                    val list = candidates() ?: return@runOnMainSync
                    val flow = list.layoutManager as com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.SideCandidateFlowLayoutManager
                    val first = flow.findViewByPosition(0)
                    val below = flow.findViewByPosition(1)
                    val nextColumn = flow.flexLines.firstOrNull()?.itemCount?.let(flow::findViewByPosition)
                    if (first != null && below != null && nextColumn != null &&
                        first.left == below.left && nextColumn.left != first.left &&
                        first.findViewById<android.widget.TextView>(R.id.suggestion_item_text_view)?.text.toString().contains("明")) {
                        result = kotlin.math.abs(nextColumn.left - first.left) to (below.top - first.bottom)
                    }
                }
                return result
            }
            check(prefs.edit().putInt(key, 0).commit())
            var compact: Pair<Int, Int>? = null
            await("compact vertical candidates") { gaps()?.also { compact = it } != null }
            if (!rightToLeft) screenshot("vertical-compact.png")
            check(prefs.edit().putInt(key, 24).commit())
            var observed: Pair<Int, Int>? = null
            await("normal candidates use the new spacing") {
                val expanded = gaps()
                if (observed != expanded) {
                    android.util.Log.i("CandidateSpacingTest", "side=$side compact=$compact expanded=$expanded")
                    observed = expanded
                }
                expanded != null && expanded.first > compact!!.first && expanded.second > compact!!.second
            }
            var lineY = 0
            var backgroundY = 0
            var textCenter = 0f
            var scanLeft = 0
            var scanRight = 0
            var density = 1f
            instrumentation.runOnMainSync {
                val list = candidates()!!
                val first = list.findViewHolderForAdapterPosition(0)!!.itemView
                val origin = IntArray(2).also(list::getLocationOnScreen)
                val text = first.findViewById<android.view.View>(R.id.suggestion_item_text_view)
                val textOrigin = IntArray(2).also(text::getLocationOnScreen)
                density = list.resources.displayMetrics.density
                textCenter = textOrigin[0] + text.width / 2f
                lineY = origin[1] + first.bottom + (24 * density / 2).toInt()
                backgroundY = lineY + (3 * density).toInt()
                scanLeft = origin[0] + first.left
                scanRight = origin[0] + first.right - 1
            }
            val bitmap = requireNotNull(automation.takeScreenshot())
            val linePixels = (scanLeft.coerceAtLeast(0)..scanRight.coerceAtMost(bitmap.width - 1)).filter { x ->
                bitmap.getPixel(x, lineY) != bitmap.getPixel(x, backgroundY)
            }
            assertTrue("A short horizontal divider is drawn below an ordinary vertical candidate", linePixels.isNotEmpty())
            val lineWidth = linePixels.last() - linePixels.first() + 1
            assertTrue("The divider should be short, but was $lineWidth pixels", lineWidth <= 20 * density)
            assertTrue("The divider should be visible", lineWidth >= 15 * density)
            assertTrue("The divider should be centered under the text",
                kotlin.math.abs((linePixels.first() + linePixels.last()) / 2f - textCenter) <= 2f)
            bitmap.recycle()
            screenshot(if (rightToLeft) "vertical-candidates-rtl.png" else "vertical-candidates.png")
        }

    @Test fun wrappedLongCandidateScrollsAndCommitsItsFullText() = withKeyboard(wrapLongText = true) {
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
                val key = android.view.inspector.WindowInspector.getGlobalWindowViews().asReversed()
                    .mapNotNull { it.findViewById<android.view.View>(R.id.custom_layout_default) }
                    .filter { it.isShown }.mapNotNull(::find).firstOrNull()
                bounds = key?.let {
                    val location = IntArray(2).also(it::getLocationOnScreen)
                    android.graphics.Rect(location[0], location[1], location[0] + it.width, location[1] + it.height)
                }
            }
            bounds != null && !bounds!!.isEmpty
        }
        tapAt(bounds!!.centerX().toFloat(), bounds!!.centerY().toFloat())
    }

    private fun allNodes(): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            nodes.add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        automation.windows.mapNotNull { it.root }.forEach(::visit)
        return nodes
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
        floating: Boolean = false, live: Boolean = false, sumire: Boolean = false, qwerty: Boolean = false,
        landscape: Boolean = false, side: String = "LEFT", wrapLongText: Boolean = false,
        verticalText: Boolean = true, sideWidthPercent: Int = 25, rightToLeft: Boolean = false,
        block: () -> Unit,
    ) {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val context = instrumentation.targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val geometry = if (qwerty) "qwerty" else if (sumire) "sumire" else "tenkey"
        val orientation = if (landscape) "landscape" else "portrait"
        val values = mapOf<String, Any?>(
            "conversion_bunsetsu_separation_preference" to false,
            "conversion_bunsetsu_cursor_move_preference" to false,
            "candidate_order_override_enable_preference" to false,
            "keyboard_order_preference" to if (qwerty) "[\"QWERTY\"]" else if (sumire) "[\"SUMIRE\"]" else "[\"TENKEY\"]",
            "keyboard_geometry_${geometry}_${orientation}_candidates" to side,
            "keyboard_geometry_${geometry}_${orientation}_split" to if (qwerty) 40 else 0,
            "keyboard_geometry_${geometry}_${orientation}_candidate_width_percent" to sideWidthPercent,
            "keyboard_geometry_${geometry}_${orientation}_center_candidate_spacing_dp" to 0,
            "keyboard_geometry_${geometry}_${orientation}_candidate_vertical_text" to verticalText,
            "keyboard_geometry_${geometry}_${orientation}_candidate_right_to_left" to rightToLeft,
            "keyboard_geometry_${geometry}_${orientation}_candidate_wrap_long_text" to wrapLongText,
            "keyboard_geometry_${geometry}_${orientation}_candidate_spacing_dp" to 4,
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
                var qwertyShown = false
                if (qwerty) instrumentation.runOnMainSync {
                    qwertyShown = android.view.inspector.WindowInspector.getGlobalWindowViews()
                        .any { it.findViewById<android.view.View>(if (floating) R.id.qwerty_view_floating else R.id.qwerty_view)?.isShown == true }
                }
                qwertyShown || allNodes().any { it.contentDescription?.toString() == "delete_key" && it.isVisibleToUser } ||
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
