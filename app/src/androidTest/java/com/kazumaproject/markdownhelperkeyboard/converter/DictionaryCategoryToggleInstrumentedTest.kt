package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.SystemClock
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.ClassifiedDictionaries
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideValidator
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.CollapsiblePreferenceCategory
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.DictionaryPreferenceFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DictionaryCategoryToggleInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun main(block: () -> Unit) = ins.runOnMainSync(block)
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 90_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        error(message)
    }

    @Test fun categoryActionsUpdateNestedSwitchesAndPersistAcrossRecreation() {
        listOf("input keyevent KEYCODE_WAKEUP", "wm dismiss-keyguard").forEach { command ->
            ins.uiAutomation.executeShellCommand(command).use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
            }
        }
        val store = DictionaryOverrideStore(context, DictionaryOverrideValidator())
        val specs = ClassifiedDictionaries.all.filter { it.preferenceKey.startsWith("optional_bundled_enabled_CLASSIFIED_FACILITY_") }
        val previous = specs.associate { it.category to store.isOptionalBundledEnabled(it.category) }
        val externalBefore = specs.associate { it.category to store.isExternalEnabledForCategory(it.category) }
        val expansion = context.getSharedPreferences(CollapsiblePreferenceCategory.STATE_PREFERENCES, Context.MODE_PRIVATE)
        val expansionBefore = expansion.all
        val unrelated = ClassifiedDictionaries.all.filterNot { it in specs }
            .associate { it.category to store.isOptionalBundledEnabled(it.category) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var activity: MainActivity
                scenario.onActivity { activity = it }
                await("Settings home not ready") { activity.isSettingsContentReady }
                fun host() = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                lateinit var fragment: DictionaryPreferenceFragment
                fun ready(): Boolean {
                    val current = host().childFragmentManager.fragments.filterIsInstance<DictionaryPreferenceFragment>().firstOrNull()
                    return if (current?.findPreference<Preference>("classified_group_facility_bulk_toggle") != null && current.listView.adapter != null) {
                        fragment = current
                        true
                    } else false
                }
                main { host().navController.navigate(R.id.dictionaryPreferenceFragment) }
                await("Dictionary settings not ready", ::ready)
                fun choose(group: String, enabled: Boolean) {
                    val key = "classified_group_${group}_bulk_toggle"
                    main {
                        fragment.findPreference<CollapsiblePreferenceCategory>("classified_group_facility")!!.setExpanded(true)
                        fragment.findPreference<CollapsiblePreferenceCategory>("classified_group_$group")!!.setExpanded(true)
                    }
                    ins.waitForIdleSync()
                    main { fragment.scrollToPreference(key) }
                    await("Bulk action not visible") {
                        val adapter = fragment.listView.adapter as PreferenceGroup.PreferencePositionCallback
                        val position = adapter.getPreferenceAdapterPosition(key)
                        position >= 0 && fragment.listView.findViewHolderForAdapterPosition(position) != null
                    }
                    main {
                        val adapter = fragment.listView.adapter as PreferenceGroup.PreferencePositionCallback
                        val position = adapter.getPreferenceAdapterPosition(key)
                        assertTrue(fragment.listView.findViewHolderForAdapterPosition(position)!!.itemView.performClick())
                    }
                    onView(withText(if (enabled) R.string.classified_group_enable_all else R.string.classified_group_disable_all)).inRoot(isDialog()).perform(click())
                    ins.waitForIdleSync()
                }
                choose("facility", true)
                main {
                    specs.forEach { spec ->
                        assertTrue(store.isOptionalBundledEnabled(spec.category))
                        assertTrue(fragment.findPreference<SwitchPreferenceCompat>(spec.preferenceKey)!!.isChecked)
                    }
                }
                choose("facility_religious", false)
                val religious = specs.filter { it.preferenceKey.contains("_RELIGIOUS_") }
                main {
                    specs.forEach { spec ->
                        assertEquals(spec !in religious, store.isOptionalBundledEnabled(spec.category))
                        assertEquals(spec !in religious, fragment.findPreference<SwitchPreferenceCompat>(spec.preferenceKey)!!.isChecked)
                    }
                }
                scenario.recreate()
                scenario.onActivity { activity = it }
                await("Recreated settings not ready", ::ready)
                main {
                    specs.forEach { spec -> assertEquals(spec !in religious, fragment.findPreference<SwitchPreferenceCompat>(spec.preferenceKey)!!.isChecked) }
                }
                choose("facility", false)
                specs.forEach { assertFalse(store.isOptionalBundledEnabled(it.category)) }
                unrelated.forEach { (category, enabled) -> assertEquals(enabled, store.isOptionalBundledEnabled(category)) }
            }
        } finally {
            previous.forEach { (category, enabled) -> store.setOptionalBundledEnabled(category, enabled) }
            externalBefore.forEach { (category, enabled) -> store.setExternalEnabledForCategory(category, enabled) }
            expansion.edit().clear().apply {
                expansionBefore.forEach { (key, value) -> putBoolean(key, value as Boolean) }
            }.commit()
        }
    }
}
