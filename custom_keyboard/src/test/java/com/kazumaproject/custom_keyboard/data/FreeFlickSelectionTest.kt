package com.kazumaproject.custom_keyboard.data

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.key.KeyInfo
import com.kazumaproject.core.domain.key.KeyMap
import org.junit.Assert.*
import org.junit.Test

class FreeFlickSelectionTest {
    private fun config(vararg regions: PopupRegion) = PopupKeyConfig("は","root",listOf(PopupMap("root","通常","は",regions.toList())))
    private fun text(id: String,bounds: PopupBounds) = PopupRegion(id,bounds,PopupAction(text=id))

    @Test fun defaultTemplateMatchesExistingTwelveKeyTextAndDirections() {
        val keys=listOf(Key.KeyA,Key.KeyKA,Key.KeySA,Key.KeyTA,Key.KeyNA,Key.KeyHA,Key.KeyMA,Key.KeyYA,Key.KeyRA,Key.KeyWA)
        for(key in keys) {
            val info=KeyMap().getKeyInfoJapanese(key,false) as KeyInfo.KeyTapFlickInfo
            val config=SumirePopupDefaults.create(info.tap.toString())
            assertEquals(1,config.maps.size)
            assertTrue(config.maps.single().regions.none {it.isControl})
            val session=PopupMapSession(config)
            assertEquals(info.tap.toString(),session.release(0f,0f))
            for((point,expected) in listOf((-1f to 0f) to info.flickLeft,(0f to -1f) to info.flickTop,(1f to 0f) to info.flickRight,(0f to 1f) to info.flickBottom)) {
                assertEquals(expected.toString(),session.release(point.first,point.second))
            }
        }
    }
    @Test fun nearestTextDefaultsOnAndOffRestoresOutsideNoOutput() {
        val value=SumirePopupDefaults.create("あ")
        assertEquals("え",PopupMapSession(value).release(4f,0f))
        assertNull(PopupMapSession(value,options=PopupMapInputOptions(nearestEnabled=false)).release(4f,0f))
        assertNull(PopupMapSession(value,options=PopupMapInputOptions(nearestText=false)).release(4f,0f))
        assertEquals("あ",PopupMapSession(value).release(.49f,.49f))
        assertEquals("お",PopupMapSession(value).release(0f,.51f))
    }
    @Test fun shortestDistanceUsesEdgesAndPhysicalAspectRatio() {
        val wide=text("wide",PopupBounds(.6f,-.4f,4f,.4f))
        val small=text("small",PopupBounds(1f,1f,1.3f,1.3f))
        assertEquals("wide",PopupMapSession(config(wide,small),100f,100f).release(1f,.5f))
        val a=text("A",PopupBounds(.6f,-.4f,1.4f,.4f))
        val b=text("B",PopupBounds(-.4f,.6f,.4f,1.4f))
        assertEquals("A",PopupMapSession(config(a,b),200f,100f).release(1.5f,1.5f))
        assertEquals("B",PopupMapSession(config(a,b),100f,200f).release(1.5f,1.5f))
        assertTrue(a.bounds.distanceTo(.6f,-.4f,200f,100f)>0f)
    }
    @Test fun tiesKeepLastSelectionOtherwiseUseSavedOrder() {
        val a=text("A",PopupBounds(.6f,-.4f,1.4f,.4f))
        val b=text("B",PopupBounds(-.4f,.6f,.4f,1.4f))
        val session=PopupMapSession(config(a,b))
        assertEquals("A",session.release(2f,2f))
        session.move(0f,1f)
        assertEquals("B",session.release(2f,2f))
    }
    @Test fun directHitsWinEvenWhenTheActionIsExcludedFromNearest() {
        val target=PopupMap("child","次","ば")
        val open=PopupRegion("open",PopupBounds(.6f,.6f,1.4f,1.4f),PopupAction(PopupActionType.SWITCH,targetMapId="child"))
        val value=config(text("text",PopupBounds(.6f,-.4f,1.4f,.4f)),open).let {it.copy(maps=it.maps+target)}
        val session=PopupMapSession(value)
        session.move(1.5f,1.5f);assertEquals("root",session.currentMap.id);assertEquals("text",session.selectedText)
        session.move(1f,1f);assertEquals("child",session.currentMap.id)
    }
    @Test fun mapSwitchAndBackAreIndependentAndArrivalNeedsReentry() {
        val open=PopupRegion("open",PopupBounds(.6f,.6f,1.4f,1.4f),PopupAction(PopupActionType.SWITCH,targetMapId="child"))
        val back=PopupRegion("back",PopupBounds(2f,2f,3f,3f),PopupAction(PopupActionType.BACK))
        val value=config(open).let {it.copy(maps=it.maps+PopupMap("child","次","ば",listOf(back)))}
        val noSwitch=PopupMapSession(value,options=PopupMapInputOptions(nearestText=false,nearestBack=true))
        noSwitch.move(5f,5f);assertEquals("root",noSwitch.currentMap.id)
        val noBack=PopupMapSession(value,options=PopupMapInputOptions(nearestSwitch=true))
        noBack.move(5f,5f);noBack.move(0f,0f);noBack.move(5f,5f);assertEquals("child",noBack.currentMap.id)
        val both=PopupMapSession(value,options=PopupMapInputOptions(nearestSwitch=true,nearestBack=true))
        both.move(5f,5f);assertEquals("child",both.currentMap.id)
        repeat(10) {both.move(5.01f,5.01f)}
        assertEquals("child",both.currentMap.id)
        both.move(0f,0f);both.move(5f,5f);assertEquals("root",both.currentMap.id)
        both.move(5.01f,5.01f);assertEquals("root",both.currentMap.id)
    }
    @Test fun emptyTextDoesNotConsumeNearestSelectionAndReleaseDoesNotSwitchMaps() {
        val blank=PopupRegion("empty",PopupBounds(.6f,.6f,1.4f,1.4f))
        assertNull(PopupMapSession(config(blank)).release(5f,5f))
        val value=config(text("A",PopupBounds(.6f,-.4f,1.4f,.4f)),blank)
        assertNull(PopupMapSession(value).release(1f,1f))
        assertEquals("A",PopupMapSession(value).release(5f,5f))
        val open=blank.copy(action=PopupAction(PopupActionType.SWITCH,targetMapId="child"))
        val switching=config(open).let {it.copy(maps=it.maps+PopupMap("child","次","ば"))}
        val session=PopupMapSession(switching,options=PopupMapInputOptions(nearestSwitch=true))
        assertNull(session.release(5f,5f));assertEquals("root",session.currentMap.id)
    }
}
