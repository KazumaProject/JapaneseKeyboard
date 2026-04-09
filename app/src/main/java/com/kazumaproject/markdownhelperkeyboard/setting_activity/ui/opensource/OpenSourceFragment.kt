package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.opensource

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentOpenSourceBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.other.LicenseDialogLicense
import com.kazumaproject.markdownhelperkeyboard.setting_activity.other.MozcLicense
import com.kazumaproject.markdownhelperkeyboard.setting_activity.other.NeologdLicense
import com.kazumaproject.markdownhelperkeyboard.setting_activity.other.WikiLicense
import com.kazumaproject.markdownhelperkeyboard.setting_activity.other.WikiTextLicense
import com.kazumaproject.markdownhelperkeyboard.setting_activity.other.ZenzLicense
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import de.psdev.licensesdialog.LicensesDialog
import de.psdev.licensesdialog.licenses.ApacheSoftwareLicense20
import de.psdev.licensesdialog.licenses.MITLicense
import de.psdev.licensesdialog.model.Notice

class OpenSourceFragment : Fragment() {

    private data class LicenseEntry(
        val label: String,
        val onClick: () -> Unit,
    )

    private var _binding: FragmentOpenSourceBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as AppCompatActivity).supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOpenSourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbarAndMenu()

        val licenses = buildLicenseEntries()
        val arrayAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            licenses.map { it.label }
        )
        binding.openSourceLicenseList.apply {
            adapter = arrayAdapter
            setOnItemClickListener { _, _, position, _ ->
                licenses[position].onClick()
            }
        }
    }

    private fun buildLicenseEntries(): List<LicenseEntry> {
        val entries = mutableListOf<LicenseEntry>()
        val apacheLicenses = listOf(
            "androidx.core:core-ktx",
            "androidx.appcompat:appcompat",
            "com.google.android.material:material",
            "androidx.constraintlayout:constraintlayout",
            "androidx.preference:preference-ktx",
            "androidx.lifecycle:lifecycle-extensions",
            "androidx.lifecycle:lifecycle-livedata-ktx",
            "androidx.lifecycle:lifecycle-runtime-ktx",
            "androidx.lifecycle:lifecycle-viewmodel-ktx",
            "com.google.dagger:hilt-android",
            "com.google.dagger:hilt-android-compiler",
            "androidx.lifecycle:lifecycle-livedata-ktx",
            "androidx.hilt:hilt-compiler",
            "androidx.room:room-runtime",
            "androidx.room:room-compiler",
            "androidx.room:room-ktx",
            "androidx.navigation:navigation-fragment-ktx",
            "androidx.navigation:navigation-ui-ktx",
        )

        apacheLicenses.forEach { name ->
            entries += LicenseEntry(name) {
                showLicenseDialog(
                    title = "Apache Software License",
                    notice = Notice(
                        name,
                        "",
                        "Copyright (c) 2005-2011, The Android Open Source Project",
                        ApacheSoftwareLicense20()
                    )
                )
            }
        }

        entries += LicenseEntry("org.jetbrains.kotlinx:kotlinx-coroutines-core") {
            showLicenseDialog(
                title = "MIT Software License",
                notice = Notice(
                    "org.jetbrains.kotlinx:kotlinx-coroutines-core",
                    "",
                    "Copyright (c) 2018 Wellington Costa",
                    MITLicense()
                )
            )
        }

        entries += LicenseEntry("de.psdev.licensesdialog:licensesdialog") {
            showLicenseDialog(
                title = "Apache License",
                notice = Notice(
                    "de.psdev.licensesdialog:licensesdialog",
                    "https://github.com/PSDev/LicensesDialog.git",
                    "Apache License",
                    LicenseDialogLicense()
                )
            )
        }

        entries += LicenseEntry("mozc") {
            showLicenseDialog(
                title = "Copyright 2010-2018, Google Inc.",
                notice = Notice(
                    "mozc",
                    "https://github.com/google/mozc",
                    "Copyright 2010-2018, Google Inc.",
                    MozcLicense()
                )
            )
        }

        entries += LicenseEntry("com.github.MasayukiSuda:BubbleLayout") {
            showLicenseDialog(
                title = "MIT License",
                notice = Notice(
                    "com.github.MasayukiSuda:BubbleLayout",
                    "https://github.com/MasayukiSuda/BubbleLayout",
                    "Copyright 2016 MasayukiSuda",
                    MITLicense()
                )
            )
        }

        entries += LicenseEntry("jawiki-latest-pages-articles-multistream-index.txt: CC BY-SA") {
            showLicenseDialog(
                title = "jawiki-latest-pages-articles-multistream-index.txt: CC BY-SA",
                notice = Notice(
                    "jawiki-latest-pages-articles-multistream-index.txt: CC BY-SA",
                    "https://ja.wikipedia.org/wiki/Wikipedia:%E3%82%A6%E3%82%A3%E3%82%AD%E3%83%9A%E3%83%87%E3%82%A3%E3%82%A2%E3%82%92%E4%BA%8C%E6%AC%A1%E5%88%A9%E7%94%A8%E3%81%99%E3%82%8B",
                    "Wikipedia CC BY-SA",
                    WikiLicense()
                )
            )
        }

        entries += LicenseEntry("mecab-ipadic-neologd") {
            showLicenseDialog(
                title = "mecab-ipadic-neologd",
                notice = Notice(
                    "mecab-ipadic-neologd",
                    "https://github.com/neologd/mecab-ipadic-neologd/blob/master/COPYING",
                    "2015-2019 Toshinori Sato (@overlast)",
                    NeologdLicense()
                )
            )
        }

        entries += LicenseEntry("merge-ut-dictionaries") {
            showLicenseDialog(
                title = "merge-ut-dictionaries",
                notice = Notice(
                    "merge-ut-dictionaries",
                    "https://github.com/utuhiro78/merge-ut-dictionaries/blob/main/LICENSE",
                    "",
                    ApacheSoftwareLicense20()
                )
            )
        }

        entries += LicenseEntry("Salesforce/wikitext") {
            showLicenseDialog(
                title = "Salesforce/wikitext",
                notice = Notice(
                    "Salesforce/wikitext",
                    "https://huggingface.co/datasets/Salesforce/wikitext",
                    "Creative Commons Attribution-ShareAlike License (CC BY-SA 4.0)",
                    WikiTextLicense()
                )
            )
        }

        listOf(
            "com.afollestad.material-dialogs:core:3.3.0",
            "com.afollestad.material-dialogs:color:3.3.0"
        ).forEach { name ->
            entries += LicenseEntry(name) {
                showLicenseDialog(
                    title = name,
                    notice = Notice(
                        name,
                        "https://github.com/afollestad/material-dialogs",
                        "Copyright 2018 Aidan Follestad",
                        LicenseDialogLicense()
                    )
                )
            }
        }

        if (AppVariantConfig.hasGemma) {
            entries += LicenseEntry("com.google.ai.edge.litertlm:litertlm-android") {
                showLicenseDialog(
                    title = "com.google.ai.edge.litertlm:litertlm-android",
                    notice = Notice(
                        "com.google.ai.edge.litertlm:litertlm-android",
                        "https://github.com/google-ai-edge/LiteRT-LM",
                        "Apache License 2.0",
                        ApacheSoftwareLicense20()
                    )
                )
            }
        }

        if (AppVariantConfig.hasZenz) {
            entries += LicenseEntry("Miwa-Keita/zenz-v3.1-xsmall-gguf") {
                showLicenseDialog(
                    title = "Miwa-Keita/zenz-v3.1-xsmall-gguf",
                    notice = Notice(
                        "Miwa-Keita/zenz-v3.1-xsmall-gguf",
                        "https://huggingface.co/Miwa-Keita/zenz-v3.1-xsmall-gguf",
                        "Copyright 2025 Miwa-Keita",
                        ZenzLicense()
                    )
                )
            }

            entries += LicenseEntry("azooKey/llama.cpp") {
                showLicenseDialog(
                    title = "azooKey/llama.cpp",
                    notice = Notice(
                        "azooKey/llama.cpp",
                        "https://github.com/azooKey/llama.cpp.git",
                        "Copyright (c) 2023-2024 The ggml authors",
                        MITLicense()
                    )
                )
            }
        }

        return entries
    }

    private fun showLicenseDialog(title: String, notice: Notice) {
        LicensesDialog.Builder(requireContext())
            .setTitle(title)
            .setNotices(notice)
            .build()
            .show()
    }

    private fun setupToolbarAndMenu() {
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = getString(R.string.open_sorce_title)
            setDisplayHomeAsUpEnabled(true)
        }

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = Unit

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        findNavController().popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
}
