package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyActionMapperTextTest {

    @Test
    fun fromKeyAction_savesTextAction() {
        assertEquals("Text:q", KeyActionMapper.fromKeyAction(KeyAction.Text("q")))
    }

    @Test
    fun toKeyAction_restoresTextAction() {
        assertEquals(KeyAction.Text("q"), KeyActionMapper.toKeyAction("Text:q"))
    }

    @Test
    fun inputText_roundTripsSeparatelyFromText() {
        val saved = KeyActionMapper.fromKeyAction(KeyAction.InputText("hello"))

        assertEquals("InputText:hello", saved)
        assertEquals(KeyAction.InputText("hello"), KeyActionMapper.toKeyAction(saved))
    }
}
