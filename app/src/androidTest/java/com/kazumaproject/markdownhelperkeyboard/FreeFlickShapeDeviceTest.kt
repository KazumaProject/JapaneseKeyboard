package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inspector.WindowInspector
import android.widget.Button
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.material.button.MaterialButton
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.view.PopupMapGuideView
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.sumire_popup.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FreeFlickShapeDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context get() = ins.targetContext
    private val automation get() = ins.uiAutomation
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var value: T? = null
        ins.runOnMainSync { value = block() }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun activity() = onMain { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull() }
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun windows() = onMain { WindowInspector.getGlobalWindowViews().flatMap(::all) }
    private fun canvas() = onMain { activity()?.window?.decorView?.let(::all)?.filterIsInstance<PopupMapEditorCanvas>()?.firstOrNull() }
    private fun shape() = windows().filterIsInstance<PopupShapeEditorView>().firstOrNull()
    private fun await(condition: () -> Boolean) {
        repeat(400) { var ready = false; ins.runOnMainSync { ready = condition() }; if (ready) return; SystemClock.sleep(50) }
        fail("Shape editor/input did not reach expected state")
    }
    private fun button(resource: Int) {
        ins.runOnMainSync { windows().filterIsInstance<MaterialButton>().last { it.text == context.getString(resource) }.performClick() }
        ins.waitForIdleSync()
    }
    private fun dialogButton(id: Int) {
        ins.runOnMainSync { windows().filterIsInstance<Button>().last { it.id == id }.performClick() }
        ins.waitForIdleSync()
    }
    private fun startEditor() {
        assumeTrue(context.packageName.endsWith(".popuptest"))
        shell("am start -f 0x34000000 -n ${context.packageName}/${MainActivity::class.java.name}")
        await { (activity()?.supportFragmentManager?.findFragmentById(R.id.nav_host_fragment_activity_main) as? NavHostFragment)?.navController?.currentDestination != null }
        ins.runOnMainSync { (activity()!!.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController.navigate(R.id.popupMapEditorFragment) }
        await { canvas()?.width?.let { it > 0 } == true }
        ins.runOnMainSync {
            val c = canvas()!!
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(0, SystemClock.uptimeMillis(), action, c.originX - c.keyWidth, c.originY, 0)
                c.dispatchTouchEvent(event); event.recycle()
            }
        }
        button(R.string.free_flick_shape_customize)
        await { shape()?.frame?.width()?.let { it > 0 } == true }
    }
    private fun dragVertex(index: Int, x: Float, y: Float, cancel: Boolean = false) {
        ins.runOnMainSync {
            val view = shape()!!; val start = view.vertexPosition(index)
            val targetX = view.frame.left + x * view.frame.width(); val targetY = view.frame.top + y * view.frame.height()
            val down = SystemClock.uptimeMillis()
            for (step in 0..16) {
                val t = step / 16f
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), if (step == 0) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE,
                    start.first + (targetX - start.first) * t, start.second + (targetY - start.second) * t, 0)
                view.dispatchTouchEvent(event); event.recycle()
            }
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, targetX, targetY, 0)
            view.dispatchTouchEvent(event); event.recycle()
        }
        ins.waitForIdleSync()
    }

    @Test fun vertexDraftSurvivesRecreationAndCancelApplySaveAndUndoRemainSeparate() {
        val repository = SumirePopupRepository(context); val original = SumirePopupDefaults.create("は")
        repository.save(original)
        startEditor()
        button(R.string.free_flick_shape_add)
        assertEquals(5, shape()!!.vertices.size)
        dragVertex(1, .5f, .30f)
        val edited = shape()!!.region.outline
        assertEquals(.30f, edited!!.vertices[1].y, .02f)
        dragVertex(1, .5f, .45f, cancel = true)
        assertEquals(edited, shape()!!.region.outline)
        val oldView = shape()
        ins.runOnMainSync { activity()!!.recreate() }
        await { shape() != null && shape() !== oldView && shape()!!.frame.width() > 0 }
        assertEquals(edited, shape()!!.region.outline)
        assertEquals(1, shape()!!.selectedVertex)
        assertEquals(original, repository.load("は"))
        dialogButton(android.R.id.button2)
        await { shape() == null }
        assertEquals(original, canvas()!!.config)
        button(R.string.free_flick_shape_customize); await { shape() != null }
        button(R.string.free_flick_shape_add); dragVertex(1, .5f, .30f)
        dialogButton(android.R.id.button1)
        await { shape() == null && canvas()!!.config!!.version == 2 }
        val draft = canvas()!!.config!!
        assertEquals(original, repository.load("は"))
        button(R.string.popup_save)
        assertEquals(draft, repository.load("は"))
        button(R.string.popup_undo)
        assertEquals(original, canvas()!!.config)
        assertEquals(draft, repository.load("は"))
        ins.runOnMainSync { activity()!!.finish() }
    }

    @Test fun removingVerticesCreatesACircleAndResetAndUndoPreserveIt() {
        val repository = SumirePopupRepository(context)
        repository.save(SumirePopupDefaults.create("は")); val other = repository.load("か")
        startEditor()
        button(R.string.free_flick_shape_remove); assertEquals(3, shape()!!.vertices.size)
        button(R.string.free_flick_shape_remove); assertTrue(shape()!!.region.outline!!.isCircle)
        button(R.string.free_flick_shape_add); assertEquals(3, shape()!!.vertices.size)
        button(R.string.free_flick_shape_remove); assertTrue(shape()!!.vertices.isEmpty())
        button(R.string.free_flick_shape_reset); assertNull(shape()!!.region.outline)
        button(R.string.popup_undo); assertTrue(shape()!!.region.outline!!.isCircle)
        dialogButton(android.R.id.button1); await { shape() == null }
        button(R.string.popup_save)
        assertTrue(repository.load("は").maps.single().regions.first().outline!!.isCircle)
        assertEquals(other, repository.load("か"))
        ins.runOnMainSync { activity()!!.finish() }
    }

    @Test fun realImeUsesShapeContoursForDirectHitsNearestSelectionAndAboveKeyGuide() {
        assumeTrue(context.packageName.endsWith(".popuptest"))
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val args = InstrumentationRegistry.getArguments()
        val guide = args.getString("presentation") == "guide"
        val floating = args.getString("surface") == "floating"
        val landscape = args.getString("orientation") == "landscape"
        val originalIme = shell("settings get secure default_input_method").trim()
        val targetIme = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        val initial = SumirePopupDefaults.create("は")
        val map = initial.maps.single()
        val config = initial.withMap(map.copy(regions = listOf(
            map.regions[0].copy(action = PopupAction(text = "○"), outline = PopupOutline()),
            map.regions[1].copy(action = PopupAction(text = "★"), outline = PopupOutline(listOf(PopupVertex(0f, 0f), PopupVertex(1f, 0f), PopupVertex(1f, .35f), PopupVertex(.35f, .35f), PopupVertex(.35f, 1f), PopupVertex(0f, 1f))))
        )))
        SumirePopupRepository(context).save(config)
        prefs.edit().putString("keyboard_order_preference", "[\"SUMIRE\"]").putBoolean("save_last_used_keyboard", false)
            .putString("sumire_keyboard_style_preference", SumirePopupDefaults.STYLE)
            .putBoolean("keyboard_floating_preference", floating).putBoolean("landscape_force_qwerty_preference", false)
            .putBoolean("live_conversion_preference", false).putBoolean("learn_dictionary_preference", false)
            .putBoolean(FreeFlickPreferences.NEAREST, false).putBoolean(FreeFlickPreferences.TEXT, true)
            .putString(FreeFlickPreferences.PRESENTATION, if (guide) "guide_above_key" else "around_key").commit()
        try {
            shell("ime enable $targetIme"); shell("ime set $targetIme")
            ins.startActivitySync(Intent(context, FastInputHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await { host() != null }
            ins.runOnMainSync { host()!!.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            await { (context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) == landscape }
            for ((point, expected) in listOf(((-1.45f) to (-.35f)) to "", ((-1.05f) to 0f) to "○", (.30f to -.75f) to "", (-.36f to -.76f) to "★")) {
                reset(); val key = stableKey(); val down = SystemClock.uptimeMillis()
                inject(down, MotionEvent.ACTION_DOWN, key, 0f, 0f)
                inject(down, MotionEvent.ACTION_MOVE, key, point.first, point.second)
                if (guide) {
                    await { windows().any { it is PopupMapGuideView } }
                    ins.runOnMainSync { assertEquals(config.maps.single(), windows().filterIsInstance<PopupMapGuideView>().single().map) }
                }
                inject(down, MotionEvent.ACTION_UP, key, point.first, point.second)
                await { host()?.editText?.text?.toString() == expected }
            }
            prefs.edit().putBoolean(FreeFlickPreferences.NEAREST, true).commit()
            reset(); val key = stableKey(); val down = SystemClock.uptimeMillis()
            inject(down, MotionEvent.ACTION_DOWN, key, 0f, 0f)
            inject(down, MotionEvent.ACTION_MOVE, key, -1.45f, -.35f)
            inject(down, MotionEvent.ACTION_UP, key, -1.45f, -.35f)
            await { host()?.editText?.text?.toString() == "○" }
        } finally {
            val now = SystemClock.uptimeMillis()
            MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN; automation.injectInputEvent(this, true); recycle()
            }
            ins.runOnMainSync { host()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; host()?.finish() }
            shell("ime set $originalIme")
        }
    }
    private fun host() = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<FastInputHostActivity>().firstOrNull()
    private fun reset() { ins.runOnMainSync { host()!!.restartEditorInput(true) }; SystemClock.sleep(250) }
    private fun stableKey(): Rect {
        var previous = key()
        var unchanged = 0
        repeat(100) {
            SystemClock.sleep(100)
            val current = key()
            unchanged = if (current == previous) unchanged + 1 else 0
            if (unchanged >= 4) return current
            previous = current
        }
        error("Source key geometry did not settle")
    }
    private fun key(): Rect {
        repeat(400) {
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            fun visit(node: AccessibilityNodeInfo) { nodes += node; repeat(node.childCount) { node.getChild(it)?.let(::visit) } }
            automation.windows.forEach { it.root?.let(::visit) }
            nodes.firstOrNull { it.packageName?.toString() == context.packageName && it.isVisibleToUser && it.isClickable && it.text?.toString()?.lineSequence()?.firstOrNull()?.trim() == "は" }
                ?.let { return Rect().also(it::getBoundsInScreen) }
            SystemClock.sleep(50)
        }
        error("Source key not found")
    }
    private fun inject(down: Long, action: Int, key: Rect, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, key.centerX() + x * key.width(), key.centerY() + y * key.height(), 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        assertTrue(automation.injectInputEvent(event, true)); event.recycle(); SystemClock.sleep(100)
    }
}
