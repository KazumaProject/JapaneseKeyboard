package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.preview

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardPreviewViewportTest {

    @Test
    fun measuresLogicalCanvasToBoundedScaleAndKeepsResizeStripsReachable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val density = context.resources.displayMetrics.density
        val themedContext = ContextThemeWrapper(context, R.style.Theme_MarkdownKeyboard)
        val root = LayoutInflater.from(themedContext)
            .inflate(R.layout.fragment_keyboard_setting, null, false)
        val viewport = root.findViewById<KeyboardPreviewViewport>(R.id.keyboard_preview_viewport)
        viewport.setLogicalCanvasSize((900 * density).toInt(), (420 * density).toInt())
        val keyboard = root.findViewById<View>(R.id.keyboard_container)
        val keyboardParams = keyboard.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        keyboardParams.width = (900 * density).toInt()
        keyboardParams.height = (100 * density).toInt()
        keyboardParams.bottomMargin = 0
        keyboard.layoutParams = keyboardParams

        val maxHeightPx = (300 * density).toInt()
        viewport.measure(
            View.MeasureSpec.makeMeasureSpec((420 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.AT_MOST)
        )
        viewport.layout(0, 0, viewport.measuredWidth, viewport.measuredHeight)

        val canvas = viewport.findViewById<ViewGroup>(R.id.keyboard_preview_canvas)
        assertTrue(viewport.scale < 1f)
        assertTrue(viewport.measuredHeight <= maxHeightPx)
        assertEquals((900 * density).toInt(), canvas.width)
        assertEquals((420 * density).toInt(), canvas.height)
        assertEquals(viewport.scale, canvas.scaleX, 0.0001f)
        assertEquals(viewport.scale, canvas.scaleY, 0.0001f)

        val top = canvas.findViewById<View>(R.id.handle_top)
        val bottom = canvas.findViewById<View>(R.id.handle_bottom)
        val left = canvas.findViewById<View>(R.id.handle_left)
        val right = canvas.findViewById<View>(R.id.handle_right)
        val move = canvas.findViewById<View>(R.id.handle_move)
        assertEquals(48f, top.width * viewport.scale / density, 0.6f)
        assertTrue(top.height * viewport.scale / density in 0.1f..24.6f)
        assertTrue(right.width * viewport.scale / density in 0.1f..24.6f)
        assertTrue(right.height * viewport.scale / density in 0.1f..48.6f)
        assertHandleInsideCanvas(canvas, top)
        assertHandleInsideCanvas(canvas, bottom)
        assertHandleInsideCanvas(canvas, left)
        assertHandleInsideCanvas(canvas, right)
        assertTrue(boundsInCanvas(canvas, top).bottom <= boundsInCanvas(canvas, bottom).top)
        listOf(top, bottom, left, right, move).forEach { handle ->
            assertHandleReceivesTouch(viewport, canvas, handle)
        }
    }

    @Test
    fun narrowWindowCapsHandlesToCanvasInsteadOfCreatingAnInvalidClampRange() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val density = context.resources.displayMetrics.density
        val themedContext = ContextThemeWrapper(context, R.style.Theme_MarkdownKeyboard)
        val root = LayoutInflater.from(themedContext).inflate(R.layout.fragment_keyboard_setting, null, false)
        val viewport = root.findViewById<KeyboardPreviewViewport>(R.id.keyboard_preview_viewport)
        viewport.setLogicalCanvasSize((900 * density).toInt(), (190 * density).toInt())
        val keyboard = root.findViewById<View>(R.id.keyboard_container)
        keyboard.layoutParams = keyboard.layoutParams.apply {
            width = (900 * density).toInt()
            height = (100 * density).toInt()
        }
        viewport.measure(
            View.MeasureSpec.makeMeasureSpec((158 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((300 * density).toInt(), View.MeasureSpec.AT_MOST)
        )
        viewport.layout(0, 0, viewport.measuredWidth, viewport.measuredHeight)
        val canvas = viewport.findViewById<ViewGroup>(R.id.keyboard_preview_canvas)
        val keyboardView = canvas.findViewById<View>(R.id.keyboard_container)
        val top = canvas.findViewById<View>(R.id.handle_top)
        val bottom = canvas.findViewById<View>(R.id.handle_bottom)
        val left = canvas.findViewById<View>(R.id.handle_left)
        val right = canvas.findViewById<View>(R.id.handle_right)
        val move = canvas.findViewById<View>(R.id.handle_move)
        listOf(top, bottom, left, right, move).forEach { handle ->
            assertHandleInsideCanvas(canvas, handle)
        }
        assertTrue(boundsInCanvas(canvas, top).bottom <= boundsInCanvas(canvas, bottom).top)
        assertTrue(boundsInCanvas(canvas, left).right <= boundsInCanvas(canvas, right).left)
        val keyboardBounds = boundsInCanvas(canvas, keyboardView)
        val centerX = (keyboardBounds.left + keyboardBounds.right) / 2f
        val centerY = (keyboardBounds.top + keyboardBounds.bottom) / 2f
        listOf(top, bottom, left, right).forEach { edgeHandle ->
            val bounds = boundsInCanvas(canvas, edgeHandle)
            assertFalse(centerX in bounds.left..bounds.right && centerY in bounds.top..bounds.bottom)
        }
        listOf(top, bottom, left, right, move).forEach { handle ->
            assertHandleReceivesTouch(viewport, canvas, handle)
        }
    }

    private fun assertHandleInsideCanvas(canvas: ViewGroup, handle: View) {
        val bounds = boundsInCanvas(canvas, handle)
        assertTrue(bounds.left >= 0f)
        assertTrue(bounds.top >= 0f)
        assertTrue(bounds.right <= canvas.width.toFloat())
        assertTrue(bounds.bottom <= canvas.height.toFloat())
    }

    private data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private fun boundsInCanvas(canvas: ViewGroup, view: View): Bounds {
        var left = view.left + view.translationX
        var top = view.top + view.translationY
        var ancestor = view.parent as? View
        while (ancestor != null && ancestor !== canvas) {
            left += ancestor.left + ancestor.translationX
            top += ancestor.top + ancestor.translationY
            ancestor = ancestor.parent as? View
        }
        return Bounds(left, top, left + view.width, top + view.height)
    }

    private fun assertHandleReceivesTouch(
        viewport: KeyboardPreviewViewport,
        canvas: ViewGroup,
        handle: View
    ) {
        var receivedDown = false
        handle.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) receivedDown = true
            true
        }
        val bounds = boundsInCanvas(canvas, handle)
        val x = canvas.left + (bounds.left + bounds.right) / 2f * viewport.scale
        val y = canvas.top + (bounds.top + bounds.bottom) / 2f * viewport.scale
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, x, y, 0)
        try {
            assertTrue("${resourcesName(handle)} did not receive DOWN", viewport.dispatchTouchEvent(down))
        } finally {
            down.recycle()
        }
        val up = MotionEvent.obtain(0L, 1L, MotionEvent.ACTION_UP, x, y, 0)
        try {
            viewport.dispatchTouchEvent(up)
        } finally {
            up.recycle()
        }
        assertTrue("${resourcesName(handle)} was occluded", receivedDown)
    }

    private fun resourcesName(view: View): String =
        runCatching { view.resources.getResourceEntryName(view.id) }.getOrDefault("view")
}
