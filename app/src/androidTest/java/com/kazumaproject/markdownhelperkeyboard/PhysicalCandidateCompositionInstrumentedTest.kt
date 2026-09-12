package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.view.accessibility.AccessibilityNodeInfo
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.text.style.BackgroundColorSpan
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWordMatchMode
import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplate
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.BeforeClass
import org.junit.AfterClass
import org.junit.runner.RunWith

/** Sends real hardware-device KeyEvents through the selected IME to a standard EditText. */
@RunWith(AndroidJUnit4::class)
class PhysicalCandidateCompositionInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private lateinit var host: ActivityScenario<FastInputHostActivity>
    private var keyboardId = 0

    @Test fun bunsetsuFirstConversionAndCandidateChangePreserveOtherSegments() = withKeyboard(bunsetsu = true) {
        assertBunsetsuConversionAndNavigation()
    }

    @Test fun bunsetsuConversionAlsoWorksWithCandidateOrderingEnabled() =
        withKeyboard(bunsetsu = true, candidateOrdering = true) {
            assertBunsetsuConversionAndNavigation()
        }

    @Test fun bunsetsuRapidNavigationAndCancellationDoNotRestoreStaleConversion() = withKeyboard(bunsetsu = true) {
        type("ashitahatoukyouniikimasu")
        awaitText("あしたはとうきょうにいきます")
        key(KeyEvent.KEYCODE_SPACE)
        awaitText("明日は東京に行きます")
        repeat(5) {
            key(KeyEvent.KEYCODE_DPAD_RIGHT, settleMillis = 0)
            key(KeyEvent.KEYCODE_DPAD_LEFT, settleMillis = 0)
        }
        await { focusedRange().first == 0 }
        assertEquals("明日は東京に行きます", text())
        key(KeyEvent.KEYCODE_DPAD_RIGHT, settleMillis = 0)
        key(KeyEvent.KEYCODE_SPACE, settleMillis = 0)
        key(KeyEvent.KEYCODE_ESCAPE, settleMillis = 0)
        awaitText("あしたはとうきょうにいきます")
        SystemClock.sleep(1000)
        assertEquals("あしたはとうきょうにいきます", text())
        key(KeyEvent.KEYCODE_DEL)
        awaitText("あしたはとうきょうにいきま")
    }

    @Test fun bunsetsuTemplatesRemainSelectableAndCommitWithSurroundingSegments() {
        val templates = listOf(
            UserTemplate(word = "文節テスト定型文Ａ", reading = "とうきょうに", posIndex = 0, posScore = 100),
            UserTemplate(word = "文節テスト定型文Ｂ", reading = "とうきょうに", posIndex = 0, posScore = 200),
        )
        withCandidateFixtures(templates = templates) {
            withKeyboard(bunsetsu = true) {
                type("ashitahatoukyouniikimasu")
                awaitText("あしたはとうきょうにいきます")
                key(KeyEvent.KEYCODE_SPACE)
                awaitText("明日は東京に行きます")
                key(KeyEvent.KEYCODE_DPAD_RIGHT)
                await { focusedRange() == (3 until 6) }
                key(KeyEvent.KEYCODE_SPACE)
                awaitText("明日は文節テスト定型文Ａ行きます")
                key(KeyEvent.KEYCODE_SPACE)
                awaitText("明日は文節テスト定型文Ｂ行きます")
                key(KeyEvent.KEYCODE_DPAD_RIGHT)
                key(KeyEvent.KEYCODE_DPAD_LEFT)
                assertEquals("明日は文節テスト定型文Ｂ行きます", text())
                key(KeyEvent.KEYCODE_ENTER)
                awaitText("明日は文節テスト定型文Ｂ行きます")
                host.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
            }
        }
    }

    @Test fun bunsetsuExactNgWordIsExcludedFromUnfocusedSegmentAndCommit() {
        assertExactNgWordExcluded("とうきょうに", "東京に", moveRight = true)
    }

    @Test fun bunsetsuExactNgWordIsExcludedFromFirstSegmentAndCommit() {
        assertExactNgWordExcluded("あしたは", "明日は", moveRight = false)
    }

    private fun assertExactNgWordExcluded(reading: String, blocked: String, moveRight: Boolean) {
        withCandidateFixtures(ngWord = NgWord(yomi = reading, tango = blocked, matchMode = NgWordMatchMode.EXACT)) {
            withKeyboard(bunsetsu = true) {
                type("ashitahatoukyouniikimasu")
                awaitText("あしたはとうきょうにいきます")
                key(KeyEvent.KEYCODE_SPACE)
                await { focusedRange().first == 0 && text().endsWith("行きます") }
                assertFalse("Blocked output appeared on initial conversion: ${text()}", text().contains(blocked))
                if (moveRight) {
                    assertTrue(text().startsWith("明日は"))
                    key(KeyEvent.KEYCODE_DPAD_RIGHT)
                    await { focusedRange().first == 3 }
                } else {
                    assertTrue(text().endsWith("東京に行きます"))
                }
                repeat(6) {
                    key(KeyEvent.KEYCODE_SPACE)
                    assertFalse("Blocked output returned while cycling: ${text()}", text().contains(blocked))
                    assertTrue(text().endsWith("行きます"))
                }
                val converted = text()
                key(KeyEvent.KEYCODE_ENTER)
                awaitText(converted)
                host.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
            }
        }
    }

    private fun withCandidateFixtures(
        templates: List<UserTemplate> = emptyList(),
        ngWord: NgWord? = null,
        block: () -> Unit,
    ) {
        val db = EntryPointAccessors.fromApplication(
            instrumentation.targetContext.applicationContext, BunsetsuTestDatabaseEntryPoint::class.java,
        ).database()
        val insertedTemplates = mutableListOf<Int>()
        var insertedNgWord: NgWord? = null
        try {
            runBlocking {
                for (template in templates) {
                    val existing = db.userTemplateDao().searchByReadingExactSuspend(template.reading, Int.MAX_VALUE)
                    check(existing.none { it.word == template.word }) { "Fixture already exists" }
                    db.userTemplateDao().insert(template)
                    insertedTemplates += db.userTemplateDao().searchByReadingExactSuspend(template.reading, Int.MAX_VALUE)
                        .single { it.word == template.word }.id
                }
                if (ngWord != null) {
                    check(db.ngWordDao().find(ngWord.yomi, ngWord.tango) == null) { "NG fixture already exists" }
                    insertedNgWord = ngWord.copy(id = db.ngWordDao().insert(ngWord).toInt())
                }
            }
            block()
        } finally {
            runBlocking {
                insertedTemplates.forEach { db.userTemplateDao().delete(it) }
                insertedNgWord?.let { db.ngWordDao().delete(it) }
            }
        }
    }

    private fun assertBunsetsuConversionAndNavigation() {
        type("ashitahatoukyouniikimasu")
        awaitText("あしたはとうきょうにいきます")
        key(KeyEvent.KEYCODE_SPACE)
        awaitText("明日は東京に行きます")
        await { focusedRange() == (0 until 3) }
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        await { focusedRange() == (3 until 6) }
        assertEquals("明日は東京に行きます", text())
        key(KeyEvent.KEYCODE_SPACE)
        await { text() != "明日は東京に行きます" }
        val changed = text()
        assertTrue("Only the second segment may change: $changed", changed.startsWith("明日は") && changed.endsWith("行きます"))
        val secondRange = focusedRange()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        await { focusedRange().first == secondRange.last + 1 }
        assertEquals(changed, text())
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        await { focusedRange() == secondRange }
        assertEquals(changed, text())
        key(KeyEvent.KEYCODE_ENTER)
        awaitText(changed)
        host.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
    }

    private fun focusedRange(): IntRange {
        var range = IntRange.EMPTY
        host.onActivity { activity ->
            val value = activity.editText.text
            val span = value.getSpans(0, value.length, BackgroundColorSpan::class.java).singleOrNull()
            if (span != null) range = value.getSpanStart(span) until value.getSpanEnd(span)
        }
        return range
    }

    @Test fun cursorTailSurvivesPreviewCancelAndCommit() = withKeyboard {
        type("ashitaha")
        awaitText("あしたは")
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        key(KeyEvent.KEYCODE_SPACE)
        await { selectedPrefixWithTail("は") }
        assertTrue(text().endsWith("は"))
        key(KeyEvent.KEYCODE_ESCAPE)
        awaitText("あしたは")
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        key(KeyEvent.KEYCODE_SPACE)
        await { selectedPrefixWithTail("は") }
        val preview = text()
        key(KeyEvent.KEYCODE_ENTER)
        // Remaining reading may be automatically converted, but must not disappear.
        await { text().length >= preview.length && text().startsWith(preview.dropLast(1)) }
    }

    @Test fun typingAfterPartialCandidateCommitsTailBeforeNewRomaji() = withKeyboard {
        selectPartialCandidate()
        val preview = text()
        type("a")
        awaitText(preview + "あ")
        host.onActivity {
            assertEquals(preview.length, BaseInputConnection.getComposingSpanStart(it.editText.text))
        }
    }

    @Test fun typingAfterPartialCandidateCommitsTailBeforeNewKana() = withKeyboard(kana = true) {
        // JIS: 3=あ, D=し, Q=た, F=は. Move は outside the conversion query.
        listOf(KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_F).forEach { key(it) }
        awaitText("あしたは")
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        key(KeyEvent.KEYCODE_SPACE)
        await { selectedPrefixWithTail("は") }
        val preview = text()
        key(KeyEvent.KEYCODE_3)
        awaitText(preview + "あ")
    }

    @Test fun narrowedCandidateCanBeCancelledWithoutLosingSource() = withKeyboard {
        selectPartialCandidate()
        key(KeyEvent.KEYCODE_ESCAPE)
        awaitText("あしたはれるといいですねはれた")
        key(KeyEvent.KEYCODE_DEL)
        awaitText("あしたはれるといいですねはれ")
    }

    @Test fun bunsetsuWidthChangesPreserveReadingOnCancel() = withKeyboard(bunsetsu = true) {
        type("ashitaharerutoiidesunehareta")
        awaitText("あしたはれるといいですねはれた")
        key(KeyEvent.KEYCODE_SPACE)
        SystemClock.sleep(800)
        key(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_SHIFT_ON)
        key(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_SHIFT_ON)
        key(KeyEvent.KEYCODE_ESCAPE)
        awaitText("あしたはれるといいですねはれた")
    }

    @Test fun clickingPartialCandidateKeepsRemainderForNextCommit() = withKeyboard {
        selectPartialCandidate()
        val preview = text()
        var selected = ""
        host.onActivity { activity ->
            val value = activity.editText.text
            val span = value.getSpans(0, value.length, BackgroundColorSpan::class.java)
                .first { value.getSpanStart(it) == 0 && value.getSpanEnd(it) in 1 until value.length }
            selected = value.substring(0, value.getSpanEnd(span))
        }
        val node = automation.windows.asSequence().mapNotNull { it.root }
            .flatMap { it.findAccessibilityNodeInfosByText(selected).asSequence() }
            .first { it.text?.toString() == selected }
        var clickable: AccessibilityNodeInfo? = node
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        assertTrue(clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        SystemClock.sleep(500)
        key(KeyEvent.KEYCODE_ESCAPE)
        awaitText(preview)
        key(KeyEvent.KEYCODE_ENTER)
        awaitText(preview)
        host.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
    }

    @Test fun editorRestartDoesNotRevivePendingRemainder() = withKeyboard {
        selectPartialCandidate()
        key(KeyEvent.KEYCODE_ENTER)
        host.onActivity { it.restartEditorInput(true) }
        SystemClock.sleep(500)
        type("a")
        awaitText("あ")
        SystemClock.sleep(500)
        assertEquals("あ", text())
    }

    @Test fun typingImmediatelyAfterPartialEnterDoesNotDropKeyOrReading() = withKeyboard {
        selectPartialCandidate()
        val preview = text()
        val selectedEnd = focusedRange().last + 1
        val prefix = preview.take(selectedEnd)
        val tail = preview.drop(selectedEnd)
        // Partial Enter may convert the entire remaining reading before A arrives.
        // Check both exact fixture outputs without allowing a lost prefix, tail, or new key.
        val convertedTail = when (tail) {
            "はれるといいですねはれた" -> "晴れると良いですね晴れた"
            "はれた" -> "晴れた"
            else -> error("Unexpected partial-candidate fixture: prefix=[$prefix], tail=[$tail]")
        }
        val expected = setOf(preview + "あ", prefix + convertedTail + "あ")
        key(KeyEvent.KEYCODE_ENTER, settleMillis = 0)
        key(KeyEvent.KEYCODE_A)
        await { text() in expected }
    }

    private fun selectPartialCandidate() {
        type("ashitaharerutoiidesunehareta")
        awaitText("あしたはれるといいですねはれた")
        key(KeyEvent.KEYCODE_SPACE)
        repeat(100) {
            if (selectedPrefixWithTail("はれた")) return
            key(KeyEvent.KEYCODE_DPAD_DOWN)
        }
        fail("No partial candidate found: ${text()}")
    }

    private fun selectedPrefixWithTail(tail: String): Boolean {
        var selected = false
        host.onActivity { activity ->
            val text = activity.editText.text
            selected = text.toString().endsWith(tail) &&
                text.getSpans(0, text.length, BackgroundColorSpan::class.java).any {
                    text.getSpanStart(it) == 0 && text.getSpanEnd(it) in 1 until text.length
                }
        }
        return selected
    }

    private fun type(romaji: String) = romaji.forEach { key(KeyEvent.KEYCODE_A + (it - 'a')) }

    private fun key(code: Int, meta: Int = 0, settleMillis: Long = 160) {
        val time = SystemClock.uptimeMillis()
        fun inject(keyCode: Int, action: Int, metaState: Int) {
            assertTrue(automation.injectInputEvent(KeyEvent(
                time, SystemClock.uptimeMillis(), action, keyCode, 0, metaState, keyboardId,
                0, 0, InputDevice.SOURCE_KEYBOARD
            ), true))
        }
        val shifted = meta and KeyEvent.META_SHIFT_ON != 0
        if (shifted) inject(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_DOWN, meta)
        inject(code, KeyEvent.ACTION_DOWN, meta)
        inject(code, KeyEvent.ACTION_UP, meta)
        if (shifted) inject(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_UP, 0)
        SystemClock.sleep(settleMillis)
    }

    private fun text(): String {
        var value = ""
        host.onActivity { value = it.editText.text.toString() }
        return value
    }

    private fun awaitText(expected: String) = await { text() == expected }

    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        fail("Timed out; editor=[${text()}]")
    }

    companion object {
        private lateinit var originalIme: String
        private lateinit var targetIme: String
        private lateinit var originalShowWithHardware: String
        private var wasEnabled = false

        private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ).bufferedReader().use { it.readText().trim() }

        private fun sameIme(first: String, second: String): Boolean =
            ComponentName.unflattenFromString(first) == ComponentName.unflattenFromString(second)

        @JvmStatic @BeforeClass fun selectIme() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val component = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
            targetIme = shell("ime list -a -s").lines().first { sameIme(it, component) }
            originalIme = shell("settings get secure default_input_method")
            originalShowWithHardware = shell("settings get secure show_ime_with_hard_keyboard")
            wasEnabled = shell("ime list -s").lines().any { sameIme(it, targetIme) }
            shell("settings put secure show_ime_with_hard_keyboard 1")
            shell("ime enable $targetIme")
            shell("ime set $targetIme")
            assertTrue("Test IME was not selected", sameIme(shell("settings get secure default_input_method"), targetIme))
        }

        @JvmStatic @AfterClass fun restoreIme() {
            if (originalIme.isNotEmpty() && originalIme != "null") shell("ime set $originalIme")
            if (!wasEnabled && !sameIme(originalIme, targetIme)) shell("ime disable $targetIme")
            if (originalShowWithHardware == "null") {
                shell("settings delete secure show_ime_with_hard_keyboard")
            } else {
                shell("settings put secure show_ime_with_hard_keyboard $originalShowWithHardware")
            }
        }
    }

    private fun withKeyboard(kana: Boolean = false, bunsetsu: Boolean = false, candidateOrdering: Boolean = false, block: () -> Unit) {
        keyboardId = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice)
            .firstOrNull { !it.isVirtual && it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC }
            ?.id ?: error("A physical or emulator hardware keyboard is required")
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val context = instrumentation.targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val values = mapOf<String, Any>(
            "conversion_bunsetsu_separation_preference" to true,
            "conversion_bunsetsu_cursor_move_preference" to bunsetsu,
            "candidate_order_override_enable_preference" to candidateOrdering,
            "learn_dictionary_preference" to false,
            "user_template_preference" to true,
            "ng_word_enable_preference" to true,
            "physical_keyboard_input_mode_preference" to if (kana) "kana" else "romaji",
            "live_conversion_preference" to false,
            "sumire_keymap_guide_japanese" to false,
            "sumire_keymap_guide_modes_migrated" to true,
        )
        val previous = values.keys.associateWith { prefs.all[it] }
        try {
            prefs.edit().apply {
                values.forEach { (key, value) ->
                    when (value) { is Boolean -> putBoolean(key, value); is String -> putString(key, value) }
                }
            }.commit()
            host = ActivityScenario.launch(FastInputHostActivity::class.java)
            host.onActivity { it.restartEditorInput(true) }
            // Cold dictionary/romaji initialization may outlive Activity creation.
            // Prove that a key reaches Japanese composition before testing conversion.
            val readyBy = SystemClock.uptimeMillis() + 30000
            var ready = false
            var observed = ""
            while (!ready && SystemClock.uptimeMillis() < readyBy) {
                SystemClock.sleep(500)
                key(KeyEvent.KEYCODE_KANA)
                key(if (kana) KeyEvent.KEYCODE_3 else KeyEvent.KEYCODE_A)
                observed = text()
                ready = observed == "あ"
                host.onActivity { it.restartEditorInput(true) }
            }
            assertTrue("Japanese hardware input did not initialize: [$observed]", ready)
            SystemClock.sleep(500)
            block()
        } finally {
            if (::host.isInitialized) host.close()
            prefs.edit().apply {
                previous.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                        null -> remove(key)
                    }
                }
            }.commit()
        }
    }
}
