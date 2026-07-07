package com.kazumaproject.markdownhelperkeyboard.tenkey

import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TwoStateNumberReturnTarget
import com.kazumaproject.core.domain.state.toInputMode
import com.kazumaproject.core.domain.state.toTwoStateNumberReturnTargetOrNull
import com.kazumaproject.core.ui.input_mode_witch.resolveInputModeSwitchIconResId
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.tenkey.TenKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.kazumaproject.core.R as CoreR

class TenKeyInputModeChangedListenerTest {
    @Test
    fun inputModeSwitchNotifiesNewMode() {
        val tenKey = createTenKey()
        val notifiedModes = mutableListOf<InputMode>()
        tenKey.setOnInputModeChangedListener { inputMode ->
            notifiedModes.add(inputMode)
        }

        clickInputModeSwitch(tenKey)

        assertEquals(listOf(InputMode.ModeEnglish), notifiedModes)
    }

    @Test
    fun twoStateNumberModeReturnsToJapaneseWhenOpenedFromJapanese() {
        val tenKey = createTenKey()
        val notifiedModes = mutableListOf<InputMode>()
        tenKey.setOnInputModeChangedListener { inputMode ->
            notifiedModes.add(inputMode)
        }
        tenKey.setUseThreeStateKeyboard(false)
        tenKey.setUseQwertyNumberWhenThreeStateOff(false)

        switchToNumberMode(tenKey)
        clickInputModeSwitch(tenKey)

        assertEquals(
            listOf(InputMode.ModeNumber, InputMode.ModeJapanese),
            notifiedModes
        )
        assertEquals(InputMode.ModeJapanese, tenKey.currentInputMode.value)
    }

    @Test
    fun twoStateNumberModeReturnsToEnglishWhenOpenedFromEnglish() {
        val tenKey = createTenKey()
        val notifiedModes = mutableListOf<InputMode>()
        tenKey.setOnInputModeChangedListener { inputMode ->
            notifiedModes.add(inputMode)
        }
        tenKey.setUseThreeStateKeyboard(false)
        tenKey.setUseQwertyNumberWhenThreeStateOff(false)

        clickInputModeSwitch(tenKey)
        switchToNumberMode(tenKey)
        clickInputModeSwitch(tenKey)

        assertEquals(
            listOf(InputMode.ModeEnglish, InputMode.ModeNumber, InputMode.ModeEnglish),
            notifiedModes
        )
        assertEquals(InputMode.ModeEnglish, tenKey.currentInputMode.value)
    }

    @Test
    fun twoStateNumberReturnTargetConversionsDoNotStoreNumber() {
        assertEquals(
            TwoStateNumberReturnTarget.Japanese,
            InputMode.ModeJapanese.toTwoStateNumberReturnTargetOrNull()
        )
        assertEquals(
            TwoStateNumberReturnTarget.English,
            InputMode.ModeEnglish.toTwoStateNumberReturnTargetOrNull()
        )
        assertNull(InputMode.ModeNumber.toTwoStateNumberReturnTargetOrNull())
        assertEquals(InputMode.ModeJapanese, TwoStateNumberReturnTarget.Japanese.toInputMode())
        assertEquals(InputMode.ModeEnglish, TwoStateNumberReturnTarget.English.toInputMode())
    }

    @Test
    fun twoStateNumberModeIconUsesReturnTarget() {
        assertEquals(
            CoreR.drawable.input_mode_japanese_select_custom,
            resolveInputModeSwitchIconResId(
                inputMode = InputMode.ModeNumber,
                isTablet = false,
                useThreeStateKeyboard = false,
                twoStateNumberReturnTarget = TwoStateNumberReturnTarget.Japanese
            )
        )
        assertEquals(
            CoreR.drawable.input_mode_english_custom,
            resolveInputModeSwitchIconResId(
                inputMode = InputMode.ModeNumber,
                isTablet = false,
                useThreeStateKeyboard = false,
                twoStateNumberReturnTarget = TwoStateNumberReturnTarget.English
            )
        )
    }

    private fun createTenKey(): TenKey {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = LayoutInflater.from(context).inflate(R.layout.main_layout, null)
        return root.findViewById(R.id.keyboard_view)
    }

    private fun clickInputModeSwitch(tenKey: TenKey) {
        val switchMethod = TenKey::class.java.getDeclaredMethod("handleClickInputModeSwitch")
        switchMethod.isAccessible = true
        switchMethod.invoke(tenKey)
    }

    private fun switchToNumberMode(tenKey: TenKey) {
        val switchMethod = TenKey::class.java.getDeclaredMethod("switchToNumberMode")
        switchMethod.isAccessible = true
        switchMethod.invoke(tenKey)
    }
}
