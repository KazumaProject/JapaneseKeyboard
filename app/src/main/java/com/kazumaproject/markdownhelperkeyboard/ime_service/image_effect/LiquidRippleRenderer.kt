package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.view.Surface
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class LiquidRippleRenderer(
    private val inputQueue: LiquidRippleInputCommandQueue,
    private val callback: LiquidRippleRendererCallback,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val simulationFactory: () -> LiquidRippleSimulation = { LiquidRippleSimulation() },
    private val clockNanos: () -> Long = { System.nanoTime() }
) : LiquidRippleRendererController {

    private val rendererThread = HandlerThread(
        "$THREAD_NAME_PREFIX-${threadIds.incrementAndGet()}",
        Process.THREAD_PRIORITY_DISPLAY
    )
    private val handler: Handler
    private val frameRunnable = Runnable { renderFrameOnRendererThread() }

    private var settings = LiquidRippleSettings.Disabled
    private var egl: EglEnvironment? = null
    private var simulation: LiquidRippleSimulation? = null
    private val performanceGovernor = LiquidRipplePerformanceGovernor()
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var paused = true
    private var released = false
    private var frameScheduled = false
    private var activePointers = HashSet<Int>()
    private var lastFrameTimeNanos = 0L
    private var state = LiquidRippleRendererState.Idle

    init {
        rendererThread.start()
        handler = Handler(rendererThread.looper)
    }

    override fun configure(settings: LiquidRippleSettings) {
        postOnRenderer {
            this.settings = settings
            if (!settings.enabled) {
                clearOnRendererThread()
                paused = true
            }
        }
    }

    override fun attachSurface(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        postOnRenderer {
            if (released || !settings.enabled) return@postOnRenderer
            runRendererCatching("attach EGL surface") {
                surfaceWidth = width
                surfaceHeight = height
                if (egl == null) {
                    egl = EglEnvironment(surfaceTexture)
                    simulation = simulationFactory()
                    simulation?.initialize(
                        surfaceWidth = width,
                        surfaceHeight = height,
                        qualityLevel = performanceGovernor.qualityLevel()
                    )
                } else {
                    simulation?.resizeSurface(
                        surfaceWidth = width,
                        surfaceHeight = height,
                        qualityLevel = performanceGovernor.qualityLevel()
                    )
                }
                paused = false
                requestRenderOnRendererThread(forceSoon = true)
            }
        }
    }

    override fun resizeSurface(width: Int, height: Int) {
        postOnRenderer {
            if (released || egl == null || !settings.enabled) return@postOnRenderer
            runRendererCatching("resize liquid ripple surface") {
                surfaceWidth = width
                surfaceHeight = height
                simulation?.resizeSurface(
                    surfaceWidth = width,
                    surfaceHeight = height,
                    qualityLevel = performanceGovernor.qualityLevel()
                )
                requestRenderOnRendererThread(forceSoon = true)
            }
        }
    }

    override fun detachSurface() {
        runBlockingOnRendererThread(maxWaitMillis = 120L) {
            handler.removeCallbacks(frameRunnable)
            frameScheduled = false
            paused = true
            simulation?.release()
            simulation = null
            releaseEglSurfaceOnly()
        }
    }

    override fun resume() {
        postOnRenderer {
            if (released || !settings.enabled) return@postOnRenderer
            paused = false
            requestRenderOnRendererThread(forceSoon = true)
        }
    }

    override fun requestRender() {
        postOnRenderer {
            requestRenderOnRendererThread(forceSoon = true)
        }
    }

    override fun clear() {
        postOnRenderer {
            clearOnRendererThread()
        }
    }

    override fun pause() {
        postOnRenderer {
            paused = true
            handler.removeCallbacks(frameRunnable)
            frameScheduled = false
            activePointers.clear()
            inputQueue.clear()
        }
    }

    override fun release() {
        runBlockingOnRendererThread(maxWaitMillis = 250L) {
            if (released) return@runBlockingOnRendererThread
            released = true
            handler.removeCallbacks(frameRunnable)
            frameScheduled = false
            inputQueue.clear()
            activePointers.clear()
            simulation?.release()
            simulation = null
            releaseEglSurfaceOnly()
        }
        rendererThread.quitSafely()
    }

    override fun isRendererThreadAliveForTesting(): Boolean {
        return rendererThread.isAlive && !released
    }

    private fun renderFrameOnRendererThread() {
        frameScheduled = false
        val activeSimulation = simulation
        if (released || paused || !settings.enabled || egl == null || activeSimulation == null) {
            return
        }

        runRendererCatching("render liquid ripple frame") {
            val frameStart = clockNanos()
            val dtSeconds = if (lastFrameTimeNanos == 0L) {
                1f / 60f
            } else {
                ((frameStart - lastFrameTimeNanos) / NANOS_PER_SECOND_FLOAT)
                    .coerceIn(1f / 120f, 1f / 20f)
            }
            lastFrameTimeNanos = frameStart

            val commands = inputQueue.drain(MAX_INPUT_COMMANDS_PER_FRAME)
            updatePointerState(commands)
            state = resolveRendererState(activeSimulation.hasVisibleEnergy())
            val hasEnergy = activeSimulation.render(
                inputCommands = commands,
                dtSeconds = dtSeconds,
                params = performanceGovernor.stepParams(state)
            )
            egl?.swapBuffers()

            val frameMillis = (clockNanos() - frameStart) / 1_000_000L
            val qualityChanged = performanceGovernor.reportFrameTime(frameMillis, state)
            if (qualityChanged) {
                activeSimulation.resizeSurface(
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                    qualityLevel = performanceGovernor.qualityLevel()
                )
                Timber.d(
                    "Reduced liquid ripple quality to %d",
                    performanceGovernor.qualityLevel()
                )
            }
            if (activePointers.isNotEmpty() || hasEnergy || inputQueue.sizeForTesting() > 0) {
                requestRenderOnRendererThread(forceSoon = false)
            }
        }
    }

    private fun updatePointerState(commands: List<LiquidRippleInputCommand>) {
        commands.forEach { command ->
            when (command) {
                is LiquidRippleInputCommand.Impulse -> {
                    if (command.kind == LiquidRippleImpulseKind.Down) {
                        activePointers.add(command.pointerId)
                    }
                }

                is LiquidRippleInputCommand.PointerUp -> activePointers.remove(command.pointerId)
                is LiquidRippleInputCommand.PointerCancel -> activePointers.remove(command.pointerId)
                is LiquidRippleInputCommand.CancelAll -> activePointers.clear()
            }
        }
    }

    private fun resolveRendererState(hasEnergy: Boolean): LiquidRippleRendererState {
        return when {
            activePointers.isNotEmpty() -> LiquidRippleRendererState.Active
            hasEnergy -> LiquidRippleRendererState.Settling
            else -> LiquidRippleRendererState.Idle
        }
    }

    private fun requestRenderOnRendererThread(forceSoon: Boolean) {
        if (released || paused || egl == null || !settings.enabled) return
        if (frameScheduled) {
            if (!forceSoon) return
            handler.removeCallbacks(frameRunnable)
        }
        val delayMillis = if (forceSoon) 0L else performanceGovernor.frameIntervalMillis(state)
        frameScheduled = true
        handler.postDelayed(frameRunnable, delayMillis)
    }

    private fun clearOnRendererThread() {
        inputQueue.clear()
        activePointers.clear()
        lastFrameTimeNanos = 0L
        simulation?.clear()
    }

    private fun postOnRenderer(action: () -> Unit) {
        if (released && Looper.myLooper() != handler.looper) return
        if (Looper.myLooper() == handler.looper) {
            action()
        } else {
            handler.post(action)
        }
    }

    private fun runBlockingOnRendererThread(maxWaitMillis: Long, action: () -> Unit) {
        if (Looper.myLooper() == handler.looper) {
            action()
            return
        }
        if (!rendererThread.isAlive) return
        val latch = CountDownLatch(1)
        handler.post {
            runCatching(action).onFailure {
                Timber.w(it, "Failed to run liquid ripple renderer cleanup.")
            }
            latch.countDown()
        }
        latch.await(maxWaitMillis, TimeUnit.MILLISECONDS)
    }

    private fun runRendererCatching(operation: String, action: () -> Unit) {
        runCatching(action).onFailure { throwable ->
            Timber.w(throwable, "Liquid ripple renderer failed during %s", operation)
            releaseAfterFailureOnRendererThread()
            mainHandler.post {
                callback.onRendererDisabled(operation, throwable)
            }
        }
    }

    private fun releaseAfterFailureOnRendererThread() {
        handler.removeCallbacks(frameRunnable)
        frameScheduled = false
        paused = true
        inputQueue.clear()
        activePointers.clear()
        runCatching {
            simulation?.release()
        }
        simulation = null
        releaseEglSurfaceOnly()
    }

    private fun releaseEglSurfaceOnly() {
        egl?.release()
        egl = null
    }

    private class EglEnvironment(surfaceTexture: SurfaceTexture) {
        private val surface = Surface(surfaceTexture)
        private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var context: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        init {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }

            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) {
                "eglInitialize failed: 0x${EGL14.eglGetError().toString(16)}"
            }

            val config = chooseConfig(display)
            context = EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE),
                0
            )
            check(context != EGL14.EGL_NO_CONTEXT) {
                "eglCreateContext failed: 0x${EGL14.eglGetError().toString(16)}"
            }

            eglSurface = EGL14.eglCreateWindowSurface(
                display,
                config,
                surface,
                intArrayOf(EGL14.EGL_NONE),
                0
            )
            check(eglSurface != EGL14.EGL_NO_SURFACE) {
                "eglCreateWindowSurface failed: 0x${EGL14.eglGetError().toString(16)}"
            }

            makeCurrent()
        }

        fun swapBuffers() {
            check(EGL14.eglSwapBuffers(display, eglSurface)) {
                "eglSwapBuffers failed: 0x${EGL14.eglGetError().toString(16)}"
            }
        }

        fun release() {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    display,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
                )
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(display, eglSurface)
                }
                if (context != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(display, context)
                }
                EGL14.eglTerminate(display)
            }
            surface.release()
            display = EGL14.EGL_NO_DISPLAY
            context = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }

        private fun makeCurrent() {
            check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
                "eglMakeCurrent failed: 0x${EGL14.eglGetError().toString(16)}"
            }
        }

        private fun chooseConfig(display: EGLDisplay): EGLConfig {
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            val attributes = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE,
                EGL_OPENGL_ES3_BIT,
                EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE,
                8,
                EGL14.EGL_GREEN_SIZE,
                8,
                EGL14.EGL_BLUE_SIZE,
                8,
                EGL14.EGL_ALPHA_SIZE,
                8,
                EGL14.EGL_DEPTH_SIZE,
                0,
                EGL14.EGL_STENCIL_SIZE,
                0,
                EGL14.EGL_NONE
            )
            check(
                EGL14.eglChooseConfig(
                    display,
                    attributes,
                    0,
                    configs,
                    0,
                    configs.size,
                    numConfigs,
                    0
                ) && numConfigs[0] > 0
            ) {
                "eglChooseConfig failed: 0x${EGL14.eglGetError().toString(16)}"
            }
            return configs[0] ?: error("eglChooseConfig returned null")
        }
    }

    companion object {
        const val THREAD_NAME_PREFIX = "LiquidRippleRenderer"
        private const val EGL_OPENGL_ES3_BIT = 0x00000040
        private const val MAX_INPUT_COMMANDS_PER_FRAME = 64
        private const val NANOS_PER_SECOND_FLOAT = 1_000_000_000f
        private val threadIds = AtomicInteger(0)
    }
}
