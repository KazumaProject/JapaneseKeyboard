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
class EnglishEnterContinuationDeviceTest {
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

    private fun runCase(floating: Boolean, enabled: Boolean = true, correctionEnabled: Boolean = false, block: (ActivityScenario<FastInputHostActivity>) -> Unit) {
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
                .putBoolean("english_floating_grammar_correction_preference", correctionEnabled)
                .putBoolean("zero_query_suggestion_preference", enabled)
                .commit())
            shell("input keyevent WAKEUP")
            shell("wm dismiss-keyguard")
            shell("ime enable $target")
            shell("ime set $target")
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                scenario.onActivity { it.restartEditorInput(clearText = false) }
                awaitStableKeyboard(floating)
                block(scenario)
            }
        } finally {
            if (previousIme.isNotBlank() && previousIme != "null") shell("ime set $previousIme")
            if (!wasEnabled) shell("ime disable $target")
            AppPreference.importAllFromJson(backup)
        }
    }

    private fun awaitStableKeyboard(floating: Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        var previous: List<Int>? = null
        var stableSamples = 0
        while (SystemClock.uptimeMillis() < deadline) {
            val geometry = onMain {
                val keyboard = view(if (floating) R.id.qwerty_view_floating else R.id.qwerty_view)
                val key = view(QwertyR.id.key_i)
                if (keyboard == null || key == null) null else listOf(keyboard, key).flatMap {
                    val location = IntArray(2).also(it::getLocationOnScreen)
                    listOf(location[0], location[1], it.width, it.height)
                }
            }
            stableSamples = if (geometry != null && geometry == previous) stableSamples + 1 else 0
            if (geometry != previous) android.util.Log.i("EnglishEnterSetup", "floating=$floating geometry=$geometry")
            if (stableSamples >= 6) return
            previous = geometry
            SystemClock.sleep(100)
        }
        error("QWERTY geometry did not settle: floating=$floating geometry=$previous")
    }

    @Suppress("UNCHECKED_CAST")
    private fun continuations() = IMEService::class.java.getDeclaredField("zeroQueryCandidates")
        .apply { isAccessible = true }.get(service()) as List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate>

    @Test fun enterConfirmationOffersAndCommitsRegisteredContinuation() {
        for (floating in listOf(false, true)) runCase(floating) { scenario ->
            type("ariana")
            scenario.onActivity { assertEquals("ariana", it.editText.text.toString()) }
            tap(QwertyR.id.key_return)
            await("Grande after Enter confirmation") { continuations().any { it.string == "Grande" } }
            scenario.onActivity {
                assertEquals("ariana", it.editText.text.toString())
                assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text))
            }
            fun grandeView(): android.widget.TextView? = WindowInspector.getGlobalWindowViews().asSequence()
                .flatMap { root ->
                    fun descendants(view: View): Sequence<View> = sequence {
                        yield(view)
                        if (view is android.view.ViewGroup) for (index in 0 until view.childCount) {
                            yieldAll(descendants(view.getChildAt(index)))
                        }
                    }
                    descendants(root)
                }.filterIsInstance<android.widget.TextView>()
                .firstOrNull { it.isShown && it.text.toString().trim() == "Grande" }
            await("visible Grande candidate") { grandeView() != null }
            onMain {
                generateSequence(checkNotNull(grandeView()) as View) { it.parent as? View }
                    .first { it.isClickable }.performClick()
            }
            scenario.onActivity { assertEquals("ariana Grande", it.editText.text.toString()) }
        }
    }

    @Test fun grammarAndContinuationsRemainUsableWhenBothEnabled() {
        for (floating in listOf(false, true)) runCase(floating, correctionEnabled = true) { scenario ->
            type("i go home yesterday")
            scenario.onActivity { assertEquals("i go home yesterday", it.editText.text.toString()) }
            tap(QwertyR.id.key_return)
            await("sentence correction with continuations enabled") { view(R.id.english_floating_correction_text) != null }
            tap(R.id.english_floating_correction_text)
            scenario.onActivity {
                assertEquals("i went home yesterday", it.editText.text.toString())
                it.editText.setText("")
            }
            type("ariana")
            scenario.onActivity { assertEquals("ariana", it.editText.text.toString()) }
            tap(QwertyR.id.key_return)
            await("Grande with grammar correction enabled") { continuations().any { it.string == "Grande" } }
        }
    }

    @Test fun disabledContinuationSettingKeepsTheCandidateListEmpty() = runCase(false, enabled = false) { scenario ->
        type("ariana")
        scenario.onActivity { assertEquals("ariana", it.editText.text.toString()) }
        tap(QwertyR.id.key_return)
        SystemClock.sleep(500)
        onMain { assertTrue(continuations().isEmpty()) }
        scenario.onActivity { assertEquals("ariana", it.editText.text.toString()) }
    }
}
