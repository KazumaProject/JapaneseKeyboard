package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
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

        val result = KeyboardLayoutJsonImporter.parse(json)

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

        val result = KeyboardLayoutJsonImporter.parse(json)

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

        val result = KeyboardLayoutJsonImporter.parse(json)

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

        val result = KeyboardLayoutJsonImporter.parse(json)

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
    fun parse_blankString_returnsEmpty() {
        assertEquals(emptyList<ImportableKeyboardLayout>(), KeyboardLayoutJsonImporter.parse(""))
        assertEquals(emptyList<ImportableKeyboardLayout>(), KeyboardLayoutJsonImporter.parse("   "))
    }

    @Test
    fun parse_invalidJson_returnsEmpty() {
        assertEquals(
            emptyList<ImportableKeyboardLayout>(),
            KeyboardLayoutJsonImporter.parse("this is not json")
        )
    }

    @Test
    fun parse_schemaVersion2_withMissingLayouts_returnsEmpty() {
        val json = """{ "schemaVersion": 2 }"""
        assertEquals(emptyList<ImportableKeyboardLayout>(), KeyboardLayoutJsonImporter.parse(json))
    }

    // -----------------------------
    // G. 旧形式 -> 正規化 round-trip 的に Repository 渡せるか
    // -----------------------------
    @Test
    fun parse_legacyArrayWithoutSpacers_producesNonNullSpacersForRepository() {
        val json = "[$minimalLayoutJson]"

        val result = KeyboardLayoutJsonImporter.parse(json)

        // Repository 側で spacers.map { ... } が呼ばれても落ちないことを保証するため、
        // 必ず non-null List<SpacerDefinition> が入っていることを確認する。
        val spacers = result.single().spacers
        // map() を呼べる = non-null
        spacers.map { it.itemIdentifier }
    }
}
