package com.kazumaproject.core.data.popup

import org.junit.Assert.assertEquals
import org.junit.Test

class TfbiPopupPresentationModeTest {

    @Test
    fun unknownPreferenceKeepsLegacyPresentation() {
        assertEquals(
            TfbiPopupPresentationMode.LEGACY_GRID,
            TfbiPopupPresentationMode.fromPreference("unknown")
        )
    }

    @Test
    fun guideModeRoundTripsThroughPreferenceValue() {
        assertEquals(
            TfbiPopupPresentationMode.GUIDE_ABOVE_KEY,
            TfbiPopupPresentationMode.fromPreference(
                TfbiPopupPresentationMode.GUIDE_ABOVE_KEY.preferenceValue
            )
        )
    }
}
