package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat.getSystemService
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
        isKeyboardBoardEnabled()?.let { enabled ->
            if (!enabled){
                goToKeyboardSettingScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isKeyboardBoardEnabled()?.let { enabled ->
            if (!enabled){
                goToKeyboardSettingScreen()
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

    private fun isKeyboardBoardEnabled(): Boolean? {
        val imm = getSystemService(requireContext(),InputMethodManager::class.java)
        return imm?.enabledInputMethodList?.any { it.packageName == requireContext().packageName }
    }

    private fun goToKeyboardSettingScreen(){
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        requireActivity().startActivity(intent)
    }

}