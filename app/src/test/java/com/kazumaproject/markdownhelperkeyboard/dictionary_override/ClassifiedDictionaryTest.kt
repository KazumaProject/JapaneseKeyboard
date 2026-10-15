package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.preference.PreferenceManager
import com.google.gson.JsonParser
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
        val manifest = File("../tools/classified-dictionaries/distribution-manifest.json")
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val distributionRevision = manifest.get("revision").asString
        assertTrue("Distribution revision must be a commit SHA", distributionRevision.matches(Regex("[0-9a-f]{40}")))
        assertEquals(30, ClassifiedDictionaries.all.size)
        assertEquals(30, ClassifiedDictionaries.categories.size)
        ClassifiedDictionaries.all.forEach { spec ->
            val files = DictionaryFileSpecs.forCategory(spec.category)
            assertEquals(3, files.size)
            files.forEach {
                assertEquals(it.key, DictionaryZipEntryNameMapper.map("app/src/main/assets/${it.bundledAssetPath}"))
                assertEquals(it.key, DictionaryZipEntryNameMapper.map(it.bundledAssetPath.replace('/', '\\')))
            }
            assertTrue(context.getString(spec.nameRes).isNotBlank())
            val notice = context.assets.open(spec.licenseAssetPath).bufferedReader().use { it.readText() }
            assertTrue("${spec.id} notice must match the distribution manifest revision",
                notice.contains("($distributionRevision)"))
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

    @Test fun allCategoriesDefaultDisabledWithoutMigratingLegacyFlags() {
        val prefs = context.getSharedPreferences("classified-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val legacy = PreferenceManager.getDefaultSharedPreferences(context)
        legacy.edit().putBoolean("mozc_ut_person_name_preference", true).putBoolean("mozc_ut_places_preference", true).commit()
        val store = DictionaryOverrideStore(File(context.cacheDir, "classified-test"), prefs, legacy)
        ClassifiedDictionaries.all.forEach { assertFalse(store.isOptionalBundledEnabled(it.category)) }
        val first = ClassifiedDictionaries.all.first().category
        val revision = store.currentRevision
        store.setDisableableCategoryEnabled(first, true, true)
        assertTrue(store.isOptionalBundledEnabled(first))
        assertTrue(store.currentRevision > revision)
        ClassifiedDictionaries.all.drop(1).forEach { assertFalse(store.isOptionalBundledEnabled(it.category)) }
        // A stored ON is a choice, not a value to migrate or overwrite.
        val reopened = DictionaryOverrideStore(File(context.cacheDir, "classified-test"), prefs, legacy)
        assertTrue(reopened.isOptionalBundledEnabled(first))
        reopened.removeAllOverrides()
        ClassifiedDictionaries.all.forEach { assertFalse(reopened.isOptionalBundledEnabled(it.category)) }

    }

    @Test fun dictionarySettingsAndSearchExposeEveryNonPersistentSwitch() {
        val parser = context.resources.getXml(com.kazumaproject.markdownhelperkeyboard.R.xml.pref_dictionary)
        val keys = mutableSetOf<String>()
        parser.use {
            while (it.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (it.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && it.name == "SwitchPreferenceCompat") {
                    val key = it.getAttributeValue("http://schemas.android.com/apk/res/android", "key")
                    if (key.startsWith("optional_bundled_enabled_CLASSIFIED_")) {
                        assertFalse(it.getAttributeBooleanValue("http://schemas.android.com/apk/res/android", "persistent", true))
                        assertFalse(it.getAttributeBooleanValue("http://schemas.android.com/apk/res/android", "defaultValue", true))
                        keys.add(key)
                    }
                }
                it.next()
            }
        }
        assertEquals(ClassifiedDictionaries.all.map { it.preferenceKey }.toSet(), keys)
        val destinations = com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchIndex.searchable(context)
        assertTrue(destinations.map { it.key }.containsAll(keys))
        destinations.filter { it.key in keys }.forEach {
            assertTrue(it.destination is com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinationType.NavDestination)
            assertEquals(it.key, (it.destination as com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinationType.NavDestination).highlightPreferenceKey)
        }
        assertEquals("[漢方・生薬・医療]", classifiedCandidateLabel(context, setOf(DictionaryCategory.CLASSIFIED_MEDICAL, DictionaryCategory.CLASSIFIED_KAMPO)))
    }

    @Test fun specialistLicensesKeepSeparateTermsAndOfflineSourceNotices() {
        fun notice(id: String) = context.assets.open("licenses/classified/$id.html").bufferedReader().use { it.readText() }
        assertTrue(notice("kampo").contains("CC-BY-SA-2.1-JP"))
        assertTrue(notice("kampo").contains("金谷"))
        assertTrue(notice("medical").contains("CC-BY-4.0"))
        assertTrue(notice("medical").contains("MANBYO_202106"))
        assertTrue(notice("medical").contains("HYAKUYAKU_202107"))
        assertTrue(notice("medical").contains("2,006"))
        assertTrue(context.assets.open("licenses/english-reading.html").bufferedReader().use { it.readText() }.contains("Carnegie Mellon"))
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
