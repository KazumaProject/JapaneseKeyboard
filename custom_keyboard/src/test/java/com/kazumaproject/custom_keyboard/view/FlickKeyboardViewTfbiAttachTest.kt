package com.kazumaproject.custom_keyboard.view

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.data.popup.TfbiPopupPresentationMode
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.TfbiGuidePopupState
import com.kazumaproject.custom_keyboard.controller.TfbiHierarchicalFlickController
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.robolectric.Robolectric
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickKeyboardViewTfbiAttachTest {

    @Test
    fun secondFlickAttachesControllerToNaKey() {
        val layout = createLayout("second-flick")
        val keyboard = createView()

        keyboard.setKeyboard(layout)

        assertNotNull(layout.twoStepFlickKeyMaps["な"])
        assertEquals(11, controllersOf<TfbiInputController>(keyboard).size)
        assertNotNull(keyboard.findControllerForLabel("な", TfbiInputController::class.java))
    }

    @Test
    fun thirdFlickAttachesControllerToNaKey() {
        val layout = createLayout("third-flick")
        val keyboard = createView()

        keyboard.setKeyboard(layout)

        assertNotNull(layout.hierarchicalFlickMaps["な"])
        assertEquals(11, controllersOf<TfbiHierarchicalFlickController>(keyboard).size)
        assertNotNull(
            keyboard.findControllerForLabel("な", TfbiHierarchicalFlickController::class.java)
        )
    }

    @Test
    fun secondFlickGuideShowsNaOnTouchDown() {
        val state = touchDownState("second-flick", TfbiInputController::class.java)

        assertEquals("な", state.currentText)
        assertEquals(0.5f, state.fingerPosition?.x ?: -1f, 0.001f)
        assertEquals(0.5f, state.fingerPosition?.y ?: -1f, 0.001f)
    }

    @Test
    fun thirdFlickGuideShowsNaOnTouchDown() {
        val state = touchDownState("third-flick", TfbiHierarchicalFlickController::class.java)

        assertEquals("な", state.currentText)
        assertEquals(0.5f, state.fingerPosition?.x ?: -1f, 0.001f)
        assertEquals(0.5f, state.fingerPosition?.y ?: -1f, 0.001f)
    }

    @Test
    fun secondFlickGuideTracksFingerPosition() {
        val state = touchStateAfterLeftFlick("second-flick", TfbiInputController::class.java)

        assertEquals(0f, state.fingerPosition?.x ?: -1f, 0.001f)
        assertEquals(0.5f, state.fingerPosition?.y ?: -1f, 0.001f)
    }

    @Test
    fun thirdFlickGuideTracksFingerPosition() {
        val state = touchStateAfterLeftFlick(
            "third-flick",
            TfbiHierarchicalFlickController::class.java
        )

        assertEquals(0f, state.fingerPosition?.x ?: -1f, 0.001f)
        assertEquals(0.5f, state.fingerPosition?.y ?: -1f, 0.001f)
    }

    @Test
    fun thirdFlickGuideShowsNaStageCharacterOnLeftFlick() {
        val state = touchStateAfterLeftFlick(
            "third-flick",
            TfbiHierarchicalFlickController::class.java
        )

        assertEquals("に", state.currentText)
    }

    @Test
    fun secondFlickGuideUsesGuideCellForSecondStage() {
        val state = touchStateAfterFlickToCell(
            "second-flick",
            TfbiInputController::class.java,
            targetX = 0.1f,
            targetY = 1f
        )

        assertEquals("じ", state.currentText)
        assertEquals(TfbiFlickDirection.DOWN_LEFT, state.selectedOption)
    }

    @Test
    fun thirdFlickGuideUsesBottomCenterCellForSecondStage() {
        val state = touchStateAfterFlickToCell(
            "third-flick",
            TfbiHierarchicalFlickController::class.java,
            targetX = 0.65f,
            targetY = 1f
        )

        assertEquals("しょ", state.currentText)
        assertEquals("しょ", state.optionLabels[TfbiFlickDirection.DOWN])
    }

    @Test
    fun allBuiltInKanaLayoutVariantsAttachNaController() {
        listOf("toggle", "flick", "switch-mode-effective").forEach { layoutType ->
            val secondLayout = KeyboardDefaultLayouts.createFinalLayout(
                mode = KeyboardInputMode.HIRAGANA,
                dynamicKeyStates = emptyMap(),
                inputLayoutType = layoutType,
                inputStyle = "second-flick"
            )
            val thirdLayout = KeyboardDefaultLayouts.createFinalLayout(
                mode = KeyboardInputMode.HIRAGANA,
                dynamicKeyStates = emptyMap(),
                inputLayoutType = layoutType,
                inputStyle = "third-flick"
            )

            assertNotNull("$layoutType second-flick na map", secondLayout.twoStepFlickKeyMaps["な"])
            assertNotNull("$layoutType third-flick na map", thirdLayout.hierarchicalFlickMaps["な"])
            assertEquals(
                "$layoutType second-flick key type",
                KeyType.TWO_STEP_FLICK,
                secondLayout.keys.first { it.label == "な" }.keyType
            )
            assertEquals(
                "$layoutType third-flick key type",
                KeyType.HIERARCHICAL_FLICK,
                thirdLayout.keys.first { it.label == "な" }.keyType
            )
        }
    }

    private fun createView(): FlickKeyboardView {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return FlickKeyboardView(
            ContextThemeWrapper(
                context,
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
            )
        )
    }

    private fun touchDownState(
        inputStyle: String,
        controllerClass: Class<*>
    ): TfbiGuidePopupState {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val keyboard = FlickKeyboardView(
            ContextThemeWrapper(
                activity,
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
            )
        ).apply {
            setTfbiPopupPresentationMode(TfbiPopupPresentationMode.GUIDE_ABOVE_KEY)
            setKeyboard(createLayout(inputStyle))
        }
        activity.setContentView(keyboard)
        keyboard.measure(exactly(1080), exactly(1000))
        keyboard.layout(0, 0, 1080, 1000)

        val naButton = (0 until keyboard.childCount)
            .map { keyboard.getChildAt(it) }
            .filterIsInstance<AutoSizeButton>()
            .first { it.text.toString() == "な" }
        val down = MotionEvent.obtain(
            0L,
            0L,
            MotionEvent.ACTION_DOWN,
            naButton.width / 2f,
            naButton.height / 2f,
            0
        )
        try {
            assertNotNull(naButton.dispatchTouchEvent(down))
        } finally {
            down.recycle()
        }

        val controller = findControllerForLabel("な", controllerClass, keyboard)
        val host = controller!!::class.java.getDeclaredMethod("getGuidePopupHost").apply {
            isAccessible = true
        }.invoke(controller)
        val guideView = host!!::class.java.getDeclaredField("guideView").apply {
            isAccessible = true
        }.get(host)
        val state = guideView!!::class.java.getDeclaredField("state").apply {
            isAccessible = true
        }.get(guideView)
        return state as TfbiGuidePopupState
    }

    private fun touchStateAfterLeftFlick(
        inputStyle: String,
        controllerClass: Class<*>
    ): TfbiGuidePopupState {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val keyboard = FlickKeyboardView(
            ContextThemeWrapper(
                activity,
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
            )
        ).apply {
            setTfbiPopupPresentationMode(TfbiPopupPresentationMode.GUIDE_ABOVE_KEY)
            setKeyboard(createLayout(inputStyle))
        }
        activity.setContentView(keyboard)
        keyboard.measure(exactly(1080), exactly(1000))
        keyboard.layout(0, 0, 1080, 1000)

        val naButton = (0 until keyboard.childCount)
            .map { keyboard.getChildAt(it) }
            .filterIsInstance<AutoSizeButton>()
            .first { it.text.toString() == "な" }
        val centerX = naButton.width / 2f
        val centerY = naButton.height / 2f
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, centerX, centerY, 0)
        val move = MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_MOVE, 0f, centerY, 0)
        try {
            assertNotNull(naButton.dispatchTouchEvent(down))
            assertNotNull(naButton.dispatchTouchEvent(move))
        } finally {
            down.recycle()
            move.recycle()
        }

        val controller = findControllerForLabel("な", controllerClass, keyboard)
        val host = controller!!::class.java.getDeclaredMethod("getGuidePopupHost").apply {
            isAccessible = true
        }.invoke(controller)
        val guideView = host!!::class.java.getDeclaredField("guideView").apply {
            isAccessible = true
        }.get(host)
        val state = guideView!!::class.java.getDeclaredField("state").apply {
            isAccessible = true
        }.get(guideView)
        return state as TfbiGuidePopupState
    }

    private fun touchStateAfterFlickToCell(
        inputStyle: String,
        controllerClass: Class<*>,
        targetX: Float,
        targetY: Float
    ): TfbiGuidePopupState {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val keyboard = FlickKeyboardView(
            ContextThemeWrapper(
                activity,
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
            )
        ).apply {
            setTfbiPopupPresentationMode(TfbiPopupPresentationMode.GUIDE_ABOVE_KEY)
            setKeyboard(createLayout(inputStyle))
        }
        activity.setContentView(keyboard)
        keyboard.measure(exactly(1080), exactly(1000))
        keyboard.layout(0, 0, 1080, 1000)

        val saButton = (0 until keyboard.childCount)
            .map { keyboard.getChildAt(it) }
            .filterIsInstance<AutoSizeButton>()
            .first { it.text.toString() == "さ" }
        val centerX = saButton.width / 2f
        val centerY = saButton.height / 2f
        val targetTouchX = saButton.width * targetX
        val targetTouchY = saButton.height * targetY
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, centerX, centerY, 0)
        val firstMove = MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_MOVE, 0f, centerY, 0)
        val secondMove = MotionEvent.obtain(
            0L,
            20L,
            MotionEvent.ACTION_MOVE,
            targetTouchX,
            targetTouchY,
            0
        )
        try {
            assertNotNull(saButton.dispatchTouchEvent(down))
            assertNotNull(saButton.dispatchTouchEvent(firstMove))
            assertNotNull(saButton.dispatchTouchEvent(secondMove))
        } finally {
            down.recycle()
            firstMove.recycle()
            secondMove.recycle()
        }

        val controller = findControllerForLabel("さ", controllerClass, keyboard)
        val host = controller!!::class.java.getDeclaredMethod("getGuidePopupHost").apply {
            isAccessible = true
        }.invoke(controller)
        val guideView = host!!::class.java.getDeclaredField("guideView").apply {
            isAccessible = true
        }.get(host)
        val state = guideView!!::class.java.getDeclaredField("state").apply {
            isAccessible = true
        }.get(guideView)
        return state as TfbiGuidePopupState
    }

    private fun exactly(size: Int): Int =
        android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY)

    private fun createLayout(inputStyle: String) = KeyboardDefaultLayouts.createFinalLayout(
        mode = KeyboardInputMode.HIRAGANA,
        dynamicKeyStates = emptyMap(),
        inputLayoutType = "flick",
        inputStyle = inputStyle
    )

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> controllersOf(keyboard: FlickKeyboardView): List<T> {
        val field = FlickKeyboardView::class.java.getDeclaredField(
            when (T::class) {
                TfbiInputController::class -> "tfbiControllers"
                TfbiHierarchicalFlickController::class -> "hierarchicalTfbiControllers"
                else -> error("Unsupported controller type")
            }
        ).apply { isAccessible = true }
        return field.get(keyboard) as List<T>
    }

    private fun <T : Any> FlickKeyboardView.findControllerForLabel(
        label: String,
        controllerClass: Class<T>
    ): T? {
        val keyInfos = FlickKeyboardView::class.java.getDeclaredField("keyInfos").apply {
            isAccessible = true
        }.get(this) as List<*>
        return keyInfos.firstOrNull { info ->
            val keyData = info!!::class.java.getDeclaredField("keyData").apply {
                isAccessible = true
            }.get(info)
            val dataLabel = keyData!!::class.java.getDeclaredField("label").apply {
                isAccessible = true
            }.get(keyData) as String
            dataLabel == label
        }?.let { info ->
            val controller = info!!::class.java.getDeclaredField("controller").apply {
                isAccessible = true
            }.get(info)
            controller?.takeIf(controllerClass::isInstance) as T?
        }
    }

    private fun findControllerForLabel(
        label: String,
        controllerClass: Class<*>,
        keyboard: FlickKeyboardView
    ): Any? {
        val keyInfos = FlickKeyboardView::class.java.getDeclaredField("keyInfos").apply {
            isAccessible = true
        }.get(keyboard) as List<*>
        return keyInfos.firstOrNull { info ->
            val keyData = info!!::class.java.getDeclaredField("keyData").apply {
                isAccessible = true
            }.get(info)
            val dataLabel = keyData!!::class.java.getDeclaredField("label").apply {
                isAccessible = true
            }.get(keyData) as String
            dataLabel == label
        }?.let { info ->
            val controller = info!!::class.java.getDeclaredField("controller").apply {
                isAccessible = true
            }.get(info)
            controller?.takeIf(controllerClass::isInstance)
        }
    }
}
