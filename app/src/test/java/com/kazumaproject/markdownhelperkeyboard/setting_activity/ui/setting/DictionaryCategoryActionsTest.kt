package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.ClassifiedDictionaries
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DictionaryCategoryActionsTest {
    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard,
    )
    private lateinit var screen: PreferenceScreen
    private lateinit var store: DictionaryOverrideStore
    private lateinit var actions: DictionaryCategoryActions
    private var changedCategories = emptyList<DictionaryCategory>()

    @Before fun setUp() {
        context.getSharedPreferences(CollapsiblePreferenceCategory.STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val prefs = context.getSharedPreferences("category-actions", Context.MODE_PRIVATE)
        val defaults = context.getSharedPreferences("category-actions-defaults", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        defaults.edit().clear().commit()
        store = DictionaryOverrideStore(File(context.cacheDir, "category-actions"), prefs, defaults)
        val manager = PreferenceManager(context)
        screen = manager.inflateFromResource(context, R.xml.pref_dictionary, null)
        manager.setPreferences(screen)
        screen.onAttached()
        actions = DictionaryCategoryActions(screen, store::isOptionalBundledEnabled) { categories, enabled ->
            changedCategories = categories
            categories.forEach { store.setDisableableCategoryEnabled(it, enabled, true) }
        }
        actions.bind()
    }

    private fun action(group: String) = screen.findPreference<Preference>("classified_group_${group}_bulk_toggle")!!

    private fun choose(group: String, index: Int) {
        action(group).performClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.listView.performItemClick(null, index, index.toLong())
    }

    @Test fun parentActionIncludesNestedCategoriesAndPreservesUnrelatedSelections() {
        val unrelated = DictionaryCategory.CLASSIFIED_PERSON_ACTOR
        store.setDisableableCategoryEnabled(unrelated, true, true)
        val group = screen.findPreference<CollapsiblePreferenceCategory>("classified_group_facility")!!
        assertFalse(action("facility").isVisible)
        group.setExpanded(true)
        assertTrue(action("facility").isVisible)
        choose("facility", 0)
        val facilityCategories = ClassifiedDictionaries.all.filter { it.preferenceKey.startsWith("optional_bundled_enabled_CLASSIFIED_FACILITY_") }.map { it.category }
        assertEquals(facilityCategories.toSet(), changedCategories.toSet())
        facilityCategories.forEach { assertTrue(it.name, store.isOptionalBundledEnabled(it)) }
        assertTrue(store.isOptionalBundledEnabled(unrelated))
        assertEquals(context.getString(R.string.classified_group_enabled_count, facilityCategories.size, facilityCategories.size), action("facility").summary)
        assertTrue(group.isExpanded)
        val persisted = DictionaryOverrideStore(
            File(context.cacheDir, "category-actions"),
            context.getSharedPreferences("category-actions", Context.MODE_PRIVATE),
            context.getSharedPreferences("category-actions-defaults", Context.MODE_PRIVATE),
        )
        facilityCategories.forEach { assertTrue(persisted.isOptionalBundledEnabled(it)) }
        ClassifiedDictionaries.all.filter { it.category !in facilityCategories && it.category != unrelated }
            .forEach { assertFalse(store.isOptionalBundledEnabled(it.category)) }
        choose("facility", 1)
        facilityCategories.forEach { assertFalse(it.name, store.isOptionalBundledEnabled(it)) }
        assertTrue(store.isOptionalBundledEnabled(unrelated))
        val reopened = DictionaryOverrideStore(
            File(context.cacheDir, "category-actions"),
            context.getSharedPreferences("category-actions", Context.MODE_PRIVATE),
            context.getSharedPreferences("category-actions-defaults", Context.MODE_PRIVATE),
        )
        facilityCategories.forEach { assertFalse(reopened.isOptionalBundledEnabled(it)) }
    }

    @Test fun nestedActionAndMixedSelectionsRefreshParentCounts() {
        choose("facility", 0)
        choose("facility_religious", 1)
        val nested = changedCategories.toSet()
        assertTrue(nested.contains(DictionaryCategory.CLASSIFIED_FACILITY_RELIGIOUS_SHRINE))
        ClassifiedDictionaries.all.filter { it.preferenceKey.startsWith("optional_bundled_enabled_CLASSIFIED_FACILITY_") }.forEach {
            assertEquals(it.category.name, it.category !in nested, store.isOptionalBundledEnabled(it.category))
        }
        assertEquals(context.getString(R.string.classified_group_enabled_count, 0, nested.size), action("facility_religious").summary)
        val total = ClassifiedDictionaries.all.count { it.preferenceKey.startsWith("optional_bundled_enabled_CLASSIFIED_FACILITY_") }
        assertEquals(context.getString(R.string.classified_group_enabled_count, total - nested.size, total), action("facility").summary)
        store.setDisableableCategoryEnabled(DictionaryCategory.CLASSIFIED_FACILITY_RELIGIOUS_SHRINE, true, true)
        actions.refresh()
        assertEquals(context.getString(R.string.classified_group_enabled_count, 1, nested.size), action("facility_religious").summary)
        choose("facility_religious", 0)
        nested.forEach { assertTrue(store.isOptionalBundledEnabled(it)) }
    }

    @Test fun cancelDoesNotWriteAndLegacyFilteringRemovesBulkActions() {
        val revision = store.currentRevision
        action("person").performClick()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(revision, store.currentRevision)
        assertTrue(changedCategories.isEmpty())
        val key = "optional_bundled_enabled_CLASSIFIED_FACILITY_RELIGIOUS_SHRINE"
        LegacyPreferenceResultFilter.filterPreferenceScreen(screen, key, emptyList())
        assertNull(screen.findPreference<Preference>("classified_group_facility_bulk_toggle"))
        assertNotNull(screen.findPreference<SwitchPreferenceCompat>(key))
    }
}
