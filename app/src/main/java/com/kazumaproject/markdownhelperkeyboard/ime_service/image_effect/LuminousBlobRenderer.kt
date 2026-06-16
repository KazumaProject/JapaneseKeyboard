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

internal class LuminousBlobRenderer(
    private val inputQueue: LuminousBlobInputCommandQueue,
    private val callback: LuminousBlobRendererCallback,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val simulationFactory: () -> LuminousBlobSimulation = { LuminousBlobSimulation() },
    private val clockNanos: () -> Long = { System.nanoTime() }
) : LuminousBlobRendererController {

    private val rendererThread = HandlerThread(
        "$THREAD_NAME_PREFIX-${threadIds.incrementAndGet()}",
        Process.THREAD_PRIORITY_DISPLAY
    )
    private val handler: Handler
    private val frameRunnable = Runnable { renderFrameOnRendererThread() }

    private var settings = LuminousBlobSettings.Disabled
    private var egl: EglEnvironment? = null
    private var simulation: LuminousBlobSimulation? = null
    private val performanceGovernor = LuminousBlobPerformanceGovernor()
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var paused = true
    private var released = false
    private var frameScheduled = false
    private val activePointers = HashMap<Int, LuminousBlobActivePointer>()
    private var pendingReleasedPointer: LuminousBlobPointerSnapshot? = null
    private var lastFrameTimeNanos = 0L
    private var state = LuminousBlobRendererState.Idle

    init {
        rendererThread.start()
        handler = Handler(rendererThread.looper)
    }

    override fun configure(settings: LuminousBlobSettings) {
        postOnRenderer {
            val qualityChanged = this.settings.normalizedQuality != settings.normalizedQuality
            this.settings = settings
            performanceGovernor.configureQuality(settings.normalizedQuality)
            simulation?.configure(settings)
            if (!settings.enabled) {
                clearOnRendererThread()
                paused = true
                state = LuminousBlobRendererState.Idle
                return@postOnRenderer
            }
            if (qualityChanged && surfaceWidth > 0 && surfaceHeight > 0 && simulation != null) {
                simulation?.resizeSurface(
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                    params = performanceGovernor.stepParams(resolveRendererState())
                )
            }
        }
    }

    override fun attachSurface(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        postOnRenderer {
            if (released || !settings.enabled) return@postOnRenderer
            runRendererCatching("attach EGL surface") {
                surfaceWidth = width
                surfaceHeight = height
                state = LuminousBlobRendererState.Ambient
                if (egl == null) {
                    egl = EglEnvironment(surfaceTexture)
                    simulation = simulationFactory()
                    simulation?.initialize(
                        surfaceWidth = width,
                        surfaceHeight = height,
                        params = performanceGovernor.stepParams(state),
                        settings = settings
                    )
                } else {
                    simulation?.resizeSurface(
                        surfaceWidth = width,
                        surfaceHeight = height,
                        params = performanceGovernor.stepParams(state)
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
            runRendererCatching("resize luminous blob surface") {
                surfaceWidth = width
                surfaceHeight = height
                simulation?.resizeSurface(
                    surfaceWidth = width,
                    surfaceHeight = height,
                    params = performanceGovernor.stepParams(resolveRendererState())
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
            state = LuminousBlobRendererState.Idle
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
            pendingReleasedPointer = null
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
            pendingReleasedPointer = null
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

        runRendererCatching("render luminous blob frame") {
            val frameStart = clockNanos()
            val dtSeconds = if (lastFrameTimeNanos == 0L) {
                1f / 60f
            } else {
                ((frameStart - lastFrameTimeNanos) / NANOS_PER_SECOND_FLOAT)
                    .coerceIn(1f / 120f, 1f / 18f)
            }
            lastFrameTimeNanos = frameStart

            val commands = inputQueue.drain(performanceGovernor.maxCommandsPerFrame())
            val cancelTouch = updatePointerState(commands)
            if (cancelTouch) {
                activeSimulation.cancelTouch()
            }
            state = resolveRendererState()
            val params = performanceGovernor.stepParams(state)
            activeSimulation.render(
                pointer = resolvePointerSnapshotForFrame(),
                dtSeconds = dtSeconds,
                params = params
            )
            egl?.swapBuffers()

            val frameMillis = (clockNanos() - frameStart) / 1_000_000L
            val qualityChanged = performanceGovernor.reportFrameTime(frameMillis, state)
            if (qualityChanged) {
                activeSimulation.resizeSurface(
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                    params = performanceGovernor.stepParams(state)
                )
                Timber.d(
                    "Reduced luminous blob quality to %d",
                    performanceGovernor.qualityLevel()
                )
            }
            if (settings.enabled && egl != null && !paused) {
                requestRenderOnRendererThread(forceSoon = false)
            }
        }
    }

    private fun updatePointerState(commands: List<LuminousBlobInputCommand>): Boolean {
        var cancelTouch = false
        commands.forEach { command ->
            when (command) {
                is LuminousBlobInputCommand.Pointer -> {
                    val nextPointer = LuminousBlobActivePointer(
                        pointerId = command.pointerId,
                        x = command.x,
                        y = command.y,
                        velocityX = command.velocityX,
                        velocityY = command.velocityY,
                        colorSet = command.colorSet,
                        downTimeMillis = command.eventTimeMillis,
                        lastEventTimeMillis = command.eventTimeMillis
                    )
                    activePointers[command.pointerId] = when (command.kind) {
                        LuminousBlobInputKind.Down -> nextPointer
                        LuminousBlobInputKind.Move,
                        LuminousBlobInputKind.Up -> {
                            val current = activePointers[command.pointerId]
                            nextPointer.copy(
                                downTimeMillis = current?.downTimeMillis
                                    ?: command.eventTimeMillis
                            )
                        }
                    }
                    if (command.kind == LuminousBlobInputKind.Up) {
                        pendingReleasedPointer = activePointers[command.pointerId]?.toSnapshot()
                    }
                }

                is LuminousBlobInputCommand.PointerUp -> {
                    val removed = activePointers.remove(command.pointerId)
                    if (activePointers.isEmpty() && pendingReleasedPointer == null) {
                        pendingReleasedPointer = removed?.toSnapshot()
                    }
                }

                is LuminousBlobInputCommand.PointerCancel -> {
                    activePointers.remove(command.pointerId)
                    if (activePointers.isEmpty()) {
                        pendingReleasedPointer = null
                        cancelTouch = true
                    }
                }

                is LuminousBlobInputCommand.CancelAll -> {
                    activePointers.clear()
                    pendingReleasedPointer = null
                    cancelTouch = true
                }
            }
        }
        return cancelTouch
    }

    private fun resolveRendererState(): LuminousBlobRendererState {
        val residual = simulation?.hasResidualTouch(
            performanceGovernor.stepParams(state).idleStrengthThreshold
        ) ?: false
        return when {
            !settings.enabled || egl == null -> LuminousBlobRendererState.Idle
            activePointers.isNotEmpty() -> LuminousBlobRendererState.Active
            residual -> LuminousBlobRendererState.Settling
            else -> LuminousBlobRendererState.Ambient
        }
    }

    private fun resolvePointerSnapshotForFrame(): LuminousBlobPointerSnapshot? {
        val activeSnapshot = resolveActivePointerSnapshot()
        if (activeSnapshot != null) return activeSnapshot
        return pendingReleasedPointer.also {
            pendingReleasedPointer = null
        }
    }

    private fun resolveActivePointerSnapshot(): LuminousBlobPointerSnapshot? {
        if (activePointers.isEmpty()) return null
        var x = 0f
        var y = 0f
        var velocityX = 0f
        var velocityY = 0f
        var newest: LuminousBlobActivePointer? = null
        activePointers.values.forEach { pointer ->
            x += pointer.x
            y += pointer.y
            velocityX += pointer.velocityX
            velocityY += pointer.velocityY
            val currentNewest = newest
            if (currentNewest == null || pointer.lastEventTimeMillis >= currentNewest.lastEventTimeMillis) {
                newest = pointer
            }
        }
        val count = activePointers.size.toFloat()
        val colorSet = newest?.colorSet ?: LuminousBlobColorSet.Default
        return LuminousBlobPointerSnapshot(
            x = x / count,
            y = y / count,
            velocityX = velocityX / count,
            velocityY = velocityY / count,
            colorSet = colorSet
        )
    }

    private fun LuminousBlobActivePointer.toSnapshot(): LuminousBlobPointerSnapshot {
        return LuminousBlobPointerSnapshot(
            x = x,
            y = y,
            velocityX = velocityX,
            velocityY = velocityY,
            colorSet = colorSet
        )
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
        pendingReleasedPointer = null
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
                Timber.w(it, "Failed to run luminous blob renderer cleanup.")
            }
            latch.countDown()
        }
        latch.await(maxWaitMillis, TimeUnit.MILLISECONDS)
    }

    private fun runRendererCatching(operation: String, action: () -> Unit) {
        runCatching(action).onFailure { throwable ->
            Timber.w(throwable, "Luminous blob renderer failed during %s", operation)
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
        state = LuminousBlobRendererState.Idle
        inputQueue.clear()
        activePointers.clear()
        pendingReleasedPointer = null
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
        const val THREAD_NAME_PREFIX = "LuminousBlobRenderer"
        private const val EGL_OPENGL_ES3_BIT = 0x00000040
        private const val NANOS_PER_SECOND_FLOAT = 1_000_000_000f
        private val threadIds = AtomicInteger(0)
    }
}
