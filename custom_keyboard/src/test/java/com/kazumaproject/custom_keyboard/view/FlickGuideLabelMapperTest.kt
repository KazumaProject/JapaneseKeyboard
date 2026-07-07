package com.kazumaproject.custom_keyboard.view

import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlickGuideLabelMapperTest {

    @Test
    fun hasVisibleGuides_includesDiagonalDirections() {
        assertTrue(AutoSizeButton.FlickGuideLabels(upRight = "ぁ").hasVisibleGuides())
        assertTrue(AutoSizeButton.FlickGuideLabels(upLeft = "ぱ").hasVisibleGuides())
        assertTrue(AutoSizeButton.FlickGuideLabels(downRight = "」").hasVisibleGuides())
        assertTrue(AutoSizeButton.FlickGuideLabels(downLeft = "「").hasVisibleGuides())
        assertFalse(AutoSizeButton.FlickGuideLabels().hasVisibleGuides())
    }

    @Test
    fun sanitizeGuideText_respectsMaxCodePoints() {
        assertEquals("き", FlickGuideLabelMapper.sanitizeGuideText("きゅ", 1))
        assertEquals("きゅ", FlickGuideLabelMapper.sanitizeGuideText("きゅ", 2))
        assertEquals("きゅう", FlickGuideLabelMapper.sanitizeGuideText("きゅう", 3))
        assertEquals("あいうえ", FlickGuideLabelMapper.sanitizeGuideText("あいうえお", 4))
    }

    @Test
    fun sanitizeGuideText_doesNotSplitSurrogatePairs() {
        assertEquals("😀", FlickGuideLabelMapper.sanitizeGuideText("😀あ", 1))
        assertEquals("😀あ", FlickGuideLabelMapper.sanitizeGuideText("😀あい", 2))
    }

    @Test
    fun buildTwoStepRootGuideLabels_usesOnlyMatchingRootDirections() {
        val twoStepMap = mapOf(
            TfbiFlickDirection.TAP to mapOf(TfbiFlickDirection.TAP to "あ"),
            TfbiFlickDirection.LEFT to mapOf(
                TfbiFlickDirection.TAP to "あ",
                TfbiFlickDirection.LEFT to "い",
                TfbiFlickDirection.DOWN_LEFT to "ぃ"
            ),
            TfbiFlickDirection.UP to mapOf(TfbiFlickDirection.UP to "う"),
            TfbiFlickDirection.UP_RIGHT to mapOf(TfbiFlickDirection.UP_RIGHT to "ぁ"),
            TfbiFlickDirection.RIGHT to mapOf(TfbiFlickDirection.RIGHT to "え"),
            TfbiFlickDirection.DOWN to mapOf(TfbiFlickDirection.DOWN to "お")
        )

        val labels = FlickGuideLabelMapper.buildTwoStepRootGuideLabels(twoStepMap, 1)

        assertEquals("あ", labels.tap)
        assertEquals("い", labels.left)
        assertEquals("う", labels.up)
        assertEquals("ぁ", labels.upRight)
        assertEquals("え", labels.right)
        assertEquals("お", labels.down)
        assertEquals("", labels.downLeft)
        assertEquals("", labels.downRight)
        assertEquals("", labels.upLeft)
    }

    @Test
    fun buildHierarchicalGuideLabels_usesRootNodeDisplayText() {
        val normalMap = mapOf(
            TfbiFlickDirection.TAP to TfbiFlickNode.Input("か"),
            TfbiFlickDirection.LEFT to TfbiFlickNode.Input("き"),
            TfbiFlickDirection.UP to TfbiFlickNode.Input("く"),
            TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input("が"),
            TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("け"),
            TfbiFlickDirection.DOWN to TfbiFlickNode.Input("こ")
        )

        val labels = FlickGuideLabelMapper.buildHierarchicalGuideLabels(normalMap, 1)

        assertEquals("か", labels.tap)
        assertEquals("き", labels.left)
        assertEquals("く", labels.up)
        assertEquals("が", labels.upRight)
        assertEquals("け", labels.right)
        assertEquals("こ", labels.down)
    }
}
