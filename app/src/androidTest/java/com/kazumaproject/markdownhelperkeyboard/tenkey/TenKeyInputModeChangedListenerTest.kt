package com.kazumaproject.markdownhelperkeyboard.tenkey

import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.tenkey.TenKey
import org.junit.Assert.assertEquals
import org.junit.Test

class TenKeyInputModeChangedListenerTest {
    @Test
    fun inputModeSwitchNotifiesNewMode() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = LayoutInflater.from(context).inflate(R.layout.main_layout, null)
        val tenKey = root.findViewById<TenKey>(R.id.keyboard_view)
        val notifiedModes = mutableListOf<InputMode>()
        tenKey.setOnInputModeChangedListener { inputMode ->
            notifiedModes.add(inputMode)
        }

        val switchMethod = TenKey::class.java.getDeclaredMethod("handleClickInputModeSwitch")
        switchMethod.isAccessible = true
        switchMethod.invoke(tenKey)

        assertEquals(listOf(InputMode.ModeEnglish), notifiedModes)
    }
}
