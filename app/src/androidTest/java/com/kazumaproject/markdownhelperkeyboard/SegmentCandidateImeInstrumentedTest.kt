package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.view.accessibility.AccessibilityNodeInfo
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
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
class SegmentCandidateImeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private lateinit var host: EditorHost
    private var keyboardId = 0

    @Test fun treeSelectionOnlyReplacesTargetAndConfirmCommitsWholeSentence() = withKeyboard {
        startConversion()
        clickDescription(instrumentation.targetContext.getString(R.string.segment_select_description, "東京に", "とうきょうに"))
        await { findDescription(instrumentation.targetContext.getString(R.string.segment_range_description, 4, 10)) != null }
        screenshot("segments.png")
        await { treeBounds() != null }
        screenshot("tree.png")
        val alternative = allNodes().firstOrNull { node ->
            val label = node.text?.toString().orEmpty()
            node.isClickable && node.contentDescription?.toString() == instrumentation.targetContext.getString(
                R.string.segment_replace_description, label, "とうきょうに")
        } ?: error("No alternative for the focused reading in the tree")
        val replacement = alternative.text.toString().substringBefore('\n')
        assertTrue(alternative.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        awaitText("明日は" + replacement + "行きます")
        assertComposing()
        clickDescription(instrumentation.targetContext.getString(R.string.segment_tree_close))
        key(KeyEvent.KEYCODE_ENTER)
        awaitText("明日は" + replacement + "行きます")
        host.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
    }

    @Test fun tappingSegmentsAndSelectedTreeNodesChangesFocusWithoutReplacingText() = withKeyboard {
        startConversion()
        clickDescription(instrumentation.targetContext.getString(R.string.segment_select_description, "行きます", "いきます"))
        await { findDescription(instrumentation.targetContext.getString(R.string.segment_range_description, 10, 14)) != null }
        awaitText("明日は東京に行きます")
        await { treeBounds() != null }
        // Tapping a chosen tree node focuses it without replacing its conversion state.
        await { allNodes().any { it.text?.toString() == "明日は\nあしたは" && it.isClickable } }
        val first = allNodes().first { it.text?.toString() == "明日は\nあしたは" && it.isClickable }
        assertTrue(first.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        await { findDescription(instrumentation.targetContext.getString(R.string.segment_range_description, 0, 4)) != null }
        await { allNodes().any { it.text?.toString() == "明日は\nあしたは" && it.isSelected } }
        awaitText("明日は東京に行きます")
        assertComposing()
        val middle = allNodes().first { it.text?.toString() == "東京に" && it.isClickable }
        assertTrue(middle.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        await { findDescription(instrumentation.targetContext.getString(R.string.segment_range_description, 4, 10)) != null }
        await { allNodes().any { it.text?.toString() == "東京に\nとうきょうに" && it.isSelected } }
        awaitText("明日は東京に行きます")
        assertComposing()
        screenshot("focus.png")
    }

    @Test fun segmentReadingStripScrollsHorizontallyWithoutChangingComposition() = withKeyboard {
        type("ashitahatoukyouniikimasusoshite")
        awaitText("あしたはとうきょうにいきますそして")
        key(KeyEvent.KEYCODE_SPACE)
        showSoftwareKeyboard()
        await { scrollState(R.id.segment_candidate_scroll)?.let { it.second.width() > 0 } == true }
        val before = scrollState(R.id.segment_candidate_scroll)!!
        val area = before.second
        val density = instrumentation.targetContext.resources.displayMetrics.density
        // Swipe over the segment labels rather than the boundary drag handles.
        drag(area.right - 24 * density, area.top + 16 * density,
            area.left + 24 * density, area.top + 16 * density)
        await { scrollState(R.id.segment_candidate_scroll)!!.first > before.first + 20 * density }
        assertComposing()
        val scrolled = scrollState(R.id.segment_candidate_scroll)!!.first
        SystemClock.sleep(500)
        assertEquals(scrolled, scrollState(R.id.segment_candidate_scroll)!!.first)
        drag(area.left + 24 * density, area.top + 16 * density,
            area.right - 24 * density, area.top + 16 * density)
        await { scrollState(R.id.segment_candidate_scroll)!!.first < scrolled }
        assertComposing()
    }

    private fun scrollState(id: Int): Pair<Int, android.graphics.Rect>? {
        var result: Pair<Int, android.graphics.Rect>? = null
        instrumentation.runOnMainSync {
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEach { root ->
                root.findViewById<android.widget.HorizontalScrollView>(id)?.takeIf { it.isShown }?.let { view ->
                    val location = IntArray(2).also(view::getLocationOnScreen)
                    val visible = android.graphics.Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
                    // The horizontal view can be taller than the vertical viewport. Inject
                    // swipes inside the visible viewport, not above a scrolled graph.
                    (view.parent as? android.view.View)?.let { parent ->
                        val parentLocation = IntArray(2).also(parent::getLocationOnScreen)
                        visible.intersect(android.graphics.Rect(parentLocation[0], parentLocation[1],
                            parentLocation[0] + parent.width, parentLocation[1] + parent.height))
                    }
                    result = view.scrollX to visible
                }
            }
        }
        return result
    }

    @Test fun draggingSegmentReordersWholeStatesAndConfirmCommitsTheNewOrder() = withKeyboard {
        startConversion()
        dragMiddleSegmentBeforeFirst()
        awaitText("東京に明日は行きます")
        await { findDescription(instrumentation.targetContext.getString(R.string.segment_range_description, 0, 6)) != null }
        SystemClock.sleep(500)
        assertEquals("東京に明日は行きます", text())
        assertComposing()
        screenshot("reorder.png")
        key(KeyEvent.KEYCODE_ENTER)
        awaitText("東京に明日は行きます")
        host.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
    }

    @Test fun reorderedSegmentStillHasWorkingRangeHandlesAndCancelsToReorderedReading() = withKeyboard {
        startConversion()
        await { treeBounds() != null }
        dragMiddleSegmentBeforeFirst()
        awaitText("東京に明日は行きます")
        val description = instrumentation.targetContext.getString(R.string.segment_range_description, 0, 6)
        await { findDescription(description) != null }
        val area = android.graphics.Rect().also(findDescription(description)!!::getBoundsInScreen)
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val x = area.left + 172 * density
        drag(x, area.centerY().toFloat(), x - 26 * density, area.centerY().toFloat())
        await { findDescription(instrumentation.targetContext.getString(R.string.segment_range_description, 0, 5)) != null }
        assertComposing()
        screenshot("reorder-range.png")
        key(KeyEvent.KEYCODE_ESCAPE)
        awaitText("とうきょうにあしたはいきます")
        type("a")
        awaitText("とうきょうにあしたはいきますあ")
    }

    private fun dragMiddleSegmentBeforeFirst() {
        val source = android.graphics.Rect().also(allNodes().first { it.text?.toString() == "東京に" && it.isLongClickable }::getBoundsInScreen)
        val target = android.graphics.Rect().also(allNodes().first { it.text?.toString() == "明日は" && it.isLongClickable }::getBoundsInScreen)
        drag(source.centerX().toFloat(), source.centerY().toFloat(), target.centerX().toFloat(), target.centerY().toFloat(),
            holdMillis = android.view.ViewConfiguration.getLongPressTimeout().toLong() + 200)
    }

    @Test fun draggingBoundaryRequeriesRangeWithoutLosingReadingOrCommitting() = withKeyboard {
        startConversion()
        clickDescription(instrumentation.targetContext.getString(R.string.segment_select_description, "東京に", "とうきょうに"))
        val description = instrumentation.targetContext.getString(R.string.segment_range_description, 4, 10)
        await { findDescription(description) != null }
        val bounds = android.graphics.Rect().also(findDescription(description)!!::getBoundsInScreen)
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val x = bounds.left + 276 * density
        val y = bounds.centerY().toFloat()
        drag(x, y, x - 26 * density, y)
        await { findDescription(instrumentation.targetContext.getString(R.string.segment_range_description, 4, 9)) != null }
        assertComposing()
        screenshot("boundary.png")
        key(KeyEvent.KEYCODE_ESCAPE)
        awaitText("あしたはとうきょうにいきます")
    }

    @Test fun candidateTapBeforeConversionDoesNotCommitAndLegacySettingsStayDisabled() = withKeyboard {
        type("ashitahatoukyouniikimasu")
        awaitText("あしたはとうきょうにいきます")
        showSoftwareKeyboard()
        fun isWholeCandidate(node: AccessibilityNodeInfo): Boolean {
            val label = node.text?.toString().orEmpty()
            return "明日" in label && "東京" in label && "行き" in label
        }
        await { allNodes().any(::isWholeCandidate) }
        var candidate: AccessibilityNodeInfo? = allNodes().first(::isWholeCandidate)
        while (candidate != null && !candidate.isClickable) candidate = candidate.parent
        assertTrue(candidate?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        awaitText("明日は東京に行きます")
        assertComposing()
        val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        assertFalse(prefs.getBoolean("conversion_bunsetsu_separation_preference", true))
        assertFalse(prefs.getBoolean("conversion_bunsetsu_cursor_move_preference", true))
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun floatingKeyboardAnchorsTreeAboveItsOwnCandidateBar() = withKeyboard(floating = true) {
        startConversion()
        // Hardware typing deliberately leaves floating mode. Restore the real popup for this scenario.
        instrumentation.runOnMainSync {
            val bar = android.view.inspector.WindowInspector.getGlobalWindowViews()
                .firstNotNullOf { it.findViewById<android.view.View>(R.id.segment_candidate_bar) }
            var context: android.content.Context = bar.context
            while (context is android.content.ContextWrapper && context !is com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService) {
                context = context.baseContext
            }
            check(context is com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService)
            context.javaClass.getDeclaredMethod("applyFloatingModeState", Boolean::class.javaPrimitiveType).apply {
                isAccessible = true
            }.invoke(context, true)
        }
        await {
            var shown = false
            instrumentation.runOnMainSync {
                shown = android.view.inspector.WindowInspector.getGlobalWindowViews().any { root ->
                    root.findViewById<android.view.View>(R.id.floating_keyboard_container)?.isShown == true &&
                        root.findViewById<android.view.View>(R.id.segment_candidate_bar)?.isShown == true
                }
            }
            shown
        }
        await { treeBounds() != null }
        assertComposing()
        screenshot("floating.png")
        clickDescription(instrumentation.targetContext.getString(R.string.segment_tree_close))
        assertComposing()
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun treeVisibilitySettingAndResizePersistWithoutCommitting() = withKeyboard {
        startConversion()
        await { treeBounds() != null }
        assertFalse(allNodes().any { it.text?.toString()?.contains("Full-sentence candidate tree") == true })
        clickDescription(instrumentation.targetContext.getString(R.string.composing_guide_edit))
        await { findDescription(instrumentation.targetContext.getString(R.string.composing_guide_done)) != null }
        val before = treeBounds()!!
        val density = instrumentation.targetContext.resources.displayMetrics.density
        // The shared frame exposes its left edge in editing mode.
        drag(before.left + 12 * density, before.centerY().toFloat() - 9 * density,
            before.left + 60 * density, before.centerY().toFloat() - 9 * density)
        await { treeBounds()!!.width() < before.width() - 24 * density }
        screenshot("tree-edit.png")
        clickDescription(instrumentation.targetContext.getString(R.string.composing_guide_done))
        val resized = treeBounds()!!
        val treeScroll = scrollState(R.id.segment_tree_scroll)!!
        val treeArea = treeScroll.second
        drag(treeArea.right - 16 * density, treeArea.top + 16 * density,
            treeArea.left + 16 * density, treeArea.top + 16 * density)
        await { scrollState(R.id.segment_tree_scroll)!!.first > treeScroll.first }
        assertFalse(allNodes().any { it.text?.toString() == "Close\ntree" })
        clickDescription(instrumentation.targetContext.getString(R.string.segment_tree_close))
        await { treeBounds() == null }
        SystemClock.sleep(500)
        assertNull(treeBounds())
        clickDescription(instrumentation.targetContext.getString(R.string.segment_select_description, "明日は", "あしたは"))
        await { treeBounds()?.width() == resized.width() }
        val positioned = treeBounds()!!
        drag(positioned.centerX().toFloat(), positioned.bottom - 12 * density,
            positioned.centerX() - 24 * density, positioned.bottom - 36 * density)
        await { treeBounds()!!.left < positioned.left - 12 * density }
        val moved = treeBounds()!!
        clickDescription(instrumentation.targetContext.getString(R.string.segment_tree_close))
        await { treeBounds() == null }
        clickDescription(instrumentation.targetContext.getString(R.string.segment_select_description, "明日は", "あしたは"))
        await { treeBounds() == moved }
        assertComposing()
        val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        prefs.edit().putBoolean("segment_tree_enabled", false).commit()
        await { treeBounds() == null }
        clickDescription(instrumentation.targetContext.getString(R.string.segment_select_description, "明日は", "あしたは"))
        SystemClock.sleep(500)
        assertNull(treeBounds())
        assertComposing()
        prefs.edit().putBoolean("segment_tree_enabled", true).commit()
        await { treeBounds() != null }
        assertComposing()
    }

    @Test fun treeStaysHiddenWhenDisabledAndAutomaticallyReturnsForANewConversion() = withKeyboard {
        val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        prefs.edit().putBoolean("segment_tree_enabled", false).commit()
        type("ashitahatoukyouniikimasu")
        awaitText("あしたはとうきょうにいきます")
        showSoftwareKeyboard()
        assertNull(treeBounds()) // Reading preview does not open the tree.
        key(KeyEvent.KEYCODE_SPACE)
        awaitText("明日は東京に行きます")
        showSoftwareKeyboard()
        assertNull(treeBounds())
        prefs.edit().putBoolean("segment_tree_enabled", true).commit()
        await { treeBounds() != null }
        clickDescription(instrumentation.targetContext.getString(R.string.segment_tree_close))
        await { treeBounds() == null }
        SystemClock.sleep(500)
        assertNull(treeBounds())
        key(KeyEvent.KEYCODE_ESCAPE)
        awaitText("あしたはとうきょうにいきます")
        key(KeyEvent.KEYCODE_SPACE)
        awaitText("明日は東京に行きます")
        showSoftwareKeyboard()
        await { treeBounds() != null }
        assertComposing()
    }

    @Test fun floatingWindowSurvivesTypingCandidateRefreshAndDeletionUntilCompositionIsEmpty() = withKeyboard {
        startConversion()
        val panel = treePanel()!!
        val detached = java.util.concurrent.atomic.AtomicInteger()
        instrumentation.runOnMainSync {
            panel.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: android.view.View) = Unit
                override fun onViewDetachedFromWindow(view: android.view.View) { detached.incrementAndGet() }
            })
        }
        key(KeyEvent.KEYCODE_ESCAPE)
        awaitText("あしたはとうきょうにいきます")
        assertSame(panel, treePanel())
        type("soshite")
        awaitText("あしたはとうきょうにいきますそして")
        showSoftwareKeyboard()
        assertSame(panel, treePanel())
        assertEquals(0, detached.get())
        screenshot("continuous.png")
        val reading = text()
        for (remaining in reading.lastIndex downTo 0) {
            key(KeyEvent.KEYCODE_DEL)
            awaitText(reading.take(remaining))
            if (remaining > 0) {
                assertSame(panel, treePanel())
                assertEquals(0, detached.get())
            }
        }
        await { treeBounds() == null }
        assertEquals(1, detached.get())
    }

    @Test fun perSegmentBranchesCanReplaceCandidatesAndSwitchLayoutWithoutClosingThePanel() = withKeyboard {
        startConversion()
        val panel = treePanel()!!
        instrumentation.runOnMainSync {
            val labels = mutableListOf<android.widget.TextView>()
            fun collect(view: android.view.View) {
                if (view is android.widget.TextView && view.isClickable && view.contentDescription != null) labels.add(view)
                if (view is android.view.ViewGroup) for (index in 0 until view.childCount) collect(view.getChildAt(index))
            }
            collect(panel)
            val primary = labels.filter { it.isActivated }
            val alternatives = labels.filter { !it.isActivated && it.contentDescription.toString() ==
                instrumentation.targetContext.getString(R.string.segment_replace_description, it.text.toString(), "あしたは") }
            assertTrue(primary.isNotEmpty() && alternatives.isNotEmpty())
            assertTrue(primary.minOf { it.textSize } > alternatives.maxOf { it.textSize })
            assertTrue(primary.minOf { it.height } > alternatives.maxOf { it.height })
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        prefs.edit().putString("segment_tree_style", "branches").commit()
        await { branchViewVisible() }
        assertSame(panel, treePanel())
        val alternative = allNodes().first { node ->
            val value = node.text?.toString().orEmpty()
            value.endsWith("\nあしたは") && value != "明日は\nあしたは" && node.isClickable
        }
        val replacement = alternative.text.toString().substringBefore('\n')
        assertTrue(alternative.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        awaitText(replacement + "東京に行きます")
        assertComposing()
        screenshot("branches.png")
        prefs.edit().putString("segment_tree_style", "sentence").commit()
        await { !branchViewVisible() }
        assertSame(panel, treePanel())
        awaitText(replacement + "東京に行きます")
        key(KeyEvent.KEYCODE_ENTER)
        await { treeBounds() == null }
        host.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
    }

    @Test fun graphIncludesAdditionalEngineSegmentationAndTappingItsBranchChangesTheRange() = withKeyboard {
        startConversion()
        val panel = treePanel()!!
        var snapshot: Any? = null
        instrumentation.runOnMainSync {
            var context: android.content.Context = panel.context
            while (context is android.content.ContextWrapper && context !is com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService) {
                context = context.baseContext
            }
            snapshot = context.javaClass.getDeclaredField("segmentGraphSource").apply { isAccessible = true }.get(context)
        }
        val source = checkNotNull(snapshot)
        @Suppress("UNCHECKED_CAST")
        val patterns = source.javaClass.getMethod("getSplitPatterns").invoke(source) as List<List<Int>>
        @Suppress("UNCHECKED_CAST")
        val initial = source.javaClass.getMethod("getInitialSplitPositions").invoke(source) as List<Int>
        val reading = source.javaClass.getMethod("getInput").invoke(source) as String
        val primary = (listOf(0) + initial + reading.length).zipWithNext().toSet()
        val different = patterns.flatMap { (listOf(0) + it + reading.length).zipWithNext() }
            .filter { it !in primary }.minByOrNull { it.first }
            ?: error("Fixture produced no alternate engine segmentation: $patterns")
        val segmentReading = reading.substring(different.first, different.second)
        await { allNodes().any { it.isClickable && it.text?.toString()?.endsWith("\n$segmentReading") == true } }
        screenshot("segmentation-branches.png")
        val branch = allNodes().first { it.isClickable && it.text?.toString()?.endsWith("\n$segmentReading") == true }
        assertTrue(branch.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        await { findDescription(instrumentation.targetContext.getString(R.string.segment_range_description,
            different.first, different.second)) != null }
        assertComposing()
    }

    @Test fun editingSliderScalesTheGraphAndScaledCandidatesStillAcceptNativeTaps() = withKeyboard {
        startConversion()
        val panel = treePanel()!!
        fun graphWidth(): Int {
            var width = 0
            instrumentation.runOnMainSync { width = panel.findViewById<android.view.View>(R.id.segment_tree_zoom)?.width ?: 0 }
            return width
        }
        await { graphWidth() > 0 }
        val before = graphWidth()
        clickDescription(instrumentation.targetContext.getString(R.string.composing_guide_edit))
        val title = instrumentation.targetContext.getString(R.string.segment_tree_scale_title)
        await { findDescription(title) != null }
        assertTrue(findDescription(title)!!.performAction(
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
            android.os.Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 0f) }))
        val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        await { prefs.getInt("segment_tree_scale", 85) == 50 && graphWidth() < before * .7f }
        assertSame(panel, treePanel())
        screenshot("zoom.png")
        clickDescription(instrumentation.targetContext.getString(R.string.composing_guide_done))
        val scaledWidth = graphWidth()
        clickDescription(instrumentation.targetContext.getString(R.string.segment_tree_close))
        await { treeBounds() == null }
        clickDescription(instrumentation.targetContext.getString(R.string.segment_select_description, "明日は", "あしたは"))
        await { treeBounds() != null }
        val reopened = treePanel()!!
        await {
            var width = 0
            instrumentation.runOnMainSync { width = reopened.findViewById<android.view.View>(R.id.segment_tree_zoom)?.width ?: 0 }
            width == scaledWidth
        }
        fun isScaledAlternative(node: AccessibilityNodeInfo): Boolean {
            val value = node.text?.toString().orEmpty()
            return node.isClickable && node.isVisibleToUser && node.contentDescription?.toString() ==
                instrumentation.targetContext.getString(R.string.segment_replace_description, value, "あしたは")
        }
        await("scaled candidate window accessibility") { allNodes().any(::isScaledAlternative) }
        val alternative = allNodes().first(::isScaledAlternative)
        val replacement = alternative.text.toString()
        val at = android.graphics.Rect().also(alternative::getBoundsInScreen)
        drag(at.exactCenterX(), at.exactCenterY(), at.exactCenterX(), at.exactCenterY())
        awaitText(replacement + "東京に行きます")
        assertComposing()
    }

    private fun branchViewVisible(): Boolean {
        val panel = treePanel()
        var shown = false
        instrumentation.runOnMainSync {
            shown = panel?.findViewById<android.view.View>(R.id.segment_tree_branches)?.isShown == true
        }
        return shown
    }

    private fun treePanel(): android.view.View? {
        var panel: android.view.View? = null
        instrumentation.runOnMainSync {
            panel = android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { root ->
                root.findViewById<android.view.View>(R.id.segment_candidate_tree_panel)?.takeIf { it.isShown }
            }
        }
        return panel
    }

    private fun treeBounds(): android.graphics.Rect? {
        var result: android.graphics.Rect? = null
        instrumentation.runOnMainSync {
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEach { root ->
                root.findViewById<android.view.View>(R.id.segment_candidate_tree_panel)?.takeIf { it.isShown }?.let { panel ->
                    val location = IntArray(2).also(panel::getLocationOnScreen)
                    result = android.graphics.Rect(location[0], location[1], location[0] + panel.width, location[1] + panel.height)
                }
            }
        }
        return result
    }

    @Test fun switchingToLegacyAndRestartingEditorDismissTreeWithoutStaleResults() = withKeyboard {
        startConversion()
        val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        prefs.edit().putString("candidate_display_method", "legacy").commit()
        awaitText("あしたはとうきょうにいきます")
        await { treeBounds() == null }
        host.onActivity { it.restartEditorInput(true) }
        SystemClock.sleep(500)
        key(KeyEvent.KEYCODE_KANA)
        type("a")
        awaitText("あ")
        SystemClock.sleep(500)
        assertEquals("あ", text())
        assertFalse(prefs.getBoolean("conversion_bunsetsu_separation_preference", true))
        assertFalse(prefs.getBoolean("conversion_bunsetsu_cursor_move_preference", true))
    }

    @Test fun landscapeBarAndTreeRemainEditable() = withKeyboard {
        host.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        await { instrumentation.targetContext.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        prepareJapaneseEditor()
        startConversion()
        screenshot("landscape-before.png")
        screenshot("landscape-open.png")
        await { treeBounds() != null }
        screenshot("landscape.png")
        assertComposing()
        // Expand the compact panel to inspect the reference-style graph end to end.
        clickDescription(instrumentation.targetContext.getString(R.string.composing_guide_edit))
        await { findDescription(instrumentation.targetContext.getString(R.string.composing_guide_done)) != null }
        val panel = treeBounds()!!
        val density = instrumentation.targetContext.resources.displayMetrics.density
        drag(panel.centerX().toFloat(), panel.bottom - 20 * density, panel.centerX().toFloat(),
            instrumentation.targetContext.resources.displayMetrics.heightPixels - 40 * density)
        clickDescription(instrumentation.targetContext.getString(R.string.composing_guide_done))
        await { findDescription(instrumentation.targetContext.getString(R.string.composing_guide_edit)) != null }
        screenshot("graph-expanded.png")
        assertComposing()
    }

    private fun prepareJapaneseEditor() {
        val deadline = SystemClock.uptimeMillis() + 30000
        var observed = ""
        while (SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(500)
            key(KeyEvent.KEYCODE_KANA)
            key(KeyEvent.KEYCODE_A)
            observed = text()
            host.onActivity { it.restartEditorInput(true) }
            if (observed == "あ") { SystemClock.sleep(500); return }
        }
        fail("Japanese IME input did not initialize: [$observed]")
    }

    private fun startConversion() {
        type("ashitahatoukyouniikimasu")
        awaitText("あしたはとうきょうにいきます")
        key(KeyEvent.KEYCODE_SPACE)
        awaitText("明日は東京に行きます")
        showSoftwareKeyboard()
        await { treeBounds() != null }
        assertFalse(allNodes().any { it.text?.toString() in setOf("Start", "End", "開始", "終わり", "Show\ntree", "ツリー\n表示") })
        assertComposing()
    }

    private fun showSoftwareKeyboard() {
        fun shown() = allNodes().any { it.contentDescription?.toString() == "delete_key" && it.isVisibleToUser }
        if (!shown()) clickDescription(instrumentation.targetContext.getString(R.string.physical_toolbar_keyboard_title))
        await { shown() && scrollState(R.id.segment_candidate_scroll) != null }
    }

    private fun assertComposing() {
        host.onActivity { assertEquals(0, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
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

    private fun type(romaji: String) = romaji.forEach { key(KeyEvent.KEYCODE_A + (it - 'a'), settleMillis = 250) }

    private fun key(code: Int, meta: Int = 0, settleMillis: Long = 160) {
        val time = SystemClock.uptimeMillis()
        fun inject(keyCode: Int, action: Int, metaState: Int) {
            assertTrue(automation.injectInputEvent(KeyEvent(
                time, SystemClock.uptimeMillis(), action, keyCode, 0, metaState, keyboardId,
                0, 0, InputDevice.SOURCE_KEYBOARD
            ), true))
        }
        val shifted = meta and KeyEvent.META_SHIFT_ON != 0
        if (shifted) inject(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_DOWN, meta)
        inject(code, KeyEvent.ACTION_DOWN, meta)
        inject(code, KeyEvent.ACTION_UP, meta)
        if (shifted) inject(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_UP, 0)
        SystemClock.sleep(settleMillis)
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

    private fun withKeyboard(floating: Boolean = false, block: () -> Unit) {
        keyboardId = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice)
            .firstOrNull { !it.isVirtual && it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC }
            ?.id ?: android.view.KeyCharacterMap.VIRTUAL_KEYBOARD
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val context = instrumentation.targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val values = mapOf<String, Any?>(
            "conversion_bunsetsu_separation_preference" to false,
            "conversion_bunsetsu_cursor_move_preference" to false,
            "candidate_order_override_enable_preference" to false,
            "candidate_display_method" to "segments",
            "segment_tree_enabled" to true,
            "segment_tree_style" to "sentence",
            "segment_tree_scale" to 85,
            "segment_tree_portrait_width" to null,
            "segment_tree_portrait_height" to null,
            "segment_tree_portrait_x" to null,
            "segment_tree_portrait_y" to null,
            "segment_tree_landscape_width" to null,
            "segment_tree_landscape_height" to null,
            "segment_tree_landscape_x" to null,
            "segment_tree_landscape_y" to null,
            "physical_toolbar_style" to "floating",
            "keyboard_floating_preference" to floating,
            "allow_fullscreen_mode_preference" to false,
            "keyboard_skin_id" to "default",
            "composing_guide_enabled" to false,
            "composing_guide_text_enabled" to false,
            "learn_dictionary_preference" to false,
            "user_template_preference" to true,
            "ng_word_enable_preference" to true,
            "physical_keyboard_input_mode_preference" to "romaji",
            "live_conversion_preference" to false,
            "sumire_keymap_guide_japanese" to false,
            "sumire_keymap_guide_modes_migrated" to true,
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
            host.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            host.onActivity { it.restartEditorInput(true) }
            prepareJapaneseEditor()
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
