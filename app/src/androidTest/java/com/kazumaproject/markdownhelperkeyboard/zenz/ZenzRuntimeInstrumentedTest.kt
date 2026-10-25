package com.kazumaproject.markdownhelperkeyboard.zenz

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import com.kazumaproject.markdownhelperkeyboard.zenz.runtime.IZenzRuntime
import com.kazumaproject.markdownhelperkeyboard.zenz.runtime.IZenzRuntimeCallback
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZenzRuntimeInstrumentedTest {
    private lateinit var context: Context
    private var runtime: IZenzRuntime? = null
    private var connection: ServiceConnection? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Zenz is only available in the full edition", AppVariantConfig.hasZenz)
    }

    @After
    fun tearDown() {
        runCatching { runtime?.closeEngine() }
        connection?.let { runCatching { context.unbindService(it) } }
        runtime = null
        connection = null
    }

    @Test(timeout = 120_000)
    fun separateProcessGeneratesScoresAndSkipsCancelledQueue() {
        val service = bindRuntime()
        val model = copyBundledModel()
        val runtimePid = initialize(service, model)
        assertTrue("Runtime process PID was not reported", runtimePid > 0)
        assertNotEquals(
            "Zenz must run outside the instrumentation process",
            Process.myPid(),
            runtimePid,
        )

        val generated = generate(service, requestId = 10L, input = "コンニチハ")
        assertTrue("Zenz returned an empty conversion", generated.isNotBlank())

        val scores = score(
            service = service,
            requestId = 11L,
            input = "コンニチハ",
            candidates = arrayOf("こんにちは", "今日は"),
        )
        assertEquals(2, scores.size)
        assertTrue("Zenz returned no finite score", scores.any(Float::isFinite))

        // This is the service-level equivalent of 0 ms debounce under rapid typing. Every stale
        // request is cancelled before the final request; queued stale work must never block it.
        repeat(8) { index ->
            val requestId = 100L + index
            service.generate(
                requestId,
                "",
                "",
                "",
                "",
                "",
                "",
                "ナガイニュウリョクヲレンゾクシテキャンセルスル",
                128,
                noOpCallback(),
            )
            service.cancel(requestId)
        }
        val finalResult = generate(service, requestId = 200L, input = "アリガトウ")
        assertTrue("Final request was blocked by cancelled work", finalResult.isNotBlank())
    }

    @Test(timeout = 180_000)
    fun generatedCandidatesStopAtProtocolBoundaryAcrossReadings() {
        val service = bindRuntime()
        initialize(service, copyBundledModel())

        val readings = arrayOf(
            "マツ",
            "まつ",
            "セイドガタカイ",
            "コンニチハ",
            "アリガトウ",
        )
        readings.forEachIndexed { index, reading ->
            val candidate = generate(
                service = service,
                requestId = 300L + index,
                input = reading,
            )
            assertTrue("No complete candidate for reading=$reading", candidate.isNotBlank())
            assertSafeCandidate(reading, candidate)
            if (reading == "マツ") {
                // This is a semantic regression guard for the bundled model:
                // text after U+EE08 must never be appended to the candidate.
                assertEquals("末", candidate)
            }
        }

        // The protocol range is reserved by Zenz, so even a context value that
        // contains a marker must not be able to change the output contract.
        val candidateWithCleanContext = generateWithFields(
            service = service,
            requestId = 400L,
            profile = "一般",
            topic = "日常",
            style = "普通",
            preference = "標準",
            leftContext = "左文脈",
            rightContext = "右文脈",
            input = "まつ",
        )
        val candidateWithMarkerInContext = generateWithFields(
            service = service,
            requestId = 401L,
            profile = "一般\uEE00",
            topic = "日常\uEE0F",
            style = "普通\uEE08",
            preference = "標準\uEE06",
            leftContext = "左文脈\uEE02",
            rightContext = "右文脈\uEE07",
            input = "まつ",
        )
        assertEquals(
            "Protocol markers in structured fields changed the prompt",
            candidateWithCleanContext,
            candidateWithMarkerInContext,
        )
        assertTrue(
            "Context marker caused an incomplete candidate",
            candidateWithMarkerInContext.isNotBlank(),
        )
        assertSafeCandidate("まつ with marked context", candidateWithMarkerInContext)
    }

    @Test(timeout = 900_000)
    fun measureGenerationPerformance() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("zenzGenerationPerf") == "true")
        val ins = InstrumentationRegistry.getInstrumentation()
        val report = StringBuilder()
        fun memory(pid: Int, label: String) {
            val descriptor = ins.uiAutomation.executeShellCommand("dumpsys meminfo $pid")
            val result = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                .bufferedReader().use { it.readText() }
            report.appendLine("memory/$label/pid=$pid\n$result")
        }
        memory(Process.myPid(), "imeBeforeLoad")
        val bindStart = android.os.SystemClock.elapsedRealtimeNanos()
        val service = bindRuntime()
        report.appendLine("serviceBindNs=${android.os.SystemClock.elapsedRealtimeNanos()-bindStart}")
        val copyStart = android.os.SystemClock.elapsedRealtimeNanos()
        val model = copyBundledModel()
        report.appendLine("modelAssetCopyNs=${android.os.SystemClock.elapsedRealtimeNanos()-copyStart}")
        val start = android.os.SystemClock.elapsedRealtimeNanos()
        val pid = initialize(service, model, nThreads = 4)
        report.appendLine("modelLoadNs=${android.os.SystemClock.elapsedRealtimeNanos()-start},pid=$pid,nCtx=512,nThreads=4,maxTokens=32")
        memory(pid, "zenzLoaded")
        memory(Process.myPid(), "imeLoaded")
        if (args.getString("coldOnly") != "true") {
            val readings = listOf("キョウ", "ガッコウ", "センセイ", "コウエンニイキマス")
            var requestId = 10L
            for (reading in readings) {
                repeat(3) { generate(service, requestId++, reading) }
                val samples = ArrayList<Long>()
                repeat(20) {
                    val generatedAt = android.os.SystemClock.elapsedRealtimeNanos()
                    val result = generate(service, requestId++, reading)
                    samples += android.os.SystemClock.elapsedRealtimeNanos()-generatedAt
                    assertTrue("Empty conversion for $reading", result.isNotBlank())
                    assertSafeCandidate(reading, result)
                    report.appendLine("generation,$reading,ns=${samples.last()},result=$result")
                    File(context.filesDir, "zenz-generation-perf.txt").writeText(report.toString())
                }
                val sorted = samples.sorted()
                report.appendLine("summary,$reading,meanMs=${samples.average()/1e6},p50Ms=${sorted[9]/1e6},p95Ms=${sorted[18]/1e6}")
                memory(pid, "zenzAfter/$reading")
                memory(Process.myPid(), "imeAfter/$reading")
            }
        }
        File(context.filesDir, "zenz-generation-perf.txt").writeText(report.toString())
    }

    private fun bindRuntime(): IZenzRuntime {
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                runtime = IZenzRuntime.Stub.asInterface(binder)
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                runtime = null
            }
        }
        this.connection = connection
        val intent = Intent().setClassName(
            context.packageName,
            "com.kazumaproject.markdownhelperkeyboard.zenz.runtime.ZenzRuntimeService",
        )
        assertTrue(
            "Zenz runtime service could not be bound",
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE),
        )
        assertTrue("Timed out binding Zenz runtime", connected.await(10, TimeUnit.SECONDS))
        return requireNotNull(runtime)
    }

    private fun copyBundledModel(): File {
        val target = File(context.cacheDir, MODEL_ASSET_NAME)
        context.assets.open(MODEL_ASSET_NAME).use { input ->
            FileOutputStream(target).use(input::copyTo)
        }
        return target
    }

    private fun initialize(service: IZenzRuntime, model: File, nThreads: Int = 2): Int {
        val completed = CountDownLatch(1)
        val processPid = AtomicInteger(-1)
        val error = AtomicReference<String?>(null)
        val requestId = 1L
        service.initialize(
            requestId,
            model.absolutePath,
            512,
            nThreads,
            object : IZenzRuntimeCallback.Stub() {
                override fun onReady(callbackRequestId: Long, pid: Int) {
                    if (callbackRequestId == requestId) {
                        processPid.set(pid)
                        completed.countDown()
                    }
                }

                override fun onStringResult(requestId: Long, result: String) = Unit
                override fun onScoresResult(requestId: Long, scores: FloatArray) = Unit

                override fun onError(callbackRequestId: Long, message: String) {
                    if (callbackRequestId == requestId) {
                        error.set(message)
                        completed.countDown()
                    }
                }
            },
        )
        assertTrue("Zenz initialization timed out", completed.await(30, TimeUnit.SECONDS))
        check(error.get() == null) { "Zenz initialization failed: ${error.get()}" }
        return processPid.get()
    }

    private fun generate(service: IZenzRuntime, requestId: Long, input: String): String {
        return generateWithFields(
            service = service,
            requestId = requestId,
            input = input,
        )
    }

    private fun generateWithFields(
        service: IZenzRuntime,
        requestId: Long,
        profile: String = "",
        topic: String = "",
        style: String = "",
        preference: String = "",
        leftContext: String = "",
        rightContext: String = "",
        input: String,
    ): String {
        val completed = CountDownLatch(1)
        val result = AtomicReference<String?>(null)
        val error = AtomicReference<String?>(null)
        service.generate(
            requestId,
            profile,
            topic,
            style,
            preference,
            leftContext,
            rightContext,
            input,
            32,
            object : IZenzRuntimeCallback.Stub() {
                override fun onReady(requestId: Long, processId: Int) = Unit

                override fun onStringResult(callbackRequestId: Long, value: String) {
                    if (callbackRequestId == requestId) {
                        result.set(value)
                        completed.countDown()
                    }
                }

                override fun onScoresResult(requestId: Long, scores: FloatArray) = Unit

                override fun onError(callbackRequestId: Long, message: String) {
                    if (callbackRequestId == requestId) {
                        error.set(message)
                        completed.countDown()
                    }
                }
            },
        )
        assertTrue("Zenz generate timed out", completed.await(30, TimeUnit.SECONDS))
        check(error.get() == null) { "Zenz generate failed: ${error.get()}" }
        return result.get().orEmpty()
    }

    private fun assertSafeCandidate(reading: String, candidate: String) {
        assertTrue(
            "Protocol marker leaked for reading=$reading",
            candidate.none { it.code in 0xEE00..0xEE0F },
        )
        assertTrue(
            "Control character leaked for reading=$reading",
            candidate.none { it.isISOControl() || it == '\uFFFD' },
        )
    }

    private fun score(
        service: IZenzRuntime,
        requestId: Long,
        input: String,
        candidates: Array<String>,
    ): FloatArray {
        val completed = CountDownLatch(1)
        val result = AtomicReference<FloatArray?>(null)
        val error = AtomicReference<String?>(null)
        service.score(
            requestId,
            "",
            "",
            "",
            "",
            "",
            "",
            input,
            candidates,
            object : IZenzRuntimeCallback.Stub() {
                override fun onReady(requestId: Long, processId: Int) = Unit
                override fun onStringResult(requestId: Long, result: String) = Unit

                override fun onScoresResult(callbackRequestId: Long, scores: FloatArray) {
                    if (callbackRequestId == requestId) {
                        result.set(scores)
                        completed.countDown()
                    }
                }

                override fun onError(callbackRequestId: Long, message: String) {
                    if (callbackRequestId == requestId) {
                        error.set(message)
                        completed.countDown()
                    }
                }
            },
        )
        assertTrue("Zenz score timed out", completed.await(30, TimeUnit.SECONDS))
        check(error.get() == null) { "Zenz score failed: ${error.get()}" }
        return result.get() ?: FloatArray(0)
    }

    private fun noOpCallback(): IZenzRuntimeCallback {
        return object : IZenzRuntimeCallback.Stub() {
            override fun onReady(requestId: Long, processId: Int) = Unit
            override fun onStringResult(requestId: Long, result: String) = Unit
            override fun onScoresResult(requestId: Long, scores: FloatArray) = Unit
            override fun onError(requestId: Long, message: String) = Unit
        }
    }

    private companion object {
        const val MODEL_ASSET_NAME = "ggml-model-Q5_K_M.gguf"
    }
}
