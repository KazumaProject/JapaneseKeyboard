package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickComposingBackgroundTest {
    @Test
    fun flickOnlyInput_doesNotScheduleToggleTimeout() {
        val service = IMEService()
        ReflectionHelpers.setField(service, "isFlickOnlyMode", true)
        ReflectionHelpers.callInstanceMethod<Unit>(service, "scheduleDefaultInputFinalize",
            ClassParameter.from(String::class.java, "あ"))
        assertNull(ReflectionHelpers.getField<Any?>(service, "defaultInputFinalizeJob"))
    }

    @Test
    fun previewAndNormalInput_useAfterEditBackgroundOnlyInFlickOnlyMode() {
        val context = RuntimeEnvironment.getApplication()
        val service = IMEService()
        ReflectionHelpers.callInstanceMethod<Unit>(service, "attachBaseContext",
            ClassParameter.from(Context::class.java, context))
        val before = 0x44112233
        val after = 0x77556677
        for (custom in listOf(false, true)) {
            for (flickOnly in listOf(false, true)) {
                ReflectionHelpers.setField(service, "isFlickOnlyMode", flickOnly)
                ReflectionHelpers.setField(service, "customComposingTextPreference", custom)
                ReflectionHelpers.setField(service, "inputCompositionBackgroundColor", before)
                ReflectionHelpers.setField(service, "inputCompositionAfterBackgroundColor", after)
                val expected = if (flickOnly) {
                    if (custom) after else context.getColor(com.kazumaproject.core.R.color.blue)
                } else {
                    if (custom) before else context.getColor(com.kazumaproject.core.R.color.char_in_edit_color)
                }
                val preEdit = if (custom) before else context.getColor(com.kazumaproject.core.R.color.char_in_edit_color)
                val normal = ReflectionHelpers.callInstanceMethod<SpannableString>(service,
                    "applyPreEditComposingSpans",
                    ClassParameter.from(SpannableString::class.java, SpannableString("あいう")),
                    ClassParameter.from(Int::class.javaPrimitiveType, 2),
                    ClassParameter.from(Int::class.javaPrimitiveType, 3),
                    ClassParameter.from(Int::class.javaPrimitiveType, preEdit),
                    ClassParameter.from(Int::class.javaObjectType, null))
                val preview = ReflectionHelpers.callInstanceMethod<CharSequence>(service,
                    "createFlickPreviewComposingText",
                    ClassParameter.from(String::class.java, "あい"),
                    ClassParameter.from(String::class.java, "う")) as SpannableString
                for (text in listOf(normal, preview)) {
                    val span = text.getSpans(0, text.length, BackgroundColorSpan::class.java).single()
                    assertEquals("custom=$custom flickOnly=$flickOnly", expected, span.backgroundColor)
                    assertEquals(0, text.getSpanStart(span))
                    assertEquals(2, text.getSpanEnd(span))
                }
            }
        }
    }
}
