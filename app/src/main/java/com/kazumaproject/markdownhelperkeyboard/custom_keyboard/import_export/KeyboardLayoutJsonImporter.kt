package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import java.io.StringReader

/**
 * 外部 JSON 文字列からカスタムキーボードレイアウトを読み込むための importer。
 *
 * 担当範囲:
 * - JSON の root が array(旧/現行形式) なのか object(schemaVersion 付き新形式) なのかを判定
 * - schemaVersion が無い場合は version 1 として扱う
 * - 欠損 / null フィールドをすべて空 list / 妥当な値に正規化
 * - 結果として [ImportableKeyboardLayout] を返す
 *
 * 重要: ここから外側(Repository, ViewModel)では null 防御を意識しなくて良いように、
 * すべての List を non-null にして返す。
 */
object KeyboardLayoutJsonImporter {

    /**
     * 想定する最新の schemaVersion。
     * これより新しい version の JSON を渡された場合も、可能な限り 互換 parse を試みる。
     */
    const val LATEST_SCHEMA_VERSION: Int = 2

    /** UTF-8 BOM (U+FEFF) を表す Char。リテラル化を避けるため数値から生成。 */
    private val BOM_CHAR: Char = 0xFEFF.toChar()
    /** NULL byte (U+0000) を表す String。リテラル化を避けるため数値から生成。 */
    private val NULL_CHAR: String = 0.toChar().toString()

    private val gson: Gson by lazy {
        GsonBuilder()
            .setLenient()
            .create()
    }

    /**
     * JSON 文字列を parse して [ImportableKeyboardLayout] のリストを返す。
     *
     * 旧形式 JSON でも、欠損フィールド付き JSON でも、可能な限り読み込みを成功させる。
     * 全体として読めない場合のみ空リストを返す。
     */
    fun parse(jsonString: String): List<ImportableKeyboardLayout> {
        // BOM (U+FEFF) と null byte (U+0000) を除去するだけにとどめる。
        // 通常の空白 / 改行は JSON として有効なので削除しない。
        val sanitized = jsonString
            .trimStart(BOM_CHAR)
            .replace(NULL_CHAR, "")

        if (sanitized.isBlank()) return emptyList()

        val root: JsonElement = try {
            JsonParser.parseReader(
                JsonReader(StringReader(sanitized)).apply { isLenient = true }
            )
        } catch (_: Exception) {
            return emptyList()
        }

        val rawDtos: List<KeyboardLayoutExportDto> = when {
            root.isJsonArray -> parseLegacyArray(root)
            root.isJsonObject -> parseObjectRoot(root.asJsonObject)
            else -> emptyList()
        }

        return rawDtos.mapNotNull { dto -> dto.toImportableOrNull() }
    }

    // -----------------------------
    // root parsing
    // -----------------------------

    /**
     * root が array の旧 / 現行形式を読む。
     *
     * 形式:
     * ```
     * [
     *   { "layout": {...}, "keysWithFlicks": [...], "spacers": [...] },
     *   ...
     * ]
     * ```
     */
    private fun parseLegacyArray(root: JsonElement): List<KeyboardLayoutExportDto> {
        return try {
            val type = object : TypeToken<List<KeyboardLayoutExportDto>>() {}.type
            gson.fromJson<List<KeyboardLayoutExportDto>?>(root, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * root が object 形式の場合を読む。
     *
     * - schemaVersion が無ければ version 1 として扱う
     * - schemaVersion が 2 以降は最新互換 parse を試みる
     */
    private fun parseObjectRoot(obj: JsonObject): List<KeyboardLayoutExportDto> {
        val version = obj["schemaVersion"]
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asInt
            ?: 1

        return when {
            version <= 1 -> parseVersion1Object(obj)
            else -> parseVersion2OrLatestCompatibleObject(obj)
        }
    }

    /**
     * 旧式 (schemaVersion 無し) で、たまたま root が object になっているような
     * ケース向けのフォールバック。
     *
     * - {"layouts": [...]} の形だけ受け入れる。
     * - 単一 layout を root object 直下に置いた形ももしあれば一応読む。
     */
    private fun parseVersion1Object(obj: JsonObject): List<KeyboardLayoutExportDto> {
        // {"layouts": [...]} 形式を優先
        val layoutsElement = obj["layouts"]
        if (layoutsElement != null && layoutsElement.isJsonArray) {
            return runCatching {
                val type = object : TypeToken<List<KeyboardLayoutExportDto>>() {}.type
                gson.fromJson<List<KeyboardLayoutExportDto>?>(layoutsElement, type) ?: emptyList()
            }.getOrDefault(emptyList())
        }

        // 単一 layout root の保険(root object そのものが 1 layout dto)
        if (obj.has("layout") || obj.has("keysWithFlicks")) {
            return runCatching {
                val singleDto = gson.fromJson(obj, KeyboardLayoutExportDto::class.java)
                if (singleDto != null) listOf(singleDto) else emptyList()
            }.getOrDefault(emptyList())
        }

        return emptyList()
    }

    /**
     * schemaVersion >= 2 の object 形式を読む。
     * 将来 schemaVersion = 3, 4 ... が来ても、未知フィールドは無視して
     * 既知フィールド範囲で互換 parse する。
     */
    private fun parseVersion2OrLatestCompatibleObject(obj: JsonObject): List<KeyboardLayoutExportDto> {
        return runCatching {
            val fileDto = gson.fromJson(obj, KeyboardLayoutExportFileDto::class.java)
            fileDto?.layouts ?: emptyList()
        }.getOrDefault(emptyList())
    }
}

// -----------------------------
// DTO -> Importable normalization
// -----------------------------

/**
 * 外部 JSON DTO を、Repository に渡せる正規化済みモデル
 * [ImportableKeyboardLayout] に変換する。
 *
 * - layout が null の場合は import 対象から除外する。
 * - keysWithFlicks 内の各 dto で key が null の要素は skip する。
 * - 全ての List は emptyList で埋めて non-null にする。
 */
internal fun KeyboardLayoutExportDto.toImportableOrNull(): ImportableKeyboardLayout? {
    val layout = this.layout ?: return null

    val keys: List<ImportableKeyWithFlicks> =
        (this.keysWithFlicks ?: emptyList()).mapNotNull { kw ->
            val key = kw.key ?: return@mapNotNull null
            ImportableKeyWithFlicks(
                key = key,
                flicks = kw.flicks ?: emptyList(),
                circularFlicks = kw.circularFlicks ?: emptyList(),
                twoStepFlicks = kw.twoStepFlicks ?: emptyList(),
                longPressFlicks = kw.longPressFlicks ?: emptyList(),
                twoStepLongPressFlicks = kw.twoStepLongPressFlicks ?: emptyList()
            )
        }

    val spacers = this.spacers ?: emptyList()

    return ImportableKeyboardLayout(
        layout = layout,
        keysWithFlicks = keys,
        spacers = spacers
    )
}
