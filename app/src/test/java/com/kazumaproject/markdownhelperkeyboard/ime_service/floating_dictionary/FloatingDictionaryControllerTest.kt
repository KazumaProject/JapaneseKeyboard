package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_dictionary

import android.app.Activity
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingDictionaryControllerTest {
    @Test fun closeButtonAndToolbarShareVisibilityAndHiddenDraftSurvivesUntilSessionEnds() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val store = mock<FloatingDictionaryStore>()
        DictionaryKind.entries.forEach { whenever(store.observe(it)).thenReturn(flowOf(emptyList())) }
        val controller = FloatingDictionaryController(activity, store,
            { CandidatePanelColors(-1, -1, -16777216, -1, -16777216, -1) }, {}, {})
        try {
            DictionaryKind.entries.forEach(controller::toggle)
            assertEquals(3, controller.activeShortcuts.size)
            val root = root(controller, DictionaryKind.USER)
            descendants(root).filterIsInstance<TextView>().first { it.text == activity.getString(R.string.floating_dictionary_add) }.performClick()
            val reading = descendants(root).filterIsInstance<EditText>().first { it.contentDescription == activity.getString(R.string.floating_dictionary_reading) }
            reading.setText("未保存の読み")
            descendants(root).first { it.contentDescription == activity.getString(R.string.floating_dictionary_hide) }.performClick()
            assertFalse(DictionaryKind.USER.shortcut in controller.activeShortcuts)
            assertEquals(2, controller.activeShortcuts.size)
            controller.toggle(DictionaryKind.USER)
            assertEquals("未保存の読み", reading.text.toString())
            assertEquals(3, controller.activeShortcuts.size)
            controller.detach()
            assertEquals(3, controller.activeShortcuts.size)
            val oldPanel = panel(controller, DictionaryKind.USER)
            controller.endSession()
            // Delayed window/gesture callbacks must not resurrect a finished session.
            assertFalse(oldPanel.javaClass.getDeclaredField("visible").apply { isAccessible = true }.getBoolean(oldPanel))
            assertTrue(controller.activeShortcuts.isEmpty())
            controller.toggle(DictionaryKind.USER)
            assertNotSame(root, root(controller, DictionaryKind.USER))
        } finally {
            controller.destroy()
            shadowOf(Looper.getMainLooper()).idle()
            activity.finish()
        }
    }

    @Test fun readingAndWordKeepIndependentTextWhenSwitchingInputTargets() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val store = mock<FloatingDictionaryStore>()
        DictionaryKind.entries.forEach { whenever(store.observe(it)).thenReturn(flowOf(emptyList())) }
        var target: EditText? = null
        val controller = FloatingDictionaryController(activity, store,
            { CandidatePanelColors(-1, -1, -16777216, -1, -16777216, -1) }, { target = it }, {})
        try {
            for (kind in DictionaryKind.entries) {
                controller.toggle(kind)
                val root = root(controller, kind)
                descendants(root).filterIsInstance<TextView>().first { it.text == activity.getString(R.string.floating_dictionary_add) }.performClick()
                val editors = descendants(root).filterIsInstance<EditText>().toList()
                val reading = editors.first { it.contentDescription == activity.getString(R.string.floating_dictionary_reading) }
                val word = editors.first { it.contentDescription == activity.getString(R.string.floating_dictionary_word) }
                assertSame(reading, target)
                target!!.onCreateInputConnection(EditorInfo()).commitText("かな", 1)
                word.requestFocus()
                assertSame(word, target)
                target!!.onCreateInputConnection(EditorInfo()).commitText("仮名", 1)
                reading.requestFocus()
                assertSame(reading, target)
                assertEquals("かな", reading.text.toString())
                assertEquals("仮名", word.text.toString())
                controller.toggle(kind)
                assertNull(target)
            }
        } finally {
            controller.destroy()
            shadowOf(Looper.getMainLooper()).idle()
            activity.finish()
        }
    }

    private fun root(controller: FloatingDictionaryController, kind: DictionaryKind): View {
        val panel = panel(controller, kind)
        return panel.javaClass.getDeclaredField("root").apply { isAccessible = true }.get(panel) as View
    }
    private fun panel(controller: FloatingDictionaryController, kind: DictionaryKind): Any {
        val panels = controller.javaClass.getDeclaredField("panels").apply { isAccessible = true }.get(controller) as Map<*, *>
        return panels[kind]!!
    }
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
}
