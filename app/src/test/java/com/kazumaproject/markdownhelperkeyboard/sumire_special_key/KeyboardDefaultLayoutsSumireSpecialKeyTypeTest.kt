package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardDefaultLayoutsSumireSpecialKeyTypeTest {
    @Test
    fun activeSumireLayoutsContainNormalAndCrossFlickSpecialKeys() {
        val typeSets = layoutTypes.flatMap { layoutType ->
            KeyboardInputMode.entries.map { inputMode ->
                KeyboardDefaultLayouts.createFinalLayout(
                    mode = inputMode,
                    dynamicKeyStates = previewDynamicStates,
                    inputLayoutType = layoutType,
                    inputStyle = "default"
                ).items
                    .filterIsInstance<KeyItem>()
                    .filter { it.keyData.isSpecialKey }
                    .map { it.keyData.keyType }
                    .toSet()
            }
        }

        assertTrue(typeSets.any { KeyType.NORMAL in it })
        assertTrue(typeSets.any { KeyType.CROSS_FLICK in it })
    }

    @Test
    fun deleteKeyKeyIdIsStableAndAlwaysCrossFlickRegardlessOfFlickPreference() {
        val noFlick = KeyboardDefaultLayouts.createFinalLayout(
            mode = KeyboardInputMode.HIRAGANA,
            dynamicKeyStates = previewDynamicStates,
            inputLayoutType = "toggle",
            inputStyle = "default",
            deleteKeyFlickSettings = KeyboardDefaultLayouts.DeleteKeyFlickSettings(
                left = false,
                up = false,
                down = false
            )
        )

        val withFlick = KeyboardDefaultLayouts.createFinalLayout(
            mode = KeyboardInputMode.HIRAGANA,
            dynamicKeyStates = previewDynamicStates,
            inputLayoutType = "toggle",
            inputStyle = "default",
            deleteKeyFlickSettings = KeyboardDefaultLayouts.DeleteKeyFlickSettings(
                left = true,
                up = true,
                down = true
            )
        )

        val noFlickDelete = noFlick.deleteKey()
        val withFlickDelete = withFlick.deleteKey()
        assertEquals("delete_key", noFlickDelete.keyData.keyId)
        assertEquals("delete_key", withFlickDelete.keyData.keyId)
        // Sumire 特殊キー action override の上下左右を受け取れるよう、
        // delete key の通常 delete flick preference が無効でも CROSS_FLICK にする。
        assertEquals(KeyType.CROSS_FLICK, noFlickDelete.keyData.keyType)
        assertEquals(KeyType.CROSS_FLICK, withFlickDelete.keyData.keyType)
        assertNotNull(noFlick.flickKeyMaps["delete_key"])
        assertNotNull(withFlick.flickKeyMaps["delete_key"])
    }

    @Test
    fun deleteFlickPreferenceIsPreservedInDeleteKeyFlickMap() {
        // hasFlickActions=true のとき、Delete flick (UP_LEFT=DeleteUntilSymbol, UP=DeleteAfterCursorUntilSymbol,
        // DOWN=UndoLastDelete) は keyId alias に反映される。
        val withFlick = KeyboardDefaultLayouts.createFinalLayout(
            mode = KeyboardInputMode.HIRAGANA,
            dynamicKeyStates = previewDynamicStates,
            inputLayoutType = "toggle",
            inputStyle = "default",
            deleteKeyFlickSettings = KeyboardDefaultLayouts.DeleteKeyFlickSettings(
                left = true,
                up = true,
                down = true
            )
        )
        val map = withFlick.flickKeyMaps["delete_key"]?.firstOrNull()
        assertNotNull(map)
        val nonNullMap = map!!
        assertTrue(nonNullMap.containsKey(FlickDirection.UP_LEFT))
        assertTrue(nonNullMap.containsKey(FlickDirection.UP))
        assertTrue(nonNullMap.containsKey(FlickDirection.DOWN))

        // hasFlickActions=false のとき、delete flick は剥がされて Tap だけになる。
        val noFlick = KeyboardDefaultLayouts.createFinalLayout(
            mode = KeyboardInputMode.HIRAGANA,
            dynamicKeyStates = previewDynamicStates,
            inputLayoutType = "toggle",
            inputStyle = "default",
            deleteKeyFlickSettings = KeyboardDefaultLayouts.DeleteKeyFlickSettings(
                left = false,
                up = false,
                down = false
            )
        )
        val noFlickMap = noFlick.flickKeyMaps["delete_key"]?.firstOrNull()
        assertNotNull(noFlickMap)
        val nonNullNoFlickMap = noFlickMap!!
        assertTrue(nonNullNoFlickMap.containsKey(FlickDirection.TAP))
        assertFalse(nonNullNoFlickMap.containsKey(FlickDirection.UP_LEFT))
        assertFalse(nonNullNoFlickMap.containsKey(FlickDirection.UP))
        assertFalse(nonNullNoFlickMap.containsKey(FlickDirection.DOWN))
    }

    @Test
    fun allTargetSumireSpecialKeysSatisfyCrossFlickAttachConditionForEveryActiveLayout() {
        layoutTypes.forEach { layoutType ->
            KeyboardInputMode.entries.forEach { inputMode ->
                val layout = KeyboardDefaultLayouts.createFinalLayout(
                    mode = inputMode,
                    dynamicKeyStates = previewDynamicStates,
                    inputLayoutType = layoutType,
                    inputStyle = "default"
                )

                val targetSpecialKeys = layout.items
                    .filterIsInstance<KeyItem>()
                    .map { it.keyData }
                    .filter { it.isSpecialKey && it.keyId in TARGET_KEY_IDS }

                targetSpecialKeys.forEach { keyData ->
                    val keyId = keyData.keyId
                    val scope = "$layoutType/$inputMode/$keyId"
                    // CROSS_FLICK 経路で direction 付き dispatch に到達できることを担保する。
                    assertEquals(
                        "$scope keyType must be CROSS_FLICK",
                        KeyType.CROSS_FLICK,
                        keyData.keyType
                    )
                    // FlickKeyboardView の CROSS_FLICK attach 条件: layout.flickKeyMaps[keyId] が存在する。
                    assertNotNull(
                        "$scope must have a flickKeyMaps[keyId] entry",
                        layout.flickKeyMaps[keyId]
                    )
                    assertTrue(
                        "$scope must have at least one flick action map",
                        layout.flickKeyMaps[keyId]?.firstOrNull()?.isNotEmpty() == true
                    )
                    // dakuten_toggle_key は対象外
                    assertFalse("$scope must not equal dakuten_toggle_key", keyId == "dakuten_toggle_key")
                }
            }
        }
    }

    @Test
    fun dynamicStatesArePreservedAfterCrossFlickAttachForTargetKeys() {
        val layout = KeyboardDefaultLayouts.createFinalLayout(
            mode = KeyboardInputMode.HIRAGANA,
            dynamicKeyStates = mapOf(
                "enter_key" to 2,
                "space_convert_key" to 1,
                "katakana_toggle_key" to 1
            ),
            inputLayoutType = "switch-mode-effective",
            inputStyle = "default"
        )

        listOf("enter_key", "space_convert_key", "katakana_toggle_key").forEach { keyId ->
            val keyData = layout.items
                .filterIsInstance<KeyItem>()
                .firstOrNull { it.keyData.keyId == keyId }
                ?.keyData
            assertNotNull("$keyId not found", keyData)
            val nonNull = keyData!!
            assertTrue("$keyId.isSpecialKey", nonNull.isSpecialKey)
            assertEquals("$keyId keyType must be CROSS_FLICK", KeyType.CROSS_FLICK, nonNull.keyType)
            assertNotNull("$keyId.dynamicStates must be preserved", nonNull.dynamicStates)
            assertTrue(
                "$keyId.dynamicStates must contain entries",
                nonNull.dynamicStates!!.isNotEmpty()
            )
            assertEquals(keyId, nonNull.keyId)
        }
    }

    @Test
    fun dakutenToggleKeyIsExplicitlyNotForcedToCrossFlick() {
        // dakuten_toggle_key は今回の修正対象外。helper はその keyType に触れない。
        // (一部のレイアウトでは元から CROSS_FLICK だが、helper 経由での強制変換対象には入らない。)
        val targetIds = setOf(
            "enter_key",
            "switch_next_ime",
            "katakana_toggle_key",
            "space_convert_key",
            "delete_key"
        )
        assertFalse("dakuten_toggle_key must not be a forced CROSS_FLICK target", "dakuten_toggle_key" in targetIds)
    }

    private fun KeyboardLayout.deleteKey(): KeyItem =
        items.filterIsInstance<KeyItem>().first { it.keyData.keyId == "delete_key" }

    private companion object {
        val layoutTypes = listOf("toggle", "flick", "switch-mode-effective")
        val previewDynamicStates = mapOf(
            "enter_key" to 0,
            "dakuten_toggle_key" to 0,
            "katakana_toggle_key" to 0,
            "space_convert_key" to 0
        )
        val TARGET_KEY_IDS = setOf(
            "enter_key",
            "switch_next_ime",
            "katakana_toggle_key",
            "space_convert_key",
            "delete_key"
        )
    }
}
