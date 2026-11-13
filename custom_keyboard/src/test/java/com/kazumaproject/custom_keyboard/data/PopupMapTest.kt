package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.*
import org.junit.Test

class PopupMapTest {
    @Test fun onlyEditableJapaneseTextKeysUseTheNewStyleAndTapDoesNotToggle() {
        val builder=com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
        for(method in listOf("toggle","flick")) {
            val layout=builder.createFinalLayout(KeyboardInputMode.HIRAGANA,emptyMap(),method,SumirePopupDefaults.STYLE)
            assertEquals(SumirePopupDefaults.rows.size,layout.keys.count {it.popupMapConfig!=null})
            assertTrue(layout.keys.filter {it.popupMapConfig!=null}.all {it.textInputBehavior==KeyTextInputBehavior.NORMAL && !it.isSpecialKey})
        }
        for(mode in listOf(KeyboardInputMode.ENGLISH,KeyboardInputMode.SYMBOLS)) {
            assertTrue(builder.createFinalLayout(mode,emptyMap(),"flick",SumirePopupDefaults.STYLE).keys.none {it.popupMapConfig!=null})
        }
        assertTrue(builder.createFinalLayout(KeyboardInputMode.HIRAGANA,emptyMap(),"flick","default").keys.none {it.popupMapConfig!=null})
    }
    @Test fun allInitialMapsAreValid() {
        SumirePopupDefaults.rows.keys.forEach { assertEquals(it, emptyList<PopupConflict>(), PopupMapValidator.conflicts(SumirePopupDefaults.create(it))) }
    }
    @Test fun defaultFlickPopupsMatchTapSizeAndTouchTheOriginalKey() {
        for (key in SumirePopupDefaults.rows.keys) {
            val config = SumirePopupDefaults.create(key)
            assertTrue(PopupMapValidator.conflicts(config).isEmpty())
            for (region in config.maps.single().regions) {
                val b = region.bounds
                assertEquals(PopupBounds.KEY.width,b.width,.0001f); assertEquals(PopupBounds.KEY.height,b.height,.0001f)
                assertTrue(b.right == -.5f || b.left == .5f || b.bottom == -.5f || b.top == .5f)
            }
        }
    }
    @Test fun tapBelongsToActiveMapAndBackRestoresPreviousTap() {
        val session = PopupMapSession(LegacyPopupFixture.create("は"))
        assertEquals("は", session.release(.45f,.45f))
        session.move(-1f,-2f)
        assertEquals("voiced", session.currentMap.id)
        assertEquals("ば",session.release(-.4f,.4f))
        session.move(0f,-3f)
        assertEquals("normal",session.currentMap.id)
        assertEquals("は",session.release(0f,0f))
    }
    @Test fun textUnderFingerAfterSwitchCommitsWithoutReentry() {
        val initial = LegacyPopupFixture.create("は")
        val target = initial.map("voiced")!!
        val config = initial.withMap(target.copy(regions = target.regions + PopupRegion("immediate", initial.map("normal")!!.regions.first { it.isControl }.bounds, PopupAction(text="ありがとう"))))
        val session = PopupMapSession(config)
        session.move(-1f,-2f)
        assertEquals("ありがとう", session.release(-1f,-2f))
    }
    @Test fun controlsCannotAppearUnderFingerOnEitherForwardOrBackTransition() {
        val initial = LegacyPopupFixture.create("は")
        val target = initial.map("voiced")!!
        val source = initial.map("normal")!!
        val bounds = source.regions.first { it.isControl }.bounds
        val invalid = initial.withMap(target.copy(regions=target.regions + PopupRegion("loop", bounds, PopupAction(PopupActionType.BACK))))
        assertTrue(PopupMapValidator.conflicts(invalid).any { it.type==PopupConflictType.TRANSITION })
        // A different control in the parent also matters when Back returns to it.
        val back = target.regions.first { it.action.type==PopupActionType.BACK }
        val invalidBack = initial.withMap(source.copy(regions=source.regions + PopupRegion("other", back.bounds, PopupAction(PopupActionType.SWITCH,targetMapId="semi"))))
        assertTrue(PopupMapValidator.conflicts(invalidBack).any { it.mapId=="voiced" && it.otherMapId=="normal" })
    }
    @Test fun overlapAndKeyIntrusionAreRejected() {
        val initial=LegacyPopupFixture.create("か"); val map=initial.map("normal")!!
        assertTrue(PopupMapValidator.conflicts(initial.withMap(map.copy(regions=map.regions + map.regions.first().copy(id="duplicate")))).any { it.type==PopupConflictType.OVERLAP })
        assertTrue(PopupMapValidator.conflicts(initial.withMap(map.copy(regions=map.regions + PopupRegion("key",PopupBounds.KEY)))).any { it.type==PopupConflictType.KEY })
    }
    @Test fun releaseUsesCurrentPositionNotPreviouslyVisitedText() {
        val session=PopupMapSession(LegacyPopupFixture.create("か"),options=PopupMapInputOptions(nearestEnabled=false))
        session.move(-1f,-.7f)
        assertEquals("き",session.selectedText)
        assertNull(session.release(3f,3f))
        assertEquals("か",session.release(.49f,-.49f))
    }
    @Test fun missingAndSelfTargetsCannotRun() {
        val initial=LegacyPopupFixture.create("か"); val map=initial.map("normal")!!
        listOf("missing","normal").forEach { target ->
            val bad=initial.withMap(map.copy(regions=map.regions.map { if(it.isControl) it.copy(action=it.action.copy(targetMapId=target)) else it }))
            assertTrue(PopupMapValidator.conflicts(bad).any { it.type==PopupConflictType.TARGET })
        }
    }
    @Test fun roundedCornerIsNotAnInvisibleHitTarget() {
        val bounds=PopupBounds(-1f,-2f,0f,-1f)
        assertFalse(bounds.contains(-1f,-2f))
        assertTrue(bounds.contains(-.5f,-1.5f))
    }
    @Test fun nonSquareKeysUseTheSamePixelRadiusForDrawingAndHitTesting() {
        val bounds=PopupBounds(0f,0f,1f,1f)
        assertTrue(bounds.contains(.05f,.03f,200f,100f))
        assertFalse(bounds.contains(.02f,.02f,200f,100f))
        assertFalse(bounds.contains(1f,.5f,200f,100f))
    }
}

/** Saved maps produced by the parent PR, used to verify v1 compatibility. */
private object LegacyPopupFixture {
    val rows = linkedMapOf("あ" to "あいうえお", "か" to "かきくけこ", "さ" to "さしすせそ", "た" to "たちつてと", "な" to "なにぬねの", "は" to "はひふへほ", "ま" to "まみむめも", "や" to "やゆよ", "ら" to "らりるれろ", "わ" to "わをんー")
    private val voiced = mapOf("か" to "がぎぐげご", "さ" to "ざじずぜぞ", "た" to "だぢづでど", "は" to "ばびぶべぼ")
    private val slots = listOf(PopupBounds(-1.4f,-1.1f,-.6f,-.3f), PopupBounds(-.4f,-1.4f,.4f,-.6f), PopupBounds(.6f,-1.1f,1.4f,-.3f), PopupBounds(-.4f,-2.4f,.4f,-1.6f))
    fun create(keyId: String): PopupKeyConfig {
        val row = rows.getValue(keyId)
        fun page(id: String, chars: String): PopupMap {
            val regions = chars.drop(1).mapIndexed { index, char -> PopupRegion("$id-$index", slots[index], PopupAction(text = char.toString())) }.toMutableList()
            if (id != "normal") regions += PopupRegion("$id-back", PopupBounds(-.4f,-3.4f,.4f,-2.6f), PopupAction(PopupActionType.BACK), "戻る")
            return PopupMap(id, chars.first().toString() + "行", chars.first().toString(), regions)
        }
        val normal = page("normal", row).copy(name = "通常")
        val maps = mutableListOf(normal)
        voiced[keyId]?.let { maps += page("voiced", it) }
        if (keyId == "は") maps += page("semi", "ぱぴぷぺぽ")
        val controls = maps.drop(1).mapIndexed { index, target ->
            val x = if (index == 0) -1.4f else .6f
            PopupRegion("open-${target.id}", PopupBounds(x,-2.4f,x+.8f,-1.6f), PopupAction(PopupActionType.SWITCH, targetMapId = target.id), target.name)
        }
        return PopupKeyConfig(keyId, "normal", listOf(normal.copy(regions = normal.regions + controls)) + maps.drop(1))
    }


}
