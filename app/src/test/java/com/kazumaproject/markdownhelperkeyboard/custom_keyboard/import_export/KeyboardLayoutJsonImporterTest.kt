package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [KeyboardLayoutJsonImporter] / [KeyboardLayoutJsonExporter] の互換性テスト。
 *
 * 主な観点:
 * - 旧 root array 形式 (spacers なし / null / 完全形)
 * - flick 系 List 欠損
 * - 新 schemaVersion = 2 object 形式
 * - export round-trip
 */
class KeyboardLayoutJsonImporterTest {

    /**
     * 共通: テストで使う最小レイアウト JSON。
     * 古いバージョン由来の JSON にあわせて spacers / 各 flick 系 list は意図的に省略する。
     */
    private val minimalLayoutJson = """
        {
            "layout": {
                "layoutId": 0,
                "name": "TestKeyboard",
                "columnCount": 5,
                "rowCount": 4,
                "isRomaji": false,
                "isDirectMode": false,
                "createdAt": 0,
                "sortOrder": 0,
                "stableId": "stable-1"
            },
            "keysWithFlicks": []
        }
    """.trimIndent()

    // -----------------------------
    // A. 旧 root array で spacers が無くても import できる
    // -----------------------------
    @Test
    fun parse_legacyRootArray_withoutSpacers_doesNotCrash_andSpacersIsEmpty() {
        val json = "[$minimalLayoutJson]"

        val result = parseSuccessLayouts(json)

        assertEquals(1, result.size)
        val layout = result.single()
        assertEquals("TestKeyboard", layout.layout.name)
        assertEquals(emptyList<Any>(), layout.spacers)
        assertEquals(emptyList<Any>(), layout.keysWithFlicks)
    }

    // -----------------------------
    // B. spacers: null でも import できる
    // -----------------------------
    @Test
    fun parse_legacyRootArray_withNullSpacers_isNormalizedToEmpty() {
        val json = """
            [
              {
                "layout": {
                    "layoutId": 0,
                    "name": "TestKeyboard",
                    "columnCount": 5,
                    "rowCount": 4,
                    "isRomaji": false,
                    "isDirectMode": false,
                    "createdAt": 0,
                    "sortOrder": 0,
                    "stableId": "stable-1"
                },
                "keysWithFlicks": [],
                "spacers": null
              }
            ]
        """.trimIndent()

        val result = parseSuccessLayouts(json)

        assertEquals(1, result.size)
        assertEquals(emptyList<Any>(), result.single().spacers)
    }

    // -----------------------------
    // C. flick 系 List が無くても / null でも import できる
    // -----------------------------
    @Test
    fun parse_keysWithFlicks_missingFlickFields_isNormalizedToEmpty() {
        val json = """
            [
              {
                "layout": {
                    "layoutId": 0,
                    "name": "TestKeyboard",
                    "columnCount": 5,
                    "rowCount": 4,
                    "isRomaji": false,
                    "isDirectMode": false,
                    "createdAt": 0,
                    "sortOrder": 0,
                    "stableId": "stable-1"
                },
                "keysWithFlicks": [
                  {
                    "key": {
                      "keyId": 0,
                      "ownerLayoutId": 0,
                      "label": "あ",
                      "row": 0,
                      "column": 0,
                      "rowSpan": 1,
                      "colSpan": 1,
                      "keyType": "PETAL_FLICK",
                      "isSpecialKey": false,
                      "drawableResId": null,
                      "keyIdentifier": "key-1",
                      "action": null
                    }
                  },
                  {
                    "key": {
                      "keyId": 0,
                      "ownerLayoutId": 0,
                      "label": "い",
                      "row": 0,
                      "column": 1,
                      "rowSpan": 1,
                      "colSpan": 1,
                      "keyType": "PETAL_FLICK",
                      "isSpecialKey": false,
                      "drawableResId": null,
                      "keyIdentifier": "key-2",
                      "action": null
                    },
                    "flicks": null,
                    "circularFlicks": null,
                    "twoStepFlicks": null,
                    "longPressFlicks": null,
                    "twoStepLongPressFlicks": null
                  }
                ]
              }
            ]
        """.trimIndent()

        val result = parseSuccessLayouts(json)

        assertEquals(1, result.size)
        val layout = result.single()
        assertEquals(2, layout.keysWithFlicks.size)
        layout.keysWithFlicks.forEach { kw ->
            assertEquals(emptyList<Any>(), kw.flicks)
            assertEquals(emptyList<Any>(), kw.circularFlicks)
            assertEquals(emptyList<Any>(), kw.twoStepFlicks)
            assertEquals(emptyList<Any>(), kw.longPressFlicks)
            assertEquals(emptyList<Any>(), kw.twoStepLongPressFlicks)
        }
    }

    // -----------------------------
    // D. 新 schemaVersion = 2 object 形式を import できる
    // -----------------------------
    @Test
    fun parse_schemaVersion2_objectFormat_isAccepted() {
        val json = """
            {
              "schemaVersion": 2,
              "layouts": [
                $minimalLayoutJson
              ]
            }
        """.trimIndent()

        val result = parseSuccessLayouts(json)

        assertEquals(1, result.size)
        assertEquals("TestKeyboard", result.single().layout.name)
    }

    // -----------------------------
    // E. export は schemaVersion = 2 の object 形式になる
    // -----------------------------
    @Test
    fun exporter_emitsSchemaVersionedObjectRoot() {
        // FullKeyboardLayout を経由しない簡易テスト:
        // export のフォーマット保証だけ確認するため、
        // exporter に空 list を渡して root 形 / schemaVersion を検証する。
        val json = KeyboardLayoutJsonExporter.toJson(emptyList())

        val root = JsonParser.parseString(json)
        assertTrue("root must be object", root.isJsonObject)

        val obj = root.asJsonObject
        assertEquals(2, obj["schemaVersion"].asInt)
        assertNotNull(obj["layouts"])
        assertTrue("layouts must be array", obj["layouts"].isJsonArray)
        assertEquals(0, obj["layouts"].asJsonArray.size())
    }

    // -----------------------------
    // F. blank / 不正 JSON でも crash しない
    // -----------------------------
    @Test
    fun parse_blankString_returnsEmptyInputFailure() {
        assertTrue(KeyboardLayoutJsonImporter.parse("") is KeyboardLayoutImportResult.Failure)
        assertTrue(KeyboardLayoutJsonImporter.parse("   ") is KeyboardLayoutImportResult.Failure)
    }

    @Test
    fun parse_invalidJson_returnsInvalidJsonFailure() {
        val result = KeyboardLayoutJsonImporter.parse("{")
        assertTrue(result is KeyboardLayoutImportResult.Failure)
        assertTrue((result as KeyboardLayoutImportResult.Failure).error is KeyboardLayoutImportError.InvalidJson)
    }

    @Test
    fun parse_schemaVersion2_withMissingLayouts_returnsNoImportableLayouts() {
        val json = """{ "schemaVersion": 2 }"""
        val result = KeyboardLayoutJsonImporter.parse(json)
        assertTrue(result is KeyboardLayoutImportResult.Failure)
        assertEquals(
            KeyboardLayoutImportError.NoImportableLayouts,
            (result as KeyboardLayoutImportResult.Failure).error
        )
    }

    // -----------------------------
    // G. 旧形式 -> 正規化 round-trip 的に Repository 渡せるか
    // -----------------------------
    @Test
    fun parse_legacyArrayWithoutSpacers_producesNonNullSpacersForRepository() {
        val json = "[$minimalLayoutJson]"

        val result = parseSuccessLayouts(json)

        // Repository 側で spacers.map { ... } が呼ばれても落ちないことを保証するため、
        // 必ず non-null List<SpacerDefinition> が入っていることを確認する。
        val spacers = result.single().spacers
        // map() を呼べる = non-null
        spacers.map { it.itemIdentifier }
    }

    @Test
    fun backupImporter_sharedPreferencesXmlWrapper_extractsEscapedJsonPayload() {
        val escapedJson = "[$minimalLayoutJson]"
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "&apos;")
        val xml = """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <map>
                <string name="keyboard_layouts_backup">$escapedJson</string>
            </map>
        """.trimIndent()

        val result = KeyboardLayoutBackupImporter.importText(xml)

        val layouts = result.layoutsOrThrow()
        assertEquals(1, layouts.size)
        assertEquals("TestKeyboard", layouts.single().layout.name)
    }

    @Test
    fun backupImporter_xmlWithMultipleStrings_prefersKnownKey() {
        val unrelatedLayout = minimalLayoutJson.replace("TestKeyboard", "WrongKeyboard")
        val xml = """
            <map>
                <string name="other">[$unrelatedLayout]</string>
                <string name="keyboard_layouts_backup">[$minimalLayoutJson]</string>
            </map>
        """.trimIndent()

        val result = KeyboardLayoutBackupImporter.importText(xml)

        assertEquals("TestKeyboard", result.layoutsOrThrow().single().layout.name)
    }

    @Test
    fun backupImporter_xmlWithoutKnownKey_usesLayoutLikeJsonString() {
        val xml = """
            <map>
                <string name="some_legacy_name">[$minimalLayoutJson]</string>
            </map>
        """.trimIndent()

        val result = KeyboardLayoutBackupImporter.importText(xml)

        assertEquals("TestKeyboard", result.layoutsOrThrow().single().layout.name)
    }

    @Test
    fun backupImporter_xmlWithoutPayload_returnsNoLayoutPayloadFound() {
        val result = KeyboardLayoutBackupImporter.importText(
            """<map><string name="not_keyboard">{"hello":"world"}</string></map>"""
        )

        assertFailure(result, KeyboardLayoutImportError.NoLayoutPayloadFound)
    }

    @Test
    fun backupImporter_invalidXml_returnsInvalidXml() {
        val result = KeyboardLayoutBackupImporter.importText("""<map><string name="keyboard_layouts_backup">[]""")

        assertTrue(result is KeyboardLayoutImportResult.Failure)
        assertTrue((result as KeyboardLayoutImportResult.Failure).error is KeyboardLayoutImportError.InvalidXml)
    }

    @Test
    fun backupImporter_unsupportedPlainText_returnsUnsupportedFormat() {
        assertFailure(
            KeyboardLayoutBackupImporter.importText("this is not json"),
            KeyboardLayoutImportError.UnsupportedFormat
        )
    }

    @Test
    fun backupImporter_emptyInput_returnsEmptyInput() {
        assertFailure(
            KeyboardLayoutBackupImporter.importText("\uFEFF\u0000   "),
            KeyboardLayoutImportError.EmptyInput
        )
    }

    @Test
    fun normalize_missingSpacersAndKeyIdentifier_generatesWarningsAndIds() {
        val json = """
            [
              {
                "layout": {
                    "layoutId": 14,
                    "name": "TestKeyboard",
                    "columnCount": 0,
                    "rowCount": 0,
                    "isRomaji": false,
                    "createdAt": 0
                },
                "keysWithFlicks": [
                  {
                    "key": {
                      "keyId": 90,
                      "ownerLayoutId": 14,
                      "label": "A",
                      "row": 0,
                      "column": 0,
                      "rowSpan": 1,
                      "colSpan": 1,
                      "keyType": "NORMAL",
                      "isSpecialKey": false,
                      "drawableResId": null,
                      "action": null
                    },
                    "flicks": []
                  }
                ]
              }
            ]
        """.trimIndent()

        val result = KeyboardLayoutBackupImporter.importText(json)
        val success = result as KeyboardLayoutImportResult.Success

        val layout = success.layouts.single()
        assertEquals(1, layout.layout.rowCount)
        assertEquals(1, layout.layout.columnCount)
        assertEquals(0L, layout.layout.layoutId)
        assertEquals(0L, layout.keysWithFlicks.single().key.keyId)
        assertEquals(0L, layout.keysWithFlicks.single().key.ownerLayoutId)
        assertFalse(layout.keysWithFlicks.single().key.keyIdentifier.isBlank())
        assertTrue(success.warnings.any { it is KeyboardLayoutImportWarning.MissingSpacerListTreatedAsEmpty })
        assertTrue(success.warnings.any { it is KeyboardLayoutImportWarning.MissingKeyIdentifierGenerated })
        assertTrue(success.warnings.any { it is KeyboardLayoutImportWarning.MissingLayoutIdentifierGenerated })
        assertTrue(success.warnings.any { it is KeyboardLayoutImportWarning.InvalidRowColumnCorrected })
    }

    @Test
    fun normalize_invalidSpan_isCorrectedWithWarning() {
        val json = """
            [
              {
                "layout": {
                    "layoutId": 14,
                    "name": "TestKeyboard",
                    "columnCount": 1,
                    "rowCount": 1,
                    "isRomaji": false,
                    "stableId": "stable-1"
                },
                "keysWithFlicks": [
                  {
                    "key": {
                      "keyId": 90,
                      "ownerLayoutId": 14,
                      "label": "A",
                      "row": 0,
                      "column": 0,
                      "rowSpan": 0,
                      "colSpan": -2,
                      "keyType": "NORMAL",
                      "isSpecialKey": false,
                      "drawableResId": null,
                      "keyIdentifier": "key-1",
                      "action": null
                    },
                    "flicks": []
                  }
                ],
                "spacers": []
              }
            ]
        """.trimIndent()

        val success = KeyboardLayoutBackupImporter.importText(json) as KeyboardLayoutImportResult.Success

        val key = success.layouts.single().keysWithFlicks.single().key
        assertEquals(1, key.rowSpan)
        assertEquals(1, key.colSpan)
        assertTrue(success.warnings.any { it is KeyboardLayoutImportWarning.InvalidSpanCorrected })
    }

    @Test
    fun parse_legacyBackup_preservesActionsAndDirectionsAndEmptyFlicks() {
        val json = """
            [
              {
                "layout": {
                    "layoutId": 14,
                    "name": "ActionKeyboard",
                    "columnCount": 4,
                    "rowCount": 2,
                    "isRomaji": false,
                    "stableId": "stable-1"
                },
                "keysWithFlicks": [
                  {
                    "key": {
                      "keyId": 1,
                      "ownerLayoutId": 14,
                      "label": "",
                      "row": 0,
                      "column": 0,
                      "rowSpan": 1,
                      "colSpan": 1,
                      "keyType": "PETAL_FLICK",
                      "isSpecialKey": true,
                      "drawableResId": null,
                      "keyIdentifier": "left",
                      "action": "MoveCursorLeft"
                    },
                    "flicks": [
                      { "ownerKeyId": 1, "stateIndex": 0, "flickDirection": "TAP", "actionType": "DELETE", "actionValue": null },
                      { "ownerKeyId": 1, "stateIndex": 0, "flickDirection": "UP", "actionType": "MOVE_CURSOR_RIGHT", "actionValue": null },
                      { "ownerKeyId": 1, "stateIndex": 0, "flickDirection": "UP_LEFT_FAR", "actionType": "SWITCH_TO_NEXT_IME", "actionValue": null }
                    ]
                  },
                  {
                    "key": {
                      "keyId": 2,
                      "ownerLayoutId": 14,
                      "label": "",
                      "row": 0,
                      "column": 1,
                      "rowSpan": 1,
                      "colSpan": 1,
                      "keyType": "NORMAL",
                      "isSpecialKey": true,
                      "drawableResId": null,
                      "keyIdentifier": "right",
                      "action": "MoveCursorRight"
                    },
                    "flicks": []
                  }
                ]
              }
            ]
        """.trimIndent()

        val layout = KeyboardLayoutBackupImporter.importText(json).layoutsOrThrow().single()

        assertEquals(2, layout.keysWithFlicks.size)
        assertEquals("MoveCursorLeft", layout.keysWithFlicks[0].key.action)
        assertEquals("MoveCursorRight", layout.keysWithFlicks[1].key.action)
        assertEquals(emptyList<Any>(), layout.keysWithFlicks[1].flicks)
        assertEquals("TAP", layout.keysWithFlicks[0].flicks[0].flickDirection.name)
        assertEquals("UP", layout.keysWithFlicks[0].flicks[1].flickDirection.name)
        assertEquals("UP_LEFT_FAR", layout.keysWithFlicks[0].flicks[2].flickDirection.name)
        assertEquals("DELETE", layout.keysWithFlicks[0].flicks[0].actionType)
        assertEquals("MOVE_CURSOR_RIGHT", layout.keysWithFlicks[0].flicks[1].actionType)
        assertEquals("SWITCH_TO_NEXT_IME", layout.keysWithFlicks[0].flicks[2].actionType)
    }

    private fun parseSuccessLayouts(json: String): List<ImportableKeyboardLayout> {
        return KeyboardLayoutJsonImporter.parse(json).layoutsOrThrow()
    }

    private fun KeyboardLayoutImportResult.layoutsOrThrow(): List<ImportableKeyboardLayout> {
        return when (this) {
            is KeyboardLayoutImportResult.Success -> layouts
            is KeyboardLayoutImportResult.PartialSuccess -> layouts
            is KeyboardLayoutImportResult.Failure -> error("Expected import success, got $error")
        }
    }

    private fun assertFailure(
        result: KeyboardLayoutImportResult,
        expectedError: KeyboardLayoutImportError
    ) {
        assertTrue(result is KeyboardLayoutImportResult.Failure)
        assertEquals(expectedError, (result as KeyboardLayoutImportResult.Failure).error)
    }
}
