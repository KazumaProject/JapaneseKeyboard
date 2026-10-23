package com.kazumaproject.markdownhelperkeyboard

import androidx.test.core.app.ActivityScenario
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.ListPreference
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.awaitSettingsContentReady
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import android.app.UiAutomation
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import java.io.File
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.inspector.WindowInspector
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Use an isolated applicationId containing orientation696 to avoid changing an installed keyboard. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class OrientationKeyboardDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext

    @Test fun rotationAndInputRestartApplyDefaultsButManualSwitchRemainsAvailable() = runScenario(false)

    @Test fun floatingKeyboardUsesOrientationDefaultsAndBackground() = runScenario(true)

    @Test fun settingsShowBothOrientationsAndBackgroundChangesStayIndependent() {
        assumeTrue(context.packageName.contains("orientation696"))
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.awaitSettingsContentReady()
            scenario.onActivity { activity ->
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                host.navController.navigate(R.id.keyboardDisplayPreferenceFragment)
            }
            val deadline = SystemClock.uptimeMillis() + 15000
            var ready = false
            while (!ready && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                    val fragment = host.childFragmentManager.primaryNavigationFragment as? PreferenceFragmentCompat
                    ready = fragment?.findPreference<Preference>("orientation_portrait_background_preference") != null
                }
                if (!ready) SystemClock.sleep(50)
            }
            assertTrue("Orientation settings did not load", ready)
            scenario.onActivity { activity ->
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                val fragment = host.childFragmentManager.primaryNavigationFragment as PreferenceFragmentCompat
                for (orientation in listOf("portrait", "landscape")) {
                    val keyboard = fragment.findPreference<ListPreference>("orientation_${orientation}_keyboard_preference")!!
                    assertTrue(keyboard.entries.size > 1)
                    assertEquals(context.getString(R.string.orientation_keyboard_default), keyboard.summary.toString())
                }
                assertNull(fragment.findPreference<Preference>("landscape_force_qwerty_preference"))
                val background = fragment.findPreference<Preference>("orientation_portrait_background_preference")!!
                background.onPreferenceClickListener!!.onPreferenceClick(background)
            }
            onView(withText(R.string.orientation_background_none)).perform(click())
            assertEquals("none", AppPreference.orientationSettings(false).background)
            assertEquals("common", AppPreference.orientationSettings(true).background)
        }
    }

    private fun runScenario(floating: Boolean) {
        assumeTrue(context.packageName.contains("orientation696"))
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val originalIme = shell("settings get secure default_input_method").trim()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val image = File(context.filesDir, "orientation-test.png")
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
            image.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val customId = if (floating) R.id.custom_layout_floating else R.id.custom_layout_default
        val qwertyId = if (floating) R.id.qwerty_view_floating else R.id.qwerty_view
        val backgroundId = if (floating) R.id.floating_keyboard_background_image else R.id.keyboard_background_image
        try {
            preferences.edit().clear()
                .putString("keyboard_order_preference", "[\"SUMIRE\",\"QWERTY\"]")
                .putBoolean("gojuon_keyboard_type_migrated_v1", true)
                .putString("orientation_portrait_keyboard_preference", "SUMIRE")
                .putString("orientation_landscape_keyboard_preference", "QWERTY")
                // An obsolete setting must not override the new portrait choice.
                .putBoolean("landscape_force_qwerty_preference", true)
                .putBoolean("landscape_force_qwerty_romaji_preference", true)
                .putString("orientation_portrait_background_preference", "image")
                .putString("orientation_portrait_image_uri_preference", Uri.fromFile(image).toString())
                .putString("orientation_landscape_background_preference", "none")
                .putBoolean("keyboard_floating_preference", floating).commit()
            automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            shell("ime enable $ime")
            shell("ime set $ime")
            openEditor()
            awaitVisible(customId)
            awaitVisible(backgroundId)
            instrumentation.runOnMainSync { service().switchNextKeyboard() }
            awaitVisible(qwertyId)
            shell("input keyevent 4")
            openEditor()
            awaitVisible(customId)
            automation.setRotation(UiAutomation.ROTATION_FREEZE_90)
            awaitVisible(qwertyId)
            awaitHidden(backgroundId)
            automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            awaitVisible(customId)
            awaitVisible(backgroundId)
        } finally {
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            if (originalIme.isNotBlank() && originalIme != "null") shell("ime set $originalIme")
            shell("input keyevent 3")
            shell("ime disable $ime")
            image.delete()
        }
    }

    private fun openEditor() {
        shell("am start -n ${context.packageName}/${FastInputHostActivity::class.java.name}")
        shell("input tap 150 250")
    }

    private fun awaitVisible(id: Int) = awaitVisibility(id, true)
    private fun awaitHidden(id: Int) = awaitVisibility(id, false)

    private fun awaitVisibility(id: Int, expected: Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            var visible = false
            instrumentation.runOnMainSync {
                visible = WindowInspector.getGlobalWindowViews().any {
                    it.findViewById<View>(id)?.isShown == true
                }
            }
            if (visible == expected) return
            SystemClock.sleep(50)
        }
        assertTrue("Keyboard ${context.resources.getResourceEntryName(id)} did not reach visibility $expected", false)
    }

    private fun service(): IMEService {
        for (root in WindowInspector.getGlobalWindowViews()) {
            var candidate: Context? = (root.findViewById<View>(R.id.custom_layout_default)
                ?: root.findViewById<View>(R.id.custom_layout_floating))?.context
            while (candidate != null) {
                if (candidate is IMEService) return candidate
                candidate = (candidate as? ContextWrapper)?.baseContext
            }
        }
        error("IME service not found")
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
}
