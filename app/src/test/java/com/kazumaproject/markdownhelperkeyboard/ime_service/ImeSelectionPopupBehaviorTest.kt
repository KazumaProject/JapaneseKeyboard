package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.PopupWindow
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.FloatingCandidateListAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.InkTouchDispatchFrameLayout
import com.kazumaproject.markdownhelperkeyboard.repository.UserTemplateRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplate
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class ImeSelectionPopupBehaviorTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).apply {
        get().setTheme(R.style.Theme_MarkdownKeyboard)
    }.setup().get()
    private val appEditor = EditText(activity).apply { setText("background"); setSelection(length()) }
    private var appConnection = appEditor.onCreateInputConnection(android.view.inputmethod.EditorInfo())
    private val root = InkTouchDispatchFrameLayout(activity)
    private val binding = mock<MainLayoutBinding>().apply { whenever(getRoot()).thenReturn(this@ImeSelectionPopupBehaviorTest.root) }
    private val service = spy(IMEService()).also { service ->
        ReflectionHelpers.callInstanceMethod<Unit>(service, "attachBaseContext",
            ClassParameter.from(Context::class.java, activity))
        service.zenzRuntimeClient = mock()
        service.appPreference = mock<AppPreference>()
        service.userTemplateRepository = mock<UserTemplateRepository>()
        ReflectionHelpers.setField(service, "mainLayoutBinding", binding)
        ReflectionHelpers.setField(service, "isInputViewActive", true)
        ReflectionHelpers.setField(service, "listAdapter", mock<FloatingCandidateListAdapter>())
        doReturn(Dialog(activity)).whenever(service).getWindow()
        doReturn(android.view.LayoutInflater.from(activity)).whenever(service).getLayoutInflater()
        doAnswer {
            ReflectionHelpers.getField<android.view.inputmethod.InputConnection?>(service, "dictionaryInputConnection")
                ?: appConnection
        }.whenever(service).getCurrentInputConnection()
        doAnswer {
            ReflectionHelpers.getField<android.view.inputmethod.EditorInfo?>(service, "dictionaryEditorInfo")
                ?: android.view.inputmethod.EditorInfo().apply { inputType = android.text.InputType.TYPE_CLASS_TEXT }
        }.whenever(service).getCurrentInputEditorInfo()
        activity.setContentView(FrameLayout(activity).apply {
            addView(appEditor)
            addView(root, FrameLayout.LayoutParams(-1, 300, Gravity.BOTTOM))
        })
        val decor = activity.window.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 1080, 1920)
        shadowOf(Looper.getMainLooper()).idle()
        appEditor.requestFocus()
    }

    private val popup get() = ReflectionHelpers.getField<PopupWindow?>(service, "keyboardSelectionPopupWindow")
    private fun call(name: String) = ReflectionHelpers.callInstanceMethod<Unit>(service, name)

    @After fun cleanup() {
        call("dismissKeyboardSelectionPopups")
        listOf("scope", "ioScope").forEach {
            ReflectionHelpers.getField<CoroutineScope>(service, it).cancel()
        }
        Dispatchers.resetMain()
        activity.finish()
    }

    @Test fun dateSelectionKeepsEditorFocusAndCommitsOnce() {
        call("showCurrentDateListPopup")
        val shown = requireNotNull(popup)
        assertTrue(shown.isShowing)
        assertFalse(shown.isFocusable)
        assertTrue(appEditor.hasFocus())
        val list = shown.contentView.findViewById<ListView>(R.id.popup_listview)
        val selected = list.adapter.getItem(0).toString()
        val consumed = ReflectionHelpers.callInstanceMethod<Boolean>(service, "handleImeSwitchPopupKeyDown",
            ClassParameter.from(Int::class.javaPrimitiveType, KeyEvent.KEYCODE_ENTER))
        assertTrue(consumed)
        assertEquals("background$selected", appEditor.text.toString())
        assertNull(popup)
        assertFalse(ReflectionHelpers.getField<Boolean>(service, "onKeyboardSwitchLongPressUp"))
    }

    @Test fun lateTemplateResultCannotReplaceNewerDateMenu() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        ReflectionHelpers.getField<CoroutineScope>(service, "ioScope").cancel()
        ReflectionHelpers.setField(service, "ioScope", CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        whenever(service.userTemplateRepository.allTemplatesSuspend()).thenReturn(
            listOf(UserTemplate(word = "late result", reading = "late", posIndex = 0, posScore = 0)))
        call("showUserTemplateListPopup")
        call("showCurrentDateListPopup")
        val newer = requireNotNull(popup)
        advanceUntilIdle()
        assertSame(newer, popup)
        assertTrue(newer.isShowing)
    }

    @Test fun templateResultIsDiscardedAfterConnectionChangesEvenWithinSameApp() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        ReflectionHelpers.getField<CoroutineScope>(service, "ioScope").cancel()
        ReflectionHelpers.setField(service, "ioScope", CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        whenever(service.userTemplateRepository.allTemplatesSuspend()).thenReturn(
            listOf(UserTemplate(word = "old editor", reading = "old", posIndex = 0, posScore = 0)))
        call("showUserTemplateListPopup")
        appConnection = EditText(activity).onCreateInputConnection(android.view.inputmethod.EditorInfo())
        advanceUntilIdle()
        assertNull(popup)
    }

    @Test fun hidingBeforeTemplateReadFinishesPreventsReopening() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        ReflectionHelpers.getField<CoroutineScope>(service, "ioScope").cancel()
        ReflectionHelpers.setField(service, "ioScope", CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        whenever(service.userTemplateRepository.allTemplatesSuspend()).thenReturn(
            listOf(UserTemplate(word = "stale", reading = "stale", posIndex = 0, posScore = 0)))
        call("showUserTemplateListPopup")
        call("dismissKeyboardSelectionPopups")
        ReflectionHelpers.setField(service, "isInputViewActive", false)
        advanceUntilIdle()
        assertNull(popup)
        assertFalse(ReflectionHelpers.getField<Boolean>(service, "onKeyboardSwitchLongPressUp"))
    }

    @Test fun multilineListFitsSmallLandscapeViewportAndRemainsScrollable() {
        val content = android.view.LayoutInflater.from(activity).inflate(R.layout.popup_list_layout, root, false)
        val list = content.findViewById<ListView>(R.id.popup_listview)
        list.adapter = android.widget.ArrayAdapter(activity, R.layout.list_item_layout,
            (1..40).map { "Long row $it\nSecond line\nThird line" })
        val window = ImeSelectionPopupWindow(activity, content)
        window.contentView.measure(
            View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
        )
        window.contentView.layout(0, 0, 480, 240)
        assertTrue(list.measuredHeight > 0)
        assertTrue(list.measuredHeight <= 240)
        assertTrue(list.lastVisiblePosition < list.count - 1)
        assertTrue(list.measuredWidth <= 480)
    }

    @Test fun editorMutationDiscardsPendingTemplateQuery() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        ReflectionHelpers.getField<CoroutineScope>(service, "ioScope").cancel()
        ReflectionHelpers.setField(service, "ioScope", CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        whenever(service.userTemplateRepository.allTemplatesSuspend()).thenReturn(
            listOf(UserTemplate(word = "stale", reading = "stale", posIndex = 0, posScore = 0)))
        call("showUserTemplateListPopup")
        service.commitText("changed", 1)
        advanceUntilIdle()
        assertNull(popup)
    }

    @Test fun outsideTapAndEscapeOnlyCloseTheMenu() {
        call("showCurrentDateListPopup")
        requireNotNull(popup).contentView.performClick()
        assertNull(popup)
        call("showCurrentDateListPopup")
        ReflectionHelpers.callInstanceMethod<Boolean>(service, "handleImeSwitchPopupKeyDown",
            ClassParameter.from(Int::class.javaPrimitiveType, KeyEvent.KEYCODE_ESCAPE))
        assertNull(popup)
        assertEquals("background", appEditor.text.toString())
    }
}
