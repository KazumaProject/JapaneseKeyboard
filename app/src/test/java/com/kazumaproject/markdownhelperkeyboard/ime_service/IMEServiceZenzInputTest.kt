package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ConversionCorrection
import com.kazumaproject.markdownhelperkeyboard.converter.session.CandidateQueryMode
import com.kazumaproject.markdownhelperkeyboard.converter.session.ConversionBackend
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryResult
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateRequestToken
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateRequestTracker
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IMEServiceZenzInputTest {
    private val service = IMEService().also {
        ReflectionHelpers.callInstanceMethod<Unit>(it, "attachBaseContext", ClassParameter.from(Context::class.java, ApplicationProvider.getApplicationContext<Context>()))
        it.appPreference = mock<AppPreference>()
        it.zenzRuntimeClient = mock()
        ReflectionHelpers.setField(it, "runtimeInputSharedPreferences", mock<SharedPreferences>())
        ReflectionHelpers.setField(it, "zenzEnableStatePreference", true)
        ReflectionHelpers.setField(it, "zenzEnableLongPressConversionPreference", true)
        ReflectionHelpers.setField(it, "isOmissionSearchEnable", true)
        ReflectionHelpers.setField(it, "candidateTabVisibility", false)
        ReflectionHelpers.setField(it, "hasHardwareKeyboardConnected", false)
    }
    private val tracker: CandidateRequestTracker = ReflectionHelpers.getField(service, "candidateRequestTracker")
    private val input: MutableStateFlow<String> = ReflectionHelpers.getField(service, "_inputString")
    private val corrected = Candidate("学校", 1, 4u, 1, yomi = "がっこう", correctionMask = ConversionCorrection.OMISSION)

    @After fun close() {
        listOf("scope", "ioScope").forEach { ReflectionHelpers.getField<CoroutineScope>(service, it).cancel() }
    }

    private lateinit var latestToken: CandidateRequestToken
    private lateinit var latestContext: Any

    private fun request(manual: Boolean = false, record: Boolean = true): Any {
        input.value = "かつこう"
        val token = tracker.begin(input.value, CandidateQueryMode.NO_TAB_DEFAULT, ConversionBackend.LEGACY)
        val sourceClass = IMEService::class.java.declaredClasses.single { it.simpleName == "ZenzRequestSource" }
        val source = sourceClass.enumConstants.single { (it as Enum<*>).name == if (manual) "ManualConvertLongPress" else "AutoLive" }
        val method = IMEService::class.java.declaredMethods.single { it.name == "beginZenzLiveRequest" }
        val request = method.apply { isAccessible = true }.invoke(service, input.value, emptyList<Candidate>(), source, if (manual) null else token, null)!!
        val context = request.javaClass.getDeclaredField("correctionContext").apply { isAccessible = true }.get(request)
        latestToken = token
        latestContext = context
        if (record) recordCurrentReading()
        return request
    }

    private fun recordCurrentReading() {
        IMEService::class.java.declaredMethods.single { it.name == "recordZenzReading" }.apply { isAccessible = true }
            .invoke(service, input.value, latestToken, latestContext, KanaKanjiQueryResult(listOf(corrected), bestNBestCandidate = corrected))
    }

    @Test fun manualRequestAwaitsTheInFlightLocalQuery() = runBlocking {
        val request = request(manual = true, record = false)
        val pendingClass = IMEService::class.java.declaredClasses.single { it.simpleName == "PendingZenzReading" }
        val completion = CompletableDeferred<Any>()
        val pending = pendingClass.declaredConstructors.single { it.parameterCount == 3 }.apply { isAccessible = true }
            .newInstance(latestToken, latestContext, completion)
        ReflectionHelpers.setField(service, "pendingZenzReading", pending)
        val reading = async(start = CoroutineStart.UNDISPATCHED) { resolve(request) }
        assertFalse("Manual conversion must await the original query", reading.isCompleted)
        recordCurrentReading()
        assertEquals("がっこう", reading.await())
    }

    @Test fun segmentRequestWaitsForPreparationAndUsesThatSegmentsNBest() = runBlocking {
        input.value = "かつこう"
        ReflectionHelpers.setField(service, "bunsetsuSeparation", true)
        ReflectionHelpers.setField(service, "bunsetsuCursorMove", true)
        ReflectionHelpers.getField<java.util.concurrent.atomic.AtomicBoolean>(service, "isHenkan").set(true)
        val segment = BunsetsuSegmentState(
            reading = input.value, displayText = "学校", candidates = listOf(corrected),
            candidatesLoaded = true, bestNBestCandidate = corrected,
        )
        val sessionClass = IMEService::class.java.declaredClasses.single { it.simpleName == "BunsetsuConversionSession" }
        val session = sessionClass.declaredConstructors.single { it.parameterCount == 9 }.apply { isAccessible = true }
            .newInstance(input.value, input.value, listOf(segment), 0L, null, "", 0, emptyList<List<Int>>(), 0)
        ReflectionHelpers.setField(service, "bunsetsuConversionSession", session)
        val mutex: Mutex = ReflectionHelpers.getField(service, "bunsetsuOperationMutex")
        val requests: SharedFlow<Any> = ReflectionHelpers.getField(service, "zenzRequest")
        val emitted = async(start = CoroutineStart.UNDISPATCHED) { requests.first() }
        mutex.lock()
        val dispatched = async(start = CoroutineStart.UNDISPATCHED) {
            suspendCoroutineUninterceptedOrReturn<Boolean> { continuation ->
                IMEService::class.java.declaredMethods.single { it.name == "requestZenzForCurrentBunsetsuSegmentIfNeeded" }
                    .apply { isAccessible = true }.invoke(service, false, continuation)
            }
        }
        try {
            assertNull(ReflectionHelpers.getField<StateFlow<*>>(service, "_zenzLiveSlotState").value)
            // Preparation replaces the immutable session while preserving its logical target.
            val prepared = sessionClass.declaredConstructors.single { it.parameterCount == 9 }
                .apply { isAccessible = true }
                .newInstance(input.value, input.value, listOf(segment), 0L, null, "", 0, emptyList<List<Int>>(), 0)
            ReflectionHelpers.setField(service, "bunsetsuConversionSession", prepared)
        } finally {
            mutex.unlock()
        }
        assertTrue(dispatched.await())
        assertEquals("がっこう", resolve(emitted.await()))
    }

    private suspend fun resolve(request: Any): String? = suspendCoroutineUninterceptedOrReturn { continuation ->
        IMEService::class.java.declaredMethods.single { it.name == "resolveZenzLiveRequestInput" }.apply { isAccessible = true }
            .invoke(service, request, continuation)
    }

    @Test fun automaticAndManualRequestsUseNBestReadingWithoutChangingComposingInput() = runBlocking {
        for (manual in listOf(false, true)) {
            assertEquals("がっこう", resolve(request(manual)))
            assertEquals("かつこう", input.value)
        }
    }

    @Test fun rejectsOlderCandidateRevisionEvenForTheSameInput() = runBlocking {
        val request = request()
        tracker.begin(input.value, CandidateQueryMode.NO_TAB_DEFAULT, ConversionBackend.LEGACY)
        assertNull(resolve(request))
    }

    @Test fun rejectsChangedInputAndCorrectionSettings() = runBlocking {
        var request = request()
        input.value = "かつ"
        assertNull(resolve(request))
        request = request()
        ReflectionHelpers.setField(service, "isOmissionSearchEnable", false)
        assertNull(resolve(request))
    }
}
