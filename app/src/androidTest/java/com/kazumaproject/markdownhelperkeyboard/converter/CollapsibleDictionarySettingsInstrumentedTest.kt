package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideValidator
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.CollapsiblePreferenceCategory
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.CommonPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.DictionaryPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.LegacyPreferenceResultFilter
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollapsibleDictionarySettingsInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun main(block: () -> Unit) = ins.runOnMainSync(block)
    private fun shell(command: String): String = ins.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
    }
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
    private fun screenshot(name: String, list: androidx.recyclerview.widget.RecyclerView) {
        await("Screenshot layout did not settle: $name") {
            !list.isLayoutRequested && !list.isComputingLayout && !list.hasPendingAdapterUpdates() &&
                list.itemAnimator?.isRunning != true
        }
        ins.waitForIdleSync()
        checkNotNull(ins.uiAutomation.takeScreenshot()).let { bitmap ->
            File(context.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun groupsPersistAndSearchExpandsNestedTargetsWithoutChangingSelections() {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val enabledBefore = shell("ime list -s").lines().contains(ime)
        shell("ime enable $ime")
        val expansion = context.getSharedPreferences(CollapsiblePreferenceCategory.STATE_PREFERENCES, Context.MODE_PRIVATE)
        expansion.edit().clear().commit()
        val store = DictionaryOverrideStore(context, DictionaryOverrideValidator())
        val shrineCategory = DictionaryCategory.CLASSIFIED_FACILITY_RELIGIOUS_SHRINE
        val previous = store.isOptionalBundledEnabled(shrineCategory)
        store.setOptionalBundledEnabled(shrineCategory, false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var activity: MainActivity
                scenario.onActivity { activity = it }
                await("Settings home not ready") { activity.isSettingsContentReady }
                fun host() = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                lateinit var fragment: DictionaryPreferenceFragment
                fun ready(): Boolean {
                    val current = host().childFragmentManager.fragments.filterIsInstance<DictionaryPreferenceFragment>().firstOrNull()
                    val leaf = current?.findPreference<SwitchPreferenceCompat>("optional_bundled_enabled_CLASSIFIED_FACILITY_RELIGIOUS_SHRINE")
                    return if (current != null && leaf?.onPreferenceChangeListener != null && current.listView.adapter != null) { fragment = current; true } else false
                }
                fun group(key: String) = fragment.findPreference<CollapsiblePreferenceCategory>("classified_group_$key")!!
                fun scroll(key: String, alignTop: Boolean = true) {
                    main {
                        val adapter = fragment.listView.adapter as PreferenceGroup.PreferencePositionCallback
                        val position = adapter.getPreferenceAdapterPosition(key)
                        check(position >= 0) { "Hidden preference: $key" }
                        (fragment.listView.layoutManager as androidx.recyclerview.widget.LinearLayoutManager)
                            .scrollToPositionWithOffset(position, 0)
                    }
                    await("Preference $key not visible") {
                        val adapter = fragment.listView.adapter as PreferenceGroup.PreferencePositionCallback
                        val position = adapter.getPreferenceAdapterPosition(key)
                        val row = fragment.listView.findViewHolderForAdapterPosition(position)?.itemView
                        position >= 0 && row != null &&
                            (!alignTop || kotlin.math.abs(row.top - fragment.listView.paddingTop) <= 2)
                    }
                }
                fun click(key: String) {
                    scroll(key)
                    main {
                        val adapter = fragment.listView.adapter as PreferenceGroup.PreferencePositionCallback
                        val position = adapter.getPreferenceAdapterPosition(key)
                        assertTrue(fragment.listView.findViewHolderForAdapterPosition(position)!!.itemView.performClick())
                    }
                    ins.waitForIdleSync()
                }
                main { host().navController.navigate(R.id.dictionaryPreferenceFragment) }
                await("Dictionary settings not ready", ::ready)
                main {
                    assertFalse(group("person").isExpanded)
                    assertFalse(group("facility").isExpanded)
                }
                scroll("classified_group_person")
                screenshot("after-collapsed.png", fragment.listView)
                val beforeExpansionRevision = store.currentRevision
                click("classified_group_facility")
                click("classified_group_facility_religious")
                assertEquals(beforeExpansionRevision, store.currentRevision)
                val shrineKey = "optional_bundled_enabled_CLASSIFIED_FACILITY_RELIGIOUS_SHRINE"
                val templeKey = "optional_bundled_enabled_CLASSIFIED_FACILITY_RELIGIOUS_TEMPLE"
                var templeBefore = false
                main { templeBefore = fragment.findPreference<SwitchPreferenceCompat>(templeKey)!!.isChecked }
                click(shrineKey)
                main {
                    assertTrue(store.isOptionalBundledEnabled(shrineCategory))
                    assertEquals(templeBefore, fragment.findPreference<SwitchPreferenceCompat>(templeKey)!!.isChecked)
                }
                scroll("classified_group_facility")
                screenshot("after-facility-expanded.png", fragment.listView)
                scenario.recreate()
                scenario.onActivity { activity = it }
                await("Recreated settings not ready", ::ready)
                main {
                    assertTrue(group("facility").isExpanded)
                    assertTrue(group("facility_religious").isExpanded)
                    assertTrue(fragment.findPreference<SwitchPreferenceCompat>(shrineKey)!!.isChecked)
                    group("facility").setExpanded(false)
                    group("facility_education").setExpanded(false)
                    host().navController.popBackStack()
                }
                val universityKey = "optional_bundled_enabled_CLASSIFIED_FACILITY_EDUCATION_UNIVERSITY"
                main {
                    host().navController.navigate(R.id.dictionaryPreferenceFragment, Bundle().apply {
                        putString(CommonPreferenceFragment.ARG_HIGHLIGHT_PREFERENCE_KEY, universityKey)
                    })
                }
                await("Search destination not ready") {
                    ready() && fragment.arguments?.getString(CommonPreferenceFragment.ARG_HIGHLIGHT_PREFERENCE_KEY) == universityKey
                }
                await("Search did not expand ancestors") { group("facility").isExpanded && group("facility_education").isExpanded }
                await("Search target was not scrolled into view") {
                    val adapter = fragment.listView.adapter as PreferenceGroup.PreferencePositionCallback
                    val position = adapter.getPreferenceAdapterPosition(universityKey)
                    position >= 0 && fragment.listView.findViewHolderForAdapterPosition(position) != null
                }
                assertFalse(expansion.getBoolean("classified_group_facility", false))
                assertFalse(expansion.getBoolean("classified_group_facility_education", false))
                scroll("classified_group_facility_education")
                screenshot("after-search-target.png", fragment.listView)
                main {
                    host().navController.popBackStack()
                    host().navController.navigate(R.id.dictionaryPreferenceFragment, Bundle().apply {
                        putBoolean(LegacyPreferenceResultFilter.ARG_LEGACY_SEARCH_RESULT_MODE, true)
                        putString(LegacyPreferenceResultFilter.ARG_LEGACY_SEARCH_TARGET_KEY, universityKey)
                    })
                }
                await("Legacy search target not ready") {
                    val current = host().childFragmentManager.fragments.filterIsInstance<DictionaryPreferenceFragment>().firstOrNull()
                    if (current?.arguments?.getBoolean(LegacyPreferenceResultFilter.ARG_LEGACY_SEARCH_RESULT_MODE) != true ||
                        current.findPreference<SwitchPreferenceCompat>(universityKey)?.onPreferenceChangeListener == null) false
                    else { fragment = current; group("facility").isExpanded && group("facility_education").isExpanded }
                }
                main { assertNull(fragment.findPreference<Preference>(templeKey)) }
                scroll(universityKey, alignTop = false)
                screenshot("after-legacy-search-target.png", fragment.listView)
            }
        } finally {
            store.setOptionalBundledEnabled(shrineCategory, previous)
            expansion.edit().clear().commit()
            if (!enabledBefore) shell("ime disable $ime")
        }
    }
}
