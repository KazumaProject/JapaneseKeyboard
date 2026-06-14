package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SuminagashiFluidContractTest {

    @Test
    fun rendererUsesDedicatedHandlerThreadInsteadOfCoroutineDispatchers() {
        val renderer = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/FluidInkRenderer.kt"
        ).readText()

        assertTrue(renderer.contains("HandlerThread"))
        assertTrue(renderer.contains("THREAD_NAME_PREFIX"))
        assertTrue(renderer.contains("SuminagashiFluidRenderer"))
        assertFalse(renderer.contains("Dispatchers."))
        assertFalse(renderer.contains("CoroutineScope"))
        assertFalse(renderer.contains("Dispatchers.IO"))
        assertFalse(renderer.contains("Dispatchers.Default"))
    }

    @Test
    fun fluidSimulationDoesNotUseCanvasBitmapOrGpuReadback() {
        val simulation = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/FluidSimulation.kt"
        ).readText()

        assertTrue(simulation.contains("velocity"))
        assertTrue(simulation.contains("dye"))
        assertTrue(simulation.contains("pressure"))
        assertTrue(simulation.contains("divergence"))
        assertTrue(simulation.contains("curl"))
        assertTrue(simulation.contains("velocityDiffusionSource"))
        assertTrue(simulation.contains("VELOCITY_DIFFUSION_FRAGMENT_SHADER"))
        assertTrue(simulation.contains("PRESSURE_FRAGMENT_SHADER"))
        assertTrue(simulation.contains("subtractPressureGradient"))
        assertTrue(simulation.contains("computeDivergence"))
        assertTrue(simulation.contains("solvePressure"))
        assertTrue(simulation.contains("applyVorticity"))
        assertTrue(simulation.contains("computeCurl"))
        assertFalse(simulation.contains("android.graphics.Canvas"))
        assertFalse(simulation.contains("android.graphics.Bitmap"))
        assertFalse(simulation.contains("android.graphics.Path"))
        assertFalse(simulation.contains("glReadPixels"))
        listOf(
            "Candidate",
            "Converter",
            "InputConnection",
            "Zenz",
            "Gemma",
            "Clipboard"
        ).forEach { forbidden ->
            assertFalse("FluidSimulation must not depend on $forbidden", simulation.contains(forbidden))
        }
    }

    @Test
    fun imeLifecycleClearsPausesAndReleasesFluidEffect() {
        val lines = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"
        ).readLines()

        val finishBody = functionBody(lines, "onFinishInputView")
        val hiddenBody = functionBody(lines, "onWindowHidden")
        val destroyBody = functionBody(lines, "onDestroy")

        assertTrue(finishBody.any { it.contains("clearAndPauseSuminagashiInkEffects()") })
        assertTrue(hiddenBody.any { it.contains("clearAndPauseSuminagashiInkEffects()") })
        assertTrue(destroyBody.any { it.contains("releaseSuminagashiInkEffects()") })
    }

    @Test
    fun candidateGenerationAndSuggestionAdapterDoNotReferenceFluidRenderer() {
        val files = listOf(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/adapters/SuggestionAdapter.kt",
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/QwertyGlideInputCoordinator.kt",
            "java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/Candidate.kt",
            "java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/ZenzCandidate.kt",
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/InputConnectionCommandRunner.kt"
        )

        files.forEach { path ->
            val file = mainFile(path)
            if (!file.exists()) return@forEach
            val text = mainFile(path).readText()
            assertFalse("$path must not depend on fluid renderer", text.contains("FluidInk"))
            assertFalse("$path must not depend on suminagashi view", text.contains("Suminagashi"))
        }
    }

    @Test
    fun gridResolverStartsBelowScreenResolutionAndCanReduceQuality() {
        val resolver = FluidSimulationGridResolver()

        val fullQuality = resolver.resolve(
            surfaceWidth = 3000,
            surfaceHeight = 1200,
            qualityLevel = 0,
            userQuality = KeyboardTouchEffectQuality.HIGH
        )
        val reducedQuality = resolver.resolve(
            surfaceWidth = 3000,
            surfaceHeight = 1200,
            qualityLevel = -2,
            userQuality = KeyboardTouchEffectQuality.HIGH
        )

        assertTrue(fullQuality.width <= 512)
        assertTrue(fullQuality.height <= 206)
        assertTrue(reducedQuality.width < fullQuality.width)
        assertTrue(reducedQuality.height < fullQuality.height)
    }

    @Test
    fun fluidRenderTargetFormatDoesNotDisableWhenHalfFloatExtensionIsMissing() {
        val format = FluidSimulation.FluidRenderTargetFormat.resolve(
            version = "OpenGL ES 3.0",
            extensions = ""
        )

        assertTrue(format.encodesSignedFields)
    }

    @Test
    fun fluidRenderTargetFormatPrefersHalfFloatWhenAvailable() {
        val format = FluidSimulation.FluidRenderTargetFormat.resolve(
            version = "OpenGL ES 3.2",
            extensions = "GL_EXT_color_buffer_half_float"
        )

        assertFalse(format.encodesSignedFields)
    }

    @Test
    fun textureViewRendererRequestsWindowSurfaceConfig() {
        val renderer = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/FluidInkRenderer.kt"
        ).readText()

        assertTrue(renderer.contains("EGL14.EGL_SURFACE_TYPE"))
        assertTrue(renderer.contains("EGL14.EGL_WINDOW_BIT"))
    }

    @Test
    fun imePassesTouchEffectQualityToMainAndFloatingEffects() {
        val imeService = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"
        ).readText()

        assertTrue(imeService.contains("keyboardTouchEffectQualityPreference"))
        assertTrue(
            imeService.split("quality = keyboardTouchEffectQualityPreference").size - 1 >= 4
        )
    }

    @Test
    fun imeSelectsLiquidInkAndAuroraInkOnSharedInkView() {
        val imeService = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"
        ).readText()

        assertTrue(imeService.contains("KeyboardTouchEffectType.isLiquidInk(effectType)"))
        assertTrue(imeService.contains("KeyboardTouchEffectType.isAuroraInk(effectType)"))
        assertTrue(imeService.contains("FluidInkTransportMode.WATER_DRIFT"))
        assertTrue(imeService.contains("transportMode = inkTransportMode"))
    }

    @Test
    fun liquidRippleUsesOpenGlRendererThreadAndHeightFieldSimulation() {
        val renderer = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/LiquidRippleRenderer.kt"
        ).readText()
        val simulation = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/LiquidRippleSimulation.kt"
        ).readText()

        assertTrue(renderer.contains("HandlerThread"))
        assertTrue(renderer.contains("LiquidRippleRenderer"))
        assertFalse(renderer.contains("Dispatchers."))
        assertTrue(simulation.contains("uPrevious"))
        assertTrue(simulation.contains("uCurrent"))
        assertTrue(simulation.contains("uWaveSpeed"))
        assertTrue(simulation.contains("normal = normalize"))
        assertTrue(simulation.contains("R16F"))
        assertTrue(simulation.contains("EncodedRgba8"))
        assertFalse(simulation.contains("android.graphics.Canvas"))
        assertFalse(simulation.contains("android.graphics.Bitmap"))
        assertFalse(simulation.contains("glReadPixels"))
    }

    @Test
    fun simulationAvoidsHalfFloatLinearFilteringAndOutputsPremultipliedAlpha() {
        val simulation = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/FluidSimulation.kt"
        ).readText()

        assertTrue(simulation.contains("GLES30.GL_NEAREST"))
        assertFalse(simulation.contains("GLES30.GL_LINEAR"))
        assertTrue(simulation.contains("fragColor = vec4(color * alpha, alpha);"))
    }

    @Test
    fun shaderSourceIsTrimmedBeforeCompileSoVersionDirectiveIsFirstLine() {
        val simulation = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/FluidSimulation.kt"
        ).readText()

        assertTrue(simulation.contains("GLES30.glShaderSource(shader, source.trimIndent())"))

        val liquidSimulation = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/LiquidRippleSimulation.kt"
        ).readText()
        assertTrue(liquidSimulation.contains("GLES30.glShaderSource(shader, source.trimIndent())"))
    }

    @Test
    fun simulationUsesPhysicalFieldsForInkTransport() {
        val simulation = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/FluidSimulation.kt"
        ).readText()

        assertTrue(simulation.contains("private const val VELOCITY_SPLAT_SCALE = 4.2f"))
        assertTrue(simulation.contains("MAX_DYE_DENSITY = 0.84"))
        assertTrue(simulation.contains("float radiusSquared = radius * radius;"))
        assertTrue(simulation.contains("exp(-dot(delta, delta) / radiusSquared)"))
        assertTrue(simulation.contains("if (command.injectVelocity)"))
        assertTrue(simulation.contains("if (command.injectDye)"))
        assertTrue(simulation.contains("sampleVelocity"))
        assertTrue(simulation.contains("sampleSource"))
        assertTrue(simulation.contains("vec2 coord = clamp(vUv - velocity * uDt"))
        assertTrue(simulation.contains("Dye is transported by the solved velocity field plus optional water drift"))
        assertTrue(simulation.contains("uniform float uWaterDrift;"))
        assertTrue(simulation.contains("waterDriftVelocity"))
        assertFalse(simulation.contains("vec4 mixed = base + add"))

        val renderer = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/FluidInkRenderer.kt"
        ).readText()
        assertTrue(renderer.contains("SETTLING_NANOS = 8_000_000_000L"))

        val governor = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/image_effect/FluidPerformanceGovernor.kt"
        ).readText()
        assertTrue(governor.contains("velocityDiffusionIterations"))
        assertTrue(governor.contains("dyeDissipation = 0.9984f"))
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
