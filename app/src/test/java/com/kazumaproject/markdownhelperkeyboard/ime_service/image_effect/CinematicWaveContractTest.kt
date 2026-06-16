package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CinematicWaveContractTest {

    @Test
    fun rendererUsesOpenGlRendererThreadAndSafeFailureCallback() {
        val renderer = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/CinematicWaveRenderer.kt"
        ).readText()

        assertTrue(renderer.contains("HandlerThread"))
        assertTrue(renderer.contains("CinematicWaveRenderer"))
        assertTrue(renderer.contains("callback.onRendererDisabled"))
        assertTrue(renderer.contains("EGL_OPENGL_ES3_BIT"))
        assertFalse(renderer.contains("Dispatchers."))
        assertFalse(renderer.contains("CoroutineScope"))
    }

    @Test
    fun shaderUsesDomainWarpFbmLayeredWaveAndTouchLensDistortion() {
        val simulation = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/CinematicWaveSimulation.kt"
        ).readText()

        assertTrue(simulation.contains("float fbm"))
        assertTrue(simulation.contains("vec2 warp"))
        assertTrue(simulation.contains("warpedUv"))
        assertTrue(simulation.contains("float w1 = sin"))
        assertTrue(simulation.contains("float w2 = sin"))
        assertTrue(simulation.contains("ridge"))
        assertTrue(simulation.contains("uTouchPositions[5]"))
        assertTrue(simulation.contains("uTouchStrengths[5]"))
        assertTrue(simulation.contains("lens"))
        assertTrue(simulation.contains("fragColor = vec4(color * alpha, alpha);"))
        assertFalse(simulation.contains("android.graphics.Canvas"))
        assertFalse(simulation.contains("android.graphics.Bitmap"))
        assertFalse(simulation.contains("drawCircle"))
        assertFalse(simulation.contains("drawLine"))
    }

    @Test
    fun imeClearsPausesAndReleasesCinematicWaveWithOtherTouchEffects() {
        val imeService = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"
        ).readText()

        assertTrue(imeService.contains("KeyboardTouchEffectType.isCinematicWave(effectType)"))
        assertTrue(imeService.contains("cinematicWaveEffectView.configure"))
        assertTrue(imeService.contains("dispatchCinematicWaveMotionEvent"))
        assertTrue(imeService.contains("clearWave()"))
        assertTrue(imeService.contains("pauseWave()"))
        assertTrue(imeService.contains("releaseWave()"))
    }

    private fun mainFile(path: String): File {
        val moduleFile = File("src/main/$path")
        return if (moduleFile.exists()) moduleFile else File("app/src/main/$path")
    }
}
