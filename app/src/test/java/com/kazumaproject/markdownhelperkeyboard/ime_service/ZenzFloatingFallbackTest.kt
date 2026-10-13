package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ZenzFloatingFallbackTest {
    private val service = spy(IMEService())
    private val preferences = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("zenz-floating-fallback", Context.MODE_PRIVATE)
    private val local = Candidate("垢", 1, 2u, 1000, yomi = "あか")
    private val generated = Candidate("赤", 33, 2u, 0, yomi = "あか")

    @Before fun setup() {
        org.junit.Assume.assumeTrue(com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig.hasZenz)
        preferences.edit().clear().putBoolean("enable_ai_conversion_zenz_preference", true)
            .putBoolean("zenz_floating_candidates_enabled", true).commit()
        ReflectionHelpers.setField(service, "runtimeInputSharedPreferences", preferences)
        ReflectionHelpers.setField(service, "isInputViewActive", true)
        ReflectionHelpers.setField(service, "zenzEnableStatePreference", true)
        doReturn(false).whenever(service).isFullscreenMode
    }

    @After fun cleanup() {
        listOf("scope", "ioScope").forEach {
            ReflectionHelpers.getField<CoroutineScope>(service, it).cancel()
        }
        ReflectionHelpers.getField<ExecutorCoroutineDispatcher>(service, "kanaKanjiConversionDispatcher").close()
        preferences.edit().clear().commit()
    }

    @Test fun fullscreenKeepsGeneratedCandidateInTheRegularList() {
        doReturn(true).whenever(service).isFullscreenMode
        assertEquals(listOf(generated, local), displayed(generated))
    }

    @Test fun fullscreenKeepsTheLoadingSlotInTheRegularList() {
        doReturn(true).whenever(service).isFullscreenMode
        val result = displayed(null)
        assertEquals(listOf("...", local.string), result.map { it.string })
    }

    @Test fun normalModeStillUsesTheDedicatedPanel() {
        assertEquals(listOf(local), displayed(generated))
    }

    private fun displayed(candidate: Candidate?): List<Candidate> {
        val sourceClass = IMEService::class.java.declaredClasses.first { it.simpleName == "ZenzRequestSource" }
        val source = sourceClass.enumConstants.first { (it as Enum<*>).name == "AutoLive" }
        val stateClass = IMEService::class.java.declaredClasses.first { it.simpleName == "ZenzLiveSlotState" }
        val state = stateClass.declaredConstructors.first { it.parameterCount == 7 }
            .apply { isAccessible = true }.newInstance("あか", "あか", candidate, candidate == null, 0L, source, null)
        val method = IMEService::class.java.getDeclaredMethod("buildDisplayedCandidatesWithZenzSlot",
            List::class.java, String::class.java, stateClass, Boolean::class.javaPrimitiveType)
        @Suppress("UNCHECKED_CAST")
        return method.apply { isAccessible = true }.invoke(service, listOf(local), "あか", state, false) as List<Candidate>
    }
}
