package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.tenkey.TenKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingTenkeySizingTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard)
    private val service = IMEService()

    @Before fun setup() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
        service.appPreference = AppPreference
        ReflectionHelpers.callInstanceMethod<Unit>(service, "attachBaseContext", ClassParameter.from(Context::class.java, context))
    }

    @After fun cleanup() {
        listOf("scope", "ioScope").forEach { ReflectionHelpers.getField<CoroutineScope>(service, it).cancel() }
        ReflectionHelpers.getField<ExecutorCoroutineDispatcher>(service, "kanaKanjiConversionDispatcher").close()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test fun floatingAndSplitSurfacesUseSavedSizesAcrossModeAndGuideChanges() {
        val modes = listOf(KeyboardInputMode.HIRAGANA, KeyboardInputMode.ENGLISH, KeyboardInputMode.SYMBOLS)
        val inputs = listOf(InputMode.ModeJapanese, InputMode.ModeEnglish, InputMode.ModeNumber)
        modes.forEachIndexed { index, mode ->
            AppPreference.setModeKeySize("tenkey", mode, false, 20f + index * 4)
            AppPreference.setModeKeySize("tenkey", mode, true, 8f + index * 2)
        }
        // Floating keyboards and both split panes use this service configuration path.
        val keyboard = keyboard()
        try {
            val binding = mock<FloatingKeyboardLayoutBinding>()
            ReflectionHelpers.setField(binding, "keyboardViewFloating", keyboard)
            configure(binding)
            inputs.forEachIndexed { index, input ->
                keyboard.setCurrentMode(input)
                shadowOf(android.os.Looper.getMainLooper()).idle()
                keyboard.setFlickGuideEnabled(true, true, true)
                val key = keyboard.findViewById<TextView>(com.kazumaproject.tenkey.R.id.key_1)
                assertEquals((20f + index * 4) * context.resources.displayMetrics.scaledDensity, key.textSize, .1f)
                val text = key.text as Spanned
                val spans = text.getSpans(0, text.length, AbsoluteSizeSpan::class.java)
                assertTrue(spans.isNotEmpty())
                spans.forEach { span ->
                    assertEquals(((8f + index * 2) * context.resources.displayMetrics.scaledDensity).toInt(), span.size)
                }
                keyboard.setFlickGuideEnabled(false, false, false)
                assertEquals((20f + index * 4) * context.resources.displayMetrics.scaledDensity, key.textSize, .1f)
            }
            // Resetting settings and configuring an existing surface must remove old overrides.
            AppPreference.resetModeKeySizes("tenkey", KeyboardInputMode.HIRAGANA)
            keyboard.setCurrentMode(InputMode.ModeJapanese)
            configure(binding)
            keyboard.setFlickGuideEnabled(false, false, false)
            assertEquals(17f * context.resources.displayMetrics.scaledDensity,
                keyboard.findViewById<TextView>(com.kazumaproject.tenkey.R.id.key_1).textSize, .1f)
        } finally {
            keyboard.cancelTenKeyScope()
        }
    }

    private fun keyboard(): TenKey = LayoutInflater.from(context)
        .inflate(R.layout.fragment_tenkey_candidate_letter_size, null).findViewById(R.id.tenkeyLetterSizePreview)

    private fun configure(binding: FloatingKeyboardLayoutBinding) = ReflectionHelpers.callInstanceMethod<Unit>(service,
        "configureFloatingTenKeyView", ClassParameter.from(FloatingKeyboardLayoutBinding::class.java, binding))
}
