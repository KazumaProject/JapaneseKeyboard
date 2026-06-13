package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.content.Context
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
class LiquidRippleEffectViewTest {

    private lateinit var view: LiquidRippleEffectView
    private lateinit var rendererFactory: RecordingRendererFactory

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        rendererFactory = RecordingRendererFactory()
        view = LiquidRippleEffectView(context).apply {
            rendererFactory = this@LiquidRippleEffectViewTest.rendererFactory
        }
    }

    @Test
    fun settingOffDoesNotCreateRenderer() {
        view.configure(enabled = false)

        assertEquals(View.GONE, view.visibility)
        assertFalse(view.hasRendererForTesting())
        assertTrue(rendererFactory.renderers.isEmpty())
    }

    @Test
    fun settingOnCreatesRendererAndShowsView() {
        view.configure(enabled = true)

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
        view.configure(enabled = true)
        val renderer = rendererFactory.renderers.single()
        val surfaceTexture = SurfaceTexture(0)

        view.onSurfaceTextureAvailable(surfaceTexture, 300, 120)

        assertTrue(renderer.calls.contains("attach:300x120"))
        surfaceTexture.release()
    }

    @Test
    fun pointerInputQueuesCommandsWithoutRunningRendererWork() {
        view.configure(enabled = true)

        view.onPointerDown(pointerId = 7, x = 20f, y = 30f)
        view.onPointerMove(pointerId = 7, x = 42f, y = 35f)
        view.onPointerUp(pointerId = 7, x = 42f, y = 35f)

        assertEquals(0, view.pointerStateCountForTesting())
        assertTrue(view.queuedInputCountForTesting() >= 2)
        assertTrue(rendererFactory.renderers.single().calls.count { it == "requestRender" } >= 2)
    }

    @Test
    fun rendererFailureDisablesOnlyEffect() {
        view.configure(enabled = true)
        val renderer = rendererFactory.renderers.single()

        rendererFactory.callbacks.single().onRendererDisabled(
            "shader compile",
            IllegalStateException("boom")
        )

        assertEquals(View.GONE, view.visibility)
        assertFalse(view.hasRendererForTesting())
        assertTrue(renderer.calls.contains("release"))
    }

    private class RecordingRendererFactory : LiquidRippleRendererFactory {
        val renderers = mutableListOf<RecordingRenderer>()
        val callbacks = mutableListOf<LiquidRippleRendererCallback>()

        override fun create(
            inputQueue: LiquidRippleInputCommandQueue,
            callback: LiquidRippleRendererCallback
        ): LiquidRippleRendererController {
            callbacks.add(callback)
            return RecordingRenderer().also(renderers::add)
        }
    }

    private class RecordingRenderer : LiquidRippleRendererController {
        val calls = mutableListOf<String>()
        private var alive = true

        override fun configure(settings: LiquidRippleSettings) {
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
