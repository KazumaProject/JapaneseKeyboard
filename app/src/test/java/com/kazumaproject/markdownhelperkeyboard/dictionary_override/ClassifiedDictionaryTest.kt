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
import kotlinx.coroutines.runBlocking
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.dictionary_browser.DictionaryBrowserRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClassifiedDictionaryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun allCategoriesHaveThreeDistinctPathMappedFiles() {
        val manifest = File("../tools/classified-dictionaries/distribution-manifest.json")
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val distributionRevision = manifest.get("revision").asString
        assertTrue("Distribution revision must be a commit SHA", distributionRevision.matches(Regex("[0-9a-f]{40}")))
        assertEquals(143, ClassifiedDictionaries.all.size)
        assertEquals(143, ClassifiedDictionaries.categories.size)
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
        assertNull(DictionaryZipEntryNameMapper.map("categories/person/actor/token.dat"))
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
        assertEquals("[${context.getString(com.kazumaproject.markdownhelperkeyboard.R.string.classified_label_person_actor)}・地名]", classifiedCandidateLabel(context, setOf(DictionaryCategory.CLASSIFIED_PLACE, DictionaryCategory.CLASSIFIED_PERSON_ACTOR)))
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

    @Test fun retiredParentFlagsNeverEnableChildrenOrOverwriteExistingChoices() {
        val prefs = context.getSharedPreferences("classified-parent-upgrade", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putBoolean("optional_bundled_enabled_CLASSIFIED_PERSON", true)
            .putBoolean("optional_bundled_enabled_CLASSIFIED_EVENT", true)
            .putBoolean("optional_bundled_enabled_CLASSIFIED_TERM", true)
            .putBoolean("optional_bundled_enabled_CLASSIFIED_FACILITY", true)
            .putBoolean(DictionaryOverrideStore.optionalBundledEnabledKey(DictionaryCategory.CLASSIFIED_PLACE), true)
            .apply()
        val defaults = context.getSharedPreferences("classified-parent-defaults", Context.MODE_PRIVATE)
        val directory = File(context.cacheDir, "classified-parent-upgrade")
        val store = DictionaryOverrideStore(directory, prefs, defaults)
        val children = ClassifiedDictionaries.all.filter { '/' in it.id }
        assertEquals(117, children.size)
        children.forEach { assertFalse(it.id, store.isOptionalBundledEnabled(it.category)) }
        assertTrue(store.isOptionalBundledEnabled(DictionaryCategory.CLASSIFIED_PLACE))
        val chosen = children.first().category
        store.setDisableableCategoryEnabled(chosen, true, true)
        val reopened = DictionaryOverrideStore(directory, prefs, defaults)
        assertTrue(reopened.isOptionalBundledEnabled(chosen))
        children.drop(1).forEach { assertFalse(reopened.isOptionalBundledEnabled(it.category)) }
        assertTrue(reopened.isOptionalBundledEnabled(DictionaryCategory.CLASSIFIED_PLACE))
        for (retired in listOf("person", "event", "term", "facility")) {
            assertNull(ClassifiedDictionaries.all.find { it.id == retired })
            assertNull(DictionaryZipEntryNameMapper.map("categories/$retired/tango.dat.zip"))
        }
        reopened.removeAllOverrides()
        children.forEach { assertFalse(reopened.isOptionalBundledEnabled(it.category)) }
    }

    @Test fun additionsSince268DefaultOffWhileExistingSelectionsPersist() {
        val additions = ClassifiedDictionaries.all.filter {
            it.id.startsWith("facility/") || it.id.startsWith("media_demographic/")
        }.map { it.category }.toSet()
        val prefs = context.getSharedPreferences("classified-271-upgrade", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("optional_bundled_enabled_CLASSIFIED_FACILITY", true).commit()
        val defaults = context.getSharedPreferences("classified-271-defaults", Context.MODE_PRIVATE)
        val directory = File(context.cacheDir, "classified-271-upgrade")
        val store = DictionaryOverrideStore(directory, prefs, defaults)
        ClassifiedDictionaries.all.filter { it.category !in additions }.forEach { store.setOptionalBundledEnabled(it.category, true) }
        val reopened = DictionaryOverrideStore(directory, prefs, defaults)
        assertEquals(67, additions.size)
        ClassifiedDictionaries.all.forEach { assertEquals(it.id, it.category !in additions, reopened.isOptionalBundledEnabled(it.category)) }
        assertTrue(DictionaryFileSpecs.forCategory(DictionaryCategory.CLASSIFIED_FACILITY).isEmpty())
    }

    @Test fun literalSurfaceRepairsApplyToBundledAndIdenticalImportedTriplesOnly() {
        val prefs = context.getSharedPreferences("classified-literal-repairs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        val defaults = context.getSharedPreferences("classified-literal-defaults", Context.MODE_PRIVATE)
        val store = DictionaryOverrideStore(File(context.cacheDir, "classified-literal-repairs"), prefs, defaults)
        store.removeAllOverrides()
        val reader = DictionaryBinaryReader(DictionarySourceResolver(context, store), store)
        val category = DictionaryCategory.CLASSIFIED_PERSON_COMEDIAN
        val expected = mapOf("-2\tくりき" to "くりきひなた", "-1\tしゅうじ" to "シュージ", "-1\tまつだ" to "ﾏﾂﾀﾞ")
        assertEquals(expected, reader.loadLiteralSurfaces(category))
        assertTrue(reader.loadLiteralSurfaces(DictionaryCategory.CLASSIFIED_PERSON_ACTOR).isEmpty())
        for (spec in DictionaryFileSpecs.forCategory(category)) {
            assertTrue(store.saveOverrideFromInputStream(spec.key, context.assets.open(spec.bundledAssetPath)).isValid)
        }
        store.setDisableableCategoryEnabled(category, true, true)
        assertTrue(store.isExternalEnabledForCategory(category))
        assertEquals(expected, reader.loadLiteralSurfaces(category))
        val tango = DictionaryFileSpecs.forCategory(category).single { it.role == DictionaryFileRole.TANGO }
        val payload = java.util.zip.ZipInputStream(context.assets.open(tango.bundledAssetPath)).use { input ->
            input.nextEntry
            input.readBytes()
        }
        val output = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(output).use { zip ->
            zip.setComment("Different external dictionary archive")
            zip.putNextEntry(java.util.zip.ZipEntry("tango.dat"))
            zip.write(payload)
            zip.closeEntry()
        }
        assertTrue(store.saveOverrideFromInputStream(tango.key, output.toByteArray().inputStream()).isValid)
        assertTrue(reader.loadLiteralSurfaces(category).isEmpty())
        store.setExternalEnabledForCategory(category, false)
        assertEquals(expected, reader.loadLiteralSurfaces(category))
    }

    @Test fun dictionaryBrowserRestoresTheThreeLiteralComedianSpellings() = runBlocking {
        val prefs = context.getSharedPreferences("classified-literal-browser", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        val defaults = context.getSharedPreferences("classified-literal-browser-defaults", Context.MODE_PRIVATE)
        val store = DictionaryOverrideStore(File(context.cacheDir, "classified-literal-browser"), prefs, defaults)
        val resolver = DictionarySourceResolver(context, store)
        val repository = DictionaryBrowserRepository(DictionaryBinaryReader(resolver, store), resolver)
        for ((reading, surface) in listOf("くりき" to "くりきひなた", "しゅうじ" to "シュージ", "まつだ" to "ﾏﾂﾀﾞ")) {
            val page = repository.open(DictionaryCategory.CLASSIFIED_PERSON_COMEDIAN, reading).nextPage()
            assertTrue("$reading / $surface: ${page.rows}", page.rows.any { it.reading == reading && it.word == surface })
        }
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
        assertTrue(notice("medical").contains("2,003"))
        assertTrue(context.assets.open("licenses/english-reading.html").bufferedReader().use { it.readText() }.contains("Carnegie Mellon"))
    }

    @Test fun allClassifiedDictionariesOpenWithPinnedLiteralSurfaceRepairs() = runBlocking {
        assertTrue(context.assets.list("categories")!!.contains("surface-overrides.json"))
        val prefs = context.getSharedPreferences("classified-no-sidecar", Context.MODE_PRIVATE)
        val defaults = context.getSharedPreferences("classified-no-sidecar-defaults", Context.MODE_PRIVATE)
        val store = DictionaryOverrideStore(File(context.cacheDir, "classified-no-sidecar"), prefs, defaults)
        val resolver = DictionarySourceResolver(context, store)
        val repository = DictionaryBrowserRepository(DictionaryBinaryReader(resolver, store), resolver)
        for (spec in ClassifiedDictionaries.all) {
            val page = repository.open(spec.category, "").nextPage()
            val manifest = File("../tools/classified-dictionaries/distribution-manifest.json")
                .reader().use { JsonParser.parseReader(it).asJsonObject }
            val count = manifest.getAsJsonArray("categories").single { it.asJsonObject.get("id").asString == spec.id }
                .asJsonObject.get("recordCount").asInt
            assertEquals("${spec.id} empty category", count == 0, page.rows.isEmpty())
        }
    }

    @Test fun unknownCategoryCannotCollideWithSystemAndDuplicatesAreNotImported() {
        val plan = DictionaryZipEntryPlanner.plan(listOf("categories/person/actor/tango.dat.zip", "wrapper/categories/person/actor/tango.dat.zip", "categories/unknown/tango.dat.zip", "system/tango.dat.zip"))
        assertEquals(listOf(DictionaryFileKey.CLASSIFIED_PERSON_ACTOR_TANGO), plan.duplicateKeys.map { it.key })
        assertEquals(listOf(DictionaryFileKey.SYSTEM_TANGO), plan.importableEntries.map { it.key })
    }
}
