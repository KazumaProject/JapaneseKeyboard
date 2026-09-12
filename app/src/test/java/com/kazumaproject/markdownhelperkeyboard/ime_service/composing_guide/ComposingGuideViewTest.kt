package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComposingGuideViewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun px(dp: Int) = (dp * context.resources.displayMetrics.density).toInt()
    private fun view(editing: Boolean): ComposingGuideView = ComposingGuideView(
        context, onEdit = {}, onHide = {}, onTextSize = { _, _ -> }, onHandleEvent = {},
    ).apply {
        setEditing(editing)
        measure(View.MeasureSpec.makeMeasureSpec(px(280), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(px(if (editing) 384 else 288), View.MeasureSpec.EXACTLY))
        layout(0, 0, measuredWidth, measuredHeight)
    }

    @Test fun normalModeMovesFromTheEntireBottomBandOnly() {
        val view = view(false)
        for (x in listOf(1f, view.width / 2f, view.width - 1f)) {
            for (y in listOf(view.height - px(24) + 1f, view.height - px(12).toFloat(), view.height - 1f)) {
                assertEquals(GuideHandle.MOVE, view.handleAt(x, y))
            }
        }
        assertNull(view.handleAt(view.width / 2f, view.height - px(24) - 1f))
        assertNull(view.handleAt(1f, view.height / 2f))
    }

    @Test fun editingDisablesTheWholeMoveBandAndSeparatesBottomResize() {
        val view = view(true)
        for (x in listOf(1f, view.width / 2f, view.width - 1f)) {
            for (y in listOf(view.height - px(24) + 1f, view.height - px(12).toFloat(), view.height - 1f)) {
                assertNull(view.handleAt(x, y))
            }
        }
        assertEquals(GuideHandle.BOTTOM, view.handleAt(view.width / 2f, view.height - px(36).toFloat()))
        assertEquals(GuideHandle.TOP, view.handleAt(view.width / 2f, px(12).toFloat()))
    }

    @Test fun finishingEditingRestoresMoveBand() {
        val view = view(true)
        view.setEditing(false)
        assertEquals(GuideHandle.MOVE, view.handleAt(view.width / 2f, view.height - px(12).toFloat()))
    }
}
