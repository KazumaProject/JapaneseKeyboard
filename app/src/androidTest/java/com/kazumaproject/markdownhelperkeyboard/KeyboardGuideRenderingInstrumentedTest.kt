package com.kazumaproject.markdownhelperkeyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.AutoSizeButton
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.tenkey.TenKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardGuideRenderingInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun tenKey_guidesAreSelectedIndependentlyForJapaneseEnglishAndNumber() {
        lateinit var tenKey: TenKey
        instrumentation.runOnMainSync {
            val context = themedContext()
            val root = LayoutInflater.from(context).inflate(R.layout.main_layout, null)
            tenKey = root.findViewById(R.id.keyboard_view)
            tenKey.setFlickGuideEnabled(
                japaneseEnabled = false,
                englishEnabled = true,
                numberEnabled = false
            )
        }

        assertEquals("か", key2Text(tenKey, InputMode.ModeJapanese))

        val englishGuideText = key2Text(tenKey, InputMode.ModeEnglish)
        assertEquals(3, englishGuideText.lines().size)
        assertTrue(englishGuideText.contains("a"))
        assertTrue(englishGuideText.contains("b"))
        assertTrue(englishGuideText.contains("c"))
        assertTrue(englishGuideText.contains("2"))

        val numberWithoutGuide = key2Text(tenKey, InputMode.ModeNumber)
        assertEquals(2, numberWithoutGuide.lines().size)
        assertEquals("2\n￥\$€", numberWithoutGuide)

        instrumentation.runOnMainSync {
            tenKey.setFlickGuideEnabled(
                japaneseEnabled = true,
                englishEnabled = false,
                numberEnabled = true
            )
        }
        assertEquals(3, key2Text(tenKey, InputMode.ModeJapanese).lines().size)
        assertEquals("ABC", key2Text(tenKey, InputMode.ModeEnglish))
        assertEquals(3, key2Text(tenKey, InputMode.ModeNumber).lines().size)
    }

    @Test
    fun tenKey_englishGuideProducesMeasurableRenderedDifference() {
        lateinit var tenKey: TenKey
        lateinit var withoutGuide: Bitmap
        lateinit var withGuide: Bitmap
        instrumentation.runOnMainSync {
            val context = themedContext()
            val root = LayoutInflater.from(context).inflate(R.layout.main_layout, null)
            tenKey = root.findViewById(R.id.keyboard_view)
            tenKey.setCurrentMode(InputMode.ModeEnglish)
            tenKey.setFlickGuideEnabled(false, false, false)
            withoutGuide = render(tenKey, width = 1080, height = 760)
            tenKey.setFlickGuideEnabled(false, true, false)
            withGuide = render(tenKey, width = 1080, height = 760)
        }

        val changedPixels = countChangedPixels(withoutGuide, withGuide)
        Log.i(MEASUREMENT_TAG, "TenKey English guide changedPixels=$changedPixels")
        assertTrue("Expected a visible TenKey guide difference", changedPixels > 1_000)
    }

    @Test
    fun tenKey_numberBracketGuideUsesDirectionalLayout() {
        lateinit var legacyIcon: Bitmap
        lateinit var guideIcon: Bitmap
        instrumentation.runOnMainSync {
            val root = LayoutInflater.from(themedContext()).inflate(R.layout.main_layout, null)
            val tenKey = root.findViewById<TenKey>(R.id.keyboard_view)
            val bracketKey = tenKey.findViewById<AppCompatImageButton>(
                com.kazumaproject.tenkey.R.id.key_small_letter
            )

            tenKey.setCurrentMode(InputMode.ModeNumber)
            tenKey.setFlickGuideEnabled(false, false, false)
            legacyIcon = renderDrawable(requireNotNull(bracketKey.drawable), 100, 100)

            tenKey.setFlickGuideEnabled(false, false, true)
            guideIcon = renderDrawable(requireNotNull(bracketKey.drawable), 100, 100)
        }

        val legacyTopPixels = countOpaquePixels(legacyIcon, 40, 10, 60, 38)
        val guideTopPixels = countOpaquePixels(guideIcon, 40, 10, 60, 38)
        val guideLeftPixels = countOpaquePixels(guideIcon, 10, 40, 38, 62)
        val guideCenterPixels = countOpaquePixels(guideIcon, 40, 40, 60, 62)
        val guideRightPixels = countOpaquePixels(guideIcon, 62, 40, 90, 62)
        val changedPixels = countChangedPixels(legacyIcon, guideIcon)

        Log.i(
            MEASUREMENT_TAG,
            "TenKey number bracket guide changedPixels=$changedPixels " +
                "top=$guideTopPixels left=$guideLeftPixels " +
                "center=$guideCenterPixels right=$guideRightPixels"
        )
        assertEquals(0, legacyTopPixels)
        assertTrue("Expected [ in the top guide position", guideTopPixels > 5)
        assertTrue("Expected ) in the left guide position", guideLeftPixels > 5)
        assertTrue("Expected ( in the tap position", guideCenterPixels > 5)
        assertTrue("Expected ] in the right guide position", guideRightPixels > 5)
        assertTrue("Expected the bracket guide to differ from the legacy icon", changedPixels > 100)
    }

    @Test
    fun sumireEnglishAndNumberGuidesRenderTapLabelsAndMeasurableDifferences() {
        lateinit var flickView: FlickKeyboardView
        lateinit var englishWithoutGuide: Bitmap
        lateinit var englishWithGuide: Bitmap
        instrumentation.runOnMainSync {
            flickView = FlickKeyboardView(themedContext())
            val englishLayout = createSumireLayout(KeyboardInputMode.ENGLISH)

            flickView.setFlickGuideEnabled(false, allowMultiCharacterLabels = true)
            flickView.setKeyboard(englishLayout)
            assertNotNull(flickView.findButtonWithText("ABC"))
            englishWithoutGuide = render(flickView, width = 1080, height = 760)

            flickView.setFlickGuideEnabled(true, allowMultiCharacterLabels = true)
            assertNotNull(flickView.findButtonWithText("a"))
            englishWithGuide = render(flickView, width = 1080, height = 760)

            flickView.setFlickGuideEnabled(true, allowMultiCharacterLabels = true)
            flickView.setKeyboard(createSumireLayout(KeyboardInputMode.SYMBOLS))
            assertNotNull(flickView.findButtonWithText("1"))
        }

        val changedPixels = countChangedPixels(englishWithoutGuide, englishWithGuide)
        Log.i(MEASUREMENT_TAG, "Sumire English guide changedPixels=$changedPixels")
        assertTrue("Expected a visible Sumire guide difference", changedPixels > 1_000)
    }

    private fun key2Text(tenKey: TenKey, mode: InputMode): String {
        var text = ""
        instrumentation.runOnMainSync {
            tenKey.setCurrentMode(mode)
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val key2 = tenKey.findViewById<AppCompatButton>(com.kazumaproject.tenkey.R.id.key_2)
            text = key2.text.toString()
        }
        return text
    }

    private fun createSumireLayout(mode: KeyboardInputMode) =
        KeyboardDefaultLayouts.createFinalLayout(
            mode = mode,
            dynamicKeyStates = emptyMap(),
            inputLayoutType = "flick",
            inputStyle = "default"
        )

    private fun themedContext(): android.content.Context {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return ContextThemeWrapper(context, R.style.Theme_MarkdownKeyboard)
    }

    private fun FlickKeyboardView.findButtonWithText(text: String): AutoSizeButton? {
        for (index in 0 until childCount) {
            val button = getChildAt(index) as? AutoSizeButton ?: continue
            if (button.text.toString() == text) return button
        }
        return null
    }

    private fun render(view: View, width: Int, height: Int): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            view.draw(Canvas(bitmap))
        }
    }

    private fun renderDrawable(drawable: Drawable, width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(bitmap))
        }
    }

    private fun countOpaquePixels(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int {
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 0) count++
            }
        }
        return count
    }

    private fun countChangedPixels(first: Bitmap, second: Bitmap): Int {
        require(first.width == second.width && first.height == second.height)
        val firstPixels = IntArray(first.width * first.height)
        val secondPixels = IntArray(second.width * second.height)
        first.getPixels(firstPixels, 0, first.width, 0, 0, first.width, first.height)
        second.getPixels(secondPixels, 0, second.width, 0, 0, second.width, second.height)
        return firstPixels.indices.count { firstPixels[it] != secondPixels[it] }
    }

    private companion object {
        const val MEASUREMENT_TAG = "GuideMeasurement"
    }
}
