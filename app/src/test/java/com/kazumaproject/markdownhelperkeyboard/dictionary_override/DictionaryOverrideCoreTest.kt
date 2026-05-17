package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
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
        assertEquals(DictionaryCategoryLoadState.Missing, resolver.state(DictionaryCategory.COMMON))
        assertEquals(DictionaryCategoryLoadState.Bundled, resolver.state(DictionaryCategory.SYSTEM))
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
}

private class FakeResolver(private val store: DictionaryOverrideStore) {
    fun state(category: DictionaryCategory): DictionaryCategoryLoadState {
        if (category == DictionaryCategory.COMMON) return DictionaryCategoryLoadState.Missing
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
