package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.opensource

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.ClassifiedDictionaries
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenSourceLicensesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val catalog get() = OpenSourceLicenses(context)
    private val distribution get() = JSONObject(File("../tools/classified-dictionaries/distribution-manifest.json").readText())

    @Test fun editionsHaveOnlyTheirResolvedLibrariesAndModelsWithoutDuplicates() {
        val full = catalog.sections("full", false).flatMap { it.items }
        val lite = catalog.sections("lite", false).flatMap { it.items }
        val fdroid = catalog.sections("lite", true).flatMap { it.items }
        for (items in listOf(full, lite, fdroid)) {
            assertEquals(items.size, items.map { it.id }.toSet().size)
            assertTrue(items.all { it.title.isNotBlank() && it.summary.isNotBlank() })
        }
        assertEquals(138, full.count { it.id.startsWith("library/") })
        assertEquals(136, lite.count { it.id.startsWith("library/") })
        assertEquals(lite.map { it.id }.toSet(), fdroid.map { it.id }.toSet())
        assertTrue(full.any { it.id == "zenz-model" })
        assertFalse(lite.any { it.id in listOf("zenz-model", "llama.cpp", "external-models") })
        assertTrue(full.any { it.id.contains("litertlm-android") })
        assertFalse(lite.any { it.id.contains("litertlm-android") })
        assertTrue(full.any { it.id.contains("litert-npu-runtime-google-tensor") })
        assertFalse(lite.any { it.id.contains("litert-npu-runtime-google-tensor") })
        assertTrue(full.any { it.id == "library/androidx.core:core/1.17.0" })
        assertTrue(lite.any { it.id == "library/androidx.core:core/1.16.0" })
    }

    @Test fun universalAiRuntimeKeepsPinnedNoticesInFullEditionOnly() {
        val native = catalog.sections("full", false).flatMap { it.items }.first { it.id == "ai-runtime" }.document()
        assertTrue(native.contains("cd959bae99ff4bed22bef2604f45241c09bbb8c8"))
        assertTrue(native.contains("0d3f509e57041fbd073a1ee84cd9ccd36d148446"))
        assertTrue(native.contains("04f10f650d514df88b76d25e83db360142c7b174"))
        assertTrue(native.contains("The ggml authors"))
        assertTrue(native.contains("Sean Barrett"))
        assertTrue(native.contains("David Reid"))
        assertFalse(catalog.sections("lite", false).flatMap { it.items }.any { it.id == "ai-runtime" })
    }

    @Test fun dictionaryNoticesCoverEverySelectableCategoryAndCurrentRelease() {
        val items = catalog.sections("lite", false).flatMap { it.items }
            .filter { it.id.startsWith("dictionary/") }
        assertEquals(ClassifiedDictionaries.all.size + 6, items.size)
        ClassifiedDictionaries.all.forEach { spec ->
            assertTrue(items.any { it.id == "dictionary/classified/${spec.id}" })
        }
        val manifest = distribution
        val version = manifest.getString("version")
        val revision = manifest.getString("revision")
        items.forEach {
            val html = it.document()
            assertTrue(it.id, html.contains("配布: $version ($revision)"))
            assertTrue(it.id, html.contains("<pre>"))
        }
    }

    @Test fun dictionaryDetailsShowPinnedEnglishInputs() {
        val english = catalog.sections("lite", false).flatMap { it.items }
            .single { it.id == "dictionary/english" }.document()
        val manifest = distribution.getJSONObject("englishDictionary")
        assertTrue(english.contains("<code>${manifest.getString("revision")}</code>"))
        assertTrue(english.contains(manifest.getString("catalogUrl")))
        assertTrue(english.contains(manifest.getString("reviewedGrammarSourceUrl")))
    }

    @Test fun correctedModelAndCoroutinesLicensesKeepOfflineOriginals() {
        val items = catalog.sections("full", false).flatMap { it.items }
        val coroutines = items.first { it.id == "library/org.jetbrains.kotlinx:kotlinx-coroutines-core/1.8.1" }
        assertTrue(coroutines.summary.contains("Apache"))
        assertFalse(coroutines.document().contains("Wellington Costa"))
        val model = items.first { it.id == "zenz-model" }.document()
        assertTrue(model.contains("apache-2.0"))
        assertTrue(model.contains("4f5423f0fad41a73b1242eb96fe5c12ae4fdca83"))
        assertFalse(model.contains("CC BY-SA"))
        assertTrue(items.first { it.id == "llama.cpp" }.document().contains("The ggml authors"))
        assertTrue(catalog.application().document().contains("Copyright (c) 2024 Kazuma Naka"))
    }

    @Test fun everyPreservedLibraryNoticeMatchesItsEvidenceHash() {
        val json = context.assets.open("software-licenses/catalog.json").bufferedReader().use { JSONObject(it.readText()) }
        val modules = json.getJSONArray("modules")
        for (i in 0 until modules.length()) {
            val module = modules.getJSONObject(i)
            assertTrue(module.getJSONArray("licenses").length() > 0)
            val notices = module.getJSONArray("notices")
            for (j in 0 until notices.length()) {
                val notice = notices.getJSONObject(j)
                val bytes = context.assets.open(notice.getString("asset")).use { it.readBytes() }
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                assertEquals(notice.getString("asset"), notice.getString("sha256"), hash)
            }
        }
    }
}
