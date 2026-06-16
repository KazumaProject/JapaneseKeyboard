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
import android.os.SystemClock
import android.view.Surface
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.exp

internal class CinematicWaveRenderer(
    private val inputQueue: CinematicWaveInputCommandQueue,
    private val callback: CinematicWaveRendererCallback,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val simulationFactory: () -> CinematicWaveSimulation = { CinematicWaveSimulation() },
    private val clockNanos: () -> Long = { System.nanoTime() },
    private val clockMillis: () -> Long = { SystemClock.uptimeMillis() }
) : CinematicWaveRendererController {

    private val rendererThread = HandlerThread(
        "$THREAD_NAME_PREFIX-${threadIds.incrementAndGet()}",
        Process.THREAD_PRIORITY_DISPLAY
    )
    private val handler: Handler
    private val frameRunnable = Runnable { renderFrameOnRendererThread() }

    private val activePointers = HashMap<Int, CinematicWaveTouchPoint>()
    private val releasedPointers = ArrayList<CinematicWaveTouchPoint>()
    private val performanceGovernor = CinematicWavePerformanceGovernor()
    private val colorController = CinematicWaveColorController()

    private var settings = CinematicWaveSettings.Disabled
    private var egl: EglEnvironment? = null
    private var simulation: CinematicWaveSimulation? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var viewWidth = 0
    private var viewHeight = 0
    private var renderWidth = 0
    private var renderHeight = 0
    private var paused = true
    private var released = false
    private var frameScheduled = false
    private var lastFrameTimeNanos = 0L
    private var state = CinematicWaveRendererState.Idle

    init {
        rendererThread.start()
        handler = Handler(rendererThread.looper)
    }

    override fun configure(settings: CinematicWaveSettings) {
        postOnRenderer {
            val previousQuality = this.settings.normalizedQuality
            this.settings = settings
            performanceGovernor.configureQuality(settings.normalizedQuality)
            colorController.configure(settings, clockMillis())
            simulation?.configure(settings)
            if (!settings.enabled) {
                clearOnRendererThread()
                paused = true
                state = CinematicWaveRendererState.Idle
                return@postOnRenderer
            }
            if (
                previousQuality != settings.normalizedQuality &&
                surfaceTexture != null &&
                viewWidth > 0 &&
                viewHeight > 0
            ) {
                resizeSurfaceForCurrentSettings()
            }
            requestRenderOnRendererThread(forceSoon = true)
        }
    }

    override fun attachSurface(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        postOnRenderer {
            if (released || !settings.enabled || width <= 0 || height <= 0) return@postOnRenderer
            this.surfaceTexture = surfaceTexture
            viewWidth = width
            viewHeight = height
            state = CinematicWaveRendererState.Ambient
            runRendererCatching("attach cinematic wave EGL surface") {
                ensureSurfaceForCurrentSize()
                paused = false
                requestRenderOnRendererThread(forceSoon = true)
            }
        }
    }

    override fun resizeSurface(width: Int, height: Int) {
        postOnRenderer {
            if (width <= 0 || height <= 0) return@postOnRenderer
            if (width == viewWidth && height == viewHeight) return@postOnRenderer
            if (released || surfaceTexture == null || !settings.enabled) return@postOnRenderer
            viewWidth = width
            viewHeight = height
            runRendererCatching("resize cinematic wave surface") {
                ensureSurfaceForCurrentSize()
                requestRenderOnRendererThread(forceSoon = true)
            }
        }
    }

    override fun detachSurface() {
        runBlockingOnRendererThread(maxWaitMillis = 120L) {
            handler.removeCallbacks(frameRunnable)
            frameScheduled = false
            paused = true
            state = CinematicWaveRendererState.Idle
            clearOnRendererThread()
            simulation?.release()
            simulation = null
            releaseEglSurfaceOnly()
            surfaceTexture = null
            viewWidth = 0
            viewHeight = 0
            renderWidth = 0
            renderHeight = 0
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
            releasedPointers.clear()
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
            releasedPointers.clear()
            simulation?.release()
            simulation = null
            releaseEglSurfaceOnly()
            surfaceTexture = null
        }
        rendererThread.quitSafely()
    }

    override fun isRendererThreadAliveForTesting(): Boolean {
        return rendererThread.isAlive && !released
    }

    private fun ensureSurfaceForCurrentSize() {
        val texture = surfaceTexture ?: return
        val nextRenderWidth = viewWidth.coerceAtLeast(1)
        val nextRenderHeight = viewHeight.coerceAtLeast(1)
        if (
            egl != null &&
            simulation != null &&
            renderWidth == nextRenderWidth &&
            renderHeight == nextRenderHeight
        ) {
            return
        }

        renderWidth = nextRenderWidth
        renderHeight = nextRenderHeight
        if (egl == null || simulation == null) {
            if (egl == null) {
                egl = EglEnvironment(texture)
            }
            simulation = simulationFactory().also {
                it.initialize(
                    surfaceWidth = renderWidth,
                    surfaceHeight = renderHeight,
                    settings = settings
                )
            }
        } else {
            simulation?.resizeSurface(renderWidth, renderHeight)
        }
    }

    private fun resizeSurfaceForCurrentSettings() {
        ensureSurfaceForCurrentSize()
    }

    private fun renderFrameOnRendererThread() {
        frameScheduled = false
        val activeSimulation = simulation
        if (released || paused || !settings.enabled || egl == null || activeSimulation == null) {
            return
        }

        runRendererCatching("render cinematic wave frame") {
            val frameStartNanos = clockNanos()
            val frameStartMillis = clockMillis()
            val dtSeconds = if (lastFrameTimeNanos == 0L) {
                1f / 60f
            } else {
                ((frameStartNanos - lastFrameTimeNanos) / NANOS_PER_SECOND_FLOAT)
                    .coerceIn(1f / 120f, 1f / 18f)
            }
            lastFrameTimeNanos = frameStartNanos

            val commands = inputQueue.drain(
                performanceGovernor.stepParams(settings, state).maxCommandsPerFrame
            )
            updatePointerState(commands)
            state = resolveRendererState()
            val params = performanceGovernor.stepParams(settings, state)
            val palette = colorController.paletteAt(frameStartMillis)
            activeSimulation.render(
                dtSeconds = dtSeconds,
                params = params,
                palette = palette,
                touches = resolveTouchSnapshots(frameStartMillis)
            )
            egl?.swapBuffers()

            val frameMillis = (clockNanos() - frameStartNanos) / 1_000_000L
            val qualityChanged = performanceGovernor.reportFrameTime(frameMillis, state)
            if (qualityChanged) {
                Timber.d(
                    "Reduced cinematic wave quality to %d",
                    performanceGovernor.qualityLevel()
                )
            }
            if (settings.enabled && egl != null && !paused) {
                requestRenderOnRendererThread(forceSoon = false)
            }
        }
    }

    private fun updatePointerState(commands: List<CinematicWaveInputCommand>) {
        commands.forEach { command ->
            when (command) {
                is CinematicWaveInputCommand.Pointer -> {
                    val current = activePointers[command.pointerId]
                    val startTime = current?.startTimeMs ?: command.eventTimeMs
                    val nextPointer = CinematicWaveTouchPoint(
                        pointerId = command.pointerId,
                        x = command.x,
                        y = command.y,
                        startTimeMs = startTime,
                        lastUpdateTimeMs = command.eventTimeMs,
                        pressure = command.pressure,
                        strength = command.pressure
                    )
                    activePointers[command.pointerId] = nextPointer
                    if (command.kind == CinematicWaveInputKind.Up) {
                        releasePointer(command.pointerId, command.eventTimeMs)
                    }
                }

                is CinematicWaveInputCommand.PointerUp -> {
                    releasePointer(command.pointerId, command.eventTimeMs)
                }

                is CinematicWaveInputCommand.PointerCancel -> {
                    activePointers.remove(command.pointerId)
                }

                is CinematicWaveInputCommand.CancelAll -> {
                    activePointers.clear()
                    releasedPointers.clear()
                }
            }
        }
    }

    private fun releasePointer(pointerId: Int, eventTimeMs: Long) {
        val pointer = activePointers.remove(pointerId) ?: return
        releasedPointers.add(
            pointer.copy(
                lastUpdateTimeMs = eventTimeMs,
                strength = pointer.strength.coerceAtLeast(0.55f)
            )
        )
        while (releasedPointers.size > MAX_RELEASED_TOUCHES) {
            releasedPointers.removeAt(0)
        }
    }

    private fun resolveTouchSnapshots(nowMs: Long): List<CinematicWaveTouchSnapshot> {
        val snapshots = ArrayList<CinematicWaveTouchSnapshot>(MAX_TOUCHES)
        activePointers.values
            .sortedByDescending { it.lastUpdateTimeMs }
            .take(MAX_TOUCHES)
            .forEach { pointer ->
                snapshots.add(pointer.toSnapshot(nowMs, pointer.strength.coerceIn(0.35f, 1.5f)))
            }

        val iterator = releasedPointers.iterator()
        while (iterator.hasNext()) {
            val pointer = iterator.next()
            val elapsedMs = (nowMs - pointer.lastUpdateTimeMs).coerceAtLeast(0L)
            val decay = exp(-elapsedMs / RELEASE_DECAY_MS)
            val strength = pointer.strength * decay
            if (strength < RELEASE_MIN_STRENGTH || elapsedMs > RELEASE_MAX_AGE_MS) {
                iterator.remove()
                continue
            }
            if (snapshots.size < MAX_TOUCHES) {
                snapshots.add(pointer.toSnapshot(nowMs, strength))
            }
        }
        return snapshots
    }

    private fun CinematicWaveTouchPoint.toSnapshot(
        nowMs: Long,
        resolvedStrength: Float
    ): CinematicWaveTouchSnapshot {
        val safeWidth = viewWidth.coerceAtLeast(1).toFloat()
        val safeHeight = viewHeight.coerceAtLeast(1).toFloat()
        return CinematicWaveTouchSnapshot(
            x = (x / safeWidth).coerceIn(0f, 1f),
            y = (1f - y / safeHeight).coerceIn(0f, 1f),
            ageSeconds = ((nowMs - startTimeMs).coerceAtLeast(0L) / 1000f),
            strength = resolvedStrength.coerceIn(0f, 1.6f)
        )
    }

    private fun resolveRendererState(): CinematicWaveRendererState {
        return when {
            !settings.enabled || egl == null -> CinematicWaveRendererState.Idle
            activePointers.isNotEmpty() -> CinematicWaveRendererState.Active
            releasedPointers.isNotEmpty() -> CinematicWaveRendererState.Settling
            else -> CinematicWaveRendererState.Ambient
        }
    }

    private fun requestRenderOnRendererThread(forceSoon: Boolean) {
        if (released || paused || egl == null || !settings.enabled) return
        if (frameScheduled) {
            if (!forceSoon) return
            handler.removeCallbacks(frameRunnable)
        }
        val delayMillis = if (forceSoon) {
            0L
        } else {
            performanceGovernor.stepParams(settings, state).frameIntervalMs
        }
        frameScheduled = true
        handler.postDelayed(frameRunnable, delayMillis)
    }

    private fun clearOnRendererThread() {
        inputQueue.clear()
        activePointers.clear()
        releasedPointers.clear()
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
                Timber.w(it, "Failed to run cinematic wave renderer cleanup.")
            }
            latch.countDown()
        }
        latch.await(maxWaitMillis, TimeUnit.MILLISECONDS)
    }

    private fun runRendererCatching(operation: String, action: () -> Unit) {
        runCatching(action).onFailure { throwable ->
            Timber.w(throwable, "Cinematic wave renderer failed during %s", operation)
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
        state = CinematicWaveRendererState.Idle
        inputQueue.clear()
        activePointers.clear()
        releasedPointers.clear()
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
        const val THREAD_NAME_PREFIX = "CinematicWaveRenderer"
        private const val EGL_OPENGL_ES3_BIT = 0x00000040
        private const val NANOS_PER_SECOND_FLOAT = 1_000_000_000f
        private const val MAX_TOUCHES = 5
        private const val MAX_RELEASED_TOUCHES = 5
        private const val RELEASE_DECAY_MS = 720f
        private const val RELEASE_MAX_AGE_MS = 1_800L
        private const val RELEASE_MIN_STRENGTH = 0.018f
        private val threadIds = AtomicInteger(0)
    }
}
