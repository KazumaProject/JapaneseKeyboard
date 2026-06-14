package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TouchEffectSimulationResizeContractTest {

    @Test
    fun liquidRippleResizeSurfacePreservesVisibleState() {
        val lines = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/LiquidRippleSimulation.kt"
        ).readLines()
        val simulation = lines.joinToString("\n")
        val resizeSurface = functionBody(lines, "resizeSurface").joinToString("\n")
        val resizeTargets = functionBody(lines, "resizeTargets").joinToString("\n")

        assertFalse(resizeSurface.contains("clear()"))
        assertTrue(resizeSurface.contains("preserveExistingRipple = true"))
        assertTrue(resizeTargets.contains("preserveExistingRipple: Boolean"))
        assertTrue(resizeTargets.contains("copyTextureToTarget(oldTargets.previous.texture"))
        assertTrue(resizeTargets.contains("copyTextureToTarget(oldTargets.current.texture"))
        assertTrue(resizeTargets.contains("clearTarget(createdNext)"))
        assertTrue(resizeTargets.contains("energyEstimate = 0f"))
        assertTrue(resizeTargets.contains("accumulatedWaveTimeSeconds = 0f"))
        assertTrue(simulation.contains("private const val COPY_FRAGMENT_SHADER"))
        assertTrue(simulation.contains("uniform sampler2D uTexture;"))
        assertTrue(simulation.contains("Do not call this from resizeSurface()."))
    }

    @Test
    fun sprayPaintResizeSurfacePreservesVisibleState() {
        val lines = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/SprayPaintSimulation.kt"
        ).readLines()
        val simulation = lines.joinToString("\n")
        val resizeSurface = functionBody(lines, "resizeSurface").joinToString("\n")
        val resizeTargets = functionBody(lines, "resizeTargets").joinToString("\n")

        assertFalse(resizeSurface.contains("clear()"))
        assertTrue(resizeSurface.contains("preserveExistingPaint = true"))
        assertTrue(resizeTargets.contains("preserveExistingPaint: Boolean"))
        assertTrue(resizeTargets.contains("copyTextureToTarget(oldPaint.texture"))
        assertTrue(resizeTargets.contains("clearTarget(nextTemporary)"))
        assertTrue(resizeTargets.contains("popBursts.clear()"))
        assertTrue(resizeTargets.contains("petalSprites.clear()"))
        assertTrue(resizeTargets.contains("longPressResidualByPointer.clear()"))
        assertTrue(resizeTargets.contains("nextDripMillisByPointer.clear()"))
        assertTrue(resizeTargets.contains("energyEstimate = 0f"))
        assertTrue(simulation.contains("private const val COPY_FRAGMENT_SHADER"))
        assertTrue(simulation.contains("uniform sampler2D uTexture;"))
        assertTrue(simulation.contains("Do not call this from resizeSurface()."))
    }

    @Test
    fun rendererResizeAndPauseDoNotClearVisibleSimulationState() {
        listOf(
            "LiquidRippleRenderer.kt",
            "SprayPaintRenderer.kt"
        ).forEach { fileName ->
            val lines = mainFile(
                "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/$fileName"
            ).readLines()
            val resizeSurface = functionBody(lines, "resizeSurface").joinToString("\n")
            val pause = functionBody(lines, "pause").joinToString("\n")

            assertFalse("$fileName resizeSurface must not clear simulation", resizeSurface.contains("simulation?.clear()"))
            assertFalse("$fileName resizeSurface must not clear renderer", resizeSurface.contains("clearOnRendererThread()"))
            assertFalse("$fileName pause must not clear simulation", pause.contains("simulation?.clear()"))
        }
    }

    @Test
    fun effectViewSizeChangedOnlyResizesRenderer() {
        listOf(
            "LiquidRippleEffectView.kt",
            "SprayPaintEffectView.kt"
        ).forEach { fileName ->
            val lines = mainFile(
                "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/$fileName"
            ).readLines()
            val sizeChanged = functionBody(lines, "onSurfaceTextureSizeChanged").joinToString("\n")

            assertTrue(sizeChanged.contains("renderer?.resizeSurface(width, height)"))
            assertFalse("$fileName size change must not clear effect", sizeChanged.contains("clear()"))
        }
    }

    private fun functionBody(lines: List<String>, functionName: String): List<String> {
        val start = lines.indexOfFirst { it.contains("fun $functionName") }
        assertTrue("Missing function $functionName", start >= 0)

        var depth = 0
        var seenBody = false
        for (index in start until lines.size) {
            val line = lines[index]
            depth += line.count { it == '{' }
            seenBody = seenBody || line.contains('{')
            depth -= line.count { it == '}' }
            if (seenBody && depth == 0) {
                return lines.subList(start, index + 1)
            }
        }
        error("Missing function body end for $functionName")
    }

    private fun mainFile(path: String): File {
        val moduleFile = File("src/main/$path")
        return if (moduleFile.exists()) moduleFile else File("app/src/main/$path")
    }
}
