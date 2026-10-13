package com.kazumaproject.core.ui.skin

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.key_window.KeyWindowLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SkinGuidePopupSparseCandidatesTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun sparseGojuonCandidatesKeepAnEmptyUnselectedCenterConnector() {
        val popup = SkinGuidePopup(context)
        val skin = checkNotNull(KeyboardSkinRegistry.find(KeyboardSkinId.CUPERTINO_CLASSIC))
        val guide = popup.configure(
            width = 64,
            height = 48,
            skin = skin,
            labels = mapOf(
                PopupDirection.TOP to "か",
                PopupDirection.LEFT to "が",
            ),
            includeEmptyCenterConnector = true,
        )

        val center = guide.cellAt(0)
        assertEquals(View.VISIBLE, center.visibility)
        assertEquals("", (center.getChildAt(0) as TextView).text.toString())
        assertFalse(center.skinSelected)
        assertEquals(View.VISIBLE, guide.cellAt(1).visibility)
        assertEquals(View.VISIBLE, guide.cellAt(2).visibility)
        assertEquals(View.INVISIBLE, guide.cellAt(3).visibility)
        assertEquals(View.INVISIBLE, guide.cellAt(4).visibility)
    }

    @Test
    fun emptyCenterRemainsHiddenForOtherGuideCallers() {
        val popup = SkinGuidePopup(context)
        val skin = checkNotNull(KeyboardSkinRegistry.find(KeyboardSkinId.CUPERTINO_CLASSIC))
        val guide = popup.configure(
            width = 64,
            height = 48,
            skin = skin,
            labels = mapOf(PopupDirection.TOP to "か"),
        )

        assertEquals(View.INVISIBLE, guide.cellAt(0).visibility)
    }

    private fun View.cellAt(index: Int): KeyWindowLayout =
        ((this as FrameLayout).getChildAt(index) as KeyWindowLayout)
}
