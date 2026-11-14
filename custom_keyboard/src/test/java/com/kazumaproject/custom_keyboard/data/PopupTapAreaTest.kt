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
