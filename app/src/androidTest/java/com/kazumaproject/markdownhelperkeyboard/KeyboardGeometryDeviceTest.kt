package com.kazumaproject.markdownhelperkeyboard

import android.content.SharedPreferences
import android.graphics.Matrix
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.view.inputmethod.BaseInputConnection
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.android.flexbox.FlexboxLayoutManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
import com.kazumaproject.tenkey.TenKey
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.SideCandidateSizing
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.SideCandidateFlowLayoutManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class KeyboardGeometryDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun splitQwertyAcceptsInputAndCenterCandidatesCanBeSelected() = verify(floating = false, tenkey = false)
    @Test fun floatingSplitQwertyAcceptsInputAndCenterCandidatesCanBeSelected() = verify(floating = true, tenkey = false)
    @Test fun curvedTenKeyAcceptsInputAndSideCandidatesCanBeSelected() = verify(floating = false, tenkey = true)
    @Test fun floatingCurvedTenKeyAcceptsInputAndSideCandidatesCanBeSelected() = verify(floating = true, tenkey = true)
    @Test fun reverseSplitQwertyAcceptsInputAndCenterCandidatesCanBeSelected() = verify(floating = false, tenkey = false, reverse = true)
    @Test fun floatingReverseTenKeyAcceptsInputAndSideCandidatesCanBeSelected() = verify(floating = true, tenkey = true, reverse = true)
    @Test fun sideCandidatesCanSwitchSidesAndReturnToTheTop() = verify(floating = false, tenkey = true, switchSide = true)
    @Test fun sideCandidatesRestoreTheConfiguredMultiRowTopLayout() = verify(floating = false, tenkey = true, switchSide = true, columns = "3")
    @Test fun rightSideCandidatesWorkWithKeyboardMargins() = verify(floating = false, tenkey = true, side = "RIGHT", marginDp = 50)
    @Test fun dockedSideCandidatesResizeWithTheKeyboard() = verify(floating = false, tenkey = true, resizeSide = true)
    @Test fun floatingSideCandidatesResizeWithTheKeyboard() = verify(floating = true, tenkey = true, side = "RIGHT", resizeSide = true)
    @Test fun dockedSideCandidateWidthAndVerticalTextCanChange() = verify(floating = false, tenkey = true, adjustSide = true)
    @Test fun floatingSideCandidateWidthAndVerticalTextCanChange() = verify(floating = true, tenkey = true, side = "RIGHT", adjustSide = true)
    @Test fun sumireSideCandidateWidthAndVerticalTextCanChange() = verify(floating = false, tenkey = true, adjustSide = true, sumire = true)
    @Test fun scrolledVerticalCandidateCommitsItsFullText() = verify(floating = false, tenkey = true, adjustSide = true, selectScrolled = true)
    @Test fun sideCandidatesReturnAfterExpandedListCloses() = verify(floating = false, tenkey = true, expanded = true)
    @Test fun floatingSideCandidatesReturnAfterExpandedListCloses() = verify(floating = true, tenkey = true, side = "RIGHT", expanded = true)
    @Test fun sideCandidatesKeepTabsWhileExpanded() = verify(floating = false, tenkey = true, expanded = true, chromeMode = 1)
    @Test fun expandedSideCandidatesDoNotHideItemsOutsideTheHeader() = verify(floating = false, tenkey = true, expanded = true, narrowHeader = true)
    @Test fun sideCandidatesKeepIndependentToolbarAndTabs() = verify(floating = false, tenkey = true, chromeMode = 1)
    @Test fun sideCandidatesKeepIntegratedToolbarAndTabs() = verify(floating = false, tenkey = true, chromeMode = 2)
    @Test fun narrowTenKeyFallsBackToTopCandidates() = verify(floating = false, tenkey = true, marginDp = 110, fallback = true)
    @Test fun narrowQwertyGapFallsBackToTopCandidates() = verify(floating = false, tenkey = false, splitPercent = 10, fallback = true)

    @Test fun landscapeSplitQwertyAcceptsInputAndCenterCandidatesCanBeSelected() {
        val oldAuto = shell("settings get system accelerometer_rotation")
        val oldRotation = shell("settings get system user_rotation")
        try {
            shell("settings put system accelerometer_rotation 0")
            shell("settings put system user_rotation 1")
            SystemClock.sleep(1500)
            verify(floating = false, tenkey = false, landscape = true)
        } finally {
            if (oldRotation == "null") shell("settings delete system user_rotation") else shell("settings put system user_rotation $oldRotation")
            if (oldAuto == "null") shell("settings delete system accelerometer_rotation") else shell("settings put system accelerometer_rotation $oldAuto")
        }
    }

    private fun verify(floating: Boolean, tenkey: Boolean, landscape: Boolean = false, reverse: Boolean = false,
        side: String = "LEFT", marginDp: Int = 0, splitPercent: Int = 40, fallback: Boolean = false,
        switchSide: Boolean = false, columns: String = "1", resizeSide: Boolean = false, adjustSide: Boolean = false,
        sumire: Boolean = false, expanded: Boolean = false, chromeMode: Int = 0, narrowHeader: Boolean = false,
        selectScrolled: Boolean = false) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.all.toMap()
        val originalIme = shell("settings get secure default_input_method")
        val targetIme = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val wasEnabled = shell("ime list -s").lineSequence().any { it.trim() == targetIme }
        val orientation = if (landscape) "landscape" else "portrait"
        val geometryName = if (sumire) "sumire" else "tenkey"
        var activity: FastInputHostActivity? = null
        try {
            check(prefs.edit().clear()
                .putString("keyboard_order_preference", if (sumire) "[\"SUMIRE\"]" else if (tenkey) "[\"TENKEY\"]" else "[\"QWERTY\"]")
                .putString("sumire_keyboard_style_preference", "default")
                .putString("sumire_input_method_preference", "toggle")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("keyboard_floating_preference", floating)
                .putBoolean("shortcut_toolbar_visibility_preference", chromeMode != 0)
                .putBoolean("shortcut_toolbar_integrated_in_suggestion_preference", chromeMode == 2)
                .putBoolean("candidate_tab_visibility_preference", chromeMode != 0)
                .putBoolean("zero_query_suggestion_preference", false)
                .putBoolean("qwerty_glide_input_preference", true)
                .putBoolean("learn_dictionary_preference", false)
                .putString("candidate_column_preference", columns)
                .putInt("qwerty_keyboard_width_preference", 100)
                .putInt("qwerty_keyboard_height_preference", 220)
                .putInt("qwerty_keyboard_width_landscape_preference", 100)
                .putInt("qwerty_keyboard_height_landscape_preference", 200)
                .putInt("keyboard_margin_start_dp_preference", marginDp)
                .putInt("keyboard_margin_end_dp_preference", marginDp)
                .putInt("keyboard_geometry_qwerty_${orientation}_split", splitPercent)
                .putInt("keyboard_geometry_qwerty_${orientation}_curve", 20)
                .putString("keyboard_geometry_qwerty_${orientation}_curve_direction", if (reverse) "UP" else "DOWN")
                .putString("keyboard_geometry_qwerty_${orientation}_candidates", "CENTER")
                .putBoolean("keyboard_geometry_qwerty_${orientation}_auto_height", true)
                .putInt("keyboard_geometry_${geometryName}_portrait_curve", 20)
                .putString("keyboard_geometry_${geometryName}_portrait_curve_direction", if (reverse) "UP" else "DOWN")
                .putString("keyboard_geometry_${geometryName}_portrait_candidates", side)
                .putBoolean("keyboard_geometry_${geometryName}_portrait_candidate_vertical_text", expanded || chromeMode != 0)
                .putBoolean("flick_input_only_preference", true)
                .commit())
            shell("ime enable $targetIme")
            shell("am start -n ${context.packageName}/${FastInputHostActivity::class.java.name}")
            await {
                instrumentation.runOnMainSync {
                    activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<FastInputHostActivity>().firstOrNull()?.takeIf { it.hasWindowFocus() }
                }
                activity != null
            }
            shell("ime set $targetIme")
            await { shell("settings get secure default_input_method") == targetIme }
            // The selected IME can be reported before its service has rebound to this editor.
            SystemClock.sleep(750)
            val host = requireNotNull(activity)
            val editor = host.editText
            instrumentation.runOnMainSync {
                host.restartEditorInput(clearText = true)
                host.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    .showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            val keyboard: View = if (sumire) {
                val keyboard = awaitView<ViewGroup>(R.id.custom_layout_default) {
                    runCatching { findTextView(it, "あ"); findTextView(it, "か") }.isSuccess
                }
                tap(findTextView(keyboard, "あ"))
                if (adjustSide) tap(findTextView(keyboard, "か"))
                keyboard
            } else if (tenkey) {
                val keyboard = awaitView<TenKey>(if (floating) R.id.keyboard_view_floating else R.id.keyboard_view) {
                    val rotation = it.findViewById<View>(com.kazumaproject.tenkey.R.id.key_1)?.rotation ?: 0f
                    if (reverse) rotation > 0f else rotation < 0f
                }
                tap(keyboard.findViewById(com.kazumaproject.tenkey.R.id.key_1))
                if (adjustSide) tap(keyboard.findViewById(com.kazumaproject.tenkey.R.id.key_2))
                keyboard
            } else {
                val keyboard = awaitView<QWERTYKeyboardView>(if (floating) R.id.qwerty_view_floating else R.id.qwerty_view) {
                    val rotation = it.findViewById<View>(com.kazumaproject.qwerty_keyboard.R.id.key_t)?.rotation ?: 0f
                    it.effectiveSplitGapPx() > 0f &&
                        if (reverse) rotation < 0f else rotation > 0f
                }
                for (id in listOf(com.kazumaproject.qwerty_keyboard.R.id.key_h,
                    com.kazumaproject.qwerty_keyboard.R.id.key_e,
                    com.kazumaproject.qwerty_keyboard.R.id.key_l,
                    com.kazumaproject.qwerty_keyboard.R.id.key_l,
                    com.kazumaproject.qwerty_keyboard.R.id.key_o)) {
                    tap(keyboard.findViewById(id))
                }
                keyboard
            }
            await { var text = ""; instrumentation.runOnMainSync { text = editor.text.toString() }; text.isNotEmpty() }
            SystemClock.sleep(1200)
            fun awaitCandidates(position: String): RecyclerView {
                val vertical = position != "TOP"
                val candidates = awaitView<RecyclerView>(R.id.suggestion_recycler_view) {
                    ((it.layoutManager as? LinearLayoutManager)?.orientation ==
                        (if (vertical) RecyclerView.VERTICAL else RecyclerView.HORIZONTAL) ||
                        (vertical && it.layoutManager is SideCandidateFlowLayoutManager) ||
                        (!vertical && floating && it.layoutManager is FlexboxLayoutManager)) && it.childCount > 0 &&
                        (vertical || columns == "1" || it.layoutManager is GridLayoutManager)
                }
                await {
                    var correctlyPlaced = false
                    instrumentation.runOnMainSync {
                        val body = Rect(); val strip = Rect()
                        if (keyboard.getGlobalVisibleRect(body) && candidates.getGlobalVisibleRect(strip)) {
                            val tolerance = (2f * context.resources.displayMetrics.density).toInt()
                            correctlyPlaced = when (position) {
                                "LEFT" -> strip.right <= body.left + tolerance
                                "RIGHT" -> strip.left >= body.right - tolerance
                                "TOP" -> strip.bottom <= body.top + tolerance
                                else -> strip.left >= body.left && strip.right <= body.right
                            }
                        }
                    }
                    correctlyPlaced
                }
                return candidates
            }
            val initialPosition = if (fallback) "TOP" else if (tenkey) side else "CENTER"
            var candidates = awaitCandidates(initialPosition)
            if (chromeMode != 0) {
                val tabs = awaitView<com.google.android.material.tabs.TabLayout>(R.id.candidate_tab_layout) { it.tabCount > 1 }
                instrumentation.runOnMainSync {
                    val tabBounds = Rect(); val keyboardBounds = Rect()
                    assertTrue(tabs.getGlobalVisibleRect(tabBounds) && keyboard.getGlobalVisibleRect(keyboardBounds))
                    assertTrue("Candidate tabs remain above the keyboard", tabBounds.bottom <= keyboardBounds.top)
                    val toolbar = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull {
                        it.findViewById<View>(R.id.shortcut_toolbar_recyclerview)
                    }
                    assertTrue("Shortcuts give way to active candidates", toolbar == null || !toolbar.isShown)
                    tabs.getTabAt(1)?.select()
                }
                await { var ok = false; instrumentation.runOnMainSync { ok = tabs.selectedTabPosition == 1 }; ok }
                instrumentation.runOnMainSync { tabs.getTabAt(0)?.select() }
                await { var ok = false; instrumentation.runOnMainSync { ok = tabs.selectedTabPosition == 0 }; ok }
                candidates = awaitCandidates(initialPosition)
            }
            if (expanded) {
                val toggle = awaitView<View>(R.id.suggestion_visibility) {
                    !floating || it.rootView === keyboard.rootView
                }
                var originalToggleWidth = 0
                if (narrowHeader) instrumentation.runOnMainSync {
                    val params = toggle.layoutParams
                    originalToggleWidth = params.width
                    params.width = context.resources.displayMetrics.widthPixels * 3 / 5
                    toggle.layoutParams = params
                }
                // Popup window coordinates differ from injected screen coordinates on the emulator.
                if (floating || narrowHeader) instrumentation.runOnMainSync { assertTrue(toggle.performClick()) } else tap(toggle)
                val full = awaitView<RecyclerView>(R.id.candidates_row_view) {
                    (!floating || it.rootView === keyboard.rootView) && it.childCount > 0
                }
                instrumentation.runOnMainSync {
                    assertTrue("Expanded candidates use the existing full list", full.layoutManager is FlexboxLayoutManager)
                    assertTrue("The keyboard is hidden while candidates are expanded", !keyboard.isShown)
                    val text = (0 until full.childCount).firstNotNullOfOrNull {
                        full.getChildAt(it).findViewById<android.widget.TextView>(R.id.suggestion_item_text_view)
                    }
                    assertTrue("The full list retains the existing horizontal candidate text", text != null && text.maxLines == 1)
                }
                if (chromeMode != 0) {
                    val tabs = awaitView<com.google.android.material.tabs.TabLayout>(R.id.candidate_tab_layout) { it.tabCount > 1 }
                    instrumentation.runOnMainSync { tabs.getTabAt(1)?.select() }
                    await { var ok = false; instrumentation.runOnMainSync { ok = tabs.selectedTabPosition == 1 }; ok }
                    SystemClock.sleep(1200)
                    instrumentation.runOnMainSync { tabs.getTabAt(0)?.select() }
                    await { var ok = false; instrumentation.runOnMainSync { ok = tabs.selectedTabPosition == 0 }; ok }
                    SystemClock.sleep(1200)
                }
                if (narrowHeader) {
                    SystemClock.sleep(1200)
                    instrumentation.runOnMainSync {
                        assertEquals("A narrow header must not hide candidates from the full list", 0,
                            (full.layoutManager as FlexboxLayoutManager).findFirstVisibleItemPosition())
                    }
                    instrumentation.runOnMainSync {
                        val params = toggle.layoutParams
                        params.width = originalToggleWidth
                        toggle.layoutParams = params
                    }
                } else {
                    await {
                        var firstVisible = 0
                        instrumentation.runOnMainSync {
                            firstVisible = (full.layoutManager as FlexboxLayoutManager).findFirstVisibleItemPosition()
                        }
                        firstVisible > 0
                    }
                    instrumentation.runOnMainSync {
                        val skipped = (full.layoutManager as FlexboxLayoutManager).findFirstVisibleItemPosition()
                        val visibleHeaderPositions = (0 until candidates.childCount).mapNotNull { index ->
                            val child = candidates.getChildAt(index)
                            val visible = Rect()
                            if (child.getGlobalVisibleRect(visible) && visible.width() >= child.width) {
                                candidates.getChildAdapterPosition(child)
                            } else null
                        }.toSet()
                        assertTrue("Every skipped candidate is fully visible in the header",
                            (0 until skipped).all { it in visibleHeaderPositions })
                    }
                }
                if (floating || narrowHeader) instrumentation.runOnMainSync { assertTrue(toggle.performClick()) } else tap(toggle)
                candidates = awaitCandidates(initialPosition)
                await { var ok = false; instrumentation.runOnMainSync { ok = candidates.layoutManager is SideCandidateFlowLayoutManager }; ok }
            }
            if (tenkey && !floating && !fallback) {
                val density = context.resources.displayMetrics.density
                val available = context.resources.displayMetrics.widthPixels - (2 * marginDp * density).toInt()
                val expectedWidth = available / 4
                assertEquals("candidate width uses the available width after margins", expectedWidth.toFloat(), candidates.width.toFloat(), 2f)
            }
            if (resizeSide) {
                val density = context.resources.displayMetrics.density
                val screenWidth = context.resources.displayMetrics.widthPixels
                for (percent in listOf(80, 70)) {
                    check(prefs.edit().putInt("keyboard_width_preference", percent)
                        .putInt("keyboard_height_preference", 200).commit())
                    val expectedSurface = screenWidth * percent / 100f - if (floating) 16 * density else 0f
                    await {
                        var matches = false
                        instrumentation.runOnMainSync {
                            val body = Rect(); val strip = Rect()
                            if (keyboard.getGlobalVisibleRect(body) && candidates.getGlobalVisibleRect(strip)) {
                                val total = body.width() + strip.width()
                                matches = kotlin.math.abs(total - expectedSurface) < 8f &&
                                    kotlin.math.abs(strip.width() * 4f - total) < 8f &&
                                    kotlin.math.abs(body.height() - strip.height()) < 8f &&
                                    if (side == "LEFT") kotlin.math.abs(strip.right - body.left) < 8
                                    else kotlin.math.abs(strip.left - body.right) < 8
                            }
                        }
                        matches
                    }
                }
                check(prefs.edit().putInt("keyboard_width_preference", 50).commit())
                candidates = awaitCandidates("TOP")
                check(prefs.edit().putInt("keyboard_width_preference", 80).commit())
                candidates = awaitCandidates(side)
            }
            if (adjustSide) {
                for (percent in listOf(40, 20, 50, 25)) {
                    check(prefs.edit().putInt("keyboard_geometry_${geometryName}_portrait_candidate_width_percent", percent).commit())
                    await {
                        var matches = false
                        instrumentation.runOnMainSync {
                            val body = Rect(); val strip = Rect()
                            if (keyboard.getGlobalVisibleRect(body) && candidates.getGlobalVisibleRect(strip)) {
                                val total = body.width() + strip.width()
                                matches = kotlin.math.abs(strip.width() - total * percent / 100f) < 8f &&
                                    kotlin.math.abs(strip.height() - body.height()) < 8f
                            }
                        }
                        matches
                    }
                }
                for (vertical in listOf(true, false, true)) {
                    check(prefs.edit().putBoolean("keyboard_geometry_${geometryName}_portrait_candidate_vertical_text", vertical).commit())
                    await {
                        var matches = false
                        instrumentation.runOnMainSync {
                            val text = (0 until candidates.childCount).firstNotNullOfOrNull {
                                candidates.getChildAt(it).findViewById<android.widget.TextView>(R.id.suggestion_item_text_view)
                            }
                            matches = text != null && if (vertical) {
                                text.maxLines > 1 && candidates.layoutManager is SideCandidateFlowLayoutManager
                            } else text.maxLines == 1 && candidates.layoutManager !is SideCandidateFlowLayoutManager
                        }
                        matches
                    }
                }
                var sawOneColumn = false
                var sawMultipleColumns = false
                for (percent in listOf(20, 40, 50, 20)) {
                    check(prefs.edit().putInt("keyboard_geometry_${geometryName}_portrait_candidate_width_percent", percent).commit())
                    await {
                        var matches = false
                        instrumentation.runOnMainSync {
                            val manager = candidates.layoutManager as? SideCandidateFlowLayoutManager
                            val metrics = context.resources.displayMetrics
                            val textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                                if (floating) 18f else 14f, metrics)
                            val expected = SideCandidateSizing.columnCount(candidates.width, metrics.density, textSize)
                            val body = Rect(); val strip = Rect()
                            val target = if (keyboard.getGlobalVisibleRect(body) && candidates.getGlobalVisibleRect(strip)) {
                                kotlin.math.abs(strip.width() - (body.width() + strip.width()) * percent / 100f) < 8f
                            } else false
                            val positions = (0 until candidates.childCount).mapNotNull {
                                val child = candidates.getChildAt(it)
                                val visible = Rect()
                                child.takeIf { view -> view.findViewById<View>(R.id.suggestion_item_text_view) != null &&
                                    view.getGlobalVisibleRect(visible) && visible.width() >= view.width }?.left
                            }.distinct()
                            matches = target && manager != null && manager.canScrollHorizontally() &&
                                !manager.canScrollVertically() && (expected == 1 || positions.size > 1)
                            if (matches) {
                                sawOneColumn = sawOneColumn || expected == 1
                                sawMultipleColumns = sawMultipleColumns || expected > 1
                            }
                        }
                        matches
                    }
                }
                assertTrue("narrow candidate area uses one column", sawOneColumn)
                assertTrue("wider candidate area shows multiple columns", sawMultipleColumns)
                check(prefs.edit().putInt("keyboard_geometry_${geometryName}_portrait_candidate_width_percent", 40).commit())
                for ((iteration, rightToLeft) in listOf(false, true, false).withIndex()) {
                    check(prefs.edit().putBoolean("keyboard_geometry_${geometryName}_portrait_candidate_right_to_left", rightToLeft).commit())
                    await {
                        var matches = false
                        instrumentation.runOnMainSync {
                            val flow = candidates.layoutManager as? SideCandidateFlowLayoutManager
                            val first = flow?.findViewByPosition(0)
                            val second = flow?.flexLines?.firstOrNull()?.itemCount?.let { flow.findViewByPosition(it) }
                            if (flow != null && first != null && second != null) {
                                matches = if (rightToLeft) first.left > second.left else first.left < second.left
                            }
                        }
                        matches
                    }
                    val direction = if (rightToLeft) -1 else 1
                    await { var canScroll = false; instrumentation.runOnMainSync { canScroll = candidates.canScrollHorizontally(direction) }; canScroll }
                    swipeCandidates(candidates, rightToLeft)
                    await {
                        var firstVisible = 0
                        instrumentation.runOnMainSync {
                            firstVisible = (candidates.layoutManager as SideCandidateFlowLayoutManager).findFirstVisibleItemPosition()
                        }
                        firstVisible > 0
                    }
                    await { var canReturn = false; instrumentation.runOnMainSync { canReturn = candidates.canScrollHorizontally(-direction) }; canReturn }
                    if (!selectScrolled || iteration != 2) instrumentation.runOnMainSync { candidates.scrollToPosition(0) }
                }
                if (!selectScrolled) {
                    check(prefs.edit().putString("keyboard_geometry_${geometryName}_portrait_candidates", "TOP").commit())
                    candidates = awaitCandidates("TOP")
                    await {
                        var restored = false
                        instrumentation.runOnMainSync {
                            val text = (0 until candidates.childCount).firstNotNullOfOrNull {
                                candidates.getChildAt(it).findViewById<android.widget.TextView>(R.id.suggestion_item_text_view)
                            }
                            restored = text?.maxLines == 1
                        }
                        restored
                    }
                    check(prefs.edit().putString("keyboard_geometry_${geometryName}_portrait_candidates", side).commit())
                    candidates = awaitCandidates(side)
                    await {
                        var restored = false
                        instrumentation.runOnMainSync { restored = candidates.layoutManager is SideCandidateFlowLayoutManager }
                        restored
                    }
                }
            }
            if (switchSide) {
                for (position in listOf("RIGHT", "TOP", "LEFT")) {
                    check(prefs.edit().putString("keyboard_geometry_tenkey_portrait_candidates", position).commit())
                    candidates = awaitCandidates(position)
                }
            }
            var committed = false
            var expectedCommit: String? = null
            // Suggestions can be replaced asynchronously as dictionaries finish loading.
            // Reacquire the visible item if a pending update replaces it during a tap.
            repeat(5) {
                if (!committed) {
                    candidates = awaitCandidates(if (switchSide) "LEFT" else initialPosition)
                    var first: View? = null
                    instrumentation.runOnMainSync {
                        first = (0 until candidates.childCount).map { candidates.getChildAt(it) }.firstOrNull {
                            val visible = Rect()
                            it.isShown && it.isAttachedToWindow && it.getGlobalVisibleRect(visible) && visible.width() >= it.width &&
                                (!selectScrolled || it.findViewById<android.widget.TextView>(R.id.suggestion_item_text_view)?.contentDescription != null)
                        }
                        if (selectScrolled) expectedCommit = first?.findViewById<android.widget.TextView>(R.id.suggestion_item_text_view)?.contentDescription?.toString()
                    }
                    first?.let { tap(it) }
                    val end = SystemClock.uptimeMillis() + 2000
                    while (!committed && SystemClock.uptimeMillis() < end) {
                        instrumentation.runOnMainSync { committed = BaseInputConnection.getComposingSpanStart(editor.text) < 0 }
                        if (!committed) SystemClock.sleep(100)
                    }
                }
            }
            assertTrue("A candidate in the relocated list should commit the composition", committed)
            if (selectScrolled) instrumentation.runOnMainSync {
                assertEquals("Selecting a scrolled candidate commits its full value", expectedCommit, editor.text.toString())
            }
            if (chromeMode == 1) {
                awaitView<RecyclerView>(R.id.shortcut_toolbar_recyclerview) { it.childCount > 0 }
            } else if (chromeMode == 2) {
                awaitView<RecyclerView>(R.id.suggestion_recycler_view) { view ->
                    (0 until view.childCount).any { view.getChildAt(it).findViewById<View>(R.id.item_image) != null }
                }
            }
            assertTrue(prefs.getBoolean("qwerty_glide_input_preference", false))
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }
            restore(prefs, original)
            if (originalIme.isNotBlank() && originalIme != "null") shell("ime set $originalIme")
            if (!wasEnabled) shell("ime disable $targetIme")
        }
    }

    private fun findTextView(root: View, text: String): View {
        if (root is android.widget.TextView && root.text.toString() == text) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) {
            val found = runCatching { findTextView(root.getChildAt(i), text) }.getOrNull()
            if (found != null) return found
        }
        error("No visible key labeled $text")
    }

    private fun swipeCandidates(view: RecyclerView, rightToLeft: Boolean) {
        instrumentation.runOnMainSync {
            val start = view.width * if (rightToLeft) .12f else .88f
            val end = view.width * if (rightToLeft) .88f else .12f
            val y = view.height / 2f
            val down = SystemClock.uptimeMillis()
            for (step in 0..8) {
                val action = when (step) { 0 -> MotionEvent.ACTION_DOWN; 8 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                val event = MotionEvent.obtain(down, down + step * 30L, action, start + (end - start) * step / 8f, y, 0)
                try { view.dispatchTouchEvent(event) } finally { event.recycle() }
            }
        }
    }

    private inline fun <reified T : View> awaitView(id: Int, crossinline predicate: (T) -> Boolean): T {
        var found: T? = null
        await {
            instrumentation.runOnMainSync {
                found = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull {
                    it.findViewById<T>(id)?.takeIf { view -> view.isShown && view.width > 0 && predicate(view) }
                }
            }
            found != null
        }
        return requireNotNull(found)
    }

    private fun tap(view: View) {
        val point = floatArrayOf(0f, 0f)
        var previous = Pair(Float.NaN, Float.NaN)
        var stableSince = 0L
        await {
            instrumentation.runOnMainSync {
                val position = IntArray(2).also(view::getLocationOnScreen)
                val points = floatArrayOf(0f, 0f, view.width / 2f, view.height / 2f)
                val transform = Matrix(); view.transformMatrixToGlobal(transform); transform.mapPoints(points)
                point[0] = position[0] + points[2] - points[0]
                point[1] = position[1] + points[3] - points[1]
            }
            val current = point[0] to point[1]
            if (current != previous) { previous = current; stableSince = SystemClock.uptimeMillis() }
            SystemClock.uptimeMillis() - stableSince >= 400
        }
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point[0], point[1], 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
            SystemClock.sleep(50)
        }
        SystemClock.sleep(200)
    }

    private fun await(predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < end) {
            if (predicate()) return
            SystemClock.sleep(100)
        }
        error("Timed out waiting for the IME to update")
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }

    private fun restore(prefs: SharedPreferences, values: Map<String, *>) {
        val edit = prefs.edit().clear()
        values.forEach { (key, value) -> when (value) {
            is String -> edit.putString(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }
        check(edit.commit())
    }
}
