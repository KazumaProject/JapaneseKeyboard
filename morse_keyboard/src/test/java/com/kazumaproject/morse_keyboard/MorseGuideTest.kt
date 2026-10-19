package com.kazumaproject.morse_keyboard

import org.junit.Assert.*
import org.junit.Test

class MorseGuideTest {
    @Test fun everyPresetEntryIsSelectableWithoutCharacterSpecificRules() {
        for (map in MorsePresets.maps(false)) {
            val guide = MorseGuide().apply { setMap(map) }
            assertEquals(map.entries, guide.entries)
            for (entry in map.entries) {
                guide.select(entry.signal)
                assertEquals(entry, guide.selected)
                for (index in entry.signal.indices) {
                    val progress = guide.progress(entry.signal.take(index))
                    assertEquals(index, progress.count)
                    assertEquals(entry.signal[index], progress.next)
                    assertFalse(progress.mismatch)
                }
                assertNull(guide.progress(entry.signal).next)
                guide.decoded(entry.signal)
                assertTrue(guide.progress("").completed)
            }
        }
    }
    @Test fun longCustomSignalUsesAllSymbolsInOrder() {
        val signal = ".-".repeat(32)
        val entry = MorseEntry(signal, MorseOutput.LiteralText("ありがとうございます".repeat(100)))
        val guide = MorseGuide().apply { setMap(MorseMap("long", "Long", listOf(entry))); select(signal) }
        for (count in signal.indices) {
            assertEquals(64, guide.progress(signal.take(count)).total)
            assertEquals(signal[count], guide.progress(signal.take(count)).next)
        }
        assertEquals(64, guide.progress(signal).count)
    }
    @Test fun customTextEmojiAndCommandsAppearAndMapChangesClearSelection() {
        val map = MorseMap("custom", "Custom", listOf(
            MorseEntry(".", MorseOutput.LiteralText("ありがとうございます😊")),
            MorseEntry("-", MorseOutput.Command(MorseCommand.DELETE))))
        val guide = MorseGuide().apply { setMap(map) }
        guide.select("-")
        assertEquals(map.entries.last(), guide.selected)
        assertTrue(guide.progress(".").mismatch)
        assertNull(guide.progress(".").next)
        guide.reset()
        assertEquals(0, guide.progress("").count)
        guide.setMap(MorsePresets.maps(false).first())
        assertNull(guide.selected)
        assertFalse(guide.entries.any { it.output == map.entries.first().output })
    }
}
