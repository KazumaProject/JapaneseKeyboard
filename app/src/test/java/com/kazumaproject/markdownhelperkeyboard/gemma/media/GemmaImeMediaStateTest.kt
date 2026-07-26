package com.kazumaproject.markdownhelperkeyboard.gemma.media

import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaInputModality
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaImeMediaStateTest {
    @Test
    fun hiddenState_isNotVisibleAndHasNoMedia() {
        assertFalse(GemmaImeMediaState.Hidden.visible)
        assertFalse(GemmaImeMediaState.Hidden.hasMedia)
        assertNull(GemmaImeMediaState.Hidden.imageSource)
    }

    @Test
    fun readyImageState_reportsMediaAvailability() {
        val state = GemmaImeMediaState(
            visible = true,
            modality = GemmaInputModality.IMAGE,
            phase = GemmaImeMediaPhase.READY,
            mediaPath = "/cache/gemma_media/image.png",
            imageSource = GemmaImeImageSource.CLIPBOARD,
        )

        assertTrue(state.visible)
        assertTrue(state.hasMedia)
        assertTrue(state.imageSource == GemmaImeImageSource.CLIPBOARD)
    }
}
