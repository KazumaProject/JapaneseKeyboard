package com.kazumaproject.markdownhelperkeyboard.zenz

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.zenz.runtime.IZenzRuntime
import com.kazumaproject.markdownhelperkeyboard.zenz.runtime.IZenzRuntimeCallback
import com.kazumaproject.markdownhelperkeyboard.zenz.runtime.ZenzRuntimeClient
import com.kazumaproject.markdownhelperkeyboard.zenz.runtime.ZenzRuntimeConfig
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt in with zenzBenchmark=true. Reports stay in app-private storage, outside source control. */
@RunWith(AndroidJUnit4::class)
class ZenzPerformanceInstrumentedTest {
    @Test(timeout = 1_800_000)
    fun measureRuntimeRequests() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("zenzBenchmark") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val iterations = (args.getString("zenzBenchmarkIterations") ?: "20").toInt()
        val threads = (args.getString("zenzBenchmarkThreads") ?: "4").toInt()
        val label = args.getString("zenzBenchmarkLabel") ?: "current"
        require(iterations > 0 && threads > 0 && label.matches(Regex("[A-Za-z0-9_-]+")))
        val models = args.getString("zenzBenchmarkManifest")?.let { manifest ->
            val file = File(manifest)
            val entries = JSONArray(file.readText())
            (0 until entries.length()).map { index ->
                val entry = entries.getJSONObject(index)
                Triple(entry.getString("name"), File(file.parentFile, entry.getString("filename")),
                    entry.getString("sha256"))
            }
        } ?: run {
            val file = File(context.cacheDir, "zenz-benchmark.gguf")
            context.assets.open("ggml-model-Q5_K_M.gguf").use { input ->
                file.outputStream().use(input::copyTo)
            }
            listOf(Triple("bundled", file, sha256(file)))
        }
        val report = JSONObject().put("label", label).put("device", Build.MODEL)
            .put("sdk", Build.VERSION.SDK_INT).put("nCtx", 512).put("nThreads", threads)
            .put("maxTokens", 32).put("iterationsPerReading", iterations)
            .put("thermalBefore", shell("dumpsys thermalservice"))
            .put("batteryBefore", shell("dumpsys battery"))
        val results = JSONArray()
        report.put("models", results)
        val output = File(context.filesDir, "zenz-benchmark-$label.json")
        val serviceReady = CountDownLatch(1)
        val runtime = AtomicReference<IZenzRuntime>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                runtime.set(IZenzRuntime.Stub.asInterface(binder))
                serviceReady.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        val intent = Intent().setClassName(context.packageName,
            "com.kazumaproject.markdownhelperkeyboard.zenz.runtime.ZenzRuntimeService")
        assertTrue(context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        val client = ZenzRuntimeClient(context)
        try {
            assertTrue(serviceReady.await(10, TimeUnit.SECONDS))
            for ((name, file, expectedHash) in models) {
                assertEquals("Unexpected model bytes: $name", expectedHash, sha256(file))
                val ready = CountDownLatch(1)
                val pid = AtomicInteger(-1)
                val error = AtomicReference<String?>()
                val loadStart = SystemClock.elapsedRealtimeNanos()
                runtime.get().initialize(1, file.absolutePath, 512, threads,
                    object : IZenzRuntimeCallback.Stub() {
                        override fun onReady(requestId: Long, processId: Int) {
                            pid.set(processId)
                            ready.countDown()
                        }
                        override fun onError(requestId: Long, message: String?) {
                            error.set(message ?: "unknown error")
                            ready.countDown()
                        }
                        override fun onStringResult(requestId: Long, result: String?) = Unit
                        override fun onScoresResult(requestId: Long, scores: FloatArray?) = Unit
                    })
                assertTrue("Model initialization timed out", ready.await(60, TimeUnit.SECONDS))
                val loadNs = SystemClock.elapsedRealtimeNanos() - loadStart
                assertEquals(error.get(), null, error.get())
                assertTrue(pid.get() > 0)
                val row = JSONObject().put("name", name).put("sha256", expectedHash)
                    .put("modelBytes", file.length()).put("pid", pid.get()).put("loadNs", loadNs)
                    .put("memoryLoaded", shell("dumpsys meminfo ${pid.get()}"))
                results.put(row)
                val config = ZenzRuntimeConfig(file.absolutePath, 512, threads)
                suspend fun generate(reading: String) = client.generate(config,
                    "", "", "", "", "", "", reading, 32)
                suspend fun score(reading: String, candidates: Array<String>) = client.score(config,
                    null, null, null, null, null, null, reading, candidates)
                suspend fun evaluate(reading: String, candidate: String) = client.evaluate(config,
                    null, null, null, null, null, null, reading, candidate)
                val firstStart = SystemClock.elapsedRealtimeNanos()
                val firstResult = generate(readings.first().first)
                row.put("firstGenerationNs", SystemClock.elapsedRealtimeNanos() - firstStart)
                    .put("firstGenerated", firstResult)
                assertTrue(firstResult.isNotBlank())
                // Warm all request paths. Generation clears its KV state on every request.
                repeat(3) {
                    for ((reading, candidates) in readings) {
                        generate(reading)
                        score(reading, candidates)
                        evaluate(reading, candidates.first())
                    }
                }
                row.put("memoryWarm", shell("dumpsys meminfo ${pid.get()}"))
                val samples = JSONArray()
                row.put("samples", samples)
                repeat(iterations) { iteration ->
                    // Rotate the first reading to distribute order and temperature effects.
                    readings.indices.forEach { offset ->
                        val (reading, candidates) = readings[(iteration + offset) % readings.size]
                        var start = SystemClock.elapsedRealtimeNanos()
                        val generated = generate(reading)
                        val generationNs = SystemClock.elapsedRealtimeNanos() - start
                        start = SystemClock.elapsedRealtimeNanos()
                        val scores = score(reading, candidates)
                        val scoreNs = SystemClock.elapsedRealtimeNanos() - start
                        start = SystemClock.elapsedRealtimeNanos()
                        val evaluation = evaluate(reading, candidates.first())
                        val evaluationNs = SystemClock.elapsedRealtimeNanos() - start
                        assertTrue("Empty generation: $name/$reading", generated.isNotBlank())
                        assertEquals(candidates.size, scores.size)
                        assertTrue(scores.all(Float::isFinite))
                        assertTrue("Invalid evaluation: $name/$reading", evaluation != "ERROR")
                        samples.put(JSONObject().put("reading", reading).put("iteration", iteration)
                            .put("generated", generated).put("generationNs", generationNs)
                            .put("candidates", JSONArray(candidates.toList())).put("scoreNs", scoreNs)
                            .put("evaluation", evaluation).put("evaluationNs", evaluationNs))
                    }
                }
                row.put("memoryAfter", shell("dumpsys meminfo ${pid.get()}"))
                output.writeText(report.toString(2))
            }
            report.put("thermalAfter", shell("dumpsys thermalservice"))
                .put("batteryAfter", shell("dumpsys battery"))
            output.writeText(report.toString(2))
        } finally {
            client.close()
            runCatching { runtime.get()?.closeEngine() }
            context.unbindService(connection)
        }
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private val readings = listOf(
        "キョウ" to arrayOf("今日", "京", "強"),
        "コンニチハ" to arrayOf("こんにちは", "今日は", "こんにちは。"),
        "カンジヘンカン" to arrayOf("漢字変換", "感じ変換", "漢字変換。"),
        "ハシヲワタル" to arrayOf("橋を渡る", "箸を渡る", "端を渡る"),
        "キョウハイイテンキデス" to arrayOf("今日はいい天気です", "今日は良い天気です", "今日もいい天気です"),
        "ニホンゴヲニュウリョクスル" to arrayOf("日本語を入力する", "日本語を入力する。", "日本語入力をする"),
    )
}
