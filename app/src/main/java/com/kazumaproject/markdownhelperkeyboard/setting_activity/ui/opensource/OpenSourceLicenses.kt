package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.opensource

import android.content.Context
import android.text.TextUtils
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.ClassifiedDictionaries
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import org.json.JSONObject

internal data class LicenseItem(
    val id: String,
    val title: String,
    val summary: String,
    val document: () -> String,
)

internal data class LicenseSection(val title: String, val items: List<LicenseItem>)

/** Offline documents use the exact bundled version's notices, never a generic copyright. */
internal class OpenSourceLicenses(private val context: Context) {
    private fun text(id: Int) = context.getString(id)
    private fun asset(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }
    private fun escape(value: String) = TextUtils.htmlEncode(value)
    private fun paragraph(value: String) = "<p>${escape(value)}</p>"
    private fun section(heading: Int, body: String) = "<h2>${escape(text(heading))}</h2>$body"
    private fun link(url: String) = "<p><a href=\"${escape(url)}\">${escape(url)}</a></p>"
    private fun original(path: String) = "<pre>${escape(asset(path))}</pre>"

    private fun document(title: String, overview: String, source: String, version: String,
                         changes: String, terms: String, originals: String): String =
        "<h1>${escape(title)}</h1>" +
            section(R.string.license_overview_heading, paragraph(overview)) +
            section(R.string.license_source_heading, source) +
            section(R.string.license_version_heading, paragraph(version)) +
            section(R.string.license_changes_heading, paragraph(changes)) +
            section(R.string.license_terms_heading, paragraph(terms)) +
            section(R.string.license_original_heading, originals)

    fun application(): LicenseItem = LicenseItem("application", text(R.string.license_app), "MIT License") {
        "<h1>${escape(text(R.string.license_app))}</h1>" +
            paragraph(text(R.string.license_app_overview)) +
            link("https://github.com/KazumaProject/JapaneseKeyboard") + original("software-licenses/app-mit.txt")
    }

    fun sections(edition: String = AppVariantConfig.edition,
                 fdroid: Boolean = AppVariantConfig.isFdroid): List<LicenseSection> {
        val dictionarySummary = text(R.string.license_dictionary_summary)
        val dictionaries = listOf(
            R.string.license_core to "core",
            R.string.license_english_reading to "english-reading",
            R.string.license_english to "english",
            R.string.license_ngram to "ngram",
            R.string.license_auxiliary to "auxiliary",
            R.string.license_wikipedia to "wiki",
        ).map { (label, id) ->
            LicenseItem("dictionary/$id", text(label), dictionarySummary) { asset("licenses/$id.html").replace(Regex("<h1>.*?</h1>"), "") }
        } + ClassifiedDictionaries.all.map { spec ->
            LicenseItem("dictionary/classified/${spec.id}", text(spec.nameRes), dictionarySummary) {
                asset(spec.licenseAssetPath).replace(Regex("<h1>.*?</h1>"), "")
            }
        }
        val variant = if (edition == "full") "fullStandardRelease"
                      else if (fdroid) "liteFdroidRelease" else "liteStandardRelease"
        val modules = JSONObject(asset("software-licenses/catalog.json")).getJSONArray("modules")
        val libraries = (0 until modules.length()).map { modules.getJSONObject(it) }
            .filter { module ->
                val variants = module.getJSONArray("variants")
                (0 until variants.length()).any { variants.getString(it) == variant }
            }.map { module ->
                val name = "${module.getString("group")}:${module.getString("name")}"
                val version = module.getString("version")
                val licenses = module.getJSONArray("licenses")
                val terms = (0 until licenses.length()).joinToString(" / ") { licenses.getJSONObject(it).getString("name") }
                LicenseItem("library/$name/$version", name, "$version · Apache License 2.0") {
                    val notices = module.getJSONArray("notices")
                    val originals = buildString {
                        append(original("licenses/sources/apache-2.txt"))
                        for (i in 0 until notices.length()) {
                            val notice = notices.getJSONObject(i)
                            append("<h3>${escape(notice.getString("title"))}</h3>")
                            append(original(notice.getString("asset")))
                        }
                        if (notices.length() == 0) append(paragraph(text(R.string.license_copyright_unknown)))
                    }
                    document(name, text(R.string.license_library_overview), link(module.getString("pomUrl")),
                        version, text(R.string.license_library_changes), terms, originals)
                }
            }
        val flexbox = LicenseItem("flexbox", "FlexboxLayout", "Apache License 2.0") {
            document("FlexboxLayout", text(R.string.license_flexbox_overview),
                link("https://github.com/google/flexbox-layout"), text(R.string.license_version_unconfirmed),
                text(R.string.license_flexbox_changes), "Apache License 2.0",
                paragraph("Copyright 2016 Google Inc.") + original("licenses/sources/apache-2.txt"))
        }
        val sections = mutableListOf(
            LicenseSection(text(R.string.license_dictionaries), dictionaries),
            LicenseSection(text(R.string.license_libraries), libraries + flexbox),
        )
        if (edition == "full") {
            val models = listOf(
                LicenseItem("zenz-model", "Zenz v3.2 xsmall", text(R.string.license_zenz_summary)) {
                    document("Zenz v3.2 xsmall", text(R.string.license_zenz_overview),
                        link("https://huggingface.co/Miwa-Keita/zenz-v3.2-xsmall-gguf/blob/4f5423f0fad41a73b1242eb96fe5c12ae4fdca83/README.md"),
                        "4f5423f0fad41a73b1242eb96fe5c12ae4fdca83 · Q5_K_M",
                        text(R.string.license_zenz_changes), "Apache License 2.0",
                        original("software-licenses/zenz-model-card.txt") + paragraph(text(R.string.license_copyright_unknown)) +
                            original("licenses/sources/apache-2.txt"))
                },
                LicenseItem("llama.cpp", "azooKey/llama.cpp", "MIT License") {
                    document("azooKey/llama.cpp", text(R.string.license_llama_overview),
                        link("https://github.com/azooKey/llama.cpp/tree/88b97a47dc7f5892e2d5a6856fbe9cfe237f9e5c"),
                        "88b97a47dc7f5892e2d5a6856fbe9cfe237f9e5c",
                        text(R.string.license_llama_changes), "MIT License", original("software-licenses/llama-mit.txt"))
                },
                LicenseItem("openmp", "LLVM OpenMP runtime", "Apache License 2.0 with LLVM Exceptions") {
                    document("LLVM OpenMP runtime", text(R.string.license_openmp_overview),
                        link("https://developer.android.com/ndk/downloads/older_releases#r29"), "Android NDK 29.0.14206865",
                        text(R.string.license_openmp_changes), "Apache License 2.0 with LLVM Exceptions",
                        original("software-licenses/openmp.txt"))
                },
                LicenseItem("external-models", text(R.string.license_external_models), text(R.string.license_external_summary)) {
                    "<h1>${escape(text(R.string.license_external_models))}</h1>" + paragraph(text(R.string.license_external_overview))
                },
            )
            sections += LicenseSection(text(R.string.license_models), models)
        }
        return sections
    }
}
