package com.kazumaproject.markdownhelperkeyboard.skin

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.gojuon_keyboard.GojuonKeyboardView
import com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
import com.kazumaproject.tenkey.TenKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyboardSkinViewLifecycleTest {
    private fun views(): List<ViewGroup> {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),
            com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard)
        return listOf(TenKey(context, Robolectric.buildAttributeSet().build()),
            GojuonKeyboardView(context), QWERTYKeyboardView(context))
    }
    private fun apply(view: View, id: KeyboardSkinId) {
        val palette = KeyboardSkinRegistry.find(id)?.palette
        view.javaClass.methods.single { it.name == "applyKeyboardTheme" }.invoke(view,
            "custom", Configuration.UI_MODE_NIGHT_NO, false,
            palette?.background ?: 0xffabcdef.toInt(), palette?.key ?: 0xffededed.toInt(),
            palette?.key ?: 0xffcccccc.toInt(), palette?.text ?: 0xff123456.toInt(),
            palette?.text ?: 0xff234567.toInt(), false, false, 0xff000000.toInt(), 255, 1, id)
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1080, 640)
    }
    private fun geometry(view: View): List<Int> = buildList {
        addAll(listOf(view.id, view.left, view.top, view.right, view.bottom))
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(geometry(view.getChildAt(i)))
    }
    private fun pixels(view: View): Bitmap = Bitmap.createBitmap(1080,640,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }

    @Test fun skinCanBeAppliedOnFirstCreationBeforeAnyLegacyPopupExists() {
        views().forEach { apply(it, KeyboardSkinId.CUPERTINO_DARK) }
    }
    @Test fun roundTripPreservesKeyBoundsAndRestoresLegacyRendering() {
        views().forEach { view ->
            apply(view, KeyboardSkinId.DEFAULT)
            val before = pixels(view)
            val bounds = geometry(view)
            apply(view, KeyboardSkinId.CUPERTINO_LIGHT)
            assertEquals(view.javaClass.simpleName, bounds, geometry(view))
            apply(view, KeyboardSkinId.CUPERTINO_DARK)
            assertEquals(view.javaClass.simpleName, bounds, geometry(view))
            apply(view, KeyboardSkinId.DEFAULT)
            assertTrue("Legacy pixels differ: ${view.javaClass.simpleName}", before.sameAs(pixels(view)))
        }
    }
}
