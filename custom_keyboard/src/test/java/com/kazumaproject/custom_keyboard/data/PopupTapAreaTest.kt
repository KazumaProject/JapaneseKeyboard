package com.kazumaproject.custom_keyboard.data

import com.kazumaproject.core.domain.flick.FlickGestureMath
import org.junit.Assert.*
import org.junit.Test

class PopupTapAreaTest {
    private fun threshold(sensitivity: Int)=FlickGestureMath.thresholdPxForSensitivity(sensitivity,8,1.5f,3.5f,4.25f)

    @Test fun defaultAndUnknownPreferencePreserveWholeKeyTaps() {
        for(value in listOf(null,"key","unknown")) {
            val area=PopupTapArea.resolve(PopupTapSizeMode.fromPreference(value),100f,60f,12f)
            assertEquals(PopupTapArea.KEY,area)
            assertTrue(area.contains(.49f,.49f,100f,60f))
        }
    }
    @Test fun sensitivityUsesPhysicalDistanceAndKeepsTheBoxWithinTheKey() {
        val small=PopupTapArea.resolve(PopupTapSizeMode.FLICK_SENSITIVITY,100f,60f,threshold(1))
        val medium=PopupTapArea.resolve(PopupTapSizeMode.FLICK_SENSITIVITY,100f,60f,threshold(100))
        val large=PopupTapArea.resolve(PopupTapSizeMode.FLICK_SENSITIVITY,100f,60f,threshold(200))
        assertEquals(24f,small.bounds.width*100f,.001f)
        assertEquals(24f,small.bounds.height*60f,.001f)
        assertTrue(small.bounds.width<medium.bounds.width)
        assertTrue(medium.bounds.width<large.bounds.width)
        assertEquals(1f,large.bounds.height,.001f)
        assertTrue(large.bounds.left>=-.5f && large.bounds.right<=.5f)
    }
    @Test fun roundedTapHitsAndDirectPopupHitsUseTheirDrawnRegions() {
        val area=PopupTapArea.resolve(PopupTapSizeMode.FLICK_SENSITIVITY,200f,100f,20f)
        val value=SumirePopupDefaults.create("は")
        val session=PopupMapSession(value,200f,100f,PopupMapInputOptions(nearestEnabled=false),area)
        assertEquals("は",session.release(.099f,0f))
        assertNull(session.release(.101f,0f))
        assertNull(session.release(.099f,.199f)) // Outside the rounded corner.
        assertEquals("へ",session.release(1f,0f))
        assertEquals(value,SumirePopupDefaults.create("は"))
    }
    @Test fun nearestSelectionStartsOutsideTheConfiguredTapArea() {
        val area=PopupTapArea.resolve(PopupTapSizeMode.FLICK_SENSITIVITY,200f,100f,20f)
        val session=PopupMapSession(SumirePopupDefaults.create("は"),200f,100f,tapArea=area)
        assertEquals("は",session.release(.09f,0f))
        assertEquals("へ",session.release(.11f,0f))
        assertFalse(session.isTap)
        assertEquals("ふ",session.release(0f,-.21f))
    }
    @Test fun offCenterSensitivityTapsAndShortFlicksUseTheInitialTouch() {
        val area=PopupTapArea.resolve(PopupTapSizeMode.FLICK_SENSITIVITY,200f,100f,20f)
        for((x,y) in listOf(-.49f to -.49f,.49f to .49f,0f to 0f)) {
            val session=PopupMapSession(SumirePopupDefaults.create("は"),200f,100f,tapArea=area,tapOriginX=x,tapOriginY=y)
            assertTrue(session.isTap)
            assertEquals("は",session.release(x,y))
            assertEquals("は",session.release(x+.09f,y))
            assertEquals("へ",session.release(x+.11f,y))
            assertFalse(session.isTap)
        }
    }
    @Test fun horizontalMovementAcrossTheReservedKeyKeepsItsDirectionAfterAnEdgePress() {
        val area=PopupTapArea.resolve(PopupTapSizeMode.FLICK_SENSITIVITY,200f,100f,20f)
        for(originX in listOf(-.49f,.49f)) for(y in listOf(-.49f,.49f)) {
            val session=PopupMapSession(SumirePopupDefaults.create("は"),200f,100f,tapArea=area,tapOriginX=originX,tapOriginY=y)
            val direction=if(originX<0f) 1f else -1f
            val expected=if(direction>0f) "へ" else "ひ"
            for(distance in listOf(.11f,.5f,.98f)) {
                assertEquals("Movement from ($originX,$y) by $distance is horizontal",expected,session.release(originX+direction*distance,y))
            }
        }
    }
    @Test fun offCenterPressDoesNotShiftDirectShapeHitsOrMapTransitions() {
        val area=PopupTapArea.resolve(PopupTapSizeMode.FLICK_SENSITIVITY,200f,100f,20f)
        val bounds=PopupBounds(.7f,-.2f,1.1f,.2f)
        val config=PopupKeyConfig("は","normal",listOf(
            PopupMap("normal","通常","は",listOf(PopupRegion("switch",bounds,PopupAction(PopupActionType.SWITCH,targetMapId="next")))),
            PopupMap("next","次","ば",listOf(PopupRegion("text",bounds,PopupAction(text="○"),outline=PopupOutline())))),version=2)
        val session=PopupMapSession(config,200f,100f,PopupMapInputOptions(nearestEnabled=false),area,-.375f,.25f)
        // A point outside the actual contour must not switch the map.
        session.move(.71f,.19f)
        assertEquals("normal",session.currentMap.id)
        session.move(.9f,0f)
        assertEquals("next",session.currentMap.id)
        assertEquals("○",session.release(.9f,0f))
        assertEquals("ば",session.release(-.375f,.25f))
    }
    @Test fun aGestureKeepsItsFrozenTapAreaWhenSettingsChange() {
        val first=PopupTapArea.resolve(PopupTapSizeMode.FLICK_SENSITIVITY,200f,100f,12f)
        val session=PopupMapSession(SumirePopupDefaults.create("は"),200f,100f,tapArea=first)
        val next=PopupTapArea.resolve(PopupTapSizeMode.FLICK_SENSITIVITY,200f,100f,34f)
        assertFalse(first.contains(.1f,0f,200f,100f))
        assertTrue(next.contains(.1f,0f,200f,100f))
        assertEquals("へ",session.release(.1f,0f))
        assertEquals(first,session.tapArea)
    }
    @Test fun aNewSensitivitySizedPopupDoesNotJumpToTheOldMinimumWhenResizingStarts() {
        val region=PopupRegion("small",PopupBounds(.7f,.7f,.94f,.94f))
        val config=SumirePopupDefaults.create("は").let {it.withMap(it.maps.single().copy(regions=listOf(region)))}
        for(handle in listOf(PopupBoundsSnapper.LEFT,PopupBoundsSnapper.RIGHT,PopupBoundsSnapper.TOP,PopupBoundsSnapper.BOTTOM)) {
            val snapper=PopupBoundsSnapper(config,config.startMapId,region.id,PopupBounds(-3f,-3f,3f,3f),100f,60f,1f,handle)
            assertEquals(region.bounds,snapper.constrain(region.bounds,region.bounds))
        }
    }
}
