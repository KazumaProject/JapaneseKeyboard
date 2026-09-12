package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CandidatePanelColorsTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),
        com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard)

    @Test fun bothSkinsSupplyEveryColorAndOverrideLegacyCustomColors() {
        val custom = CandidatePanelColors(1, 2, 3, 4, 5, 6, 7)
        for (id in listOf(KeyboardSkinId.CUPERTINO_LIGHT, KeyboardSkinId.CUPERTINO_DARK)) {
            val palette = KeyboardSkinRegistry.find(id)!!.palette
            val actual = CandidatePanelColors.resolve(context, palette, custom)
            assertEquals(CandidatePanelColors(palette.background, palette.key, palette.text,
                palette.pressed, palette.selection, palette.selectionText), actual)
        }
    }

    @Test fun customColorsAreNotReplacedByLightDarkGuesses() {
        val custom = CandidatePanelColors(1, 2, 3, 4, 5, 6, 7)
        assertEquals(custom, CandidatePanelColors.resolve(context, custom = custom))
    }

    @Test @Config(sdk = [28]) fun oldDevicesUseExistingColorResources() {
        val actual = CandidatePanelColors.resolve(context)
        assertEquals(context.getColor(com.kazumaproject.core.R.color.keyboard_bg), actual.background)
        assertEquals(context.getColor(com.kazumaproject.core.R.color.main_text_color), actual.text)
        assertEquals(context.getColor(com.kazumaproject.core.R.color.qwety_key_bg_color), actual.pressed)
    }

    @Test fun themedBackgroundComesFromKeyboardContext() {
        val attr = android.util.TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, attr, true)
        val expected = if (attr.resourceId != 0) context.getColor(attr.resourceId) else attr.data
        assertEquals(expected, CandidatePanelColors.resolve(context).background)
    }
}
