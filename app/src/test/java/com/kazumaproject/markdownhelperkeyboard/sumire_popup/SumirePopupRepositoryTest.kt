package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class SumirePopupRepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val preferences=PreferenceManager.getDefaultSharedPreferences(context)
    private val repository=SumirePopupRepository(context)
    @Before fun setup() {preferences.edit().clear().commit();AppPreference.init(context)}
    @Test fun keysAndMapsPersistIndependentlyAndSurviveBackup() {
        val initial=LegacyPopupFixture.create("は")
        val edited=initial.withMap(initial.map("voiced")!!.copy(tapText="ありがとう"))
        repository.save(edited)
        assertEquals(edited,SumirePopupRepository(context).load("は"))
        assertEquals(SumirePopupDefaults.create("か"),repository.load("か"))
        val backup=AppPreference.exportAllToJson()
        preferences.edit().clear().commit()
        AppPreference.importAllFromJson(backup)
        assertEquals(edited,repository.load("は"))
    }
    @Test fun malformedOrDanglingDataIsNotExecutedOrSilentlyOverwritten() {
        val key=SumirePopupRepository.PREFIX+"は"
        preferences.edit().putString(key,"{broken").commit()
        assertTrue(repository.hasInvalidSavedConfig("は"))
        assertEquals(SumirePopupDefaults.create("は"),repository.load("は"))
        assertEquals("{broken",preferences.getString(key,null))
        val original=LegacyPopupFixture.create("は")
        val invalid=original.copy(maps=original.maps.filterNot {it.id=="voiced"})
        assertThrows(IllegalArgumentException::class.java) {repository.save(invalid)}
    }
    @Test fun draftAndUndoSurviveViewModelRecreationWithoutChangingSavedConfiguration() {
        val handle=SavedStateHandle()
        val first=PopupMapEditorViewModel(handle)
        repository.save(LegacyPopupFixture.create("は"))
        first.load(repository.load("は"))
        first.mapId="voiced"
        val edited=first.config!!.withMap(first.config!!.map("voiced")!!.copy(tapText="★"))
        first.change(edited)
        val restored=PopupMapEditorViewModel(handle)
        assertEquals("voiced",restored.mapId)
        assertEquals("★",restored.config!!.map("voiced")!!.tapText)
        assertTrue(restored.dirty)
        assertEquals("ば",repository.load("は").map("voiced")!!.tapText)
        assertTrue(restored.undo())
        assertEquals("ば",restored.config!!.map("voiced")!!.tapText)
        assertFalse(restored.dirty)
    }
    @Test fun literalV1MapsStillLoadAndV2ShapesSurviveSaveBackupAndDraftRestore() {
        val legacy="""{"keyId":"は","startMapId":"normal","version":1,"maps":[{"id":"normal","name":"通常","tapText":"は","regions":[{"id":"left","bounds":{"left":-1.5,"top":-0.4,"right":-0.6,"bottom":0.4},"action":{"type":"TEXT","text":"ひ"},"label":""}]}]}"""
        val original=repository.decode(legacy,"は")
        assertEquals(1,original.version)
        assertNull(original.maps.single().regions.single().outline)
        val map=original.maps.single()
        val shape=original.withMap(map.copy(regions=listOf(map.regions.single().copy(outline=PopupOutline(PopupOutline.TRIANGLE)))))
        assertEquals(2,shape.version)
        repository.save(shape)
        val backup=AppPreference.exportAllToJson()
        preferences.edit().clear().commit();AppPreference.importAllFromJson(backup)
        assertEquals(shape,repository.load("は"))
        val handle=SavedStateHandle();val model=PopupMapEditorViewModel(handle)
        model.load(original);model.change(shape)
        val restored=PopupMapEditorViewModel(handle)
        assertEquals(shape,restored.config);assertTrue(restored.undo());assertEquals(original,restored.config)
    }
    @Test fun malformedOrUnsupportedShapeDataFallsBackWithoutOverwritingTheSavedValue() {
        val base=SumirePopupDefaults.create("は")
        val map=base.maps.single()
        val shape=base.withMap(map.copy(regions=map.regions.map {it.copy(outline=PopupOutline())}))
        for(invalid in listOf(shape.copy(version=3),shape.withMap(shape.maps.single().copy(regions=listOf(map.regions.first().copy(outline=PopupOutline(listOf(PopupVertex(0f,0f))))))))) {
            val json=repository.encode(invalid)
            preferences.edit().putString(SumirePopupRepository.PREFIX+"は",json).commit()
            assertTrue(repository.hasInvalidSavedConfig("は"));assertEquals(base,repository.load("は"))
            assertEquals(json,preferences.getString(SumirePopupRepository.PREFIX+"は",null))
        }
    }
}

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
