package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.*
import org.junit.Test

class PopupBoundsSnapperTest {
    private val viewport=PopupBounds(-5f,-5f,5f,5f)
    private fun config(bounds: PopupBounds=PopupBounds(.6f,-.4f,1.5f,.4f)) = PopupKeyConfig("は","normal",
        listOf(PopupMap("normal","通常","は",listOf(PopupRegion("r",bounds)))))
    private fun snap(config: PopupKeyConfig=config(),handle: Int=0)=PopupBoundsSnapper(config,"normal","r",viewport,100f,80f,1f,handle)
    @Test fun largeMoveStopsAtContactInsteadOfCrossingTheKey() {
        val before=config().maps.single().regions.single().bounds
        val result=snap().constrain(before,before.offset(-4f,0f))
        assertEquals(.5f,result.left,.00001f)
        assertEquals(before.width,result.width,.00001f)
        assertFalse(result.intersects(PopupBounds.KEY))
    }
    @Test fun diagonalDragSlidesAlongTheKeysBoundary() {
        val before=config().maps.single().regions.single().bounds
        val result=snap().constrain(before,before.offset(-.5f,.2f))
        assertEquals(.5f,result.left,.00001f)
        assertEquals(before.top+.2f,result.top,.00001f)
    }
    @Test fun magneticContactHasDifferentEngageAndReleaseDistances() {
        val before=config().maps.single().regions.single().bounds
        val engine=snap()
        var result=engine.constrain(before,before.offset(-.04f,0f))
        assertEquals(.5f,result.left,.00001f) // 6px away: engage
        result=engine.constrain(result,before.offset(0f,0f))
        assertEquals(.5f,result.left,.00001f) // 10px away: remain held
        result=engine.constrain(result,before.offset(.04f,0f))
        assertEquals(.64f,result.left,.00001f) // 14px away: release
    }
    @Test fun edgesAndCornersResizeAtExactContactAndKeepMinimumSize() {
        val before=config().maps.single().regions.single().bounds
        for(handle in listOf(PopupBoundsSnapper.LEFT,PopupBoundsSnapper.LEFT or PopupBoundsSnapper.TOP)) {
            val engine=snap(handle=handle)
            val result=engine.constrain(before,before.copy(left=-3f,top=if(handle and PopupBoundsSnapper.TOP!=0) -1f else before.top))
            assertEquals(.5f,result.left,.00001f)
            assertEquals(before.right,result.right,0f)
            assertFalse(result.intersects(PopupBounds.KEY))
            val small=engine.constrain(result,result.copy(left=4f))
            assertTrue(small.width>=.34999f)
        }
    }
    @Test fun moveDoesNotTunnelThroughOtherPopupsOrViewport() {
        val original=config(PopupBounds(-3f,-2f,-2f,-1f))
        val page=original.maps.single()
        val withOther=original.withMap(page.copy(regions=page.regions+PopupRegion("other",PopupBounds(-1f,-2f,0f,-1f))))
        val engine=snap(withOther)
        val result=engine.constrain(page.regions.single().bounds,PopupBounds(3f,-2f,4f,-1f))
        assertEquals(-1f,result.right,.00001f)
        assertEquals(withOther.map("normal")!!.regions.last().bounds,PopupBounds(-1f,-2f,0f,-1f))
        val outside=engine.constrain(result,result.offset(0f,-30f))
        assertEquals(viewport.top,outside.top,0f)
    }
    @Test fun transitionControlsRespectCrossMapConflictConstraints() {
        val source=PopupRegion("r",PopupBounds(-3f,-2f,-2f,-1f),PopupAction(PopupActionType.SWITCH,targetMapId="child"))
        val child=PopupRegion("back",PopupBounds(0f,-2f,1f,-1f),PopupAction(PopupActionType.BACK))
        val config=PopupKeyConfig("は","normal",listOf(PopupMap("normal","通常","は",listOf(source)),PopupMap("child","追加","ば",listOf(child))))
        val result=snap(config).constrain(source.bounds,source.bounds.offset(6f,0f))
        assertEquals(0f,result.right,.00001f)
        val changed=config.withMap(config.maps.first().copy(regions=listOf(source.copy(bounds=result))))
        assertTrue(PopupMapValidator.conflicts(changed).isEmpty())
    }
}
