package com.kazumaproject.markdownhelperkeyboard

import android.content.ContextWrapper
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.ImageView
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Checks candidate chrome on the live IME across input restarts and floating transitions. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class CupertinoCandidateAppearanceDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context = ins.targetContext
    private val ui = ins.uiAutomation
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val skins = listOf(KeyboardSkinId.CUPERTINO_CLASSIC,
        KeyboardSkinId.CUPERTINO_LIGHT, KeyboardSkinId.CUPERTINO_DARK)

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }

    private fun onMain(action: () -> Unit) {
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        Handler(Looper.getMainLooper()).post {
            try { action() } catch (error: Throwable) { failure = error }
            finally { done.countDown() }
        }
        check(done.await(5, TimeUnit.SECONDS)) { "IME main thread did not respond" }
        failure?.let { throw it }
    }

    private fun hostActivity(): FastInputHostActivity =
        WindowInspector.getGlobalWindowViews().asSequence()
            .flatMap { root -> generateSequence(root.findViewById<View>(android.R.id.input)?.context ?: root.context) { (it as? ContextWrapper)?.baseContext } }
            .filterIsInstance<FastInputHostActivity>().first()

    private fun imeRoot(floating: Boolean = false): View? {
        val id = if (floating) R.id.keyboard_view_floating else R.id.keyboard_view
        return WindowInspector.getGlobalWindowViews().firstOrNull {
            val keyboard = it.findViewById<View>(id)
            keyboard?.isShown == true && generateSequence(keyboard as View?) { view -> view.parent as? View }
                .all { view -> view.alpha > 0.9f }
        }
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            onMain { ready = condition() }
            if (ready) return
            SystemClock.sleep(100)
        }
        error(message)
    }

    private fun withIsolatedIme(test: () -> Unit) {
        check(context.packageName.startsWith("com.kazumaproject.skinfidelity")) {
            "Run in the isolated test APK"
        }
        val saved = prefs.all.toMap()
        val oldIme = shell("settings get secure default_input_method")
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val wasEnabled = shell("ime list -s").lines().contains(target)
        try {
            check(prefs.edit().clear()
                .putString("keyboard_order_preference", "[\"TENKEY\"]")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("candidate_tab_visibility_preference", true)
                .putBoolean("keyboard_floating_preference", false)
                .putBoolean("landscape_force_qwerty_preference", false)
                .putBoolean("landscape_force_qwerty_romaji_preference", false)
                .putBoolean("live_conversion_preference", false)
                .putBoolean("clipboard_preview_enable_preference", false)
                .putBoolean("clipboard_history_preference", false)
                .putBoolean("inline_suggestion_enabled_preference", true).commit())
            shell("ime enable $target")
            shell("ime set $target")
            test()
        } finally {
            val edit = prefs.edit().clear()
            saved.forEach { (key, value) -> when (value) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(key, value as Set<String>)
            } }
            check(edit.commit())
            shell("ime set $oldIme")
            if (!wasEnabled) shell("ime disable $target")
            ui.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
        }
    }

    @Test fun candidateTabsAndControlsSurviveRestartsAndFloatingRoundTrips() = withIsolatedIme {
        val landscape = InstrumentationRegistry.getArguments().getString("rotation") == "landscape"
        ui.setRotation(if (landscape) android.app.UiAutomation.ROTATION_FREEZE_90
            else android.app.UiAutomation.ROTATION_FREEZE_0)
        shell("am start -n ${context.packageName}/com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity")
        run {
            var originalIndicatorType: Class<*>? = null
            await("Initial keyboard not shown") { imeRoot() != null }
            onMain { originalIndicatorType = imeRoot()!!.findViewById<TabLayout>(R.id.candidate_tab_layout).tabSelectedIndicator.javaClass }
            for (skin in skins + KeyboardSkinId.DEFAULT) {
                for (floating in listOf(false, true, false)) {
                    check(prefs.edit().putString(KeyboardSkinId.PREFERENCE_KEY, skin.preferenceValue)
                        .putBoolean("keyboard_floating_preference", floating).commit())
                    onMain { hostActivity().restartEditorInput(true) }
                    val colors = CandidatePanelColors.resolve(context,
                        KeyboardSkinRegistry.find(skin)?.palette,
                        cupertinoClassic = skin == KeyboardSkinId.CUPERTINO_CLASSIC)
                    await("$skin floating=$floating did not apply") {
                        val root = imeRoot(floating) ?: return@await false
                        val tint = if (skin == KeyboardSkinId.DEFAULT)
                            context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color) else colors.icon
                        root.findViewById<ImageView>(R.id.suggestion_visibility)?.imageTintList?.defaultColor == tint
                    }
                    if (!floating) onMain {
                        val tabs = imeRoot()!!.findViewById<TabLayout>(R.id.candidate_tab_layout)
                        assertEquals(3, tabs.tabCount)
                        if (skin == KeyboardSkinId.CUPERTINO_CLASSIC) {
                            val strip = tabs.getChildAt(0) as ViewGroup
                            repeat(strip.childCount) { assertTrue(strip.getChildAt(it).background is StateListDrawable) }
                        } else assertEquals(originalIndicatorType, tabs.tabSelectedIndicator.javaClass)
                    }
                }
            }
        }
    }
}
