package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.navigation.fragment.findNavController
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** The conventional home is a single, searchable list, with no off-screen tabs. */
@AndroidEntryPoint
class SettingMainFragment : AsyncPreferenceFragment() {
    @Inject lateinit var settingDataInitializer: SettingDataInitializer

    override suspend fun preparePreferenceData(context: Context) {
        settingsIo(SettingsLoadStage.DATABASE) { settingDataInitializer.initializeIfNeeded() }
    }

    override fun createPreferenceHierarchy(context: Context): PreferenceScreen =
        preferenceManager.createPreferenceScreen(context).apply {
            addPreference(Preference(context).apply {
                key = "settings_overview_search"
                setTitle(R.string.setting_home_search_content_description)
                setIcon(com.kazumaproject.core.R.drawable.baseline_search_24)
                configureClickListener {
                    navigateSafely(R.id.settingSearchFragment, bundleOf(
                        SettingSearchFragment.ARG_SEARCH_SCOPE to SettingSearchScope.LEGACY_TABS.name,
                    ))
                    true
                }
            })
            SettingsOverview.sections(context).forEach { section ->
                val group = PreferenceCategory(context).apply {
                    setTitle(section.title)
                    isIconSpaceReserved = false
                }
                addPreference(group)
                section.entries.forEach { destination ->
                    group.addPreference(Preference(context).apply {
                        key = destination.key
                        title = destination.title
                        summary = destination.summary
                        isIconSpaceReserved = false
                        configureClickListener {
                            SettingDestinations.destinationId(destination.destination)?.let { navigateSafely(it) }
                            true
                        }
                    })
                }
                if (section.title == R.string.settings_overview_other) {
                    group.addPreference(Preference(context).apply {
                        key = "settings_overview_macros"
                        setTitle(R.string.setting_category_text_macro_title)
                        isIconSpaceReserved = false
                        configureClickListener { navigateSafely(R.id.textMacroFragment); true }
                    })
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val nav = findNavController()
                if (!AppPreference.setting_use_new_home_screen_preference &&
                    nav.previousBackStackEntry?.destination?.id == R.id.navigation_setting) {
                    requireActivity().finish()
                } else if (!nav.popBackStack()) requireActivity().finish()
            }
        })
    }

    override fun onPreferencesResumed() {
        val context = requireContext().applicationContext
        launchPreferenceRefresh {
            val enabled = settingsIo(SettingsLoadStage.KEYBOARD_STATUS) {
                ContextCompat.getSystemService(context, InputMethodManager::class.java)
                    ?.enabledInputMethodList?.any { it.packageName == context.packageName } != false
            }
            if (!enabled && isResumed) navigateSafely(R.id.enableKeyboardFragment)
        }
    }
}
