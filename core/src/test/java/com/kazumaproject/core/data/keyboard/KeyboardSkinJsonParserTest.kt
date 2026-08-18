package com.kazumaproject.core.data.keyboard

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KeyboardSkinJsonParserTest {

    @Test
    fun publicTemplateAndExampleAreAcceptedByTheRuntimeParser() {
        val template = KeyboardSkinJsonParser.parse(publicFile("template.json").readBytes())
        val example = KeyboardSkinJsonParser.parse(publicFile("example.json").readBytes())

        assertTrue(template is KeyboardSkinParseResult.Success)
        assertTrue(example is KeyboardSkinParseResult.Success)
        val definition = (example as KeyboardSkinParseResult.Success).definition
        assertEquals("ai-sakura-cyber", definition.id)
        assertEquals(KeyboardElementRole.entries.toSet(), definition.spec.keyStyles.keys)
        assertEquals(KeyboardSurfaceRole.entries.toSet(), definition.spec.surfaceStyles.keys)
        assertEquals(KeyboardSkinBackgroundAnimation.SHIFT, definition.spec.motion.backgroundAnimation)
        assertTrue(definition.spec.keyStyles.values.any { it.shape == KeyboardSkinShape.HEXAGON })
        assertTrue(definition.spec.keyStyles.values.any { it.fill is KeyboardSkinFill.LinearGradient })
        assertTrue(definition.spec.keyStyles.values.any { it.fill is KeyboardSkinFill.RadialGradient })
        assertTrue(definition.spec.surfaceStyles.values.any { it.decoration?.type == KeyboardSkinDecorationType.WEAVE })
    }

    @Test
    fun acceptsBomAndOneOuterJsonFence() {
        val json = publicFile("template.json").readText()
        val result = KeyboardSkinJsonParser.parse("\uFEFF```json\n$json\n```")

        assertTrue(result is KeyboardSkinParseResult.Success)
    }

    @Test
    fun rejectsExplanationsAndExtraFences() {
        val json = publicFile("template.json").readText()

        assertFailure(KeyboardSkinJsonParser.parse("Here is your skin:\n$json"), "$")
        assertFailure(KeyboardSkinJsonParser.parse("```json\n$json\n```\n```"), "$")
    }

    @Test
    fun rejectsLenientJsonExtensions() {
        val json = publicFile("template.json").readText()
        assertFailure(KeyboardSkinJsonParser.parse("/* comment */$json"), "$")
        assertFailure(
            KeyboardSkinJsonParser.parse(json.replaceFirst("\"format\"", "format")),
            "$",
        )
    }

    @Test
    fun rejectsUnknownFieldsWithAFieldPath() {
        val json = publicFile("template.json").readText()
        val changed = json.replaceFirst(
            "\"format\": \"sumire-keyboard-skin\"",
            "\"unexpected\": true,\n  \"format\": \"sumire-keyboard-skin\"",
        )

        assertFailure(KeyboardSkinJsonParser.parse(changed), "unexpected")
    }

    @Test
    fun rejectsRangeAndGradientOrderViolationsWithoutCorrection() {
        val json = publicFile("example.json").readText()
        val rangeFailure = json.replaceFirst("\"cornerRadiusDp\": 12", "\"cornerRadiusDp\": 33")
        val stopFailure = json.replaceFirst("\"stops\": [0, 0.55, 1]", "\"stops\": [0, 0.8, 0.7]")

        assertFailure(KeyboardSkinJsonParser.parse(rangeFailure), "keys.base.cornerRadiusDp")
        assertFailure(KeyboardSkinJsonParser.parse(stopFailure), "keys.base.fill.stops")
    }

    @Test
    fun acceptsDocumentedNumericBoundaries() {
        val json = publicFile("template.json").readText()
            .replace("\"cornerRadiusDp\": 8", "\"cornerRadiusDp\": 32")
            .replace("\"insetDp\": 1", "\"insetDp\": 8")
            .replace("\"roughnessDp\": 0", "\"roughnessDp\": 3")
            .replace("\"cutSizeDp\": 0", "\"cutSizeDp\": 32")
            .replace("\"widthDp\": 0", "\"widthDp\": 4")
            .replace("\"scale\": 0.97", "\"scale\": 0.90")
            .replace("\"translationXDp\": 0", "\"translationXDp\": 4")
            .replace("\"translationYDp\": 1", "\"translationYDp\": 4")
            .replace("\"durationMs\": 80", "\"durationMs\": 500")
            .replace("\"releaseDurationMs\": 110", "\"releaseDurationMs\": 500")
            .replace(
                "\"background\": {\n      \"type\": \"none\",\n      \"periodSeconds\": 0",
                "\"background\": {\n      \"type\": \"pulse\",\n      \"periodSeconds\": 2",
            )

        assertTrue(KeyboardSkinJsonParser.parse(json) is KeyboardSkinParseResult.Success)
    }

    @Test
    fun contrastIsAWarningAndDoesNotBlockImport() {
        val json = publicFile("template.json").readText()
            .replaceFirst("\"normalKeyText\": \"#FFFFFF\"", "\"normalKeyText\": \"#303744\"")
        val result = KeyboardSkinJsonParser.parse(json)

        assertTrue(result is KeyboardSkinParseResult.Success)
        val warnings = (result as KeyboardSkinParseResult.Success).definition.warnings
        assertTrue(warnings.any { it.path == "palette.normalKeyText" })
    }

    @Test
    fun rejectsVersionSizeAndInvalidUtf8() {
        val json = publicFile("template.json").readText()
            .replaceFirst("\"formatVersion\": 1", "\"formatVersion\": 2")
        assertFailure(KeyboardSkinJsonParser.parse(json), "formatVersion")

        val oversized = ByteArray(KeyboardSkinJsonParser.MAX_UTF8_BYTES + 1)
        assertFailure(KeyboardSkinJsonParser.parse(oversized), "$")
        assertFailure(KeyboardSkinJsonParser.parse(byteArrayOf(0xC3.toByte(), 0x28)), "$")
    }

    @Test
    fun schemaAndNoteDeclareTheSamePublicRuntimeContract() {
        val schema = JsonParser.parseString(publicFile("sumire-keyboard-skin-v1.schema.json").readText()).asJsonObject
        val properties = schema.getAsJsonObject("properties")
        val note = publicFile("note-draft-ja.md").readText()

        assertEquals(KeyboardSkinJsonParser.FORMAT, properties.getAsJsonObject("format").get("const").asString)
        assertEquals(KeyboardSkinJsonParser.FORMAT_VERSION, properties.getAsJsonObject("formatVersion").get("const").asInt)
        assertTrue(note.contains("AIで自分だけのキーボードスキンを作り、Sumireに読み込む方法"))
        assertTrue(note.contains("template.json"))
        assertTrue(note.contains("example.json"))
        assertTrue(note.contains("設定 → テーマ → キーボードスキン"))
    }

    private fun assertFailure(result: KeyboardSkinParseResult, pathPart: String) {
        assertTrue("Expected a parse failure, got $result", result is KeyboardSkinParseResult.Failure)
        val failure = result as KeyboardSkinParseResult.Failure
        assertTrue(failure.errors.any { it.path.contains(pathPart) })
    }

    private fun publicFile(name: String): File {
        val relative = "docs/keyboard-skins/import-v1/$name"
        return listOf(File(relative), File("../$relative"), File("../../$relative"))
            .firstOrNull(File::isFile)
            ?: error("Unable to locate $relative from ${File(".").absolutePath}")
    }
}
