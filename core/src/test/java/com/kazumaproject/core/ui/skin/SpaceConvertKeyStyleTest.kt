package com.kazumaproject.core.ui.skin

import com.kazumaproject.core.domain.skin.KeyboardSkinId
import org.junit.Assert.assertEquals
import org.junit.Test

class SpaceConvertKeyStyleTest {
    @Test
    fun classicUsesTheModifierSurfaceForBothSpaceAndConvertIcons() {
        val classic = checkNotNull(KeyboardSkinRegistry.find(KeyboardSkinId.CUPERTINO_CLASSIC))

        assertEquals(
            SpaceConvertKeyStyle(SkinKeyRole.MODIFIER, classic.palette.specialText),
            classic.spaceConvertKeyStyle(),
        )
    }

    @Test
    fun otherSkinsKeepTheirExistingSpaceSurface() {
        listOf(KeyboardSkinId.CUPERTINO_LIGHT, KeyboardSkinId.CUPERTINO_DARK).forEach { id ->
            val skin = checkNotNull(KeyboardSkinRegistry.find(id))

            assertEquals(
                SpaceConvertKeyStyle(SkinKeyRole.SPACE, skin.palette.spaceText),
                skin.spaceConvertKeyStyle(),
            )
        }
    }
}
