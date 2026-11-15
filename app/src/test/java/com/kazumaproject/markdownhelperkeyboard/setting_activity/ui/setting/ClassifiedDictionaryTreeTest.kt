package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.ClassifiedDictionaries
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClassifiedDictionaryTreeTest {
    private fun tree(): ClassifiedDictionaryNode {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.JAPANESE) }
        return ClassifiedDictionaryNode.load(context.createConfigurationContext(configuration))
    }

    @Test fun everyShippedCategoryAppearsExactlyOnceAndDirectLeavesRemainReachable() {
        val tree = tree()
        val leaves = tree.leaves()
        assertEquals(ClassifiedDictionaries.all.size, leaves.size)
        assertEquals(ClassifiedDictionaries.all.map { it.category }.toSet(), leaves.map { it.category }.toSet())
        assertTrue(tree.children.all { it.category == null })
        assertTrue(tree.find("classified_group_other")!!.leaves().any { it.category == DictionaryCategory.CLASSIFIED_STATION })
        leaves.forEach { assertEquals(it, tree.pathTo(it.key).last()) }
    }

    @Test fun parentNameSearchIncludesNestedLeavesAndEnabledFilterAppliesToTheSameScope() {
        val tree = tree()
        val shrine = DictionaryCategory.CLASSIFIED_FACILITY_RELIGIOUS_SHRINE
        val enabled: (DictionaryCategory) -> Boolean = { it == shrine }
        val matches = tree.visible("宗教", DictionarySelectionFilter.ALL, enabled)
        assertEquals(4, matches.size)
        assertEquals(listOf(shrine), tree.visible("宗教 神社", DictionarySelectionFilter.ENABLED, enabled).map { it.category })
        val disabled = tree.visible("宗教", DictionarySelectionFilter.DISABLED, enabled)
        assertEquals(3, disabled.size)
        assertFalse(disabled.any { it.category == shrine })
        assertTrue(tree.visible("存在しないカテゴリ", DictionarySelectionFilter.ALL, enabled).isEmpty())
    }

    @Test fun bulkScopeIsLimitedToFilteredLeavesAndNeverIncludesUnrelatedGroups() {
        val tree = tree()
        val group = tree.find("classified_group_facility_religious")!!
        val enabled: (DictionaryCategory) -> Boolean = { it == DictionaryCategory.CLASSIFIED_FACILITY_RELIGIOUS_SHRINE }
        val shown = group.visible("", DictionarySelectionFilter.DISABLED, enabled)
        val changed = shown.flatMap { it.leaves() }.mapNotNull { it.category }.toSet()
        assertEquals(3, changed.size)
        assertFalse(DictionaryCategory.CLASSIFIED_FACILITY_RELIGIOUS_SHRINE in changed)
        assertFalse(DictionaryCategory.CLASSIFIED_PERSON_ACTOR in changed)
        assertEquals(7, tree.visible("", DictionarySelectionFilter.ALL, enabled).size)
    }
}
