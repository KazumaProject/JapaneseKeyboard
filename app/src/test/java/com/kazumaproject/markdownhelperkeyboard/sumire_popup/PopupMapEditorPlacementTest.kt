package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.kazumaproject.custom_keyboard.data.PopupBounds
import com.kazumaproject.custom_keyboard.data.PopupKeyConfig
import com.kazumaproject.custom_keyboard.data.PopupKeyConfigCodec
import com.kazumaproject.custom_keyboard.data.PopupMap
import com.kazumaproject.custom_keyboard.data.PopupRegion
import com.kazumaproject.custom_keyboard.data.SumirePopupDefaults
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.CustomFreeFlickEditorDialog
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-mdpi")
class PopupMapEditorPlacementTest {
    @Test fun customKeyEditorCanDropAFullSizePopupThreeRowsBelowTap() = checkDrop(customKey = true)
    @Test fun sumireEditorCanDropAFullSizePopupThreeRowsBelowTap() = checkDrop(customKey = false)

    @Test fun addPopupFillsThreeRowsAboveAndBelowTap() = withEditor(
        customKey = false,
        regions = emptyList(),
    ) { editor ->
        val add = button(editor.requireView(), R.string.popup_add)
        val expected = (-3..3).flatMap { row ->
            (-1..1).map { column -> PopupBounds.KEY.offset(column.toFloat(), row.toFloat()) }
        }.filterNot { it == PopupBounds.KEY }.toSet()

        repeat(expected.size) { add.performClick() }

        assertEquals(expected, config(editor).maps.single().regions.map { it.bounds }.toSet())
    }

    private fun checkDrop(customKey: Boolean) {
        val side = PopupBounds.KEY.offset(-1.1f, 0f)
        withEditor(customKey, listOf(PopupRegion("side", side))) { editor ->
            val root = editor.requireView()
            val canvas = descendants(root).filterIsInstance<PopupMapEditorCanvas>().single()
            val offset = IntArray(2).also(canvas::getLocationInWindow)
            val rootLocation = IntArray(2).also(root::getLocationInWindow)
            val downTime = SystemClock.uptimeMillis()
            fun touch(action: Int, x: Float, y: Float) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                    offset[0] - rootLocation[0] + canvas.originX + x * canvas.keyWidth,
                    offset[1] - rootLocation[1] + canvas.originY + y * canvas.keyHeight, 0)
                root.dispatchTouchEvent(event)
                event.recycle()
            }

            touch(MotionEvent.ACTION_DOWN, -1.1f, 0f)
            touch(MotionEvent.ACTION_MOVE, -1.1f, 3f)
            touch(MotionEvent.ACTION_MOVE, 0f, 3f)
            touch(MotionEvent.ACTION_UP, 0f, 3f)

            val placed = config(editor).maps.single().regions.single().bounds
            assertTrue("The popup must fit in the third row below the tap key", placed.top >= 2.5f - .0001f)
            assertEquals(PopupBounds.KEY.left, placed.left, .0001f)
            assertEquals(side.width, placed.width, .0001f)
            assertEquals(side.height, placed.height, .0001f)
            for (label in listOf(R.string.popup_add, R.string.popup_undo)) {
                val control = button(root, label)
                val buttonTop = IntArray(2).also(control::getLocationInWindow)[1]
                assertTrue("${control.text} must be above the canvas", buttonTop + control.height <= offset[1])
            }
        }
    }

    private fun withEditor(customKey: Boolean, regions: List<PopupRegion>, check: (PopupMapEditorFragment) -> Unit) {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        controller.get().setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        controller.setup()
        try {
            val activity = controller.get()
            AppPreference.init(activity)
            val config = PopupKeyConfig("は", "normal", listOf(PopupMap("normal", "Normal", "は", regions)))
            val editor = if (customKey) {
                val dialog = CustomFreeFlickEditorDialog.create(PopupKeyConfigCodec.encode(config))
                dialog.showNow(activity.supportFragmentManager, "placement-editor")
                dialog.childFragmentManager.findFragmentById(R.id.custom_free_flick_editor_host) as PopupMapEditorFragment
            } else {
                AppPreference.sumire_keyboard_style = SumirePopupDefaults.STYLE
                val layout = FreeFlickLayoutRepository(activity).create("Placement test")
                SumirePopupRepository(activity).save(config, layout.id)
                PopupMapEditorFragment().apply {
                    arguments = bundleOf(FreeFlickLayoutRepository.ARG_LAYOUT_ID to layout.id)
                    activity.supportFragmentManager.beginTransaction().replace(android.R.id.content, this).commitNow()
                }
            }
            shadowOf(Looper.getMainLooper()).idle()
            val root = editor.requireView()
            fun exact(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
            root.measure(exact(360), exact(800))
            root.layout(0, 0, 360, 800)
            check(editor)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun button(root: View, label: Int) = descendants(root).filterIsInstance<MaterialButton>()
        .single { it.text == root.context.getString(label) }

    private fun config(editor: PopupMapEditorFragment) =
        requireNotNull(ViewModelProvider(editor)[PopupMapEditorViewModel::class.java].config)

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
