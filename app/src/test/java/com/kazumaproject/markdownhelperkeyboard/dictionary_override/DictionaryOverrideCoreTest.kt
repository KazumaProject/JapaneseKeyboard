package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import android.content.SharedPreferences
import com.google.gson.Gson
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.external_dictionary.CORE_REPLACEMENT_CATEGORIES
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.external_dictionary.COMMON_REPLACEMENT_KEYS
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.external_dictionary.ExternalDictionaryDisplayState
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.external_dictionary.ExternalDictionaryDisplayStateResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DictionaryOverrideCoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun zipAwareRawInput_doesNotTreatRawDatAsZip() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val read = DictionaryBinaryReader.openZipAwareRaw(ByteArrayInputStream(bytes), "raw").readBytes()
        assertTrue(bytes.contentEquals(read))
    }

    @Test
    fun zipAwareRawInput_detectsZipHeaderAndReturnsEntryStream() {
        val zipped = zip("entry.dat", "payload".toByteArray())
        val read = DictionaryBinaryReader.openZipAwareRaw(ByteArrayInputStream(zipped), "zip").readBytes()
        assertEquals("payload", read.toString(Charsets.UTF_8))
    }

    @Test
    fun zipAwareTextReader_readsRawAndZippedIdDefText() {
        val raw = "1 名詞\n2 動詞\n".toByteArray()
        val zipped = zip("id.def", raw)

        val rawText = DictionaryBinaryReader.openZipAwareText(ByteArrayInputStream(raw), "raw-id").readText()
        val zippedText = DictionaryBinaryReader.openZipAwareText(ByteArrayInputStream(zipped), "zip-id").readText()

        assertEquals("1 名詞\n2 動詞\n", rawText)
        assertEquals(rawText, zippedText)
    }

    @Test
    fun store_savesRemovesMetadataAndEnabledStateForIdDef() {
        val prefs = FakeSharedPreferences()
        val store = DictionaryOverrideStore(
            directory = temp.newFolder("overrides"),
            prefs = prefs,
            defaultPrefs = FakeSharedPreferences(),
        )

        val result = store.saveOverrideFromInputStream(
            DictionaryFileKey.ID_DEF,
            ByteArrayInputStream("1 名詞\n".toByteArray()),
            "id.def",
        )

        assertTrue(result.isValid)
        assertTrue(store.hasOverride(DictionaryFileKey.ID_DEF))
        assertTrue(store.isExternalEnabledForKey(DictionaryFileKey.ID_DEF))
        assertEquals("id.def", store.getOverrideMetadata(DictionaryFileKey.ID_DEF)?.originalFileName)

        store.setExternalEnabledForKey(DictionaryFileKey.ID_DEF, false)
        assertFalse(store.isExternalEnabledForKey(DictionaryFileKey.ID_DEF))
        assertTrue(store.hasOverride(DictionaryFileKey.ID_DEF))

        store.removeOverride(DictionaryFileKey.ID_DEF)
        assertFalse(store.hasOverride(DictionaryFileKey.ID_DEF))
        assertFalse(store.isExternalEnabledForKey(DictionaryFileKey.ID_DEF))
    }

    @Test
    fun tripleCategory_isPartialUntilAllFilesAreValidAndEnabled() {
        val prefs = FakeSharedPreferences()
        val store = DictionaryOverrideStore(
            directory = temp.newFolder("triple"),
            prefs = prefs,
            defaultPrefs = FakeSharedPreferences(),
        )
        val resolver = FakeResolver(store)

        store.setExternalEnabledForCategory(DictionaryCategory.SYSTEM, true)
        assertEquals(DictionaryCategoryLoadState.Bundled, resolver.state(DictionaryCategory.COMMON))
        assertEquals(DictionaryCategoryLoadState.Bundled, resolver.state(DictionaryCategory.SYSTEM))
    }

    @Test
    fun displayState_metadataNullIsBundledInUseNotMissingForCommonFiles() {
        val store = DictionaryOverrideStore(
            directory = temp.newFolder("common-no-overrides"),
            prefs = FakeSharedPreferences(),
            defaultPrefs = FakeSharedPreferences(),
        )
        val resolver = ExternalDictionaryDisplayStateResolver(store)

        COMMON_REPLACEMENT_KEYS.forEach { key ->
            val state = resolver.resolveFileDisplayState(key)
            assertEquals(ExternalDictionaryDisplayState.BundledInUseNoOverride, state.displayState)
            assertFalse(state.switchEnabled)
            assertFalse(state.switchChecked)
        }
    }

    @Test
    fun displayState_commonValidOverrideUsesSwitchAsReplacementChoice() {
        val store = DictionaryOverrideStore(
            directory = temp.newFolder("common-valid"),
            prefs = FakeSharedPreferences(),
            defaultPrefs = FakeSharedPreferences(),
        )
        store.saveOverrideFromInputStream(
            DictionaryFileKey.ID_DEF,
            ByteArrayInputStream("1 名詞\n".toByteArray()),
            "id.def",
        )

        store.setExternalEnabledForKey(DictionaryFileKey.ID_DEF, false)
        val bundledState = ExternalDictionaryDisplayStateResolver(store)
            .resolveFileDisplayState(DictionaryFileKey.ID_DEF)
        assertEquals(ExternalDictionaryDisplayState.BundledInUseWithValidOverride, bundledState.displayState)
        assertTrue(bundledState.switchEnabled)
        assertFalse(bundledState.switchChecked)

        store.setExternalEnabledForKey(DictionaryFileKey.ID_DEF, true)
        val externalState = ExternalDictionaryDisplayStateResolver(store)
            .resolveFileDisplayState(DictionaryFileKey.ID_DEF)
        assertEquals(ExternalDictionaryDisplayState.ExternalInUse, externalState.displayState)
        assertTrue(externalState.switchEnabled)
        assertTrue(externalState.switchChecked)
    }

    @Test
    fun displayState_invalidCommonOverrideFallsBackToBundledAndDisablesSwitch() {
        val prefs = FakeSharedPreferences()
        val directory = temp.newFolder("common-invalid")
        addMetadataOverride(directory, prefs, DictionaryFileKey.POS_TABLE, ValidationStatus.INVALID)
        val store = DictionaryOverrideStore(
            directory = directory,
            prefs = prefs,
            defaultPrefs = FakeSharedPreferences(),
        )
        store.setExternalEnabledForKey(DictionaryFileKey.POS_TABLE, true)

        val state = ExternalDictionaryDisplayStateResolver(store)
            .resolveFileDisplayState(DictionaryFileKey.POS_TABLE)

        assertEquals(ExternalDictionaryDisplayState.InvalidOverrideBundledFallback, state.displayState)
        assertFalse(state.switchEnabled)
        assertFalse(state.switchChecked)
    }

    @Test
    fun displayState_coreCategoriesUseBundledWhenNoExternalOverrideExists() {
        val store = DictionaryOverrideStore(
            directory = temp.newFolder("core-no-overrides"),
            prefs = FakeSharedPreferences(),
            defaultPrefs = FakeSharedPreferences(),
        )
        val resolver = ExternalDictionaryDisplayStateResolver(store)

        CORE_REPLACEMENT_CATEGORIES.forEach { category ->
            val state = resolver.resolveCategoryDisplayState(category)
            assertEquals(ExternalDictionaryDisplayState.BundledInUseNoOverride, state.displayState)
            assertFalse(state.switchEnabled)
            assertFalse(state.switchChecked)
        }
    }

    @Test
    fun displayState_systemCategoryRequiresAllThreeValidFilesBeforeSwitchIsEnabled() {
        val prefs = FakeSharedPreferences()
        val directory = temp.newFolder("system-partial")
        addMetadataOverride(directory, prefs, DictionaryFileKey.SYSTEM_TANGO, ValidationStatus.VALID)
        val store = DictionaryOverrideStore(
            directory = directory,
            prefs = prefs,
            defaultPrefs = FakeSharedPreferences(),
        )
        store.setExternalEnabledForCategory(DictionaryCategory.SYSTEM, true)

        val state = ExternalDictionaryDisplayStateResolver(store)
            .resolveCategoryDisplayState(DictionaryCategory.SYSTEM)

        assertEquals(ExternalDictionaryDisplayState.PartialOverrideBundledFallback, state.displayState)
        assertFalse(state.switchEnabled)
        assertFalse(state.switchChecked)
    }

    @Test
    fun displayState_systemCategoryUsesSwitchAsReplacementChoiceWhenAllFilesAreValid() {
        val prefs = FakeSharedPreferences()
        val directory = temp.newFolder("system-valid")
        listOf(
            DictionaryFileKey.SYSTEM_TANGO,
            DictionaryFileKey.SYSTEM_YOMI,
            DictionaryFileKey.SYSTEM_TOKEN,
        ).forEach { key ->
            addMetadataOverride(directory, prefs, key, ValidationStatus.VALID)
        }
        val store = DictionaryOverrideStore(
            directory = directory,
            prefs = prefs,
            defaultPrefs = FakeSharedPreferences(),
        )

        store.setExternalEnabledForCategory(DictionaryCategory.SYSTEM, false)
        val bundledState = ExternalDictionaryDisplayStateResolver(store)
            .resolveCategoryDisplayState(DictionaryCategory.SYSTEM)
        assertEquals(ExternalDictionaryDisplayState.BundledInUseWithValidOverride, bundledState.displayState)
        assertTrue(bundledState.switchEnabled)
        assertFalse(bundledState.switchChecked)

        store.setExternalEnabledForCategory(DictionaryCategory.SYSTEM, true)
        val externalState = ExternalDictionaryDisplayStateResolver(store)
            .resolveCategoryDisplayState(DictionaryCategory.SYSTEM)
        assertEquals(ExternalDictionaryDisplayState.ExternalInUse, externalState.displayState)
        assertTrue(externalState.switchEnabled)
        assertTrue(externalState.switchChecked)

        val fileState = ExternalDictionaryDisplayStateResolver(store)
            .resolveFileDisplayState(DictionaryFileSpecs.get(DictionaryFileKey.SYSTEM_TOKEN))
        assertEquals(ExternalDictionaryDisplayState.ExternalInUse, fileState)
    }

    @Test
    fun coreReplacementScopeDoesNotIncludeReadingCorrectionOrKotowaza() {
        assertEquals(
            listOf(
                DictionaryCategory.SYSTEM,
                DictionaryCategory.SINGLE_KANJI,
                DictionaryCategory.EMOJI,
                DictionaryCategory.EMOTICON,
                DictionaryCategory.SYMBOL,
                DictionaryCategory.ENGLISH,
            ),
            CORE_REPLACEMENT_CATEGORIES,
        )
        assertFalse(CORE_REPLACEMENT_CATEGORIES.contains(DictionaryCategory.READING_CORRECTION))
        assertFalse(CORE_REPLACEMENT_CATEGORIES.contains(DictionaryCategory.KOTOWAZA))
    }

    @Test
    fun japaneseResourcesContainAllExternalDictionaryStrings() {
        val base = resourceFile("values/strings.xml").readText()
        val japanese = resourceFile("values-ja/strings.xml").readText()
        val keyRegex = Regex("""<string name="(external_dictionary_[^"]+)"""")
        val baseKeys = keyRegex.findAll(base).map { it.groupValues[1] }.toSet()
        val japaneseKeys = keyRegex.findAll(japanese).map { it.groupValues[1] }.toSet()

        assertEquals(emptySet<String>(), baseKeys - japaneseKeys)
    }

    @Test
    fun optionalMigration_preservesLegacyOnOffValues() {
        val oldPrefs = FakeSharedPreferences(
            mutableMapOf(
                "mozc_ut_person_name_preference" to true,
                "mozc_ut_places_preference" to false,
            )
        )
        val newPrefs = FakeSharedPreferences()

        OptionalDictionaryMigration(oldPrefs, newPrefs).migrateIfNeeded()

        assertTrue(newPrefs.getBoolean(DictionaryOverrideStore.optionalBundledEnabledKey(DictionaryCategory.PERSON_NAME), false))
        assertFalse(newPrefs.getBoolean(DictionaryOverrideStore.optionalBundledEnabledKey(DictionaryCategory.PLACES), true))
    }

    private fun zip(name: String, bytes: ByteArray): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun addMetadataOverride(
        directory: File,
        prefs: SharedPreferences,
        key: DictionaryFileKey,
        status: ValidationStatus,
    ) {
        val spec = DictionaryFileSpecs.get(key)
        if (!directory.exists()) directory.mkdirs()
        File(directory, "${key.name}.bin").writeBytes(byteArrayOf(1, 2, 3))
        val metadata = DictionaryOverrideMetadata(
            key = key,
            category = spec.category,
            originalFileName = key.name,
            importedAt = 1_700_000_000_000,
            size = 3,
            contentType = spec.contentType,
            validationStatus = status,
            validationMessage = status.name,
        )
        prefs.edit()
            .putString(DictionaryOverrideStore.metadataPrefKey(key), Gson().toJson(metadata))
            .apply()
    }

    private fun resourceFile(path: String): File {
        val candidates = listOf(
            File("src/main/res/$path"),
            File("app/src/main/res/$path"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Resource file not found: $path")
    }
}

private class FakeResolver(private val store: DictionaryOverrideStore) {
    fun state(category: DictionaryCategory): DictionaryCategoryLoadState {
        if (category == DictionaryCategory.COMMON) return DictionaryCategoryLoadState.Bundled
        val specs = DictionaryFileSpecs.forCategory(category)
        val hasAny = specs.any { store.hasOverride(it.key) }
        val hasAll = specs.all { store.hasOverride(it.key) }
        val validAll = specs.all { store.isValidOverride(it.key) }
        if (hasAny && (!hasAll || !validAll)) return DictionaryCategoryLoadState.Partial
        if (validAll && store.isExternalEnabledForCategory(category)) return DictionaryCategoryLoadState.User
        return DictionaryCategoryLoadState.Bundled
    }
}

private class FakeSharedPreferences(
    private val values: MutableMap<String, Any?> = mutableMapOf(),
) : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor(values)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private class Editor(private val values: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private var clear = false
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { pending[key] = value }
        override fun remove(key: String): SharedPreferences.Editor = apply { removals.add(key) }
        override fun clear(): SharedPreferences.Editor = apply { clear = true }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clear) values.clear()
            removals.forEach(values::remove)
            pending.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
        }
    }
}
