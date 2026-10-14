package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.preference.PreferenceManager
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.classifiedCandidateLabel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.ByteArrayInputStream
import org.mockito.kotlin.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClassifiedDictionaryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun allCategoriesHaveThreeDistinctPathMappedFiles() {
        assertEquals(28, ClassifiedDictionaries.all.size)
        assertEquals(28, ClassifiedDictionaries.categories.size)
        ClassifiedDictionaries.all.forEach { spec ->
            val files = DictionaryFileSpecs.forCategory(spec.category)
            assertEquals(3, files.size)
            files.forEach {
                assertEquals(it.key, DictionaryZipEntryNameMapper.map("app/src/main/assets/${it.bundledAssetPath}"))
                assertEquals(it.key, DictionaryZipEntryNameMapper.map(it.bundledAssetPath.replace('/', '\\')))
            }
            assertTrue(context.getString(spec.nameRes).isNotBlank())
            assertTrue(context.assets.open(spec.licenseAssetPath).bufferedReader().use { it.readText() }.contains("v1.7.262-pre.1"))
        }
        assertNull(DictionaryZipEntryNameMapper.map("categories/unknown/tango.dat.zip"))
        assertNull(DictionaryZipEntryNameMapper.map("categories/unknown/system/tango.dat.zip"))
        assertNull(DictionaryZipEntryNameMapper.map("categories/person/token.dat"))
        assertNull(DictionaryZipEntryNameMapper.map("tango_person_names.dat"))
    }

    @Test fun categoryNamesAreAvailableInEnglishAndJapanese() {
        fun localized(tag: String) = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(tag)) })
        val english = localized("en")
        val japanese = localized("ja")
        ClassifiedDictionaries.all.forEach {
            assertTrue(english.getString(it.nameRes).isNotBlank())
            assertTrue(japanese.getString(it.nameRes).isNotBlank())
            assertNotEquals(english.getString(it.nameRes), japanese.getString(it.nameRes))
        }
    }

    @Test fun labelsAreOrderedAndDoNotDependOnCandidateType() {
        assertEquals("[人名・地名]", classifiedCandidateLabel(context, setOf(DictionaryCategory.CLASSIFIED_PLACE, DictionaryCategory.CLASSIFIED_PERSON)))
        assertEquals("", classifiedCandidateLabel(context, emptySet()))
    }

    @Test fun legacyEnabledFlagsDoNotEnableAnyClassifiedCategory() {
        val prefs = context.getSharedPreferences("classified-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val legacy = PreferenceManager.getDefaultSharedPreferences(context)
        legacy.edit().putBoolean("mozc_ut_person_name_preference", true).putBoolean("mozc_ut_places_preference", true).commit()
        val store = DictionaryOverrideStore(File(context.cacheDir, "classified-test"), prefs, legacy)
        ClassifiedDictionaries.all.forEach { assertFalse(store.isOptionalBundledEnabled(it.category)) }
        val first = ClassifiedDictionaries.all.first().category
        store.setOptionalBundledEnabled(first, true)
        assertTrue(store.isOptionalBundledEnabled(first))
        ClassifiedDictionaries.all.drop(1).forEach { assertFalse(store.isOptionalBundledEnabled(it.category)) }
        store.setOptionalBundledEnabled(first, false)
        assertFalse(store.isOptionalBundledEnabled(first))
    }

    @Test fun literalRepairOnlyAppliesToThePinnedDataOrIdenticalImports() {
        val resolver = mock<DictionarySourceResolver>()
        whenever(resolver.openBundledAsset("categories/surface-overrides.json")).thenAnswer {
            context.assets.open("categories/surface-overrides.json")
        }
        val reader = DictionaryBinaryReader(resolver, mock())
        val category = DictionaryCategory.CLASSIFIED_PLACE
        assertEquals("アトコシヤラカ", reader.loadClassifiedSurfaceOverrides(category)["-1\tあとこしゃらか\t1851\t1851\t9450"])
        whenever(resolver.shouldUseOverrideCategory(category)).thenReturn(true)
        whenever(resolver.openBundledForKey(any())).thenAnswer { ByteArrayInputStream(byteArrayOf(1)) }
        whenever(resolver.openOverrideForKey(any())).thenAnswer { ByteArrayInputStream(byteArrayOf(2)) }
        assertTrue(reader.loadClassifiedSurfaceOverrides(category).isEmpty())
        whenever(resolver.openOverrideForKey(any())).thenAnswer { ByteArrayInputStream(byteArrayOf(1)) }
        assertEquals(1, reader.loadClassifiedSurfaceOverrides(category).size)
    }

    @Test fun unknownCategoryCannotCollideWithSystemAndDuplicatesAreNotImported() {
        val plan = DictionaryZipEntryPlanner.plan(listOf("categories/person/tango.dat.zip", "wrapper/categories/person/tango.dat.zip", "categories/unknown/tango.dat.zip", "system/tango.dat.zip"))
        assertEquals(listOf(DictionaryFileKey.CLASSIFIED_PERSON_TANGO), plan.duplicateKeys.map { it.key })
        assertEquals(listOf(DictionaryFileKey.SYSTEM_TANGO), plan.importableEntries.map { it.key })
    }
}
