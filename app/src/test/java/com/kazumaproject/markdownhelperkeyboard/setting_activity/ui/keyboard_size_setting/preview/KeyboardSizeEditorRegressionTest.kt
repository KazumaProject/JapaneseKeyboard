package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.preview

import android.content.SharedPreferences
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizeKeyboardType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizeOrientation
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizePreferenceAccessor
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizeValues
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w420dp-h900dp-port")
class KeyboardSizeEditorRegressionTest {
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var activity: MainActivity
    private lateinit var host: NavHostFragment
    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        AppPreference.init(ApplicationProvider.getApplicationContext<Context>())
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()
        awaitSettingsReady()
        preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        AppPreference.init(activity)
        host = activity.supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        drainNavigation()
        val accessor = KeyboardSizePreferenceAccessor(AppPreference)
        var slot = 0
        KeyboardSizeOrientation.values().forEach { orientation ->
            KeyboardSizeKeyboardType.values().forEach { keyboardType ->
                accessor.save(
                    orientation, keyboardType,
                    KeyboardSizeValues(200 + slot * 30, 32 + slot * 15, slot * 300, 1000 + slot, 1200 + slot)
                )
                slot++
            }
        }
        // Absent alignment keys must stay absent, even when displaying the default alignment.
        preferences.edit()
            .remove("keyboard_position_preference")
            .remove("qwerty_keyboard_position_preference")
            .remove("keyboard_position_landscape_preference")
            .remove("qwerty_keyboard_position_landscape_preference")
            .commit()
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized) controller.pause().stop().destroy()
    }

    @Test
    fun openingSwitchingTappingAndCancellingPreserveAllFourPreferenceSlots() {
        assertReadOnlyOperations()
    }

    @Test
    @Config(qualifiers = "w900dp-h420dp-land")
    fun oppositeDeviceOrientationAlsoPreservesPreferences() {
        assertReadOnlyOperations()
    }

    @Test
    fun returningFromNumericInputRestoresDimensionsForBothEditors() {
        listOf(R.id.keyboardSettingFragment, R.id.keyboardSizeLandscapeFragment).forEach { destination ->
            val previousOrientation = activity.requestedOrientation
            val root = openEditor(destination)
            val keyboard = root.findViewById<View>(R.id.keyboard_container)
            val width = keyboard.width
            val height = keyboard.height
            assertTrue(width > 1 && height > 1)
            val originalOrientation = activity.requestedOrientation
            host.navController.navigate(R.id.keyboardSizeDirectInputFragment)
            drainNavigation()
            assertTrue(host.navController.popBackStack())
            drainNavigation()
            val restoredRoot = editorRoot()
            layoutEditor(restoredRoot)
            val restored = restoredRoot.findViewById<View>(R.id.keyboard_container)
            assertEquals(width, restored.width)
            assertEquals(height, restored.height)
            assertEquals(originalOrientation, activity.requestedOrientation)
            assertTrue(host.navController.popBackStack())
            drainNavigation()
            assertEquals(previousOrientation, activity.requestedOrientation)
        }
    }

    @Test
    fun heightDragAcrossLayoutsSavesOnlyTheLandscapeTenKeyHeight() {
        AppPreference.keyboard_height_landscape = 220
        AppPreference.keyboard_vertical_margin_bottom_landscape = 0
        val root = openEditor(R.id.keyboardSizeLandscapeFragment)
        val before = sizePreferences()
        val keyboard = root.findViewById<View>(R.id.keyboard_container)
        val handle = root.findViewById<View>(R.id.handle_top)
        val initialHeight = keyboard.layoutParams.height
        val density = root.resources.displayMetrics.density
        val distance = 20f
        val expected = ((initialHeight + distance.roundToInt()) / density).roundToInt()

        touch(handle, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(handle, MotionEvent.ACTION_MOVE, 100f, 100f - distance)
        layoutEditor(root)
        touch(handle, MotionEvent.ACTION_UP, 100f, 100f - distance)

        assertEquals(expected, AppPreference.keyboard_height_landscape)
        assertEquals(before + ("keyboard_height_landscape_preference" to expected), sizePreferences())
    }

    @Test
    fun bothEditorsUseFullAvailableWidthAndUnscaledHeight() {
        assertFullSizePreview()
    }

    @Test
    @Config(qualifiers = "w900dp-h420dp-land")
    fun bothEditorsStayUnscaledOnALandscapeDevice() {
        assertFullSizePreview()
    }

    @Test
    fun widthDragStartsAtFullWidthAndSavesOnlyThePortraitQwertyWidth() {
        AppPreference.qwerty_keyboard_width = 100
        AppPreference.qwerty_keyboard_vertical_margin_bottom = 0
        AppPreference.qwerty_keyboard_margin_start_dp = 0
        AppPreference.qwerty_keyboard_margin_end_dp = 0
        val root = openEditor(R.id.keyboardSettingFragment)
        root.findViewById<View>(R.id.qwerty_tooltip_button).performClick()
        drainNavigation()
        layoutEditor(root)
        val before = sizePreferences()
        val keyboard = root.findViewById<View>(R.id.keyboard_container)
        val handle = root.findViewById<View>(R.id.handle_right)
        val initialWidth = keyboard.width
        val distance = (40f * root.resources.displayMetrics.density).roundToInt()
        val expected = KeyboardPreviewGeometry.percentForWidth(initialWidth - distance, initialWidth)

        touch(handle, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(handle, MotionEvent.ACTION_MOVE, 100f - distance, 100f)
        layoutEditor(root)
        touch(handle, MotionEvent.ACTION_UP, 100f - distance, 100f)

        assertEquals(expected, AppPreference.qwerty_keyboard_width)
        assertEquals(before + ("qwerty_keyboard_width_preference" to expected), sizePreferences())
    }

    @Test
    fun closingEachEditorRestoresTheOriginalOrientationRequest() {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        listOf(
            R.id.keyboardSettingFragment to ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            R.id.keyboardSizeLandscapeFragment to ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        ).forEach { (destination, expectedOrientation) ->
            openEditor(destination)
            assertEquals(expectedOrientation, activity.requestedOrientation)
            assertTrue(host.navController.popBackStack())
            drainNavigation()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, activity.requestedOrientation)
        }
    }

    @Test
    fun recreationKeepsTheOriginalOrientationForWhenTheEditorCloses() {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        openEditor(R.id.keyboardSizeLandscapeFragment)
        controller.recreate()
        activity = controller.get()
        awaitSettingsReady()
        host = activity.supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        drainNavigation()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, activity.requestedOrientation)
        assertTrue(host.navController.popBackStack())
        drainNavigation()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, activity.requestedOrientation)
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-420dpi")
    fun previewUsesNormalImePixelDimensionsAndBothSideMargins() {
        AppPreference.keyboard_height = 221
        AppPreference.keyboard_width = 100
        AppPreference.keyboard_margin_start_dp = 12
        AppPreference.keyboard_margin_end_dp = 20
        AppPreference.keyboard_vertical_margin_bottom = 30
        val before = sizePreferences()
        val root = openEditor(R.id.keyboardSettingFragment)
        val keyboard = root.findViewById<View>(R.id.keyboard_container)
        val parent = root.findViewById<View>(R.id.keyboard_setting_constraint)
        val density = root.resources.displayMetrics.density
        val lp = keyboard.layoutParams as ViewGroup.MarginLayoutParams
        assertEquals((221 * density).toInt(), keyboard.height)
        assertEquals(parent.width - (12 * density).toInt() - (20 * density).toInt(), keyboard.width)
        assertEquals((12 * density).toInt(), lp.marginStart)
        assertEquals((20 * density).toInt(), lp.marginEnd)
        assertEquals(30, lp.bottomMargin)
        assertEquals(before, sizePreferences())
    }

    private fun assertFullSizePreview() {
        val accessor = KeyboardSizePreferenceAccessor(AppPreference)
        KeyboardSizeOrientation.values().forEach { orientation ->
            KeyboardSizeKeyboardType.values().forEach { keyboardType ->
                accessor.save(orientation, keyboardType, KeyboardSizeValues(220, 100, 0, 0, 0))
            }
        }
        val before = sizePreferences()
        listOf(R.id.keyboardSettingFragment, R.id.keyboardSizeLandscapeFragment).forEach { destination ->
            val root = openEditor(destination)
            listOf(R.id.tenkey_tooltip_button, R.id.qwerty_tooltip_button).forEach { button ->
                root.findViewById<View>(button).performClick()
                drainNavigation()
                layoutEditor(root)
                val parent = root.findViewById<View>(R.id.keyboard_setting_constraint)
                val keyboard = root.findViewById<View>(R.id.keyboard_container)
                assertEquals(parent.width - parent.paddingLeft - parent.paddingRight, keyboard.width)
                assertEquals((220 * root.resources.displayMetrics.density).toInt(), keyboard.height)
                var ancestor: View? = keyboard
                while (ancestor != null) {
                    assertEquals(1f, ancestor.scaleX, 0f)
                    assertEquals(1f, ancestor.scaleY, 0f)
                    ancestor = ancestor.parent as? View
                }
            }
        }
        assertEquals(before, sizePreferences())
    }

    private fun assertReadOnlyOperations() {
        val before = sizePreferences()
        val originalOrientation = activity.requestedOrientation
        listOf(R.id.keyboardSettingFragment, R.id.keyboardSizeLandscapeFragment).forEach { destination ->
            val root = openEditor(destination)
            listOf(R.id.tenkey_tooltip_button, R.id.qwerty_tooltip_button).forEach { pageButton ->
                root.findViewById<View>(pageButton).performClick()
                drainNavigation()
                layoutEditor(root)
                listOf(R.id.handle_top, R.id.handle_bottom, R.id.handle_left, R.id.handle_right, R.id.handle_move)
                    .forEach { handleId ->
                        val handle = root.findViewById<View>(handleId)
                        touch(handle, MotionEvent.ACTION_DOWN, 100f, 100f)
                        touch(handle, MotionEvent.ACTION_UP, 100f, 100f)
                        touch(handle, MotionEvent.ACTION_DOWN, 100f, 100f)
                        touch(handle, MotionEvent.ACTION_MOVE, 110f, 90f)
                        touch(handle, MotionEvent.ACTION_CANCEL, 110f, 90f)
                    }
                assertEquals(before, sizePreferences())
            }
            assertTrue(host.navController.popBackStack())
            drainNavigation()
            assertEquals(originalOrientation, activity.requestedOrientation)
        }
        assertEquals(originalOrientation, activity.requestedOrientation)
    }

    private fun openEditor(destination: Int): View {
        host.navController.navigate(destination)
        drainNavigation()
        return editorRoot().also(::layoutEditor)
    }

    private fun editorRoot(): View = host.childFragmentManager.primaryNavigationFragment!!.requireView()

    private fun drainNavigation() {
        activity.supportFragmentManager.executePendingTransactions()
        host.childFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun awaitSettingsReady() {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!activity.isSettingsContentReady && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Settings content did not finish loading", activity.isSettingsContentReady)
    }

    private fun layoutEditor(root: View) {
        val metrics = root.resources.displayMetrics
        root.measure(
            View.MeasureSpec.makeMeasureSpec(root.width.takeIf { it > 0 } ?: metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(root.height.takeIf { it > 0 } ?: metrics.heightPixels, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun sizePreferences(): Map<String, Any?> = preferences.all.filterKeys { key ->
        (key.startsWith("keyboard_") || key.startsWith("qwerty_keyboard_")) &&
            listOf("height", "width", "position", "margin").any { it in key }
    }

    private fun touch(handle: View, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0, 0, action, x, y, 0)
        try {
            handle.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
