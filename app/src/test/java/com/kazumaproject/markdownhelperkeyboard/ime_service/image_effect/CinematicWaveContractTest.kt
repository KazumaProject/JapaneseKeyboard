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
        assertTrue(simulation.contains("uniform int uWaveType"))
        assertTrue(simulation.contains("if (uWaveType == 1)"))
        assertTrue(simulation.contains("if (uWaveType == 2)"))
        assertTrue(simulation.contains("if (uWaveType == 3)"))
        assertTrue(simulation.contains("if (uWaveType == 4)"))
        assertTrue(simulation.contains("float ribbon"))
        assertTrue(simulation.contains("prismaticCrossing"))
        assertTrue(simulation.contains("spectrumSheetField"))
        assertTrue(simulation.contains("spectrumIntersectionGlow"))
        assertTrue(simulation.contains("vividSpectrumField"))
        assertTrue(simulation.contains("vividSpectrumEdge"))
        assertTrue(simulation.contains("vividSpectrumCrossGlow"))
        assertTrue(simulation.contains("lens"))
        assertTrue(simulation.contains("fragColor = vec4(color * alpha, alpha);"))
        assertFalse(simulation.contains("android.graphics.Canvas"))
        assertFalse(simulation.contains("android.graphics.Bitmap"))
        assertFalse(simulation.contains("drawCircle"))
        assertFalse(simulation.contains("drawLine"))

        val spectrumBranch = shaderBranch(
            simulation = simulation,
            startMarker = "if (uWaveType == 3)",
            endMarker = "if (uWaveType == 4)"
        )
        val vividSpectrumBranch = shaderBranch(
            simulation = simulation,
            startMarker = "if (uWaveType == 4)",
            endMarker = "if (uWaveType == 1)"
        )
        assertFalse(spectrumBranch.contains("ribbon("))
        assertFalse(vividSpectrumBranch.contains("ribbon("))
        assertFalse(spectrumBranch.contains("prismatic"))
        assertFalse(vividSpectrumBranch.contains("prismatic"))
        assertFalse(vividSpectrumBranch.contains("oledRibbon"))
        assertFalse(vividSpectrumBranch.contains("fold"))
        assertFalse(vividSpectrumBranch.contains("Fold"))
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

    @Test
    fun rendererResizesWithoutRecreatingEglSurfaceToAvoidKeyboardHeightFlicker() {
        val lines = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/CinematicWaveRenderer.kt"
        ).readLines()
        val resizeSurface = functionBody(lines, "resizeSurface").joinToString("\n")
        val ensureSurfaceForCurrentSize =
            functionBody(lines, "ensureSurfaceForCurrentSize").joinToString("\n")

        assertTrue(resizeSurface.contains("ensureSurfaceForCurrentSize()"))
        assertFalse(resizeSurface.contains("recreateSurfaceForCurrentSettings()"))
        assertTrue(ensureSurfaceForCurrentSize.contains("simulation?.resizeSurface"))
        assertFalse(ensureSurfaceForCurrentSize.contains("setDefaultBufferSize"))
        assertFalse(ensureSurfaceForCurrentSize.contains("simulation?.release()"))
        assertFalse(ensureSurfaceForCurrentSize.contains("releaseEglSurfaceOnly()"))
    }

    private fun mainFile(path: String): File {
        val moduleFile = File("src/main/$path")
        return if (moduleFile.exists()) moduleFile else File("app/src/main/$path")
    }

    private fun functionBody(lines: List<String>, functionName: String): List<String> {
        val start = lines.indexOfFirst { it.contains("fun $functionName") }
        require(start >= 0) { "Missing function $functionName" }
        var depth = 0
        var seenOpen = false
        val body = mutableListOf<String>()
        for (index in start until lines.size) {
            val line = lines[index]
            body += line
            line.forEach { char ->
                when (char) {
                    '{' -> {
                        depth++
                        seenOpen = true
                    }

                    '}' -> depth--
                }
            }
            if (seenOpen && depth == 0) break
        }
        return body
    }

    private fun shaderBranch(
        simulation: String,
        startMarker: String,
        endMarker: String
    ): String {
        val start = simulation.indexOf(startMarker)
        require(start >= 0) { "Missing shader branch $startMarker" }
        val end = simulation.indexOf(endMarker, start + startMarker.length)
        require(end > start) { "Missing shader branch end $endMarker" }
        return simulation.substring(start, end)
    }
}
