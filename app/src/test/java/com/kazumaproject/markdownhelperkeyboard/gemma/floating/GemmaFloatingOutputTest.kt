package com.kazumaproject.markdownhelperkeyboard.gemma.floating

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaLoadState
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaRuntimeClient
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GemmaFloatingOutputTest {
    @Test fun replacementKeepsIndentationTabsAndFinalNewlines() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppPreference.init(context)
        val runtime = mock<GemmaRuntimeClient>()
        val replacement = "    Markdown code\n\tsecond line 😀\n\n"
        whenever(runtime.generatePreview(any(), any())).thenReturn(replacement)
        val manager = GemmaTranslationManager(context, AppPreference, runtime)
        ReflectionHelpers.getField<MutableStateFlow<GemmaLoadState>>(manager, "_loadState").value =
            GemmaLoadState.Ready("CPU", "/models/gemma-4-E2B-it.litertlm")

        assertEquals(replacement, manager.generateFloatingText("input", "Preserve all formatting."))
    }
}
