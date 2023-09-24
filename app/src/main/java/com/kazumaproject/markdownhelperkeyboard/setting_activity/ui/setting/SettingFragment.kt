package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var appPreference: AppPreference

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        object : OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                requireActivity().finish()
            }
        }

    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.setting_preference, rootKey)

        val openSourcePreference = findPreference<Preference>("preference_open_source")

        openSourcePreference?.setOnPreferenceClickListener {
            findNavController().navigate(
                R.id.action_navigation_dashboard_to_openSourceFragment
            )
            true
        }

    }

}