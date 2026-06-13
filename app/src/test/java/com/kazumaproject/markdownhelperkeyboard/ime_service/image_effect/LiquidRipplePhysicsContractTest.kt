package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiquidRipplePhysicsContractTest {

    @Test
    fun liquidRippleSimulationUsesDampedHeightFieldWaveEquation() {
        val simulation = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/LiquidRippleSimulation.kt"
        ).readText()

        assertTrue(simulation.contains("previous"))
        assertTrue(simulation.contains("current"))
        assertTrue(simulation.contains("next"))
        assertTrue(simulation.contains("laplacian"))
        assertTrue(simulation.contains("uPrevious"))
        assertTrue(simulation.contains("uCurrent"))
        assertTrue(simulation.contains("uDamping"))
        assertTrue(simulation.contains("uBoundaryAbsorptionWidth"))
        assertTrue(simulation.contains("boundaryDamping"))
        assertTrue(simulation.contains("accumulatedWaveTimeSeconds"))
        assertTrue(simulation.contains("FIXED_WAVE_STEP_SECONDS"))
        assertTrue(simulation.contains("stepWaveAtFixedRate"))
        assertFalse(simulation.contains("nextHeight *= smoothstep"))
        assertTrue(simulation.contains("uNormalSampleMode"))
        assertTrue(simulation.contains("normal = normalize"))
        assertTrue(simulation.contains("sampleHeight"))
        assertTrue(simulation.contains("directionalWake"))
        assertTrue(simulation.contains("uTime * 1.18"))
        assertTrue(simulation.contains("gradient * 12.0"))
        assertFalse(simulation.contains("android.graphics.Canvas"))
        assertFalse(simulation.contains("android.graphics.Bitmap"))
        assertFalse(simulation.contains("glReadPixels"))
    }

    @Test
    fun liquidRippleSimulationDoesNotDependOnInputOrCandidateSystems() {
        val simulation = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/LiquidRippleSimulation.kt"
        ).readText()

        listOf(
            "Candidate",
            "Converter",
            "InputConnection",
            "Zenz",
            "Gemma",
            "Clipboard"
        ).forEach { forbidden ->
            assertFalse("LiquidRippleSimulation must not depend on $forbidden", simulation.contains(forbidden))
        }
    }

    private fun mainFile(path: String): File {
        val moduleFile = File("src/main/$path")
        return if (moduleFile.exists()) moduleFile else File("app/src/main/$path")
    }
}
