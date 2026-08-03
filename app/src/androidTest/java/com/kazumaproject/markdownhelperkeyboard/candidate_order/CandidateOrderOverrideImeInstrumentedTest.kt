package com.kazumaproject.markdownhelperkeyboard.candidate_order

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.PointF
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.navigation.Navigation
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.CandidateOrderScope
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CandidateOrderOverrideImeInstrumentedTest {

    private val instrumentation: Instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun lexicalHiOrderPromotesHiNodeInsideHiwoUsingSoftKeyboardTouches() = runBlocking {
        verifyHiwoUsingSoftKeyboardTouches(
            scope = CandidateOrderScope.LEXICAL_UNIT,
            expectedFirstCandidate = "火を",
        )
    }

    @Test
    fun exactHiOrderDoesNotAffectHiwoUsingSoftKeyboardTouches() = runBlocking {
        verifyHiwoUsingSoftKeyboardTouches(
            scope = CandidateOrderScope.EXACT_INPUT,
            expectedFirstCandidate = "日を",
        )
    }

    @Test
    fun candidateOrderScreenShowsAndSwitchesBothScopesOnDevice() {
        configureAccessibilityInspection()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java),
        )
        try {
            scenario.onActivity { activity ->
                Navigation.findNavController(
                    activity,
                    R.id.nav_host_fragment_activity_main,
                ).navigate(R.id.candidateOrderOverrideFragment)
            }
            instrumentation.waitForIdleSync()

            assertTrue(awaitVisibleText("この読みだけ（完全一致）").width() > 0)
            val lexicalScope = awaitVisibleText("同じ語として使われる場合")
            assertTrue(tap(lexicalScope.center()))
            instrumentation.waitForIdleSync()
            assertTrue(
                awaitVisibleText(
                    "通常変換の第一候補がこの読みを独立した同じ語として解析した場合だけ、長い入力にも適用します。異なる語区切りには影響しません。",
                ).width() > 0,
            )
        } finally {
            scenario.close()
        }
    }

    private suspend fun verifyHiwoUsingSoftKeyboardTouches(
        scope: CandidateOrderScope,
        expectedFirstCandidate: String,
    ) {
        configureAccessibilityInspection()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val candidateOrderRepository = entryPoint.candidateOrderOverrideRepository()
        val originalHiOrders = candidateOrderRepository.observeAll()
            .first()
            .filter { it.input.trim() == "ひ" }
            .groupBy { CandidateOrderScope.fromDatabase(it.scope) }
            .mapValues { (_, rows) -> rows.sortedBy { it.rank }.map { it.candidate } }
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val originalPreferences = preferences.all
        preferences
            .edit()
            .putBoolean("candidate_order_override_enable_preference", true)
            .putString(
                "keyboard_order_preference",
                """["TENKEY","SUMIRE","QWERTY","ROMAJI","CUSTOM"]""",
            )
            .putBoolean("save_last_used_keyboard", false)
            .putBoolean("flick_input_only_preference", true)
            .putBoolean("live_conversion_preference", false)
            .commit()
        candidateOrderRepository.saveOrder(
            input = "ひ",
            candidates = listOf("火", "日", "陽"),
            scope = scope,
        )

        val scenario = ActivityScenario.launch<FastInputHostActivity>(
            Intent(context, FastInputHostActivity::class.java),
        )
        try {
            scenario.onActivity { it.restartEditorInput(clearText = true) }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(1_500)

            assertTrue(flickLeft("key_6")) // は -> ひ
            assertTrue(flickLeft("key_11")) // わ -> を

            val candidates = awaitVisibleCandidates()
            assertEquals(expectedFirstCandidate, candidates.first().first)
            assertTrue(tap(candidates.first().second.center()))

            val deadline = SystemClock.uptimeMillis() + 8_000L
            var actual = ""
            while (SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { actual = it.editText.text.toString() }
                if (actual == expectedFirstCandidate) break
                SystemClock.sleep(50)
            }
            assertEquals(expectedFirstCandidate, actual)
        } finally {
            scenario.close()
            candidateOrderRepository.deleteByInput("ひ")
            originalHiOrders.forEach { (scope, candidates) ->
                candidateOrderRepository.saveOrder("ひ", candidates, scope)
            }
            preferences.edit().clear().also { editor ->
                originalPreferences.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is String -> editor.putString(key, value)
                        is Set<*> -> editor.putStringSet(
                            key,
                            value.filterIsInstance<String>().toSet(),
                        )
                    }
                }
            }.commit()
        }
    }

    @Test
    fun lexicalHiOrderDoesNotChangeProductionHitoOosugiBaselinePath() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val candidateOrderRepository = entryPoint.candidateOrderOverrideRepository()
        val originalLexicalOrder = candidateOrderRepository.observeAll()
            .first()
            .filter {
                it.input.trim() == "ひ" &&
                    CandidateOrderScope.fromDatabase(it.scope) == CandidateOrderScope.LEXICAL_UNIT
            }
            .sortedBy { it.rank }
            .map { it.candidate }
        val input = "ひとおおすぎ"
        val segments = LinkedHashMap<String, List<CandidateConversionSegment>>()

        try {
            candidateOrderRepository.saveOrder(
                input = "ひ",
                candidates = listOf("火", "日", "陽"),
                scope = CandidateOrderScope.LEXICAL_UNIT,
            )
            val baselineCandidates = entryPoint.kanaKanjiEngine()
                .getCandidatesWithBunsetsuSeparation(
                    input = input,
                    n = 4,
                    mozcUtPersonName = false,
                    mozcUTPlaces = false,
                    mozcUTWiki = false,
                    mozcUTNeologd = false,
                    mozcUTWeb = false,
                    userDictionaryRepository = entryPoint.userDictionaryRepository(),
                    learnRepository = null,
                    isOmissionSearchEnable = false,
                    enableTypoCorrectionJapaneseFlick = false,
                    enableTypoCorrectionQwertyEnglish = false,
                    typoCorrectionOffsetScore = 3000,
                    omissionSearchOffsetScore = 1900,
                    beamWidth = 20,
                    candidateSegmentCollector = segments,
                )
                .candidates
            assertTrue(baselineCandidates.isNotEmpty())

            val orderedCandidates = candidateOrderRepository.applyOrderFromSnapshot(
                input = input,
                candidates = baselineCandidates,
                candidateSegmentsByString = segments,
            )

            assertEquals(baselineCandidates.first().string, orderedCandidates.first().string)
            println(
                "CANDIDATE_ORDER_PRODUCTION_REGRESSION " +
                    "input=$input " +
                    "baselineFirst=${baselineCandidates.first().string} " +
                    "orderedFirst=${orderedCandidates.first().string} " +
                    "baselineSegments=${segments[baselineCandidates.first().string]}",
            )
        } finally {
            candidateOrderRepository.deleteRule("ひ", CandidateOrderScope.LEXICAL_UNIT)
            if (originalLexicalOrder.isNotEmpty()) {
                candidateOrderRepository.saveOrder(
                    "ひ",
                    originalLexicalOrder,
                    CandidateOrderScope.LEXICAL_UNIT,
                )
            }
        }
    }

    private fun configureAccessibilityInspection() {
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
    }

    private fun flickLeft(keyId: String): Boolean {
        val bounds = awaitVisibleBounds(keyId)
        val start = bounds.center()
        val end = PointF(start.x - bounds.width() * 0.70f, start.y)
        val downTime = SystemClock.uptimeMillis()
        var injected = inject(downTime, MotionEvent.ACTION_DOWN, start)
        SystemClock.sleep(16)
        injected = inject(downTime, MotionEvent.ACTION_MOVE, end) && injected
        SystemClock.sleep(16)
        injected = inject(downTime, MotionEvent.ACTION_UP, end) && injected
        SystemClock.sleep(100)
        return injected
    }

    private fun tap(point: PointF): Boolean {
        val downTime = SystemClock.uptimeMillis()
        var injected = inject(downTime, MotionEvent.ACTION_DOWN, point)
        SystemClock.sleep(32)
        injected = inject(downTime, MotionEvent.ACTION_UP, point) && injected
        return injected
    }

    private fun inject(downTime: Long, action: Int, point: PointF): Boolean {
        val event = MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            point.x,
            point.y,
            0,
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        return instrumentation.uiAutomation.injectInputEvent(event, true).also {
            event.recycle()
        }
    }

    private fun awaitVisibleBounds(idName: String): Rect {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        while (SystemClock.uptimeMillis() < deadline) {
            findVisibleNodeById(idName)?.let { node ->
                return Rect().also(node::getBoundsInScreen)
            }
            SystemClock.sleep(50)
        }
        error("Timed out waiting for visible id=$idName")
    }

    private fun awaitVisibleText(text: String): Rect {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        while (SystemClock.uptimeMillis() < deadline) {
            for (window in instrumentation.uiAutomation.windows) {
                val root = window.root ?: continue
                val found = findDescendant(root) { node ->
                    node.isVisibleToUser && node.text?.toString() == text
                }
                if (found != null) {
                    return Rect().also(found::getBoundsInScreen)
                }
            }
            SystemClock.sleep(50)
        }
        error("Timed out waiting for visible text=$text")
    }

    private fun awaitVisibleCandidates(): List<Pair<String, Rect>> {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        while (SystemClock.uptimeMillis() < deadline) {
            val recycler = findVisibleNodeById("suggestion_recycler_view")
            val candidates = buildList {
                if (recycler != null) {
                    for (index in 0 until recycler.childCount) {
                        val child = recycler.getChild(index) ?: continue
                        val textNode = findDescendant(child) { node ->
                            node.viewIdResourceName
                                ?.endsWith(":id/suggestion_item_text_view") == true
                        } ?: continue
                        val text = textNode.text?.toString().orEmpty().trim()
                        if (text.isNotEmpty()) {
                            add(text to Rect().also(textNode::getBoundsInScreen))
                        }
                    }
                }
            }
            if (candidates.isNotEmpty()) return candidates
            SystemClock.sleep(50)
        }
        error("Timed out waiting for candidates")
    }

    private fun findVisibleNodeById(idName: String): AccessibilityNodeInfo? {
        for (window in instrumentation.uiAutomation.windows) {
            val root = window.root ?: continue
            val found = findDescendant(root) { node ->
                node.isVisibleToUser &&
                    node.viewIdResourceName?.endsWith(":id/$idName") == true
            }
            if (found != null) return found
        }
        return null
    }

    private fun findDescendant(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
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

    private fun Rect.center(): PointF = PointF(exactCenterX(), exactCenterY())
}
