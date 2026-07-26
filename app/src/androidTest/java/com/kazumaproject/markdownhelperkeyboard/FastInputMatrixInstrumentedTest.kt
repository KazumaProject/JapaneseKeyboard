package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.app.KeyguardManager
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PointF
import android.graphics.Rect
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
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
                                        reloadIme(session)
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
                                reloadIme(session)
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
        scenario.onActivity { activity ->
            activity.editText.setText("")
            activity.editText.requestFocus()
            activity.editText.setSelection(0)
            val inputMethodManager = activity.getSystemService(InputMethodManager::class.java)
            inputMethodManager.restartInput(activity.editText)
            activity.editText.windowInsetsController?.show(WindowInsets.Type.ime())
            inputMethodManager.showSoftInput(
                activity.editText,
                InputMethodManager.SHOW_FORCED
            )
        }
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
        SystemClock.sleep(HOST_LAUNCH_SETTLE_MS)
        return scenario
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
        val currentIme = shell("settings get secure default_input_method")
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
        val originalIme = shell("settings get secure default_input_method")
            .takeUnless { it.isBlank() || it == "null" }
        val expectedTargetIme =
            "${context.packageName}/" +
                "com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        // `-a` also returns installed-but-disabled IMEs. Android 16 reports those entries but
        // rejects `ime set`, so only select a reload fallback from the currently enabled list.
        val installedImeIds = shell("ime list -s")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        val targetIme = installedImeIds
            .firstOrNull { sameComponent(it, expectedTargetIme) }
            ?: expectedTargetIme
        val fallbackIme = installedImeIds
            .filterNot { sameComponent(it, targetIme) }
            .minByOrNull(::imeReloadFallbackPriority)
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
            fallbackIme = fallbackIme,
            originalIme = originalIme,
            outputDirectory = outputDirectory
        )

        sendProgress(
            "FAST_INPUT_SESSION name=$name device=${android.os.Build.MODEL} " +
                "sdk=${android.os.Build.VERSION.SDK_INT} output=$outputDirectory " +
                "originalIme=$originalIme fallbackIme=$fallbackIme\n"
        )

        try {
            setIme(targetIme)
            assertDeviceReady(context, targetIme)
            block(session)
        } finally {
            restorePreferences(preferences, originalPreferences)
            // Reload the restored settings before handing the device back.
            runCatching { setIme(targetIme) }
            if (originalIme != null) {
                runCatching { setIme(originalIme) }
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

    private fun reloadIme(session: PhysicalDeviceSession) {
        session.fallbackIme?.let {
            setIme(it)
            SystemClock.sleep(IME_TOGGLE_SETTLE_MS)
        }
        setIme(session.targetIme)
        SystemClock.sleep(IME_RELOAD_SETTLE_MS)
        if (!sameComponent(shell("settings get secure default_input_method"), session.targetIme)) {
            // A cold emulator can fall back while the target IME process is still starting.
            // Re-select only for setup; the measured key timing below remains unchanged.
            setIme(session.targetIme)
            SystemClock.sleep(IME_RELOAD_SETTLE_MS)
        }
    }

    private fun setIme(component: String) {
        val deadline = SystemClock.uptimeMillis() + IME_SWITCH_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            shell("ime set $component")
            val attemptDeadline = minOf(
                deadline,
                SystemClock.uptimeMillis() + IME_SWITCH_RETRY_MS
            )
            while (SystemClock.uptimeMillis() < attemptDeadline) {
                if (
                    sameComponent(
                        shell("settings get secure default_input_method"),
                        component
                    )
                ) {
                    return
                }
                SystemClock.sleep(POLL_MS)
            }
        }
        throw SetupException("Unable to select IME $component")
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

    private fun imeReloadFallbackPriority(component: String): Int {
        return when (ComponentName.unflattenFromString(component)?.packageName) {
            // The system keyboard remains selectable through repeated switches on stock devices.
            "com.google.android.inputmethod.latin" -> 0
            // An alternate installed build is also a keyboard-mode IME and is a safe fallback.
            "com.kazumaproject.markdownhelperkeyboard.lite.fdroid" -> 1
            // Auxiliary voice IMEs and third-party IMEs may reject repeated `ime set` calls.
            else -> 2
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

    private data class PhysicalDeviceSession(
        val context: Context,
        val preferences: SharedPreferences,
        val targetIme: String,
        val fallbackIme: String?,
        val originalIme: String?,
        val outputDirectory: File
    )

    private data class CandidateState(
        val bounds: ScreenRect?,
        val texts: List<String>
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

    private class SetupException(message: String) : RuntimeException(message)

    companion object {
        private const val TAG = "FastInputMatrix"
        private const val SEQUENCE_REPETITIONS = 8
        private const val DEFAULT_MATRIX_ROUNDS = 3
        private const val DEFAULT_RATE_TRIALS = 10
        private const val TAP_HOLD_MS = 18L
        private const val TAP_GAP_MS = 12L
        private const val POLL_MS = 32L
        private const val GEOMETRY_SAMPLE_MS = 32L
        private const val GEOMETRY_STABLE_SAMPLES = 3
        private const val TEXT_STABLE_SAMPLES = 4
        private const val MAX_CANDIDATE_TEXTS = 8
        private const val SETUP_TIMEOUT_MS = 2_000L
        private const val RESULT_TIMEOUT_MS = 1_000L
        private const val ORIENTATION_TIMEOUT_MS = 4_000L
        private const val ORIENTATION_SETTLE_MS = 500L
        private const val HOST_LAUNCH_SETTLE_MS = 1_000L
        private const val IME_LAYOUT_SETTLE_MS = 350L
        private const val IME_TOGGLE_SETTLE_MS = 100L
        private const val IME_RELOAD_SETTLE_MS = 600L
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
