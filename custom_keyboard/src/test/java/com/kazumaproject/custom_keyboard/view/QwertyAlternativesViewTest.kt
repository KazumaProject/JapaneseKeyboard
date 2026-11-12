package com.kazumaproject.custom_keyboard.view

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.custom_keyboard.data.QwertyKeyLayer
import com.kazumaproject.custom_keyboard.data.QwertyPopupSlot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QwertyAlternativesViewTest {
    @Test fun selectionUsesDisplayedGridAndBlankCellsCancelWhileOutsidePreservesSelection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = QwertyAlternativesView(context)
        view.setContent(QwertyKeyLayer("a", columns = 2, slots = listOf(
            QwertyPopupSlot(), QwertyPopupSlot("abc", "label"), QwertyPopupSlot("👩‍💻"))),
            PopupViewStyle(100, 28f), KeyboardFontSnapshot())
        view.layout(0, 0, 200, 100)
        assertEquals("abc", view.selectedOutput)
        view.updateSelection(50f, 75f)
        assertEquals("👩‍💻", view.selectedOutput)
        view.updateSelection(-1f, -1f)
        assertEquals("👩‍💻", view.selectedOutput)
        view.updateSelection(50f, 25f)
        assertNull(view.selectedOutput)
        view.updateSelection(150f, 75f)
        assertNull(view.selectedOutput)
        view.updateSelection(150f, 25f)
        assertEquals("abc", view.selectedOutput)
    }
}
