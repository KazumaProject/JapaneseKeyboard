package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.preference.ListPreference
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.tabs.TabLayout
import androidx.viewpager2.widget.ViewPager2
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingTabRegistry
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.DictionaryPreferenceFragment
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.ClassifiedDictionaries
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideValidator
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.ClassifiedDictionarySettingsFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingMainFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.CommonPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingsListPreferenceDialog
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.external_dictionary.ExternalDictionarySettingsFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDesignRefreshInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun host(activity: MainActivity) = activity.supportFragmentManager
        .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment

    private fun await(scenario: ActivityScenario<MainActivity>, message: String, condition: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(40)
        }
        fail(message)
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    @Test fun conventionalHomeRestoresTabsAndStudioPreviewReturnsAfterAppearanceEditing() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.getBoolean("setting_use_new_home_screen_preference", true)
        try {
            prefs.edit().putBoolean("setting_use_new_home_screen_preference", false).commit()
            AppPreference.init(context)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.awaitSettingsContentReady()
                scenario.onActivity { host(it).navController.navigate(R.id.settingMainFragment) }
                val tabs = SettingTabRegistry.createTabs()
                val dictionaryIndex = tabs.indexOfFirst { it.key == SettingTabRegistry.TAB_DICTIONARY }
                await(scenario, "Conventional tabs did not load") {
                    it.findViewById<TabLayout>(R.id.setting_tab_layout)?.tabCount == tabs.size
                }
                scenario.onActivity { activity ->
                    val tabLayout = activity.findViewById<TabLayout>(R.id.setting_tab_layout)
                    tabs.forEachIndexed { index, tab ->
                        assertEquals(tab.title(activity), tabLayout.getTabAt(index)?.text)
                    }
                    tabLayout.getTabAt(dictionaryIndex)!!.select()
                }
                await(scenario, "Dictionary tab did not load") { activity ->
                    val fragment = host(activity).childFragmentManager.primaryNavigationFragment as? SettingMainFragment
                    fragment?.childFragmentManager?.fragments?.filterIsInstance<DictionaryPreferenceFragment>()
                        ?.any { it.isResumed && it.preferenceScreen != null } == true
                }
                scenario.recreate()
                scenario.awaitSettingsContentReady()
                await(scenario, "Selected tab did not survive recreation") {
                    it.findViewById<ViewPager2>(R.id.setting_view_pager)?.currentItem == dictionaryIndex &&
                        it.findViewById<TabLayout>(R.id.setting_tab_layout)?.selectedTabPosition == dictionaryIndex
                }
                scenario.onActivity { activity ->
                    AppPreference.setting_use_new_home_screen_preference = true
                    activity.applySettingHomeModeFromPreference()
                    host(activity).navController.navigate(R.id.navigation_setting)
                }
                await(scenario, "Studio preview did not load") { it.findViewById<View>(R.id.setting_home_preview_edit) != null }
                scenario.onActivity { it.findViewById<View>(R.id.setting_home_preview_edit).performClick() }
                await(scenario, "Appearance route is not reachable") { host(it).navController.currentDestination?.id == R.id.keyboardDisplayPreferenceFragment }
                scenario.onActivity { assertTrue(host(it).navController.popBackStack()) }
                await(scenario, "Studio preview was not restored") { it.findViewById<View>(R.id.setting_home_preview) != null }
                scenario.recreate()
                scenario.awaitSettingsContentReady()
                await(scenario, "Studio preview did not survive recreation") { it.findViewById<View>(R.id.setting_home_preview) != null }
                scenario.onActivity {
                    host(it).navController.navigate(R.id.advancedPreferenceFragment)
                    host(it).navController.navigate(R.id.settingMainFragment)
                }
                await(scenario, "Tabbed settings did not open") {
                    it.findViewById<TabLayout>(R.id.setting_tab_layout)?.tabCount == tabs.size
                }
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                await(scenario, "Back from all settings did not restore the previous screen") {
                    host(it).navController.currentDestination?.id == R.id.advancedPreferenceFragment
                }
            }
        } finally {
            prefs.edit().putBoolean("setting_use_new_home_screen_preference", original).commit()
            AppPreference.init(context)
        }
    }

    @Test fun dictionaryFilteringScopesBulkChangesAndUndoAndRecreationPreserveSelections() {
        val store = DictionaryOverrideStore(context, DictionaryOverrideValidator())
        val categories = ClassifiedDictionaries.all.map { it.category }
        val previous = categories.associateWith { store.isOptionalBundledEnabled(it) to store.isExternalEnabledForCategory(it) }
        val shrine = DictionaryCategory.CLASSIFIED_FACILITY_RELIGIOUS_SHRINE
        val unrelated = DictionaryCategory.CLASSIFIED_PERSON_ACTOR
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.getBoolean("setting_use_new_home_screen_preference", true)
        try {
            categories.forEach { store.setDisableableCategoryEnabled(it, false, true) }
            store.setDisableableCategoryEnabled(unrelated, true, true)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.awaitSettingsContentReady()
                scenario.onActivity { host(it).navController.navigate(R.id.classifiedDictionarySettingsFragment) }
                await(scenario, "Dictionary categories did not load") { (it.findViewById<RecyclerView>(R.id.classified_list)?.adapter?.itemCount ?: 0) == 7 }
                scenario.onActivity { it.findViewById<TextInputEditText>(R.id.classified_search).setText(context.getString(R.string.classified_dictionary_facility_religious_shrine)) }
                await(scenario, "Category search did not narrow the list") { it.findViewById<RecyclerView>(R.id.classified_list).adapter!!.itemCount == 1 }
                scenario.onActivity { it.findViewById<View>(R.id.classified_enable_all).performClick() }
                await(scenario, "Filtered bulk action did not enable the category") { store.isOptionalBundledEnabled(shrine) }
                assertTrue(store.isOptionalBundledEnabled(unrelated))
                assertEquals(2, categories.count(store::isOptionalBundledEnabled))
                await(scenario, "Undo was not offered") { activity -> descendants(activity.window.decorView).filterIsInstance<TextView>().any { it.text == context.getString(R.string.classified_undo) } }
                scenario.onActivity { activity ->
                    descendants(activity.window.decorView).filterIsInstance<TextView>().first { it.text == context.getString(R.string.classified_undo) }.performClick()
                }
                await(scenario, "Undo did not restore the category") { !store.isOptionalBundledEnabled(shrine) }
                assertTrue(store.isOptionalBundledEnabled(unrelated))
                scenario.onActivity { it.findViewById<View>(R.id.classified_enable_all).performClick() }
                await(scenario, "Second toggle was not saved") { store.isOptionalBundledEnabled(shrine) }
                scenario.recreate()
                scenario.awaitSettingsContentReady()
                await(scenario, "Query and dictionary rows did not survive recreation") { activity ->
                    activity.findViewById<TextInputEditText>(R.id.classified_search)?.text?.toString() == context.getString(R.string.classified_dictionary_facility_religious_shrine) &&
                        activity.findViewById<RecyclerView>(R.id.classified_list)?.adapter?.itemCount == 1 &&
                        descendants(activity.window.decorView).none { it.id == R.id.settings_loading_overlay && it.isShown }
                }
                assertTrue(store.isOptionalBundledEnabled(shrine))
                scenario.onActivity { it.findViewById<Chip>(R.id.classified_filter_disabled).performClick() }
                await(scenario, "Disabled filter includes an enabled category") { it.findViewById<RecyclerView>(R.id.classified_list).adapter!!.itemCount == 0 }
                scenario.onActivity { assertFalse(it.findViewById<View>(R.id.classified_disable_all).isEnabled) }
            }
        } finally {
            previous.forEach { (category, selection) ->
                store.setOptionalBundledEnabled(category, selection.first)
                store.setExternalEnabledForCategory(category, selection.second)
            }
            prefs.edit().putBoolean("setting_use_new_home_screen_preference", original).commit()
            AppPreference.init(context)
        }
    }

    @Test fun studioListChoiceSavesThroughThePreferenceAndCancelLeavesTheValueUnchanged() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val mode = prefs.getBoolean("setting_use_new_home_screen_preference", true)
        val key = "flick_threshold_shape_preference"
        val original = prefs.getString(key, null)
        try {
            prefs.edit().putBoolean("setting_use_new_home_screen_preference", true).commit()
            AppPreference.init(context)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.awaitSettingsContentReady()
                scenario.onActivity { host(it).navController.navigate(R.id.legacyCommonPreferenceFragment) }
                await(scenario, "List preference did not load") {
                    (host(it).childFragmentManager.primaryNavigationFragment as? CommonPreferenceFragment)?.findPreference<ListPreference>(key) != null
                }
                var selected = ""
                scenario.onActivity { activity ->
                    val owner = host(activity).childFragmentManager.primaryNavigationFragment as CommonPreferenceFragment
                    val preference = owner.findPreference<ListPreference>(key)!!
                    selected = preference.entryValues!!.first { it.toString() != preference.value }.toString()
                    owner.onDisplayPreferenceDialog(preference)
                }
                await(scenario, "Studio choice sheet did not open") { activity ->
                    val owner = host(activity).childFragmentManager.primaryNavigationFragment!!
                    (owner.parentFragmentManager.findFragmentByTag("androidx.preference.PreferenceFragment.DIALOG") as? SettingsListPreferenceDialog)?.dialog?.isShowing == true
                }
                scenario.onActivity { activity ->
                    val owner = host(activity).childFragmentManager.primaryNavigationFragment as CommonPreferenceFragment
                    val preference = owner.findPreference<ListPreference>(key)!!
                    val label = preference.entries!![preference.entryValues!!.indexOf(selected)]
                    val dialog = owner.parentFragmentManager.findFragmentByTag("androidx.preference.PreferenceFragment.DIALOG") as SettingsListPreferenceDialog
                    val choice = descendants(dialog.requireDialog().window!!.decorView).filterIsInstance<TextView>().first { it.text == label }
                    assertTrue("Choice must be visible in the sheet", choice.getGlobalVisibleRect(android.graphics.Rect()))
                    choice.performClick()
                }
                assertEquals(selected, prefs.getString(key, null))
                scenario.recreate()
                scenario.awaitSettingsContentReady()
                await(scenario, "Saved choice did not survive recreation") {
                    (host(it).childFragmentManager.primaryNavigationFragment as? CommonPreferenceFragment)?.findPreference<ListPreference>(key)?.value == selected
                }
                scenario.onActivity { activity ->
                    val owner = host(activity).childFragmentManager.primaryNavigationFragment as CommonPreferenceFragment
                    owner.onDisplayPreferenceDialog(owner.findPreference<ListPreference>(key)!!)
                }
                scenario.recreate()
                scenario.awaitSettingsContentReady()
                await(scenario, "Open choice sheet did not survive recreation") { activity ->
                    val owner = host(activity).childFragmentManager.primaryNavigationFragment!!
                    val sheet = owner.parentFragmentManager.findFragmentByTag("androidx.preference.PreferenceFragment.DIALOG") as? SettingsListPreferenceDialog
                    sheet?.dialog?.window?.decorView?.let { root ->
                        descendants(root).filterIsInstance<com.google.android.material.radiobutton.MaterialRadioButton>().any()
                    } == true
                }
                scenario.onActivity { activity ->
                    val owner = host(activity).childFragmentManager.primaryNavigationFragment!!
                    (owner.parentFragmentManager.findFragmentByTag("androidx.preference.PreferenceFragment.DIALOG") as SettingsListPreferenceDialog).dismiss()
                }
                assertEquals(selected, prefs.getString(key, null))
            }
        } finally {
            prefs.edit().putBoolean("setting_use_new_home_screen_preference", mode).apply {
                if (original == null) remove(key) else putString(key, original)
            }.commit()
            AppPreference.init(context)
        }
    }

    @Test fun externalDictionaryIndexOpensOnlyOneCategoryAndRestoresItsParent() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.awaitSettingsContentReady()
            scenario.onActivity { host(it).navController.navigate(R.id.externalDictionarySettingsFragment) }
            await(scenario, "External dictionary index did not load") {
                (host(it).childFragmentManager.primaryNavigationFragment as? ExternalDictionarySettingsFragment)
                    ?.findPreference<androidx.preference.Preference>("external_dictionary_category_SYSTEM") != null
            }
            scenario.onActivity { activity ->
                val owner = host(activity).childFragmentManager.primaryNavigationFragment as ExternalDictionarySettingsFragment
                assertTrue(owner.preferenceScreen.preferenceCount < 30)
                owner.findPreference<androidx.preference.Preference>("external_dictionary_category_SYSTEM")!!.performClick()
            }
            await(scenario, "External dictionary category did not open") {
                val owner = host(it).childFragmentManager.primaryNavigationFragment as? ExternalDictionarySettingsFragment
                owner?.arguments?.getString("externalDictionaryCategory") == DictionaryCategory.SYSTEM.name && owner.preferenceScreen != null
            }
            scenario.recreate()
            scenario.awaitSettingsContentReady()
            await(scenario, "Category selection did not survive recreation") {
                (host(it).childFragmentManager.primaryNavigationFragment as? ExternalDictionarySettingsFragment)
                    ?.arguments?.getString("externalDictionaryCategory") == DictionaryCategory.SYSTEM.name
            }
            scenario.onActivity { assertTrue(host(it).navController.popBackStack()) }
            await(scenario, "External dictionary parent was not restored") {
                (host(it).childFragmentManager.primaryNavigationFragment as? ExternalDictionarySettingsFragment)
                    ?.findPreference<androidx.preference.Preference>("external_dictionary_category_SYSTEM") != null
            }
        }
    }

    @Test fun settingsSearchHighlightScrollsToAnOffscreenDictionaryAfterListPublication() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.awaitSettingsContentReady()
            scenario.onActivity {
                host(it).navController.navigate(R.id.classifiedDictionarySettingsFragment, bundleOf(
                    CommonPreferenceFragment.ARG_HIGHLIGHT_PREFERENCE_KEY to "optional_bundled_enabled_CLASSIFIED_MEDICAL",
                ))
            }
            await(scenario, "Dictionary search target did not become visible") {
                val list = it.findViewById<RecyclerView>(R.id.classified_list)
                val count = list?.adapter?.itemCount ?: 0
                count == 15 && list.findViewHolderForAdapterPosition(count - 1) != null
            }
        }
    }

    @Test fun studioSizePreviewStagesChangesUntilApplyAndRetainsTheDraftAcrossRecreation() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val mode = prefs.getBoolean("setting_use_new_home_screen_preference", true)
        val key = "keyboard_position_preference"
        val hadPosition = prefs.contains(key)
        val original = prefs.getBoolean(key, true)
        try {
            prefs.edit().putBoolean("setting_use_new_home_screen_preference", true).commit()
            AppPreference.init(context)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.awaitSettingsContentReady()
                scenario.onActivity { host(it).navController.navigate(R.id.keyboardSettingFragment) }
                await(scenario, "Size preview did not load") { it.findViewById<View>(R.id.settings_draft_apply) != null }
                fun changeAlignment() {
                    scenario.onActivity { activity ->
                        val label = context.getString(if (original) R.string.keyboard_layout_align_start else R.string.keyboard_layout_align_end)
                        descendants(activity.window.decorView).filterIsInstance<android.widget.RadioButton>().first { it.text == label }.performClick()
                    }
                }
                changeAlignment()
                assertEquals("Preview changes must not be persisted", original, prefs.getBoolean(key, true))
                scenario.onActivity { assertTrue(it.findViewById<View>(R.id.settings_draft_apply).isEnabled) }
                scenario.recreate()
                scenario.awaitSettingsContentReady()
                await(scenario, "Draft did not survive recreation") { it.findViewById<View>(R.id.settings_draft_apply)?.isEnabled == true }
                assertEquals(original, prefs.getBoolean(key, true))
                scenario.onActivity { it.findViewById<View>(R.id.settings_draft_discard).performClick() }
                scenario.onActivity { assertFalse(it.findViewById<View>(R.id.settings_draft_apply).isEnabled) }
                assertEquals(original, prefs.getBoolean(key, true))
                changeAlignment()
                scenario.onActivity { it.findViewById<View>(R.id.settings_draft_apply).performClick() }
                assertEquals(!original, prefs.getBoolean(key, true))
            }
        } finally {
            prefs.edit().putBoolean("setting_use_new_home_screen_preference", mode).apply {
                if (hadPosition) putBoolean(key, original) else remove(key)
            }.commit()
            AppPreference.init(context)
        }
    }
}
