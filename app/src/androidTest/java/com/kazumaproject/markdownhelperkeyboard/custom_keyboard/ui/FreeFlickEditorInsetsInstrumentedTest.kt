package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.android.material.textfield.TextInputEditText
import com.kazumaproject.custom_keyboard.data.PopupKeyConfigCodec
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.awaitSettingsContentReady
import com.kazumaproject.markdownhelperkeyboard.sumire_popup.PopupMapEditorFragment
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class FreeFlickEditorInsetsInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var lastGeometry = ""
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
    private fun dialog(activity: MainActivity) = activity.supportFragmentManager.findFragmentByTag("insets-editor") as? CustomFreeFlickEditorDialog
    private fun editor(activity: MainActivity, custom: Boolean): PopupMapEditorFragment? = if (custom) {
        dialog(activity)?.childFragmentManager?.findFragmentById(R.id.custom_free_flick_editor_host) as? PopupMapEditorFragment
    } else {
        val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        host.childFragmentManager.primaryNavigationFragment as? PopupMapEditorFragment
    }
    private fun window(activity: MainActivity, custom: Boolean): Window = if (custom) dialog(activity)!!.requireDialog().window!! else activity.window
    private fun await(scenario: ActivityScenario<MainActivity>, message: String, condition: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(50)
        }
        fail("$message; $lastGeometry")
    }
    private fun safeActions(activity: MainActivity, custom: Boolean, ime: Boolean): Boolean {
        val root = editor(activity, custom)?.view ?: return false
        val win = window(activity, custom)
        val insets = ViewCompat.getRootWindowInsets(win.decorView) ?: return false
        if (insets.isVisible(WindowInsetsCompat.Type.ime()) != ime) return false
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val bottom = maxOf(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
        val bounds = win.windowManager.currentWindowMetrics.bounds
        val label = activity.getString(if (custom) R.string.popup_apply else R.string.popup_save)
        val apply = descendants(root).filterIsInstance<TextView>().singleOrNull { it.text == label } ?: return false
        val location = IntArray(2); apply.getLocationOnScreen(location)
        val rect = Rect(location[0], location[1], location[0] + apply.width, location[1] + apply.height)
        val maxGap = (32 * root.resources.displayMetrics.density).toInt()
        val rootLocation = IntArray(2); root.getLocationOnScreen(rootLocation)
        lastGeometry = "custom=$custom ime=$ime bounds=$bounds bars=$bars bottom=$bottom action=$rect " +
            "rootY=${rootLocation[1]} rootHeight=${root.height} rootPadding=${root.paddingBottom} " +
            "softInputMode=${win.attributes.softInputMode}"
        return apply.isShown && apply.isEnabled && rect.width() > 0 && rect.height() > 0 &&
            rect.left >= bounds.left + bars.left && rect.right <= bounds.right - bars.right &&
            rect.top >= bounds.top + bars.top && rect.bottom <= bounds.bottom - bottom &&
            rect.bottom >= bounds.bottom - bottom - maxGap
    }
    @Test fun customEditorKeepsApplyAboveSystemBarsAndImeAfterRecreation() = checkEditor(true)
    @Test fun builtInEditorKeepsSaveAboveSystemBarsAndImeWithoutDoublePadding() = checkEditor(false)

    @Test fun builtInEditorOpensFromSearchWhileTheImeIsAlreadyVisible() = checkEditor(false, true)

    private fun checkEditor(custom: Boolean, visibleImeAtEntry: Boolean = false) {
        assumeTrue("Use an isolated review app", context.packageName.endsWith(".settingsuxreview"))
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.awaitSettingsContentReady()
            if (visibleImeAtEntry) {
                scenario.onActivity { activity ->
                    (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment)
                        .navController.navigate(R.id.settingSearchFragment)
                }
                await(scenario, "Search did not show the IME") {
                    ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
            }
            scenario.onActivity { activity ->
                if (custom) CustomFreeFlickEditorDialog.create(PopupKeyConfigCodec.encode(PopupKeyConfigCodec.create("review-key", "tap")))
                    .showNow(activity.supportFragmentManager, "insets-editor")
                else (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment)
                    .navController.navigate(R.id.popupMapEditorFragment)
            }
            await(scenario, "Editor did not open") { editor(it, custom)?.view != null }
            if (!visibleImeAtEntry) {
                scenario.onActivity { activity ->
                    val root = editor(activity, custom)!!.requireView()
                    WindowCompat.getInsetsController(window(activity, custom), root).hide(WindowInsetsCompat.Type.ime())
                }
                await(scenario, "Actions overlap system bars or have excessive bottom padding") { safeActions(it, custom, false) }
            }
            scenario.onActivity { activity ->
                val field = descendants(editor(activity, custom)!!.requireView()).filterIsInstance<TextInputEditText>().first()
                field.requestFocus()
                WindowCompat.getInsetsController(window(activity, custom), field).show(WindowInsetsCompat.Type.ime())
            }
            await(scenario, "Actions overlap the IME or have excessive bottom padding") { safeActions(it, custom, true) }
            scenario.recreate()
            scenario.awaitSettingsContentReady()
            await(scenario, "Editor was not restored") { editor(it, custom)?.view != null }
            scenario.onActivity { activity ->
                WindowCompat.getInsetsController(window(activity, custom), editor(activity, custom)!!.requireView()).hide(WindowInsetsCompat.Type.ime())
            }
            await(scenario, "Restored editor actions overlap system bars") { safeActions(it, custom, false) }
        }
    }
}
