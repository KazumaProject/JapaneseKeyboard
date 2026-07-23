package com.kazumaproject.markdownhelperkeyboard.gemma

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.IGemmaRuntime
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.IGemmaRuntimeCallback
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-only smoke test for the real LiteRT-LM runtime. It is skipped unless a
 * .litertlm model is present in the app's external files/gemma directory.
 */
@RunWith(AndroidJUnit4::class)
class GemmaRuntimeInstrumentedTest {
    private lateinit var context: Context
    private var runtime: IGemmaRuntime? = null
    private var connection: ServiceConnection? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @After
    fun tearDown() {
        runCatching { runtime?.closeEngine() }
        connection?.let { runCatching { context.unbindService(it) } }
        runtime = null
        connection = null
    }

    @Test(timeout = 360_000)
    fun separateProcessHandlesTextImageAndAudio() {
        val model = context.getExternalFilesDir(null)
            ?.resolve("gemma")
            ?.listFiles()
            ?.firstOrNull { it.isFile && it.extension.equals("litertlm", ignoreCase = true) }
        assumeTrue("Install a .litertlm model before running the runtime smoke test", model != null)

        val service = bindRuntime()
        initialize(service, requireNotNull(model))

        val text = generate(
            service = service,
            requestId = 101,
            prompt = "Reply with only the word OK.",
            mediaFile = null,
            mediaType = MEDIA_TEXT,
        )
        Log.i(TAG, "Text response: $text")
        assertTrue("Text response did not contain OK: $text", text.contains("OK", ignoreCase = true))

        val image = createTestImage()
        val imageResult = generate(
            service = service,
            requestId = 102,
            prompt = "Read the large text in this image. Return only that text.",
            mediaFile = image,
            mediaType = MEDIA_IMAGE,
        )
        Log.i(TAG, "Image response: $imageResult")
        assertTrue(
            "Image response did not recognize HELLO 42: $imageResult",
            imageResult.contains("HELLO", ignoreCase = true) && imageResult.contains("42"),
        )

        val audio = createTestAudio()
        val audioResult = generate(
            service = service,
            requestId = 103,
            prompt = "Analyze this audio, then return only the word AUDIO.",
            mediaFile = audio,
            mediaType = MEDIA_AUDIO,
        )
        Log.i(TAG, "Audio response: $audioResult")
        assertTrue(
            "Audio response did not contain AUDIO: $audioResult",
            audioResult.contains("AUDIO", ignoreCase = true),
        )
    }

    private fun bindRuntime(): IGemmaRuntime {
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                runtime = IGemmaRuntime.Stub.asInterface(binder)
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                runtime = null
            }
        }
        this.connection = connection
        val intent = Intent().setClassName(
            context.packageName,
            "${context.packageName}.gemma.runtime.GemmaRuntimeService",
        )
        assertTrue("Gemma runtime service could not be bound", context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        assertTrue("Timed out binding Gemma runtime service", connected.await(10, TimeUnit.SECONDS))
        return requireNotNull(runtime)
    }

    private fun initialize(service: IGemmaRuntime, model: File) {
        val completed = CountDownLatch(1)
        val processPid = AtomicInteger(-1)
        val error = AtomicReference<String?>(null)
        service.initialize(
            model.absolutePath,
            "gpu_if_available",
            MODALITY_IMAGE or MODALITY_AUDIO,
            object : IGemmaRuntimeCallback.Stub() {
                override fun onStateChanged(state: String, detail: String) {
                    when (state) {
                        "PROCESS" -> processPid.set(detail.toIntOrNull() ?: -1)
                        "READY" -> completed.countDown()
                    }
                }

                override fun onResult(requestId: Long, result: String) = Unit

                override fun onError(requestId: Long, message: String) {
                    error.set(message)
                    completed.countDown()
                }
            },
        )
        assertTrue("Gemma initialization timed out", completed.await(180, TimeUnit.SECONDS))
        check(error.get() == null) { "Gemma initialization failed: ${error.get()}" }
        assertTrue("Runtime process PID was not reported", processPid.get() > 0)
        assertNotEquals("Gemma must run outside the instrumentation process", Process.myPid(), processPid.get())
    }

    private fun generate(
        service: IGemmaRuntime,
        requestId: Long,
        prompt: String,
        mediaFile: File?,
        mediaType: Int,
    ): String {
        val completed = CountDownLatch(1)
        val result = AtomicReference<String?>(null)
        val error = AtomicReference<String?>(null)
        service.generate(
            requestId,
            prompt,
            mediaFile?.absolutePath.orEmpty(),
            mediaType,
            object : IGemmaRuntimeCallback.Stub() {
                override fun onStateChanged(state: String, detail: String) = Unit

                override fun onResult(returnedRequestId: Long, value: String) {
                    if (returnedRequestId == requestId) {
                        result.set(value)
                        completed.countDown()
                    }
                }

                override fun onError(returnedRequestId: Long, message: String) {
                    if (returnedRequestId == requestId) {
                        error.set(message)
                        completed.countDown()
                    }
                }
            },
        )
        assertTrue("Gemma request $requestId timed out", completed.await(120, TimeUnit.SECONDS))
        check(error.get() == null) { "Gemma request $requestId failed: ${error.get()}" }
        return result.get().orEmpty()
    }

    private fun createTestImage(): File {
        val target = File(context.cacheDir, "gemma_runtime_test.png")
        val bitmap = Bitmap.createBitmap(640, 320, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 96f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("HELLO 42", bitmap.width / 2f, bitmap.height / 2f + 32f, paint)
        FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return target
    }

    private fun createTestAudio(): File {
        val sampleRate = 16_000
        val sampleCount = sampleRate
        val pcm = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(sampleCount) { sample ->
            val value = (sin(2.0 * PI * 440.0 * sample / sampleRate) * Short.MAX_VALUE * 0.15).toInt()
            pcm.putShort(value.toShort())
        }
        val target = File(context.cacheDir, "gemma_runtime_test.wav")
        FileOutputStream(target).use { output ->
            output.write(wavHeader(pcm.array().size, sampleRate))
            output.write(pcm.array())
        }
        return target
    }

    private fun wavHeader(dataSize: Int, sampleRate: Int): ByteArray =
        ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()

    private companion object {
        const val TAG = "GemmaRuntimeTest"
        const val MODALITY_IMAGE = 1
        const val MODALITY_AUDIO = 1 shl 1
        const val MEDIA_TEXT = 0
        const val MEDIA_IMAGE = 1
        const val MEDIA_AUDIO = 2
    }
}
