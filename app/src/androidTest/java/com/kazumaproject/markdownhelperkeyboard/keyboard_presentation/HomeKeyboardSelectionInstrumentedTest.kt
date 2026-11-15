package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.awaitSettingsContentReady
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeKeyboardSelectionInstrumentedTest {
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
    private fun awaitTarget(scenario: ActivityScenario<MainActivity>, expected: KeyboardPreviewTarget) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { activity ->
                val pager = activity.findViewById<ViewPager2>(R.id.keyboard_preview_pager)
                ready = pager?.adapter?.itemCount == 1 && !pager.isUserInputEnabled &&
                    descendants(pager).filterIsInstance<KeyboardPreviewView>().any { it.target == expected && it.ready }
            }
            if (ready) return
            SystemClock.sleep(50)
        }
        fail("Missing single selected preview: $expected")
    }
    @Test fun savedFamilyCustomSelectionLiveChangesAndDeletionResolveOnePreview() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(context.packageName.endsWith(".settingsuxreview"))
        AppPreference.init(context)
        val backup = AppPreference.exportAllToJson()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val repo = EntryPointAccessors.fromApplication(context, KeyboardPreviewDependencies::class.java).keyboards()
        val created = mutableListOf<Long>()
        try {
            repeat(2) { index -> created += repo.saveLayout(KeyboardDefaultLayouts.createFinalLayout(
                KeyboardInputMode.HIRAGANA, emptyMap(), "flick", "default"), "Selection review $index", null) }
            val layouts = repo.getLayoutsNotFlow()
            val custom = layouts.single { it.layoutId == created.last() }
            prefs.edit().putBoolean("setting_use_new_home_screen_preference", true).commit()
            AppPreference.keyboard_order = listOf(KeyboardType.TENKEY, KeyboardType.SUMIRE, KeyboardType.QWERTY, KeyboardType.CUSTOM)
            AppPreference.save_last_used_keyboard_enable_preference = true
            AppPreference.save_last_used_keyboard_position_preference = 2
            AppPreference.remember_last_custom_keyboard_preference = true
            AppPreference.last_used_custom_keyboard_stable_id = custom.stableId
            KeyboardPreviewSelection.publish(null)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.awaitSettingsContentReady()
                scenario.onActivity { (it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController.navigate(R.id.keyboardDisplayPreferenceFragment) }
                awaitTarget(scenario, KeyboardPreviewTarget(KeyboardType.QWERTY))
                scenario.recreate(); scenario.awaitSettingsContentReady()
                awaitTarget(scenario, KeyboardPreviewTarget(KeyboardType.QWERTY))
                scenario.onActivity { KeyboardPreviewSelection.publish(KeyboardPreviewTarget(KeyboardType.CUSTOM, custom.stableId, custom.layoutId)) }
                awaitTarget(scenario, KeyboardPreviewTarget(KeyboardType.CUSTOM, custom.stableId, custom.layoutId))
                repo.deleteLayout(custom.layoutId)
                scenario.recreate(); scenario.awaitSettingsContentReady()
                awaitTarget(scenario, KeyboardPreviewTarget(KeyboardType.QWERTY))
                scenario.onActivity {
                    AppPreference.save_last_used_keyboard_position_preference = 3
                    KeyboardPreviewSelection.publish(null)
                    (it.findViewById<ViewGroup>(R.id.setting_home_preview).getChildAt(0) as KeyboardPreviewCarousel).reload()
                }
                val fallback = repo.getLayoutsNotFlow().first()
                awaitTarget(scenario, KeyboardPreviewTarget(KeyboardType.CUSTOM, fallback.stableId, fallback.layoutId))
            }
        } finally {
            KeyboardPreviewSelection.publish(null)
            created.forEach { repo.deleteLayout(it) }
            AppPreference.importAllFromJson(backup)
        }
    }
}
