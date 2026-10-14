package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_keyboard

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.GuideHandle
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 30])
class FloatingKeyboardPanelTest {
    @Before fun resetChromePreferences() {
        PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext<Context>())
            .edit().remove(FloatingKeyboardChromeSettings.HIDE_BUTTON_POSITION)
            .remove(FloatingKeyboardChromeSettings.SHOW_EDIT_BUTTON).commit()
    }

    private fun layout(panel: FloatingKeyboardPanel) {
        panel.measure(View.MeasureSpec.makeMeasureSpec(340, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST))
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
    }

    private fun tap(panel: FloatingKeyboardPanel, target: View) {
        val x = (target.left + target.right) / 2f
        val y = (target.top + target.bottom) / 2f
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(0, 0, action, x, y, 0)
            panel.dispatchTouchEvent(event)
            event.recycle()
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun bottomButtonClosesWithoutMovingAndKeysKeepTheirHeight() {
        val context = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        context.setTheme(R.style.Theme_MarkdownKeyboard)
        val binding = FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(context))
        var hides = 0
        var moves = 0
        binding.floatingHideKeyboardBtn.setOnClickListener { hides++ }
        val panel = FloatingKeyboardPanel(binding, {}, { _, _, _ -> moves++ })
        context.setContentView(panel)
        layout(panel)
        val hide = binding.floatingHideKeyboardBtn
        assertEquals(panel.height, hide.bottom)
        assertEquals(48, hide.height)
        assertEquals(200, binding.floatingKeyboardContainer.height)
        assertEquals(GuideHandle.MOVE, panel.handleAt(170f, panel.height - 2f))
        tap(panel, hide)
        assertEquals(1, hides)
        assertEquals(0, moves)
        context.finish()
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun topButtonStaysFixedAndClickableWhileShortcutsScroll() {
        val context = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        context.setTheme(R.style.Theme_MarkdownKeyboard)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(FloatingKeyboardChromeSettings.HIDE_BUTTON_POSITION, "top_left").commit()
        val binding = FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(context))
        var hides = 0
        binding.floatingHideKeyboardBtn.setOnClickListener { hides++ }
        val recycler = binding.suggestionRecyclerView
        recycler.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        recycler.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = 20
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                object : RecyclerView.ViewHolder(TextView(context).apply {
                    layoutParams = RecyclerView.LayoutParams(64, 48)
                }) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as TextView).text = position.toString()
            }
        }
        val panel = FloatingKeyboardPanel(binding, {}, { _, _, _ -> })
        context.setContentView(panel)
        layout(panel)
        val hide = binding.floatingHideKeyboardBtn
        val before = android.graphics.Rect(hide.left, hide.top, hide.right, hide.bottom)
        val recyclerBounds = android.graphics.Rect(0, 0, recycler.width, recycler.height)
        panel.offsetDescendantRectToMyCoords(recycler, recyclerBounds)
        assertTrue(hide.right <= recyclerBounds.left)
        recycler.scrollBy(128, 0)
        assertTrue((recycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() > 0)
        layout(panel)
        assertEquals(before, android.graphics.Rect(hide.left, hide.top, hide.right, hide.bottom))
        tap(panel, hide)
        assertEquals(1, hides)
        context.finish()
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun hidingPencilRemovesHeaderButEditingStillHasDoneButton() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.setTheme(R.style.Theme_MarkdownKeyboard)
        val visible = FloatingKeyboardPanel(FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(context)), {}, { _, _, _ -> })
        layout(visible)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(FloatingKeyboardChromeSettings.SHOW_EDIT_BUTTON, false).commit()
        val binding = FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(context))
        lateinit var panel: FloatingKeyboardPanel
        panel = FloatingKeyboardPanel(binding, { panel.setEditing(false) }, { _, _, _ -> })
        layout(panel)
        val compactHeight = panel.height
        assertEquals(48, visible.height - compactHeight)
        panel.setEditing(true)
        layout(panel)
        val doneButtons = ArrayList<View>()
        panel.findViewsWithText(doneButtons, context.getString(R.string.composing_guide_done), View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
        assertEquals(1, doneButtons.size)
        assertTrue(doneButtons.single().performClick())
        layout(panel)
        assertFalse(panel.editing)
        assertEquals(compactHeight, panel.height)
        assertEquals(200, binding.floatingKeyboardContainer.height)
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun attachedPanelAppliesChangesAndReopensWithSavedSettings() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        activity.setTheme(R.style.Theme_MarkdownKeyboard)
        val binding = FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(activity))
        val panel = FloatingKeyboardPanel(binding, {}, { _, _, _ -> })
        activity.setContentView(panel)
        layout(panel)
        val originalHeight = panel.height
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        prefs.edit().putBoolean(FloatingKeyboardChromeSettings.SHOW_EDIT_BUTTON, false)
            .putString(FloatingKeyboardChromeSettings.HIDE_BUTTON_POSITION, "top_left").commit()
        layout(panel)
        assertEquals(72, originalHeight - panel.height)
        assertTrue(binding.floatingHideKeyboardBtn.bottom < panel.height - 24)
        activity.setContentView(android.widget.FrameLayout(activity))
        prefs.edit().putString(FloatingKeyboardChromeSettings.HIDE_BUTTON_POSITION, "bottom_left").commit()
        activity.setContentView(panel)
        layout(panel)
        assertEquals(panel.height, binding.floatingHideKeyboardBtn.bottom)
        assertEquals(originalHeight - 48, panel.height)
        activity.finish()
    }

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
