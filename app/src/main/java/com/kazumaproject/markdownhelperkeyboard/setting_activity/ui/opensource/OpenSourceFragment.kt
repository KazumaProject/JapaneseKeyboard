package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.opensource

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentOpenSourceBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.other.MozcLicense
import de.psdev.licensesdialog.LicensesDialog
import de.psdev.licensesdialog.licenses.ApacheSoftwareLicense20
import de.psdev.licensesdialog.licenses.MITLicense
import de.psdev.licensesdialog.model.Notice

class OpenSourceFragment : Fragment() {

    companion object{
        val OPEN_SOURCE_LICENSES = listOf(
            "androidx.core:core-ktx","androidx.appcompat:appcompat","com.google.android.material:material",
            "androidx.constraintlayout:constraintlayout","androidx.preference:preference-ktx","androidx.lifecycle:lifecycle-extensions",
            "androidx.lifecycle:lifecycle-livedata-ktx","androidx.lifecycle:lifecycle-runtime-ktx","androidx.lifecycle:lifecycle-viewmodel-ktx",
            "com.google.dagger:hilt-android", "com.google.dagger:hilt-android-compiler","androidx.lifecycle:lifecycle-livedata-ktx",
            "androidx.hilt:hilt-compiler", "androidx.room:room-runtime", "androidx.room:room-compiler",
            "androidx.room:room-ktx", "androidx.navigation:navigation-fragment-ktx",
            "androidx.navigation:navigation-ui-ktx","com.google.code.gson:gson","org.jetbrains.kotlinx:kotlinx-coroutines-core","de.psdev.licensesdialog:licensesdialog",
            "Mozc","com.github.MasayukiSuda:BubbleLayout"
        )
    }

    private var _binding : FragmentOpenSourceBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
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


        val arrayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, OPEN_SOURCE_LICENSES)
        binding.openSourceLicenseList.apply {
            adapter = arrayAdapter
            setOnItemClickListener { parent, view, position, id ->
                when(position){
                    0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17 ->{
                        val name = OPEN_SOURCE_LICENSES[position]
                        val copyright = "Copyright (c) 2005-2011, The Android Open Source Project"
                        val license = ApacheSoftwareLicense20()
                        val notice = Notice(name,"",copyright,license)
                        LicensesDialog.Builder(requireContext())
                            .setTitle("Apache Software License")
                            .setNotices(notice)
                            .build()
                            .show()
                    }
                    19 ->{
                        val name = OPEN_SOURCE_LICENSES[position]
                        val copyright = "Copyright (c) 2018 Wellington Costa"
                        val license = MITLicense()
                        val notice = Notice(name,"",copyright,license)
                        LicensesDialog.Builder(requireContext())
                            .setTitle("MIT Software License")
                            .setNotices(notice)
                            .build()
                            .show()
                    }
                    20 ->{
                        LicensesDialog.Builder(requireContext())
                            .setIncludeOwnLicense(true)
                            .build()
                            .show()
                    }
                    21 ->{
                        val name = OPEN_SOURCE_LICENSES[position]
                        val copyright = "Copyright 2010-2018, Google Inc."
                        val license = MozcLicense()
                        val notice = Notice(name,"https://github.com/google/mozc",copyright,license)
                        LicensesDialog.Builder(requireContext())
                            .setTitle("Copyright 2010-2018, Google Inc.")
                            .setNotices(notice)
                            .build()
                            .show()
                    }

                    22 ->{
                        val name = OPEN_SOURCE_LICENSES[position]
                        val copyright = "Copyright 2016 MasayukiSuda"
                        val license = MITLicense()
                        val notice = Notice(name,"https://github.com/MasayukiSuda/BubbleLayout",copyright,license)
                        LicensesDialog.Builder(requireContext())
                            .setTitle("MIT License")
                            .setNotices(notice)
                            .build()
                            .show()
                    }
                }}
            }
    }

}