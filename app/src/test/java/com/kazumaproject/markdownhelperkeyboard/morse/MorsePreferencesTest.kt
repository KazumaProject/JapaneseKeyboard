package com.kazumaproject.markdownhelperkeyboard.morse

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.morse_keyboard.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MorsePreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val store = MorsePreferences(context)
    @Before fun reset() { preferences.edit().clear().commit() }

    @Test fun beginnerGuideDefaultsOffAndPersistsWithOtherMorseSettings() {
        assertFalse(MorsePreferences(context).settings().beginnerGuide)
        preferences.edit().putBoolean("morse_beginner_guide", true).commit()
        assertTrue(MorsePreferences(context).settings().beginnerGuide)
        preferences.edit().putBoolean("morse_beginner_guide", false).commit()
        assertFalse(MorsePreferences(context).settings().beginnerGuide)
    }
    @Test fun defaultsSelectWabunAndRestoreOrderAndEnabledMapsByStableId() {
        assertEquals(MorsePresets.WABUN, store.selectedMapId)
        assertEquals(listOf(MorsePresets.WABUN, MorsePresets.ROMAJI, MorsePresets.ENGLISH), store.enabledMaps().map { it.id })
        store.saveOrder(listOf(MorsePresets.ENGLISH, MorsePresets.WABUN, MorsePresets.ROMAJI), setOf(MorsePresets.ENGLISH, MorsePresets.WABUN))
        store.selectedMapId = MorsePresets.ENGLISH
        val reopened = MorsePreferences(context)
        assertEquals(listOf(MorsePresets.ENGLISH, MorsePresets.WABUN), reopened.enabledMaps().map { it.id })
        assertEquals(MorsePresets.ENGLISH, reopened.selectedMapId)
    }
    @Test fun cannotDisableEveryMap() {
        val before = preferences.all.toMap()
        assertThrows(IllegalArgumentException::class.java) { store.saveOrder(emptyList(), emptySet()) }
        assertEquals(before, preferences.all)
    }
    @Test fun codecPreservesTypesUnicodePhrasesAndPrefixAssignments() {
        val map = MorseMap("custom", "チャット", listOf(
            MorseEntry(".", MorseOutput.LiteralText("削除")),
            MorseEntry("..", MorseOutput.Command(MorseCommand.DELETE)),
            MorseEntry(".-", MorseOutput.Reading("ありがとう")),
            MorseEntry("-", MorseOutput.Romaji("ka")),
            MorseEntry("--", MorseOutput.LiteralText("😊\nthanks"))
        ))
        assertEquals(listOf(map), MorseMapCodec.decode(MorseMapCodec.encode(listOf(map))))
    }
    @Test fun customMapEditRenameAndDeleteKeepOtherMaps() {
        val map = MorseMap("mine", "My map", listOf(MorseEntry(".", MorseOutput.Reading("の"))))
        store.saveMap(map)
        store.saveMap(map.copy(name = "Renamed"))
        assertEquals("mine", store.selectedMapId)
        assertEquals("Renamed", MorsePreferences(context).allMaps().last().name)
        store.deleteMap("mine")
        assertEquals(3, store.allMaps().size)
        assertEquals(MorsePresets.WABUN, store.selectedMapId)
    }
    @Test fun invalidImportsAreAtomicIncludingDuplicateSignalsAndUnknownCommands() {
        store.saveMap(MorseMap("mine", "Keep", emptyList()))
        val before = preferences.all.toMap()
        val invalid = listOf(
            "{}", "{\"version\":2,\"maps\":[]}",
            """{"version":1.5,"maps":[{"id":"x","name":"X","entries":[]}]}""",
            """{"version":"1","maps":[{"id":"x","name":"X","entries":[]}]}""",
            """{"version":1,"maps":[{"id":"x","name":"X","entries":[{"signal":".","type":"text","value":true}]}]}""",
            """{"version":1,"maps":[{"id":"x","name":"X","entries":[{"signal":".","type":"text","value":"a"},{"signal":".","type":"text","value":"b"}]}]}""",
            """{"version":1,"maps":[{"id":"x","name":"X","entries":[{"signal":"-","type":"command","value":"UNKNOWN"}]}]}""",
            """{"version":1,"maps":[{"id":"x","name":"X","entries":[{"signal":"x","type":"text","value":"a"}]}]}"""
        )
        invalid.forEach { json ->
            assertTrue(runCatching { store.importMaps(json) }.isFailure)
            assertEquals(before, preferences.all)
        }
    }
    @Test fun importingPresetsCreatesEditableCopiesWithoutOverwritingBuiltins() {
        store.importMaps(MorseMapCodec.encode(MorsePresets.maps()))
        val maps = store.allMaps()
        assertEquals(6, maps.size)
        assertTrue(maps.take(3).all { it.builtIn })
        assertTrue(maps.drop(3).none { it.builtIn || it.id.startsWith("builtin_") })
        assertEquals(6, maps.map { it.id }.distinct().size)
    }
    @Test fun deletingOnlyEnabledCustomMapRestoresAnAvailableMap() {
        store.saveMap(MorseMap("mine", "Only", emptyList()))
        store.saveOrder(store.allMaps().map { it.id }, setOf("mine"))
        store.deleteMap("mine")
        assertEquals(1, store.enabledMaps().size)
        assertEquals(MorsePresets.WABUN, store.selectedMapId)
    }
    @Test fun settingsReadSameKeysAsPreferenceScreenAndClampRestoredValues() {
        preferences.edit().putString("morse_style", "STRAIGHT").putInt("morse_wpm", 0)
            .putBoolean("morse_auto_boundary", false).putInt("morse_volume", 200).commit()
        val settings = store.settings()
        assertEquals(MorseInputStyle.STRAIGHT, settings.style)
        assertEquals(5, settings.wpm); assertEquals(100, settings.volumePercent)
        assertFalse(settings.autoBoundary)
    }
}
