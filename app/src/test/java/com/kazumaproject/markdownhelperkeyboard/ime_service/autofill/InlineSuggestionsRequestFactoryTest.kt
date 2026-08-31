package com.kazumaproject.markdownhelperkeyboard.ime_service.autofill

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class InlineSuggestionsRequestFactoryTest {

    @Test
    @Config(sdk = [35])
    fun requestUsesFourFixedHeightAndroidxStyleSpecs() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val request = InlineSuggestionsRequestFactory.create(context)
        val specs = request.inlinePresentationSpecs

        assertEquals(InlineSuggestionsRequestFactory.MAX_SUGGESTION_COUNT, request.maxSuggestionCount)
        assertEquals(InlineSuggestionsRequestFactory.MAX_SUGGESTION_COUNT, specs.size)
        specs.forEach { spec ->
            assertTrue(spec.minSize.width > 0)
            assertTrue(spec.maxSize.width >= spec.minSize.width)
            assertEquals(spec.minSize.height, spec.maxSize.height)
            assertTrue(spec.minSize.height > 0)
            assertTrue(spec.style.size() > 0)
        }
    }

    @Test
    @Config(sdk = [29])
    fun inlineHostClassCanBeCreatedBeforeAndroidEleven() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertNotNull(InlineSuggestionClipView(context))
    }
}
