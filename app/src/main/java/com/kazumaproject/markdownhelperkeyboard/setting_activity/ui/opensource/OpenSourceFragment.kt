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
import de.psdev.licensesdialog.LicensesDialog
import de.psdev.licensesdialog.licenses.ApacheSoftwareLicense20
import de.psdev.licensesdialog.licenses.MITLicense
import de.psdev.licensesdialog.model.Notice

class OpenSourceFragment : Fragment() {

    companion object {
        val OPEN_SOURCE_LICENSES = listOf(
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
            "com.google.code.gson:gson",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core",
            "de.psdev.licensesdialog:licensesdialog",
            "mozc",
            "com.github.MasayukiSuda:BubbleLayout",
            "jawiki-latest-pages-articles-multistream-index.txt: CC BY-SA",
            "mecab-ipadic-neologd",
            "merge-ut-dictionaries",
            "Salesforce/wikitext",
            "com.afollestad.material-dialogs:core:3.3.0",
            "com.afollestad.material-dialogs:color:3.3.0",
            "Miwa-Keita/zenz-v3.1-xsmall-gguf",
            "azooKey/llama.cpp"
        )
    }

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

        val arrayAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            OPEN_SOURCE_LICENSES
        )
        binding.openSourceLicenseList.apply {
            adapter = arrayAdapter
            setOnItemClickListener { _, _, position, _ ->
                when (position) {
                    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17 -> {
                        val name = OPEN_SOURCE_LICENSES[position]
                        val copyright = "Copyright (c) 2005-2011, The Android Open Source Project"
                        val license = ApacheSoftwareLicense20()
                        val notice = Notice(name, "", copyright, license)
                        LicensesDialog.Builder(requireContext())
                            .setTitle("Apache Software License")
                            .setNotices(notice)
                            .build()
                            .show()
                    }

                    19 -> {
                        val name = OPEN_SOURCE_LICENSES[position]
                        val copyright = "Copyright (c) 2018 Wellington Costa"
                        val license = MITLicense()
                        val notice = Notice(name, "", copyright, license)
                        LicensesDialog.Builder(requireContext())
                            .setTitle("MIT Software License")
                            .setNotices(notice)
                            .build()
                            .show()
                    }

                    20 -> {
                        val name = OPEN_SOURCE_LICENSES[position]
                        val copyright = "Apache License"
                        val license = LicenseDialogLicense()
                        val notice =
                            Notice(
                                name,
                                "https://github.com/PSDev/LicensesDialog.git",
                                copyright,
                                license
                            )
                        LicensesDialog.Builder(requireContext())
                            .setTitle("Apache License")
                            .setNotices(notice)
                            .build()
                            .show()
                    }

                    21 -> {
                        val name = OPEN_SOURCE_LICENSES[position]
                        val copyright = "Copyright 2010-2018, Google Inc."
                        val license = MozcLicense()
                        val notice =
                            Notice(name, "https://github.com/google/mozc", copyright, license)
                        LicensesDialog.Builder(requireContext())
                            .setTitle("Copyright 2010-2018, Google Inc.")
                            .setNotices(notice)
                            .build()
                            .show()
                    }

                    22 -> {
                        val name = OPEN_SOURCE_LICENSES[position]
                        val copyright = "Copyright 2016 MasayukiSuda"
                        val license = MITLicense()
                        val notice = Notice(
                            name,
                            "https://github.com/MasayukiSuda/BubbleLayout",
                            copyright,
                            license
                        )
                        LicensesDialog.Builder(requireContext())
                            .setTitle("MIT License")
                            .setNotices(notice)
                            .build()
                            .show()
                    }

                    23 -> {
                        val name = OPEN_SOURCE_LICENSES[position]
                        val copyright =
                            "Wikipedia CC BY-SA"
                        val license = WikiLicense()
                        val notice = Notice(
                            name,
                            "https://ja.wikipedia.org/wiki/Wikipedia:%E3%82%A6%E3%82%A3%E3%82%AD%E3%83%9A%E3%83%87%E3%82%A3%E3%82%A2%E3%82%92%E4%BA%8C%E6%AC%A1%E5%88%A9%E7%94%A8%E3%81%99%E3%82%8B",
                            copyright,
                            license
                        )
                        LicensesDialog.Builder(requireContext())
                            .setTitle("jawiki-latest-pages-articles-multistream-index.txt: CC BY-SA")
                            .setNotices(notice)
                            .build()
                            .show()
                    }

                    24 -> {
                        val name = OPEN_SOURCE_LICENSES[position]
                        val copyright =
                            "2015-2019 Toshinori Sato (@overlast)"
                        val license = NeologdLicense()
                        val notice = Notice(
                            name,
                            "https://github.com/neologd/mecab-ipadic-neologd/blob/master/COPYING",
                            copyright,
                            license
                        )
                        LicensesDialog.Builder(requireContext())
                            .setTitle("mecab-ipadic-neologd")
                            .setNotices(notice)
                            .build()
                            .show()
                    }

                    25 -> {
                        val name = OPEN_SOURCE_LICENSES[position]
                        val copyright = ""
                        val license = ApacheSoftwareLicense20()
                        val notice = Notice(
                            name,
                            "https://github.com/utuhiro78/merge-ut-dictionaries/blob/main/LICENSE",
                            copyright,
                            license
                        )
                        LicensesDialog.Builder(requireContext())
                            .setTitle("merge-ut-dictionaries")
                            .setNotices(notice)
                            .build()
                            .show()
                    }

                    26 -> {
                        val name = OPEN_SOURCE_LICENSES[position]
                        val copyright =
                            " Creative Commons Attribution-ShareAlike License (CC BY-SA 4.0)"
                        val license = WikiTextLicense()
                        val notice = Notice(
                            name,
                            "https://huggingface.co/datasets/Salesforce/wikitext",
                            copyright,
                            license
                        )
                        LicensesDialog.Builder(requireContext())
                            .setTitle("Salesforce/wikitext")
                            .setNotices(notice)
                            .build()
                            .show()
                    }

                    27 -> {
                        val name = OPEN_SOURCE_LICENSES[position]
                        val copyright = "Copyright 2018 Aidan Follestad"
                        val license = LicenseDialogLicense()
                        val notice =
                            Notice(
                                name,
                                "https://github.com/afollestad/material-dialogs",
                                copyright,
                                license
                            )
                        LicensesDialog.Builder(requireContext())
                            .setTitle("com.afollestad.material-dialogs:core:3.3.0")
                            .setNotices(notice)
                            .build()
                            .show()
                    }

                    28 -> {
                        val name = OPEN_SOURCE_LICENSES[position]
                        val copyright = "Copyright 2018 Aidan Follestad"
                        val license = LicenseDialogLicense()
                        val notice =
                            Notice(
                                name,
                                "https://github.com/afollestad/material-dialogs",
                                copyright,
                                license
                            )
                        LicensesDialog.Builder(requireContext())
                            .setTitle("com.afollestad.material-dialogs:color:3.3.0")
                            .setNotices(notice)
                            .build()
                            .show()
                    }

                    29 -> {
                        val name = OPEN_SOURCE_LICENSES[position]
                        val copyright = "Copyright 2025 Miwa-Keita"
                        val license = ZenzLicense()
                        val notice =
                            Notice(
                                name,
                                "https://huggingface.co/Miwa-Keita/zenz-v3.1-xsmall-gguf",
                                copyright,
                                license
                            )
                        LicensesDialog.Builder(requireContext())
                            .setTitle("Miwa-Keita/zenz-v3.1-xsmall-gguf")
                            .setNotices(notice)
                            .build()
                            .show()
                    }

                    30 -> {
                        val name = OPEN_SOURCE_LICENSES[position]
                        val copyright = "Copyright (c) 2023-2024 The ggml authors"
                        val license = MITLicense()
                        val notice =
                            Notice(
                                name,
                                "https://github.com/azooKey/llama.cpp.git",
                                copyright,
                                license
                            )
                        LicensesDialog.Builder(requireContext())
                            .setTitle("azooKey/llama.cpp")
                            .setNotices(notice)
                            .build()
                            .show()
                    }

                }
            }
        }
    }

    private fun setupToolbarAndMenu() {
        // Set up the activity's action bar
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = getString(R.string.open_sorce_title) // Set a title for the screen
            setDisplayHomeAsUpEnabled(true) // Show the back arrow
        }

        // Add the menu provider, which is the modern way to handle menus in fragments.
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
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
