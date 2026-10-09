package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IMEServiceCounterDictionaryStartupTest {
    @Test fun preferencesCanBeAppliedBeforeAsynchronousEngineLoadCompletes() {
        val service = Robolectric.buildService(IMEService::class.java).get()
        applyPreference(service, false)
        applyPreference(service, true)
    }

    @Test fun loadedEngineReceivesDictionaryPreferenceChanges() {
        val service = Robolectric.buildService(IMEService::class.java).get()
        val engine = mock<KanaKanjiEngine>()
        IMEService::class.java.getDeclaredField("kanaKanjiEngine").apply {
            isAccessible = true
            set(service, engine)
        }
        applyPreference(service, false)
        verify(engine).setCounterDictionaryEnabled(false)
        applyPreference(service, true)
        verify(engine).setCounterDictionaryEnabled(true)
    }

    private fun applyPreference(service: IMEService, enabled: Boolean) {
        IMEService::class.java.getDeclaredMethod(
            "applyCounterDictionaryPreference", Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }.invoke(service, enabled)
    }
}
