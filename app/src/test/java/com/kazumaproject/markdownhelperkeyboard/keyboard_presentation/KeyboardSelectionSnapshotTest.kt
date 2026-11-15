package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardSelectionSnapshotTest {
    @Test fun serviceStateCanBeClearedWithoutLosingTheLastRealIdentityAndMode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val target = KeyboardPreviewTarget(KeyboardType.CUSTOM, "stable-second", 22, InputMode.ModeEnglish,
            customDirectMode = true, customRomajiMode = false)
        KeyboardPreviewSelection.publish(target, context)
        KeyboardPreviewSelection.publish(null)
        assertNull(KeyboardPreviewSelection.current.value)
        assertEquals(target, KeyboardPreviewSelection.lastUsed(context))
        assertEquals("CUSTOM:stable-second", KeyboardPreviewSelection.lastUsed(context)!!.id)
    }
    @Test fun renderAndTrialTargetsDoNotOverwriteTheStoredRealKeyboard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val real = KeyboardPreviewTarget(KeyboardType.SUMIRE, inputMode = InputMode.ModeNumber, presentation = "native")
        KeyboardPreviewSelection.publish(real, context)
        resolveHomeKeyboardPreviewTarget(listOf(KeyboardType.TENKEY, KeyboardType.SUMIRE), emptyList(), false, 0, false, null,
            live = null, lastUsed = KeyboardPreviewSelection.lastUsed(context))
        keyboardPreviewTargets(listOf(KeyboardType.TENKEY), emptyList())
        val trial = KeyboardTrialSession.begin(KeyboardPreviewTarget(KeyboardType.TENKEY))
        try {
            assertTrue(KeyboardTrialSession.active.value)
            assertEquals(real, KeyboardPreviewSelection.lastUsed(context))
            assertEquals(real, KeyboardPreviewSelection.current.value)
        } finally { KeyboardTrialSession.end(trial) }
        KeyboardPreviewSelection.publish(null)
    }
}
