package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.*
import org.junit.Assert.*
import org.junit.Test

class KeyboardDefaultLayoutsToggleKanaTest {
    @Test fun template_preservesLayoutAndActionsAndOnlyEnablesTextToggles() {
        val original = KeyboardDefaultLayouts.createFlickKanaTemplateLayout(true)
        val toggle = KeyboardDefaultLayouts.createToggleKanaTemplateLayout()
        assertEquals(original.flickKeyMaps, toggle.flickKeyMaps)
        assertEquals(original.rowCount, toggle.rowCount)
        assertEquals(original.columnCount, toggle.columnCount)
        val labels = setOf("あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ", "、。?!")
        assertEquals(labels, toggle.keys.filter { it.textInputBehavior == KeyTextInputBehavior.TOGGLE }.map { it.label }.toSet())
        original.keys.zip(toggle.keys).forEach { (before, after) ->
            assertEquals(before, after.copy(textInputBehavior = before.textInputBehavior))
        }
        assertTrue(original.keys.none { it.textInputBehavior == KeyTextInputBehavior.TOGGLE })
        val sequences = mapOf("あ" to "あいうえお", "か" to "かきくけこ", "さ" to "さしすせそ",
            "た" to "たちつてと", "な" to "なにぬねの", "は" to "はひふへほ", "ま" to "まみむめも",
            "や" to "や(ゆ)よ", "ら" to "らりるれろ", "わ" to "わをんー〜", "、。?!" to "、。？！…")
        sequences.forEach { (label, expected) ->
            val map = toggle.flickKeyMaps.getValue(label).first()
            assertEquals(expected, PETAL_TOGGLE_DIRECTIONS.mapNotNull { (map[it] as? FlickAction.Input)?.char }.joinToString(""))
        }
    }
}
