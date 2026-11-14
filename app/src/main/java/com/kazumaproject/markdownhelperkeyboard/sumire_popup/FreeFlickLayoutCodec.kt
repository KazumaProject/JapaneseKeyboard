package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import com.kazumaproject.custom_keyboard.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream
import java.io.ByteArrayOutputStream

data class FreeFlickLayout(val id: String, val name: String, val keys: List<PopupKeyConfig>)
internal data class FreeFlickLayoutLibrary(val selectedId: String, val layouts: List<FreeFlickLayout>)

/** Explicit wire names: neither the file format nor library storage depends on R8 field names. */
object FreeFlickLayoutCodec {
    const val MAX_BYTES = 1024 * 1024
    const val MAX_LAYOUTS = 32
    private const val FORMAT = "sumire-free-flick-layout"

    fun read(input: InputStream): FreeFlickLayout {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
            require(output.size() <= MAX_BYTES) { "Layout file is too large" }
        }
        return decode(output.toString(Charsets.UTF_8.name()))
    }

    fun encode(layout: FreeFlickLayout): String {
        validate(layout)
        return JSONObject().put("format", FORMAT).put("version", 1).put("name", layout.name)
            .put("keys", keysJson(layout.keys)).toString(2).also {
                require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            }
    }

    fun decode(json: String): FreeFlickLayout {
        val root = parse(json)
        require(root.string("format") == FORMAT && root.integer("version") == 1)
        return FreeFlickLayout("", root.string("name"), readKeys(root.array("keys"))).also { validate(it) }
    }

    internal fun encodeLibrary(library: FreeFlickLayoutLibrary): String = JSONObject().put("version", 1)
        .put("selectedId", library.selectedId).put("layouts", JSONArray().apply {
            library.layouts.forEach { layout ->
                validate(layout)
                put(JSONObject().put("id", layout.id).put("name", layout.name).put("keys", keysJson(layout.keys)))
            }
        }).toString()

    internal fun decodeLibrary(json: String): FreeFlickLayoutLibrary {
        val root = parse(json, MAX_BYTES * MAX_LAYOUTS)
        require(root.integer("version") == 1)
        val layouts = root.array("layouts").objects(MAX_LAYOUTS).map {
            FreeFlickLayout(it.string("id"), it.string("name"), readKeys(it.array("keys"))).also { layout ->
                require(layout.id.isNotBlank() && layout.id != FreeFlickLayoutRepository.BUILTIN_ID)
                validate(layout)
            }
        }
        require(layouts.map { it.id }.distinct().size == layouts.size)
        require(layouts.map { it.name }.distinct().size == layouts.size)
        val selected = root.string("selectedId")
        require(selected == FreeFlickLayoutRepository.BUILTIN_ID || layouts.any { it.id == selected })
        return FreeFlickLayoutLibrary(selected, layouts)
    }

    fun validate(layout: FreeFlickLayout) {
        require(layout.name.isNotBlank() && layout.name.length <= 64)
        require(layout.keys.map { it.keyId }.toSet() == SumirePopupDefaults.rows.keys && layout.keys.size == SumirePopupDefaults.rows.size)
        layout.keys.forEach { key ->
            require(key.maps.size in 1..64)
            key.maps.forEach { map ->
                require(map.id.isNotBlank() && map.id.length <= 128 && map.name.isNotBlank() && map.name.length <= 128)
                require(map.tapText.length <= 4096 && map.regions.size <= 128)
                map.regions.forEach { region ->
                    require(region.id.isNotBlank() && region.id.length <= 128 && region.label.length <= 128 && region.action.text.length <= 4096)
                    val b = region.bounds
                    require(listOf(b.left, b.top, b.right, b.bottom).all { it.isFinite() && it in -20f..20f })
                    require(b.width >= .01f && b.height >= .01f)
                }
            }
            require(PopupMapValidator.conflicts(key).isEmpty())
        }
    }

    private fun keysJson(keys: List<PopupKeyConfig>) = JSONArray().apply { keys.forEach { key ->
        put(JSONObject().put("keyId", key.keyId).put("startMapId", key.startMapId).put("version", key.version)
            .put("maps", JSONArray().apply { key.maps.forEach { map ->
                put(JSONObject().put("id", map.id).put("name", map.name).put("tapText", map.tapText)
                    .put("regions", JSONArray().apply { map.regions.forEach { region ->
                        put(JSONObject().put("id", region.id).put("label", region.label)
                            .put("bounds", JSONObject().put("left", region.bounds.left).put("top", region.bounds.top)
                                .put("right", region.bounds.right).put("bottom", region.bounds.bottom))
                            .put("action", JSONObject().put("type", region.action.type.name).put("text", region.action.text)
                                .put("targetMapId", region.action.targetMapId ?: JSONObject.NULL)).apply {
                                region.outline?.let { outline -> put("outline", JSONObject().put("vertices", JSONArray().apply {
                                    outline.vertices.forEach { put(JSONObject().put("x", it.x).put("y", it.y)) }
                                })) }
                            })
                    }}))
            }}))
    }}

    private fun readKeys(array: JSONArray): List<PopupKeyConfig> {
        val keys = array.objects(SumirePopupDefaults.rows.size).map { key ->
        PopupKeyConfig(key.string("keyId"), key.string("startMapId"), key.array("maps").objects(64).map { map ->
            PopupMap(map.string("id"), map.string("name"), map.string("tapText"), map.array("regions").objects(128).map { region ->
                val b = region.getJSONObject("bounds"); val action = region.getJSONObject("action")
                PopupRegion(region.string("id"), PopupBounds(b.number("left"), b.number("top"), b.number("right"), b.number("bottom")),
                    PopupAction(PopupActionType.valueOf(action.string("type")), action.string("text"),
                        if (!action.has("targetMapId") || action.isNull("targetMapId")) null else action.string("targetMapId")),
                    if (region.has("label")) region.string("label") else "",
                    if (!region.has("outline") || region.isNull("outline")) null else PopupOutline(
                        region.getJSONObject("outline").array("vertices").objects(PopupOutline.MAX_VERTICES).map { PopupVertex(it.number("x"), it.number("y")) }
                    ))
            })
        }, key.integer("version"))
        }
        // v1 layouts written before punctuation editing contain exactly the ten kana keys.
        val legacyKeys = SumirePopupDefaults.rows.keys - SumirePopupDefaults.PUNCTUATION_KEY
        return if (keys.size == legacyKeys.size && keys.map { it.keyId }.toSet() == legacyKeys) {
            keys + SumirePopupDefaults.create(SumirePopupDefaults.PUNCTUATION_KEY)
        } else keys
    }
    private fun parse(json: String, limit: Int = MAX_BYTES): JSONObject {
        require(json.toByteArray(Charsets.UTF_8).size <= limit)
        var depth = 0; var quoted = false; var escaped = false
        json.forEach { char ->
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 32) }
                '}', ']' -> depth--
            }
        }
        val parser = JSONTokener(json)
        val root = parser.nextValue() as? JSONObject ?: error("Expected a JSON object")
        require(parser.nextClean() == '\u0000')
        return root
    }
    private fun JSONObject.string(key: String) = (get(key) as? String ?: error("Expected $key string"))
    private fun JSONObject.array(key: String) = getJSONArray(key)
    private fun JSONObject.integer(key: String): Int {
        val number = get(key) as? Number ?: error("Expected $key number")
        require(number.toDouble().isFinite() && number.toDouble() == number.toInt().toDouble())
        return number.toInt()
    }
    private fun JSONObject.number(key: String): Float {
        val number = get(key) as? Number ?: error("Expected $key number")
        return number.toFloat().also { require(it.isFinite()) }
    }
    private fun JSONArray.objects(max: Int): List<JSONObject> {
        require(length() <= max)
        return (0 until length()).map { getJSONObject(it) }
    }
}
