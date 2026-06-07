package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SumireJapanese109AContractTest {
    @Test
    fun prefHardwareKeyboard_hasNoInternalLayoutPreference() {
        val text = mainResFile("xml/pref_hardware_keyboard.xml").readText()

        assertFalse(text.contains("physical_keyboard_" + "layout_preference"))
        assertFalse(text.contains("physical_keyboard_" + "layout_entries"))
        assertFalse(text.contains("physical_keyboard_" + "layout_values"))
        assertTrue(text.contains("physical_keyboard_input_mode_preference"))
    }

    @Test
    fun arraysAndStrings_haveNoInternalFallbackLayoutResources() {
        val arrays = mainResFile("values/arrays.xml").readText()
        val strings = mainResFile("values/strings.xml").readText()
        val stringsJa = mainResFile("values-ja/strings.xml").readText()

        assertFalse(arrays.contains("physical_keyboard_" + "layout_entries"))
        assertFalse(arrays.contains("physical_keyboard_" + "layout_values"))
        assertFalse(strings.contains("physical_keyboard_" + "layout_title"))
        assertFalse(strings.contains("physical_keyboard_" + "layout_summary"))
        assertFalse(strings.contains("physical_keyboard_" + "layout_japanese_" + "106_os"))
        assertFalse(stringsJa.contains("physical_keyboard_" + "layout_title"))
        assertFalse(stringsJa.contains("physical_keyboard_" + "layout_summary"))
        assertFalse(stringsJa.contains("physical_keyboard_" + "layout_japanese_" + "106_os"))
        assertTrue(strings.contains("physical_keyboard_layout_japanese_109a_os"))
        assertTrue(stringsJa.contains("physical_keyboard_layout_japanese_109a_os"))
    }

    @Test
    fun japanese109AKcm_definesLettersAsLowercaseAndShiftCapsAsUppercase() {
        val keys = readKcmKeys()

        ('A'..'Z').forEach { letter ->
            val key = keys.getValue(letter.toString())
            assertEquals(letter.lowercase(), key.base)
            assertEquals(letter.toString(), key.shift)
            assertEquals(letter.toString(), key.capslock)
        }
    }

    @Test
    fun japanese109AKcm_definesRequiredNumberAndSymbolMappings() {
        val keys = readKcmKeys()

        assertKey(keys, "1", base = "1", shift = "!")
        assertKey(keys, "2", base = "2", shift = "\"")
        assertKey(keys, "3", base = "3", shift = "#")
        assertKey(keys, "4", base = "4", shift = "$")
        assertKey(keys, "5", base = "5", shift = "%")
        assertKey(keys, "6", base = "6", shift = "&")
        assertKey(keys, "7", base = "7", shift = "'")
        assertKey(keys, "8", base = "8", shift = "(")
        assertKey(keys, "9", base = "9", shift = ")")
        assertKey(keys, "0", base = "0", shift = null)
        assertKey(keys, "MINUS", base = "-", shift = "=")
        assertKey(keys, "EQUALS", base = "^", shift = "~")
        assertKey(keys, "LEFT_BRACKET", base = "@", shift = "`")
        assertKey(keys, "RIGHT_BRACKET", base = "[", shift = "{")
        assertKey(keys, "BACKSLASH", base = "]", shift = "}")
        assertKey(keys, "SEMICOLON", base = ";", shift = "+")
        assertKey(keys, "APOSTROPHE", base = ":", shift = "*")
        assertKey(keys, "COMMA", base = ",", shift = "<")
        assertKey(keys, "PERIOD", base = ".", shift = ">")
        assertKey(keys, "SLASH", base = "/", shift = "?")
        assertKey(keys, "YEN", base = "¥", shift = "|")
        assertKey(keys, "RO", base = "\\", shift = "_")
    }

    @Test
    fun imeService_usesOsUnicodeForNormalSymbolsAndKeepsSpecialKeys() {
        val text = mainFile("java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt").readText()

        assertFalse(text.contains("PhysicalKeyboard" + "SymbolMapper"))
        assertFalse(text.contains("physicalKeyboard" + "Layout"))
        assertTrue(text.contains("getUnicodeChar(event.metaState)"))
        assertTrue(text.contains("KEYCODE_HENKAN"))
        assertTrue(text.contains("KEYCODE_MUHENKAN"))
        assertTrue(text.contains("KEYCODE_EISU"))
        assertTrue(text.contains("KEYCODE_KANA"))
        assertTrue(text.contains("KEYCODE_KATAKANA_HIRAGANA"))
        assertTrue(text.contains("KEYCODE_ZENKAKU_HANKAKU"))
    }

    private fun assertKey(
        keys: Map<String, KcmKey>,
        name: String,
        base: String,
        shift: String?
    ) {
        val key = keys[name]
        assertNotNull("Missing key $name", key)
        assertEquals("base for $name", base, key!!.base)
        assertEquals("shift for $name", shift, key.shift)
    }

    private fun readKcmKeys(): Map<String, KcmKey> {
        val text = mainResFile("raw/keyboard_layout_japanese_109a.kcm").readText()
        return text.lineSequence()
            .mapNotNull { Regex("""^\s*key\s+([A-Z0-9_]+)\s*\{(.*)}\s*$""").find(it) }
            .associate { match ->
                val name = match.groupValues[1]
                val body = match.groupValues[2]
                val capslock = attribute(body, "capslock")
                name to KcmKey(
                    base = attribute(body, "base"),
                    shift = attribute(body, "shift") ?: capslock.takeIf {
                        body.contains(Regex("""\bshift\s*,"""))
                    },
                    capslock = capslock
                )
            }
    }

    private fun attribute(body: String, name: String): String? {
        return Regex("""\b$name\s*:\s*'((?:\\\\|\\'|[^'])*)'""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.replace("\\'", "'")
            ?.replace("\\\\", "\\")
    }

    private fun readXml(path: String) = DocumentBuilderFactory
        .newInstance()
        .newDocumentBuilder()
        .parse(mainResFile(path))

    private fun mainResFile(path: String): File {
        val moduleFile = File("src/main/res/$path")
        return if (moduleFile.exists()) moduleFile else File("app/src/main/res/$path")
    }

    private fun mainFile(path: String): File {
        val moduleFile = File("src/main/$path")
        return if (moduleFile.exists()) moduleFile else File("app/src/main/$path")
    }

    private data class KcmKey(
        val base: String?,
        val shift: String?,
        val capslock: String?
    )
}
