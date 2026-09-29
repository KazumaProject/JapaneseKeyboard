package com.kazumaproject.markdownhelperkeyboard.local_font

import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableString
import android.text.TextPaint
import android.text.Spanned
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.font.KeyboardFontSpan
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardFontApplicatorTest {
    @Test fun appliesFontFamilyAndRestoresEachTextViewsOriginalTypeface() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = TextView(context)
        val original = view.typeface
        val custom = Typeface.create("serif", Typeface.BOLD)

        KeyboardFontApplicator.apply(view, KeyboardFontSnapshot(custom, 1))
        assertEquals(Typeface.create(custom, original?.style ?: Typeface.NORMAL), view.typeface)

        val nativeStyle = Typeface.SANS_SERIF
        view.typeface = nativeStyle
        val second = Typeface.create("monospace", Typeface.ITALIC)
        KeyboardFontApplicator.apply(view, KeyboardFontSnapshot(second, 2))
        assertEquals(Typeface.create(second, nativeStyle.style), view.typeface)

        KeyboardFontApplicator.apply(view, KeyboardFontSnapshot(null, 3))
        assertSame(nativeStyle, view.typeface)
    }

    @Test fun appliesFontFamilyAndRestoresPaintTypeface() {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
        val original = paint.typeface
        KeyboardFontApplicator.apply(paint, KeyboardFontSnapshot(Typeface.create("serif", Typeface.NORMAL), 10))
        assertEquals(Typeface.create(Typeface.create("serif", Typeface.NORMAL), Typeface.BOLD), paint.typeface)
        KeyboardFontApplicator.apply(paint, KeyboardFontSnapshot(null, 11))
        assertSame(original, paint.typeface)
    }

    @Test fun sameRevisionStandardModeRestoresAndAllowsReapplyingTheLocalFont() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val local = Typeface.create("serif", Typeface.NORMAL)
        val snapshot = KeyboardFontSnapshot(local, 42)
        val standardForCurrentMode = snapshot.copy(typeface = null)
        val view = TextView(context)
        val originalViewTypeface = view.typeface

        KeyboardFontApplicator.apply(view, snapshot)
        KeyboardFontApplicator.apply(view, standardForCurrentMode)
        assertSame(originalViewTypeface, view.typeface)
        KeyboardFontApplicator.apply(view, snapshot)
        assertEquals(Typeface.create(local, originalViewTypeface?.style ?: Typeface.NORMAL), view.typeface)

        val originalPaintTypeface = Typeface.DEFAULT_BOLD
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = originalPaintTypeface }
        KeyboardFontApplicator.apply(paint, snapshot)
        KeyboardFontApplicator.apply(paint, standardForCurrentMode)
        assertSame(originalPaintTypeface, paint.typeface)
    }

    @Test fun keyboardFontSpanPreservesMenuTextStyle() {
        val local = Typeface.create("serif", Typeface.NORMAL)
        val text = SpannableString("Paste").apply {
            setSpan(KeyboardFontSpan(local), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val paint = TextPaint().apply { typeface = Typeface.DEFAULT_BOLD }

        text.getSpans(0, text.length, KeyboardFontSpan::class.java).single().updateMeasureState(paint)

        assertEquals(Typeface.create(local, Typeface.BOLD), paint.typeface)
    }
}
