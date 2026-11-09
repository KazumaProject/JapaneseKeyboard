package com.kazumaproject.markdownhelperkeyboard.gemma

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class GemmaTextOutputTest {
    private fun manager(runtime: GemmaRuntimeClient): GemmaTranslationManager {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppPreference.init(context)
        return GemmaTranslationManager(context, AppPreference, runtime).also {
            ReflectionHelpers.getField<MutableStateFlow<GemmaLoadState>>(it, "_loadState").value =
                GemmaLoadState.Ready("CPU", "/models/text.gguf")
        }
    }

    @Test fun customActionPreservesTheCompleteGeneratedText() = runBlocking {
        val runtime = mock<GemmaRuntimeClient>()
        val result = "  「こんにちは NEXT」\n\t\"日本語😀\"\n```code```\n\n"
        whenever(runtime.generateText(any(), any())).thenReturn(result)
        assertEquals(result, manager(runtime).runCustomPrompt("source", "QA", "Copy the input."))
    }

    @Test fun translationPreservesTheCompleteGeneratedText() = runBlocking {
        val runtime = mock<GemmaRuntimeClient>()
        val result = "  \"Hello 日本語\"\n\tsecond line\n\n"
        whenever(runtime.generateTranslation(any(), any(), any())).thenReturn(result)
        assertEquals(result, manager(runtime).translate("source"))
    }
}
