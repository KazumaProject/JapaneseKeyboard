package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SplitKeyboardControllerTest {
    @Test fun editingBlocksInputAndDisposeRemovesBothWindows() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity))
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        prefs.edit().clear().commit()
        val activated = mutableListOf<SplitSlot>()
        val controller = SplitKeyboardController(activity, activity.window.decorView, activated::add, {}, {}, {})
        val bodies = SplitSlot.entries.associateWith { FrameLayout(activity) }
        val lists = SplitSlot.entries.associateWith { RecyclerView(activity) }
        bodies.forEach { (slot, body) -> controller.add(slot, body, lists.getValue(slot), 160) }
        try {
            controller.start()
            val inputFrames = bodies.mapValues { (_, body) -> body.parent.parent as ViewGroup }
            fun touch(slot: SplitSlot) {
                val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1f, 200f, 0)
                try { inputFrames.getValue(slot).dispatchTouchEvent(event) } finally { event.recycle() }
            }
            touch(SplitSlot.SUB)
            assertEquals(listOf(SplitSlot.SUB), activated)
            controller.setEditing(true)
            touch(SplitSlot.MAIN)
            assertEquals(listOf(SplitSlot.SUB), activated)
            controller.setEditing(false)
            touch(SplitSlot.MAIN)
            assertEquals(listOf(SplitSlot.SUB, SplitSlot.MAIN), activated)
            val roots = inputFrames.values.map { it.parent as View }
            controller.stop()
            roots.forEach { assertNull(it.parent) }
            controller.stop()
        } finally { controller.stop(); activity.finish() }
    }
    @Test fun candidateChoiceIsAppliedToBothWindows() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity))
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        prefs.edit().clear().commit()
        val controller = SplitKeyboardController(activity, activity.window.decorView, {}, {}, {}, {})
        val lists = SplitSlot.entries.associateWith { RecyclerView(activity) }
        lists.forEach { (slot, list) -> controller.add(slot, FrameLayout(activity), list, 160) }
        try {
            controller.start()
            SplitCandidatePlacement.entries.forEach { placement ->
                prefs.edit().putString(SplitKeyboardSettings.CANDIDATES, placement.name).commit()
                controller.refresh()
                lists.forEach { (slot, list) -> assertEquals(placement.shows(slot), list.visibility == View.VISIBLE) }
            }
        } finally { controller.stop(); activity.finish() }
    }
    @Test fun cancelledDragDoesNotPersistAndResizeOnlyChangesItsSlot() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity))
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        prefs.edit().clear().commit()
        val settings = SplitKeyboardSettings(prefs)
        val controller = SplitKeyboardController(activity, activity.window.decorView, {}, {}, {}, {})
        val body = FrameLayout(activity)
        controller.add(SplitSlot.MAIN, body, RecyclerView(activity), 160)
        controller.add(SplitSlot.SUB, FrameLayout(activity), RecyclerView(activity), 160)
        fun send(view: View, action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(0, 0, action, x, y, 0)
            try { view.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        try {
            controller.start()
            val root = body.parent.parent.parent as ViewGroup
            val handle = (root.getChildAt(0) as ViewGroup).getChildAt(0)
            send(handle, MotionEvent.ACTION_DOWN, 20f, 20f)
            send(handle, MotionEvent.ACTION_MOVE, 40f, 0f)
            send(handle, MotionEvent.ACTION_CANCEL, 40f, 0f)
            assertFalse(settings.hasPlacement(SplitSlot.MAIN, false))
            send(handle, MotionEvent.ACTION_DOWN, 20f, 20f)
            send(handle, MotionEvent.ACTION_MOVE, 40f, 0f)
            send(handle, MotionEvent.ACTION_UP, 40f, 0f)
            assertTrue(settings.hasPlacement(SplitSlot.MAIN, false))
            assertFalse(settings.hasPlacement(SplitSlot.SUB, false))
            val saved = settings.placement(SplitSlot.MAIN, false)
            controller.setEditing(true)
            val resize = root.getChildAt(root.childCount - 1)
            send(resize, MotionEvent.ACTION_DOWN, 20f, 20f)
            send(resize, MotionEvent.ACTION_MOVE, 40f, 40f)
            send(resize, MotionEvent.ACTION_CANCEL, 40f, 40f)
            assertEquals(saved, settings.placement(SplitSlot.MAIN, false))
        } finally { controller.stop(); activity.finish() }
    }

    @Test
    @Config(qualifiers = "w500dp-h1000dp-mdpi")
    fun differentMinimumWidthsDoNotOverlapOnInitialPlacement() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity))
        PreferenceManager.getDefaultSharedPreferences(activity).edit().clear().commit()
        val controller = SplitKeyboardController(activity, activity.window.decorView, {}, {}, {}, {})
        val main = FrameLayout(activity)
        val sub = FrameLayout(activity)
        controller.add(SplitSlot.MAIN, main, RecyclerView(activity), 200)
        controller.add(SplitSlot.SUB, sub, RecyclerView(activity), 360)
        try {
            controller.start()
            fun params(body: View) = (body.parent.parent.parent as View).layoutParams as android.view.WindowManager.LayoutParams
            val mainParams = params(main)
            val subParams = params(sub)
            assertTrue(mainParams.y + mainParams.height <= subParams.y)
        } finally { controller.stop(); activity.finish() }
    }

    @Test fun windowFailureDisposesTheFirstPaneAndReportsFailureOnlyOnce() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity))
        PreferenceManager.getDefaultSharedPreferences(activity).edit().clear().commit()
        var additions = 0
        var failures = 0
        val failingManager = object : android.view.WindowManager by activity.windowManager {
            override fun addView(view: View, params: ViewGroup.LayoutParams) {
                additions++
                if (additions == 2) throw android.view.WindowManager.BadTokenException("Expired IME token")
                activity.windowManager.addView(view, params)
            }
        }
        val context = object : android.content.ContextWrapper(activity) {
            override fun getSystemService(name: String): Any? =
                if (name == android.content.Context.WINDOW_SERVICE) failingManager else super.getSystemService(name)
        }
        val controller = SplitKeyboardController(context, activity.window.decorView, {}, {}, {}, {}, { failures++ })
        val main = FrameLayout(activity)
        controller.add(SplitSlot.MAIN, main, RecyclerView(activity), 160)
        controller.add(SplitSlot.SUB, FrameLayout(activity), RecyclerView(activity), 160)
        try {
            controller.start()
            controller.refresh()
            assertEquals(2, additions)
            assertEquals(1, failures)
            assertNull((main.parent.parent.parent as View).parent)
        } finally { controller.stop(); activity.finish() }
    }

}
