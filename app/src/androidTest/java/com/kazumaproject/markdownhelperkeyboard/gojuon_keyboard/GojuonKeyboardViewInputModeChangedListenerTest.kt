package com.kazumaproject.markdownhelperkeyboard.gojuon_keyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.gojuon_keyboard.GojuonKeyboardView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GojuonKeyboardViewInputModeChangedListenerTest {

    @Test
    fun inputModeSwitchNotifiesUpdatedInputModeInCycleOrder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val notifiedModes = mutableListOf<InputMode>()
        val currentModesAtNotification = mutableListOf<InputMode>()

        instrumentation.runOnMainSync {
            val view = GojuonKeyboardView(context)
            view.setOnInputModeChangedListener { inputMode ->
                notifiedModes.add(inputMode)
                currentModesAtNotification.add(view.currentInputMode.get())
            }

            repeat(3) {
                clickInputModeSwitch(view)
            }
        }

        val expectedModes = listOf(
            InputMode.ModeEnglish,
            InputMode.ModeNumber,
            InputMode.ModeJapanese
        )
        assertEquals(expectedModes, notifiedModes)
        assertEquals(expectedModes, currentModesAtNotification)
    }

    private fun clickInputModeSwitch(view: GojuonKeyboardView) {
        GojuonKeyboardView::class.java
            .getDeclaredMethod("handleClickInputModeSwitch")
            .apply { isAccessible = true }
            .invoke(view)
    }
}
