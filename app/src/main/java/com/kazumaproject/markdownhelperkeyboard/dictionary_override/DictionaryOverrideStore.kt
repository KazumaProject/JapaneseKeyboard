package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryOverrideStore private constructor(
    private val validator: DictionaryOverrideValidator,
    private val prefs: SharedPreferences,
    private val defaultPrefs: SharedPreferences,
    private val baseDirectory: File,
    private val streamOpener: (Uri) -> InputStream?,
    private val nameResolver: (Uri) -> String?,
) {
    private val gson = Gson()

    @Inject
    constructor(
        @ApplicationContext context: Context,
        validator: DictionaryOverrideValidator,
    ) : this(
        validator = validator,
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
        defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context),
        baseDirectory = File(context.filesDir, DIRECTORY_NAME),
        streamOpener = { uri -> context.contentResolver.openInputStream(uri) },
        nameResolver = { uri -> resolveDisplayName(context, uri) },
    )

    constructor(
        directory: File,
        prefs: SharedPreferences,
        defaultPrefs: SharedPreferences,
        validator: DictionaryOverrideValidator = DictionaryOverrideValidator(),
        streamOpener: (Uri) -> InputStream? = { null },
        nameResolver: (Uri) -> String? = { it.lastPathSegment },
    ) : this(
        validator = validator,
        prefs = prefs,
        defaultPrefs = defaultPrefs,
        baseDirectory = directory,
        streamOpener = streamOpener,
        nameResolver = nameResolver,
    )

    val directory: File
        get() = baseDirectory

    init {
        OptionalDictionaryMigration(defaultPrefs, prefs).migrateIfNeeded()
    }

    fun hasOverride(key: DictionaryFileKey): Boolean = overrideFile(key).exists()

    fun openOverride(key: DictionaryFileKey): InputStream = FileInputStream(overrideFile(key))

    fun saveOverrideFromUri(key: DictionaryFileKey, uri: Uri): ValidationResult {
        val originalFileName = nameResolver(uri) ?: key.name
        val inputStream = streamOpener(uri)
            ?: return ValidationResult.invalid("Could not open selected file")
        return inputStream.use { input ->
            saveOverrideFromInputStream(key, input, originalFileName)
        }
    }

    fun saveOverrideFromInputStream(
        key: DictionaryFileKey,
        inputStream: InputStream,
        originalFileName: String = key.name,
    ): ValidationResult {
        ensureDirectory()
        val spec = DictionaryFileSpecs.get(key)
        val tempFile = File(directory, "${key.name}.tmp")
        runCatching {
            FileOutputStream(tempFile).use { output -> inputStream.copyTo(output) }
        }.getOrElse { error ->
            tempFile.delete()
            return ValidationResult.invalid(error.message ?: error::class.java.simpleName)
        }

        val validationResult = validator.validate(tempFile, spec)
        if (!validationResult.isValid) {
            tempFile.delete()
            return validationResult
        }

        val target = overrideFile(key)
        if (target.exists()) target.delete()
        if (!tempFile.renameTo(target)) {
            tempFile.delete()
            return ValidationResult.invalid("Could not save override file")
        }

        val metadata = DictionaryOverrideMetadata(
            key = key,
            category = spec.category,
            originalFileName = originalFileName,
            importedAt = System.currentTimeMillis(),
            size = target.length(),
            contentType = spec.contentType,
            validationStatus = validationResult.status,
            validationMessage = validationResult.message,
        )
        saveMetadata(metadata)
        if (spec.partOfTripleDictionary) {
            if (isTripleValid(spec.category)) {
                setExternalEnabledForCategory(spec.category, true)
            }
        } else {
            setExternalEnabledForKey(key, true)
        }
        return validationResult
    }

    fun removeOverride(key: DictionaryFileKey) {
        overrideFile(key).delete()
        removeMetadata(key)
        setExternalEnabledForKey(key, false)
        val category = DictionaryFileSpecs.get(key).category
        if (DictionaryFileSpecs.forCategory(category).all { it.partOfTripleDictionary }) {
            setExternalEnabledForCategory(category, false)
        }
    }

    fun removeAllOverrides() {
        if (directory.exists()) {
            directory.deleteRecursively()
        }
        prefs.edit()
            .clear()
            .apply()
        OptionalDictionaryMigration(defaultPrefs, prefs).migrateIfNeeded(force = true)
    }

    fun getOverrideMetadata(key: DictionaryFileKey): DictionaryOverrideMetadata? {
        val json = prefs.getString(metadataPrefKey(key), null) ?: return null
        return runCatching {
            gson.fromJson<DictionaryOverrideMetadata>(json, metadataType)
        }.getOrNull()
    }

    fun listAllOverrideStates(): List<DictionaryOverrideState> =
        DictionaryFileSpecs.all.map { spec ->
            DictionaryOverrideState(
                spec = spec,
                hasOverride = hasOverride(spec.key),
                metadata = getOverrideMetadata(spec.key),
                externalEnabled = if (spec.partOfTripleDictionary) {
                    isExternalEnabledForCategory(spec.category)
                } else {
                    isExternalEnabledForKey(spec.key)
                },
            )
        }

    fun setExternalEnabledForCategory(category: DictionaryCategory, enabled: Boolean) {
        prefs.edit().putBoolean(categoryExternalEnabledKey(category), enabled).apply()
    }

    fun isExternalEnabledForCategory(category: DictionaryCategory): Boolean =
        prefs.getBoolean(categoryExternalEnabledKey(category), false)

    fun setExternalEnabledForKey(key: DictionaryFileKey, enabled: Boolean) {
        prefs.edit().putBoolean(keyExternalEnabledKey(key), enabled).apply()
    }

    fun isExternalEnabledForKey(key: DictionaryFileKey): Boolean =
        prefs.getBoolean(keyExternalEnabledKey(key), false)

    fun isOptionalBundledEnabled(category: DictionaryCategory): Boolean =
        prefs.getBoolean(optionalBundledEnabledKey(category), false)

    fun setOptionalBundledEnabled(category: DictionaryCategory, enabled: Boolean) {
        prefs.edit().putBoolean(optionalBundledEnabledKey(category), enabled).apply()
    }

    fun isValidOverride(key: DictionaryFileKey): Boolean {
        val metadata = getOverrideMetadata(key) ?: return false
        return hasOverride(key) && metadata.validationStatus == ValidationStatus.VALID
    }

    fun markInvalid(key: DictionaryFileKey, message: String) {
        val metadata = getOverrideMetadata(key) ?: return
        saveMetadata(
            metadata.copy(
                validationStatus = ValidationStatus.INVALID,
                validationMessage = message,
            )
        )
    }

    private fun isTripleValid(category: DictionaryCategory): Boolean =
        DictionaryFileSpecs.forCategory(category).all { isValidOverride(it.key) }

    private fun saveMetadata(metadata: DictionaryOverrideMetadata) {
        prefs.edit()
            .putString(metadataPrefKey(metadata.key), gson.toJson(metadata))
            .apply()
    }

    private fun removeMetadata(key: DictionaryFileKey) {
        prefs.edit().remove(metadataPrefKey(key)).apply()
    }

    private fun ensureDirectory() {
        if (!directory.exists()) directory.mkdirs()
    }

    private fun overrideFile(key: DictionaryFileKey): File = File(directory, "${key.name}.bin")

    companion object {
        private const val PREF_NAME = "dictionary_override_store"
        private const val DIRECTORY_NAME = "dictionary_overrides"
        private val metadataType = object : TypeToken<DictionaryOverrideMetadata>() {}.type

        fun metadataPrefKey(key: DictionaryFileKey) = "metadata_${key.name}"
        fun keyExternalEnabledKey(key: DictionaryFileKey) = "external_enabled_key_${key.name}"
        fun categoryExternalEnabledKey(category: DictionaryCategory) =
            "external_enabled_category_${category.name}"
        fun optionalBundledEnabledKey(category: DictionaryCategory) =
            "optional_bundled_enabled_${category.name}"

        private fun resolveDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else {
                        null
                    }
                }
        }.getOrNull()
        }
    }
}

class OptionalDictionaryMigration(
    private val defaultPrefs: SharedPreferences,
    private val overridePrefs: SharedPreferences,
) {
    fun migrateIfNeeded(force: Boolean = false) {
        if (!force && overridePrefs.getBoolean(MIGRATION_DONE_KEY, false)) return

        val editor = overridePrefs.edit()
        LEGACY_KEYS.forEach { (category, legacyKey) ->
            val enabled = defaultPrefs.getBoolean(legacyKey, false)
            editor.putBoolean(DictionaryOverrideStore.optionalBundledEnabledKey(category), enabled)
        }
        editor.putBoolean(MIGRATION_DONE_KEY, true).apply()
    }

    companion object {
        private const val MIGRATION_DONE_KEY = "optional_dictionary_migration_done_v1"
        private val LEGACY_KEYS = mapOf(
            DictionaryCategory.PERSON_NAME to "mozc_ut_person_name_preference",
            DictionaryCategory.PLACES to "mozc_ut_places_preference",
            DictionaryCategory.WIKI to "mozc_ut_wiki_preference",
            DictionaryCategory.NEOLOGD to "mozc_ut_neologd_preference",
            DictionaryCategory.WEB to "mozc_ut_web_preference",
        )
    }
}
