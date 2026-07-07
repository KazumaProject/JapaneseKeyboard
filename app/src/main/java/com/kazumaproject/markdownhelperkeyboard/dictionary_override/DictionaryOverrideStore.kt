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

    val currentRevision: Long
        get() = prefs.getLong(REVISION_PREF_KEY, 0L)

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

    fun importOverridesFromZipUri(context: Context, zipUri: Uri): DictionaryZipImportResult =
        ExternalDictionaryZipImporter(context, this).importFromUri(zipUri)

    fun saveOverrideFromZipEntryInputStream(
        key: DictionaryFileKey,
        inputStream: InputStream,
        entryName: String,
    ): ValidationResult =
        saveOverrideFromInputStream(
            key = key,
            inputStream = inputStream,
            originalFileName = entryName,
        )

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
        val autoEnableTripleCategory = spec.partOfTripleDictionary &&
            DictionaryFileSpecs.forCategory(spec.category).all { it.key == key || isValidOverride(it.key) }
        val enableExternal = !spec.category.isDisableableBundledDictionary() ||
            isOptionalBundledEnabled(spec.category)
        applyRevisionedEdit {
            putMetadata(metadata)
            if (autoEnableTripleCategory) {
                putBoolean(categoryExternalEnabledKey(spec.category), enableExternal)
            } else if (!spec.partOfTripleDictionary) {
                putBoolean(keyExternalEnabledKey(key), true)
            }
            true
        }
        return validationResult
    }

    fun removeOverride(key: DictionaryFileKey) {
        val fileChanged = overrideFile(key).delete()
        val metadataChanged = getOverrideMetadata(key) != null
        val category = DictionaryFileSpecs.get(key).category
        val keyEnabledPref = keyExternalEnabledKey(key)
        val categoryEnabledPref = categoryExternalEnabledKey(category)
        val removeCategoryEnabled =
            DictionaryFileSpecs.forCategory(category).all { it.partOfTripleDictionary }
        applyRevisionedEdit {
            remove(metadataPrefKey(key))
            remove(keyEnabledPref)
            if (removeCategoryEnabled) remove(categoryEnabledPref)
            fileChanged ||
                metadataChanged ||
                prefs.contains(keyEnabledPref) ||
                (removeCategoryEnabled && prefs.contains(categoryEnabledPref))
        }
    }

    fun removeAllOverrides() {
        val fileChanged = directory.exists() && directory.deleteRecursively()
        val stateChanged = prefs.all.any { (key, value) -> isResetAllStateChange(key, value) }
        if (fileChanged || stateChanged) {
            synchronized(this) {
                val nextRevision = currentRevision + 1L
                prefs.edit()
                    .clear()
                    .putLong(REVISION_PREF_KEY, nextRevision)
                    .apply()
            }
        }
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
        putBooleanIfChanged(categoryExternalEnabledKey(category), enabled)
    }

    fun isExternalEnabledForCategory(category: DictionaryCategory): Boolean =
        prefs.getBoolean(categoryExternalEnabledKey(category), false)

    fun setExternalEnabledForKey(key: DictionaryFileKey, enabled: Boolean) {
        putBooleanIfChanged(keyExternalEnabledKey(key), enabled)
    }

    fun isExternalEnabledForKey(key: DictionaryFileKey): Boolean =
        prefs.getBoolean(keyExternalEnabledKey(key), false)

    fun isOptionalBundledEnabled(category: DictionaryCategory): Boolean =
        prefs.getBoolean(
            optionalBundledEnabledKey(category),
            category in setOf(DictionaryCategory.READING_CORRECTION, DictionaryCategory.KOTOWAZA),
        )

    fun setOptionalBundledEnabled(category: DictionaryCategory, enabled: Boolean) {
        if (isOptionalBundledEnabled(category) == enabled) return
        applyRevisionedEdit {
            putBoolean(optionalBundledEnabledKey(category), enabled)
            true
        }
    }

    fun isValidOverride(key: DictionaryFileKey): Boolean {
        val metadata = getOverrideMetadata(key) ?: return false
        return hasOverride(key) && metadata.validationStatus == ValidationStatus.VALID
    }

    fun markInvalid(key: DictionaryFileKey, message: String) {
        val metadata = getOverrideMetadata(key) ?: return
        if (metadata.validationStatus == ValidationStatus.INVALID && metadata.validationMessage == message) return
        applyRevisionedEdit {
            putMetadata(
                metadata.copy(
                    validationStatus = ValidationStatus.INVALID,
                    validationMessage = message,
                )
            )
            true
        }
    }

    private fun SharedPreferences.Editor.putMetadata(metadata: DictionaryOverrideMetadata) {
        putString(metadataPrefKey(metadata.key), gson.toJson(metadata))
    }

    private fun putBooleanIfChanged(key: String, enabled: Boolean) {
        if (prefs.getBoolean(key, false) == enabled && prefs.contains(key)) return
        if (!enabled && !prefs.contains(key)) return
        applyRevisionedEdit {
            putBoolean(key, enabled)
            true
        }
    }

    private fun isResetAllStateChange(key: String, value: Any?): Boolean {
        if (key == REVISION_PREF_KEY || key == OptionalDictionaryMigration.MIGRATION_DONE_KEY) {
            return false
        }
        if (key.startsWith("metadata_") || key.startsWith("external_enabled_")) {
            return true
        }
        if (key.startsWith("optional_bundled_enabled_")) {
            val categoryName = key.removePrefix("optional_bundled_enabled_")
            val category = runCatching { DictionaryCategory.valueOf(categoryName) }.getOrNull()
                ?: return true
            return value as? Boolean != optionalBundledResetValue(category)
        }
        return true
    }

    private fun optionalBundledResetValue(category: DictionaryCategory): Boolean =
        when (category) {
            DictionaryCategory.READING_CORRECTION,
            DictionaryCategory.KOTOWAZA -> true
            DictionaryCategory.PERSON_NAME ->
                defaultPrefs.getBoolean("mozc_ut_person_name_preference", false)
            DictionaryCategory.PLACES ->
                defaultPrefs.getBoolean("mozc_ut_places_preference", false)
            DictionaryCategory.WIKI ->
                defaultPrefs.getBoolean("mozc_ut_wiki_preference", false)
            DictionaryCategory.NEOLOGD ->
                defaultPrefs.getBoolean("mozc_ut_neologd_preference", false)
            DictionaryCategory.WEB ->
                defaultPrefs.getBoolean("mozc_ut_web_preference", false)
            else -> false
        }

    private inline fun applyRevisionedEdit(
        edit: SharedPreferences.Editor.() -> Boolean,
    ): Boolean {
        synchronized(this) {
            val editor = prefs.edit()
            val changed = edit(editor)
            if (changed) {
                editor.putLong(REVISION_PREF_KEY, currentRevision + 1L)
            }
            editor.apply()
            return changed
        }
    }

    private fun ensureDirectory() {
        if (!directory.exists()) directory.mkdirs()
    }

    private fun overrideFile(key: DictionaryFileKey): File = File(directory, savedFileNameForKey(key))

    companion object {
        private const val PREF_NAME = "dictionary_override_store"
        private const val DIRECTORY_NAME = "dictionary_overrides"
        const val REVISION_PREF_KEY = "dictionary_override_revision"
        private val metadataType = object : TypeToken<DictionaryOverrideMetadata>() {}.type

        fun metadataPrefKey(key: DictionaryFileKey) = "metadata_${key.name}"
        fun savedFileNameForKey(key: DictionaryFileKey) = "${key.name}.bin"
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
        const val MIGRATION_DONE_KEY = "optional_dictionary_migration_done_v1"
        private val LEGACY_KEYS = mapOf(
            DictionaryCategory.PERSON_NAME to "mozc_ut_person_name_preference",
            DictionaryCategory.PLACES to "mozc_ut_places_preference",
            DictionaryCategory.WIKI to "mozc_ut_wiki_preference",
            DictionaryCategory.NEOLOGD to "mozc_ut_neologd_preference",
            DictionaryCategory.WEB to "mozc_ut_web_preference",
        )
    }
}
