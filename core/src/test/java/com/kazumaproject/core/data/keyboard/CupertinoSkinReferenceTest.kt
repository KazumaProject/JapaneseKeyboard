package com.kazumaproject.core.data.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class CupertinoSkinReferenceTest {

    @Test
    fun lightPaletteAndGeometryMatchIos26SimulatorReference() {
        val spec = KeyboardSkinCatalog.specFor(KeyboardSkinId.CUPERTINO)

        assertEquals(0xFFE8E9ED.toInt(), spec.palette.backgroundColor)
        assertEquals(0xFFFFFFFF.toInt(), spec.palette.normalKeyColor)
        assertEquals(0xFFFFFFFF.toInt(), spec.palette.specialKeyColor)
        assertEquals(0xFF0091FF.toInt(), spec.palette.actionKeyColor)
        assertEquals(0xFF000000.toInt(), spec.palette.normalKeyTextColor)
        assertEquals(7f, spec.geometry.cornerRadiusDp)
        assertEquals(0f, spec.geometry.depthDp)
        assertEquals(KeyboardSkinDepthModel.NONE, spec.depthModel)
    }

    @Test
    fun darkPaletteAndGeometryMatchIos26SimulatorReference() {
        val spec = KeyboardSkinCatalog.specFor(KeyboardSkinId.CUPERTINO_DARK)

        assertEquals(0xFF171717.toInt(), spec.palette.backgroundColor)
        assertEquals(0xFF3D3D3D.toInt(), spec.palette.normalKeyColor)
        assertEquals(0xFF3D3D3D.toInt(), spec.palette.specialKeyColor)
        assertEquals(0xFF007AFF.toInt(), spec.palette.actionKeyColor)
        assertEquals(0xFFFFFFFF.toInt(), spec.palette.normalKeyTextColor)
        assertEquals(7f, spec.geometry.cornerRadiusDp)
        assertEquals(0f, spec.geometry.depthDp)
        assertEquals(KeyboardSkinDepthModel.NONE, spec.depthModel)
        assertEquals(KeyboardSkinMaterial.CUPERTINO, spec.material)
    }
}
