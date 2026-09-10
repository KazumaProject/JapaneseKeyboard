package com.kazumaproject.markdownhelperkeyboard.skin

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.symbol_keyboard.CustomSymbolKeyboardView
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class SymbolSkinRestorationTest {
    @Test fun roundTripRestoresXmlAppearanceAndRejectsPendingSkinTabUpdates() {
        val ctx=ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard)
        val view=CustomSymbolKeyboardView(ctx)
        val button=view.findViewById<View>(com.kazumaproject.symbol_keyboard.R.id.return_jp_keyboard_button)
        val tabs=view.findViewById<TabLayout>(com.kazumaproject.symbol_keyboard.R.id.mode_tab_layout)
        val background=button.background
        val tint=tabs.tabIconTint
        val padding=listOf(button.paddingLeft,button.paddingTop,button.paddingRight,button.paddingBottom)
        view.restoreDefaultKeyboardTheme()
        assertSame(background,button.background)
        for(skin in listOf(KeyboardSkinId.CUPERTINO_LIGHT,KeyboardSkinId.CUPERTINO_DARK)) {
            val p=requireNotNull(KeyboardSkinRegistry.find(skin)).palette
            view.setKeyboardTheme(p.background,p.text,p.selection,p.key,false,skin)
        }
        view.restoreDefaultKeyboardTheme()
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(background,button.background)
        assertEquals(tint,tabs.tabIconTint)
        assertEquals(padding,listOf(button.paddingLeft,button.paddingTop,button.paddingRight,button.paddingBottom))
        assertNull(view.background)
    }
}
