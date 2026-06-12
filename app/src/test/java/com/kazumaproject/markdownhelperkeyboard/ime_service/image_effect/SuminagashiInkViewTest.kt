package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.content.Context
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SuminagashiInkViewTest {

    private lateinit var view: SuminagashiInkView
    private lateinit var rendererFactory: RecordingRendererFactory

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        rendererFactory = RecordingRendererFactory()
        view = SuminagashiInkView(context).apply {
            rendererFactory = this@SuminagashiInkViewTest.rendererFactory
        }
    }

    @Test
    fun settingOffDoesNotCreateRenderer() {
        view.configure(
            enabled = false,
            colorMode = "random",
            fixedColor = Color.rgb(17, 17, 17)
        )

        assertEquals(View.GONE, view.visibility)
        assertFalse(view.hasRendererForTesting())
        assertTrue(rendererFactory.renderers.isEmpty())
    }

    @Test
    fun settingOnCreatesRendererAndShowsView() {
        view.configure(
            enabled = true,
            colorMode = "fixed",
            fixedColor = Color.rgb(40, 80, 120)
        )

        val renderer = rendererFactory.renderers.single()
        assertEquals(View.VISIBLE, view.visibility)
        assertTrue(view.hasRendererForTesting())
        assertEquals(
            listOf("configure:true", "resume", "requestRender"),
            renderer.calls
        )
    }

    @Test
    fun surfaceAvailableSendsAttachCommand() {
        view.configure(
            enabled = true,
            colorMode = "fixed",
            fixedColor = Color.rgb(40, 80, 120)
        )
        val renderer = rendererFactory.renderers.single()
        val surfaceTexture = SurfaceTexture(0)

        view.onSurfaceTextureAvailable(surfaceTexture, 300, 120)

        assertTrue(renderer.calls.contains("attach:300x120"))
        surfaceTexture.release()
    }

    @Test
    fun settingOffAfterOnClearsReleasesAndHidesView() {
        view.configure(
            enabled = true,
            colorMode = "random",
            fixedColor = Color.rgb(17, 17, 17)
        )
        val renderer = rendererFactory.renderers.single()

        view.configure(
            enabled = false,
            colorMode = "random",
            fixedColor = Color.rgb(17, 17, 17)
        )

        assertEquals(View.GONE, view.visibility)
        assertFalse(view.hasRendererForTesting())
        assertTrue(renderer.calls.contains("clear"))
        assertTrue(renderer.calls.contains("release"))
        assertFalse(renderer.isRendererThreadAliveForTesting())
    }

    @Test
    fun clearPauseReleaseForwardLifecycleCommands() {
        view.configure(
            enabled = true,
            colorMode = "fixed",
            fixedColor = Color.rgb(40, 80, 120)
        )
        val renderer = rendererFactory.renderers.single()

        view.clearInk()
        view.pauseInk()
        view.releaseInk()

        assertTrue(renderer.calls.contains("clear"))
        assertTrue(renderer.calls.contains("pause"))
        assertTrue(renderer.calls.contains("release"))
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun pointerInputQueuesCommandsWithoutRunningRendererWork() {
        view.configure(
            enabled = true,
            colorMode = "fixed",
            fixedColor = Color.rgb(40, 80, 120)
        )

        view.onPointerDown(pointerId = 7, x = 20f, y = 30f)
        view.onPointerMove(pointerId = 7, x = 40f, y = 35f)
        view.onPointerUp(pointerId = 7, x = 40f, y = 35f)

        assertEquals(0, view.pointerStateCountForTesting())
        assertTrue(view.queuedInputCountForTesting() >= 2)
        assertTrue(rendererFactory.renderers.single().calls.count { it == "requestRender" } >= 2)
    }

    @Test
    fun rendererFailureDisablesOnlyEffect() {
        view.configure(
            enabled = true,
            colorMode = "fixed",
            fixedColor = Color.rgb(40, 80, 120)
        )
        val renderer = rendererFactory.renderers.single()

        rendererFactory.callbacks.single().onRendererDisabled(
            "shader compile",
            IllegalStateException("boom")
        )

        assertEquals(View.GONE, view.visibility)
        assertFalse(view.hasRendererForTesting())
        assertTrue(renderer.calls.contains("release"))
    }

    private class RecordingRendererFactory : FluidInkRendererFactory {
        val renderers = mutableListOf<RecordingRenderer>()
        val callbacks = mutableListOf<FluidInkRendererCallback>()

        override fun create(
            inputQueue: FluidInputCommandQueue,
            callback: FluidInkRendererCallback
        ): FluidInkRendererController {
            callbacks.add(callback)
            return RecordingRenderer().also(renderers::add)
        }
    }

    private class RecordingRenderer : FluidInkRendererController {
        val calls = mutableListOf<String>()
        private var alive = true

        override fun configure(settings: FluidInkSettings) {
            calls.add("configure:${settings.enabled}")
        }

        override fun attachSurface(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
            calls.add("attach:${width}x$height")
        }

        override fun resizeSurface(width: Int, height: Int) {
            calls.add("resize:${width}x$height")
        }

        override fun detachSurface() {
            calls.add("detach")
        }

        override fun resume() {
            calls.add("resume")
        }

        override fun requestRender() {
            calls.add("requestRender")
        }

        override fun clear() {
            calls.add("clear")
        }

        override fun pause() {
            calls.add("pause")
        }

        override fun release() {
            alive = false
            calls.add("release")
        }

        override fun isRendererThreadAliveForTesting(): Boolean = alive
    }
}
