package com.kazumaproject.markdownhelperkeyboard.setting_activity.navigation

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.awaitSettingsContentReady
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
    private fun await(scenario: ActivityScenario<MainActivity>, message: String, condition: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false; scenario.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(50)
        }
        fail(message)
    }
    private fun drawer(activity: MainActivity) = activity.findViewById<DrawerLayout>(R.id.settings_drawer_layout)
    private fun bar(activity: MainActivity) = activity.findViewById<BottomNavigationView>(R.id.nav_view)
    private fun host(activity: MainActivity) = (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment)
    private fun launch(legacy: Boolean = false, block: (ActivityScenario<MainActivity>, SettingsNavigationStore) -> Unit) {
        assumeTrue(context.packageName.endsWith(".settingsuxreview"))
        AppPreference.init(context)
        val backup = AppPreference.exportAllToJson()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        try {
            prefs.edit().remove(SettingsNavigationStore.KEY).putBoolean("setting_use_new_home_screen_preference", !legacy).commit()
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.awaitSettingsContentReady(); block(scenario, SettingsNavigationStore(context))
            }
        } finally { AppPreference.importAllFromJson(backup) }
    }
    @Test fun defaultManagementScreensRemainAccessibleInBothModesAndBackClosesDrawer() = launch { scenario, store ->
        val destinations = store.bottom().map { it.destination }
        for (legacy in listOf(false, true)) {
            scenario.onActivity {
                AppPreference.setting_use_new_home_screen_preference = !legacy
                it.applySettingHomeModeFromPreference()
                bar(it).findViewById<View>(R.id.navigation_setting).performClick()
            }
            await(scenario, "Wrong settings mode") { host(it).navController.currentDestination?.id == if (legacy) R.id.settingMainFragment else R.id.navigation_setting }
            if (legacy) await(scenario, "Selected legacy tab is off screen") { activity ->
                val tabs = activity.findViewById<TabLayout>(R.id.setting_tab_layout)
                val selected = tabs?.getTabAt(tabs.selectedTabPosition)?.view
                val bounds = android.graphics.Rect()
                selected != null && selected.getGlobalVisibleRect(bounds) && bounds.width() == selected.width
            }
            scenario.onActivity { activity ->
                assertEquals(destinations, (0 until bar(activity).menu.size()).map { bar(activity).menu.getItem(it).itemId })
                assertTrue(bar(activity).isShown)
            }
            for (destination in destinations.drop(1)) {
                scenario.onActivity { bar(it).findViewById<View>(destination).performClick() }
                await(scenario, "Missing preserved management screen $destination") { host(it).navController.currentDestination?.id == destination }
            }
            scenario.onActivity { bar(it).findViewById<View>(R.id.navigation_setting).performClick() }
            await(scenario, "Missing settings root") { host(it).navController.currentDestination?.id in setOf(R.id.navigation_setting, R.id.settingMainFragment) }
            scenario.onActivity { assertTrue(it.onSupportNavigateUp()) }
            await(scenario, "Hamburger did not open drawer") { drawer(it).isDrawerOpen(GravityCompat.START) }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            await(scenario, "Back did not close drawer") { !drawer(it).isDrawerVisible(GravityCompat.START) && !it.isFinishing }
            scenario.onActivity { it.onSupportNavigateUp() }
            await(scenario, "Drawer did not reopen") { drawer(it).isDrawerOpen(GravityCompat.START) }
            scenario.onActivity { it.onSupportNavigateUp() }
            await(scenario, "Hamburger did not close drawer") { !drawer(it).isDrawerVisible(GravityCompat.START) }
        }
    }
    @Test fun drawerEditsBottomTabsAndKeepsTheSameConfigurationAfterModeSwitchAndRecreation() = launch { scenario, store ->
        scenario.onActivity { it.onSupportNavigateUp() }
        await(scenario, "Drawer missing") { drawer(it).isDrawerOpen(GravityCompat.START) }
        scenario.onActivity { activity ->
            activity.findViewById<View>(R.id.settings_navigation_edit).performClick()
            descendants(activity.findViewById(R.id.settings_drawer_content)).filterIsInstance<TabLayout>().single().getTabAt(1)!!.select()
        }
        val learned = context.getString(R.string.learn_dictionary_fragment_title)
        await(scenario, "Bottom editor missing") { it.findViewById<RecyclerView>(R.id.settings_drawer_list).adapter?.itemCount == 5 }
        scenario.onActivity { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.settings_drawer_list)
            val remove = descendants(list).filterIsInstance<TextView>().single { it.contentDescription?.toString() == context.getString(R.string.settings_navigation_remove_item, learned) }
            assertTrue("Remove control must be visibly large enough for its text", remove.getGlobalVisibleRect(android.graphics.Rect()))
            assertTrue("Remove symbol must fit inside its padding", remove.width - remove.paddingLeft - remove.paddingRight >= remove.paint.measureText(remove.text.toString()))
            val handle = descendants(list).filterIsInstance<TextView>().first { it.text.toString() == context.getString(R.string.settings_navigation_drag_handle) }
            assertTrue("Drag symbol must fit inside its padding", handle.width - handle.paddingLeft - handle.paddingRight >= handle.paint.measureText(handle.text.toString()))
            remove.performClick()
        }
        await(scenario, "Bottom removal not reflected") { bar(it).menu.size() == 4 }
        assertEquals(listOf("home", "user", "templates", "custom"), store.read().bottom)
        scenario.onActivity { it.findViewById<View>(R.id.settings_navigation_add).performClick() }
        onView(withText(learned)).inRoot(isDialog()).perform(click())
        await(scenario, "Added bottom screen not saved") { store.read().bottom == listOf("home", "user", "templates", "custom", "learn") }
        scenario.recreate(); scenario.awaitSettingsContentReady()
        await(scenario, "Open drawer editor was not restored") { activity ->
            drawer(activity).isDrawerOpen(GravityCompat.START) &&
                (activity.findViewById<TextView>(R.id.settings_navigation_edit)?.text == context.getString(R.string.settings_navigation_done)) &&
                descendants(activity.findViewById(R.id.settings_drawer_content)).filterIsInstance<TabLayout>().single().selectedTabPosition == 1
        }
        scenario.onActivity {
            drawer(it).closeDrawer(GravityCompat.START)
            AppPreference.setting_use_new_home_screen_preference = false
            it.applySettingHomeModeFromPreference()
        }
        scenario.recreate(); scenario.awaitSettingsContentReady()
        scenario.onActivity { activity ->
            assertEquals(store.bottom().map { it.destination }, (0 until bar(activity).menu.size()).map { bar(activity).menu.getItem(it).itemId })
            assertNotNull(activity.findViewById<TabLayout>(R.id.setting_tab_layout))
        }
    }
    @Test fun sideScreensCanBeRemovedAddedAndMovedWithoutChangingTheirNames() = launch { scenario, store ->
        scenario.onActivity { it.onSupportNavigateUp() }
        await(scenario, "Drawer missing") { drawer(it).isDrawerOpen(GravityCompat.START) }
        val home = store.catalog.single { it.key == "home" }.title
        scenario.onActivity { activity ->
            activity.findViewById<View>(R.id.settings_navigation_edit).performClick()
            val list = activity.findViewById<RecyclerView>(R.id.settings_drawer_list)
            descendants(list).single { it.contentDescription?.toString() == context.getString(R.string.settings_navigation_remove_item, home) }.performClick()
        }
        assertFalse("home" in store.read().drawer)
        scenario.onActivity { it.findViewById<View>(R.id.settings_navigation_add).performClick() }
        onView(withText(home)).inRoot(isDialog()).perform(click())
        await(scenario, "Added side screen not saved") { store.read().drawer.lastOrNull() == "home" }
        scenario.onActivity { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.settings_drawer_list)
            val first = list.findViewHolderForAdapterPosition(0)!!.itemView
            assertEquals("search", store.read().drawer.first())
            assertTrue(androidx.core.view.ViewCompat.performAccessibilityAction(first, R.id.setting_frequent_action_move_down, null))
        }
        assertEquals("selection", store.read().drawer.first())
        assertEquals("Editing the side menu must not change bottom tabs", SettingsNavigationStore.DEFAULT_BOTTOM, store.read().bottom)
    }

    @Test fun bottomLongPressDragChangesOrderWithoutNavigatingAndPersistsAfterRecreation() = launch { scenario, store ->
        var from = FloatArray(2); var to = FloatArray(2)
        scenario.onActivity { activity ->
            fun center(id: Int): FloatArray { val view = bar(activity).findViewById<View>(id); val xy = IntArray(2); view.getLocationOnScreen(xy); return floatArrayOf(xy[0] + view.width / 2f, xy[1] + view.height / 2f) }
            from = center(R.id.navigation_setting); to = center(R.id.keyboardListFragment)
        }
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)); event.recycle()
        }
        event(MotionEvent.ACTION_DOWN, from[0], from[1]); SystemClock.sleep(800)
        for (i in 1..15) { event(MotionEvent.ACTION_MOVE, from[0] + (to[0] - from[0]) * i / 15f, from[1]); SystemClock.sleep(30) }
        event(MotionEvent.ACTION_UP, to[0], to[1])
        await(scenario, "Drop did not persist order") { store.read().bottom == listOf("learn", "user", "templates", "custom", "home") }
        scenario.onActivity { assertEquals(R.id.navigation_setting, host(it).navController.currentDestination?.id) }
        scenario.recreate(); scenario.awaitSettingsContentReady()
        scenario.onActivity { assertEquals(R.id.navigation_setting, bar(it).menu.getItem(4).itemId) }
    }
    @Test fun conventionalTopTabsStayFixedWhileSideAndBottomConfigurationChanges() = launch(legacy = true) { scenario, store ->
        val tabs = com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingTabRegistry.createTabs()
        await(scenario, "Legacy tabs missing") { it.findViewById<TabLayout>(R.id.setting_tab_layout)?.tabCount == tabs.size }
        scenario.onActivity { activity ->
            store.save(SettingsNavigationConfig(listOf("home", "custom"), listOf("home", "custom", "learn")))
            activity.findViewById<TabLayout>(R.id.setting_tab_layout).getTabAt(3)!!.select()
        }
        scenario.recreate(); scenario.awaitSettingsContentReady()
        await(scenario, "Fixed top tabs must retain their selection") { it.findViewById<TabLayout>(R.id.setting_tab_layout)?.selectedTabPosition == 3 }
        scenario.onActivity { activity ->
            val top = activity.findViewById<TabLayout>(R.id.setting_tab_layout)
            assertEquals(tabs.map { it.title(context) }, (0 until top.tabCount).map { top.getTabAt(it)!!.text.toString() })
            assertEquals(3, bar(activity).menu.size())
            activity.onSupportNavigateUp()
        }
        await(scenario, "Drawer missing") { drawer(it).isDrawerOpen(GravityCompat.START) }
        scenario.onActivity { activity ->
            activity.findViewById<View>(R.id.settings_navigation_edit).performClick()
            val editor = descendants(activity.findViewById(R.id.settings_drawer_content)).filterIsInstance<TabLayout>().single()
            assertEquals("Only side and bottom navigation are editable", 2, editor.tabCount)
            assertEquals(context.getString(R.string.settings_navigation_side), editor.getTabAt(0)!!.text)
            assertEquals(context.getString(R.string.settings_navigation_bottom), editor.getTabAt(1)!!.text)
        }
    }
}
