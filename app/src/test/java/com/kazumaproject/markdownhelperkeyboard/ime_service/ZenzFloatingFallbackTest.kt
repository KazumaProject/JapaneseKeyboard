package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ZenzFloatingFallbackTest {
    private val serviceInstance = Robolectric.buildService(IMEService::class.java).get()
    private val service = spy(serviceInstance)
    private val preferences = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("zenz-floating-fallback", Context.MODE_PRIVATE)
    private val local = Candidate("垢", 1, 2u, 1000, yomi = "あか")
    private val generated = Candidate("赤", 33, 2u, 0, yomi = "あか")

    @Before fun setup() {
        org.junit.Assume.assumeTrue(com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig.hasZenz)
        AppPreference.init(ApplicationProvider.getApplicationContext())
        service.appPreference = mock()
        service.zenzRuntimeClient = mock()
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

    @Test fun configuredWaitTimeUpdatesWithoutRestartingTheIme() {
        val listener = ReflectionHelpers.getField<android.content.SharedPreferences.OnSharedPreferenceChangeListener>(
            serviceInstance, "runtimeInputPreferenceListener")
        val key = "zenz_debounce_time_preference"
        for (delay in listOf(0, 300, 500)) {
            preferences.edit().putInt(key, delay).commit()
            listener.onSharedPreferenceChanged(preferences, key)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(delay, ReflectionHelpers.getField<Int>(serviceInstance, "zenzDebounceTimePreference"))
        }
        preferences.edit().remove(key).commit()
        listener.onSharedPreferenceChanged(preferences, key)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(300, ReflectionHelpers.getField<Int>(serviceInstance, "zenzDebounceTimePreference"))
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

    @Test fun panelComparesCommitTextAndRechecksUpdatedLocalCandidates() {
        val state = state(generated)
        setInput("あか")
        ReflectionHelpers.setField(service, "zenzLiveSnapshotDisplayInput", "あか")
        ReflectionHelpers.setField(service, "zenzLiveLocalCandidatesSnapshot", listOf(local))
        assertTrue(visible(state))
        ReflectionHelpers.setField(service, "zenzLiveLocalCandidatesSnapshot",
            listOf(local, local.copy(string = "赤", commitText = "赤")))
        assertTrue(visible(state))
        ReflectionHelpers.setField(service, "zenzLiveLocalCandidatesSnapshot",
            listOf(local.copy(string = "赤", commitText = "赤")))
        assertFalse(visible(state))
        // Compare committed text rather than a candidate's display label.
        ReflectionHelpers.setField(service, "zenzLiveLocalCandidatesSnapshot",
            listOf(local.copy(string = "別の表示", commitText = "赤")))
        assertFalse(visible(state))
        ReflectionHelpers.setField(service, "zenzLiveLocalCandidatesSnapshot", emptyList<Candidate>())
        assertFalse(visible(state))
    }

    @Test fun loadingStaleInputAndStaleRequestsNeverShowThePanel() {
        setInput("あか")
        ReflectionHelpers.setField(service, "zenzLiveSnapshotDisplayInput", "あか")
        ReflectionHelpers.setField(service, "zenzLiveLocalCandidatesSnapshot", listOf(local))
        assertFalse(visible(state(null)))
        val state = state(generated)
        assertTrue(visible(state))
        setInput("あお")
        assertFalse(visible(state))
        setInput("あか")
        ReflectionHelpers.setField(service, "zenzLiveRequestToken", 1L)
        assertFalse(visible(state))
    }

    @Test fun floatingResultDoesNotReplaceLiveCompositionEvenForManualRequests() {
        setInput("あか")
        ReflectionHelpers.setField(service, "isLiveConversionEnable", true)
        ReflectionHelpers.setField(service, "liveConversionStartLength", 1)
        ReflectionHelpers.setField(service, "zenzEnableLongPressConversionPreference", true)
        for (source in listOf("AutoLive", "ManualConvertLongPress")) {
            val state = state(generated, source)
            assertFalse(shouldApply(state))
            preferences.edit().putBoolean("zenz_floating_candidates_enabled", false).commit()
            assertTrue(shouldApply(state))
            preferences.edit().putBoolean("zenz_floating_candidates_enabled", true).commit()
        }
    }

    @Test fun tappingGeneratedCandidateReplacesAndCommitsTheLocalComposition() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val editor = editor(context)
        val content = requireNotNull(editor.editable)
        editor.setComposingText(local.string, 1)
        setInput("あか")
        ReflectionHelpers.setField(service, "lastCandidate", local.string)
        ReflectionHelpers.setField(service, "isLiveConversionEnable", true)
        IMEService::class.java.getDeclaredMethod("setCandidateClick", Candidate::class.java,
            String::class.java, InputMode::class.java,
            Int::class.javaPrimitiveType, List::class.java)
            .apply { isAccessible = true }.invoke(service, generated, "あか",
                InputMode.ModeJapanese, 0, listOf(generated))
        assertEquals("赤", content.toString())
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(content))
        assertEquals("", ReflectionHelpers.getField<MutableStateFlow<String>>(
            service, "_inputString").value)
    }

    @Test fun floatingBunsetsuTapCommitsTheSelectedSegment() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val editor = editor(context)
        val content = requireNotNull(editor.editable)
        val themed = ContextThemeWrapper(context,
            R.style.Theme_MarkdownKeyboard)
        val binding = MainLayoutBinding
            .inflate(LayoutInflater.from(themed))
        ReflectionHelpers.setField(service, "mainLayoutBinding", binding)
        ReflectionHelpers.setField(service, "bunsetsuSeparation", true)
        ReflectionHelpers.setField(service, "bunsetsuCursorMove", true)
        ReflectionHelpers.getField<AtomicBoolean>(service, "isHenkan").set(true)
        val sessionClass = IMEService::class.java.declaredClasses.first { it.simpleName == "BunsetsuConversionSession" }
        val segment = BunsetsuSegmentState("あか", "垢", candidates = listOf(local))
        val session = sessionClass.declaredConstructors.first { it.parameterCount == 9 }
            .apply { isAccessible = true }.newInstance("あか", "あか", listOf(segment), 0L, null, "", 0, emptyList<List<Int>>(), 0)
        ReflectionHelpers.setField(service, "bunsetsuConversionSession", session)
        val targetClass = IMEService::class.java.declaredClasses.first { it.simpleName == "BunsetsuZenzTarget" }
        val target = targetClass.declaredConstructors.single().apply { isAccessible = true }
            .newInstance("あか", 0, "あか", "")
        val state = state(generated, "BunsetsuSegment", target)
        ReflectionHelpers.getField<MutableStateFlow<Any?>>(service, "_zenzLiveSlotState").value = state
        setInput("あか")
        editor.setComposingText("垢", 1)
        assertTrue(visible(state))
        IMEService::class.java.getDeclaredMethod("handleBunsetsuCandidateClick", Candidate::class.java,
            Int::class.javaPrimitiveType, List::class.java).apply { isAccessible = true }
            .invoke(service, generated, 0, listOf(generated))
        assertEquals("赤", content.toString())
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(content))
        assertEquals(null, ReflectionHelpers.getField<Any?>(service, "bunsetsuConversionSession"))
    }

    private fun editor(context: Context): BaseInputConnection {
        val content = SpannableStringBuilder()
        Selection.setSelection(content, 0)
        val editor = object : BaseInputConnection(View(context), true) {
            override fun getEditable() = content
        }
        doReturn(editor).whenever(service).getCurrentInputConnection()
        return editor
    }

    private fun setInput(text: String) {
        ReflectionHelpers.getField<MutableStateFlow<String>>(service, "_inputString").value = text
    }

    private fun visible(state: Any): Boolean = IMEService::class.java
        .getDeclaredMethod("shouldShowZenzFloatingCandidate", state.javaClass)
        .apply { isAccessible = true }.invoke(service, state) as Boolean

    private fun shouldApply(state: Any): Boolean = IMEService::class.java
        .getDeclaredMethod("shouldApplyZenzLiveResultToComposingText", state.javaClass, Candidate::class.java)
        .apply { isAccessible = true }.invoke(service, state, generated) as Boolean

    private fun state(candidate: Candidate?, sourceName: String = "AutoLive", target: Any? = null): Any {
        val sourceClass = IMEService::class.java.declaredClasses.first { it.simpleName == "ZenzRequestSource" }
        val source = sourceClass.enumConstants.first { (it as Enum<*>).name == sourceName }
        val stateClass = IMEService::class.java.declaredClasses.first { it.simpleName == "ZenzLiveSlotState" }
        return stateClass.declaredConstructors.first { it.parameterCount == 7 }
            .apply { isAccessible = true }.newInstance("あか", "あか", candidate, candidate == null, 0L, source, target)
    }

    private fun displayed(candidate: Candidate?): List<Candidate> {
        val state = state(candidate)
        val method = IMEService::class.java.getDeclaredMethod("buildDisplayedCandidatesWithZenzSlot",
            List::class.java, String::class.java, state.javaClass, Boolean::class.javaPrimitiveType)
        @Suppress("UNCHECKED_CAST")
        return method.apply { isAccessible = true }.invoke(service, listOf(local), "あか", state, false) as List<Candidate>
    }
}
