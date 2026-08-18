package com.kazumaproject.core.data.keyboard

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.io.StringReader
import kotlin.math.abs
import kotlin.math.pow

/** Strict parser for Sumire's offline, declarative keyboard skin format v1. */
object KeyboardSkinJsonParser {
    const val FORMAT = "sumire-keyboard-skin"
    const val FORMAT_VERSION = 1
    const val MAX_UTF8_BYTES = 256 * 1024

    fun parse(text: String): KeyboardSkinParseResult = parse(text.toByteArray(StandardCharsets.UTF_8))

    fun parse(bytes: ByteArray): KeyboardSkinParseResult {
        if (bytes.size > MAX_UTF8_BYTES) {
            return failure("$", "UTF-8 JSON must be at most 256 KiB")
        }
        val decoded = try {
            decodeUtf8(bytes)
        } catch (_: CharacterCodingException) {
            return failure("$", "入力はUTF-8である必要があります")
        }
        val stripped = stripAllowedWrapper(decoded)
            ?: return failure("$", "JSON以外の説明文、または許可されていないコードフェンスがあります")
        val root = try {
            JsonReader(StringReader(stripped)).use { reader ->
                reader.setStrictness(Strictness.STRICT)
                JsonParser.parseReader(reader)
            }
        } catch (error: RuntimeException) {
            return failure("$", "JSONを解析できません: ${error.message ?: "構文エラー"}")
        }
        if (!root.isJsonObject) return failure("$", "トップレベルはJSONオブジェクトである必要があります")

        return try {
            parseRoot(root.asJsonObject, stripped)
        } catch (error: ValidationFailure) {
            KeyboardSkinParseResult.Failure(listOf(error.validationError))
        } catch (error: RuntimeException) {
            failure("$", "JSONを検証できません: ${error.message ?: "不明なエラー"}")
        }
    }

    private fun parseRoot(root: JsonObject, normalizedJson: String): KeyboardSkinParseResult.Success {
        requireFields(
            root,
            "",
            setOf(
                "format", "formatVersion", "id", "name", "author", "description",
                "palette", "keys", "surfaces", "typography", "motion",
            ),
        )
        requireString(root, "format", "format").also {
            if (it != FORMAT) fail("format", "must be \"$FORMAT\"")
        }
        requireInt(root, "formatVersion", "formatVersion").also {
            if (it != FORMAT_VERSION) fail("formatVersion", "unsupported formatVersion: $it")
        }
        val id = requireString(root, "id", "id")
        if (!KeyboardSkinIdPattern.matches(id)) {
            fail("id", "must match [a-z][a-z0-9._-]{2,63}")
        }
        val name = requireString(root, "name", "name")
        if (name.length !in 1..50) fail("name", "length must be 1..50 characters")
        val author = optionalString(root, "author", "author")?.also {
            if (it.length > 50) fail("author", "length must be at most 50 characters")
        }?.takeIf(String::isNotEmpty)
        val description = optionalString(root, "description", "description")?.also {
            if (it.length > 200) fail("description", "length must be at most 200 characters")
        }

        val paletteObject = requireObject(root, "palette", "palette")
        val palette = parsePalette(paletteObject)
        val keysObject = requireObject(root, "keys", "keys")
        val baseKey = parseBaseStyle(requireObject(keysObject, "base", "keys.base"), "keys.base", palette)
        val keyStyles = linkedMapOf<KeyboardElementRole, KeyboardSkinShapeStyle>()
        keyStyles[KeyboardElementRole.CHARACTER] = baseKey
        for ((jsonName, role) in KEY_ROLE_NAMES) {
            val overrideObject = keysObject.get(jsonName)
            keyStyles[role] = if (overrideObject == null) {
                baseKey
            } else {
                parseStyleOverride(overrideObject.asObjectOrFail("keys.$jsonName"), "keys.$jsonName", palette)
                    .let(baseKey::merge)
            }
        }

        val surfacesObject = requireObject(root, "surfaces", "surfaces")
        requireFields(surfacesObject, "surfaces", SURFACE_ROLE_NAMES.keys)
        val surfaceStyles = SURFACE_ROLE_NAMES.entries.associate { (jsonName, role) ->
            role to parseBaseStyle(
                requireObject(surfacesObject, jsonName, "surfaces.$jsonName"),
                "surfaces.$jsonName",
                palette,
            )
        }

        val typography = parseTypography(requireObject(root, "typography", "typography"))
        val motion = parseMotion(requireObject(root, "motion", "motion"))
        val warnings = contrastWarnings(palette)
        val geometry = KeyboardSkinGeometry(
            cornerRadiusDp = baseKey.cornerRadiusDp,
            visualInsetDp = baseKey.insetDp,
            strokeWidthDp = baseKey.stroke?.widthDp ?: 0f,
            depthDp = baseKey.shadows.maxOfOrNull { maxOf(abs(it.offsetXDp), abs(it.offsetYDp)) } ?: 0f,
            irregularityDp = baseKey.roughnessDp,
        )
        val spec = KeyboardSkinSpec(
            id = KeyboardSkinId.DEFAULT,
            palette = palette,
            geometry = geometry,
            typography = typography,
            material = KeyboardSkinMaterial.FLAT,
            depthModel = KeyboardSkinDepthModel.NONE,
            motion = motion,
            popup = genericPopupSpec(palette, typography),
            reference = KeyboardSkinRef.Imported(id),
            keyStyles = keyStyles,
            surfaceStyles = surfaceStyles,
            displayName = name,
            author = author,
            description = description,
        )
        return KeyboardSkinParseResult.Success(
            ImportedKeyboardSkinDefinition(id, name, author, description, spec, warnings, normalizedJson)
        )
    }

    private fun parsePalette(objectValue: JsonObject): KeyboardSkinPalette {
        requireFields(objectValue, "palette", PALETTE_FIELDS)
        return KeyboardSkinPalette(
            backgroundColor = color(objectValue, "background", "palette.background", emptyMap()),
            normalKeyColor = color(objectValue, "normalKey", "palette.normalKey", emptyMap()),
            specialKeyColor = color(objectValue, "specialKey", "palette.specialKey", emptyMap()),
            actionKeyColor = color(objectValue, "actionKey", "palette.actionKey", emptyMap()),
            normalKeyTextColor = color(objectValue, "normalKeyText", "palette.normalKeyText", emptyMap()),
            specialKeyTextColor = color(objectValue, "specialKeyText", "palette.specialKeyText", emptyMap()),
            actionKeyTextColor = color(objectValue, "actionKeyText", "palette.actionKeyText", emptyMap()),
            accentColor = color(objectValue, "accent", "palette.accent", emptyMap()),
            secondaryAccentColor = color(objectValue, "secondaryAccent", "palette.secondaryAccent", emptyMap()),
            candidateSurfaceColor = color(objectValue, "candidateSurface", "palette.candidateSurface", emptyMap()),
            candidateTextColor = color(objectValue, "candidateText", "palette.candidateText", emptyMap()),
        )
    }

    private fun parseBaseStyle(
        objectValue: JsonObject,
        path: String,
        palette: KeyboardSkinPalette,
    ): KeyboardSkinShapeStyle {
        requireFields(objectValue, path, STYLE_FIELDS)
        val shape = parseShape(requireString(objectValue, "shape", "$path.shape"), "$path.shape")
        val fill = parseFill(requireObject(objectValue, "fill", "$path.fill"), "$path.fill", palette)
        return KeyboardSkinShapeStyle(
            shape = shape,
            fill = fill,
            cornerRadiusDp = number(objectValue, "cornerRadiusDp", "$path.cornerRadiusDp", 0.0, 32.0).toFloat(),
            insetDp = number(objectValue, "insetDp", "$path.insetDp", 0.0, 8.0).toFloat(),
            roughnessDp = number(objectValue, "roughnessDp", "$path.roughnessDp", 0.0, 3.0).toFloat(),
            cutSizeDp = number(objectValue, "cutSizeDp", "$path.cutSizeDp", 0.0, 32.0).toFloat(),
            stroke = objectValue.get("stroke")?.let {
                parseStroke(it.asObjectOrFail("$path.stroke"), "$path.stroke", palette)
            },
            shadows = objectValue.get("shadows")?.let {
                parseShadows(it.asArrayOrFail("$path.shadows"), "$path.shadows", palette)
            } ?: emptyList(),
            decoration = objectValue.get("decoration")?.let {
                parseDecoration(it.asObjectOrFail("$path.decoration"), "$path.decoration", palette)
            },
        )
    }

    private fun parseStyleOverride(
        objectValue: JsonObject,
        path: String,
        palette: KeyboardSkinPalette,
    ): KeyboardSkinShapeStyleOverride {
        requireFields(objectValue, path, STYLE_FIELDS)
        return KeyboardSkinShapeStyleOverride(
            shape = objectValue.get("shape")?.let {
                parseShape(it.asStringOrFail("$path.shape"), "$path.shape")
            },
            fill = objectValue.get("fill")?.let {
                parseFill(it.asObjectOrFail("$path.fill"), "$path.fill", palette)
            },
            cornerRadiusDp = objectValue.get("cornerRadiusDp")?.let {
                number(it, "$path.cornerRadiusDp", 0.0, 32.0).toFloat()
            },
            insetDp = objectValue.get("insetDp")?.let {
                number(it, "$path.insetDp", 0.0, 8.0).toFloat()
            },
            roughnessDp = objectValue.get("roughnessDp")?.let {
                number(it, "$path.roughnessDp", 0.0, 3.0).toFloat()
            },
            cutSizeDp = objectValue.get("cutSizeDp")?.let {
                number(it, "$path.cutSizeDp", 0.0, 32.0).toFloat()
            },
            stroke = objectValue.get("stroke")?.let {
                parseStroke(it.asObjectOrFail("$path.stroke"), "$path.stroke", palette)
            },
            shadows = objectValue.get("shadows")?.let {
                parseShadows(it.asArrayOrFail("$path.shadows"), "$path.shadows", palette)
            },
            decoration = objectValue.get("decoration")?.let {
                parseDecoration(it.asObjectOrFail("$path.decoration"), "$path.decoration", palette)
            },
        )
    }

    private fun parseFill(objectValue: JsonObject, path: String, palette: KeyboardSkinPalette): KeyboardSkinFill {
        val type = requireString(objectValue, "type", "$path.type")
        return when (type) {
            "solid" -> {
                requireFields(objectValue, path, setOf("type", "color"))
                KeyboardSkinFill.Solid(color(objectValue, "color", "$path.color", paletteRefs(palette)))
            }
            "linearGradient" -> {
                requireFields(objectValue, path, setOf("type", "colors", "stops", "angleDegrees"))
                val colors = colors(objectValue, "colors", "$path.colors", paletteRefs(palette), 2..4)
                val stops = stops(objectValue, "stops", "$path.stops", colors.size)
                KeyboardSkinFill.LinearGradient(
                    colors,
                    stops,
                    number(objectValue, "angleDegrees", "$path.angleDegrees", 0.0, 360.0).toFloat(),
                )
            }
            "radialGradient" -> {
                requireFields(objectValue, path, setOf("type", "colors", "stops", "centerX", "centerY", "radius"))
                val colors = colors(objectValue, "colors", "$path.colors", paletteRefs(palette), 2..4)
                val stops = stops(objectValue, "stops", "$path.stops", colors.size)
                KeyboardSkinFill.RadialGradient(
                    colors,
                    stops,
                    number(objectValue, "centerX", "$path.centerX", 0.0, 1.0).toFloat(),
                    number(objectValue, "centerY", "$path.centerY", 0.0, 1.0).toFloat(),
                    number(objectValue, "radius", "$path.radius", 0.01, 1.0).toFloat(),
                )
            }
            else -> fail("$path.type", "unknown fill type: $type")
        }
    }

    private fun parseStroke(objectValue: JsonObject, path: String, palette: KeyboardSkinPalette): KeyboardSkinStroke {
        requireFields(objectValue, path, setOf("color", "widthDp"))
        return KeyboardSkinStroke(
            color(objectValue, "color", "$path.color", paletteRefs(palette)),
            number(objectValue, "widthDp", "$path.widthDp", 0.0, 4.0).toFloat(),
        )
    }

    private fun parseShadows(array: JsonArray, path: String, palette: KeyboardSkinPalette): List<KeyboardSkinShadow> {
        if (array.size() > 2) fail(path, "at most 2 shadows are allowed")
        return array.mapIndexed { index, element ->
            val shadowPath = "$path[$index]"
            val objectValue = element.asObjectOrFail(shadowPath)
            requireFields(objectValue, shadowPath, setOf("color", "offsetXDp", "offsetYDp", "blurDp"))
            KeyboardSkinShadow(
                color(objectValue, "color", "$shadowPath.color", paletteRefs(palette)),
                number(objectValue, "offsetXDp", "$shadowPath.offsetXDp", -8.0, 8.0).toFloat(),
                number(objectValue, "offsetYDp", "$shadowPath.offsetYDp", -8.0, 8.0).toFloat(),
                number(objectValue, "blurDp", "$shadowPath.blurDp", 0.0, 12.0).toFloat(),
            )
        }
    }

    private fun parseDecoration(objectValue: JsonObject, path: String, palette: KeyboardSkinPalette): KeyboardSkinDecoration {
        requireFields(objectValue, path, setOf("type", "color", "opacity", "sizeDp", "spacingDp", "angleDegrees"))
        val type = when (val value = requireString(objectValue, "type", "$path.type")) {
            "none" -> KeyboardSkinDecorationType.NONE
            "dots" -> KeyboardSkinDecorationType.DOTS
            "grid" -> KeyboardSkinDecorationType.GRID
            "stripes" -> KeyboardSkinDecorationType.STRIPES
            "scanlines" -> KeyboardSkinDecorationType.SCANLINES
            "speckles" -> KeyboardSkinDecorationType.SPECKLES
            "weave" -> KeyboardSkinDecorationType.WEAVE
            else -> fail("$path.type", "unknown decoration type: $value")
        }
        return KeyboardSkinDecoration(
            type,
            color(objectValue, "color", "$path.color", paletteRefs(palette)),
            number(objectValue, "opacity", "$path.opacity", 0.0, 1.0).toFloat(),
            number(objectValue, "sizeDp", "$path.sizeDp", 0.1, 8.0).toFloat(),
            number(objectValue, "spacingDp", "$path.spacingDp", 0.5, 32.0).toFloat(),
            number(objectValue, "angleDegrees", "$path.angleDegrees", 0.0, 360.0).toFloat(),
        )
    }

    private fun parseTypography(objectValue: JsonObject): KeyboardSkinTypography {
        requireFields(objectValue, "typography", setOf("font", "weight", "letterSpacing"))
        val font = requireString(objectValue, "font", "typography.font")
        if (font !in setOf("sans", "sansMedium", "sansCondensed", "serif", "monospace")) {
            fail("typography.font", "unknown font: $font")
        }
        val weight = requireString(objectValue, "weight", "typography.weight")
        if (weight !in setOf("normal", "medium", "bold")) fail("typography.weight", "unknown weight: $weight")
        val family = when (font) {
            "sans" -> "sans-serif"
            "sansMedium" -> "sans-serif-medium"
            "sansCondensed" -> "sans-serif-condensed"
            "serif" -> "serif"
            else -> "monospace"
        }
        return KeyboardSkinTypography(
            familyName = family,
            bold = weight == "bold",
            letterSpacing = number(objectValue, "letterSpacing", "typography.letterSpacing", -0.1, 0.2).toFloat(),
        )
    }

    private fun parseMotion(objectValue: JsonObject): KeyboardSkinMotionSpec {
        requireFields(objectValue, "motion", setOf("press", "background"))
        val press = requireObject(objectValue, "press", "motion.press")
        requireFields(press, "motion.press", setOf("scale", "translationXDp", "translationYDp", "durationMs", "releaseDurationMs"))
        val background = requireObject(objectValue, "background", "motion.background")
        requireFields(background, "motion.background", setOf("type", "periodSeconds"))
        val backgroundType = requireString(background, "type", "motion.background.type")
        if (backgroundType !in setOf("none", "pulse", "sweep", "shift")) {
            fail("motion.background.type", "unknown background animation: $backgroundType")
        }
        val periodSeconds = number(background, "periodSeconds", "motion.background.periodSeconds", 0.0, 30.0)
        if (backgroundType != "none" && periodSeconds !in 2.0..30.0) {
            fail("motion.background.periodSeconds", "animated backgrounds require 2..30 seconds")
        }
        return KeyboardSkinMotionSpec(
            pressScale = number(press, "scale", "motion.press.scale", 0.90, 1.05).toFloat(),
            pressTranslationYDp = number(press, "translationYDp", "motion.press.translationYDp", -4.0, 4.0).toFloat(),
            pressTranslationXDp = number(press, "translationXDp", "motion.press.translationXDp", -4.0, 4.0).toFloat(),
            pressDurationMs = integer(press, "durationMs", "motion.press.durationMs", 0, 500).toLong(),
            releaseDurationMs = integer(press, "releaseDurationMs", "motion.press.releaseDurationMs", 0, 500).toLong(),
            continuousPeriodMs = if (backgroundType == "none") 0L else (periodSeconds * 1000.0).toLong(),
            backgroundAnimation = when (backgroundType) {
                "none" -> KeyboardSkinBackgroundAnimation.NONE
                "pulse" -> KeyboardSkinBackgroundAnimation.PULSE
                "sweep" -> KeyboardSkinBackgroundAnimation.SWEEP
                else -> KeyboardSkinBackgroundAnimation.SHIFT
            },
        )
    }

    private fun contrastWarnings(palette: KeyboardSkinPalette): List<KeyboardSkinValidationWarning> = buildList {
        listOf(
            "palette.normalKeyText" to ratio(palette.normalKeyTextColor, palette.normalKeyColor),
            "palette.specialKeyText" to ratio(palette.specialKeyTextColor, palette.specialKeyColor),
            "palette.actionKeyText" to ratio(palette.actionKeyTextColor, palette.actionKeyColor),
            "palette.candidateText" to ratio(palette.candidateTextColor, palette.candidateSurfaceColor),
        ).forEach { (path, value) ->
            if (value < 4.5) add(KeyboardSkinValidationWarning(path, "コントラスト比 ${"%.2f".format(value)}:1 は4.5:1未満です"))
        }
    }

    private fun ratio(foreground: Int, background: Int): Double {
        fun composite(color: Int, over: Int): Int {
            val alpha = alpha(color) / 255.0
            return argb(
                255,
                (red(color) * alpha + red(over) * (1 - alpha)).toInt(),
                (green(color) * alpha + green(over) * (1 - alpha)).toInt(),
                (blue(color) * alpha + blue(over) * (1 - alpha)).toInt(),
            )
        }
        fun luminance(color: Int): Double {
            fun channel(value: Int): Double {
                val normalized = value / 255.0
                return if (normalized <= 0.03928) normalized / 12.92 else ((normalized + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(red(color)) + 0.7152 * channel(green(color)) + 0.0722 * channel(blue(color))
        }
        val foregroundLuminance = luminance(composite(foreground, background))
        val backgroundLuminance = luminance(background)
        val light = maxOf(foregroundLuminance, backgroundLuminance)
        val dark = minOf(foregroundLuminance, backgroundLuminance)
        return (light + 0.05) / (dark + 0.05)
    }

    private fun genericPopupSpec(palette: KeyboardSkinPalette, typography: KeyboardSkinTypography) = KeyboardSkinPopupSpec(
        surfaceColor = palette.specialKeyColor,
        selectedSurfaceColor = palette.accentColor,
        textColor = palette.specialKeyTextColor,
        selectedTextColor = palette.actionKeyTextColor,
        secondaryTextColor = palette.normalKeyTextColor,
        shadowColor = 0xFF000000.toInt(),
        shadowAlpha = 48,
        selectedShadowAlpha = 64,
        cornerRadiusDp = 8f,
        strokeWidthDp = 0f,
        stemWidthDp = 10f,
        stemHeightDp = 6f,
        contentPaddingHorizontalDp = 10f,
        contentPaddingVerticalDp = 6f,
        itemGapDp = 4f,
        keyPreviewWidthScale = 2f,
        keyPreviewHeightScale = 2f,
        keyPreviewTextSizeSp = if (typography.bold) 28f else 26f,
        variationTextSizeSp = 26f,
        flickTextSizeSp = 22f,
    )

    private fun paletteRefs(palette: KeyboardSkinPalette): Map<String, Int> = mapOf(
        "background" to palette.backgroundColor,
        "normalKey" to palette.normalKeyColor,
        "specialKey" to palette.specialKeyColor,
        "actionKey" to palette.actionKeyColor,
        "normalKeyText" to palette.normalKeyTextColor,
        "specialKeyText" to palette.specialKeyTextColor,
        "actionKeyText" to palette.actionKeyTextColor,
        "accent" to palette.accentColor,
        "secondaryAccent" to palette.secondaryAccentColor,
        "candidateSurface" to palette.candidateSurfaceColor,
        "candidateText" to palette.candidateTextColor,
    )

    private fun colors(
        objectValue: JsonObject,
        key: String,
        path: String,
        refs: Map<String, Int>,
        range: IntRange,
    ): List<Int> {
        val array = requireArray(objectValue, key, path)
        if (array.size() !in range) fail(path, "must contain ${range.first}..${range.last} colors")
        return array.mapIndexed { index, item -> parseColor(item, "$path[$index]", refs) }
    }

    private fun stops(objectValue: JsonObject, key: String, path: String, expected: Int): List<Float> {
        val array = requireArray(objectValue, key, path)
        if (array.size() != expected) fail(path, "must contain one stop per color")
        val values = array.mapIndexed { index, item -> number(item, "$path[$index]", 0.0, 1.0).toFloat() }
        if (values.firstOrNull() != 0f || values.lastOrNull() != 1f || values.zipWithNext().any { it.first >= it.second }) {
            fail(path, "stops must start at 0, end at 1, and be strictly increasing")
        }
        return values
    }

    private fun color(objectValue: JsonObject, key: String, path: String, refs: Map<String, Int>): Int =
        parseColor(objectValue.get(key) ?: fail(path, "is required"), path, refs)

    private fun parseColor(element: JsonElement, path: String, refs: Map<String, Int>): Int {
        val value = element.asStringOrFail(path)
        if (value.startsWith("@palette.")) {
            return refs[value.removePrefix("@palette.")] ?: fail(path, "unknown palette reference: $value")
        }
        if (!value.matches(Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))) {
            fail(path, "must be #RRGGBB, #AARRGGBB, or @palette.<name>")
        }
        val hex = value.removePrefix("#")
        val argbHex = if (hex.length == 6) "FF$hex" else hex
        return argbHex.toLong(16).toInt()
    }

    private fun parseShape(value: String, path: String): KeyboardSkinShape = when (value) {
        "roundedRect" -> KeyboardSkinShape.ROUNDED_RECT
        "capsule" -> KeyboardSkinShape.CAPSULE
        "cutCorner" -> KeyboardSkinShape.CUT_CORNER
        "hexagon" -> KeyboardSkinShape.HEXAGON
        "pixelNotched" -> KeyboardSkinShape.PIXEL_NOTCHED
        "roughRect" -> KeyboardSkinShape.ROUGH_RECT
        else -> fail(path, "unknown shape: $value")
    }

    private fun requireFields(objectValue: JsonObject, path: String, allowed: Set<String>) {
        objectValue.keySet().firstOrNull { it !in allowed }?.let {
            fail(if (path.isEmpty()) it else "$path.$it", "unknown field")
        }
    }

    private fun requireString(objectValue: JsonObject, key: String, path: String): String =
        (objectValue.get(key) ?: fail(path, "is required")).asStringOrFail(path)

    private fun optionalString(objectValue: JsonObject, key: String, path: String): String? =
        objectValue.get(key)?.asStringOrFail(path)

    private fun requireInt(objectValue: JsonObject, key: String, path: String): Int =
        integer(objectValue, key, path, Int.MIN_VALUE, Int.MAX_VALUE)

    private fun integer(objectValue: JsonObject, key: String, path: String, min: Int, max: Int): Int =
        number(objectValue, key, path, min.toDouble(), max.toDouble()).let {
            if (it % 1.0 != 0.0) fail(path, "must be an integer")
            it.toInt()
        }

    private fun number(objectValue: JsonObject, key: String, path: String, min: Double, max: Double): Double =
        number(objectValue.get(key) ?: fail(path, "is required"), path, min, max)

    private fun number(element: JsonElement, path: String, min: Double, max: Double): Double {
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) fail(path, "must be a number")
        val value = element.asDouble
        if (!value.isFinite()) fail(path, "must be finite")
        if (value !in min..max) fail(path, "must be in $min..$max")
        return value
    }

    private fun requireObject(objectValue: JsonObject, key: String, path: String): JsonObject =
        (objectValue.get(key) ?: fail(path, "is required")).asObjectOrFail(path)

    private fun requireArray(objectValue: JsonObject, key: String, path: String): JsonArray =
        (objectValue.get(key) ?: fail(path, "is required")).asArrayOrFail(path)

    private fun JsonElement.asObjectOrFail(path: String): JsonObject =
        if (isJsonObject) asJsonObject else fail(path, "must be an object")

    private fun JsonElement.asArrayOrFail(path: String): JsonArray =
        if (isJsonArray) asJsonArray else fail(path, "must be an array")

    private fun JsonElement.asStringOrFail(path: String): String =
        if (isJsonPrimitive && asJsonPrimitive.isString) asString else fail(path, "must be a string")

    private fun decodeUtf8(bytes: ByteArray): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val buffer: CharBuffer = decoder.decode(ByteBuffer.wrap(bytes))
        return buffer.toString()
    }

    private fun stripAllowedWrapper(value: String): String? {
        var result = value.removePrefix("\uFEFF").trim()
        if (result.startsWith("```") || result.endsWith("```")) {
            val prefix = "```json"
            if (!result.startsWith(prefix) || !result.endsWith("```")) return null
            result = result.substring(prefix.length, result.length - 3).trim()
        }
        if (result.contains("```")) return null
        return result
    }

    private fun failure(path: String, message: String) =
        KeyboardSkinParseResult.Failure(listOf(KeyboardSkinValidationError(path, message)))

    private fun fail(path: String, message: String): Nothing =
        throw ValidationFailure(KeyboardSkinValidationError(path.ifEmpty { "$" }, message))

    private class ValidationFailure(val validationError: KeyboardSkinValidationError) : RuntimeException()

    private val PALETTE_FIELDS = setOf(
        "background", "normalKey", "specialKey", "actionKey", "normalKeyText", "specialKeyText",
        "actionKeyText", "accent", "secondaryAccent", "candidateSurface", "candidateText",
    )
    private val STYLE_FIELDS = setOf(
        "shape", "fill", "cornerRadiusDp", "insetDp", "roughnessDp", "cutSizeDp", "stroke", "shadows", "decoration",
    )
    private val KEY_ROLE_NAMES = linkedMapOf(
        "character" to KeyboardElementRole.CHARACTER,
        "modifier" to KeyboardElementRole.MODIFIER,
        "action" to KeyboardElementRole.ACTION,
        "space" to KeyboardElementRole.SPACE,
        "candidate" to KeyboardElementRole.CANDIDATE,
        "toolbar" to KeyboardElementRole.TOOLBAR,
        "popup" to KeyboardElementRole.POPUP,
    )
    private val SURFACE_ROLE_NAMES = linkedMapOf(
        "deck" to KeyboardSurfaceRole.DECK,
        "candidateStrip" to KeyboardSurfaceRole.CANDIDATE_STRIP,
        "candidatePanel" to KeyboardSurfaceRole.CANDIDATE_PANEL,
        "toolbar" to KeyboardSurfaceRole.TOOLBAR,
        "popup" to KeyboardSurfaceRole.POPUP,
    )

    private fun alpha(color: Int): Int = color ushr 24 and 0xFF
    private fun red(color: Int): Int = color ushr 16 and 0xFF
    private fun green(color: Int): Int = color ushr 8 and 0xFF
    private fun blue(color: Int): Int = color and 0xFF
    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)
}

private val KeyboardSkinIdPattern = Regex("[a-z][a-z0-9._-]{2,63}")
