package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_clipboard

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.listeners.ClipboardItemAction
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors
import com.kazumaproject.markdownhelperkeyboard.repository.ShortcutRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import com.kazumaproject.markdownhelperkeyboard.short_cut.database.ShortcutDao
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class FloatingClipboardTest {
    private fun activity() = Robolectric.buildActivity(Activity::class.java).setup().get()
        .apply { setTheme(R.style.Theme_MarkdownKeyboard) }
    private fun palette() = CandidatePanelColors(-1, -1, -16777216, -3355444, -16776961, -1)
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
    private fun row(root: View, position: Int): View {
        val recycler = descendants(root).filterIsInstance<RecyclerView>().single()
        @Suppress("UNCHECKED_CAST")
        val adapter = recycler.adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
        return adapter.createViewHolder(recycler, adapter.getItemViewType(position)).also {
            adapter.bindViewHolder(it, position)
        }.itemView
    }

    @Test fun pinnedItemsComeFirstAndTapPastesWithoutClosing() {
        val activity = activity()
        val calls = mutableListOf<Pair<Long, ClipboardItemAction>>()
        var closes = 0
        val view = FloatingClipboardView(activity, {}, {}, { closes++ }, {}) { item, action -> calls += item.id() to action }
        view.updateItems(listOf(ClipboardItem.Text(1, "new"), ClipboardItem.Text(2, "pinned", true)))
        assertEquals(activity.getString(com.kazumaproject.symbol_keyboard.R.string.symbol_clipboard_section_pinned),
            descendants(row(view, 0)).filterIsInstance<TextView>().single().text.toString())
        row(view, 1).performClick()
        row(view, 3).performClick()
        assertEquals(listOf(2L to ClipboardItemAction.PASTE, 1L to ClipboardItemAction.PASTE), calls)
        assertEquals(0, closes)
        assertFalse(descendants(view).filterIsInstance<TextView>().any { it.text == activity.getString(R.string.floating_clipboard_title) })
        activity.finish()
    }

    @Test fun longPressExposesDeleteAndCorrectPinAction() {
        val activity = activity()
        val calls = mutableListOf<ClipboardItemAction>()
        val view = FloatingClipboardView(activity, {}, {}, {}, {}) { _, action -> calls += action }
        view.updateItems(listOf(ClipboardItem.Text(7, "pinned", true)))
        val anchor = row(view, 1)
        activity.addContentView(anchor, ViewGroup.LayoutParams(200, 100))
        anchor.performLongClick()
        val menu = shadowOf(activity.application).latestPopupWindow
        assertFalse(menu.isFocusable)
        val actions = descendants(menu.contentView).filterIsInstance<TextView>().toList()
        assertEquals(listOf(ClipboardItemAction.PASTE, ClipboardItemAction.UNPIN, ClipboardItemAction.DELETE), actions.map { it.tag })
        actions.last().performClick()
        assertEquals(listOf(ClipboardItemAction.DELETE), calls)
        view.dismissMenu()
        activity.finish()
    }

    @Test fun busyAndLayoutEditingBlockClipboardActions() {
        val activity = activity()
        var calls = 0
        val view = FloatingClipboardView(activity, {}, {}, {}, {}) { _, _ -> calls++ }
        view.updateItems(listOf(ClipboardItem.Text(1, "item")))
        val item = row(view, 1)
        view.setBusy(true)
        item.performClick()
        view.setBusy(false)
        view.setEditing(true)
        item.performClick()
        assertEquals(0, calls)
        view.setEditing(false)
        item.performClick()
        assertEquals(1, calls)
        activity.finish()
    }

    @Test fun historySwitchSharesStateWithoutWritingDuringRendering() {
        val activity = activity()
        val changes = mutableListOf<Boolean>()
        val view = FloatingClipboardView(activity, {}, {}, {}, { changes += it }) { _, _ -> }
        view.updateHistoryEnabled(true)
        assertTrue(changes.isEmpty())
        descendants(view).filterIsInstance<SwitchMaterial>().single().isChecked = false
        assertEquals(listOf(false), changes)
        activity.finish()
    }

    @Test fun closeEndsVisibilityAndDetachPreservesRotationVisibility() {
        val activity = activity()
        val controller = FloatingClipboardController(activity, { true }, ::palette, {}, { _, _, complete -> complete(true) }, {})
        try {
            controller.attach(activity.window.decorView)
            controller.toggle()
            assertTrue(controller.visible)
            controller.detach()
            assertTrue(controller.visible)
            controller.attach(activity.window.decorView)
            controller.hide()
            assertFalse(controller.visible)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(controller.visible)
        } finally { controller.destroy(); activity.finish() }
    }

    @Test fun newShortcutIsOptionalAndSettingsGeometryRoundTrips() {
        val activity = activity()
        assertEquals(ShortcutType.FLOATING_CLIPBOARD, ShortcutType.fromId("floating_clipboard"))
        assertFalse(ShortcutType.FLOATING_CLIPBOARD in ShortcutRepository(mock<ShortcutDao>()).defaultShortcuts)
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        preferences.edit().putFloat("floating_clipboard_portrait_width", 320f).apply()
        AppPreference.init(activity)
        val backup = AppPreference.exportAllToJson(listOf("floating_clipboard", "clip_board"))
        preferences.edit().clear().apply()
        assertEquals(listOf("floating_clipboard", "clip_board"), AppPreference.importAllFromJson(backup))
        assertEquals(320f, preferences.getFloat("floating_clipboard_portrait_width", 0f))
        activity.finish()
    }
}
