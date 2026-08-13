package com.kazumaproject.custom_keyboard.controller

import org.junit.Assert.assertEquals
import org.junit.Test

class TfbiGuidePopupPlacementTest {

    @Test
    fun placesGuideAboveKeyAndCentersIt() {
        val placement = resolveTfbiGuidePopupPlacement(
            keyLeft = 200,
            keyTop = 500,
            keyWidth = 100,
            panelWidth = 140,
            panelHeight = 110,
            hostWidth = 600,
            hostHeight = 900,
            gap = 8
        )

        assertEquals(180, placement.panelLeft)
        assertEquals(382, placement.panelTop)
        assertEquals(200, placement.arrowLeft)
        assertEquals(500, placement.arrowTop)
    }

    @Test
    fun clampsGuideToHostTopWhenTopHasNoRoom() {
        val placement = resolveTfbiGuidePopupPlacement(
            keyLeft = -20,
            keyTop = 10,
            keyWidth = 100,
            panelWidth = 160,
            panelHeight = 90,
            hostWidth = 300,
            hostHeight = 300,
            gap = 6
        )

        assertEquals(0, placement.panelLeft)
        assertEquals(0, placement.panelTop)
        assertEquals(-20, placement.arrowLeft)
        assertEquals(10, placement.arrowTop)
    }
}
