package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.app.KeyguardManager
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.os.Bundle
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device instrumentation regression tests for rapid input while the IME candidate area changes.
 *
 * They can run on a physical device or an emulator. Emulator results must not be reported as
 * physical-device results. These tests intentionally do not change the product's touch routing.
 * They also preserve and restore every preference, including the independently persisted
 * empty-candidate height and the visible-candidate height for each column count.
 */
@RunWith(AndroidJUnit4::class)
class FastInputMatrixInstrumentedTest {
    private val instrumentation: Instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val uiAutomation: UiAutomation
        get() = instrumentation.uiAutomation

    @Test
    fun flickEditorPreviewFunctionalAndPerformanceOnPhysicalDevice() {
        runPhysicalDeviceSession("flick-editor-preview") { session ->
            var scenario: ActivityScenario<FastInputHostActivity>? = null
            try {
                scenario = launchHost(session.context)
                rotateAndVerify(TestOrientation.PORTRAIT)
                val defaultPreviewBackgroundColor = session.context.getColor(
                    com.kazumaproject.core.R.color.char_in_edit_color
                )

                val sumireStyles = listOf(
                    "default",
                    "circle",
                    "second-flick",
                    "third-flick",
                    "sumire",
                )
                val functionalCases = buildList {
                    add(TestKeyboard.TENKEY to null)
                    sumireStyles.forEach { style -> add(TestKeyboard.SUMIRE to style) }
                }

                functionalCases.forEach { (keyboard, style) ->
                    val testCase = previewTestCase(keyboard)
                    applyCasePreferences(session.preferences, testCase)
                    check(
                        session.preferences.edit()
                            .putBoolean("flick_editor_preview_preference", true)
                            .putBoolean("theme_custom_input_color_enable", false)
                            .putString("sumire_keyboard_style_preference", style ?: "default")
                            .putString("sumire_input_method_preference", "switch-mode-effective")
                            .putString("keyboard_touch_effect_type_preference", "none")
                            .commit()
                    )
                    ensureTargetImeSelected(session)
                    restartInput(scenario)
                    SystemClock.sleep(IME_LAYOUT_SETTLE_MS)
                    val geometry = awaitStableGeometry(
                        keyboard = keyboard,
                        requireCandidateContent = false,
                    )

                    val downTime = SystemClock.uptimeMillis()
                    assertTrue(
                        "DOWN injection failed for $keyboard/$style",
                        injectSinglePointerEvent(
                            downTime,
                            MotionEvent.ACTION_DOWN,
                            geometry.prime.center,
                        ),
                    )
                    assertEquals(
                        "DOWN preview mismatch for $keyboard/$style",
                        keyboard.primeText,
                        awaitEditorText(scenario) { it.isNotEmpty() },
                    )
                    assertEditorPreviewDecoration(
                        scenario = scenario,
                        expectedText = keyboard.primeText,
                        expectedBackgroundColor = defaultPreviewBackgroundColor,
                        caseName = "$keyboard/$style DOWN",
                    )
                    assertTrue(
                        "Candidate generation started before UP for $keyboard/$style",
                        findCandidateState().texts.isEmpty(),
                    )
                    assertTrue(
                        "CANCEL injection failed for $keyboard/$style",
                        injectSinglePointerEvent(
                            downTime,
                            MotionEvent.ACTION_CANCEL,
                            geometry.prime.center,
                        ),
                    )
                    assertEquals(
                        "Preview remained after CANCEL for $keyboard/$style",
                        "",
                        awaitEditorText(scenario) { it.isEmpty() },
                    )
                }

                listOf(TestKeyboard.TENKEY, TestKeyboard.SUMIRE).forEach { keyboard ->
                    val testCase = previewTestCase(keyboard)
                    applyCasePreferences(session.preferences, testCase)
                    check(
                        session.preferences.edit()
                            .putBoolean("flick_editor_preview_preference", true)
                            .putBoolean("theme_custom_input_color_enable", true)
                            .putInt(
                                "theme_custom_pre_edit_bg_color",
                                PREVIEW_TEST_BACKGROUND_COLOR,
                            )
                            .putString("sumire_keyboard_style_preference", "default")
                            .commit()
                    )
                    restartInput(scenario)
                    SystemClock.sleep(IME_LAYOUT_SETTLE_MS)
                    val geometry = awaitStableGeometry(keyboard, false)
                    val end = PointF(
                        geometry.prime.center.x + geometry.prime.width * 0.70f,
                        geometry.prime.center.y,
                    )
                    val downTime = SystemClock.uptimeMillis()
                    assertTrue(
                        injectSinglePointerEvent(
                            downTime,
                            MotionEvent.ACTION_DOWN,
                            geometry.prime.center,
                        )
                    )
                    assertEquals(
                        keyboard.primeText,
                        awaitEditorText(scenario) { it.isNotEmpty() },
                    )
                    assertTrue(
                        injectSinglePointerEvent(downTime, MotionEvent.ACTION_MOVE, end)
                    )
                    val movedPreview = awaitEditorText(scenario) {
                        it.isNotEmpty() && it != keyboard.primeText
                    }
                    assertTrue("MOVE did not replace preview for $keyboard", movedPreview.isNotEmpty())
                    assertEditorPreviewDecoration(
                        scenario = scenario,
                        expectedText = movedPreview,
                        expectedBackgroundColor = PREVIEW_TEST_BACKGROUND_COLOR,
                        caseName = "$keyboard MOVE",
                    )
                    assertTrue(
                        injectSinglePointerEvent(downTime, MotionEvent.ACTION_UP, end)
                    )
                    assertEquals(
                        "UP result differed from MOVE preview for $keyboard",
                        movedPreview,
                        awaitTextSettled(scenario),
                    )
                }

                listOf(TestKeyboard.TENKEY, TestKeyboard.SUMIRE).forEach { keyboard ->
                    val testCase = previewTestCase(keyboard)
                    applyCasePreferences(session.preferences, testCase)
                    check(
                        session.preferences.edit()
                            .putBoolean("flick_editor_preview_preference", false)
                            .putString("sumire_keyboard_style_preference", "default")
                            .commit()
                    )
                    restartInput(scenario)
                    SystemClock.sleep(IME_LAYOUT_SETTLE_MS)
                    val geometry = awaitStableGeometry(keyboard, false)
                    val downTime = SystemClock.uptimeMillis()
                    assertTrue(
                        injectSinglePointerEvent(
                            downTime,
                            MotionEvent.ACTION_DOWN,
                            geometry.prime.center,
                        )
                    )
                    SystemClock.sleep(PREVIEW_HOLD_ASSERT_MS)
                    assertEquals("OFF changed editor on DOWN for $keyboard", "", readText(scenario))
                    assertTrue(
                        injectSinglePointerEvent(
                            downTime,
                            MotionEvent.ACTION_UP,
                            geometry.prime.center,
                        )
                    )
                    assertEquals(keyboard.primeText, awaitTextSettled(scenario))
                }

                listOf(TestKeyboard.TENKEY, TestKeyboard.SUMIRE).forEach { keyboard ->
                    applyCasePreferences(session.preferences, previewTestCase(keyboard))
                    check(
                        session.preferences.edit()
                            .putBoolean("flick_editor_preview_preference", true)
                            .putBoolean("theme_custom_input_color_enable", true)
                            .putInt(
                                "theme_custom_pre_edit_bg_color",
                                PREVIEW_TEST_BACKGROUND_COLOR,
                            )
                            .putString("sumire_keyboard_style_preference", "default")
                            .putString("sumire_input_method_preference", "switch-mode-effective")
                            .commit()
                    )
                    restartInput(scenario)
                    SystemClock.sleep(IME_LAYOUT_SETTLE_MS)
                    val geometry = awaitStableGeometry(keyboard, false)
                    assertTrue("First tail setup tap failed for $keyboard", injectTap(geometry.prime.center))
                    assertTrue(
                        "Second tail setup tap failed for $keyboard",
                        injectTap(requireNotNull(geometry.neighbor).center),
                    )
                    assertEquals(
                        "Tail setup text mismatch for $keyboard",
                        "あな",
                        awaitEditorText(scenario) { it == "あな" },
                    )
                    assertTrue(
                        "Cursor-left tap failed for $keyboard",
                        injectTap(findCursorLeftBounds(keyboard).center),
                    )

                    val movePoint = PointF(
                        geometry.prime.center.x + geometry.prime.width * 0.70f,
                        geometry.prime.center.y,
                    )
                    val cancelDownTime = SystemClock.uptimeMillis()
                    assertTrue(
                        injectSinglePointerEvent(
                            cancelDownTime,
                            MotionEvent.ACTION_DOWN,
                            geometry.prime.center,
                        )
                    )
                    assertEquals(
                        "Tail was not retained on DOWN for $keyboard",
                        "ああな",
                        awaitEditorText(scenario) { it == "ああな" },
                    )
                    assertEditorPreviewDecoration(
                        scenario = scenario,
                        expectedText = "ああな",
                        expectedBackgroundColor = PREVIEW_TEST_BACKGROUND_COLOR,
                        expectedBackgroundEnd = 2,
                        caseName = "$keyboard tail DOWN",
                    )
                    assertTrue(
                        injectSinglePointerEvent(
                            cancelDownTime,
                            MotionEvent.ACTION_MOVE,
                            movePoint,
                        )
                    )
                    val movedWithTail = awaitEditorText(scenario) { text ->
                        text.length == 3 &&
                            text.startsWith("あ") &&
                            text.endsWith("な") &&
                            text != "ああな"
                    }
                    assertEditorPreviewDecoration(
                        scenario = scenario,
                        expectedText = movedWithTail,
                        expectedBackgroundColor = PREVIEW_TEST_BACKGROUND_COLOR,
                        expectedBackgroundEnd = 2,
                        caseName = "$keyboard tail MOVE",
                    )
                    assertTrue(
                        injectSinglePointerEvent(
                            cancelDownTime,
                            MotionEvent.ACTION_CANCEL,
                            movePoint,
                        )
                    )
                    assertEquals(
                        "Tail preview did not restore after CANCEL for $keyboard",
                        "あな",
                        awaitEditorText(scenario) { it == "あな" },
                    )

                    val commitDownTime = SystemClock.uptimeMillis()
                    assertTrue(
                        injectSinglePointerEvent(
                            commitDownTime,
                            MotionEvent.ACTION_DOWN,
                            geometry.prime.center,
                        )
                    )
                    assertTrue(
                        injectSinglePointerEvent(
                            commitDownTime,
                            MotionEvent.ACTION_MOVE,
                            movePoint,
                        )
                    )
                    val commitPreview = awaitEditorText(scenario) { text ->
                        text.length == 3 &&
                            text.startsWith("あ") &&
                            text.endsWith("な") &&
                            text != "ああな"
                    }
                    assertTrue(
                        injectSinglePointerEvent(
                            commitDownTime,
                            MotionEvent.ACTION_UP,
                            movePoint,
                        )
                    )
                    assertEquals(
                        "UP did not insert before stringInTail for $keyboard",
                        commitPreview,
                        awaitTextSettled(scenario),
                    )
                }

                val performanceLines = mutableListOf<String>()
                applyCasePreferences(session.preferences, previewTestCase(TestKeyboard.TENKEY))
                for (enabled in listOf(false, true)) {
                    check(
                        session.preferences.edit()
                            .putBoolean("flick_editor_preview_preference", enabled)
                            .putString("keyboard_touch_effect_type_preference", "none")
                            .commit()
                    )
                    val candidateSamplesNs = LongArray(PREVIEW_CANDIDATE_SAMPLES)
                    repeat(PREVIEW_CANDIDATE_WARMUP + PREVIEW_CANDIDATE_SAMPLES) { index ->
                        restartInput(scenario)
                        val trialGeometry = awaitStableGeometry(TestKeyboard.TENKEY, false)
                        val downTime = SystemClock.uptimeMillis()
                        assertTrue(
                            injectSinglePointerEvent(
                                downTime,
                                MotionEvent.ACTION_DOWN,
                                trialGeometry.prime.center,
                            )
                        )
                        if (enabled) {
                            assertEquals(
                                TestKeyboard.TENKEY.primeText,
                                awaitEditorText(scenario) { it.isNotEmpty() },
                            )
                        }
                        assertTrue(
                            "Candidates changed before UP (enabled=$enabled)",
                            findCandidateState().texts.isEmpty(),
                        )
                        val upStartedNs = SystemClock.elapsedRealtimeNanos()
                        assertTrue(
                            injectSinglePointerEvent(
                                downTime,
                                MotionEvent.ACTION_UP,
                                trialGeometry.prime.center,
                            )
                        )
                        val candidateLatencyNs = awaitCandidateVisibleLatencyNs(upStartedNs)
                        if (index >= PREVIEW_CANDIDATE_WARMUP) {
                            candidateSamplesNs[index - PREVIEW_CANDIDATE_WARMUP] =
                                candidateLatencyNs
                        }
                    }
                    val sortedCandidateSamples = candidateSamplesNs.sorted()
                    val candidateLine = buildString {
                        append("enabled=$enabled ")
                        append("samples=$PREVIEW_CANDIDATE_SAMPLES ")
                        append("candidateLatencyMeanMs=")
                        append(candidateSamplesNs.average() / 1_000_000.0)
                        append(" candidateLatencyP50Ms=")
                        append(
                            sortedCandidateSamples[sortedCandidateSamples.size / 2] /
                                1_000_000.0
                        )
                        append(" candidateLatencyP95Ms=")
                        append(
                            sortedCandidateSamples[
                                ((sortedCandidateSamples.size - 1) * 0.95).toInt()
                            ] / 1_000_000.0
                        )
                    }
                    performanceLines += candidateLine
                    Log.i(TAG, "FLICK_EDITOR_PREVIEW_CONVERSION $candidateLine")
                    sendProgress("FLICK_EDITOR_PREVIEW_CONVERSION $candidateLine\n")

                    restartInput(scenario)
                    SystemClock.sleep(IME_LAYOUT_SETTLE_MS)
                    val geometry = awaitStableGeometry(TestKeyboard.TENKEY, false)
                    val performanceMovePoint = PointF(
                        geometry.prime.center.x + geometry.prime.width * 0.70f,
                        geometry.prime.center.y,
                    )
                    repeat(PREVIEW_PERFORMANCE_WARMUP_GESTURES) {
                        injectPreviewCancelGesture(geometry.prime.center, performanceMovePoint)
                    }
                    Runtime.getRuntime().gc()
                    SystemClock.sleep(PREVIEW_GC_SETTLE_MS)
                    val allocatedBefore = runtimeStat("art.gc.bytes-allocated")
                    val pssBeforeKb = Debug.getPss().toLong()
                    val startedNs = SystemClock.elapsedRealtimeNanos()
                    repeat(PREVIEW_PERFORMANCE_GESTURES) {
                        assertTrue(
                            injectPreviewCancelGesture(
                                geometry.prime.center,
                                performanceMovePoint,
                            )
                        )
                    }
                    val elapsedNs = SystemClock.elapsedRealtimeNanos() - startedNs
                    val allocatedAfter = runtimeStat("art.gc.bytes-allocated")
                    val pssImmediateKb = Debug.getPss().toLong()
                    Runtime.getRuntime().gc()
                    SystemClock.sleep(PREVIEW_GC_SETTLE_MS)
                    val pssSettledKb = Debug.getPss().toLong()
                    val line = buildString {
                        append("enabled=$enabled ")
                        append("gestures=$PREVIEW_PERFORMANCE_GESTURES ")
                        append("meanGestureUs=")
                        append(elapsedNs / PREVIEW_PERFORMANCE_GESTURES / 1_000.0)
                        append(" allocatedBytes=")
                        append(allocatedAfter - allocatedBefore)
                        append(" allocatedBytesPerGesture=")
                        append((allocatedAfter - allocatedBefore) / PREVIEW_PERFORMANCE_GESTURES)
                        append(" pssBeforeKb=$pssBeforeKb")
                        append(" pssImmediateKb=$pssImmediateKb")
                        append(" pssSettledKb=$pssSettledKb")
                    }
                    performanceLines += line
                    Log.i(TAG, "FLICK_EDITOR_PREVIEW_PERF $line")
                    sendProgress("FLICK_EDITOR_PREVIEW_PERF $line\n")
                }

                val report = File(session.outputDirectory, "flick-editor-preview-performance.txt")
                report.writeText(
                    buildString {
                        appendLine("device=${android.os.Build.MODEL}")
                        appendLine("sdk=${android.os.Build.VERSION.SDK_INT}")
                        performanceLines.forEach(::appendLine)
                    }
                )
                sendProgress("FLICK_EDITOR_PREVIEW_REPORT $report\n")
            } finally {
                scenario?.close()
            }
        }
    }

    @Test
    fun tenKeyNumberBracketGuideSurvivesActualFlicksOnPhysicalDevice() {
        runPhysicalDeviceSession("tenkey-number-bracket-guide") { session ->
            val testCase = TestCase(
                keyboard = TestKeyboard.TENKEY,
                columns = 1,
                candidateTabVisible = false,
                toolbarVisible = false,
                toolbarIntegrated = false,
                orientation = TestOrientation.PORTRAIT
            )
            var scenario: ActivityScenario<FastInputHostActivity>? = null
            try {
                scenario = launchHost(session.context)
                rotateAndVerify(TestOrientation.PORTRAIT)
                applyCasePreferences(session.preferences, testCase)
                check(
                    session.preferences.edit()
                        .putString("keyboard_order_preference", """["TENKEY"]""")
                        .putBoolean("tenkey_use_three_state_keyboard_preference", true)
                        .putBoolean("tenkey_switch_number_to_qwerty_number_preference", false)
                        .putBoolean("tenkey_restore_input_mode_on_restart_preference", true)
                        .putBoolean(
                            "tenkey_restore_input_mode_only_within_time_preference",
                            false
                        )
                        .putString("tenkey_last_input_mode_preference", "number")
                        .putString("tenkey_last_input_mode_presentation_preference", "native")
                        .putLong(
                            "tenkey_last_input_mode_saved_at_epoch_millis_preference",
                            System.currentTimeMillis()
                        )
                        .putBoolean("tenkey_keymap_guide", false)
                        .putBoolean("tenkey_keymap_guide_english", false)
                        .putBoolean("tenkey_keymap_guide_number", true)
                        .putString("keyboard_touch_effect_type_preference", "none")
                        .commit()
                ) {
                    "Failed to prepare the TenKey number-guide scenario"
                }

                ensureTargetImeSelected(session)
                restartInput(scenario)
                SystemClock.sleep(IME_LAYOUT_SETTLE_MS)
                assertDeviceReady(session.context, session.targetIme, scenario)

                val bracketBounds = awaitVisibleNodeBounds("key_small_letter")
                val baseline = uiAutomation.takeScreenshot()
                saveScreenshot(session, "number-guide-before-gesture")
                val directions = listOf(
                    FlickVerification("tap", PointF(0f, 0f), "("),
                    FlickVerification("left", PointF(-0.60f, 0f), "()"),
                    FlickVerification("up", PointF(0f, -0.85f), "()["),
                    FlickVerification("right", PointF(0.60f, 0f), "()[]")
                )

                directions.forEach { verification ->
                    val end = PointF(
                        bracketBounds.center.x +
                            bracketBounds.width * verification.normalizedDelta.x,
                        bracketBounds.center.y +
                            bracketBounds.height * verification.normalizedDelta.y
                    )
                    val injected = injectFlick(bracketBounds.center, end)
                    val actual = awaitTextSettled(scenario)
                    SystemClock.sleep(GUIDE_SETTLE_MS)
                    val after = uiAutomation.takeScreenshot()
                    val changedPixels = countChangedPixels(
                        baseline,
                        after,
                        bracketBounds.guideComparisonRegion(),
                        GUIDE_CHANNEL_TOLERANCE
                    )
                    Log.i(
                        TAG,
                        "TENKEY_NUMBER_GUIDE direction=${verification.name} " +
                            "expected=${verification.expectedText} actual=$actual " +
                            "changedPixels=$changedPixels"
                    )
                    saveScreenshot(session, "number-guide-after-${verification.name}")
                    after.recycle()

                    assertTrue("${verification.name} gesture injection failed", injected)
                    assertEquals(
                        "Unexpected bracket result after ${verification.name}",
                        verification.expectedText,
                        actual
                    )
                    assertTrue(
                        "TenKey number bracket guide changed after ${verification.name}: " +
                            "$changedPixels pixels",
                        changedPixels <= MAX_GUIDE_CHANGED_PIXELS
                    )
                }

                check(
                    session.preferences.edit()
                        .putBoolean("tenkey_keymap_guide_number", false)
                        .commit()
                ) {
                    "Failed to disable the TenKey number guide for the control measurement"
                }
                ensureTargetImeSelected(session)
                restartInput(scenario)
                SystemClock.sleep(IME_LAYOUT_SETTLE_MS)
                val legacyBounds = awaitVisibleNodeBounds("key_small_letter")
                val legacy = uiAutomation.takeScreenshot()
                val guideVsLegacyChangedPixels = countChangedPixels(
                    baseline,
                    legacy,
                    bracketBounds.union(legacyBounds),
                    GUIDE_CHANNEL_TOLERANCE
                )
                Log.i(
                    TAG,
                    "TENKEY_NUMBER_GUIDE controlChangedPixels=$guideVsLegacyChangedPixels"
                )
                saveScreenshot(session, "number-guide-disabled-control")
                legacy.recycle()
                baseline.recycle()

                assertTrue(
                    "Enabled guide was not measurably different from the legacy ()[] icon: " +
                        "$guideVsLegacyChangedPixels pixels",
                    guideVsLegacyChangedPixels >= MIN_GUIDE_CONTROL_CHANGED_PIXELS
                )
            } finally {
                scenario?.close()
            }
        }
    }

    @Test
    fun qwertyOverlappingTwoFingerInputOnPhysicalDevice() {
        runPhysicalDeviceSession("qwerty-multitouch") { session ->
            val testCase = TestCase(
                keyboard = TestKeyboard.QWERTY,
                columns = 1,
                candidateTabVisible = false,
                toolbarVisible = false,
                toolbarIntegrated = false,
                orientation = TestOrientation.PORTRAIT
            )
            var scenario: ActivityScenario<FastInputHostActivity>? = null
            try {
                scenario = launchHost(session.context)
                rotateAndVerify(TestOrientation.PORTRAIT)
                applyCasePreferences(session.preferences, testCase)

                for (glideEnabled in listOf(false, true)) {
                    check(
                        session.preferences.edit()
                            .putBoolean("qwerty_glide_input_preference", glideEnabled)
                            .commit()
                    )
                    ensureTargetImeSelected(session)
                    restartInput(scenario)
                    SystemClock.sleep(IME_LAYOUT_SETTLE_MS)
                    assertDeviceReady(session.context, session.targetIme, scenario)

                    for (olderPointerLiftsFirst in listOf(true, false)) {
                        prepareEmptyEditor(scenario)
                        val geometry = awaitStableGeometry(
                            keyboard = TestKeyboard.QWERTY,
                            requireCandidateContent = false
                        )
                        val injected = injectOverlappingTapPair(
                            point = geometry.first.center,
                            olderPointerLiftsFirst = olderPointerLiftsFirst
                        )
                        val actual = awaitTextSettled(scenario)
                        assertTrue(
                            "Two-pointer injection failed " +
                                "(glide=$glideEnabled, olderFirst=$olderPointerLiftsFirst)",
                            injected
                        )
                        assertEquals(
                            "Unexpected QWERTY result " +
                                "(glide=$glideEnabled, olderFirst=$olderPointerLiftsFirst)",
                            "yy",
                            actual
                        )
                    }
                }
            } finally {
                scenario?.close()
            }
        }
    }

    @Test
    fun tenKeyDeleteLongPressClearsComposingCandidatesOnPhysicalDevice() {
        runPhysicalDeviceSession("delete-long-press-candidate-clear") { session ->
            val testCase = TestCase(
                keyboard = TestKeyboard.TENKEY,
                columns = 1,
                candidateTabVisible = false,
                toolbarVisible = false,
                toolbarIntegrated = false,
                orientation = TestOrientation.PORTRAIT
            )
            var scenario: ActivityScenario<FastInputHostActivity>? = null
            try {
                scenario = launchHost(session.context)
                rotateAndVerify(TestOrientation.PORTRAIT)
                applyCasePreferences(session.preferences, testCase)
                check(
                    session.preferences.edit()
                        .putInt("long_press_timeout_preference", 100)
                        .putString("delete_long_press_conversion_behavior", "deferred")
                        .putBoolean("live_conversion_preference", false)
                        .commit()
                )

                ensureTargetImeSelected(session)
                restartInput(scenario)
                SystemClock.sleep(IME_LAYOUT_SETTLE_MS)
                assertDeviceReady(session.context, session.targetIme, scenario)
                prepareEmptyEditor(scenario)

                val before = awaitStableGeometry(
                    keyboard = TestKeyboard.TENKEY,
                    requireCandidateContent = false
                )
                val inputInjected = injectSequence(
                    first = before.first.center,
                    second = before.second.center,
                    repetitions = 1
                )
                val inputText = awaitTextSettled(scenario)
                awaitStableGeometry(
                    keyboard = TestKeyboard.TENKEY,
                    requireCandidateContent = true
                )
                assertTrue("Ten-key input injection failed", inputInjected)
                assertTrue("Composing input did not reach the editor", inputText.isNotEmpty())
                assertTrue(
                    "Candidate list was not populated before delete long press",
                    findCandidateState().texts.isNotEmpty()
                )

                val deleteBounds = findVisibleNodeById("key_delete")?.screenRect()
                    ?: throw SetupException("Delete key is not visible")
                val deleteInjected = injectTapWithoutTrailingGap(
                    point = deleteBounds.center,
                    holdMs = 850L
                )
                awaitStableGeometry(
                    keyboard = TestKeyboard.TENKEY,
                    requireCandidateContent = false
                )
                val afterText = awaitTextSettled(scenario)
                val afterCandidates = findCandidateState().texts

                assertTrue("Delete long-press injection failed", deleteInjected)
                assertTrue("ComposingText was not fully deleted", afterText.isEmpty())
                assertTrue(
                    "SuggestionAdapter still exposes candidates after ComposingText deletion: " +
                        afterCandidates,
                    afterCandidates.isEmpty()
                )
            } finally {
                scenario?.close()
            }
        }
    }

    @Test
    fun qwertyVariationPopupTwoFingerInputOnPhysicalDevice() {
        runPhysicalDeviceSession("qwerty-variation-popup-multitouch") { session ->
            val testCase = TestCase(
                keyboard = TestKeyboard.QWERTY,
                columns = 1,
                candidateTabVisible = false,
                toolbarVisible = false,
                toolbarIntegrated = false,
                orientation = TestOrientation.PORTRAIT
            )
            var scenario: ActivityScenario<FastInputHostActivity>? = null
            try {
                scenario = launchHost(session.context)
                rotateAndVerify(TestOrientation.PORTRAIT)
                applyCasePreferences(session.preferences, testCase)
                check(
                    session.preferences.edit()
                        .putInt("long_press_timeout_preference", 100)
                        .putInt("qwerty_variation_popup_size_scale_percent_preference", 100)
                        .putBoolean("qwerty_show_popup_window_preference", true)
                        .commit()
                )

                for (glideEnabled in listOf(false, true)) {
                    check(
                        session.preferences.edit()
                            .putBoolean("qwerty_glide_input_preference", glideEnabled)
                            .commit()
                    )
                    ensureTargetImeSelected(session)
                    restartInput(scenario)
                    SystemClock.sleep(IME_LAYOUT_SETTLE_MS)
                    assertDeviceReady(session.context, session.targetIme, scenario)

                    for (longPressedPointerLiftsFirst in listOf(true, false)) {
                        prepareEmptyEditor(scenario)
                        val geometry = awaitStableGeometry(
                            keyboard = TestKeyboard.QWERTY,
                            requireCandidateContent = false
                        )
                        val injected = injectLongPressThenOverlappingTap(
                            point = geometry.first.center,
                            longPressedPointerLiftsFirst = longPressedPointerLiftsFirst
                        )
                        val actual = awaitTextSettled(scenario)
                        assertTrue(
                            "Variation popup pointer injection failed " +
                                "(glide=$glideEnabled, " +
                                "longPressedFirst=$longPressedPointerLiftsFirst)",
                            injected
                        )
                        assertEquals(
                            "The selected variation and second tap must both be committed " +
                                "(glide=$glideEnabled, " +
                                "longPressedFirst=$longPressedPointerLiftsFirst)",
                            "6y",
                            actual
                        )
                    }

                    prepareEmptyEditor(scenario)
                    val geometry = awaitStableGeometry(
                        keyboard = TestKeyboard.QWERTY,
                        requireCandidateContent = false
                    )
                    val secondVariationPoint = PointF(
                        geometry.prime.center.x + 50f,
                        geometry.prime.top - 75f
                    )
                    val injected = injectSecondPointerLongPressAndMove(
                        firstPoint = geometry.first.center,
                        longPressPoint = geometry.prime.center,
                        variationPoint = secondVariationPoint
                    )
                    val actual = awaitTextSettled(scenario)
                    assertTrue(
                        "Second-pointer variation selection injection failed " +
                            "(glide=$glideEnabled)",
                        injected
                    )
                    assertEquals(
                        "Variation selection must follow the long-pressed pointer " +
                            "(glide=$glideEnabled)",
                        "yβ",
                        actual
                    )
                }
            } finally {
                scenario?.close()
            }
        }
    }

    @Test
    fun rapidInputFullMatrixOnPhysicalDevice() {
        val arguments = InstrumentationRegistry.getArguments()
        val startCase = arguments.getString("startCase")?.toIntOrNull() ?: 1
        val endCase = arguments.getString("endCase")?.toIntOrNull() ?: TOTAL_CASES
        val rounds = arguments.getString("matrixRounds")?.toIntOrNull() ?: DEFAULT_MATRIX_ROUNDS
        val casePauseMs = arguments.getString("casePauseMs")?.toLongOrNull() ?: 0L
        val captureVisuals =
            arguments.getString("captureVisuals")?.toBooleanStrictOrNull() ?: false
        require(startCase in 1..TOTAL_CASES)
        require(endCase in startCase..TOTAL_CASES)
        require(rounds > 0)

        runPhysicalDeviceSession("matrix") { session ->
            val failures = mutableListOf<String>()
            val setupErrors = mutableListOf<String>()
            val screenshots = mutableSetOf<String>()
            var completedConfigurations = 0
            var completedMeasurements = 0
            var caseIndex = 0
            var scenario: ActivityScenario<FastInputHostActivity>? = null

            try {
                matrix@ for (orientation in TestOrientation.entries) {
                    val firstCaseForOrientation = orientation.ordinal * CASES_PER_ORIENTATION + 1
                    val lastCaseForOrientation =
                        firstCaseForOrientation + CASES_PER_ORIENTATION - 1
                    if (endCase < firstCaseForOrientation ||
                        startCase > lastCaseForOrientation
                    ) {
                        caseIndex += CASES_PER_ORIENTATION
                        continue
                    }
                    scenario?.close()
                    scenario = null
                    scenario = launchHost(session.context)
                    rotateAndVerify(orientation)

                    for (keyboard in TestKeyboard.entries) {
                        for (columns in 1..3) {
                            for (candidateTabVisible in listOf(false, true)) {
                                for (toolbarVisible in listOf(false, true)) {
                                    for (toolbarIntegrated in listOf(false, true)) {
                                        caseIndex += 1
                                        if (caseIndex < startCase) continue
                                        if (caseIndex > endCase) break@matrix

                                        val testCase = TestCase(
                                            keyboard = keyboard,
                                            columns = columns,
                                            candidateTabVisible = candidateTabVisible,
                                            toolbarVisible = toolbarVisible,
                                            toolbarIntegrated = toolbarIntegrated,
                                            orientation = orientation
                                        )
                                        val configKey = "case-$caseIndex-${testCase.fileToken()}"
                                        applyCasePreferences(session.preferences, testCase)
                                        ensureTargetImeSelected(session)
                                        restartInput(scenario)
                                        SystemClock.sleep(IME_LAYOUT_SETTLE_MS)
                                        if (casePauseMs > 0L) SystemClock.sleep(casePauseMs)

                                        try {
                                            assertDeviceReady(
                                                session.context,
                                                session.targetIme,
                                                scenario
                                            )
                                            if (captureVisuals) {
                                                prepareEmptyEditor(scenario)
                                                awaitStableGeometry(
                                                    keyboard,
                                                    requireCandidateContent = false
                                                )
                                                saveScreenshot(session, "$configKey-empty")
                                            }
                                            repeat(rounds) { zeroBasedRound ->
                                                val round = zeroBasedRound + 1
                                                val cold = runColdCandidateTransitionPhase(
                                                    scenario = scenario,
                                                    keyboard = keyboard
                                                )
                                                val warm = runWarmCandidatePhase(
                                                    scenario = scenario,
                                                    keyboard = keyboard
                                                )
                                                completedMeasurements += 1

                                                val result = buildString {
                                                    append("case=$caseIndex round=$round ")
                                                    append("keyboard=${keyboard.name} ")
                                                    append("orientation=${orientation.name} ")
                                                    append("columns=$columns ")
                                                    append("candidateTab=$candidateTabVisible ")
                                                    append("toolbar=$toolbarVisible ")
                                                    append("integrated=$toolbarIntegrated ")
                                                    append("${candidateHeightSummary(session.preferences, testCase)} ")
                                                    append("cold=${cold.render()} ")
                                                    append("warm=${warm.render()}")
                                                }
                                                Log.i(TAG, "RESULT\t$result")
                                                sendProgress("FAST_INPUT_RESULT $result\n")

                                                if (!cold.passed || !warm.passed) {
                                                    failures += result
                                                    if (screenshots.add(configKey)) {
                                                        saveScreenshot(session, configKey)
                                                    }
                                                }
                                                if (captureVisuals && zeroBasedRound == 0) {
                                                    saveScreenshot(session, "$configKey-active")
                                                }
                                            }
                                        } catch (error: SetupException) {
                                            val result = buildString {
                                                append("case=$caseIndex ")
                                                append("keyboard=${keyboard.name} ")
                                                append("orientation=${orientation.name} ")
                                                append("columns=$columns ")
                                                append("candidateTab=$candidateTabVisible ")
                                                append("toolbar=$toolbarVisible ")
                                                append("integrated=$toolbarIntegrated ")
                                                append("SETUP_ERROR=${error.message}")
                                            }
                                            setupErrors += result
                                            Log.e(TAG, result, error)
                                            sendProgress("FAST_INPUT_SETUP_ERROR $result\n")
                                            if (screenshots.add(configKey)) {
                                                saveScreenshot(session, configKey)
                                            }
                                            throw SetupException(result, error)
                                        }

                                        completedConfigurations += 1
                                        if (completedConfigurations % 12 == 0) {
                                            sendProgress(
                                                "FAST_INPUT_PROGRESS case=$caseIndex " +
                                                    "configurations=$completedConfigurations/" +
                                                    "${endCase - startCase + 1} " +
                                                    "measurements=$completedMeasurements " +
                                                    "misinputOrInjection=${failures.size} " +
                                                    "setupErrors=${setupErrors.size}\n"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                scenario?.close()
            }

            val expectedConfigurations = endCase - startCase + 1
            val expectedMeasurements = expectedConfigurations * rounds
            val summary = buildString {
                append("FAST_INPUT_SUMMARY range=$startCase-$endCase ")
                append("configurations=$completedConfigurations/$expectedConfigurations ")
                append("rounds=$rounds ")
                append("measurements=$completedMeasurements/$expectedMeasurements ")
                append("phases=${completedMeasurements * 2} ")
                append("misinputOrInjection=${failures.size} ")
                append("setupErrors=${setupErrors.size}\n")
            }
            Log.i(TAG, summary.trim())
            sendProgress(summary)
            assertTrue(
                buildString {
                    append(summary)
                    if (setupErrors.isNotEmpty()) {
                        append("Setup errors (not counted as misinput):\n")
                        append(setupErrors.joinToString(separator = "\n", limit = 24))
                        append('\n')
                    }
                    if (failures.isNotEmpty()) {
                        append("Input failures:\n")
                        append(failures.joinToString(separator = "\n", limit = 24))
                    }
                },
                completedConfigurations == expectedConfigurations &&
                    completedMeasurements == expectedMeasurements &&
                    failures.isEmpty() &&
                    setupErrors.isEmpty()
            )
        }
    }

    @Test
    fun emptyCandidatePresentationStaysHiddenAfterImeReopenOnCustomKeyboard() {
        runPhysicalDeviceSession("custom-empty-candidate-reopen") { session ->
            var scenario: ActivityScenario<FastInputHostActivity>? = null
            try {
                check(
                    session.preferences.edit()
                        .putString(
                            "keyboard_order_preference",
                            """["CUSTOM","TENKEY","SUMIRE","QWERTY","ROMAJI"]"""
                        )
                        .putBoolean("save_last_used_keyboard", false)
                        .putString("candidate_column_preference", "1")
                        .putBoolean("candidate_tab_visibility_preference", true)
                        .putBoolean("shortcut_toolbar_visibility_preference", false)
                        .putBoolean("keyboard_floating_preference", false)
                        .putInt("candidate_view_height_dp_preference", 200)
                        .putInt("candidate_view_empty_height_dp_preference", 80)
                        .commit()
                ) { "Failed to configure custom keyboard candidate regression" }

                ensureTargetImeSelected(session)
                scenario = launchHost(session.context)
                restartInput(scenario)
                awaitVisibleNodeBounds("custom_layout_default")
                assertTrue(
                    "Candidate tabs must be hidden for an empty Custom composition",
                    findVisibleNodeById("candidate_tab_layout") == null
                )

                scenario.onActivity { activity ->
                    activity.getSystemService(InputMethodManager::class.java)
                        .hideSoftInputFromWindow(activity.editText.windowToken, 0)
                }
                SystemClock.sleep(500)
                scenario.onActivity { activity ->
                    activity.editText.requestFocus()
                    activity.getSystemService(InputMethodManager::class.java)
                        .showSoftInput(activity.editText, InputMethodManager.SHOW_IMPLICIT)
                }
                awaitVisibleNodeBounds("custom_layout_default")
                assertTrue(
                    "Candidate tabs must stay hidden after Custom IME close/reopen",
                    findVisibleNodeById("candidate_tab_layout") == null
                )
            } finally {
                scenario?.close()
            }
        }
    }

    @Test
    fun sumireThreeColumnRateSweepOnPhysicalDevice() {
        val arguments = InstrumentationRegistry.getArguments()
        val trialsPerInterval =
            arguments.getString("rateTrials")?.toIntOrNull() ?: DEFAULT_RATE_TRIALS
        val startConfiguration =
            arguments.getString("startRateConfig")?.toIntOrNull() ?: 1
        val endConfiguration =
            arguments.getString("endRateConfig")?.toIntOrNull() ?: RATE_CONFIGURATIONS
        require(trialsPerInterval > 0)
        require(startConfiguration in 1..RATE_CONFIGURATIONS)
        require(endConfiguration in startConfiguration..RATE_CONFIGURATIONS)

        runPhysicalDeviceSession("sumire-rate") { session ->
            val setupErrors = mutableListOf<String>()
            val unexpectedResults = mutableListOf<String>()
            val screenshots = mutableSetOf<String>()
            var configurations = 0
            var configurationIndex = 0
            var trials = 0
            var scenario: ActivityScenario<FastInputHostActivity>? = null

            try {
                for (orientation in TestOrientation.entries) {
                    val firstConfigurationForOrientation =
                        orientation.ordinal * RATE_CONFIGURATIONS_PER_ORIENTATION + 1
                    val lastConfigurationForOrientation =
                        firstConfigurationForOrientation +
                            RATE_CONFIGURATIONS_PER_ORIENTATION - 1
                    if (endConfiguration < firstConfigurationForOrientation ||
                        startConfiguration > lastConfigurationForOrientation
                    ) {
                        configurationIndex += RATE_CONFIGURATIONS_PER_ORIENTATION
                        continue
                    }
                    scenario?.close()
                    scenario = null
                    scenario = launchHost(session.context)
                    rotateAndVerify(orientation)

                    for (candidateTabVisible in listOf(false, true)) {
                        for (toolbarVisible in listOf(false, true)) {
                            for (toolbarIntegrated in listOf(false, true)) {
                                configurationIndex += 1
                                if (configurationIndex !in startConfiguration..endConfiguration) {
                                    continue
                                }
                                configurations += 1
                                val testCase = TestCase(
                                    keyboard = TestKeyboard.SUMIRE,
                                    columns = 3,
                                    candidateTabVisible = candidateTabVisible,
                                    toolbarVisible = toolbarVisible,
                                    toolbarIntegrated = toolbarIntegrated,
                                    orientation = orientation
                                )
                                val configKey = "rate-${testCase.fileToken()}"
                                applyCasePreferences(session.preferences, testCase)
                                ensureTargetImeSelected(session)
                                restartInput(scenario)
                                SystemClock.sleep(IME_LAYOUT_SETTLE_MS)

                                try {
                                    assertDeviceReady(
                                        session.context,
                                        session.targetIme,
                                        scenario
                                    )
                                    for (intervalMs in RATE_INTERVALS_MS) {
                                        val counts = RateCounts()
                                        repeat(trialsPerInterval) { trialIndex ->
                                            val result = runSumireRateTrial(
                                                scenario = scenario,
                                                intervalMs = intervalMs
                                            )
                                            trials += 1
                                            counts.add(result.category)
                                            val trialLog = buildString {
                                                append("config=$configurationIndex ")
                                                append("trial=${trialIndex + 1} ")
                                                append("intervalMs=$intervalMs ")
                                                append("orientation=${orientation.name} ")
                                                append("candidateTab=$candidateTabVisible ")
                                                append("toolbar=$toolbarVisible ")
                                                append("integrated=$toolbarIntegrated ")
                                                append("${candidateHeightSummary(session.preferences, testCase)} ")
                                                append("category=${result.category.name} ")
                                                append("events=${result.allEventsInjected} ")
                                                append("actual=[${result.actual}] ")
                                                append("geometry=${result.geometry}")
                                            }
                                            Log.i(TAG, "RATE_TRIAL\t$trialLog")
                                            sendProgress("FAST_INPUT_RATE_TRIAL $trialLog\n")
                                            if (result.category != RateCategory.EXPECTED) {
                                                unexpectedResults += trialLog
                                                if (screenshots.add(configKey)) {
                                                    saveScreenshot(session, configKey)
                                                }
                                            }
                                        }
                                        val rateSummary = buildString {
                                            append("config=$configurationIndex ")
                                            append("intervalMs=$intervalMs ")
                                            append("orientation=${orientation.name} ")
                                            append("candidateTab=$candidateTabVisible ")
                                            append("toolbar=$toolbarVisible ")
                                            append("integrated=$toolbarIntegrated ")
                                            append("counts=${counts.render()}")
                                        }
                                        Log.i(TAG, "RATE_RESULT\t$rateSummary")
                                        sendProgress("FAST_INPUT_RATE_RESULT $rateSummary\n")
                                    }
                                } catch (error: SetupException) {
                                    val result = "config=$configurationIndex ${testCase.fileToken()} " +
                                        "SETUP_ERROR=${error.message}"
                                    setupErrors += result
                                    Log.e(TAG, result, error)
                                    sendProgress("FAST_INPUT_RATE_SETUP_ERROR $result\n")
                                    if (screenshots.add(configKey)) {
                                        saveScreenshot(session, configKey)
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                scenario?.close()
            }

            val expectedConfigurations = endConfiguration - startConfiguration + 1
            val expectedTrials =
                expectedConfigurations * RATE_INTERVALS_MS.size * trialsPerInterval
            val summary = buildString {
                append("FAST_INPUT_RATE_SUMMARY configurations=$configurations/")
                append("$expectedConfigurations range=$startConfiguration-$endConfiguration ")
                append("trials=$trials/$expectedTrials ")
                append("unexpected=${unexpectedResults.size} ")
                append("setupErrors=${setupErrors.size}\n")
            }
            Log.i(TAG, summary.trim())
            sendProgress(summary)
            assertTrue(
                buildString {
                    append(summary)
                    if (setupErrors.isNotEmpty()) {
                        append("Setup errors (not counted as misinput):\n")
                        append(setupErrors.joinToString(separator = "\n", limit = 24))
                        append('\n')
                    }
                    if (unexpectedResults.isNotEmpty()) {
                        append("Unexpected rate results:\n")
                        append(unexpectedResults.joinToString(separator = "\n", limit = 24))
                    }
                },
                configurations == expectedConfigurations &&
                    trials == expectedTrials &&
                    unexpectedResults.isEmpty() &&
                    setupErrors.isEmpty()
            )
        }
    }

    private fun runColdCandidateTransitionPhase(
        scenario: ActivityScenario<FastInputHostActivity>,
        keyboard: TestKeyboard
    ): PhaseResult {
        prepareEmptyEditor(scenario)
        val before = awaitStableGeometry(keyboard, requireCandidateContent = false)
        val injected = injectSequence(
            first = before.first.center,
            second = before.second.center,
            repetitions = SEQUENCE_REPETITIONS
        )
        val actual = awaitTextSettled(scenario)
        val after = if (actual.isNotEmpty()) {
            awaitStableGeometry(keyboard, requireCandidateContent = true)
        } else {
            findGeometryOrNull(keyboard)
        }
        val expected = expectedSequence(keyboard)
        return PhaseResult.create(
            expected = expected,
            actual = actual,
            allEventsInjected = injected,
            before = before,
            after = after
        )
    }

    private fun runWarmCandidatePhase(
        scenario: ActivityScenario<FastInputHostActivity>,
        keyboard: TestKeyboard
    ): PhaseResult {
        prepareEmptyEditor(scenario)
        val empty = awaitStableGeometry(keyboard, requireCandidateContent = false)
        val primeInjected = injectTap(empty.prime.center)
        if (!primeInjected) {
            return PhaseResult.create(
                expected = keyboard.primeText + expectedSequence(keyboard),
                actual = awaitTextSettled(scenario),
                allEventsInjected = false,
                before = empty,
                after = findGeometryOrNull(keyboard)
            )
        }
        val primedText = awaitTextSettled(scenario)
        if (primedText != keyboard.primeText) {
            throw SetupException(
                "Prime key produced [$primedText], expected [${keyboard.primeText}]"
            )
        }
        val warm = awaitStableGeometry(keyboard, requireCandidateContent = true)
        val injected = injectSequence(
            first = warm.first.center,
            second = warm.second.center,
            repetitions = SEQUENCE_REPETITIONS
        )
        val actual = awaitTextSettled(scenario)
        val after = awaitStableGeometry(keyboard, requireCandidateContent = true)
        return PhaseResult.create(
            expected = keyboard.primeText + expectedSequence(keyboard),
            actual = actual,
            allEventsInjected = injected,
            before = warm,
            after = after
        )
    }

    private fun runSumireRateTrial(
        scenario: ActivityScenario<FastInputHostActivity>,
        intervalMs: Long
    ): RateTrialResult {
        prepareEmptyEditor(scenario)
        val empty = awaitStableGeometry(
            TestKeyboard.SUMIRE,
            requireCandidateContent = false
        )
        if (!injectTap(empty.prime.center)) {
            return RateTrialResult(
                category = RateCategory.MISSING,
                actual = awaitTextSettled(scenario),
                allEventsInjected = false,
                geometry = empty.renderTransition(null)
            )
        }
        val primedText = awaitTextSettled(scenario)
        if (primedText != TestKeyboard.SUMIRE.primeText) {
            throw SetupException(
                "Rate-sweep prime produced [$primedText], " +
                    "expected [${TestKeyboard.SUMIRE.primeText}]"
            )
        }
        val warm = awaitStableGeometry(
            TestKeyboard.SUMIRE,
            requireCandidateContent = true
        )
        val injected = injectTapPairAtDownInterval(warm.first.center, intervalMs)
        val actual = awaitTextSettled(scenario)
        val after = awaitStableGeometry(
            TestKeyboard.SUMIRE,
            requireCandidateContent = true
        )
        val suffix = actual.removePrefix(TestKeyboard.SUMIRE.primeText)
        val category = when {
            !injected || suffix.length < 2 -> RateCategory.MISSING
            suffix == "やや" -> RateCategory.EXPECTED
            suffix == "やな" -> RateCategory.YA_NA
            else -> RateCategory.OTHER
        }
        return RateTrialResult(
            category = category,
            actual = actual,
            allEventsInjected = injected,
            geometry = warm.renderTransition(after)
        )
    }

    private fun applyCasePreferences(
        preferences: SharedPreferences,
        case: TestCase
    ) {
        val portraitCandidateHeight = preferences.getInt(
            "candidate_view_height_portrait_column_${case.columns}_dp_preference",
            when (case.columns) {
                2 -> 120
                3 -> 160
                else -> 110
            }
        )
        val landscapeCandidateHeight = preferences.getInt(
            "candidate_view_height_landscape_column_${case.columns}_dp_preference",
            when (case.columns) {
                2 -> 90
                3 -> 120
                else -> 60
            }
        )
        val keyboardOrder = when (case.keyboard) {
            TestKeyboard.TENKEY ->
                """["TENKEY","SUMIRE","QWERTY","ROMAJI","CUSTOM"]"""

            TestKeyboard.SUMIRE ->
                """["SUMIRE","TENKEY","QWERTY","ROMAJI","CUSTOM"]"""

            TestKeyboard.QWERTY ->
                """["QWERTY","TENKEY","SUMIRE","ROMAJI","CUSTOM"]"""
        }

        // This mirrors AppPreference.setCandidateColumnAndSyncHeight(). It deliberately does not
        // overwrite the user's empty-candidate height, per-column heights, keyboard dimensions,
        // keyboard margins, or nearest-key behavior.
        val editor = preferences.edit()
            .putString("keyboard_order_preference", keyboardOrder)
            .putBoolean("save_last_used_keyboard", false)
            .putString("candidate_column_preference", case.columns.toString())
            .putString("candidate_column_landscape_preference", case.columns.toString())
            .putInt("candidate_view_height_dp_preference", portraitCandidateHeight)
            .putInt(
                "candidate_view_height_dp_landscape_preference",
                landscapeCandidateHeight
            )
            .putBoolean("candidate_tab_visibility_preference", case.candidateTabVisible)
            .putBoolean("shortcut_toolbar_visibility_preference", case.toolbarVisible)
            .putBoolean(
                "shortcut_toolbar_integrated_in_suggestion_preference",
                case.toolbarIntegrated
            )
            .putBoolean("landscape_force_qwerty_preference", false)
            .putBoolean("landscape_force_qwerty_romaji_preference", false)
            .putBoolean("keyboard_floating_preference", false)
            .putBoolean("tenkey_kana_english_qwerty_preference", false)
            .putBoolean("sumire_english_qwerty_preference", false)
            .putString("sumire_input_method_preference", "switch-mode-effective")
            .putString("sumire_keyboard_style_preference", "default")
            .putBoolean("tenkey_restore_input_mode_on_restart_preference", false)
            .putBoolean("sumire_restore_input_mode_on_restart_preference", false)
            .putBoolean("flick_input_only_preference", true)
            .putBoolean("live_conversion_preference", false)
            .putBoolean("qwerty_glide_input_preference", false)
            .putBoolean("enable_typo_correction_japanese_flick_keyboard_preference", false)
            .putBoolean("enable_typo_correction_qwerty_english_keyboard_preference", false)
            .putBoolean("learn_dictionary_preference", false)
            .putBoolean("zero_query_suggestion_preference", false)
        check(editor.commit()) { "Failed to persist preferences for $case" }

        check(
            preferences.getString("keyboard_order_preference", null) == keyboardOrder
        )
        check(
            preferences.getString("candidate_column_preference", null) ==
                case.columns.toString()
        )
        check(
            preferences.getString("candidate_column_landscape_preference", null) ==
                case.columns.toString()
        )
        check(
            preferences.getInt("candidate_view_height_dp_preference", -1) ==
                portraitCandidateHeight
        )
        check(
            preferences.getInt("candidate_view_height_dp_landscape_preference", -1) ==
                landscapeCandidateHeight
        )
        check(
            preferences.getBoolean(
                "candidate_tab_visibility_preference",
                !case.candidateTabVisible
            ) == case.candidateTabVisible
        )
        check(
            preferences.getBoolean(
                "shortcut_toolbar_visibility_preference",
                !case.toolbarVisible
            ) == case.toolbarVisible
        )
        check(
            preferences.getBoolean(
                "shortcut_toolbar_integrated_in_suggestion_preference",
                !case.toolbarIntegrated
            ) == case.toolbarIntegrated
        )
    }

    private fun candidateHeightSummary(
        preferences: SharedPreferences,
        case: TestCase
    ): String {
        val landscape = case.orientation == TestOrientation.LANDSCAPE
        val activeKey = if (landscape) {
            "candidate_view_height_dp_landscape_preference"
        } else {
            "candidate_view_height_dp_preference"
        }
        val emptyKey = if (landscape) {
            "candidate_view_empty_height_dp_landscape_preference"
        } else {
            "candidate_view_empty_height_dp_preference"
        }
        val columnKey = if (landscape) {
            "candidate_view_height_landscape_column_${case.columns}_dp_preference"
        } else {
            "candidate_view_height_portrait_column_${case.columns}_dp_preference"
        }
        val activeDefault = if (landscape) 60 else 110
        val emptyDefault = if (landscape) 110 else 110
        val columnDefault = if (landscape) {
            when (case.columns) {
                2 -> 90
                3 -> 120
                else -> 60
            }
        } else {
            when (case.columns) {
                2 -> 120
                3 -> 160
                else -> 110
            }
        }
        return "candidateHeightDp(active=${preferences.getInt(activeKey, activeDefault)}," +
            "empty=${preferences.getInt(emptyKey, emptyDefault)}," +
            "column${case.columns}=${preferences.getInt(columnKey, columnDefault)})"
    }

    private fun restartInput(scenario: ActivityScenario<FastInputHostActivity>) {
        awaitHostWindowFocus(scenario)
        scenario.onActivity { activity ->
            activity.restartEditorInput(clearText = true)
        }

        val deadline = SystemClock.uptimeMillis() + EDITOR_CONNECTION_TIMEOUT_MS
        var stableSamples = 0
        var lastState = "host activity unavailable"
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { activity ->
                val editor = activity.editText
                val inputMethodManager =
                    activity.getSystemService(InputMethodManager::class.java)
                val attached = editor.isAttachedToWindow
                val windowFocused = editor.hasWindowFocus()
                val viewFocused = editor.hasFocus()
                val shown = editor.isShown
                val active = inputMethodManager.isActive(editor)
                val acceptingText = inputMethodManager.isAcceptingText
                val imeVisible = ViewCompat.getRootWindowInsets(editor)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true

                ready = attached &&
                    windowFocused &&
                    viewFocused &&
                    shown &&
                    active &&
                    acceptingText &&
                    imeVisible
                lastState =
                    "attached=$attached windowFocused=$windowFocused " +
                        "viewFocused=$viewFocused shown=$shown active=$active " +
                        "acceptingText=$acceptingText imeVisible=$imeVisible"
                if (!ready) {
                    activity.requestImeForEditor()
                }
            }

            if (ready) {
                stableSamples += 1
                if (stableSamples >= EDITOR_CONNECTION_STABLE_SAMPLES) return
            } else {
                stableSamples = 0
            }
            SystemClock.sleep(POLL_MS)
        }

        throw SetupException(
            "Host editor was not served by InputMethodManager " +
                "within ${EDITOR_CONNECTION_TIMEOUT_MS}ms ($lastState)"
        )
    }

    private fun prepareEmptyEditor(scenario: ActivityScenario<FastInputHostActivity>) {
        restartInput(scenario)
        val deadline = SystemClock.uptimeMillis() + SETUP_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { activity ->
                ready = activity.editText.hasFocus() &&
                    activity.editText.isShown &&
                    activity.editText.text.isNullOrEmpty()
            }
            if (ready) return
            SystemClock.sleep(POLL_MS)
        }
        throw SetupException("Host editor did not become focused and empty")
    }

    private fun readText(scenario: ActivityScenario<FastInputHostActivity>): String {
        var text = ""
        scenario.onActivity { activity ->
            text = activity.editText.text?.toString().orEmpty()
        }
        return text
    }

    private fun assertEditorPreviewDecoration(
        scenario: ActivityScenario<FastInputHostActivity>,
        expectedText: String,
        expectedBackgroundColor: Int,
        caseName: String,
        expectedBackgroundEnd: Int = expectedText.length,
    ) {
        val deadline = SystemClock.uptimeMillis() + RESULT_TIMEOUT_MS
        var latest = readEditorDecoration(scenario)
        while (SystemClock.uptimeMillis() < deadline) {
            latest = readEditorDecoration(scenario)
            if (latest.matches(
                    expectedText,
                    expectedBackgroundColor,
                    expectedBackgroundEnd,
                )
            ) return
            SystemClock.sleep(PREVIEW_TEXT_POLL_MS)
        }
        assertTrue(
            "$caseName did not retain the pre-edit background/underline spans: $latest",
            latest.matches(
                expectedText,
                expectedBackgroundColor,
                expectedBackgroundEnd,
            ),
        )
    }

    private fun readEditorDecoration(
        scenario: ActivityScenario<FastInputHostActivity>
    ): EditorDecorationSnapshot {
        var snapshot = EditorDecorationSnapshot.Empty
        scenario.onActivity { activity ->
            val text = activity.editText.text
            snapshot = EditorDecorationSnapshot(
                text = text?.toString().orEmpty(),
                backgrounds = text.getSpans(
                    0,
                    text.length,
                    BackgroundColorSpan::class.java,
                ).map { span ->
                    BackgroundSpanSnapshot(
                        color = span.backgroundColor,
                        start = text.getSpanStart(span),
                        end = text.getSpanEnd(span),
                        flags = text.getSpanFlags(span),
                    )
                },
                underlines = text.getSpans(
                    0,
                    text.length,
                    UnderlineSpan::class.java,
                ).map { span ->
                    SpanRangeSnapshot(
                        start = text.getSpanStart(span),
                        end = text.getSpanEnd(span),
                        flags = text.getSpanFlags(span),
                    )
                },
            )
        }
        return snapshot
    }

    private fun awaitEditorText(
        scenario: ActivityScenario<FastInputHostActivity>,
        predicate: (String) -> Boolean,
    ): String {
        val deadline = SystemClock.uptimeMillis() + RESULT_TIMEOUT_MS
        var latest = readText(scenario)
        while (SystemClock.uptimeMillis() < deadline) {
            latest = readText(scenario)
            if (predicate(latest)) return latest
            SystemClock.sleep(PREVIEW_TEXT_POLL_MS)
        }
        return latest
    }

    private fun awaitCandidateVisibleLatencyNs(startedNs: Long): Long {
        val deadline = SystemClock.uptimeMillis() + RESULT_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (findCandidateState().texts.isNotEmpty()) {
                return SystemClock.elapsedRealtimeNanos() - startedNs
            }
            SystemClock.sleep(PREVIEW_CANDIDATE_POLL_MS)
        }
        throw SetupException("Candidate list did not become visible during latency measurement")
    }

    private fun awaitTextSettled(
        scenario: ActivityScenario<FastInputHostActivity>
    ): String {
        var previous: String? = null
        var stableSamples = 0
        val deadline = SystemClock.uptimeMillis() + RESULT_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val current = readText(scenario)
            if (current == previous) {
                stableSamples += 1
                if (stableSamples >= TEXT_STABLE_SAMPLES) return current
            } else {
                previous = current
                stableSamples = 1
            }
            SystemClock.sleep(POLL_MS)
        }
        return readText(scenario)
    }

    private fun awaitStableGeometry(
        keyboard: TestKeyboard,
        requireCandidateContent: Boolean
    ): GeometrySnapshot {
        var previous: GeometrySnapshot? = null
        var stableSamples = 0
        var lastCandidateTexts = emptyList<String>()
        var lastError = "keyboard geometry unavailable"
        val deadline = SystemClock.uptimeMillis() + SETUP_TIMEOUT_MS

        while (SystemClock.uptimeMillis() < deadline) {
            try {
                val candidateState = findCandidateState()
                lastCandidateTexts = candidateState.texts
                val current = findGeometry(keyboard, candidateState.bounds)
                val candidateReady =
                    !requireCandidateContent || candidateState.texts.isNotEmpty()
                if (candidateReady && current.sameBounds(previous)) {
                    stableSamples += 1
                } else {
                    stableSamples = if (candidateReady) 1 else 0
                }
                if (stableSamples >= GEOMETRY_STABLE_SAMPLES) return current
                previous = current
            } catch (error: SetupException) {
                lastError = error.message.orEmpty()
                previous = null
                stableSamples = 0
            }
            SystemClock.sleep(GEOMETRY_SAMPLE_MS)
        }
        throw SetupException(
            "Timed out waiting for ${keyboard.name} geometry " +
                "(requireCandidates=$requireCandidateContent, " +
                "candidateTexts=$lastCandidateTexts, lastError=$lastError)"
        )
    }

    private fun findGeometryOrNull(keyboard: TestKeyboard): GeometrySnapshot? =
        runCatching {
            val candidateState = findCandidateState()
            findGeometry(keyboard, candidateState.bounds)
        }.getOrNull()

    private fun findGeometry(
        keyboard: TestKeyboard,
        candidateBounds: ScreenRect?
    ): GeometrySnapshot {
        val root = findVisibleNodeById(keyboard.rootViewId)
            ?: throw SetupException("${keyboard.rootViewId} is not visible")
        val rootBounds = root.screenRect()
        val first = findRequiredKey(root, keyboard.firstKey)
        val second = if (keyboard.firstKey == keyboard.secondKey) {
            first
        } else {
            findRequiredKey(root, keyboard.secondKey)
        }
        val prime = findRequiredKey(root, keyboard.primeKey)
        val neighbor = keyboard.neighborKey?.let { findRequiredKey(root, it) }
        return GeometrySnapshot(
            root = rootBounds,
            first = first.screenRect(),
            second = second.screenRect(),
            prime = prime.screenRect(),
            neighbor = neighbor?.screenRect(),
            candidate = candidateBounds
        )
    }

    private fun findRequiredKey(
        keyboardRoot: AccessibilityNodeInfo,
        locator: NodeLocator
    ): AccessibilityNodeInfo {
        return findDescendant(keyboardRoot) { node ->
            when (locator) {
                is NodeLocator.Id ->
                    node.viewIdResourceName?.endsWith(":id/${locator.name}") == true

                is NodeLocator.Label ->
                    node.text?.toString() == locator.label ||
                        node.contentDescription?.toString() == locator.label
            }
        } ?: throw SetupException("Key ${locator.render()} is not visible")
    }

    private fun findCursorLeftBounds(keyboard: TestKeyboard): ScreenRect {
        return when (keyboard) {
            TestKeyboard.TENKEY -> awaitVisibleNodeBounds("key_soft_left")
            TestKeyboard.SUMIRE -> {
                val root = findVisibleNodeById(keyboard.rootViewId)
                    ?: throw SetupException("${keyboard.rootViewId} is not visible")
                val labels = setOf("CursorMoveLeft", "Move Cursor Left", "カーソル左")
                val node = findDescendant(root) { candidate ->
                    candidate.text?.toString() in labels ||
                        candidate.contentDescription?.toString() in labels
                } ?: throw SetupException("Sumire cursor-left key is not visible")
                node.screenRect()
            }

            TestKeyboard.QWERTY -> throw SetupException(
                "Tail preview test does not target QWERTY"
            )
        }
    }

    private fun findCandidateState(): CandidateState {
        val recycler = findVisibleNodeById("suggestion_recycler_view")
            ?: return CandidateState(bounds = null, texts = emptyList())
        val texts = mutableListOf<String>()
        forEachDescendant(recycler) { node ->
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                if (it !in texts && texts.size < MAX_CANDIDATE_TEXTS) texts += it
            }
        }
        return CandidateState(
            bounds = recycler.screenRect(),
            texts = texts
        )
    }

    private fun findVisibleNodeById(idName: String): AccessibilityNodeInfo? {
        for (window in uiAutomation.windows) {
            val root = window.root ?: continue
            val found = findDescendant(root) { node ->
                node.isVisibleToUser &&
                    node.viewIdResourceName?.endsWith(":id/$idName") == true &&
                    node.screenRect().isValid
            }
            if (found != null) return found
        }
        return null
    }

    private fun awaitVisibleNodeBounds(idName: String): ScreenRect {
        val deadline = SystemClock.uptimeMillis() + SETUP_TIMEOUT_MS
        var previous: ScreenRect? = null
        var stableSamples = 0
        while (SystemClock.uptimeMillis() < deadline) {
            val current = findVisibleNodeById(idName)?.screenRect()
            if (current != null && current.isValid && current == previous) {
                stableSamples += 1
                if (stableSamples >= GEOMETRY_STABLE_SAMPLES) return current
            } else {
                stableSamples = if (current?.isValid == true) 1 else 0
            }
            previous = current
            SystemClock.sleep(GEOMETRY_SAMPLE_MS)
        }
        throw SetupException("Timed out waiting for visible key id=$idName")
    }

    private fun countChangedPixels(
        first: Bitmap,
        second: Bitmap,
        region: ScreenRect,
        channelTolerance: Int
    ): Int {
        check(first.width == second.width && first.height == second.height)
        val clipped = region.intersect(ScreenRect(0, 0, first.width, first.height))
        check(clipped.isValid) { "Invalid screenshot comparison region: $region" }

        var changed = 0
        for (y in clipped.top until clipped.bottom) {
            for (x in clipped.left until clipped.right) {
                val firstPixel = first.getPixel(x, y)
                val secondPixel = second.getPixel(x, y)
                if (
                    kotlin.math.abs(
                        android.graphics.Color.red(firstPixel) -
                            android.graphics.Color.red(secondPixel)
                    ) > channelTolerance ||
                    kotlin.math.abs(
                        android.graphics.Color.green(firstPixel) -
                            android.graphics.Color.green(secondPixel)
                    ) > channelTolerance ||
                    kotlin.math.abs(
                        android.graphics.Color.blue(firstPixel) -
                            android.graphics.Color.blue(secondPixel)
                    ) > channelTolerance
                ) {
                    changed += 1
                }
            }
        }
        return changed
    }

    private fun findDescendant(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (predicate(node)) return node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return null
    }

    private fun forEachDescendant(
        root: AccessibilityNodeInfo,
        action: (AccessibilityNodeInfo) -> Unit
    ) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            action(node)
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
    }

    private fun AccessibilityNodeInfo.screenRect(): ScreenRect {
        val rect = Rect()
        getBoundsInScreen(rect)
        return ScreenRect(rect.left, rect.top, rect.right, rect.bottom)
    }

    private fun injectSequence(
        first: PointF,
        second: PointF,
        repetitions: Int
    ): Boolean {
        var allInjected = true
        repeat(repetitions) {
            allInjected = injectTap(first) && allInjected
            allInjected = injectTap(second) && allInjected
        }
        return allInjected
    }

    private fun injectTap(point: PointF): Boolean {
        return injectTapWithoutTrailingGap(point, TAP_HOLD_MS).also {
            SystemClock.sleep(TAP_GAP_MS)
        }
    }

    private fun injectFlick(start: PointF, end: PointF): Boolean {
        if (start == end) return injectTap(start)

        val downTime = SystemClock.uptimeMillis()
        var allInjected = injectSinglePointerEvent(
            downTime = downTime,
            action = MotionEvent.ACTION_DOWN,
            point = start
        )
        SystemClock.sleep(FLICK_STEP_MS)

        repeat(FLICK_MOVE_STEPS) { index ->
            val fraction = (index + 1f) / FLICK_MOVE_STEPS
            val point = PointF(
                start.x + (end.x - start.x) * fraction,
                start.y + (end.y - start.y) * fraction
            )
            allInjected = injectSinglePointerEvent(
                downTime = downTime,
                action = MotionEvent.ACTION_MOVE,
                point = point
            ) && allInjected
            SystemClock.sleep(FLICK_STEP_MS)
        }

        allInjected = injectSinglePointerEvent(
            downTime = downTime,
            action = MotionEvent.ACTION_UP,
            point = end
        ) && allInjected
        SystemClock.sleep(TAP_GAP_MS)
        return allInjected
    }

    private fun injectSinglePointerEvent(
        downTime: Long,
        action: Int,
        point: PointF
    ): Boolean {
        val event = singlePointerEvent(
            downTime = downTime,
            eventTime = SystemClock.uptimeMillis(),
            action = action,
            point = point
        )
        return uiAutomation.injectInputEvent(event, true).also {
            event.recycle()
        }
    }

    private fun injectPreviewCancelGesture(start: PointF, move: PointF): Boolean {
        val downTime = SystemClock.uptimeMillis()
        var injected = injectSinglePointerEvent(downTime, MotionEvent.ACTION_DOWN, start)
        injected = injectSinglePointerEvent(downTime, MotionEvent.ACTION_MOVE, move) && injected
        injected = injectSinglePointerEvent(downTime, MotionEvent.ACTION_CANCEL, move) && injected
        return injected
    }

    private fun runtimeStat(name: String): Long =
        Debug.getRuntimeStat(name)?.toLongOrNull() ?: 0L

    private fun injectTapPairAtDownInterval(
        point: PointF,
        downToDownIntervalMs: Long
    ): Boolean {
        val firstDownAt = SystemClock.uptimeMillis()
        val holdMs = minOf(TAP_HOLD_MS, (downToDownIntervalMs / 3).coerceAtLeast(6L))
        var injected = injectTapWithoutTrailingGap(point, holdMs)
        val secondDownAt = firstDownAt + downToDownIntervalMs
        val remaining = secondDownAt - SystemClock.uptimeMillis()
        if (remaining > 0L) SystemClock.sleep(remaining)
        injected = injectTapWithoutTrailingGap(point, holdMs) && injected
        SystemClock.sleep(TAP_GAP_MS)
        return injected
    }

    private fun injectTapWithoutTrailingGap(
        point: PointF,
        holdMs: Long
    ): Boolean {
        val downTime = SystemClock.uptimeMillis()
        val down = singlePointerEvent(
            downTime = downTime,
            eventTime = downTime,
            action = MotionEvent.ACTION_DOWN,
            point = point
        )
        val downInjected = uiAutomation.injectInputEvent(down, true)
        down.recycle()
        SystemClock.sleep(holdMs)

        val upTime = SystemClock.uptimeMillis()
        val up = singlePointerEvent(
            downTime = downTime,
            eventTime = upTime,
            action = MotionEvent.ACTION_UP,
            point = point
        )
        val upInjected = uiAutomation.injectInputEvent(up, true)
        up.recycle()
        return downInjected && upInjected
    }

    private fun injectOverlappingTapPair(
        point: PointF,
        olderPointerLiftsFirst: Boolean
    ): Boolean {
        val downTime = SystemClock.uptimeMillis()
        var allInjected = injectPointerEvent(
            downTime = downTime,
            eventTime = downTime,
            actionMasked = MotionEvent.ACTION_DOWN,
            actionIndex = 0,
            PointerSpec(id = 0, point = point)
        )
        SystemClock.sleep(TAP_HOLD_MS)

        allInjected = injectPointerEvent(
            downTime = downTime,
            eventTime = SystemClock.uptimeMillis(),
            actionMasked = MotionEvent.ACTION_POINTER_DOWN,
            actionIndex = 1,
            PointerSpec(id = 0, point = point),
            PointerSpec(id = 1, point = point)
        ) && allInjected
        SystemClock.sleep(TAP_HOLD_MS)

        val liftedIndex = if (olderPointerLiftsFirst) 0 else 1
        allInjected = injectPointerEvent(
            downTime = downTime,
            eventTime = SystemClock.uptimeMillis(),
            actionMasked = MotionEvent.ACTION_POINTER_UP,
            actionIndex = liftedIndex,
            PointerSpec(id = 0, point = point),
            PointerSpec(id = 1, point = point)
        ) && allInjected
        SystemClock.sleep(TAP_HOLD_MS)

        val remainingPointerId = if (olderPointerLiftsFirst) 1 else 0
        allInjected = injectPointerEvent(
            downTime = downTime,
            eventTime = SystemClock.uptimeMillis(),
            actionMasked = MotionEvent.ACTION_UP,
            actionIndex = 0,
            PointerSpec(id = remainingPointerId, point = point)
        ) && allInjected
        SystemClock.sleep(TAP_GAP_MS)
        return allInjected
    }

    private fun injectLongPressThenOverlappingTap(
        point: PointF,
        longPressedPointerLiftsFirst: Boolean
    ): Boolean {
        val downTime = SystemClock.uptimeMillis()
        var allInjected = injectPointerEvent(
            downTime = downTime,
            eventTime = downTime,
            actionMasked = MotionEvent.ACTION_DOWN,
            actionIndex = 0,
            PointerSpec(id = 0, point = point)
        )
        SystemClock.sleep(QWERTY_LONG_PRESS_HOLD_MS)

        allInjected = injectPointerEvent(
            downTime = downTime,
            eventTime = SystemClock.uptimeMillis(),
            actionMasked = MotionEvent.ACTION_POINTER_DOWN,
            actionIndex = 1,
            PointerSpec(id = 0, point = point),
            PointerSpec(id = 1, point = point)
        ) && allInjected
        SystemClock.sleep(TAP_HOLD_MS)

        val liftedIndex = if (longPressedPointerLiftsFirst) 0 else 1
        allInjected = injectPointerEvent(
            downTime = downTime,
            eventTime = SystemClock.uptimeMillis(),
            actionMasked = MotionEvent.ACTION_POINTER_UP,
            actionIndex = liftedIndex,
            PointerSpec(id = 0, point = point),
            PointerSpec(id = 1, point = point)
        ) && allInjected
        SystemClock.sleep(TAP_HOLD_MS)

        val remainingPointerId = if (longPressedPointerLiftsFirst) 1 else 0
        allInjected = injectPointerEvent(
            downTime = downTime,
            eventTime = SystemClock.uptimeMillis(),
            actionMasked = MotionEvent.ACTION_UP,
            actionIndex = 0,
            PointerSpec(id = remainingPointerId, point = point)
        ) && allInjected
        SystemClock.sleep(TAP_GAP_MS)
        return allInjected
    }

    private fun injectSecondPointerLongPressAndMove(
        firstPoint: PointF,
        longPressPoint: PointF,
        variationPoint: PointF
    ): Boolean {
        val downTime = SystemClock.uptimeMillis()
        var allInjected = injectPointerEvent(
            downTime = downTime,
            eventTime = downTime,
            actionMasked = MotionEvent.ACTION_DOWN,
            actionIndex = 0,
            PointerSpec(id = 0, point = firstPoint)
        )
        SystemClock.sleep(TAP_HOLD_MS)

        allInjected = injectPointerEvent(
            downTime = downTime,
            eventTime = SystemClock.uptimeMillis(),
            actionMasked = MotionEvent.ACTION_POINTER_DOWN,
            actionIndex = 1,
            PointerSpec(id = 0, point = firstPoint),
            PointerSpec(id = 1, point = longPressPoint)
        ) && allInjected
        SystemClock.sleep(QWERTY_LONG_PRESS_HOLD_MS)

        allInjected = injectPointerEvent(
            downTime = downTime,
            eventTime = SystemClock.uptimeMillis(),
            actionMasked = MotionEvent.ACTION_MOVE,
            actionIndex = 0,
            PointerSpec(id = 0, point = firstPoint),
            PointerSpec(id = 1, point = variationPoint)
        ) && allInjected
        SystemClock.sleep(TAP_HOLD_MS)

        allInjected = injectPointerEvent(
            downTime = downTime,
            eventTime = SystemClock.uptimeMillis(),
            actionMasked = MotionEvent.ACTION_POINTER_UP,
            actionIndex = 1,
            PointerSpec(id = 0, point = firstPoint),
            PointerSpec(id = 1, point = variationPoint)
        ) && allInjected
        SystemClock.sleep(TAP_HOLD_MS)

        allInjected = injectPointerEvent(
            downTime = downTime,
            eventTime = SystemClock.uptimeMillis(),
            actionMasked = MotionEvent.ACTION_UP,
            actionIndex = 0,
            PointerSpec(id = 0, point = firstPoint)
        ) && allInjected
        SystemClock.sleep(TAP_GAP_MS)
        return allInjected
    }

    private fun injectPointerEvent(
        downTime: Long,
        eventTime: Long,
        actionMasked: Int,
        actionIndex: Int,
        vararg pointers: PointerSpec
    ): Boolean {
        val properties = Array(pointers.size) { index ->
            MotionEvent.PointerProperties().apply {
                id = pointers[index].id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coordinates = Array(pointers.size) { index ->
            MotionEvent.PointerCoords().apply {
                x = pointers[index].point.x
                y = pointers[index].point.y
                pressure = 1f
                size = 1f
            }
        }
        val action = actionMasked or
            (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val event = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            pointers.size,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        )
        return try {
            uiAutomation.injectInputEvent(event, true)
        } finally {
            event.recycle()
        }
    }

    private fun singlePointerEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        point: PointF
    ): MotionEvent {
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        )
        val coordinates = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = point.x
                y = point.y
                pressure = 1f
                size = 1f
            }
        )
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        )
    }

    private fun expectedSequence(keyboard: TestKeyboard): String {
        return buildString {
            repeat(SEQUENCE_REPETITIONS) {
                append(keyboard.firstText)
                append(keyboard.secondText)
            }
        }
    }

    private fun rotateAndVerify(orientation: TestOrientation) {
        uiAutomation.setRotation(orientation.rotation)
        val deadline = SystemClock.uptimeMillis() + ORIENTATION_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val screenshot = uiAutomation.takeScreenshot()
            val rotated = screenshot?.let {
                val matches = when (orientation) {
                    TestOrientation.PORTRAIT -> it.height >= it.width
                    TestOrientation.LANDSCAPE -> it.width > it.height
                }
                it.recycle()
                matches
            } ?: false
            if (rotated) {
                SystemClock.sleep(ORIENTATION_SETTLE_MS)
                return
            }
            SystemClock.sleep(POLL_MS)
        }
        throw SetupException("Device did not rotate to ${orientation.name}")
    }

    private fun launchHost(context: Context): ActivityScenario<FastInputHostActivity> {
        val scenario = ActivityScenario.launch<FastInputHostActivity>(
            Intent(context, FastInputHostActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        awaitHostWindowFocus(scenario)
        return scenario
    }

    private fun awaitHostWindowFocus(
        scenario: ActivityScenario<FastInputHostActivity>
    ) {
        val startedAt = SystemClock.uptimeMillis()
        val deadline = startedAt + HOST_WINDOW_FOCUS_TIMEOUT_MS
        var recreated = false
        var lastState = "host activity unavailable"

        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { activity ->
                val editor = activity.editText
                val attached = editor.isAttachedToWindow
                val decorFocused = activity.window.decorView.hasWindowFocus()
                val editorWindowFocused = editor.hasWindowFocus()
                val editorFocused = editor.hasFocus()
                val shown = editor.isShown
                ready = attached &&
                    decorFocused &&
                    editorWindowFocused &&
                    editorFocused &&
                    shown
                lastState =
                    "attached=$attached decorFocused=$decorFocused " +
                        "editorWindowFocused=$editorWindowFocused " +
                        "editorFocused=$editorFocused shown=$shown"
                if (decorFocused) {
                    activity.requestImeForEditor()
                }
            }
            if (ready) return

            if (
                !recreated &&
                SystemClock.uptimeMillis() - startedAt >= HOST_WINDOW_RECREATE_AFTER_MS
            ) {
                Log.w(TAG, "Recreating host after window-focus timeout ($lastState)")
                scenario.recreate()
                recreated = true
            }
            SystemClock.sleep(POLL_MS)
        }

        val focusedWindow = shell(
            "dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'"
        ).replace('\n', ' ')
        throw SetupException(
            "Host activity did not gain window focus " +
                "within ${HOST_WINDOW_FOCUS_TIMEOUT_MS}ms " +
                "($lastState, windowManager=[$focusedWindow])"
        )
    }

    private fun assertDeviceReady(
        context: Context,
        expectedIme: String,
        scenario: ActivityScenario<FastInputHostActivity>? = null
    ) {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        if (!powerManager.isInteractive) {
            throw SetupException("Device screen is not interactive")
        }
        if (keyguardManager.isKeyguardLocked) {
            throw SetupException("Device is locked by keyguard")
        }
        val currentIme = currentIme(context)
        if (!sameComponent(currentIme, expectedIme)) {
            throw SetupException("Default IME is [$currentIme], expected [$expectedIme]")
        }
        if (scenario != null) {
            var editorReady = false
            scenario.onActivity { activity ->
                editorReady = activity.editText.hasFocus() && activity.editText.isShown
            }
            if (!editorReady) {
                throw SetupException("Physical-test host editor is not focused")
            }
        }
    }

    private fun configureAccessibilityInspection() {
        val info = uiAutomation.serviceInfo
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        uiAutomation.serviceInfo = info
    }

    private fun runPhysicalDeviceSession(
        name: String,
        block: (PhysicalDeviceSession) -> Unit
    ) {
        val context = instrumentation.targetContext
        assertDeviceReadyBeforeMutation(context)
        configureAccessibilityInspection()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val originalPreferences = preferences.all.toMap()
        val originalIme = currentIme(context)
            .takeUnless { it.isBlank() || it == "null" }
        val expectedTargetIme =
            "${context.packageName}/" +
                "com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val enabledImeIds = listImeIds(includeDisabled = false)
        val targetImeInitiallyEnabled =
            enabledImeIds.any { sameComponent(it, expectedTargetIme) }
        val targetIme = awaitAvailableIme(expectedTargetIme)
        val outputDirectory = File(
            context.getExternalFilesDir("fast-input"),
            "${name}-${System.currentTimeMillis()}"
        )
        check(outputDirectory.mkdirs() || outputDirectory.isDirectory) {
            "Unable to create $outputDirectory"
        }
        val session = PhysicalDeviceSession(
            context = context,
            preferences = preferences,
            targetIme = targetIme,
            originalIme = originalIme,
            targetImeInitiallyEnabled = targetImeInitiallyEnabled,
            outputDirectory = outputDirectory
        )

        sendProgress(
            "FAST_INPUT_SESSION name=$name device=${android.os.Build.MODEL} " +
                "sdk=${android.os.Build.VERSION.SDK_INT} output=$outputDirectory " +
                "originalIme=$originalIme initiallyEnabled=$targetImeInitiallyEnabled\n"
        )

        try {
            setIme(context, targetIme)
            assertDeviceReady(context, targetIme)
            block(session)
        } finally {
            restorePreferences(preferences, originalPreferences)
            if (originalIme != null) {
                runCatching { setIme(context, originalIme) }
                    .onFailure { reportImeRestoreFailure("restore default", originalIme, it) }
            }
            if (
                !targetImeInitiallyEnabled &&
                (originalIme == null || !sameComponent(originalIme, targetIme))
            ) {
                runCatching { disableIme(targetIme) }
                    .onFailure { reportImeRestoreFailure("restore enabled state", targetIme, it) }
            }
            uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            sendProgress(
                "FAST_INPUT_RESTORED preferences=true ime=${originalIme ?: targetIme} " +
                    "rotation=unfrozen\n"
            )
        }
    }

    private fun assertDeviceReadyBeforeMutation(context: Context) {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        val errors = mutableListOf<String>()
        if (!powerManager.isInteractive) errors += "screen is not interactive"
        if (keyguardManager.isKeyguardLocked) errors += "keyguard is locked"
        if (errors.isNotEmpty()) {
            val message =
                "SETUP_ERROR: physical-device test not started (${errors.joinToString()})"
            sendProgress("$message\n")
            throw AssertionError(message)
        }
    }

    private fun ensureTargetImeSelected(session: PhysicalDeviceSession) {
        if (!sameComponent(currentIme(session.context), session.targetIme)) {
            setIme(session.context, session.targetIme)
        }
    }

    private fun awaitAvailableIme(component: String): String {
        val deadline = SystemClock.uptimeMillis() + IME_REGISTRATION_TIMEOUT_MS
        var availableImeIds = emptyList<String>()
        while (SystemClock.uptimeMillis() < deadline) {
            availableImeIds = listImeIds(includeDisabled = true)
            availableImeIds.firstOrNull { sameComponent(it, component) }?.let { return it }
            SystemClock.sleep(IME_STATE_POLL_MS)
        }
        throw SetupException(
            "IME was not registered by InputMethodManager: target=$component " +
                "available=$availableImeIds enabled=${listImeIds(includeDisabled = false)}"
        )
    }

    private fun ensureImeEnabled(component: String) {
        val deadline = SystemClock.uptimeMillis() + IME_ENABLE_TIMEOUT_MS
        var enabledImeIds = listImeIds(includeDisabled = false)
        while (SystemClock.uptimeMillis() < deadline) {
            if (enabledImeIds.any { sameComponent(it, component) }) return
            shell("ime enable $component")
            SystemClock.sleep(IME_STATE_POLL_MS)
            enabledImeIds = listImeIds(includeDisabled = false)
        }
        throw SetupException(
            "Unable to enable IME $component; " +
                "available=${listImeIds(includeDisabled = true)} enabled=$enabledImeIds"
        )
    }

    private fun disableIme(component: String) {
        val deadline = SystemClock.uptimeMillis() + IME_ENABLE_TIMEOUT_MS
        var enabledImeIds = listImeIds(includeDisabled = false)
        while (SystemClock.uptimeMillis() < deadline) {
            if (enabledImeIds.none { sameComponent(it, component) }) return
            shell("ime disable $component")
            SystemClock.sleep(IME_STATE_POLL_MS)
            enabledImeIds = listImeIds(includeDisabled = false)
        }
        throw SetupException(
            "Unable to restore disabled state for IME $component; enabled=$enabledImeIds"
        )
    }

    private fun setIme(context: Context, component: String) {
        awaitAvailableIme(component)
        ensureImeEnabled(component)
        val deadline = SystemClock.uptimeMillis() + IME_SWITCH_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            shell("ime set $component")
            val attemptDeadline = minOf(
                deadline,
                SystemClock.uptimeMillis() + IME_SWITCH_RETRY_MS
            )
            while (SystemClock.uptimeMillis() < attemptDeadline) {
                if (sameComponent(currentIme(context), component)) {
                    return
                }
                SystemClock.sleep(POLL_MS)
            }
        }
        throw SetupException(
            "Unable to select IME $component; current=${currentIme(context)} " +
                "available=${listImeIds(includeDisabled = true)} " +
                "enabled=${listImeIds(includeDisabled = false)}"
        )
    }

    private fun currentIme(context: Context): String =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ).orEmpty().trim()

    private fun listImeIds(includeDisabled: Boolean): List<String> =
        shell(if (includeDisabled) "ime list -a -s" else "ime list -s")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()

    private fun reportImeRestoreFailure(action: String, component: String, error: Throwable) {
        val message =
            "FAST_INPUT_RESTORE_ERROR action=$action ime=$component error=${error.message}\n"
        Log.e(TAG, message.trim(), error)
        sendProgress(message)
    }

    private fun sameComponent(first: String, second: String): Boolean {
        val firstComponent = ComponentName.unflattenFromString(first)
        val secondComponent = ComponentName.unflattenFromString(second)
        return if (firstComponent != null && secondComponent != null) {
            firstComponent == secondComponent
        } else {
            first == second
        }
    }

    private fun shell(command: String): String {
        val descriptor = uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText().trim() }
    }

    private fun saveScreenshot(
        session: PhysicalDeviceSession,
        token: String
    ) {
        runCatching {
            val file = File(
                session.outputDirectory,
                "${token.replace(Regex("[^A-Za-z0-9_.-]"), "_")}.png"
            )
            val bitmap = uiAutomation.takeScreenshot()
            FileOutputStream(file).use { stream ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
            }
            bitmap.recycle()
            Log.i(TAG, "SCREENSHOT\t$file")
            sendProgress("FAST_INPUT_SCREENSHOT $file\n")
        }.onFailure {
            Log.e(TAG, "Failed to save screenshot for $token", it)
            sendProgress("FAST_INPUT_SCREENSHOT_ERROR token=$token error=${it.message}\n")
        }
    }

    private fun restorePreferences(
        preferences: SharedPreferences,
        original: Map<String, *>
    ) {
        val editor = preferences.edit().clear()
        original.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                }
            }
        }
        check(editor.commit()) { "Failed to restore original preferences" }
    }

    private fun sendProgress(message: String) {
        instrumentation.sendStatus(
            2,
            Bundle().apply {
                putString(Instrumentation.REPORT_KEY_STREAMRESULT, message)
            }
        )
    }

    private sealed interface NodeLocator {
        fun render(): String

        data class Id(val name: String) : NodeLocator {
            override fun render(): String = "id/$name"
        }

        data class Label(val label: String) : NodeLocator {
            override fun render(): String = "label/$label"
        }
    }

    private enum class TestKeyboard(
        val rootViewId: String,
        val firstKey: NodeLocator,
        val secondKey: NodeLocator,
        val primeKey: NodeLocator,
        val neighborKey: NodeLocator?,
        val firstText: String,
        val secondText: String,
        val primeText: String
    ) {
        TENKEY(
            rootViewId = "keyboard_view",
            firstKey = NodeLocator.Id("key_8"),
            secondKey = NodeLocator.Id("key_5"),
            primeKey = NodeLocator.Id("key_1"),
            neighborKey = NodeLocator.Id("key_5"),
            firstText = "や",
            secondText = "な",
            primeText = "あ"
        ),
        SUMIRE(
            rootViewId = "custom_layout_default",
            firstKey = NodeLocator.Label("や"),
            secondKey = NodeLocator.Label("や"),
            primeKey = NodeLocator.Label("あ"),
            neighborKey = NodeLocator.Label("な"),
            firstText = "や",
            secondText = "や",
            primeText = "あ"
        ),
        QWERTY(
            rootViewId = "qwerty_view",
            firstKey = NodeLocator.Id("key_y"),
            secondKey = NodeLocator.Id("key_y"),
            primeKey = NodeLocator.Id("key_s"),
            neighborKey = NodeLocator.Id("key_h"),
            firstText = "y",
            secondText = "y",
            primeText = "s"
        )
    }

    private enum class TestOrientation(val rotation: Int) {
        PORTRAIT(UiAutomation.ROTATION_FREEZE_0),
        LANDSCAPE(UiAutomation.ROTATION_FREEZE_90)
    }

    private data class TestCase(
        val keyboard: TestKeyboard,
        val columns: Int,
        val candidateTabVisible: Boolean,
        val toolbarVisible: Boolean,
        val toolbarIntegrated: Boolean,
        val orientation: TestOrientation
    ) {
        fun fileToken(): String =
            "${keyboard.name.lowercase()}-c$columns-" +
                "tab${if (candidateTabVisible) 1 else 0}-" +
                "tool${if (toolbarVisible) 1 else 0}-" +
                "int${if (toolbarIntegrated) 1 else 0}-" +
                orientation.name.lowercase()
    }

    private fun previewTestCase(keyboard: TestKeyboard) = TestCase(
        keyboard = keyboard,
        columns = 1,
        candidateTabVisible = false,
        toolbarVisible = false,
        toolbarIntegrated = false,
        orientation = TestOrientation.PORTRAIT,
    )

    private data class PhysicalDeviceSession(
        val context: Context,
        val preferences: SharedPreferences,
        val targetIme: String,
        val originalIme: String?,
        val targetImeInitiallyEnabled: Boolean,
        val outputDirectory: File
    )

    private data class CandidateState(
        val bounds: ScreenRect?,
        val texts: List<String>
    )

    private data class EditorDecorationSnapshot(
        val text: String,
        val backgrounds: List<BackgroundSpanSnapshot>,
        val underlines: List<SpanRangeSnapshot>,
    ) {
        fun matches(
            expectedText: String,
            expectedBackgroundColor: Int,
            expectedBackgroundEnd: Int,
        ): Boolean {
            val expectedUnderlineEnd = expectedText.length
            val hasBackground = backgrounds.any { span ->
                span.color == expectedBackgroundColor &&
                    span.start == 0 &&
                    span.end == expectedBackgroundEnd &&
                    span.flags and Spanned.SPAN_COMPOSING != 0
            }
            val hasUnderline = underlines.any { span ->
                span.start == 0 &&
                    span.end == expectedUnderlineEnd &&
                    span.flags and Spanned.SPAN_COMPOSING != 0
            }
            return text == expectedText && hasBackground && hasUnderline
        }

        companion object {
            val Empty = EditorDecorationSnapshot(
                text = "",
                backgrounds = emptyList(),
                underlines = emptyList(),
            )
        }
    }

    private data class BackgroundSpanSnapshot(
        val color: Int,
        val start: Int,
        val end: Int,
        val flags: Int,
    )

    private data class SpanRangeSnapshot(
        val start: Int,
        val end: Int,
        val flags: Int,
    )

    private data class FlickVerification(
        val name: String,
        val normalizedDelta: PointF,
        val expectedText: String
    )

    private data class ScreenRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val isValid: Boolean
            get() = right > left && bottom > top

        val center: PointF
            get() = PointF((left + right) / 2f, (top + bottom) / 2f)

        val width: Int
            get() = right - left

        val height: Int
            get() = bottom - top

        fun guideComparisonRegion(): ScreenRect {
            val horizontalInset = (width * 0.22f).toInt()
            return ScreenRect(
                left = left + horizontalInset,
                top = top,
                right = right - horizontalInset,
                bottom = top + (height * 0.48f).toInt()
            )
        }

        fun intersect(other: ScreenRect): ScreenRect = ScreenRect(
            left = maxOf(left, other.left),
            top = maxOf(top, other.top),
            right = minOf(right, other.right),
            bottom = minOf(bottom, other.bottom)
        )

        fun union(other: ScreenRect): ScreenRect = ScreenRect(
            left = minOf(left, other.left),
            top = minOf(top, other.top),
            right = maxOf(right, other.right),
            bottom = maxOf(bottom, other.bottom)
        )

        override fun toString(): String = "$left,$top-$right,$bottom"
    }

    private data class GeometrySnapshot(
        val root: ScreenRect,
        val first: ScreenRect,
        val second: ScreenRect,
        val prime: ScreenRect,
        val neighbor: ScreenRect?,
        val candidate: ScreenRect?
    ) {
        fun sameBounds(other: GeometrySnapshot?): Boolean =
            other != null &&
                root == other.root &&
                first == other.first &&
                second == other.second &&
                prime == other.prime &&
                neighbor == other.neighbor &&
                candidate == other.candidate

        fun renderTransition(after: GeometrySnapshot?): String {
            if (after == null) return "before=${render()} after=unavailable"
            return "before=${render()} after=${after.render()} " +
                "deltaRootTop=${after.root.top - root.top} " +
                "deltaFirstTop=${after.first.top - first.top}"
        }

        private fun render(): String =
            "root[$root],first[$first],second[$second],prime[$prime]," +
                "neighbor[$neighbor],candidate[$candidate]"
    }

    private enum class PhaseStatus {
        PASS,
        MISINPUT,
        INJECTION_ERROR
    }

    private data class PhaseResult(
        val status: PhaseStatus,
        val expected: String,
        val actual: String,
        val allEventsInjected: Boolean,
        val geometry: String
    ) {
        val passed: Boolean
            get() = status == PhaseStatus.PASS

        fun render(): String =
            "${status.name}(events=$allEventsInjected expected=[$expected] " +
                "actual=[$actual] $geometry)"

        companion object {
            fun create(
                expected: String,
                actual: String,
                allEventsInjected: Boolean,
                before: GeometrySnapshot,
                after: GeometrySnapshot?
            ): PhaseResult {
                val status = when {
                    !allEventsInjected -> PhaseStatus.INJECTION_ERROR
                    expected != actual -> PhaseStatus.MISINPUT
                    else -> PhaseStatus.PASS
                }
                return PhaseResult(
                    status = status,
                    expected = expected,
                    actual = actual,
                    allEventsInjected = allEventsInjected,
                    geometry = before.renderTransition(after)
                )
            }
        }
    }

    private data class PointerSpec(
        val id: Int,
        val point: PointF
    )

    private enum class RateCategory {
        EXPECTED,
        YA_NA,
        OTHER,
        MISSING
    }

    private data class RateTrialResult(
        val category: RateCategory,
        val actual: String,
        val allEventsInjected: Boolean,
        val geometry: String
    )

    private data class RateCounts(
        var expected: Int = 0,
        var yaNa: Int = 0,
        var other: Int = 0,
        var missing: Int = 0
    ) {
        fun add(category: RateCategory) {
            when (category) {
                RateCategory.EXPECTED -> expected += 1
                RateCategory.YA_NA -> yaNa += 1
                RateCategory.OTHER -> other += 1
                RateCategory.MISSING -> missing += 1
            }
        }

        fun render(): String =
            "expected=$expected,yaNa=$yaNa,other=$other,missing=$missing"
    }

    private class SetupException(
        message: String,
        cause: Throwable? = null
    ) : RuntimeException(message, cause)

    companion object {
        private const val TAG = "FastInputMatrix"
        private const val SEQUENCE_REPETITIONS = 8
        private const val DEFAULT_MATRIX_ROUNDS = 3
        private const val DEFAULT_RATE_TRIALS = 10
        private const val TAP_HOLD_MS = 18L
        private const val TAP_GAP_MS = 12L
        private const val FLICK_MOVE_STEPS = 4
        private const val FLICK_STEP_MS = 18L
        private const val PREVIEW_HOLD_ASSERT_MS = 120L
        private const val PREVIEW_TEXT_POLL_MS = 4L
        private const val PREVIEW_TEST_BACKGROUND_COLOR = 0x66336699
        private const val PREVIEW_PERFORMANCE_WARMUP_GESTURES = 30
        private const val PREVIEW_PERFORMANCE_GESTURES = 500
        private const val PREVIEW_GC_SETTLE_MS = 250L
        private const val PREVIEW_CANDIDATE_WARMUP = 3
        private const val PREVIEW_CANDIDATE_SAMPLES = 15
        private const val PREVIEW_CANDIDATE_POLL_MS = 1L
        private const val GUIDE_SETTLE_MS = 350L
        private const val GUIDE_CHANNEL_TOLERANCE = 12
        private const val MAX_GUIDE_CHANGED_PIXELS = 80
        private const val MIN_GUIDE_CONTROL_CHANGED_PIXELS = 120
        private const val QWERTY_LONG_PRESS_HOLD_MS = 350L
        private const val POLL_MS = 32L
        private const val GEOMETRY_SAMPLE_MS = 32L
        private const val GEOMETRY_STABLE_SAMPLES = 3
        private const val TEXT_STABLE_SAMPLES = 4
        private const val MAX_CANDIDATE_TEXTS = 8
        private const val SETUP_TIMEOUT_MS = 2_000L
        private const val RESULT_TIMEOUT_MS = 1_000L
        private const val ORIENTATION_TIMEOUT_MS = 4_000L
        private const val ORIENTATION_SETTLE_MS = 500L
        private const val HOST_WINDOW_FOCUS_TIMEOUT_MS = 15_000L
        private const val HOST_WINDOW_RECREATE_AFTER_MS = 4_000L
        private const val IME_LAYOUT_SETTLE_MS = 350L
        private const val EDITOR_CONNECTION_TIMEOUT_MS = 10_000L
        private const val EDITOR_CONNECTION_STABLE_SAMPLES = 3
        private const val IME_STATE_POLL_MS = 250L
        private const val IME_REGISTRATION_TIMEOUT_MS = 30_000L
        private const val IME_ENABLE_TIMEOUT_MS = 15_000L
        private const val IME_SWITCH_TIMEOUT_MS = 15_000L
        private const val IME_SWITCH_RETRY_MS = 1_000L
        private const val TOTAL_CASES = 3 * 3 * 2 * 2 * 2 * 2
        private const val CASES_PER_ORIENTATION = TOTAL_CASES / 2
        private const val RATE_CONFIGURATIONS = 2 * 2 * 2 * 2
        private const val RATE_CONFIGURATIONS_PER_ORIENTATION =
            RATE_CONFIGURATIONS / 2
        private val RATE_INTERVALS_MS = longArrayOf(120L, 80L, 60L, 40L, 30L)
    }
}
