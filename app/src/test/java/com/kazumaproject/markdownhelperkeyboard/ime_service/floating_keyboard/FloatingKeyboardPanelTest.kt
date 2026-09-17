package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_keyboard

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.GuideHandle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 30])
class FloatingKeyboardPanelTest {
    @Test fun contentAndHandlesRemainUsableAcrossEditing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.setTheme(R.style.Theme_MarkdownKeyboard)
        val binding = FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(context))
        val panel = FloatingKeyboardPanel(binding, {}, { _, _, _ -> })
        fun layout() {
            panel.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.AT_MOST))
            panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        }
        layout()
        assertEquals(View.GONE, binding.dragHandle.visibility)
        assertTrue(binding.floatingKeyboardContainer.height > 0)
        assertEquals(GuideHandle.MOVE, panel.handleAt(450f, panel.height - 2f))
        panel.setEditing(true)
        layout()
        assertNull(panel.handleAt(450f, panel.height - 2f))
        assertEquals(GuideHandle.TOP, panel.handleAt(450f, 2f))
        assertTrue(binding.floatingKeyboardContainer.height > 0)
        panel.setEditing(false)
        layout()
        assertEquals(GuideHandle.MOVE, panel.handleAt(450f, panel.height - 2f))
    }
    @Test
    @Config(qualifiers = "w500dp-h1000dp-mdpi")
    fun cancelledMoveRestoresOriginWithoutPersisting() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        activity.setTheme(R.style.Theme_MarkdownKeyboard)
        val binding = FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(activity))
        val positions = mutableListOf<Triple<Int, Int, Boolean>>()
        val panel = FloatingKeyboardPanel(binding, {}, { x, y, persist -> positions.add(Triple(x, y, persist)) })
        activity.setContentView(panel)
        panel.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST))
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        val origin = IntArray(2).also(panel::getLocationOnScreen)
        val y = panel.height - 2f
        fun send(action: Int, dy: Float) {
            val event = android.view.MotionEvent.obtain(0, 0, action, 150f, y + dy, 0)
            panel.dispatchTouchEvent(event)
            event.recycle()
        }
        send(android.view.MotionEvent.ACTION_DOWN, 0f)
        send(android.view.MotionEvent.ACTION_MOVE, 20f)
        send(android.view.MotionEvent.ACTION_CANCEL, 20f)
        assertTrue(positions.isNotEmpty())
        assertTrue(positions.none { it.third })
        assertEquals(Triple(origin[0], origin[1], false), positions.last())
        activity.finish()
    }

}
