package com.kazumaproject.markdownhelperkeyboard.gesture

import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.gesture_keyboard.*
import org.junit.Assert.*
import org.junit.Test

class GestureCodecMigrationTest {
    @Test fun versionOneKeepsLeftRightMapsCapsLabelsAndCommandMeaning() {
        val session = GestureCodec.decodeSession("""{"version":1,"left":"personal","right":"builtin_latin","leftUppercase":false,"rightUppercase":true}""")
        assertEquals("personal", session.pads[0].setId)
        assertTrue(session.pads[1].uppercase)
        assertEquals(GestureSessionState.defaults(2).map { it.bounds }, session.pads.map { it.bounds })
        val layout = GestureCodec.decodeLayout("""{"version":1,"left":[{"id":"a","action":"SMALL_KANA","label":"small"}],"right":[{"id":"s","action":"SPACE"},{"id":"c","action":"COMMIT"}]}""")
        assertEquals(KeyAction.InputText("ひらがな小文字"), layout.leftKeys.single().action)
        assertEquals("small", layout.leftKeys.single().labelOverride)
        assertEquals(listOf(KeyAction.CommitAndInsertSpace, KeyAction.Confirm), layout.rightKeys.map { it.action })
        assertEquals(layout, GestureCodec.decodeLayout(GestureCodec.encodeLayout(layout)))
    }
    @Test fun versionTwoRoundTripsGeometryModesAndParameterizedActions() {
        val state = GestureSessionState().withCount(4).copy(activePadId = "pad_3")
            .update("pad_3") { it.copy(bounds = GestureBounds(.05f, .6f, .3f, .3f), shift = true, direct = true, romajiOverride = true) }
        assertEquals(state, GestureCodec.decodeSession(GestureCodec.encodeSession(state)))
        val actions = listOf(KeyAction.Paste, KeyAction.ForceFullWidthSpace, KeyAction.MoveToCustomKeyboard("stable-123"))
        val layout = GestureControlLayout(actions.mapIndexed { i, action -> GestureControlKey("key_$i", action) }, emptyList())
        assertEquals(layout, GestureCodec.decodeLayout(GestureCodec.encodeLayout(layout)))
    }
    @Test fun oldSetsDefaultToLiteralAndNewSetsKeepTheirFlagAndCommandMappings() {
        val old = GestureCodec.decodeSets("""{"version":1,"sets":[{"id":"abc","name":"ABC","entries":[{"id":"a","type":"command","value":"DELETE","samples":[[[0,0],[10,10]]]}]}]}""").single()
        assertFalse(old.isRomaji)
        assertEquals(GestureOutput.Command(KeyAction.Delete), old.entries.single().output)
        val next = old.copy(isRomaji = true)
        assertEquals(listOf(next), GestureCodec.decodeSets(GestureCodec.encodeSets(listOf(next))))
    }
    @Test fun unsupportedActionsVersionsAndOverlappingAreasAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { GestureCodec.decodeLayout("""{"version":2,"left":[{"id":"a","action":"missing"}],"right":[]}""") }
        assertThrows(IllegalArgumentException::class.java) { GestureCodec.decodeSession("""{"version":3}""") }
        val state = GestureSessionState()
        val valid = GestureCodec.encodeSession(state)
        assertThrows(IllegalArgumentException::class.java) { GestureCodec.decodeSession(valid.replace("\"x\":0.5", "\"x\":0.0")) }
    }
    @Test fun everyLegacyCommandHasAStableSharedActionEncoding() {
        for (name in listOf("DELETE", "SPACE", "CONVERT", "COMMIT", "ENTER", "CURSOR_LEFT", "CURSOR_RIGHT", "DAKUTEN", "HANDAKUTEN", "SMALL_KANA", "NEXT_KEYBOARD")) {
            val key = GestureCodec.decodeLayout("""{"version":1,"left":[{"id":"x","action":"$name"}],"right":[]}""").leftKeys.single()
            assertEquals(key.action, KeyActionMapper.toKeyAction(KeyActionMapper.fromKeyAction(key.action)))
        }
    }
}
