package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.view.View
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.awaitSettingsContentReady
import com.kazumaproject.markdownhelperkeyboard.setting_activity.navigation.SettingsNavigationStore
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardEditorNavigationInstrumentedTest {

    @Test
    fun templatesTabKeepsDraftUntilDiscardIsConfirmed() = withNewEditor { scenario ->
        var originalName = ""
        var originalRows = 0
        scenario.onActivity { activity ->
            val original = editor(activity).uiState.value
            originalName = original.name
            originalRows = original.layout.rowCount
            activity.findViewById<View>(R.id.button_add_row).performClick()
        }
        onView(withId(R.id.keyboard_name_edittext)).perform(replaceText("Draft layout"))

        scenario.selectTab(R.id.userTemplateFragment)
        scenario.assertDestination(R.id.keyboardEditorFragment)
        choose(R.string.editor_continue_editing)
        onView(withId(R.id.keyboard_name_edittext)).check(matches(withText("Draft layout")))
        scenario.onActivity { assertEquals(originalRows + 1, editor(it).uiState.value.layout.rowCount) }

        scenario.selectTab(R.id.userTemplateFragment)
        choose(R.string.editor_discard_changes)
        scenario.assertDestination(R.id.userTemplateFragment)

        scenario.selectTab(R.id.keyboardListFragment)
        addNewLayout()
        onView(withId(R.id.keyboard_name_edittext)).check(matches(withText(originalName)))
        scenario.onActivity { assertEquals(originalRows, editor(it).uiState.value.layout.rowCount) }

        scenario.selectTab(R.id.userTemplateFragment)
        scenario.assertDestination(R.id.userTemplateFragment)
    }

    @Test
    fun aKeyDraftIsProtectedEvenWhenLayoutHasNoChanges() = withNewEditor { scenario ->
        scenario.onActivity { activity ->
            val viewModel = editor(activity)
            assertFalse(viewModel.hasUnsavedChanges())
            val keyId = viewModel.uiState.value.layout.items.first().id
            val layoutFragment = host(activity).childFragmentManager
                .primaryNavigationFragment as KeyboardEditorFragment
            layoutFragment.onKeySelected(keyId)
            layoutFragment.onKeySelected(keyId)
        }
        onView(withId(R.id.key_label_edittext)).perform(replaceText("Draft key"))

        scenario.selectTab(R.id.userTemplateFragment)
        choose(R.string.editor_continue_editing)
        scenario.assertDestination(R.id.keyEditorFragment)
        onView(withId(R.id.key_label_edittext)).check(matches(withText("Draft key")))

        pressBack()
        scenario.assertDestination(R.id.keyboardEditorFragment)
    }

    @Test
    fun drawerAndBothBackActionsUseTheSameConfirmation() = withNewEditor { scenario ->
        onView(withId(R.id.keyboard_name_edittext)).perform(replaceText("Draft layout"))
        scenario.onActivity { activity ->
            activity.findViewById<DrawerLayout>(R.id.settings_drawer_layout)
                .openDrawer(GravityCompat.START, false)
        }
        onView(
            allOf(withText(R.string.settings_navigation_home), isDescendantOfA(withId(R.id.settings_drawer_list)))
        ).perform(click())
        choose(R.string.editor_continue_editing)
        scenario.assertDestination(R.id.keyboardEditorFragment)

        pressBack()
        choose(R.string.editor_continue_editing)
        scenario.onActivity { assertTrue(it.onSupportNavigateUp()) }
        choose(R.string.editor_discard_changes)
        scenario.assertDestination(R.id.keyboardListFragment)
    }

    private fun withNewEditor(test: (ActivityScenario<MainActivity>) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val originalNavigation = preferences.getString(SettingsNavigationStore.KEY, null)
        preferences.edit().remove(SettingsNavigationStore.KEY).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.awaitSettingsContentReady()
                scenario.onActivity { host(it).navController.navigate(R.id.keyboardListFragment) }
                addNewLayout()
                test(scenario)
            }
        } finally {
            preferences.edit().putString(SettingsNavigationStore.KEY, originalNavigation).commit()
        }
    }

    private fun addNewLayout() {
        onView(withId(R.id.fab_add_layout)).perform(click())
    }

    private fun ActivityScenario<MainActivity>.selectTab(destination: Int) {
        onActivity { activity ->
            val bar = activity.findViewById<View>(R.id.nav_view)
            assertTrue(bar.findViewById<View>(destination).performClick())
        }
    }

    private fun choose(label: Int) {
        onView(withText(label)).inRoot(isDialog()).perform(click())
    }

    private fun ActivityScenario<MainActivity>.assertDestination(destination: Int) {
        onActivity { assertEquals(destination, host(it).navController.currentDestination?.id) }
    }

    private fun editor(activity: MainActivity) = host(activity).navController.keyboardEditorViewModel(activity)

    private fun host(activity: MainActivity) =
        activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
}
