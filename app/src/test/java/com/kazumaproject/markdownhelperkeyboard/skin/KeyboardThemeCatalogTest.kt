package com.kazumaproject.markdownhelperkeyboard.skin

import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme.KeyboardThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class KeyboardThemeCatalogTest {
    @Test fun everyStoredThemeIsSelectableAndHasARenderer() {
        val options = KeyboardThemeCatalog.options
        assertEquals(KeyboardSkinId.entries.toSet(), options.map { it.id }.toSet())
        assertEquals(options.size, options.map { it.id }.distinct().size)
        options.filter { it.id != KeyboardSkinId.DEFAULT }.forEach {
            assertNotNull(KeyboardSkinRegistry.find(it.id))
        }
    }
}
