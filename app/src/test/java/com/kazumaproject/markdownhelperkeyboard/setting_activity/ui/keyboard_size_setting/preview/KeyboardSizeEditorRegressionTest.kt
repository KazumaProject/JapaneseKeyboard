package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.preview

import android.content.SharedPreferences
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
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
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()
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
    fun returningFromNumericInputRestoresTheLogicalCanvasForBothEditors() {
        listOf(R.id.keyboardSettingFragment, R.id.keyboardSizeLandscapeFragment).forEach { destination ->
            val root = openEditor(destination)
            val viewport = root.findViewById<KeyboardPreviewViewport>(R.id.keyboard_preview_viewport)
            val width = viewport.logicalCanvasWidth
            val height = viewport.logicalCanvasHeight
            assertTrue(width > 1 && height > 1)
            val originalOrientation = activity.requestedOrientation
            host.navController.navigate(R.id.keyboardSizeDirectInputFragment)
            drainNavigation()
            assertTrue(host.navController.popBackStack())
            drainNavigation()
            val restoredRoot = editorRoot()
            layoutEditor(restoredRoot)
            val restored = restoredRoot.findViewById<KeyboardPreviewViewport>(R.id.keyboard_preview_viewport)
            assertEquals(width, restored.logicalCanvasWidth)
            assertEquals(height, restored.logicalCanvasHeight)
            assertEquals(originalOrientation, activity.requestedOrientation)
        }
    }

    @Test
    fun scaledHeightDragSavesOnlyTheLandscapeTenKeyHeightWithoutWaitingForLayout() {
        AppPreference.keyboard_height_landscape = 220
        AppPreference.keyboard_vertical_margin_bottom_landscape = 0
        val root = openEditor(R.id.keyboardSizeLandscapeFragment)
        val before = sizePreferences()
        val viewport = root.findViewById<KeyboardPreviewViewport>(R.id.keyboard_preview_viewport)
        val keyboard = root.findViewById<View>(R.id.keyboard_container)
        val handle = root.findViewById<View>(R.id.handle_top)
        val initialHeight = keyboard.layoutParams.height
        val density = root.resources.displayMetrics.density
        val distance = 20f
        val expected = ((initialHeight + (distance / viewport.scale).roundToInt()) / density).roundToInt()

        touch(handle, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(handle, MotionEvent.ACTION_MOVE, 100f, 100f - distance)
        touch(handle, MotionEvent.ACTION_UP, 100f, 100f - distance)

        assertEquals(expected, AppPreference.keyboard_height_landscape)
        assertEquals(before + ("keyboard_height_landscape_preference" to expected), sizePreferences())
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

    private fun layoutEditor(root: View) {
        val metrics = root.resources.displayMetrics
        root.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY)
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
