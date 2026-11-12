package com.kazumaproject.markdownhelperkeyboard

import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.inspector.WindowInspector
import android.view.inputmethod.BaseInputConnection
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.qwerty_keyboard.R as QwertyR
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt in with -e english_enter_test true using an isolated .englishenterqa application ID. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class EnglishEnterCorrectionDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context = ins.targetContext

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        var failure: Throwable? = null
        ins.runOnMainSync { try { result = block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST") return result as T
    }

    private fun await(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!onMain(condition)) {
            check(SystemClock.uptimeMillis() < deadline) { "Timed out waiting for $label" }
            SystemClock.sleep(25)
        }
    }

    private fun view(id: Int): View? {
        fun find(root: View): View? {
            if (!root.isShown) return null
            if (root.id == id && root.width > 0) return root
            if (root is android.view.ViewGroup) for (index in 0 until root.childCount) {
                find(root.getChildAt(index))?.let { return it }
            }
            return null
        }
        return WindowInspector.getGlobalWindowViews().asReversed().firstNotNullOfOrNull(::find)
    }

    private fun service(): IMEService = WindowInspector.getGlobalWindowViews().asSequence()
        .flatMap { root -> generateSequence(root.context) { (it as? ContextWrapper)?.baseContext } }
        .filterIsInstance<IMEService>().first()

    private fun tap(id: Int) {
        await("view $id") { view(id) != null }
        val bounds = onMain {
            val key = checkNotNull(view(id))
            check(key.getGlobalVisibleRect(Rect()))
            val location = IntArray(2)
            key.getLocationOnScreen(location)
            Rect(location[0], location[1], location[0] + key.width, location[1] + key.height)
        }
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(ins.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        SystemClock.sleep(100)
    }

    private fun type(text: String) {
        text.forEach { letter ->
            tap(context.resources.getIdentifier(if (letter == ' ') "key_space" else "key_$letter", "id", context.packageName))
        }
    }

    private fun runCase(floating: Boolean, enabled: Boolean = true, block: (ActivityScenario<FastInputHostActivity>) -> Unit) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("english_enter_test") == "true")
        check(context.packageName.endsWith(".englishenterqa"))
        AppPreference.init(context)
        val backup = AppPreference.exportAllToJson()
        val previousIme = shell("settings get secure default_input_method")
        val target = "${context.packageName}/${IMEService::class.java.name}"
        val wasEnabled = shell("ime list -s").lines().contains(target)
        try {
            check(PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("keyboard_order_preference", "[\"QWERTY\"]")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("keyboard_floating_preference", floating)
                .putBoolean("qwerty_english_direct_input_preference", false)
                .putBoolean("qwerty_glide_input_preference", false)
                .putBoolean("live_conversion_preference", false)
                .putBoolean("learn_dictionary_preference", false)
                .putBoolean("english_prediction_enable_preference", true)
                .putBoolean("english_floating_grammar_correction_preference", enabled)
                .putBoolean("zero_query_suggestion_preference", false)
                .commit())
            shell("input keyevent WAKEUP")
            shell("wm dismiss-keyguard")
            shell("ime enable $target")
            shell("ime set $target")
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                await("QWERTY input view") { view(if (floating) R.id.qwerty_view_floating else R.id.qwerty_view) != null && view(QwertyR.id.key_a) != null }
                block(scenario)
            }
        } finally {
            if (previousIme.isNotBlank() && previousIme != "null") shell("ime set $previousIme")
            if (!wasEnabled) shell("ime disable $target")
            AppPreference.importAllFromJson(backup)
        }
    }

    @Test fun enterConfirmationOffersAndAppliesSentenceCorrection() {
        for (floating in listOf(false, true)) runCase(floating) { scenario ->
            type("i go home yesterday")
            scenario.onActivity { assertEquals("i go home yesterday", it.editText.text.toString()) }
            tap(QwertyR.id.key_return)
            await("sentence correction after Enter") { view(R.id.english_floating_correction_text) != null }
            scenario.onActivity {
                assertEquals("i go home yesterday", it.editText.text.toString())
                assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text))
            }
            tap(R.id.english_floating_correction_text)
            scenario.onActivity {
                assertEquals("i went home yesterday", it.editText.text.toString())
                assertEquals(it.editText.length(), it.editText.selectionStart)
            }
        }
    }

    @Test fun newlineChecksTheCompletedLineAndKeepsTheNewline() = runCase(false) { scenario ->
        type("i go home yesterday")
        scenario.onActivity { assertEquals("i go home yesterday", it.editText.text.toString()) }
        tap(QwertyR.id.key_return)
        tap(QwertyR.id.key_return)
        await("correction after a newline") { view(R.id.english_floating_correction_text) != null }
        tap(R.id.english_floating_correction_text)
        scenario.onActivity {
            assertEquals("i went home yesterday\n", it.editText.text.toString())
            assertEquals(it.editText.length(), it.editText.selectionStart)
        }
    }

    @Test fun disabledCorrectionSettingDoesNotOfferOrApplyACorrection() = runCase(false, enabled = false) { scenario ->
        type("i go home yesterday")
        scenario.onActivity { assertEquals("i go home yesterday", it.editText.text.toString()) }
        tap(QwertyR.id.key_return)
        SystemClock.sleep(500)
        onMain { assertNull(view(R.id.english_floating_correction_text)) }
        scenario.onActivity { assertEquals("i go home yesterday", it.editText.text.toString()) }
    }

    @Test fun periodOnlyCommitChecksTheCompletedSentence() = runCase(false) { scenario ->
        type("i go home yesterday")
        scenario.onActivity { assertEquals("i go home yesterday", it.editText.text.toString()) }
        tap(QwertyR.id.key_return)
        // A punctuation-only commit must re-evaluate the completed sentence.
        onMain { service().commitText(".", 1) }
        await("correction for sentence ending in a period") { view(R.id.english_floating_correction_text) != null }
        tap(R.id.english_floating_correction_text)
        scenario.onActivity { assertEquals("i went home yesterday.", it.editText.text.toString()) }
    }
}
