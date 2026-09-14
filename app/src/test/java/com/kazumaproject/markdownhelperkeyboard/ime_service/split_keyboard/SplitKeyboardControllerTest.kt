package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.FloatingPanelFrame
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SplitKeyboardControllerTest {
    private fun root(body: View): FloatingPanelFrame {
        var current = body
        while (current !is FloatingPanelFrame) current = current.parent as View
        return current
    }
    private fun layout(view: View) {
        val params = view.layoutParams as android.view.WindowManager.LayoutParams
        view.measure(View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(params.height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    @Test fun editingBlocksInputAndDisposeRemovesBothWindows() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity))
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        prefs.edit().clear().commit()
        val activated = mutableListOf<SplitSlot>()
        val controller = SplitKeyboardController(activity, activity.window.decorView, activated::add, {}, {})
        val bodies = SplitSlot.entries.associateWith { FrameLayout(activity) }
        val lists = SplitSlot.entries.associateWith { RecyclerView(activity) }
        bodies.forEach { (slot, body) -> controller.add(slot, body, lists.getValue(slot), 160) }
        try {
            controller.start()
            val inputFrames = bodies.mapValues { (_, body) -> body.parent.parent.parent as ViewGroup }
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
            val roots = bodies.values.map(::root)
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
        val controller = SplitKeyboardController(activity, activity.window.decorView, {}, {}, {})
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
        val controller = SplitKeyboardController(activity, activity.window.decorView, {}, {}, {})
        val body = FrameLayout(activity)
        controller.add(SplitSlot.MAIN, body, RecyclerView(activity), 160)
        controller.add(SplitSlot.SUB, FrameLayout(activity), RecyclerView(activity), 160)
        fun send(view: View, action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(0, 0, action, x, y, 0)
            try { view.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        try {
            controller.start()
            val root = root(body)
            layout(root)
            val y = root.height - 4f
            send(root, MotionEvent.ACTION_DOWN, 20f, y)
            send(root, MotionEvent.ACTION_MOVE, 40f, y - 20)
            send(root, MotionEvent.ACTION_CANCEL, 40f, y - 20)
            assertFalse(settings.hasPlacement(SplitSlot.MAIN, false))
            send(root, MotionEvent.ACTION_DOWN, 20f, y)
            send(root, MotionEvent.ACTION_MOVE, 40f, y - 20)
            send(root, MotionEvent.ACTION_UP, 40f, y - 20)
            assertTrue(settings.hasPlacement(SplitSlot.MAIN, false))
            assertFalse(settings.hasPlacement(SplitSlot.SUB, false))
            val saved = settings.placement(SplitSlot.MAIN, false)
            controller.setEditing(true)
            layout(root)
            val x = root.width / 2f
            send(root, MotionEvent.ACTION_DOWN, x, 4f)
            send(root, MotionEvent.ACTION_MOVE, x, 24f)
            send(root, MotionEvent.ACTION_CANCEL, x, 24f)
            assertEquals(saved, settings.placement(SplitSlot.MAIN, false))
        } finally { controller.stop(); activity.finish() }
    }

    @Test
    @Config(qualifiers = "w500dp-h1000dp-mdpi")
    fun differentMinimumWidthsDoNotOverlapOnInitialPlacement() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity))
        PreferenceManager.getDefaultSharedPreferences(activity).edit().clear().commit()
        val controller = SplitKeyboardController(activity, activity.window.decorView, {}, {}, {})
        val main = FrameLayout(activity)
        val sub = FrameLayout(activity)
        controller.add(SplitSlot.MAIN, main, RecyclerView(activity), 200)
        controller.add(SplitSlot.SUB, sub, RecyclerView(activity), 360)
        try {
            controller.start()
            fun params(body: View) = root(body).layoutParams as android.view.WindowManager.LayoutParams
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
        val controller = SplitKeyboardController(context, activity.window.decorView, {}, {}, {}, onWindowFailure = { failures++ })
        val main = FrameLayout(activity)
        controller.add(SplitSlot.MAIN, main, RecyclerView(activity), 160)
        controller.add(SplitSlot.SUB, FrameLayout(activity), RecyclerView(activity), 160)
        try {
            controller.start()
            controller.refresh()
            assertEquals(2, additions)
            assertEquals(1, failures)
            assertNull(root(main).parent)
        } finally { controller.stop(); activity.finish() }
    }

    @Test fun mainIsAddedLastAndEditingOrRefreshingNeverReordersWindows() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity))
        PreferenceManager.getDefaultSharedPreferences(activity).edit().clear().commit()
        val added = mutableListOf<View>()
        val manager = object : android.view.WindowManager by activity.windowManager {
            override fun addView(view: View, params: ViewGroup.LayoutParams) {
                added.add(view)
                activity.windowManager.addView(view, params)
            }
        }
        val context = object : android.content.ContextWrapper(activity) {
            override fun getSystemService(name: String): Any? =
                if (name == android.content.Context.WINDOW_SERVICE) manager else super.getSystemService(name)
        }
        val controller = SplitKeyboardController(context, activity.window.decorView, {}, {}, {})
        val main = FrameLayout(activity)
        val sub = FrameLayout(activity)
        controller.add(SplitSlot.MAIN, main, RecyclerView(activity), 160)
        controller.add(SplitSlot.SUB, sub, RecyclerView(activity), 160)
        try {
            controller.start()
            assertEquals(listOf(root(sub), root(main)), added)
            controller.setEditing(true)
            controller.refresh()
            controller.setEditing(false)
            assertEquals(listOf(root(sub), root(main)), added)
        } finally { controller.stop(); activity.finish() }
    }

    @Test fun themeChangesReachBothPanesWithoutActivatingEitherKeyboard() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity))
        PreferenceManager.getDefaultSharedPreferences(activity).edit().clear().commit()
        var palette = com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors(
            0xff102030.toInt(), 0xff304050.toInt(), 0xffaabbcc.toInt(), 0xff405060.toInt(), 0xff667788.toInt(), 0xff112233.toInt())
        val activations = mutableListOf<SplitSlot>()
        val controller = SplitKeyboardController(activity, activity.window.decorView, activations::add, {}, {}, colors = { palette })
        val bodies = SplitSlot.entries.map { slot -> FrameLayout(activity).also { controller.add(slot, it, RecyclerView(activity), 160) } }
        try {
            controller.start()
            palette = palette.copy(background = 0xff203040.toInt(), icon = 0xffeeddaa.toInt())
            controller.refresh()
            for (body in bodies) {
                val panel = root(body)
                assertEquals(palette.background, (panel.background as android.graphics.drawable.GradientDrawable).color!!.defaultColor)
                val header = panel.getChildAt(1) as ViewGroup
                assertEquals(palette.icon, (header.getChildAt(0) as android.widget.ImageButton).imageTintList!!.defaultColor)
                assertEquals(1, header.childCount)
            }
            assertTrue(activations.isEmpty())
        } finally { controller.stop(); activity.finish() }
    }

    @Test
    @Config(qualifiers = "w480dp-h1000dp-hdpi")
    fun resizingLeftKeepsTheRightEdgeFixedAtFractionalDpSizes() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity))
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        prefs.edit().clear().commit()
        SplitKeyboardSettings(prefs).savePlacement(SplitSlot.MAIN, false, SplitPlacement(.5f, .5f, 240f, 200f))
        val controller = SplitKeyboardController(activity, activity.window.decorView, {}, {}, {})
        val body = FrameLayout(activity)
        controller.add(SplitSlot.MAIN, body, RecyclerView(activity), 160)
        try {
            controller.start()
            controller.setEditing(true)
            val panel = root(body)
            layout(panel)
            val params = panel.layoutParams as android.view.WindowManager.LayoutParams
            val right = params.x + params.width
            val width = params.width
            for ((action, x) in listOf(MotionEvent.ACTION_DOWN to 3f, MotionEvent.ACTION_MOVE to 5f, MotionEvent.ACTION_UP to 5f)) {
                val event = MotionEvent.obtain(0, 0, action, x, panel.height / 2f, 0)
                try { panel.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            assertEquals(width - 2, params.width)
            assertEquals(right, params.x + params.width)
        } finally { controller.stop(); activity.finish() }
    }

    @Test fun mainOnlyHeaderSavesSpaceWithoutChangingBodySize() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity))
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        prefs.edit().clear().commit()
        val controller = SplitKeyboardController(activity, activity.window.decorView, {}, {}, {})
        val bodies = SplitSlot.entries.associateWith { FrameLayout(activity) }
        bodies.forEach { (slot, body) -> controller.add(slot, body, RecyclerView(activity), 240) }
        try {
            controller.start()
            val main = root(bodies.getValue(SplitSlot.MAIN))
            val sub = root(bodies.getValue(SplitSlot.SUB))
            assertEquals(48, main.contentInsets.top - sub.contentInsets.top)
            assertEquals(View.GONE, sub.getChildAt(1).visibility)
            val height = (sub.layoutParams as android.view.WindowManager.LayoutParams).height
            controller.setEditing(true)
            assertEquals(24, sub.contentInsets.top)
            controller.setEditing(false)
            prefs.edit().putString(SplitKeyboardSettings.EDIT_PLACEMENT, "BOTH").commit()
            controller.refresh()
            assertEquals(View.VISIBLE, sub.getChildAt(1).visibility)
            assertEquals(height + 48, (sub.layoutParams as android.view.WindowManager.LayoutParams).height)
        } finally { controller.stop(); activity.finish() }
    }

}
