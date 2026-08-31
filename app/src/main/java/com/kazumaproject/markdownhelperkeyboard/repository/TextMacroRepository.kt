package com.kazumaproject.markdownhelperkeyboard.repository

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroValidator
import com.kazumaproject.markdownhelperkeyboard.text_macro.database.TextMacro
import com.kazumaproject.markdownhelperkeyboard.text_macro.database.TextMacroDao
import com.kazumaproject.markdownhelperkeyboard.text_macro.database.TextMacroImportCounts
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Locale

data class TextMacroBackupEntry(
    val name: String,
    val reading: String?,
    val body: String,
    val enabled: Boolean,
)

data class TextMacroBackup(
    val version: Int = VERSION,
    val macros: List<TextMacroBackupEntry>,
) {
    companion object {
        const val VERSION = 1
    }
}

data class TextMacroImportPlan(
    val entries: List<TextMacroBackupEntry>,
    val added: Int,
    val overwritten: Int,
)

@Singleton
class TextMacroRepository @Inject constructor(
    private val dao: TextMacroDao,
) {
    fun observeAll(): Flow<List<TextMacro>> = dao.observeAll()

    fun search(query: String): Flow<List<TextMacro>> = dao.search(escapeLike(query.trim()))

    suspend fun getById(id: Long): TextMacro? = dao.getById(id)

    suspend fun getAllEnabled(): List<TextMacro> = dao.getAllEnabled()

    suspend fun getEnabledByReading(reading: String, limit: Int = 8): List<TextMacro> =
        dao.getEnabledByReading(reading, limit)

    suspend fun getEnabledSelectionMacros(limit: Int = 8): List<TextMacro> =
        dao.getEnabledSelectionMacros(limit)

    suspend fun save(macro: TextMacro): Long {
        val normalized = normalizeAndValidate(macro)
        val sameName = dao.getByName(normalized.name)
        require(sameName == null || sameName.id == normalized.id) {
            "A macro with this name already exists"
        }
        return if (normalized.id == 0L) {
            dao.insert(normalized)
        } else {
            dao.update(normalized)
            normalized.id
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun exportJson(): String {
        val backup = TextMacroBackup(
            macros = dao.getAll().map {
                TextMacroBackupEntry(
                    name = it.name,
                    reading = it.reading,
                    body = it.body,
                    enabled = it.enabled,
                )
            },
        )
        return Gson().toJson(backup)
    }

    suspend fun prepareImport(json: String): TextMacroImportPlan {
        val root = JsonParser.parseString(json)
        require(root.isJsonObject) { "Backup root must be an object" }
        val rootObject = root.asJsonObject
        require(rootObject.keySet() == setOf("version", "macros")) {
            "Backup must contain only version and macros"
        }
        val version = rootObject.get("version")
        require(
            version != null && version.isJsonPrimitive &&
                version.asJsonPrimitive.isNumber &&
                version.asInt == TextMacroBackup.VERSION
        ) {
            "Unsupported text macro backup version"
        }
        val macrosJson = rootObject.get("macros")
        require(macrosJson != null && macrosJson.isJsonArray) { "macros must be an array" }

        val entries = macrosJson.asJsonArray.mapIndexed { index, element ->
            require(element.isJsonObject) { "macros[$index] must be an object" }
            val item = element.asJsonObject
            require(item.keySet() == setOf("name", "reading", "body", "enabled")) {
                "macros[$index] has missing or unknown fields"
            }
            val name = item.requiredString("name", index)
            val reading = item.get("reading").let {
                when {
                    it == null || it.isJsonNull -> null
                    it.isJsonPrimitive && it.asJsonPrimitive.isString -> it.asString
                    else -> throw IllegalArgumentException("macros[$index].reading must be a string or null")
                }
            }
            val body = item.requiredString("body", index)
            val enabledElement = item.get("enabled")
            require(
                enabledElement != null && enabledElement.isJsonPrimitive &&
                    enabledElement.asJsonPrimitive.isBoolean
            ) { "macros[$index].enabled must be a boolean" }
            TextMacroValidator.validateDefinition(name, reading, body)
            TextMacroBackupEntry(name, reading?.takeIf(String::isNotBlank), body, enabledElement.asBoolean)
        }
        require(entries.map { it.name.lowercase(Locale.ROOT) }.distinct().size == entries.size) {
            "Backup contains duplicate macro names"
        }

        val existingNames = dao.getAll().map { it.name.lowercase(Locale.ROOT) }.toSet()
        val overwritten = entries.count { it.name.lowercase(Locale.ROOT) in existingNames }
        return TextMacroImportPlan(
            entries = entries,
            added = entries.size - overwritten,
            overwritten = overwritten,
        )
    }

    suspend fun applyImport(plan: TextMacroImportPlan): TextMacroImportCounts =
        dao.applyByName(plan.entries.map {
            TextMacro(
                name = it.name,
                reading = it.reading,
                body = it.body,
                enabled = it.enabled,
            )
        })

    private fun normalizeAndValidate(macro: TextMacro): TextMacro {
        val name = macro.name.trim()
        val reading = macro.reading?.trim()?.takeIf(String::isNotEmpty)
        TextMacroValidator.validateDefinition(name, reading, macro.body)
        return macro.copy(name = name, reading = reading)
    }

    private fun escapeLike(query: String): String = query
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}

private fun com.google.gson.JsonObject.requiredString(name: String, index: Int): String {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
        "macros[$index].$name must be a string"
    }
    return value.asString
}
