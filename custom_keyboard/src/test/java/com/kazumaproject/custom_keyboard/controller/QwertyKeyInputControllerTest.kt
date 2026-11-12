package com.kazumaproject.custom_keyboard.controller

import android.app.Activity
import android.os.Looper
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.data.popup.QwertyPopupViewStyleSet
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.custom_keyboard.data.QwertyKeyLayer
import com.kazumaproject.custom_keyboard.data.QwertyPopupSlot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QwertyKeyInputControllerTest {
    @Test fun tapHoldCancelAndDisabledPreviewCommitOnlyTheirOwnText() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        activity.window.decorView.layout(0, 0, 1080, 2400)
        root.layout(0, 0, 1080, 2400)
        val key = Button(activity)
        root.addView(key, FrameLayout.LayoutParams(100, 100))
        key.layout(300, 1500, 400, 1600)
        val outputs = mutableListOf<String>()
        var layer = QwertyKeyLayer("a", slots = listOf(QwertyPopupSlot(), QwertyPopupSlot("long text")))
        val style = PopupViewStyle(100, 28f)
        val controller = QwertyKeyInputController(GestureSessionConfigSource {
            GestureSessionConfig(1L, 100, 20f, longPressTimeoutMillis = 300)
        }, { layer }, { QwertyPopupViewStyleSet(style, style) }, { KeyboardFontSnapshot() },
            { false }, { null }, {}, { outputs.add(it) })
        controller.attach(key)
        try {
            key.send(MotionEvent.ACTION_DOWN)
            key.send(MotionEvent.ACTION_UP)
            assertEquals(listOf("a"), outputs)
            key.send(MotionEvent.ACTION_DOWN)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
            key.send(MotionEvent.ACTION_UP)
            assertEquals(listOf("a", "long text"), outputs)
            key.send(MotionEvent.ACTION_DOWN)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
            key.send(MotionEvent.ACTION_CANCEL)
            key.send(MotionEvent.ACTION_UP)
            assertEquals(2, outputs.size)
            layer = QwertyKeyLayer("no alternatives")
            key.send(MotionEvent.ACTION_DOWN)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
            key.send(MotionEvent.ACTION_UP)
            assertEquals("no alternatives", outputs.last())
            key.send(MotionEvent.ACTION_DOWN)
            controller.cancel()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
            assertEquals(3, outputs.size)
        } finally { controller.cancel(); activity.finish() }
    }

    private fun Button.send(action: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        MotionEvent.obtain(now, now, action, 50f, 50f, 0).also { dispatchTouchEvent(it); it.recycle() }
    }
}
