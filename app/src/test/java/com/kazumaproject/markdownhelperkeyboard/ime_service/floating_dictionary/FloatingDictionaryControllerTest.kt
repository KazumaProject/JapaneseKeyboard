package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_dictionary

import android.app.Activity
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.runBlocking
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
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
        var selectionEditor: EditText? = null
        var selection = -1 to -1
        val controller = FloatingDictionaryController(activity, store,
            { CandidatePanelColors(-1, -1, -16777216, -1, -16777216, -1) }, { target = it }, {},
            { editor, start, end -> selectionEditor = editor; selection = start to end })
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
                reading.setSelection(0, 1)
                assertSame(reading, selectionEditor)
                assertEquals(0 to 1, selection)
                word.setSelection(1)
                assertSame(word, selectionEditor)
                assertEquals(1 to 1, selection)
                controller.toggle(kind)
                assertNull(target)
            }
        } finally {
            controller.destroy()
            shadowOf(Looper.getMainLooper()).idle()
            activity.finish()
        }
    }

    @Test fun userWordsAndTemplatesReadWhitespaceSettingAtSaveForAdditionAndEditing() {
        for (kind in listOf(DictionaryKind.USER, DictionaryKind.TEMPLATE)) {
            for (adding in listOf(true, false)) {
                for (preserve in listOf(false, true)) {
                    withSaveForm(kind, adding) { activity, root, saved ->
                        // Change the preference after the form was opened.
                        PreferenceManager.getDefaultSharedPreferences(activity).edit()
                            .putBoolean(AppPreference.PRESERVE_DICTIONARY_WORD_WHITESPACE_KEY, preserve)
                            .commit()
                        fillAndSave(activity, root, "　き ", " き　")
                        val result = requireNotNull(saved.poll(5, TimeUnit.SECONDS))
                        assertEquals(if (preserve) "　き " else "き", result.first.word)
                        assertEquals("き", result.first.reading)
                        assertEquals(if (adding) 0 else 7, result.first.id)
                        assertEquals(adding, result.second)
                    }
                }
            }
        }
    }

    @Test fun whitespaceOnlyWordsAndReadingsAreRejected() {
        for (kind in listOf(DictionaryKind.USER, DictionaryKind.TEMPLATE)) {
            for (adding in listOf(true, false)) {
                for (preserve in listOf(false, true)) {
                    withSaveForm(kind, adding) { activity, root, saved ->
                        PreferenceManager.getDefaultSharedPreferences(activity).edit()
                            .putBoolean(AppPreference.PRESERVE_DICTIONARY_WORD_WHITESPACE_KEY, preserve)
                            .commit()
                        for ((word, reading) in listOf("　 " to "き", "" to "き", "き" to "　 ")) {
                            fillAndSave(activity, root, word, reading)
                            assertTrue(saved.isEmpty())
                            assertTrue(descendants(root).filterIsInstance<TextView>().any {
                                it.text.toString() == activity.getString(R.string.floating_dictionary_invalid) &&
                                    it.visibility == View.VISIBLE
                            })
                        }
                    }
                }
            }
        }
    }

    @Test fun learningDictionaryKeepsWhitespaceRegardlessOfTheSetting() {
        for (preserve in listOf(false, true)) {
            withSaveForm(DictionaryKind.LEARN, true) { activity, root, saved ->
                PreferenceManager.getDefaultSharedPreferences(activity).edit()
                    .putBoolean(AppPreference.PRESERVE_DICTIONARY_WORD_WHITESPACE_KEY, preserve)
                    .commit()
                fillAndSave(activity, root, "　き ", " き　")
                assertEquals("　き ", requireNotNull(saved.poll(5, TimeUnit.SECONDS)).first.word)
            }
        }
    }

    private fun withSaveForm(
        kind: DictionaryKind,
        adding: Boolean,
        test: (Activity, View, LinkedBlockingQueue<Pair<DictionaryEntry, Boolean>>) -> Unit,
    ) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        PreferenceManager.getDefaultSharedPreferences(activity).edit().clear().commit()
        val store = mock<FloatingDictionaryStore>()
        val saved = LinkedBlockingQueue<Pair<DictionaryEntry, Boolean>>()
        DictionaryKind.entries.forEach { whenever(store.observe(it)).thenReturn(flowOf(emptyList())) }
        runBlocking {
            whenever(store.save(any(), any(), any())).thenAnswer {
                saved.add(it.getArgument<DictionaryEntry>(1) to it.getArgument<Boolean>(2))
                Unit
            }
        }
        val controller = FloatingDictionaryController(activity, store,
            { CandidatePanelColors(-1, -1, -16777216, -1, -16777216, -1) }, {}, {})
        try {
            controller.toggle(kind)
            val panel = panel(controller, kind)
            panel.javaClass.getDeclaredMethod("showForm", DictionaryEntry::class.java)
                .apply { isAccessible = true }
                .invoke(panel, if (adding) null else DictionaryEntry(7, "き", "き ", 4000))
            test(activity, root(controller, kind), saved)
        } finally {
            controller.destroy()
            shadowOf(Looper.getMainLooper()).idle()
            activity.finish()
        }
    }

    private fun fillAndSave(activity: Activity, root: View, word: String, reading: String) {
        val editors = descendants(root).filterIsInstance<EditText>().toList()
        editors.first { it.contentDescription == activity.getString(R.string.floating_dictionary_word) }
            .setText(word)
        editors.first { it.contentDescription == activity.getString(R.string.floating_dictionary_reading) }
            .setText(reading)
        descendants(root).filterIsInstance<TextView>()
            .first { it.text == activity.getString(R.string.save_string) }.performClick()
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
