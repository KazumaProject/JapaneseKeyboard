package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import java.io.StringReader

/**
 * 外部 JSON 文字列からカスタムキーボードレイアウトを読み込むための importer。
 *
 * 担当範囲:
 * - JSON の root が array(旧/現行形式) なのか object(schemaVersion 付き新形式) なのかを判定
 * - schemaVersion が無い場合は legacy v0 として扱う
 * - 欠損 / null フィールドを empty list / safe default に正規化
 * - 結果として [ImportableKeyboardLayout] を返す
 *
 * 重要: ここから外側(Repository, ViewModel)では null 防御を意識しなくて良いように、
 * すべての List を non-null にして返す。
 */
object KeyboardLayoutJsonImporter {

    /**
     * 想定する最新の schemaVersion。
     */
    const val LATEST_SCHEMA_VERSION: Int = 2

    internal val gson: Gson by lazy {
        GsonBuilder()
            .setLenient()
            .create()
    }

    /**
     * JSON 文字列を parse して [KeyboardLayoutImportResult] を返す。
     *
     * 旧形式 JSON でも、欠損フィールド付き JSON でも、可能な限り読み込みを成功させる。
     * parse 失敗は emptyList に潰さず、失敗理由を型で返す。
     */
    fun parse(jsonString: String): KeyboardLayoutImportResult {
        return when (val parsed = parseDtos(jsonString)) {
            is KeyboardLayoutJsonParseResult.Success ->
                KeyboardBackupNormalizer.normalize(parsed.layouts)

            is KeyboardLayoutJsonParseResult.Failure ->
                KeyboardLayoutImportResult.Failure(parsed.error)
        }
    }

    internal fun parseDtos(jsonString: String): KeyboardLayoutJsonParseResult {
        val sanitized = KeyboardLayoutBackupFormatDetector.sanitize(jsonString)

        if (sanitized.isBlank()) {
            return KeyboardLayoutJsonParseResult.Failure(KeyboardLayoutImportError.EmptyInput)
        }

        val root: JsonElement = try {
            JsonParser.parseReader(
                JsonReader(StringReader(sanitized)).apply { isLenient = true }
            )
        } catch (e: Exception) {
            return KeyboardLayoutJsonParseResult.Failure(
                KeyboardLayoutImportError.MalformedJson(
                    exceptionClass = e::class.java.simpleName,
                    message = e.message
                )
            )
        }

        return try {
            KeyboardBackupParser.parse(root, gson)
        } catch (e: Exception) {
            KeyboardLayoutJsonParseResult.Failure(
                KeyboardLayoutImportError.MalformedJson(
                    exceptionClass = e::class.java.simpleName,
                    message = e.message
                )
            )
        }
    }

    internal fun looksLikeLayoutBackup(jsonString: String): Boolean {
        val sanitized = KeyboardLayoutBackupFormatDetector.sanitize(jsonString)
        if (sanitized.isBlank()) return false
        return runCatching {
            val root = JsonParser.parseReader(
                JsonReader(StringReader(sanitized)).apply { isLenient = true }
            )
            KeyboardBackupValidator.isLayoutBackupRoot(root)
        }.getOrDefault(false)
    }
}

sealed class KeyboardLayoutJsonParseResult {
    data class Success(val layouts: List<KeyboardLayoutExportDto>) : KeyboardLayoutJsonParseResult()
    data class Failure(val error: KeyboardLayoutImportError) : KeyboardLayoutJsonParseResult()
}
