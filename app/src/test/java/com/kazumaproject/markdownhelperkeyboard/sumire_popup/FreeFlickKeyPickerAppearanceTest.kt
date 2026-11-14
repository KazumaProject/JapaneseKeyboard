package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.SumirePopupDefaults
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FreeFlickKeyPickerAppearanceTest {
    private val context=ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),
        com.google.android.material.R.style.Theme_Material3_DayNight)
    @Before fun setup() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
        AppPreference.keyboard_height=248
        AppPreference.flick_key_width_scale_percent=144
        AppPreference.flick_key_height_scale_percent=152
        AppPreference.flick_key_icon_scale_percent=88
        AppPreference.flick_special_key_text_size_sp=15f
        AppPreference.setModeKeySize("sumire",com.kazumaproject.custom_keyboard.data.KeyboardInputMode.HIRAGANA,false,22f)
        AppPreference.setModeKeySize("sumire",com.kazumaproject.custom_keyboard.data.KeyboardInputMode.HIRAGANA,true,12f)
        AppPreference.sumire_keymap_guide_japanese=true
        AppPreference.sumire_mode_key_highlight_preference=false
    }
    private fun verify() {
        val height=(248*context.resources.displayMetrics.density).toInt()
        val reference=FreeFlickKeyboardPreview(context).apply {layoutParams=FrameLayout.LayoutParams(600,height);refresh()}
        val picker=FreeFlickKeyPicker(context)
        picker.showSelection(SumirePopupDefaults.rows.keys.map(SumirePopupDefaults::create),"__no_selected_key__")
        assertEquals("The editor must use the configured keyboard height",height,picker.keyboard.layoutParams.height)
        fun render(view: View): Bitmap {
            view.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
            view.layout(0,0,600,height)
            return Bitmap.createBitmap(600,height,Bitmap.Config.ARGB_8888).also {view.draw(Canvas(it))}
        }
        val expected=render(reference.keyboard)
        val actual=render(picker.keyboard)
        val details=(0 until picker.keyboard.childCount).map { index ->
            val a=picker.keyboard.getChildAt(index);val e=reference.keyboard.getChildAt(index)
            fun describe(v: View)=listOf(v.javaClass.simpleName,v.left,v.top,v.width,v.height,v.alpha,v.isEnabled,v.isPressed,
                (v as? android.widget.TextView)?.text,(v as? android.widget.TextView)?.textSize,(v as? android.widget.TextView)?.currentTextColor).joinToString(",")
            "$index actual=${describe(a)} expected=${describe(e)}"
        }.joinToString("\n")
        assertTrue("Idle editor keys must match the input preview's geometry, guides, text, icons and colors\n$details",actual.sameAs(expected))
        assertTrue((0 until picker.keyboard.childCount).all {picker.keyboard.getChildAt(it).alpha==1f})
    }
    @Test fun editorKeysMatchInputPreview()=verify()
    @Test fun modeHighlightAlsoMatchesInputPreview() {
        AppPreference.sumire_mode_key_highlight_preference=true
        verify()
    }
    @Test fun customThemeAlsoMatchesWithoutDimmingSpecialKeys() {
        AppPreference.theme_mode="custom"
        AppPreference.custom_theme_bg_color=0xff142536.toInt()
        AppPreference.custom_theme_key_color=0xff354657.toInt()
        AppPreference.custom_theme_special_key_color=0xff596a7b.toInt()
        AppPreference.custom_theme_key_text_color=0xfff0e1d2.toInt()
        AppPreference.custom_theme_special_key_text_color=0xffefd0b1.toInt()
        verify()
    }
    @Test fun fullColorFunctionKeysCannotSelectAnEditingKey() {
        val selected=mutableListOf<String>()
        val picker=FreeFlickKeyPicker(context).apply {onKeySelected={selected+=it}}
        picker.showSelection(SumirePopupDefaults.rows.keys.map(SumirePopupDefaults::create),"は")
        for(index in 0 until picker.keyboard.childCount) {
            val view=picker.keyboard.getChildAt(index)
            if((view as? android.widget.TextView)?.text?.toString() !in SumirePopupDefaults.rows.keys) {
                assertEquals(1f,view.alpha,0f);view.performClick()
            }
        }
        assertTrue("Function keys must not choose an unrelated map",selected.isEmpty())
        (0 until picker.keyboard.childCount).map(picker.keyboard::getChildAt).filterIsInstance<android.widget.TextView>()
            .single {it.text.toString()==SumirePopupDefaults.PUNCTUATION_KEY}.performClick()
        assertEquals(listOf(SumirePopupDefaults.PUNCTUATION_KEY),selected)
    }

}
