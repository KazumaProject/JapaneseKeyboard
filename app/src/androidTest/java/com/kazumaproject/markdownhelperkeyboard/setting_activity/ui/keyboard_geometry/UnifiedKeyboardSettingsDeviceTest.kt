package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_geometry

import android.content.Intent
import android.content.res.Configuration
import android.content.SharedPreferences
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.gojuon_keyboard.GojuonKeyboardView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.KeyboardGeometryPreference
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.GeometryCandidatePlacement
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.keyboard_size.IndependentKeyboardSize
import com.kazumaproject.markdownhelperkeyboard.setting_activity.keyboard_size.IndependentKeyboardSizePreferences
import com.kazumaproject.markdownhelperkeyboard.setting_activity.keyboard_size.IndependentKeyboardSizeTarget
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.ModeKeySizing
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class UnifiedKeyboardSettingsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    @Test fun sideCandidateControlsAppearOnlyForSidePlacementAndSaveChanges() {
        AppPreference.init(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.all.toMap()
        try {
            prefs.edit().putString("keyboard_geometry_tenkey_portrait_candidates", "TOP").commit()
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = it.isSettingsContentReady }; ready }
                scenario.onActivity { host(it).navController.navigate(R.id.keyboardGeometrySettingsFragment,
                    Bundle().apply { putString("geometryKeyboard", "TENKEY") }) }
                var page: View? = null
                await { scenario.onActivity { page = host(it).childFragmentManager.primaryNavigationFragment?.view }; page?.findViewById<RadioGroup>(R.id.keyboard_geometry_candidate_placement) != null }
                instrumentation.runOnMainSync {
                    val root = requireNotNull(page)
                    assertNull(root.findViewById<SeekBar>(R.id.keyboard_geometry_candidate_width))
                    root.findViewById<RadioGroup>(R.id.keyboard_geometry_candidate_placement).check(3)
                }
                await { var ready = false; instrumentation.runOnMainSync { ready = (page?.findViewById<SeekBar>(R.id.keyboard_geometry_candidate_width)?.width ?: 0) > 0 }; ready }
                instrumentation.runOnMainSync {
                    val root = requireNotNull(page)
                    val seek = root.findViewById<SeekBar>(R.id.keyboard_geometry_candidate_width)
                    val x = seek.width * .8f; val y = seek.height / 2f
                    touch(seek, MotionEvent.ACTION_DOWN, x, y)
                    touch(seek, MotionEvent.ACTION_UP, x, y)
                    root.findViewById<RadioGroup>(R.id.keyboard_geometry_candidate_order).check(2)
                }
                assertTrue(prefs.getInt("keyboard_geometry_tenkey_portrait_candidate_width_percent", 25) > 25)
                assertTrue(prefs.getBoolean("keyboard_geometry_tenkey_portrait_candidate_vertical_text", false))
                instrumentation.runOnMainSync {
                    val root = requireNotNull(page)
                    root.findViewById<RadioGroup>(R.id.keyboard_geometry_candidate_placement).check(1)
                    assertNull(root.findViewById<SeekBar>(R.id.keyboard_geometry_candidate_width))
                    assertNull(root.findViewById<RadioGroup>(R.id.keyboard_geometry_candidate_order))
                }
            }
        } finally { restore(prefs, original) }
    }

    @Test fun sideCandidatePreviewUsesTheRequestedWidthAndKeepsKeysUsable() {
        AppPreference.init(context)
        instrumentation.runOnMainSync {
            val screen = context.resources.displayMetrics.widthPixels
            val density = context.resources.displayMetrics.density
            for (type in listOf(PreviewKeyboard.TENKEY, PreviewKeyboard.SUMIRE)) {
                for (placement in listOf(GeometryCandidatePlacement.LEFT, GeometryCandidatePlacement.RIGHT)) {
                    val preview = GeometryPreview(context, type)
                    for (candidatePercent in listOf(15, 25, 40, 50)) {
                        preview.update(KeyboardGeometryPreference(candidates = placement, sideCandidateWidthPercent = candidatePercent),
                            false, 100, false, IndependentKeyboardSize(widthPercent = 100))
                        measurePreview(preview, screen)
                        val surface = requireNotNull(preview.adjustmentBounds())
                        val body = requireNotNull(preview.bodyBounds())
                        val expected = (screen * candidatePercent / 100f).coerceIn(64f * density, screen - 192f * density)
                        assertEquals("candidate width follows its percentage and minimum sizes", expected, surface.width() - body.width(), 3f)
                        if (placement == GeometryCandidatePlacement.LEFT) assertEquals(surface.left + expected, body.left, 3f)
                        else assertEquals(surface.left, body.left, 2f)
                    }
                }
            }
        }
    }

    @Test fun sideCandidatePreviewResizesTheWholeSurfaceAndFallsBackWhenNarrow() {
        AppPreference.init(context)
        instrumentation.runOnMainSync {
            val screenWidth = context.resources.displayMetrics.widthPixels
            for (type in listOf(PreviewKeyboard.TENKEY, PreviewKeyboard.SUMIRE)) {
                val preview = GeometryPreview(context, type)
                for (placement in listOf(GeometryCandidatePlacement.LEFT, GeometryCandidatePlacement.RIGHT)) {
                    for (percent in listOf(100, 80, 70)) {
                        preview.update(KeyboardGeometryPreference(candidates = placement), false, percent, false,
                            IndependentKeyboardSize(widthPercent = percent))
                        measurePreview(preview, screenWidth)
                        val body = requireNotNull(preview.bodyBounds())
                        val surface = requireNotNull(preview.adjustmentBounds())
                        assertEquals("surface follows width setting", screenWidth * percent / 100f, surface.width(), 2f)
                        assertEquals("keyboard occupies three quarters", surface.width() * .75f, body.width(), 2f)
                        assertEquals("candidate and keyboard share height", surface.height(), body.height(), 1f)
                        if (placement == GeometryCandidatePlacement.LEFT) assertEquals(surface.left + surface.width() * .25f, body.left, 2f)
                        else assertEquals(surface.left, body.left, 2f)
                    }
                    preview.update(KeyboardGeometryPreference(candidates = placement), false, 50, false,
                        IndependentKeyboardSize(widthPercent = 50))
                    measurePreview(preview, screenWidth)
                    assertEquals("narrow layouts use top candidates", preview.adjustmentBounds()!!.width(), preview.bodyBounds()!!.width(), 1f)
                }
            }
        }
    }

    @Test fun sideCandidateResizeHandlesKeepTheOppositeEdgeFixed() {
        AppPreference.init(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.all.toMap()
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method")
        val enabled = shell("ime list -s").lineSequence().any { it.trim() == ime }
        try {
            shell("ime enable $ime"); shell("ime set $ime")
            prefs.edit().putInt("keyboard_width_preference", 80).putBoolean("keyboard_position_preference", true)
                .putInt("keyboard_margin_start_dp_preference", 0).putInt("keyboard_margin_end_dp_preference", 10)
                .putString("keyboard_geometry_tenkey_portrait_candidates", "LEFT").commit()
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = it.isSettingsContentReady }; ready }
                scenario.onActivity { host(it).navController.navigate(R.id.keyboardGeometrySettingsFragment,
                    Bundle().apply { putString("geometryKeyboard", "TENKEY") }) }
                var page: View? = null
                await { scenario.onActivity { page = host(it).childFragmentManager.primaryNavigationFragment?.view }; page?.findViewById<RadioGroup>(R.id.keyboard_layout_tab_selector) != null }
                instrumentation.runOnMainSync { requireNotNull(page).findViewById<RadioGroup>(R.id.keyboard_layout_tab_selector).check(1) }
                instrumentation.runOnMainSync { requireNotNull(page).findViewById<View>(R.id.keyboard_layout_edit_size).performClick() }
                await { var ready = false; instrumentation.runOnMainSync { ready = (page?.findViewById<GeometryPreview>(R.id.keyboard_layout_preview)?.adjustmentBounds()?.width() ?: 0f) > 0f }; ready }
                var left = 0f
                var startX = 0; var startY = 0; var delta = 0
                val rect = Regex("\\((\\d+),(\\d+),(\\d+),(\\d+)\\)")
                var previousStart: Pair<Int, Int>? = null
                var stableSamples = 0
                await {
                    var laidOut = false
                    instrumentation.runOnMainSync {
                        val preview = requireNotNull(page).findViewById<GeometryPreview>(R.id.keyboard_layout_preview)
                        val handles = requireNotNull(page).findViewById<View>(R.id.keyboard_layout_handles)
                        val bounds = preview.adjustmentBounds()
                        if (bounds != null && handles.isShown && handles.hasWindowFocus() && handles.width > 0 && handles.height > 0 && !handles.isLayoutRequested) {
                            left = bounds.left
                            val location = IntArray(2).also(preview::getLocationOnScreen)
                            startX = (location[0] + bounds.right.coerceAtMost(preview.width - 9 * context.resources.displayMetrics.density)).roundToInt()
                            // Start near the end of the longer visible grip, beyond the old 48 dp target.
                            startY = (location[1] + bounds.centerY() + 30 * context.resources.displayMetrics.density).roundToInt()
                            delta = (15 * context.resources.displayMetrics.density).roundToInt()
                            laidOut = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q || handles.systemGestureExclusionRects.isNotEmpty()
                        }
                    }
                    // Both the editor layout and WindowManager's exclusion arrive asynchronously.
                    // Recalculate the start point until the actual exclusion covers it.
                    val excluded = if (!laidOut) false else if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) true else {
                        val exclusion = shell("dumpsys window displays").lineSequence()
                            .firstOrNull { it.contains("mSystemGestureExclusion=SkRegion(") }.orEmpty()
                        rect.findAll(exclusion).any { match ->
                            val (l, t, r, b) = match.destructured
                            startX >= l.toInt() && startX < r.toInt() && startY >= t.toInt() && startY < b.toInt()
                        }
                    }
                    val start = startX to startY
                    stableSamples = if (excluded && start == previousStart) stableSamples + 1 else 0
                    previousStart = if (excluded) start else null
                    stableSamples >= 2
                }
                shell("input swipe $startX $startY ${startX - delta} $startY 500")
                await { prefs.getInt("keyboard_width_preference", 100) < 80 }
                await { var ready = false; instrumentation.runOnMainSync { ready = page?.findViewById<GeometryPreview>(R.id.keyboard_layout_preview)?.adjustmentBounds() != null }; ready }
                var right = 0f
                instrumentation.runOnMainSync {
                    val preview = requireNotNull(page).findViewById<GeometryPreview>(R.id.keyboard_layout_preview)
                    val bounds = preview.adjustmentBounds()!!
                    assertEquals("right handle keeps the left edge fixed", left, bounds.left, 3f)
                    right = bounds.right
                    val handles = requireNotNull(page).findViewById<View>(R.id.keyboard_layout_handles)
                    val delta = 15 * context.resources.displayMetrics.density
                    val y = bounds.centerY() - 30 * context.resources.displayMetrics.density
                    touch(handles, MotionEvent.ACTION_DOWN, bounds.left, y)
                    touch(handles, MotionEvent.ACTION_MOVE, bounds.left + delta, y)
                    touch(handles, MotionEvent.ACTION_UP, bounds.left + delta, y)
                }
                await { var ready = false; instrumentation.runOnMainSync { ready = page?.findViewById<GeometryPreview>(R.id.keyboard_layout_preview)?.adjustmentBounds() != null }; ready }
                instrumentation.runOnMainSync {
                    val bounds = requireNotNull(page).findViewById<GeometryPreview>(R.id.keyboard_layout_preview).adjustmentBounds()!!
                    assertEquals("left handle keeps the right edge fixed", right, bounds.right, 3f)
                }
            }
        } finally { restore(prefs, original); if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme"); if (!enabled) shell("ime disable $ime") }
    }

    @Test fun resizeHitAreasFollowTheVisibleHandlesAtScreenEdges() {
        AppPreference.init(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.all.toMap()
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method")
        val enabled = shell("ime list -s").lineSequence().any { it.trim() == ime }
        try {
            shell("ime enable $ime"); shell("ime set $ime")
            prefs.edit().putInt("keyboard_height_preference", 220).putInt("keyboard_width_preference", 100)
                .putBoolean("keyboard_position_preference", false)
                .putInt("keyboard_margin_start_dp_preference", 0).putInt("keyboard_margin_end_dp_preference", 0)
                .putInt("keyboard_vertical_margin_bottom_preference", 0).commit()
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = it.isSettingsContentReady }; ready }
                scenario.onActivity { host(it).navController.navigate(R.id.keyboardSettingFragment) }
                var page: View? = null
                await { scenario.onActivity { page = host(it).childFragmentManager.primaryNavigationFragment?.view }; page?.findViewById<View>(R.id.keyboard_layout_edit_size) != null }
                instrumentation.runOnMainSync { requireNotNull(page).findViewById<View>(R.id.keyboard_layout_edit_size).performClick() }
                await { var ready = false; instrumentation.runOnMainSync { ready = (page?.findViewById<View>(R.id.keyboard_layout_handles)?.height ?: 0) > 0 }; ready }
                instrumentation.runOnMainSync {
                    val preview = requireNotNull(page).findViewById<GeometryPreview>(R.id.keyboard_layout_preview)
                    val bounds = preview.adjustmentBounds()!!
                    val handles = requireNotNull(page).findViewById<View>(R.id.keyboard_layout_handles)
                    val density = context.resources.displayMetrics.density
                    assertEquals(0f, bounds.left, 1f)
                    // The screen-edge grip remains draggable both across its depth and near its end.
                    val x = bounds.left + 47 * density
                    val y = bounds.centerY() + 30 * density
                    touch(handles, MotionEvent.ACTION_DOWN, x, y)
                    touch(handles, MotionEvent.ACTION_MOVE, x + 20 * density, y)
                    touch(handles, MotionEvent.ACTION_UP, x + 20 * density, y)
                }
                assertTrue("left edge responds throughout the visible handle's hit area", prefs.getInt("keyboard_width_preference", 100) < 100)
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    val preview = requireNotNull(page).findViewById<GeometryPreview>(R.id.keyboard_layout_preview)
                    val bounds = preview.adjustmentBounds()!!
                    val handles = requireNotNull(page).findViewById<View>(R.id.keyboard_layout_handles)
                    val density = context.resources.displayMetrics.density
                    val y = bounds.bottom - 47 * density
                    val x = bounds.centerX() + 30 * density
                    touch(handles, MotionEvent.ACTION_DOWN, x, y)
                    touch(handles, MotionEvent.ACTION_MOVE, x, y - 20 * density)
                    touch(handles, MotionEvent.ACTION_UP, x, y - 20 * density)
                }
                assertEquals("bottom edge responds throughout the visible handle's hit area", 200, prefs.getInt("keyboard_height_preference", 220))
            }
        } finally { restore(prefs, original); if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme"); if (!enabled) shell("ime disable $ime") }
    }

    @Test fun openingSwitchingAndDraggingUseOnlyTheSelectedSlot() {
        AppPreference.init(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.all.toMap()
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method")
        val enabled = shell("ime list -s").lineSequence().any { it.trim() == ime }
        try {
            shell("ime enable $ime"); shell("ime set $ime")
            prefs.edit().putInt("keyboard_height_preference", 220).putInt("keyboard_width_preference", 100)
                .putInt("keyboard_margin_start_dp_preference", 0).putInt("keyboard_margin_end_dp_preference", 0)
                .putInt("keyboard_vertical_margin_bottom_preference", 0).commit()
            val before = sizeEntries(prefs)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = it.isSettingsContentReady }; ready }
                scenario.onActivity { activity -> host(activity).navController.navigate(R.id.keyboardSettingFragment) }
                instrumentation.waitForIdleSync()
                var root: View? = null
                await { scenario.onActivity { root = host(it).childFragmentManager.primaryNavigationFragment?.view }; root?.findViewById<View>(R.id.keyboard_layout_edit_size) != null }
                instrumentation.runOnMainSync {
                    val page = requireNotNull(root)
                    val types = page.findViewById<RadioGroup>(R.id.keyboard_layout_type_selector)
                    types.check(2); types.check(1)
                    page.findViewById<RadioGroup>(R.id.keyboard_layout_tab_selector).check(2)
                    page.findViewById<RadioGroup>(R.id.keyboard_layout_tab_selector).check(1)
                }
                instrumentation.waitForIdleSync()
                assertEquals(before, sizeEntries(prefs))
                instrumentation.runOnMainSync { requireNotNull(root).findViewById<View>(R.id.keyboard_layout_edit_size).performClick() }
                await { var ready = false; instrumentation.runOnMainSync { ready = (root?.findViewById<View>(R.id.keyboard_layout_handles)?.height ?: 0) > 0 }; ready }
                instrumentation.runOnMainSync {
                    val page = requireNotNull(root)
                    assertFalse(page.findViewById<View>(R.id.keyboard_layout_type_selector).isShown)
                    assertTrue(page.findViewById<View>(R.id.keyboard_layout_edit_done).isShown)
                    val preview = page.findViewById<GeometryPreview>(R.id.keyboard_layout_preview)
                    val bounds = preview.adjustmentBounds()!!
                    val handles = page.findViewById<View>(R.id.keyboard_layout_handles)
                    val d = 20 * page.resources.displayMetrics.density
                    touch(handles, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.top)
                    touch(handles, MotionEvent.ACTION_MOVE, bounds.centerX(), bounds.top - d)
                    touch(handles, MotionEvent.ACTION_CANCEL, bounds.centerX(), bounds.top - d)
                }
                assertEquals(before, sizeEntries(prefs))
                instrumentation.runOnMainSync {
                    val page = requireNotNull(root)
                    val preview = page.findViewById<GeometryPreview>(R.id.keyboard_layout_preview)
                    val bounds = preview.adjustmentBounds()!!; val handles = page.findViewById<View>(R.id.keyboard_layout_handles)
                    val d = 20 * page.resources.displayMetrics.density
                    val x = bounds.centerX() - 30 * page.resources.displayMetrics.density
                    touch(handles, MotionEvent.ACTION_DOWN, x, bounds.top)
                    touch(handles, MotionEvent.ACTION_MOVE, x, bounds.top - d)
                    touch(handles, MotionEvent.ACTION_UP, x, bounds.top - d)
                }
                assertEquals(before + ("keyboard_height_preference" to 240), sizeEntries(prefs))
                instrumentation.runOnMainSync { requireNotNull(root).findViewById<View>(R.id.keyboard_layout_edit_done).performClick() }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync { requireNotNull(root).findViewById<View>(R.id.keyboard_layout_edit_position).performClick() }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    val page = requireNotNull(root); val preview = page.findViewById<GeometryPreview>(R.id.keyboard_layout_preview)
                    val bounds = preview.bodyBounds()!!; val location = IntArray(2); preview.getLocationOnScreen(location)
                    val pageLocation = IntArray(2); page.getLocationOnScreen(pageLocation)
                    assertTrue("keyboard stays within the screen", location[1] + bounds.bottom <= pageLocation[1] + page.height)
                    val handles = page.findViewById<View>(R.id.keyboard_layout_handles)
                    val x = bounds.left + bounds.width() * .25f; val y = bounds.centerY()
                    val d = 20 * page.resources.displayMetrics.density
                    touch(handles, MotionEvent.ACTION_DOWN, x, y)
                    touch(handles, MotionEvent.ACTION_MOVE, x, y - d)
                    touch(handles, MotionEvent.ACTION_UP, x, y - d)
                }
                val bottom = (20 * context.resources.displayMetrics.density).roundToInt()
                assertEquals(before + ("keyboard_height_preference" to 240) + ("keyboard_vertical_margin_bottom_preference" to bottom), sizeEntries(prefs))
            }
        } finally { restore(prefs, original); if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme"); if (!enabled) shell("ime disable $ime") }
    }

    @Test fun gojuonAndCustomPreviewUseTheSameKeyFramesAsTheirActualViews() {
        AppPreference.init(context)
        instrumentation.runOnMainSync {
            val themed = android.view.ContextThemeWrapper(context, R.style.Theme_MarkdownKeyboard)
            val density = context.resources.displayMetrics.density
            val screen = context.resources.displayMetrics.widthPixels
            val height = (220 * density).roundToInt()
            val size = IndependentKeyboardSize(heightDp = 220)
            val actualGojuon = GojuonKeyboardView(themed)
            ModeKeySizing.applyGojuon(actualGojuon)
            measure(actualGojuon, screen, height)
            val gojuon = GeometryPreview(themed, PreviewKeyboard.GOJUON)
            gojuon.update(KeyboardGeometryPreference(), false, 100, true, size)
            measurePreview(gojuon, screen)
            val frames = gojuon.snapshotForTesting()
            assertEquals(height.toFloat(), frames.bodyHeight, 1f)
            listOf(com.kazumaproject.gojuon_keyboard.R.id.key_1, com.kazumaproject.gojuon_keyboard.R.id.key_18,
                com.kazumaproject.gojuon_keyboard.R.id.key_51).forEach { id ->
                val actual = actualGojuon.findViewById<View>(id); val frame = frames.keys.first { it.id == id }
                assertEquals(actual.left + actual.width / 2f, frame.x, 2f)
                assertEquals(actual.top + actual.height / 2f, frame.y, 2f)
                assertEquals(actual.width / 2f, frame.halfWidth, 2f)
            }
            val a = KeyData("A", 0, 0, false, action = KeyAction.Text("A"), keyId = "a")
            val b = KeyData("B", 0, 1, false, action = KeyAction.Text("B"), keyId = "b")
            val layout = KeyboardLayout(keys = listOf(a, b), flickKeyMaps = emptyMap(), columnCount = 3, rowCount = 2,
                items = listOf(KeyItem("a", a, GridPlacement(0, 0, 2, 3)), SpacerItem("space", GridPlacement(0, 3, 2, 1)),
                    KeyItem("b", b, GridPlacement(0, 4, 4, 2))), columnUnitCount = 6, rowUnitCount = 4, isFlexiblePlacementLayout = true)
            val actualCustom = FlickKeyboardView(themed).apply {
                applyKeySizing(AppPreference.flick_key_width_scale_percent ?: 160, AppPreference.flick_key_height_scale_percent ?: 160,
                    AppPreference.flick_key_icon_scale_percent ?: 80, AppPreference.flick_key_text_size_sp ?: 16f,
                    AppPreference.flick_special_key_text_size_sp ?: 12f)
                setKeyboard(layout)
            }
            measure(actualCustom, screen, height)
            val custom = GeometryPreview(themed, PreviewKeyboard.CUSTOM)
            custom.update(KeyboardGeometryPreference(), false, 100, true, size, layout)
            measurePreview(custom, screen)
            val customFrames = custom.snapshotForTesting()
            assertEquals(2, customFrames.keys.size)
            listOf(0 to "A", 2 to "B").forEach { (index, label) ->
                val actual = actualCustom.getChildAt(index); val frame = customFrames.keys.first { it.text == label }
                assertEquals(actual.left + actual.width / 2f, frame.x, 2f)
                assertEquals(actual.top + actual.height / 2f, frame.y, 2f)
                assertEquals(actual.width / 2f, frame.halfWidth, 2f)
            }
        }
    }

    @Test fun selectingASavedCustomPreviewDoesNotChangeTheActiveLayout() {
        AppPreference.init(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.all.toMap()
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method")
        val enabled = shell("ime list -s").lineSequence().any { it.trim() == ime }
        var repository: KeyboardRepository? = null
        val created = mutableListOf<Long>()
        try {
            shell("ime enable $ime"); shell("ime set $ime")
            prefs.edit().putBoolean("remember_last_custom_keyboard_preference", true)
                .putString("last_used_custom_keyboard_stable_id", "preview-test-unchanged").commit()
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = it.isSettingsContentReady }; ready }
                scenario.onActivity { activity -> host(activity).navController.navigate(R.id.keyboardGeometrySettingsFragment,
                    Bundle().apply { putString("geometryKeyboard", "CUSTOM") }) }
                instrumentation.waitForIdleSync()
                var fragment: UnifiedKeyboardSettingsFragment? = null
                await { scenario.onActivity { fragment = host(it).childFragmentManager.primaryNavigationFragment as? UnifiedKeyboardSettingsFragment }; fragment != null }
                repository = requireNotNull(fragment).repository
                runBlocking {
                    listOf("Preview A", "Preview B").forEach { label ->
                        val key = KeyData(label, 0, 0, false, action = KeyAction.Text(label), keyId = label)
                        created += requireNotNull(repository).saveLayout(KeyboardLayout(listOf(key), emptyMap(), 1, 1), label, null)
                    }
                }
                await {
                    var found = false
                    instrumentation.runOnMainSync {
                        val spinner = fragment?.view?.findViewById<Spinner>(R.id.keyboard_layout_custom_selector)
                        found = spinner != null && (0 until spinner.count).any { spinner.getItemAtPosition(it) == "Preview A" } &&
                            (0 until spinner.count).any { spinner.getItemAtPosition(it) == "Preview B" }
                    }
                    found
                }
                instrumentation.runOnMainSync {
                    val spinner = requireNotNull(fragment?.view?.findViewById<Spinner>(R.id.keyboard_layout_custom_selector))
                    spinner.setSelection((0 until spinner.count).first { spinner.getItemAtPosition(it) == "Preview B" })
                }
                await {
                    var found = false
                    instrumentation.runOnMainSync {
                        val preview = fragment?.view?.findViewById<GeometryPreview>(R.id.keyboard_layout_preview)
                        found = runCatching { preview?.snapshotForTesting()?.keys?.any { it.text == "Preview B" } == true }.getOrDefault(false)
                    }
                    found
                }
                // The active IME may restore its own last layout while the screen opens.
                // Compare immediately across a real preview selection instead.
                val activeBeforeSelection = AppPreference.last_used_custom_keyboard_stable_id
                instrumentation.runOnMainSync {
                    val spinner = requireNotNull(fragment?.view?.findViewById<Spinner>(R.id.keyboard_layout_custom_selector))
                    spinner.setSelection((0 until spinner.count).first { spinner.getItemAtPosition(it) == "Preview A" })
                }
                await {
                    var found = false
                    instrumentation.runOnMainSync {
                        val preview = fragment?.view?.findViewById<GeometryPreview>(R.id.keyboard_layout_preview)
                        found = runCatching { preview?.snapshotForTesting()?.keys?.any { it.text == "Preview A" } == true }.getOrDefault(false)
                    }
                    found
                }
                assertEquals(activeBeforeSelection, AppPreference.last_used_custom_keyboard_stable_id)
            }
            val chosen = runBlocking { requireNotNull(repository).getLayoutsNotFlow().first { it.layoutId == created.first() } }
            prefs.edit().putString("last_used_custom_keyboard_stable_id", chosen.stableId).commit()
            IndependentKeyboardSizePreferences(prefs).save(IndependentKeyboardSizeTarget.CUSTOM, false,
                IndependentKeyboardSize(heightDp = 180, widthPercent = 70))
            for (floating in listOf(false, true)) verifyImeSize(prefs, ime, "CUSTOM", floating)
        } finally {
            runBlocking { created.forEach { repository?.deleteLayoutConfirmed(it) } }
            restore(prefs, original); if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme"); if (!enabled) shell("ime disable $ime")
        }
    }

    @Test fun independentGojuonSizeIsAppliedByTheDockedAndFloatingIme() {
        AppPreference.init(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.all.toMap()
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method")
        val enabled = shell("ime list -s").lineSequence().any { it.trim() == ime }
        try {
            IndependentKeyboardSizePreferences(prefs).save(IndependentKeyboardSizeTarget.GOJUON, false,
                IndependentKeyboardSize(heightDp = 180, widthPercent = 70))
            for (floating in listOf(false, true)) verifyImeSize(prefs, ime, "GOJUON", floating)
        } finally { restore(prefs, original); if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme"); if (!enabled) shell("ime disable $ime") }
    }

    @Test fun positionEditorShowsTheKeyboardAtTheRealDockedImeScreenPosition() {
        AppPreference.init(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.all.toMap()
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method")
        val enabled = shell("ime list -s").lineSequence().any { it.trim() == ime }
        var host: FastInputHostActivity? = null
        try {
            shell("ime disable $ime")
            prefs.edit().putString("keyboard_order_preference", "[\"TENKEY\"]")
                .putBoolean("save_last_used_keyboard", false).putBoolean("keyboard_floating_preference", false)
                .putInt("keyboard_height_preference", 220).putInt("keyboard_width_preference", 75)
                .putInt("keyboard_vertical_margin_bottom_preference", 73)
                .putInt("keyboard_margin_start_dp_preference", 10).putInt("keyboard_margin_end_dp_preference", 15)
                .putBoolean("keyboard_position_preference", true)
                .putInt("keyboard_geometry_tenkey_portrait_curve", 0)
                .putString("keyboard_geometry_tenkey_portrait_candidates", "TOP").commit()
            shell("ime enable $ime"); shell("am start -n ${context.packageName}/${FastInputHostActivity::class.java.name}")
            await {
                instrumentation.runOnMainSync {
                    host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<FastInputHostActivity>().firstOrNull()?.takeIf { it.hasWindowFocus() }
                }
                host != null
            }
            shell("ime set $ime"); instrumentation.runOnMainSync { requireNotNull(host).restartEditorInput(clearText = true) }
            var keyboard: View? = null
            await {
                instrumentation.runOnMainSync {
                    keyboard = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { root ->
                        root.findViewById<View>(R.id.keyboard_view)?.takeIf { it.isShown && it.height > 0 }
                    }
                }
                keyboard != null
            }
            SystemClock.sleep(600)
            var actualBottom = 0; var actualLeft = 0; var actualWidth = 0
            instrumentation.runOnMainSync {
                val key = requireNotNull(keyboard); val location = IntArray(2); key.getLocationOnScreen(location)
                actualBottom = location[1] + key.height
                actualLeft = location[0]; actualWidth = key.width
                host?.finish(); host = null
            }
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = it.isSettingsContentReady }; ready }
                scenario.onActivity { this@UnifiedKeyboardSettingsDeviceTest.host(it).navController.navigate(R.id.keyboardSettingFragment) }
                var page: View? = null
                await { scenario.onActivity { page = this@UnifiedKeyboardSettingsDeviceTest.host(it).childFragmentManager.primaryNavigationFragment?.view }; page?.findViewById<View>(R.id.keyboard_layout_edit_position) != null }
                instrumentation.runOnMainSync { requireNotNull(page).findViewById<View>(R.id.keyboard_layout_edit_position).performClick() }
                await {
                    var ready = false
                    instrumentation.runOnMainSync { ready = (page?.findViewById<View>(R.id.keyboard_layout_handles)?.height ?: 0) > 0 }
                    ready
                }
                SystemClock.sleep(800) // Wait for the ActionBar hide animation to finish.
                instrumentation.runOnMainSync {
                    val preview = requireNotNull(page).findViewById<GeometryPreview>(R.id.keyboard_layout_preview)
                    val location = IntArray(2); preview.getLocationOnScreen(location)
                    val bounds = preview.bodyBounds()!!
                    assertEquals("bottom", actualBottom.toFloat(), location[1] + bounds.bottom, 3f)
                    assertEquals("left", actualLeft.toFloat(), location[0] + bounds.left, 3f)
                    assertEquals("width", actualWidth.toFloat(), bounds.width(), 3f)
                }
            }
        } finally { instrumentation.runOnMainSync { host?.finish() }; restore(prefs, original); if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme"); if (!enabled) shell("ime disable $ime") }
    }

    @Test fun numericCardKeepsAnUntouchedLegacyPixelMargin() {
        AppPreference.init(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.all.toMap()
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method")
        val enabled = shell("ime list -s").lineSequence().any { it.trim() == ime }
        try {
            shell("ime enable $ime"); shell("ime set $ime")
            prefs.edit().putInt("keyboard_vertical_margin_bottom_preference", 73).commit()
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = it.isSettingsContentReady }; ready }
                scenario.onActivity { host(it).navController.navigate(R.id.keyboardSettingFragment) }
                var page: View? = null
                await { scenario.onActivity { page = host(it).childFragmentManager.primaryNavigationFragment?.view }; page?.findViewById<View>(R.id.keyboard_layout_more) != null }
                instrumentation.runOnMainSync { requireNotNull(page).findViewById<View>(R.id.keyboard_layout_more).performClick() }
                onView(withText(R.string.keyboard_layout_exact_values)).perform(click())
                onView(withId(R.id.keyboard_layout_size_input)).inRoot(isDialog()).check(matches(isDisplayed()))
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                assertEquals(73, prefs.getInt("keyboard_vertical_margin_bottom_preference", -1))
            }
        } finally { restore(prefs, original); if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme"); if (!enabled) shell("ime disable $ime") }
    }

    @Test fun orientationSelectorUsesAnUnscaledLandscapePreview() {
        AppPreference.init(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.all.toMap()
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method")
        val enabled = shell("ime list -s").lineSequence().any { it.trim() == ime }
        try {
            shell("ime enable $ime"); shell("ime set $ime")
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                await { var ready = false; scenario.onActivity { ready = it.isSettingsContentReady }; ready }
                scenario.onActivity { host(it).navController.navigate(R.id.keyboardSettingFragment) }
                var page: View? = null
                await { scenario.onActivity { page = host(it).childFragmentManager.primaryNavigationFragment?.view }; page?.findViewById<View>(R.id.keyboard_layout_orientation_selector) != null }
                instrumentation.runOnMainSync { requireNotNull(page).findViewById<RadioGroup>(R.id.keyboard_layout_orientation_selector).check(2) }
                await {
                    var ready = false
                    scenario.onActivity {
                        page = if (it.isSettingsContentReady) host(it).childFragmentManager.primaryNavigationFragment?.view else null
                        ready = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                            (page?.findViewById<View>(R.id.keyboard_layout_preview)?.width ?: 0) > 0
                    }
                    ready
                }
                instrumentation.runOnMainSync {
                    val preview = requireNotNull(page).findViewById<GeometryPreview>(R.id.keyboard_layout_preview)
                    assertEquals(preview.snapshotForTesting().screenWidth, preview.width.toFloat(), 1f)
                    assertEquals(1f, preview.scaleX, 0f); assertEquals(1f, preview.scaleY, 0f)
                }
            }
        } finally { restore(prefs, original); if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme"); if (!enabled) shell("ime disable $ime") }
    }

    private fun verifyImeSize(prefs: SharedPreferences, ime: String, type: String, floating: Boolean) {
        var host: FastInputHostActivity? = null
        try {
            shell("ime disable $ime")
            await { shell("settings get secure default_input_method") != ime }
            // The previous bound service is destroyed asynchronously after the selection changes.
            SystemClock.sleep(750)
            prefs.edit().putString("keyboard_order_preference", "[\"$type\"]")
                .putBoolean("save_last_used_keyboard", false).putBoolean("keyboard_floating_preference", floating)
                .putInt("keyboard_height_preference", 300).putInt("keyboard_width_preference", 100).commit()
            shell("ime enable $ime")
            await { shell("ime list -s").lineSequence().any { it.trim() == ime } }
            shell("am start -n ${context.packageName}/${FastInputHostActivity::class.java.name}")
            await {
                instrumentation.runOnMainSync {
                    host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<FastInputHostActivity>().firstOrNull()?.takeIf { it.hasWindowFocus() }
                }
                host != null
            }
            shell("ime set $ime")
            await { shell("settings get secure default_input_method") == ime }
            instrumentation.runOnMainSync {
                val activity = requireNotNull(host)
                activity.restartEditorInput(clearText = true)
                activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    .showSoftInput(activity.editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            var surface: View? = null
            val id = when {
                type == "GOJUON" && floating -> R.id.gojuon_view_floating
                type == "GOJUON" -> R.id.gojuon_view
                floating -> R.id.custom_layout_floating
                else -> R.id.custom_layout_default
            }
            await {
                instrumentation.runOnMainSync {
                    surface = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { root ->
                        root.findViewById<View>(id)?.takeIf { it.isShown && it.width > 0 && it.height > 0 }
                    }
                }
                surface != null
            }
            SystemClock.sleep(600)
            instrumentation.runOnMainSync {
                val keyboard = requireNotNull(surface)
                var visualWidth = keyboard.width.toFloat(); var visualHeight = keyboard.height.toFloat()
                var ancestor = keyboard.parent as? View
                while (ancestor != null) {
                    visualWidth *= ancestor.scaleX; visualHeight *= ancestor.scaleY
                    ancestor = ancestor.parent as? View
                }
                val density = context.resources.displayMetrics.density
                assertEquals("$type floating=$floating height", 180 * density, visualHeight, 3f)
                // Floating chrome reserves 8dp on each side inside the configured window width.
                val expectedWidth = context.resources.displayMetrics.widthPixels * .7f - if (floating) 16 * density else 0f
                assertEquals("$type floating=$floating width", expectedWidth, visualWidth, 6f)
            }
        } finally { instrumentation.runOnMainSync { host?.finish() } }
    }

    private fun host(activity: MainActivity) = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
    private fun measure(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }
    private fun measurePreview(view: View, width: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, width, view.measuredHeight)
    }
    private fun touch(view: View, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0, SystemClock.uptimeMillis(), action, x, y, 0)
        try { view.dispatchTouchEvent(event) } finally { event.recycle() }
    }
    private fun sizeEntries(prefs: SharedPreferences) = prefs.all.filterKeys { key ->
        (key.startsWith("keyboard_") || key.startsWith("qwerty_keyboard_") || key.startsWith("independent_keyboard_size_")) &&
            listOf("height", "width", "position", "margin", "bottom", "start", "end").any(key::contains)
    }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < deadline) { if (condition()) return; SystemClock.sleep(100) }
        error("Timed out waiting for the settings screen")
    }
    private fun restore(prefs: SharedPreferences, original: Map<String, *>) {
        val edit = prefs.edit().clear()
        original.forEach { (key, value) -> when (value) {
            is Boolean -> edit.putBoolean(key, value); is Int -> edit.putInt(key, value); is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value); is String -> edit.putString(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }
        edit.commit()
    }
}
