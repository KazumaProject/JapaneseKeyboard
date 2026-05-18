package com.kazumaproject.markdownhelperkeyboard.tabletkey

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.tabletkey.TabletKeyboardView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabletKeyboardViewInputModeChangedListenerTest {

    @Test
    fun inputModeSwitchNotifiesUpdatedInputModeInCycleOrder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val notifiedModes = mutableListOf<InputMode>()
        val currentModesAtNotification = mutableListOf<InputMode>()

        instrumentation.runOnMainSync {
            val view = TabletKeyboardView(context)
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

    private fun clickInputModeSwitch(view: TabletKeyboardView) {
        TabletKeyboardView::class.java
            .getDeclaredMethod("handleClickInputModeSwitch")
            .apply { isAccessible = true }
            .invoke(view)
    }
}
