package com.kazumaproject.markdownhelperkeyboard.ime_service

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on ART: JVM tests cannot catch invalid DEX invocation register counts. */
@RunWith(AndroidJUnit4::class)
class ImePreferencesSnapshotDeviceTest {
    @Test
    fun snapshotLoadsAndCopiesForEverySkinOnArt() {
        AppPreference.init(InstrumentationRegistry.getInstrumentation().targetContext)
        val saved = ImePreferencesSnapshot.from(AppPreference)
        val baseline = saved.copy(keyboardSkin = KeyboardSkinId.DEFAULT)
        assertSame(baseline, baseline.withKeyboardSkinAppearance())
        for (skin in listOf(KeyboardSkinId.CUPERTINO_LIGHT, KeyboardSkinId.CUPERTINO_DARK,
            KeyboardSkinId.CUPERTINO_CLASSIC)) {
            val selected = baseline.copy(keyboardSkin = skin)
            val effective = selected.withKeyboardSkinAppearance()
            assertEquals("custom", effective.keyboardThemeMode)
            assertEquals(KeyboardSkinRegistry.find(skin)!!.palette.key, effective.customThemeKeyColor)
            assertEquals(selected, effective.copy(appearance = selected.appearance))
            assertEquals(saved.independentMultiTouchEnabled, effective.independentMultiTouchEnabled)
            assertEquals(saved.stabilizeCandidateStripHeightPreference, effective.stabilizeCandidateStripHeightPreference)
        }
    }
}
