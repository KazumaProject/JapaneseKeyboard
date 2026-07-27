package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

import org.junit.Assert.assertEquals
import org.junit.Test

class CursorKeyFlickResolverTest {
    @Test
    fun shortMovementRemainsTap() {
        assertEquals(
            CursorKeyGesture.Tap,
            CursorKeyFlickResolver.resolve(
                deltaX = 3f,
                deltaY = -5f,
                threshold = 16f,
            ),
        )
    }

    @Test
    fun verticalFlickMovesInRequestedDirection() {
        assertEquals(
            CursorKeyGesture.FlickUp,
            CursorKeyFlickResolver.resolve(
                deltaX = 4f,
                deltaY = -24f,
                threshold = 16f,
            ),
        )
        assertEquals(
            CursorKeyGesture.FlickDown,
            CursorKeyFlickResolver.resolve(
                deltaX = -3f,
                deltaY = 24f,
                threshold = 16f,
            ),
        )
    }

    @Test
    fun horizontalOrAmbiguousDragDoesNotTriggerCursorMovement() {
        assertEquals(
            CursorKeyGesture.Cancelled,
            CursorKeyFlickResolver.resolve(
                deltaX = 24f,
                deltaY = -8f,
                threshold = 16f,
            ),
        )
        assertEquals(
            CursorKeyGesture.Cancelled,
            CursorKeyFlickResolver.resolve(
                deltaX = 20f,
                deltaY = 20f,
                threshold = 16f,
            ),
        )
    }
}
