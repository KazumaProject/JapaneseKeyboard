package com.kazumaproject.custom_keyboard.data

import com.kazumaproject.core.domain.flick.*
import org.junit.Assert.*
import org.junit.Test

class PopupMapVisibilityTest {
    private val settings=GestureSessionConfig(7L,100,40f,300L)
    @Test fun normalShowsOnlyTheCommittedSelectionIncludingNearestOutside() {
        val session=PopupMapSession(SumirePopupDefaults.create("は"),100f,64f)
        val policy=PopupMapVisibility(settings,50f,32f)
        assertTrue(policy.visible);assertTrue(policy.showsTap(session));assertEquals(emptySet<String>(),policy.regions(session))
        session.move(-20f,0f)
        assertFalse(policy.showsTap(session));assertEquals(setOf(session.selectedRegionId),policy.regions(session))
        assertEquals(session.selectedText,session.release(-20f,0f))
    }
    @Test fun holdingOriginalKeyRevealsAllUntilTheGestureEnds() {
        val session=PopupMapSession(SumirePopupDefaults.create("は"))
        val policy=PopupMapVisibility(settings,0f,0f)
        assertTrue(policy.longPress(session.isTap));assertTrue(policy.expanded)
        session.move(3f,0f);policy.move(300f,0f)
        assertNull(policy.regions(session));assertTrue(policy.showsTap(session));assertTrue(policy.visible)
    }
    @Test fun movingOutAndBackCannotRearmLongPress() {
        val policy=PopupMapVisibility(settings,10f,10f)
        policy.move(31f,10f);policy.move(10f,10f)
        assertFalse(policy.longPress(true));assertFalse(policy.expanded);assertTrue(policy.visible)
    }
    @Test fun visibilityFlagsAreIndependentAndFrozenForThisGesture() {
        for(flick in listOf(false,true)) for(long in listOf(false,true)) {
            val config=settings.copy(showFlickPopup=flick,showLongPressPopup=long)
            val policy=PopupMapVisibility(config,0f,0f)
            assertEquals(flick,policy.visible)
            assertTrue(policy.longPress(true));assertEquals(long,policy.visible);assertEquals(long,policy.expanded)
            assertEquals(7L,policy.settings.settingsRevision)
        }
    }
    @Test fun movementCancellationUsesTheSameHalfThresholdAndShapeAsSumire() {
        val radial=PopupMapVisibility(settings,0f,0f)
        radial.move(15f,15f);assertFalse(radial.longPressEligible)
        val rectangle=PopupMapVisibility(settings.copy(flickThresholdShape=FlickThresholdShape.Rectangular),0f,0f)
        rectangle.move(15f,15f);assertTrue(rectangle.longPressEligible)
        rectangle.move(20f,0f);assertFalse(rectangle.longPressEligible)
    }
    @Test fun aboveShowsAllRegionsImmediatelyWithEitherPopupFlagAndNeverDependsOnHolding() {
        val session=PopupMapSession(SumirePopupDefaults.create("は"))
        for(flick in listOf(false,true)) for(long in listOf(false,true)) {
            val policy=PopupMapVisibility(settings.copy(showFlickPopup=flick,showLongPressPopup=long),0f,0f,PopupMapPresentation.GUIDE_ABOVE_KEY)
            assertEquals(flick || long,policy.visible)
            assertNull(policy.regions(session));assertTrue(policy.showsTap(session))
            assertFalse(policy.longPress(true));policy.move(300f,0f);session.move(1f,0f)
            assertEquals(flick || long,policy.visible)
            assertNull(policy.regions(session));assertTrue(policy.showsTap(session))
        }
    }
    @Test fun aboveKeepsTheTapRangeAndEveryCurrentMapRegionVisibleAfterSwitchingMaps() {
        val original=SumirePopupDefaults.create("は")
        val root=original.maps.single().let {it.copy(regions=it.regions.mapIndexed {i,r -> if(i==0)
            r.copy(action=PopupAction(PopupActionType.SWITCH,targetMapId="child")) else r})}
        val child=original.maps.single().copy(id="child",name="濁音",tapText="ば")
        val session=PopupMapSession(original.copy(maps=listOf(root,child)))
        val policy=PopupMapVisibility(settings,0f,0f,PopupMapPresentation.GUIDE_ABOVE_KEY)
        session.move(-1f,0f)
        assertEquals("child",session.currentMap.id)
        assertNull(policy.regions(session));assertTrue(policy.showsTap(session));assertTrue(policy.visible)
    }
}
