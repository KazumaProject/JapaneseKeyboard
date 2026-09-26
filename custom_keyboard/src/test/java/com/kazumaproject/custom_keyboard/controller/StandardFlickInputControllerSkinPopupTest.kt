package com.kazumaproject.custom_keyboard.controller

import android.app.Activity
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.domain.flick.FlickThresholdShape
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.key_window.KeyWindowLayout
import com.kazumaproject.core.ui.skin.PopupDirection
import com.kazumaproject.core.ui.skin.SkinGuidePopup
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StandardFlickInputControllerSkinPopupTest {

    @Test
    fun tapOnlyNumberDoesNotShowAnEmptyFlickGuideForLightOrDarkSkin() {
        for (skin in listOf(KeyboardSkinId.CUPERTINO_LIGHT, KeyboardSkinId.CUPERTINO_DARK)) {
            val (_, button, controller) = host(skin)
            controller.attach(button, mapOf(FlickDirection.TAP to "7"), drawable())

            button.dispatch(MotionEvent.ACTION_DOWN, 40f, 40f)

            assertNull(guide(controller))
            assertFalse(legacyPopup(controller).isShowing)
            controller.cancel()
        }
    }

    @Test
    fun flickGuideKeepsItsFrameStableSelectsMappedDirectionAndUsesSkinReleaseAnimation() {
        val (activity, button, controller) = host(KeyboardSkinId.CUPERTINO_LIGHT)
        val committed = mutableListOf<String>()
        controller.listener = listener(onFlick = committed::add)
        controller.attach(
            button,
            mapOf(
                FlickDirection.TAP to ",",
                FlickDirection.UP to "-",
                FlickDirection.DOWN to "/",
                FlickDirection.UP_LEFT_FAR to "+",
                FlickDirection.UP_RIGHT_FAR to "*",
            ),
            drawable(),
        )

        button.dispatch(MotionEvent.ACTION_DOWN, 40f, 40f)
        val guide = requireNotNull(guide(controller))
        assertTrue(guide.isShowing)
        val root = guideContent(guide)
        val originalFrame = intArrayOf(root.left, root.top, root.width, root.height)
        assertEquals(0f, root.alpha, 0f)
        assertEquals(.94f, root.scaleX, .01f)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(110))
        assertEquals(1f, root.alpha, .01f)
        assertEquals(1f, root.scaleX, .01f)
        assertEquals(setOf(PopupDirection.CENTER, PopupDirection.TOP, PopupDirection.BOTTOM,
            PopupDirection.LEFT, PopupDirection.RIGHT), visibleDirections(root))

        button.dispatch(MotionEvent.ACTION_MOVE, 120f, 40f)
        assertEquals(originalFrame.toList(), listOf(root.left, root.top, root.width, root.height))
        assertTrue(cell(root, PopupDirection.RIGHT).skinSelected)
        assertFalse(cell(root, PopupDirection.CENTER).skinSelected)

        button.dispatch(MotionEvent.ACTION_UP, 120f, 40f)
        assertEquals(listOf("*"), committed)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        assertFalse(guide.isShowing)

        activity.finish()
    }

    @Test
    fun cancelDismissesTheGuideImmediatelyWithoutCommitting() {
        val (activity, button, controller) = host(KeyboardSkinId.CUPERTINO_DARK)
        val committed = mutableListOf<String>()
        controller.listener = listener(onFlick = committed::add)
        controller.attach(
            button,
            mapOf(FlickDirection.TAP to ".", FlickDirection.UP to "%"),
            drawable(),
        )

        button.dispatch(MotionEvent.ACTION_DOWN, 40f, 40f)
        val guide = requireNotNull(guide(controller))
        assertTrue(guide.isShowing)

        button.dispatch(MotionEvent.ACTION_CANCEL, 40f, 40f)

        assertFalse(guide.isShowing)
        assertTrue(committed.isEmpty())
        activity.finish()
    }

    @Test
    fun aLongHoldKeepsTheGuideAndSelectionInPlaceUntilRelease() {
        val (activity, button, controller) = host(KeyboardSkinId.CUPERTINO_DARK)
        controller.attach(
            button,
            mapOf(FlickDirection.TAP to ".", FlickDirection.UP to "%"),
            drawable(),
        )

        button.dispatch(MotionEvent.ACTION_DOWN, 40f, 40f)
        val guide = requireNotNull(guide(controller))
        val root = guideContent(guide)
        val initialFrame = listOf(root.left, root.top, root.width, root.height)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(900))

        assertTrue(guide.isShowing)
        assertEquals(initialFrame, listOf(root.left, root.top, root.width, root.height))
        assertTrue(cell(root, PopupDirection.CENTER).skinSelected)

        controller.cancel()
        activity.finish()
    }

    @Test
    fun aNewKeyReplacesThePreviousGuideDuringItsReleaseTransition() {
        val (activity, firstButton, firstController) = host(KeyboardSkinId.CUPERTINO_LIGHT)
        firstController.attach(
            firstButton,
            mapOf(FlickDirection.TAP to "1", FlickDirection.UP to "A"),
            drawable(),
        )
        firstButton.dispatch(MotionEvent.ACTION_DOWN, 40f, 40f)
        val firstGuide = requireNotNull(guide(firstController))
        firstButton.dispatch(MotionEvent.ACTION_UP, 40f, 40f)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(16))

        val root = firstButton.parent as FrameLayout
        val secondButton = Button(firstButton.context)
        root.addView(secondButton, FrameLayout.LayoutParams(200, 160))
        secondButton.layout(650, 1600, 850, 1760)
        val secondController = StandardFlickInputController(
            firstButton.context,
            GestureSessionConfigSource {
                GestureSessionConfig(
                    settingsRevision = 1L,
                    flickSensitivity = 100,
                    flickThresholdPx = 20f,
                    longPressTimeoutMillis = 300L,
                    flickThresholdShape = FlickThresholdShape.Radial,
                )
            },
        )
        secondController.applyPopupViewStyle(PopupViewStyle(100, 19f, skinId = KeyboardSkinId.CUPERTINO_LIGHT))
        secondController.attach(
            secondButton,
            mapOf(FlickDirection.TAP to "2", FlickDirection.UP to "B"),
            drawable(),
        )

        secondButton.dispatch(MotionEvent.ACTION_DOWN, 40f, 40f)

        assertFalse("The previous key's release transition is canceled", firstGuide.isShowing)
        val secondGuide = requireNotNull(guide(secondController))
        val secondContent = guideContent(secondGuide)
        assertTrue(secondGuide.isShowing)
        assertEquals("2", guideLabel(secondContent, PopupDirection.CENTER).text.toString())
        assertEquals("B", guideLabel(secondContent, PopupDirection.TOP).text.toString())
        firstController.cancel()
        secondController.cancel()
        activity.finish()
    }

    @Test
    fun configuredPopupTextSizeIsAppliedToSkinGuideLabels() {
        val (activity, button, controller) = host(
            KeyboardSkinId.CUPERTINO_DARK,
            textSizeSp = 17.5f,
        )
        controller.attach(button, mapOf(FlickDirection.TAP to ".", FlickDirection.UP to "%"), drawable())
        button.dispatch(MotionEvent.ACTION_DOWN, 40f, 40f)

        val label = guideLabel(guideContent(requireNotNull(guide(controller))), PopupDirection.CENTER)
        assertEquals(17.5f, label.textSize / label.resources.displayMetrics.density, .05f)

        controller.cancel()
        activity.finish()
    }

    private fun host(
        skin: KeyboardSkinId,
        textSizeSp: Float = 19f,
    ): Triple<Activity, Button, StandardFlickInputController> {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val context = ContextThemeWrapper(
            activity,
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar,
        )
        val root = FrameLayout(context)
        activity.setContentView(root)
        activity.window.decorView.layout(0, 0, 1080, 2400)
        root.measure(exactly(1080), exactly(2400))
        root.layout(0, 0, 1080, 2400)
        val button = Button(context)
        root.addView(button, FrameLayout.LayoutParams(200, 160))
        button.layout(300, 1600, 500, 1760)

        val controller = StandardFlickInputController(
            context,
            GestureSessionConfigSource {
                GestureSessionConfig(
                    settingsRevision = 1L,
                    flickSensitivity = 100,
                    flickThresholdPx = 20f,
                    longPressTimeoutMillis = 300L,
                    flickThresholdShape = FlickThresholdShape.Radial,
                )
            },
        )
        controller.applyPopupViewStyle(PopupViewStyle(100, textSizeSp, skinId = skin))
        return Triple(activity, button, controller)
    }

    private fun drawable() = SegmentedBackgroundDrawable(
        label = "1",
        baseColor = 0,
        highlightColor = 0,
        textColor = 0,
        cornerRadius = 0f,
    )

    private fun listener(onFlick: (String) -> Unit) =
        object : StandardFlickInputController.StandardFlickListener {
            override fun onPress(character: String) = Unit
            override fun onFlick(character: String) = onFlick(character)
        }

    private fun guide(controller: StandardFlickInputController): SkinGuidePopup? =
        StandardFlickInputController::class.java.getDeclaredField("skinGuidePopup")
            .apply { isAccessible = true }
            .get(controller) as? SkinGuidePopup

    private fun legacyPopup(controller: StandardFlickInputController): android.widget.PopupWindow =
        StandardFlickInputController::class.java.getDeclaredField("popupWindow")
            .apply { isAccessible = true }
            .get(controller) as android.widget.PopupWindow

    private fun guideContent(guide: SkinGuidePopup): FrameLayout =
        SkinGuidePopup::class.java.getDeclaredField("content")
            .apply { isAccessible = true }
            .get(guide) as FrameLayout

    private fun visibleDirections(content: FrameLayout): Set<PopupDirection> =
        listOf(PopupDirection.CENTER, PopupDirection.LEFT, PopupDirection.TOP,
            PopupDirection.RIGHT, PopupDirection.BOTTOM)
            .filterTo(mutableSetOf()) { cell(content, it).visibility == View.VISIBLE }

    private fun cell(content: FrameLayout, direction: PopupDirection): KeyWindowLayout =
        (0 until content.childCount).map { content.getChildAt(it) as KeyWindowLayout }
            .single { it.skinDirection == direction }

    private fun guideLabel(content: FrameLayout, direction: PopupDirection): TextView =
        cell(content, direction).getChildAt(0) as TextView

    private fun exactly(size: Int) =
        View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun View.dispatch(action: Int, x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now + 1, action, x, y, 0)
        dispatchTouchEvent(event)
        event.recycle()
    }
}
