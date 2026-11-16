package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.*
import org.junit.Test

class PopupOutlineTest {
    private val bounds = PopupBounds(.6f, -.4f, 1.6f, .6f)
    private fun region(outline: PopupOutline?) = PopupRegion("shape", bounds, PopupAction(text = "★"), outline = outline)
    private val concave = PopupOutline(listOf(PopupVertex(0f, 0f), PopupVertex(1f, 0f),
        PopupVertex(1f, .35f), PopupVertex(.35f, .35f), PopupVertex(.35f, 1f), PopupVertex(0f, 1f)))

    @Test fun legacyRoundedRectanglesKeepTheirExistingGeometry() {
        val shape = region(null)
        for (x in listOf(.6f, .7f, 1.2f, 2f)) for (y in listOf(-.4f, 0f, .55f, 1f)) {
            assertEquals(bounds.contains(x, y, 200f, 100f), shape.contains(x, y, 200f, 100f))
            assertEquals(bounds.distanceTo(x, y, 200f, 100f), shape.distanceTo(x, y, 200f, 100f), .001f)
        }
    }

    @Test fun circlesStayCircularAndUseTheirEdgeForNearestDistance() {
        val circle = region(PopupOutline())
        assertTrue(circle.contains(1.1f, .1f, 200f, 100f))
        assertTrue(circle.contains(1.34f, .1f, 200f, 100f))
        assertFalse(circle.contains(1.4f, .1f, 200f, 100f))
        assertFalse(circle.contains(.65f, -.35f, 200f, 100f))
        assertEquals(10f, circle.distanceTo(1.4f, .1f, 200f, 100f), .001f)
        assertEquals(10f, circle.distanceTo(1.1f, .7f, 200f, 100f), .001f)
    }

    @Test fun concavePolygonsRejectTheMissingAreaAndMeasureTheActualEdges() {
        val polygon = region(concave)
        assertTrue(concave.isValid())
        assertTrue(polygon.contains(.8f, .4f, 200f, 100f))
        assertFalse(polygon.contains(1.4f, .4f, 200f, 100f))
        assertEquals(45f, polygon.distanceTo(1.4f, .4f, 200f, 100f), .001f)
        val label = polygon.labelBounds(200f, 100f)
        assertTrue(polygon.contains((label.left + label.right) / 2, (label.top + label.bottom) / 2, 200f, 100f))
        assertTrue(polygon.contains(label.left, label.top, 200f, 100f))
        assertTrue(polygon.contains(label.right, label.bottom, 200f, 100f))
    }

    @Test fun malformedCrossedAndDegeneratePolygonsAreRejected() {
        val invalid = listOf(
            listOf(PopupVertex(0f, 0f)), listOf(PopupVertex(0f, 0f), PopupVertex(1f, 1f)),
            listOf(PopupVertex(0f, 0f), PopupVertex(1f, 1f), PopupVertex(0f, 1f), PopupVertex(1f, 0f)),
            listOf(PopupVertex(0f, 0f), PopupVertex(.5f, 0f), PopupVertex(1f, 0f)),
            listOf(PopupVertex(0f, 0f), PopupVertex(1f, 0f), PopupVertex(.5f, 0f), PopupVertex(1f, 1f)),
            listOf(PopupVertex(Float.NaN, 0f), PopupVertex(1f, 0f), PopupVertex(1f, 1f)),
            listOf(PopupVertex(-.1f, 0f), PopupVertex(1f, 0f), PopupVertex(1f, 1f)),
            List(17) { PopupVertex(it / 17f, it / 17f) }
        )
        invalid.forEach { assertFalse(it.toString(), PopupOutline(it).isValid()) }
        assertTrue(PopupOutline(PopupOutline.RECTANGLE).isValid())
        assertTrue(PopupOutline(listOf(PopupVertex(0f, 0f), PopupVertex(.5f, 0f), PopupVertex(1f, 0f), PopupVertex(1f, 1f), PopupVertex(0f, 1f))).isValid())
    }

    @Test fun customShapesUpgradeTheFormatAndMoveWithTheirPlacementBounds() {
        val original = SumirePopupDefaults.create("は")
        val polygon = original.maps.single().regions.first().copy(outline = concave)
        val config = original.withMap(original.maps.single().copy(regions = listOf(polygon)))
        assertEquals(2, config.version)
        assertTrue(PopupMapValidator.conflicts(config).isEmpty())
        assertTrue(PopupMapValidator.conflicts(config.copy(version = 1)).any { it.type == PopupConflictType.INVALID })
        assertTrue(PopupMapValidator.conflicts(config.copy(version = 3)).any { it.type == PopupConflictType.INVALID })
        val moved = region(concave).copy(bounds = bounds.offset(2f, 3f))
        assertEquals(region(concave).contains(.8f, .4f), moved.contains(2.8f, 3.4f))
        assertEquals(region(concave).distanceTo(1.4f, .4f, 200f, 100f), moved.distanceTo(3.4f, 3.4f, 200f, 100f), .001f)
    }

    @Test fun runtimeUsesActualShapesForDirectAndNearestSelections() {
        val polygon = region(concave)
        val map = PopupMap("root", "通常", "は", listOf(polygon))
        val config = PopupKeyConfig("は", "root", listOf(map), version = 2)
        val direct = PopupMapSession(config, 200f, 100f, PopupMapInputOptions(nearestEnabled = false))
        assertNull(direct.release(1.4f, .4f))
        assertEquals("★", direct.release(.8f, .4f))
        assertEquals("★", PopupMapSession(config, 200f, 100f).release(1.4f, .4f))
        assertEquals("は", direct.release(0f, 0f))
        val circle = polygon.copy(outline = PopupOutline(), action = PopupAction(text = "○"))
        val circleConfig = config.withMap(map.copy(regions = listOf(circle)))
        assertNull(PopupMapSession(circleConfig, 200f, 100f, PopupMapInputOptions(nearestEnabled = false)).release(1.4f, .1f))
    }

    @Test fun nearestSelectionDoesNotTreatTheCirclePlacementBoxAsAHit() {
        val circle = region(PopupOutline())
        val above = PopupRegion("above", PopupBounds(.6f, -1f, 1.6f, -.5f), PopupAction(text = "上"))
        val config = PopupKeyConfig("は", "root", listOf(PopupMap("root", "通常", "は", listOf(circle, above))), version = 2)
        assertTrue(circle.bounds.contains(1.55f, -.35f, 200f, 100f))
        assertFalse(circle.contains(1.55f, -.35f, 200f, 100f))
        assertEquals("上", PopupMapSession(config, 200f, 100f).release(1.55f, -.35f))
    }

    @Test fun circleControlsOnlySwitchOnTheirContourAndArrivalTextCanReleaseImmediately() {
        val open = region(PopupOutline()).copy(action = PopupAction(PopupActionType.SWITCH, targetMapId = "child"))
        val arrival = open.copy(id = "arrival", action = PopupAction(text = "着"))
        val config = PopupKeyConfig("は", "root", listOf(PopupMap("root", "通常", "は", listOf(open)), PopupMap("child", "次", "ば", listOf(arrival))), version = 2)
        val session = PopupMapSession(config, 200f, 100f, PopupMapInputOptions(nearestEnabled = false))
        session.move(1.55f, -.35f); assertEquals("root", session.currentMap.id)
        session.move(1.1f, .1f); assertEquals("child", session.currentMap.id)
        assertEquals("着", session.release(1.1f, .1f))
    }
}
