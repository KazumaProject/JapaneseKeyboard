package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Typeface
import android.graphics.Rect
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import androidx.appcompat.widget.AppCompatImageButton
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyCharacterCase
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyIconRef
import com.kazumaproject.custom_keyboard.data.KeyIconType
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlickKeyboardViewModeKeyTextSizeTest {
    @Test
    fun modeLabelsUseSpecialTextSizeAndIgnoreIconAndNormalTextSizing() {
        val keyboard = createView()
        keyboard.setKeyboard(layout(modeKeys + key("改行", KeyAction.NewLine)))
        for (size in listOf(8f, 16f, 32f)) {
            for (iconScale in listOf(40, 80, 200)) {
                keyboard.applyKeySizing(160, 160, iconScale, 24f, size)
                measure(keyboard)
                modeLabels.forEachIndexed { index, label ->
                    val button = keyboard.getChildAt(index) as AutoSizeButton
                    assertEquals(label, button.text.toString())
                    assertEquals(1, button.maxLines)
                    assertEquals(label, button.contentDescription)
                    assertEquals(sp(keyboard, size), button.textSize, 0.01f)
                    assertEquals((keyboard.getChildAt(3) as AutoSizeButton).textSize, button.textSize, 0.01f)
                }
            }
        }
        keyboard.setKeyCharacterCase(KeyCharacterCase.UPPERCASE)
        keyboard.setKeyCharacterCase(KeyCharacterCase.AS_DEFINED)
        assertEquals("ABC", (keyboard.getChildAt(0) as AutoSizeButton).text.toString())
    }

    @Test
    fun generatedLayoutsAndPreviewRenderStandardModeIconsAsText() {
        val keyboard = createView()
        val layouts = listOf(KeyboardDefaultLayouts.defaultLayout()) +
            listOf("toggle", "flick").flatMap { layoutType ->
                KeyboardInputMode.entries.map { mode ->
                    KeyboardDefaultLayouts.createFinalLayout(mode, emptyMap(), layoutType, "default")
                }
            }
        layouts.forEach { layout ->
            keyboard.setKeyboard(layout)
            val labels = (0 until keyboard.childCount).mapNotNull {
                (keyboard.getChildAt(it) as? AutoSizeButton)?.text?.toString()
            }
            layout.keys.filter { it.isSpecialKey && it.drawableResId in modeKeys.map(KeyData::drawableResId) }
                .forEach { modeKey ->
                    val expected = modeLabels[modeKeys.indexOfFirst { it.drawableResId == modeKey.drawableResId }]
                    assertTrue("Missing mode label $expected", expected in labels)
                }
        }
    }

    @Test
    fun customFontAndNarrowKeyUseExistingTextFitting() {
        val keyboard = createView()
        keyboard.setKeyboard(layout(modeKeys + key("改行", KeyAction.NewLine)))
        keyboard.applyKeySizing(160, 160, 80, 16f, 32f)
        measure(keyboard)
        val baseline = (keyboard.getChildAt(0) as AutoSizeButton).typeface
        keyboard.setKeyboardFont(KeyboardFontSnapshot(Typeface.MONOSPACE, 1))
        measure(keyboard)
        val buttons = (0 until keyboard.childCount).map { keyboard.getChildAt(it) as AutoSizeButton }
        buttons.forEach {
            assertEquals(Typeface.create(Typeface.MONOSPACE, baseline.style), it.typeface)
            assertEquals(sp(keyboard, 32f), it.textSize, 0.01f)
            val bounds = Rect()
            it.paint.getTextBounds(it.text.toString(), 0, it.text.length, bounds)
            it.layout(0, 0, it.paddingLeft + it.paddingRight + bounds.width() / 2,
                it.paddingTop + it.paddingBottom + bounds.height() * 2)
            it.refreshTextSize()
            assertTrue("Text should shrink to fit", it.textSize < sp(keyboard, 32f))
            it.paint.getTextBounds(it.text.toString(), 0, it.text.length, bounds)
            assertTrue("Text bounds $bounds must fit key width ${it.width} with padding ${it.paddingLeft}/${it.paddingRight}",
                bounds.width() <= it.width - it.paddingLeft - it.paddingRight)
            if (it.maxLines == 1) {
                assertTrue(it.paint.measureText(it.text.toString()) <= it.width - it.paddingLeft - it.paddingRight)
            }
        }
        keyboard.setKeyboardFont(KeyboardFontSnapshot(revision = 2))
        assertEquals(baseline, buttons.first().typeface)
    }

    @Test
    fun explicitIconsAndDoNothingKeepExistingPresentation() {
        val keyboard = createView()
        val icons = listOf(
            KeyIconRef(KeyIconType.DRAWABLE_RESOURCE_NAME, "input_mode_english_custom"),
            KeyIconRef(KeyIconType.USER_IMAGE_FILE, "custom_key_icons/missing.png")
        )
        keyboard.setKeyboard(layout(icons.map { modeKeys.first().copy(icon = it) } +
            modeKeys.first().copy(action = KeyAction.DoNothing) +
            modeKeys.first().copy(icon = KeyIconRef.ActionDefault)))
        assertTrue(keyboard.getChildAt(0) is AppCompatImageButton)
        assertTrue(keyboard.getChildAt(1) is AppCompatImageButton)
        assertEquals("", (keyboard.getChildAt(2) as AutoSizeButton).text.toString())
        assertEquals("ABC", (keyboard.getChildAt(3) as AutoSizeButton).text.toString())
    }

    @Test
    fun dynamicKeySwitchesBetweenModeTextAndImageWithoutChangingActions() {
        val keyboard = createView()
        val actions = mutableListOf<KeyAction>()
        keyboard.setOnKeyboardActionListener(object : FlickKeyboardView.OnKeyboardActionListener {
            override fun onPress(action: KeyAction) = Unit
            override fun onAction(action: KeyAction, isFlick: Boolean) { actions += action }
            override fun onActionLongPress(action: KeyAction) = Unit
            override fun onActionUpAfterLongPress(action: KeyAction) = Unit
            override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
            override fun onFlickActionLongPress(action: KeyAction) = Unit
            override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
        })
        val states = modeKeys.map { FlickAction.Action(it.action!!, it.label, it.drawableResId) } +
            FlickAction.Action(KeyAction.Paste, "Paste", com.kazumaproject.core.R.drawable.content_paste_24px)
        keyboard.setKeyboard(layout(listOf(modeKeys.first().copy(keyId = "mode", dynamicStates = states, isHiLighted = true))))
        assertTrue(keyboard.getChildAt(0).isPressed)
        for (index in listOf(1, 3, 2, 0)) {
            keyboard.updateDynamicKey("mode", index)
            val button = keyboard.getChildAt(0)
            assertTrue(button.isPressed)
            if (index == 3) assertTrue(button is AppCompatImageButton)
            else assertEquals(modeLabels[index], (button as AutoSizeButton).text.toString())
            button.performClick()
            assertEquals(states[index].action, actions.last())
        }
    }

    private val modeLabels = listOf("ABC", "123", "あいう")
    private val modeKeys = listOf(
        key("SwitchToEnglish", KeyAction.SwitchToEnglishLayout, com.kazumaproject.core.R.drawable.input_mode_english_custom),
        key("SwitchToNumber", KeyAction.SwitchToNumberLayout, com.kazumaproject.core.R.drawable.input_mode_number_select_custom),
        key("SwitchToKana", KeyAction.SwitchToKanaLayout, com.kazumaproject.core.R.drawable.input_mode_japanese_select_custom)
    )

    private fun key(label: String, action: KeyAction, drawable: Int? = null) =
        KeyData(label, 0, 0, false, action, drawableResId = drawable, isSpecialKey = true)

    private fun layout(keys: List<KeyData>) = KeyboardLayout(
        keys = keys.mapIndexed { index, key -> key.copy(column = index) },
        flickKeyMaps = emptyMap(), rowCount = 1, columnCount = keys.size
    )

    private fun createView() = FlickKeyboardView(ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(),
        com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
    ))

    private fun measure(keyboard: FlickKeyboardView) {
        keyboard.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY))
        keyboard.layout(0, 0, 1200, 200)
    }

    private fun sp(view: View, size: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, view.resources.displayMetrics)
}
