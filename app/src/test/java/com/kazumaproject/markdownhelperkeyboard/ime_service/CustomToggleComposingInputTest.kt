package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomToggleComposingInputTest {
    private val service = IMEService()
    private val binding = mock(MainLayoutBinding::class.java)

    private fun tap(key: String, values: List<String>) {
        ReflectionHelpers.callInstanceMethod<Unit>(service, "handleCustomToggleText",
            ClassParameter.from(String::class.java, key),
            ClassParameter.from(List::class.java, values),
            ClassParameter.from(List::class.java, values),
            ClassParameter.from(MainLayoutBinding::class.java, binding))
    }

    private fun text(): String =
        ReflectionHelpers.getField<StateFlow<String>>(service, "_inputString").value

    @Test fun differentKeysWithSameFirstKana_appendInsteadOfLegacyCycling() {
        tap("first", listOf("あ", "お"))
        tap("second", listOf("あ", "え"))
        assertEquals("ああ", text())
        tap("second", listOf("あ", "え"))
        assertEquals("あえ", text())
    }

    @Test fun sameKey_cyclesInCustomOrderAndWraps() {
        tap("key", listOf("あ", "お", "う"))
        tap("key", listOf("あ", "お", "う"))
        assertEquals("お", text())
        tap("key", listOf("あ", "お", "う"))
        assertEquals("う", text())
        tap("key", listOf("あ", "お", "う"))
        assertEquals("あ", text())
    }

    @Test fun singleKana_alwaysAppends() {
        repeat(3) { tap("key", listOf("あ")) }
        assertEquals("あああ", text())
    }

    @Test fun resetThenSameKey_appends() {
        tap("key", listOf("あ", "お"))
        ReflectionHelpers.callInstanceMethod<Unit>(service, "resetCustomToggleState")
        tap("key", listOf("あ", "お"))
        assertEquals("ああ", text())
    }

    @Test fun configuredTimeout_preservesPreviousComposingCharacter() {
        ReflectionHelpers.setField(service, "delayTime", 200)
        tap("key", listOf("あ", "お"))
        ShadowSystemClock.advanceBy(Duration.ofMillis(199))
        tap("key", listOf("あ", "お"))
        assertEquals("お", text())
        ShadowSystemClock.advanceBy(Duration.ofMillis(200))
        tap("key", listOf("あ", "お"))
        assertEquals("おあ", text())
        ShadowSystemClock.advanceBy(Duration.ofMillis(201))
        tap("key", listOf("あ", "お"))
        assertEquals("おああ", text())
    }

    @Test fun missingPreference_usesOneSecondTimeout() {
        ReflectionHelpers.setField(service, "delayTime", null)
        tap("key", listOf("あ", "お"))
        ShadowSystemClock.advanceBy(Duration.ofMillis(999))
        tap("key", listOf("あ", "お"))
        assertEquals("お", text())
        ShadowSystemClock.advanceBy(Duration.ofMillis(1000))
        tap("key", listOf("あ", "お"))
        assertEquals("おあ", text())
    }

    @Test fun updatedPreference_changesDeadlineForNextTap() {
        ReflectionHelpers.setField(service, "delayTime", 1000)
        tap("key", listOf("あ", "お"))
        ReflectionHelpers.setField(service, "delayTime", 200)
        ShadowSystemClock.advanceBy(Duration.ofMillis(200))
        tap("key", listOf("あ", "お"))
        assertEquals("ああ", text())
        ReflectionHelpers.setField(service, "delayTime", 1000)
        ShadowSystemClock.advanceBy(Duration.ofMillis(700))
        tap("key", listOf("あ", "お"))
        assertEquals("あお", text())
    }
}
