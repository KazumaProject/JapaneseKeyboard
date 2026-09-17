package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.app.Activity
import android.os.Looper
import android.widget.EditText
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.FloatingCandidateListAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.floating_dictionary.FloatingDictionaryEditText
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class FloatingDictionaryInputTargetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val appEditor = EditText(context).apply { setText("background"); setSelection(length()) }
    private val appConnection = appEditor.onCreateInputConnection(EditorInfo())
    private val service = spy(IMEService()).also { service ->
        ReflectionHelpers.callInstanceMethod<Unit>(service, "attachBaseContext",
            ClassParameter.from(Context::class.java, context))
        service.appPreference = mock<AppPreference>().apply {
            whenever(undo_enable_preference).thenReturn(true)
        }
        ReflectionHelpers.setField(service, "listAdapter", mock<FloatingCandidateListAdapter>())
        doAnswer {
            ReflectionHelpers.getField<InputConnection?>(service, "dictionaryInputConnection") ?: appConnection
        }.whenever(service).getCurrentInputConnection()
    }

    @After fun cleanup() {
        activity.finish()
        for (name in listOf("scope", "ioScope")) {
            ReflectionHelpers.getField<CoroutineScope>(service, name).cancel()
        }
    }

    private fun switch(editor: EditText?) = ReflectionHelpers.callInstanceMethod<Unit>(
        service, "switchDictionaryInputTarget", ClassParameter.from(EditText::class.java, editor))

    private fun editor() = FloatingDictionaryEditText(context).apply {
        onSelectionChangedListener = { view, start, end ->
            ReflectionHelpers.callInstanceMethod<Unit>(service, "onDictionaryEditorSelectionChanged",
                ClassParameter.from(EditText::class.java, view),
                ClassParameter.from(Int::class.javaPrimitiveType, start),
                ClassParameter.from(Int::class.javaPrimitiveType, end))
        }
    }

    private val history get() = ReflectionHelpers.getField<Any>(service, "deletedBuffer")

    private fun seedHistory() {
        val direction = IMEService::class.java.declaredClasses.first { it.simpleName == "DeleteDirection" }
        val entryType = IMEService::class.java.declaredClasses.first { it.simpleName == "EditHistoryEntry" }
        val deletedType = entryType.declaredClasses.first { it.simpleName == "DeleteCommittedText" }
        val entry = deletedType.getDeclaredConstructor(String::class.java, direction).apply {
            isAccessible = true
        }.newInstance("private draft", direction.enumConstants[0])
        for (method in listOf("push", "pushRedo")) {
            history.javaClass.getDeclaredMethod(method, entryType).apply { isAccessible = true }.invoke(history, entry)
        }
    }

    @Test fun undoAndRedoCannotCrossAppReadingWordOrCloseTransitions() {
        val reading = editor()
        val word = editor()
        for (target in listOf(reading, word, null)) {
            seedHistory()
            switch(target)
            assertTrue(ReflectionHelpers.callInstanceMethod<Boolean>(history, "isEmpty"))
            ReflectionHelpers.callInstanceMethod<Unit>(service, "undoLastHistoryEntry")
            ReflectionHelpers.callInstanceMethod<Unit>(service, "redoLastHistoryEntry")
            assertEquals("background", appEditor.text.toString())
            assertEquals("", reading.text.toString())
            assertEquals("", word.text.toString())
        }
    }

    @Test fun reselectingSameEditorPreservesUndoAndRedo() {
        val reading = editor()
        switch(reading)
        seedHistory()
        switch(reading)
        assertTrue(ReflectionHelpers.callInstanceMethod<Boolean>(history, "hasUndoHistory"))
        assertTrue(ReflectionHelpers.callInstanceMethod<Boolean>(history, "hasRedoHistory"))
        ReflectionHelpers.callInstanceMethod<Unit>(service, "undoLastHistoryEntry")
        assertEquals("private draft", reading.text.toString())
        assertEquals("background", appEditor.text.toString())
    }

    @Test fun localSelectionReplacesAppRangeAndIgnoresInactiveEditors() {
        val coordinator = ReflectionHelpers.getField<Lazy<ForwardDeleteCoordinator>>(
            service, "forwardDeleteCoordinator\$delegate").value
        coordinator.reset(10, 15)
        val reading = editor().apply { setText("abcd"); setSelection(2) }
        val word = editor().apply { setText("other") }
        switch(reading)
        assertFalse(coordinator.hasSelection)
        reading.setSelection(1, 3)
        assertTrue(coordinator.hasSelection)
        word.setSelection(0)
        assertTrue(coordinator.hasSelection)
        reading.setSelection(2)
        assertFalse(coordinator.hasSelection)
        switch(null)
        reading.setSelection(0, 4)
        assertFalse(coordinator.hasSelection)
    }

    // Forward-delete grapheme boundaries use Paint.getTextRunCursor on Android.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun forwardDeleteAfterLocalCursorMoveAndSelectionLeavesAppUntouched() = runTest {
        val removed = mutableListOf<String>()
        var dispatched = 0
        val revision = ReflectionHelpers.getField<EditorMutationRevision>(service, "editorMutationRevision")
        val coordinator = ForwardDeleteCoordinator(
            scope = this,
            currentConnection = { service.currentInputConnection },
            currentRevision = revision::current,
            canDelete = { true },
            delete = { selected ->
                dispatched++
                ReflectionHelpers.callInstanceMethod<Unit>(service, "performForwardDelete",
                    ClassParameter.from(Boolean::class.javaPrimitiveType, selected))
            },
            recordDeletion = removed::add,
            readDispatcher = StandardTestDispatcher(testScheduler),
        )
        ReflectionHelpers.setField(service, "forwardDeleteCoordinator\$delegate", lazyOf(coordinator))
        coordinator.reset(10, 15)
        val reading = editor().apply { setText("abcd"); setSelection(0) }
        activity.setContentView(reading)
        reading.requestFocus()
        switch(reading)
        // Supply Android's key delivery at the framework boundary. The real local
        // connection still supplies text/selection, and EditText performs the edit.
        val connection = spy(service.currentInputConnection!!)
        doAnswer { reading.dispatchKeyEvent(it.getArgument(0)) }
            .whenever(connection).sendKeyEvent(any())
        ReflectionHelpers.setField(service, "dictionaryInputConnection", connection)
        reading.setSelection(2)
        fun delete() {
            ReflectionHelpers.callInstanceMethod<Unit>(service, "handleDeleteAfterCursor")
            runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            advanceUntilIdle()
        }
        delete()
        assertEquals("A synchronized local cursor must dispatch Delete", 1, dispatched)
        assertEquals("abd", reading.text.toString())
        reading.setSelection(0, 2)
        delete()
        assertEquals("d", reading.text.toString())
        assertEquals(listOf("c", "ab"), removed)
        assertEquals("background", appEditor.text.toString())
    }
}
