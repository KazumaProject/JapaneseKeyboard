package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Random
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal class SprayPaintSimulation(
    private val gridResolver: SprayPaintSimulationGridResolver =
        SprayPaintSimulationGridResolver(),
    private val random: Random = Random()
) {
    private data class RenderTarget(
        val texture: Int,
        val framebuffer: Int,
        val width: Int,
        val height: Int
    )

    private data class PopBurst(
        val x: Float,
        val y: Float,
        val color: SprayPaintColor,
        val style: SprayPaintPaletteStyle,
        var ageSeconds: Float = 0f
    )

    private data class Particle(
        val x: Float,
        val y: Float,
        val radiusPx: Float,
        val red: Float,
        val green: Float,
        val blue: Float,
        val alpha: Float,
        val directionX: Float,
        val directionY: Float,
        val stretch: Float,
        val noiseScale: Float,
        val seed: Float,
        val depositionModel: SprayPaintDepositionModel,
        val compositeModel: SprayPaintCompositeModel,
        val maxParticleAlpha: Float
    )

    private data class PetalSprite(
        var x: Float,
        var y: Float,
        var velocityX: Float,
        var velocityY: Float,
        var angle: Float,
        val angularVelocity: Float,
        val color: SprayPaintColor,
        val scale: Float,
        val phase: Float,
        val lifetimeSeconds: Float,
        val seed: Float,
        var ageSeconds: Float = 0f
    )

    private data class StyleProfile(
        val depositionModel: SprayPaintDepositionModel,
        val compositeModel: SprayPaintCompositeModel,
        val tapCountScale: Float,
        val coreShare: Float,
        val mistShare: Float,
        val speckleShare: Float,
        val coreSigmaPx: Float,
        val mistSigmaPx: Float,
        val speckleSigmaPx: Float,
        val coreMedianRadiusPx: Float,
        val mistMedianRadiusPx: Float,
        val speckleMedianRadiusPx: Float,
        val coreAlpha: Float,
        val mistAlpha: Float,
        val speckleAlpha: Float,
        val moveCountScale: Float,
        val moveAlongBasePx: Float,
        val moveAlongSpeedPx: Float,
        val moveMistSidePx: Float,
        val moveCoreSidePx: Float,
        val moveStretchBoost: Float,
        val popCountScale: Float,
        val popStartSpreadPx: Float,
        val popEndSpreadPx: Float,
        val popAlphaScale: Float,
        val longPressScale: Float,
        val maxParticleAlpha: Float,
        val maxDisplayAlpha: Float,
        val decayScale: Float,
        val dripChance: Float,
        val dripLengthScale: Float,
        val dripIntervalScale: Float,
        val wetShine: Float,
        val edgeDarkening: Float,
        val graffitiAccentScale: Float,
        val liquidGlobScale: Float,
        val petalScale: Float,
        val smokeScale: Float
    )

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var grid = SprayPaintSimulationGrid(32, 32)
    private var paintAccumulationTexture: RenderTarget? = null
    private var temporaryTexture: RenderTarget? = null
    private var noiseTexture = 0
    private var timeSeconds = 0f
    private var energyEstimate = 0f
    private var particlesEmittedThisFrame = 0
    private var maxParticlesThisFrame = MAX_HARD_PARTICLES_PER_FRAME
    private val popBursts = ArrayList<PopBurst>()
    private val petalSprites = ArrayList<PetalSprite>()
    private val longPressResidualByPointer = HashMap<Int, Float>()
    private val nextDripMillisByPointer = HashMap<Int, Long>()
    private var currentStyle = SprayPaintPaletteStyle.PaintSplash
    private val sprayProfile = StyleProfile(
        depositionModel = SprayPaintDepositionModel.AerosolGaussian,
        compositeModel = SprayPaintCompositeModel.AdditiveMist,
        tapCountScale = 1.08f,
        coreShare = 0.32f,
        mistShare = 0.48f,
        speckleShare = 0.20f,
        coreSigmaPx = 12f,
        mistSigmaPx = 46f,
        speckleSigmaPx = 74f,
        coreMedianRadiusPx = 10.5f,
        mistMedianRadiusPx = 5.8f,
        speckleMedianRadiusPx = 2.4f,
        coreAlpha = 0.060f,
        mistAlpha = 0.024f,
        speckleAlpha = 0.085f,
        moveCountScale = 1.08f,
        moveAlongBasePx = 28f,
        moveAlongSpeedPx = 86f,
        moveMistSidePx = 28f,
        moveCoreSidePx = 8f,
        moveStretchBoost = 2.75f,
        popCountScale = 0.68f,
        popStartSpreadPx = 34f,
        popEndSpreadPx = 94f,
        popAlphaScale = 0.78f,
        longPressScale = 0.72f,
        maxParticleAlpha = 0.24f,
        maxDisplayAlpha = 0.52f,
        decayScale = 0.92f,
        dripChance = 0f,
        dripLengthScale = 0f,
        dripIntervalScale = 1f,
        wetShine = 0.16f,
        edgeDarkening = 0f,
        graffitiAccentScale = 0f,
        liquidGlobScale = 0f,
        petalScale = 0f,
        smokeScale = 0.18f
    )
    private val paintSplashProfile = StyleProfile(
        depositionModel = SprayPaintDepositionModel.NoisyPaintSplat,
        compositeModel = SprayPaintCompositeModel.AlphaPaint,
        tapCountScale = 0.82f,
        coreShare = 0.70f,
        mistShare = 0.05f,
        speckleShare = 0.25f,
        coreSigmaPx = 7f,
        mistSigmaPx = 22f,
        speckleSigmaPx = 64f,
        coreMedianRadiusPx = 24f,
        mistMedianRadiusPx = 6.4f,
        speckleMedianRadiusPx = 5.8f,
        coreAlpha = 0.22f,
        mistAlpha = 0.026f,
        speckleAlpha = 0.17f,
        moveCountScale = 0.72f,
        moveAlongBasePx = 18f,
        moveAlongSpeedPx = 32f,
        moveMistSidePx = 16f,
        moveCoreSidePx = 12f,
        moveStretchBoost = 1.15f,
        popCountScale = 0.42f,
        popStartSpreadPx = 18f,
        popEndSpreadPx = 86f,
        popAlphaScale = 1.35f,
        longPressScale = 0.58f,
        maxParticleAlpha = 0.62f,
        maxDisplayAlpha = 0.92f,
        decayScale = 1.035f,
        dripChance = 0.28f,
        dripLengthScale = 0.76f,
        dripIntervalScale = 1.2f,
        wetShine = 1.2f,
        edgeDarkening = 0.16f,
        graffitiAccentScale = 0f,
        liquidGlobScale = 0.35f,
        petalScale = 0f,
        smokeScale = 0f
    )
    private val graffitiProfile = StyleProfile(
        depositionModel = SprayPaintDepositionModel.DirectionalGraffiti,
        compositeModel = SprayPaintCompositeModel.AlphaPaint,
        tapCountScale = 1.04f,
        coreShare = 0.35f,
        mistShare = 0.45f,
        speckleShare = 0.20f,
        coreSigmaPx = 12f,
        mistSigmaPx = 40f,
        speckleSigmaPx = 78f,
        coreMedianRadiusPx = 8.4f,
        mistMedianRadiusPx = 3.9f,
        speckleMedianRadiusPx = 2.0f,
        coreAlpha = 0.052f,
        mistAlpha = 0.028f,
        speckleAlpha = 0.11f,
        moveCountScale = 1.22f,
        moveAlongBasePx = 34f,
        moveAlongSpeedPx = 82f,
        moveMistSidePx = 22f,
        moveCoreSidePx = 6.6f,
        moveStretchBoost = 3.65f,
        popCountScale = 0.92f,
        popStartSpreadPx = 26f,
        popEndSpreadPx = 92f,
        popAlphaScale = 1.05f,
        longPressScale = 0.92f,
        maxParticleAlpha = 0.30f,
        maxDisplayAlpha = 0.70f,
        decayScale = 0.99f,
        dripChance = 0.26f,
        dripLengthScale = 0.66f,
        dripIntervalScale = 1.35f,
        wetShine = 0.34f,
        edgeDarkening = 1f,
        graffitiAccentScale = 1f,
        liquidGlobScale = 0f,
        petalScale = 0f,
        smokeScale = 0.08f
    )
    private val liquidPaintProfile = StyleProfile(
        depositionModel = SprayPaintDepositionModel.ViscousLiquid,
        compositeModel = SprayPaintCompositeModel.WetHeightPaint,
        tapCountScale = 0.72f,
        coreShare = 0.65f,
        mistShare = 0f,
        speckleShare = 0.35f,
        coreSigmaPx = 6f,
        mistSigmaPx = 12f,
        speckleSigmaPx = 30f,
        coreMedianRadiusPx = 26f,
        mistMedianRadiusPx = 8f,
        speckleMedianRadiusPx = 10.5f,
        coreAlpha = 0.28f,
        mistAlpha = 0f,
        speckleAlpha = 0.18f,
        moveCountScale = 0.64f,
        moveAlongBasePx = 16f,
        moveAlongSpeedPx = 24f,
        moveMistSidePx = 0f,
        moveCoreSidePx = 13f,
        moveStretchBoost = 1.75f,
        popCountScale = 0.16f,
        popStartSpreadPx = 8f,
        popEndSpreadPx = 28f,
        popAlphaScale = 0.92f,
        longPressScale = 0.86f,
        maxParticleAlpha = 0.72f,
        maxDisplayAlpha = 0.96f,
        decayScale = 1.055f,
        dripChance = 0.88f,
        dripLengthScale = 1.35f,
        dripIntervalScale = 0.62f,
        wetShine = 1.6f,
        edgeDarkening = 0.08f,
        graffitiAccentScale = 0f,
        liquidGlobScale = 1f,
        petalScale = 0f,
        smokeScale = 0f
    )
    private val flowerPetalsProfile = StyleProfile(
        depositionModel = SprayPaintDepositionModel.PetalPrimitive,
        compositeModel = SprayPaintCompositeModel.PetalAlpha,
        tapCountScale = 0.32f,
        coreShare = 0f,
        mistShare = 0f,
        speckleShare = 0f,
        coreSigmaPx = 0f,
        mistSigmaPx = 0f,
        speckleSigmaPx = 0f,
        coreMedianRadiusPx = 8f,
        mistMedianRadiusPx = 7f,
        speckleMedianRadiusPx = 5f,
        coreAlpha = 0.20f,
        mistAlpha = 0f,
        speckleAlpha = 0f,
        moveCountScale = 0.38f,
        moveAlongBasePx = 36f,
        moveAlongSpeedPx = 34f,
        moveMistSidePx = 34f,
        moveCoreSidePx = 18f,
        moveStretchBoost = 2.6f,
        popCountScale = 0f,
        popStartSpreadPx = 0f,
        popEndSpreadPx = 0f,
        popAlphaScale = 0f,
        longPressScale = 0.46f,
        maxParticleAlpha = 0.42f,
        maxDisplayAlpha = 0.72f,
        decayScale = 0.96f,
        dripChance = 0f,
        dripLengthScale = 0f,
        dripIntervalScale = 1f,
        wetShine = 0.04f,
        edgeDarkening = 0f,
        graffitiAccentScale = 0f,
        liquidGlobScale = 0f,
        petalScale = 1f,
        smokeScale = 0f
    )

    private var depositionProgram = 0
    private var decayProgram = 0
    private var compositeProgram = 0
    private var copyProgram = 0

    private val quadVertices: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD)
            position(0)
        }

    fun initialize(
        surfaceWidth: Int,
        surfaceHeight: Int,
        qualityLevel: Int,
        userQuality: String = KeyboardTouchEffectQuality.HIGH
    ) {
        require(surfaceWidth > 0 && surfaceHeight > 0) {
            "Invalid spray paint surface size ${surfaceWidth}x$surfaceHeight"
        }
        if (depositionProgram == 0) {
            createPrograms()
        }
        if (noiseTexture == 0) {
            noiseTexture = createNoiseTexture()
        }
        this.surfaceWidth = surfaceWidth
        this.surfaceHeight = surfaceHeight
        val nextGrid = gridResolver.resolve(surfaceWidth, surfaceHeight, qualityLevel, userQuality)
        resizeTargets(
            nextGrid = nextGrid,
            preserveExistingPaint = false
        )
        clear()
    }

    fun resizeSurface(
        surfaceWidth: Int,
        surfaceHeight: Int,
        qualityLevel: Int,
        userQuality: String = KeyboardTouchEffectQuality.HIGH
    ) {
        if (depositionProgram == 0) {
            initialize(surfaceWidth, surfaceHeight, qualityLevel, userQuality)
            return
        }
        this.surfaceWidth = surfaceWidth
        this.surfaceHeight = surfaceHeight
        val nextGrid = gridResolver.resolve(surfaceWidth, surfaceHeight, qualityLevel, userQuality)
        if (nextGrid == grid) return
        resizeTargets(
            nextGrid = nextGrid,
            preserveExistingPaint = true
        )
    }

    fun render(
        inputCommands: List<SprayPaintInputCommand>,
        activePointers: List<SprayPaintActivePointer>,
        nowMillis: Long,
        dtSeconds: Float,
        params: SprayPaintStepParams
    ): Boolean {
        if (paintAccumulationTexture == null || temporaryTexture == null) return false
        val clampedDt = dtSeconds.coerceIn(MIN_DT_SECONDS, MAX_DT_SECONDS)
        timeSeconds = (timeSeconds + clampedDt).let {
            if (it > TIME_WRAP_SECONDS) it - TIME_WRAP_SECONDS else it
        }
        particlesEmittedThisFrame = 0
        maxParticlesThisFrame = min(params.maxParticlesPerFrame, MAX_HARD_PARTICLES_PER_FRAME)
        currentStyle = resolveFrameStyle(inputCommands, activePointers)
        val frameProfile = styleProfile(currentStyle)

        decayPaint(params, clampedDt, frameProfile)
        applyInputCommands(inputCommands, params)
        emitPopBursts(params, clampedDt)
        emitLongPress(activePointers, nowMillis, clampedDt, params)
        advancePetalSprites(clampedDt, params)
        composite(params, styleProfile(currentStyle))

        val energyDecay = effectiveDecayPerSecond(params, frameProfile).pow(clampedDt)
            .coerceIn(0.70f, 0.999f)
        energyEstimate *= energyDecay
        if (
            energyEstimate < params.idleEnergyThreshold &&
            inputCommands.isEmpty() &&
            activePointers.isEmpty() &&
            popBursts.isEmpty() &&
            petalSprites.isEmpty()
        ) {
            clear()
            return false
        }
        return energyEstimate >= params.idleEnergyThreshold ||
            inputCommands.isNotEmpty() ||
            activePointers.isNotEmpty() ||
            popBursts.isNotEmpty() ||
            petalSprites.isNotEmpty()
    }

    fun hasVisiblePaint(): Boolean {
        return energyEstimate >= DEFAULT_IDLE_ENERGY_THRESHOLD ||
            popBursts.isNotEmpty() ||
            petalSprites.isNotEmpty()
    }

    /**
     * Clears visible simulation state.
     *
     * Do not call this from resizeSurface().
     * resizeSurface() must preserve user-visible effect state.
     */
    fun clear() {
        clearTarget(paintAccumulationTexture)
        clearTarget(temporaryTexture)
        popBursts.clear()
        petalSprites.clear()
        longPressResidualByPointer.clear()
        nextDripMillisByPointer.clear()
        energyEstimate = 0f
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        }
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    fun release() {
        releaseTarget(paintAccumulationTexture)
        releaseTarget(temporaryTexture)
        paintAccumulationTexture = null
        temporaryTexture = null
        if (noiseTexture != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(noiseTexture), 0)
            noiseTexture = 0
        }
        val programs = intArrayOf(
            depositionProgram,
            decayProgram,
            compositeProgram,
            copyProgram
        ).filter { it != 0 }.toIntArray()
        programs.forEach(GLES30::glDeleteProgram)
        depositionProgram = 0
        decayProgram = 0
        compositeProgram = 0
        copyProgram = 0
    }

    private fun resizeTargets(
        nextGrid: SprayPaintSimulationGrid,
        preserveExistingPaint: Boolean
    ) {
        val oldPaint = paintAccumulationTexture
        val oldTemporary = temporaryTexture
        var nextPaint: RenderTarget? = null
        var nextTemporary: RenderTarget? = null

        try {
            nextPaint = createTarget(nextGrid.width, nextGrid.height)
            nextTemporary = createTarget(nextGrid.width, nextGrid.height)

            if (preserveExistingPaint && oldPaint != null) {
                copyTextureToTarget(oldPaint.texture, nextPaint)
                clearTarget(nextTemporary)
            } else {
                clearTarget(nextPaint)
                clearTarget(nextTemporary)
                popBursts.clear()
                petalSprites.clear()
                longPressResidualByPointer.clear()
                nextDripMillisByPointer.clear()
                energyEstimate = 0f
            }

            grid = nextGrid
            paintAccumulationTexture = nextPaint
            temporaryTexture = nextTemporary
            releaseTarget(oldPaint)
            releaseTarget(oldTemporary)
        } catch (throwable: Throwable) {
            releaseTarget(nextPaint)
            releaseTarget(nextTemporary)
            throw throwable
        }
    }

    private fun resolveFrameStyle(
        inputCommands: List<SprayPaintInputCommand>,
        activePointers: List<SprayPaintActivePointer>
    ): SprayPaintPaletteStyle {
        inputCommands.asReversed().forEach { command ->
            if (command is SprayPaintInputCommand.Spray) {
                return command.style
            }
        }
        return activePointers.firstOrNull()?.style ?: currentStyle
    }

    private fun effectiveDecayPerSecond(
        params: SprayPaintStepParams,
        profile: StyleProfile
    ): Float {
        return (params.decayPerSecond * profile.decayScale).coerceIn(0.68f, 0.9985f)
    }

    private fun applyInputCommands(
        inputCommands: List<SprayPaintInputCommand>,
        params: SprayPaintStepParams
    ) {
        inputCommands.forEach { command ->
            when (command) {
                is SprayPaintInputCommand.Spray -> {
                    currentStyle = command.style
                    when (command.kind) {
                        SprayPaintEmissionKind.Down -> {
                            emitDown(command, params)
                            if (styleProfile(command.style).popCountScale > 0f) {
                                popBursts.add(
                                    PopBurst(
                                        x = command.x,
                                        y = command.y,
                                        color = command.color,
                                        style = command.style
                                    )
                                )
                            }
                        }

                        SprayPaintEmissionKind.Move -> emitMove(command, params)
                        SprayPaintEmissionKind.Up -> emitUp(command, params)
                    }
                }

                is SprayPaintInputCommand.PointerUp -> {
                    longPressResidualByPointer.remove(command.pointerId)
                    nextDripMillisByPointer.remove(command.pointerId)
                }

                is SprayPaintInputCommand.PointerCancel -> {
                    longPressResidualByPointer.remove(command.pointerId)
                    nextDripMillisByPointer.remove(command.pointerId)
                }

                is SprayPaintInputCommand.CancelAll -> {
                    longPressResidualByPointer.clear()
                    nextDripMillisByPointer.clear()
                }
            }
        }
    }

    private fun emitDown(command: SprayPaintInputCommand.Spray, params: SprayPaintStepParams) {
        when (command.style) {
            SprayPaintPaletteStyle.Spray -> emitSprayDown(command, params)
            SprayPaintPaletteStyle.PaintSplash -> emitPaintSplashDown(command, params)
            SprayPaintPaletteStyle.Graffiti -> emitGraffitiDown(command, params)
            SprayPaintPaletteStyle.LiquidPaint -> emitLiquidPaintDown(command, params)
            SprayPaintPaletteStyle.FlowerPetals -> emitFlowerPetalsDown(command, params)
        }
    }

    private fun emitMove(command: SprayPaintInputCommand.Spray, params: SprayPaintStepParams) {
        when (command.style) {
            SprayPaintPaletteStyle.Spray -> emitSprayMove(command, params)
            SprayPaintPaletteStyle.PaintSplash -> emitPaintSplashMove(command, params)
            SprayPaintPaletteStyle.Graffiti -> emitGraffitiMove(command, params)
            SprayPaintPaletteStyle.LiquidPaint -> emitLiquidPaintMove(command, params)
            SprayPaintPaletteStyle.FlowerPetals -> emitFlowerPetalsMove(command, params)
        }
    }

    private fun emitUp(command: SprayPaintInputCommand.Spray, params: SprayPaintStepParams) {
        when (command.style) {
            SprayPaintPaletteStyle.Spray -> emitSprayUp(command, params)
            SprayPaintPaletteStyle.PaintSplash -> emitPaintSplashUp(command, params)
            SprayPaintPaletteStyle.Graffiti -> emitGraffitiUp(command, params)
            SprayPaintPaletteStyle.LiquidPaint -> emitLiquidPaintUp(command, params)
            SprayPaintPaletteStyle.FlowerPetals -> emitFlowerPetalsUp(command, params)
        }
    }

    private fun emitSprayDown(command: SprayPaintInputCommand.Spray, params: SprayPaintStepParams) {
        val profile = styleProfile(command.style)
        emitAerosolBurst(
            centerX = command.x,
            centerY = command.y,
            directionX = 1f,
            directionY = 0f,
            color = command.color,
            style = command.style,
            profile = profile,
            count = poissonLike(params.tapParticleCount.toFloat() * profile.tapCountScale),
            params = params,
            speedFactor = 0f
        )
    }

    private fun emitSprayMove(command: SprayPaintInputCommand.Spray, params: SprayPaintStepParams) {
        emitAlongPath(command, params) { centerX, centerY, directionX, directionY, speedFactor, count ->
            val profile = styleProfile(command.style)
            emitAerosolBurst(
                centerX = centerX,
                centerY = centerY,
                directionX = directionX,
                directionY = directionY,
                color = command.color,
                style = command.style,
                profile = profile,
                count = count,
                params = params,
                speedFactor = speedFactor
            )
        }
    }

    private fun emitSprayUp(command: SprayPaintInputCommand.Spray, params: SprayPaintStepParams) {
        val profile = styleProfile(command.style)
        emitSpeckleScatter(
            centerX = command.x,
            centerY = command.y,
            color = command.color,
            style = command.style,
            profile = profile,
            count = poissonLike(params.upParticleCount.toFloat() * 0.8f),
            radiusScale = 0.82f,
            alphaScale = 0.58f,
            params = params
        )
    }

    private fun emitPaintSplashDown(
        command: SprayPaintInputCommand.Spray,
        params: SprayPaintStepParams
    ) {
        val profile = styleProfile(command.style)
        val count = poissonLike(params.tapParticleCount.toFloat() * profile.tapCountScale)
        val coreCount = (count * 0.18f).toInt().coerceAtLeast(3)
        repeat(coreCount) {
            emitParticle(
                centerX = command.x,
                centerY = command.y,
                sigmaAlong = profile.coreSigmaPx,
                sigmaSide = profile.coreSigmaPx,
                directionX = cos(random.nextFloat() * TWO_PI),
                directionY = sin(random.nextFloat() * TWO_PI),
                color = if (random.nextFloat() < 0.35f) lighten(command.color, 0.08f) else command.color,
                radiusPx = logNormal(profile.coreMedianRadiusPx * (0.86f + random.nextFloat() * 0.34f), 0.2f),
                alpha = profile.coreAlpha * command.color.alpha,
                stretch = 1f + random.nextFloat() * 0.42f,
                noiseScale = noiseScaleFor(command.style, 58f),
                profile = profile
            )
        }
        emitRadialSplashDroplets(
            centerX = command.x,
            centerY = command.y,
            color = command.color,
            style = command.style,
            profile = profile,
            count = (count * 0.56f).toInt().coerceAtLeast(8),
            spreadPx = 54f * params.mistScale,
            speedFactor = 0.34f
        )
        emitLiquidGlobules(
            centerX = command.x,
            centerY = command.y,
            directionX = 1f,
            directionY = 0f,
            color = command.color,
            count = (count * 0.14f).toInt().coerceAtLeast(2),
            speedFactor = 0f,
            params = params
        )
    }

    private fun emitPaintSplashMove(
        command: SprayPaintInputCommand.Spray,
        params: SprayPaintStepParams
    ) {
        emitAlongPath(command, params, spacingScale = 1.7f) { centerX, centerY, directionX, directionY, speedFactor, count ->
            val profile = styleProfile(command.style)
            val blobCount = (count * 0.28f).toInt().coerceAtLeast(1)
            repeat(blobCount) {
                emitParticle(
                    centerX = centerX,
                    centerY = centerY,
                    sigmaAlong = 10f + speedFactor * 18f,
                    sigmaSide = 8f + speedFactor * 6f,
                    directionX = directionX,
                    directionY = directionY,
                    color = command.color,
                    radiusPx = logNormal(profile.coreMedianRadiusPx * 0.72f, 0.26f),
                    alpha = profile.coreAlpha * 0.86f * command.color.alpha,
                    stretch = 1.1f + speedFactor * profile.moveStretchBoost,
                    noiseScale = noiseScaleFor(command.style, 64f),
                    profile = profile
                )
            }
            emitRadialSplashDroplets(
                centerX = centerX,
                centerY = centerY,
                color = command.color,
                style = command.style,
                profile = profile,
                count = (count * 0.52f).toInt().coerceAtLeast(2),
                spreadPx = 30f + speedFactor * 54f,
                speedFactor = speedFactor
            )
        }
    }

    private fun emitPaintSplashUp(command: SprayPaintInputCommand.Spray, params: SprayPaintStepParams) {
        val profile = styleProfile(command.style)
        emitRadialSplashDroplets(
            centerX = command.x,
            centerY = command.y,
            color = command.color,
            style = command.style,
            profile = profile,
            count = poissonLike(params.upParticleCount.toFloat() * 1.2f),
            spreadPx = 42f * params.mistScale,
            speedFactor = 0.2f
        )
    }

    private fun emitGraffitiDown(command: SprayPaintInputCommand.Spray, params: SprayPaintStepParams) {
        val profile = styleProfile(command.style)
        emitAerosolBurst(
            centerX = command.x,
            centerY = command.y,
            directionX = 1f,
            directionY = 0f,
            color = saturate(command.color, 1.28f),
            style = command.style,
            profile = profile,
            count = poissonLike(params.tapParticleCount.toFloat() * profile.tapCountScale),
            params = params,
            speedFactor = 0.24f
        )
    }

    private fun emitGraffitiMove(command: SprayPaintInputCommand.Spray, params: SprayPaintStepParams) {
        emitAlongPath(command, params, spacingScale = 0.88f) { centerX, centerY, directionX, directionY, speedFactor, count ->
            val profile = styleProfile(command.style)
            emitAerosolBurst(
                centerX = centerX,
                centerY = centerY,
                directionX = directionX,
                directionY = directionY,
                color = graffitiColorFor(command.color),
                style = command.style,
                profile = profile,
                count = count,
                params = params,
                speedFactor = speedFactor
            )
        }
    }

    private fun emitGraffitiUp(command: SprayPaintInputCommand.Spray, params: SprayPaintStepParams) {
        val profile = styleProfile(command.style)
        emitSpeckleScatter(
            centerX = command.x,
            centerY = command.y,
            color = darken(command.color, 0.35f),
            style = command.style,
            profile = profile,
            count = poissonLike(params.upParticleCount.toFloat() * 1.25f),
            radiusScale = 0.74f,
            alphaScale = 0.78f,
            params = params
        )
    }

    private fun emitLiquidPaintDown(
        command: SprayPaintInputCommand.Spray,
        params: SprayPaintStepParams
    ) {
        val profile = styleProfile(command.style)
        val count = poissonLike(params.tapParticleCount.toFloat() * profile.tapCountScale)
        repeat((count * 0.34f).toInt().coerceAtLeast(3)) {
            emitParticle(
                centerX = command.x,
                centerY = command.y,
                sigmaAlong = profile.coreSigmaPx,
                sigmaSide = profile.coreSigmaPx,
                directionX = gaussian() * 0.12f,
                directionY = 1f,
                color = if (random.nextFloat() < 0.32f) lighten(command.color, 0.1f) else command.color,
                radiusPx = logNormal(profile.coreMedianRadiusPx, 0.22f),
                alpha = profile.coreAlpha * command.color.alpha,
                stretch = 1.05f + random.nextFloat() * 0.34f,
                noiseScale = noiseScaleFor(command.style, 42f),
                profile = profile
            )
        }
        emitLiquidGlobules(
            centerX = command.x,
            centerY = command.y,
            directionX = 0f,
            directionY = 1f,
            color = command.color,
            count = (count * 0.44f).toInt().coerceAtLeast(4),
            speedFactor = 0.18f,
            params = params
        )
    }

    private fun emitLiquidPaintMove(
        command: SprayPaintInputCommand.Spray,
        params: SprayPaintStepParams
    ) {
        val dx = command.x - command.previousX
        val dy = command.y - command.previousY
        val distance = sqrt(dx * dx + dy * dy)
        if (distance <= 0.01f) return
        val directionX = dx / distance
        val directionY = dy / distance
        val speed = sqrt(command.velocityX * command.velocityX + command.velocityY * command.velocityY)
            .coerceIn(0f, MAX_MOVE_SPEED_PX_PER_MS)
        val speedFactor = speed / (speed + MOVE_SPEED_SOFTNESS_PX_PER_MS)
        val profile = styleProfile(command.style)
        val emitterSpacing = (MOVE_EMITTER_SPACING_PX / profile.moveCountScale * 1.8f)
            .coerceIn(18f, 42f)
        val emitterCount = ceil(distance / emitterSpacing)
            .toInt()
            .coerceIn(1, MAX_MOVE_EMITTERS.coerceAtMost(5))
        val totalCount = poissonLike(
            params.moveParticleCount *
                profile.moveCountScale *
                (0.54f + speedFactor * 0.72f)
        )
        val countPerEmitter = (totalCount / emitterCount).coerceAtLeast(2)

        repeat(emitterCount) { index ->
            val t = (index + random.nextFloat()) / emitterCount.toFloat()
            val centerX = command.previousX + dx * t
            val centerY = command.previousY + dy * t
            repeat(countPerEmitter.coerceAtMost(6)) {
                emitParticle(
                    centerX = centerX,
                    centerY = centerY,
                    sigmaAlong = profile.moveAlongBasePx + speedFactor * profile.moveAlongSpeedPx,
                    sigmaSide = profile.moveCoreSidePx,
                    directionX = directionX,
                    directionY = directionY,
                    color = if (random.nextFloat() < 0.18f) lighten(command.color, 0.12f) else command.color,
                    radiusPx = logNormal(profile.coreMedianRadiusPx * 0.76f, 0.28f),
                    alpha = profile.coreAlpha * 0.82f * command.color.alpha,
                    stretch = 1.3f + speedFactor * profile.moveStretchBoost,
                    noiseScale = noiseScaleFor(command.style, 48f),
                    profile = profile
                )
            }
            emitLiquidGlobules(
                centerX = centerX,
                centerY = centerY,
                directionX = directionX,
                directionY = directionY,
                color = command.color,
                count = (countPerEmitter * 0.8f).toInt().coerceAtLeast(1),
                speedFactor = speedFactor,
                params = params
            )
        }
    }

    private fun emitLiquidPaintUp(command: SprayPaintInputCommand.Spray, params: SprayPaintStepParams) {
        val profile = styleProfile(command.style)
        val count = poissonLike(params.upParticleCount.toFloat() * 0.9f)
        repeat(count.coerceAtMost(10)) {
            emitParticle(
                centerX = command.x,
                centerY = command.y,
                sigmaAlong = 12f,
                sigmaSide = 10f,
                directionX = gaussian() * 0.12f,
                directionY = 1f,
                color = command.color,
                radiusPx = logNormal(profile.speckleMedianRadiusPx, 0.34f),
                alpha = profile.speckleAlpha * command.color.alpha,
                stretch = 1.8f + random.nextFloat() * 1.2f,
                noiseScale = noiseScaleFor(command.style, 84f),
                profile = profile
            )
        }
    }

    private fun emitFlowerPetalsDown(
        command: SprayPaintInputCommand.Spray,
        params: SprayPaintStepParams
    ) {
        spawnPetals(
            centerX = command.x,
            centerY = command.y,
            touchVelocityX = 0f,
            touchVelocityY = 0f,
            color = command.color,
            count = (params.tapParticleCount * 0.08f).toInt().coerceIn(4, 14)
        )
    }

    private fun emitFlowerPetalsMove(
        command: SprayPaintInputCommand.Spray,
        params: SprayPaintStepParams
    ) {
        val dx = command.x - command.previousX
        val dy = command.y - command.previousY
        val distance = sqrt(dx * dx + dy * dy)
        if (distance <= 0.01f) return
        val emitterCount = ceil(distance / 32f).toInt().coerceIn(1, 4)
        repeat(emitterCount) { index ->
            val t = (index + random.nextFloat()) / emitterCount.toFloat()
            spawnPetals(
                centerX = command.previousX + dx * t,
                centerY = command.previousY + dy * t,
                touchVelocityX = command.velocityX,
                touchVelocityY = command.velocityY,
                color = command.color,
                count = (params.moveParticleCount * 0.10f).toInt().coerceIn(1, 5)
            )
        }
    }

    private fun emitFlowerPetalsUp(
        command: SprayPaintInputCommand.Spray,
        params: SprayPaintStepParams
    ) {
        spawnPetals(
            centerX = command.x,
            centerY = command.y,
            touchVelocityX = 0f,
            touchVelocityY = -0.08f,
            color = command.color,
            count = (params.upParticleCount * 0.2f).toInt().coerceIn(2, 8)
        )
    }

    private fun emitPopBursts(
        params: SprayPaintStepParams,
        dtSeconds: Float
    ) {
        val iterator = popBursts.iterator()
        while (iterator.hasNext()) {
            val burst = iterator.next()
            burst.ageSeconds += dtSeconds
            val progress = (burst.ageSeconds / POP_LIFE_SECONDS).coerceIn(0f, 1f)
            if (progress >= 1f) {
                iterator.remove()
                continue
            }
            val profile = styleProfile(burst.style)
            val count = poissonLike(
                params.popParticleCount *
                    profile.popCountScale *
                    (1f - progress) *
                    0.72f
            )
            val spread = profile.popStartSpreadPx + progress * profile.popEndSpreadPx
            when (profile.depositionModel) {
                SprayPaintDepositionModel.NoisyPaintSplat -> emitRadialSplashDroplets(
                    centerX = burst.x,
                    centerY = burst.y,
                    color = burst.color,
                    style = burst.style,
                    profile = profile,
                    count = (count * 0.72f).toInt().coerceAtLeast(1),
                    spreadPx = spread,
                    speedFactor = progress
                )

                SprayPaintDepositionModel.ViscousLiquid -> emitLiquidGlobules(
                    centerX = burst.x,
                    centerY = burst.y,
                    directionX = 0f,
                    directionY = 1f,
                    color = burst.color,
                    count = (count * 0.38f).toInt().coerceAtLeast(1),
                    speedFactor = progress,
                    params = params
                )

                SprayPaintDepositionModel.PetalPrimitive -> Unit
                else -> emitAerosolBurst(
                    centerX = burst.x,
                    centerY = burst.y,
                    directionX = 1f,
                    directionY = 0f,
                    color = burst.color,
                    style = burst.style,
                    profile = profile,
                    count = count,
                    params = params,
                    speedFactor = progress,
                    alphaScale = profile.popAlphaScale * (1f - progress)
                )
            }
        }
    }

    private fun emitLongPress(
        activePointers: List<SprayPaintActivePointer>,
        nowMillis: Long,
        dtSeconds: Float,
        params: SprayPaintStepParams
    ) {
        activePointers.forEach { pointer ->
            val stationaryMillis = pointer.stationaryDurationMillis(nowMillis)
            if (stationaryMillis < LONG_PRESS_START_MILLIS) return@forEach

            val profile = styleProfile(pointer.style)
            val count = consumeLongPressCount(pointer, params, profile, dtSeconds)
            when (pointer.style) {
                SprayPaintPaletteStyle.Spray -> emitAerosolBurst(
                    centerX = pointer.x,
                    centerY = pointer.y,
                    directionX = 1f,
                    directionY = 0f,
                    color = pointer.color,
                    style = pointer.style,
                    profile = profile,
                    count = count.coerceAtMost(18),
                    params = params,
                    speedFactor = 0f,
                    alphaScale = 0.64f
                )

                SprayPaintPaletteStyle.PaintSplash -> {
                    repeat(count.coerceAtMost(6)) {
                        emitParticle(
                            centerX = pointer.x,
                            centerY = pointer.y,
                            sigmaAlong = 7f,
                            sigmaSide = 7f,
                            directionX = gaussian() * 0.12f,
                            directionY = 1f,
                            color = pointer.color,
                            radiusPx = logNormal(profile.coreMedianRadiusPx * 0.52f, 0.24f),
                            alpha = profile.coreAlpha * 0.46f * pointer.color.alpha,
                            stretch = 1f + random.nextFloat() * 0.35f,
                            noiseScale = noiseScaleFor(pointer.style, 58f),
                            profile = profile
                        )
                    }
                }

                SprayPaintPaletteStyle.Graffiti -> {
                    emitAerosolBurst(
                        centerX = pointer.x,
                        centerY = pointer.y,
                        directionX = 1f,
                        directionY = 0f,
                        color = graffitiColorFor(pointer.color),
                        style = pointer.style,
                        profile = profile,
                        count = count.coerceAtMost(16),
                        params = params,
                        speedFactor = 0.28f,
                        alphaScale = 0.76f
                    )
                }

                SprayPaintPaletteStyle.LiquidPaint -> {
                    repeat(count.coerceAtMost(8)) {
                        emitParticle(
                            centerX = pointer.x,
                            centerY = pointer.y,
                            sigmaAlong = 6f,
                            sigmaSide = 6f,
                            directionX = gaussian() * 0.1f,
                            directionY = 1f,
                            color = if (random.nextFloat() < 0.28f) lighten(pointer.color, 0.1f) else pointer.color,
                            radiusPx = logNormal(profile.coreMedianRadiusPx * 0.62f, 0.24f),
                            alpha = profile.coreAlpha * 0.72f * pointer.color.alpha,
                            stretch = 1.2f + random.nextFloat() * 0.5f,
                            noiseScale = noiseScaleFor(pointer.style, 42f),
                            profile = profile
                        )
                    }
                }

                SprayPaintPaletteStyle.FlowerPetals -> {
                    if (count > 0) {
                        spawnPetals(
                            centerX = pointer.x,
                            centerY = pointer.y,
                            touchVelocityX = 0f,
                            touchVelocityY = -0.035f,
                            color = pointer.color,
                            count = count.coerceIn(1, 4)
                        )
                    }
                }
            }

            if (
                params.dripEnabled &&
                profile.dripChance > 0f &&
                stationaryMillis >= DRIP_START_MILLIS &&
                nowMillis >= (nextDripMillisByPointer[pointer.pointerId] ?: 0L) &&
                random.nextFloat() <= profile.dripChance
            ) {
                emitDrip(pointer, nowMillis, profile)
            }
        }
    }

    private fun consumeLongPressCount(
        pointer: SprayPaintActivePointer,
        params: SprayPaintStepParams,
        profile: StyleProfile,
        dtSeconds: Float
    ): Int {
        val residual = longPressResidualByPointer[pointer.pointerId] ?: 0f
        val target = residual + params.longPressParticlesPerSecond * profile.longPressScale * dtSeconds
        val count = target.toInt()
        longPressResidualByPointer[pointer.pointerId] = target - count
        return count
    }

    private fun emitDrip(
        pointer: SprayPaintActivePointer,
        nowMillis: Long,
        profile: StyleProfile
    ) {
        val dripCount = 1 + random.nextInt(3)
        repeat(dripCount) {
            val length = (28f + random.nextFloat() * 62f) * profile.dripLengthScale
            val x = pointer.x + gaussian() * 10f
            val y = pointer.y + 10f + length * 0.45f
            emitParticleBlobAt(
                x = x,
                y = y,
                radiusPx = if (pointer.style == SprayPaintPaletteStyle.LiquidPaint) {
                    7.2f + random.nextFloat() * 3.8f
                } else {
                    4.8f + random.nextFloat() * 2.4f
                },
                color = pointer.color,
                alpha = profile.speckleAlpha * 0.9f * pointer.color.alpha,
                directionX = 0f,
                directionY = 1f,
                stretch = 3.4f + length / 22f,
                noiseScale = noiseScaleFor(pointer.style, 92f),
                profile = profile
            )
        }
        nextDripMillisByPointer[pointer.pointerId] =
            nowMillis + ((360L + random.nextInt(420)) * profile.dripIntervalScale).toLong()
    }

    private fun styleProfile(style: SprayPaintPaletteStyle): StyleProfile {
        return when (style) {
            SprayPaintPaletteStyle.Spray -> sprayProfile
            SprayPaintPaletteStyle.PaintSplash -> paintSplashProfile
            SprayPaintPaletteStyle.Graffiti -> graffitiProfile
            SprayPaintPaletteStyle.LiquidPaint -> liquidPaintProfile
            SprayPaintPaletteStyle.FlowerPetals -> flowerPetalsProfile
        }
    }

    private fun mistColorFor(
        color: SprayPaintColor,
        style: SprayPaintPaletteStyle
    ): SprayPaintColor {
        return when (style) {
            SprayPaintPaletteStyle.Spray -> lighten(color, 0.22f)
            SprayPaintPaletteStyle.PaintSplash -> lighten(color, 0.08f)
            SprayPaintPaletteStyle.Graffiti -> saturate(lighten(color, 0.08f), 1.28f)
            SprayPaintPaletteStyle.LiquidPaint -> lighten(color, 0.12f)
            SprayPaintPaletteStyle.FlowerPetals -> mixToward(color, 1f, 0.82f, 0.92f, 0.34f)
        }
    }

    private fun speckleColorFor(
        color: SprayPaintColor,
        style: SprayPaintPaletteStyle
    ): SprayPaintColor {
        return when (style) {
            SprayPaintPaletteStyle.Spray -> lighten(color, 0.1f)
            SprayPaintPaletteStyle.PaintSplash -> {
                if (random.nextFloat() < 0.24f) darken(color, 0.2f) else saturate(color, 1.1f)
            }

            SprayPaintPaletteStyle.Graffiti -> {
                if (random.nextFloat() < 0.38f) darken(color, 0.76f) else saturate(color, 1.22f)
            }

            SprayPaintPaletteStyle.LiquidPaint -> saturate(color, 1.08f)
            SprayPaintPaletteStyle.FlowerPetals -> lighten(color, 0.2f)
        }
    }

    private fun emitAerosolBurst(
        centerX: Float,
        centerY: Float,
        directionX: Float,
        directionY: Float,
        color: SprayPaintColor,
        style: SprayPaintPaletteStyle,
        profile: StyleProfile,
        count: Int,
        params: SprayPaintStepParams,
        speedFactor: Float,
        alphaScale: Float = 1f
    ) {
        if (count <= 0) return
        val coreCount = (count * profile.coreShare).toInt().coerceAtLeast(0)
        val mistCount = (count * profile.mistShare).toInt().coerceAtLeast(0)
        val speckleCount = (count - coreCount - mistCount).coerceAtLeast(
            if (profile.speckleShare > 0f) 1 else 0
        )
        repeat(coreCount) {
            emitParticle(
                centerX = centerX,
                centerY = centerY,
                sigmaAlong = (profile.coreSigmaPx + speedFactor * profile.moveAlongBasePx) *
                    params.mistScale,
                sigmaSide = profile.coreSigmaPx,
                directionX = directionX,
                directionY = directionY,
                color = color,
                radiusPx = logNormal(profile.coreMedianRadiusPx, 0.34f),
                alpha = profile.coreAlpha * alphaScale * color.alpha,
                stretch = 1f + speedFactor * profile.moveStretchBoost,
                noiseScale = noiseScaleFor(style, 48f),
                profile = profile
            )
        }
        repeat(mistCount) {
            emitParticle(
                centerX = centerX,
                centerY = centerY,
                sigmaAlong = (profile.mistSigmaPx + speedFactor * profile.moveAlongSpeedPx) *
                    params.mistScale,
                sigmaSide = profile.moveMistSidePx.coerceAtLeast(profile.mistSigmaPx * 0.42f) *
                    params.mistScale,
                directionX = directionX,
                directionY = directionY,
                color = mistColorFor(color, style),
                radiusPx = logNormal(profile.mistMedianRadiusPx, 0.46f),
                alpha = profile.mistAlpha * alphaScale * color.alpha,
                stretch = 1f + speedFactor * profile.moveStretchBoost,
                noiseScale = noiseScaleFor(style, 76f),
                profile = profile
            )
        }
        emitSpeckleScatter(
            centerX = centerX,
            centerY = centerY,
            color = color,
            style = style,
            profile = profile,
            count = speckleCount,
            radiusScale = 1f,
            alphaScale = alphaScale,
            params = params,
            directionX = directionX,
            directionY = directionY,
            speedFactor = speedFactor
        )
        if (profile.graffitiAccentScale > 0f) {
            emitGraffitiAccents(
                centerX = centerX,
                centerY = centerY,
                directionX = directionX,
                directionY = directionY,
                color = color,
                count = (count * 0.28f * profile.graffitiAccentScale).toInt().coerceAtLeast(1),
                speedFactor = speedFactor,
                params = params
            )
        }
        if (profile.smokeScale > 0.45f) {
            emitSoftSmoke(
                centerX = centerX,
                centerY = centerY,
                color = color,
                style = style,
                count = (count * 0.12f * profile.smokeScale).toInt().coerceAtLeast(1),
                params = params
            )
        }
    }

    private inline fun emitAlongPath(
        command: SprayPaintInputCommand.Spray,
        params: SprayPaintStepParams,
        spacingScale: Float = 1f,
        emitAt: (
            centerX: Float,
            centerY: Float,
            directionX: Float,
            directionY: Float,
            speedFactor: Float,
            count: Int
        ) -> Unit
    ) {
        val dx = command.x - command.previousX
        val dy = command.y - command.previousY
        val distance = sqrt(dx * dx + dy * dy)
        if (distance <= 0.01f) return
        val directionX = dx / distance
        val directionY = dy / distance
        val speed = sqrt(command.velocityX * command.velocityX + command.velocityY * command.velocityY)
            .coerceIn(0f, MAX_MOVE_SPEED_PX_PER_MS)
        val speedFactor = speed / (speed + MOVE_SPEED_SOFTNESS_PX_PER_MS)
        val profile = styleProfile(command.style)
        val emitterSpacing = (MOVE_EMITTER_SPACING_PX / profile.moveCountScale * spacingScale)
            .coerceIn(8f, 46f)
        val emitterCount = ceil(distance / emitterSpacing)
            .toInt()
            .coerceIn(1, MAX_MOVE_EMITTERS)
        val totalCount = poissonLike(
            params.moveParticleCount *
                profile.moveCountScale *
                (0.68f + speedFactor * 1.28f)
        )
        val countPerEmitter = (totalCount / emitterCount).coerceAtLeast(1)
        repeat(emitterCount) { index ->
            val t = (index + random.nextFloat()) / emitterCount.toFloat()
            emitAt(
                command.previousX + dx * t,
                command.previousY + dy * t,
                directionX,
                directionY,
                speedFactor,
                countPerEmitter
            )
        }
    }

    private fun emitSpeckleScatter(
        centerX: Float,
        centerY: Float,
        color: SprayPaintColor,
        style: SprayPaintPaletteStyle,
        profile: StyleProfile,
        count: Int,
        radiusScale: Float,
        alphaScale: Float,
        params: SprayPaintStepParams,
        directionX: Float = 1f,
        directionY: Float = 0f,
        speedFactor: Float = 0f
    ) {
        repeat(count.coerceAtLeast(0)) {
            emitParticle(
                centerX = centerX,
                centerY = centerY,
                sigmaAlong = (profile.speckleSigmaPx + speedFactor * 34f) * params.mistScale,
                sigmaSide = profile.speckleSigmaPx * 0.72f * params.mistScale,
                directionX = directionX,
                directionY = directionY,
                color = speckleColorFor(color, style),
                radiusPx = logNormal(profile.speckleMedianRadiusPx * radiusScale, 0.55f),
                alpha = profile.speckleAlpha * alphaScale * color.alpha,
                stretch = 1f + speedFactor * profile.moveStretchBoost * 0.55f,
                noiseScale = noiseScaleFor(style, 96f),
                profile = profile
            )
        }
    }

    private fun emitRadialSplashDroplets(
        centerX: Float,
        centerY: Float,
        color: SprayPaintColor,
        style: SprayPaintPaletteStyle,
        profile: StyleProfile,
        count: Int,
        spreadPx: Float,
        speedFactor: Float
    ) {
        repeat(count.coerceAtLeast(0)) {
            val angle = random.nextFloat() * TWO_PI
            val distance = gammaRadius(spreadPx) * (0.58f + random.nextFloat() * 0.78f)
            val dropletColor = speckleColorFor(color, style)
            emitParticleBlobAt(
                x = centerX + cos(angle) * distance,
                y = centerY + sin(angle) * distance,
                radiusPx = logNormal(profile.speckleMedianRadiusPx * (1f + speedFactor), 0.42f),
                color = dropletColor,
                alpha = profile.speckleAlpha * (0.82f + random.nextFloat() * 0.42f) * color.alpha,
                directionX = cos(angle),
                directionY = sin(angle),
                stretch = 1f + random.nextFloat() * (0.8f + speedFactor),
                noiseScale = noiseScaleFor(style, 112f),
                profile = profile
            )
        }
    }

    private fun emitGraffitiAccents(
        centerX: Float,
        centerY: Float,
        directionX: Float,
        directionY: Float,
        color: SprayPaintColor,
        count: Int,
        speedFactor: Float,
        params: SprayPaintStepParams
    ) {
        val shadowColor = darken(color, 0.82f)
        repeat(count) {
            val shadow = random.nextFloat() < 0.42f
            emitParticle(
                centerX = centerX,
                centerY = centerY,
                sigmaAlong = (44f + speedFactor * 42f) * params.mistScale,
                sigmaSide = (28f + speedFactor * 18f) * params.mistScale,
                directionX = directionX,
                directionY = directionY,
                color = if (shadow) shadowColor else saturate(color, 1.28f),
                radiusPx = if (shadow) {
                    logNormal(median = 2.4f, sigma = 0.48f)
                } else {
                    logNormal(median = 1.9f, sigma = 0.58f)
                },
                alpha = (if (shadow) 0.046f else 0.082f) * color.alpha,
                stretch = if (shadow) {
                    1.7f + speedFactor * 3.4f
                } else {
                    1.1f + speedFactor * 2.2f
                },
                noiseScale = 142f,
                profile = graffitiProfile
            )
        }
        if (random.nextFloat() < 0.68f) {
            repeat(1 + random.nextInt(2)) {
                val verticalLean = 0.55f + random.nextFloat() * 0.42f
                emitParticle(
                    centerX = centerX + gaussian() * 8f,
                    centerY = centerY + 14f + random.nextFloat() * 22f,
                    sigmaAlong = 10f,
                    sigmaSide = 6f,
                    directionX = directionX * 0.32f,
                    directionY = verticalLean,
                    color = darken(color, 0.38f),
                    radiusPx = logNormal(median = 3.4f, sigma = 0.34f),
                    alpha = 0.038f * color.alpha,
                    stretch = 3.4f + random.nextFloat() * 2.2f,
                    noiseScale = 126f,
                    profile = graffitiProfile
                )
            }
        }
    }

    private fun emitLiquidGlobules(
        centerX: Float,
        centerY: Float,
        directionX: Float,
        directionY: Float,
        color: SprayPaintColor,
        count: Int,
        speedFactor: Float,
        params: SprayPaintStepParams
    ) {
        val profile = styleProfile(currentStyle)
        repeat(count) {
            emitParticle(
                centerX = centerX,
                centerY = centerY,
                sigmaAlong = (22f + speedFactor * 28f) * params.mistScale,
                sigmaSide = (18f + speedFactor * 10f) * params.mistScale,
                directionX = directionX,
                directionY = directionY,
                color = if (random.nextFloat() < 0.44f) lighten(color, 0.18f) else saturate(color, 1.16f),
                radiusPx = logNormal(median = profile.speckleMedianRadiusPx.coerceAtLeast(9f), sigma = 0.32f),
                alpha = profile.speckleAlpha * 0.76f * color.alpha,
                stretch = 1.1f + speedFactor * 1.8f,
                noiseScale = 46f,
                profile = profile
            )
        }
    }

    private fun spawnPetals(
        centerX: Float,
        centerY: Float,
        touchVelocityX: Float,
        touchVelocityY: Float,
        color: SprayPaintColor,
        count: Int
    ) {
        repeat(count.coerceAtLeast(0)) {
            while (petalSprites.size >= MAX_PETAL_SPRITES) {
                petalSprites.removeAt(0)
            }
            val angle = random.nextFloat() * TWO_PI
            val petalColor = if (random.nextFloat() < 0.5f) {
                mixToward(color, 1f, 0.72f, 0.9f, 0.44f)
            } else {
                mixToward(color, 0.78f, 0.7f, 1f, 0.38f)
            }
            val swirl = 42f + random.nextFloat() * 74f
            petalSprites.add(
                PetalSprite(
                    x = centerX + gaussian() * 10f,
                    y = centerY + gaussian() * 8f,
                    velocityX = touchVelocityX * 170f + cos(angle) * swirl + gaussian() * 22f,
                    velocityY = touchVelocityY * 120f + sin(angle) * swirl * 0.55f - 24f - random.nextFloat() * 48f,
                    angle = angle,
                    angularVelocity = (random.nextFloat() - 0.5f) * 5.8f,
                    color = petalColor,
                    scale = 0.82f + random.nextFloat() * 0.9f,
                    phase = random.nextFloat() * TWO_PI,
                    lifetimeSeconds = 0.9f + random.nextFloat() * 0.85f,
                    seed = random.nextFloat() * 1000f
                )
            )
        }
    }

    private fun advancePetalSprites(dtSeconds: Float, params: SprayPaintStepParams) {
        if (petalSprites.isEmpty()) return
        val profile = flowerPetalsProfile
        val iterator = petalSprites.iterator()
        while (iterator.hasNext()) {
            val petal = iterator.next()
            petal.ageSeconds += dtSeconds
            val progress = (petal.ageSeconds / petal.lifetimeSeconds).coerceIn(0f, 1f)
            if (progress >= 1f) {
                iterator.remove()
                continue
            }
            val flutter = sin(timeSeconds * 8.5f + petal.phase) * 18f
            petal.velocityY += PETAL_GRAVITY_PX_PER_SECOND * dtSeconds
            petal.x += (petal.velocityX + flutter) * dtSeconds
            petal.y += petal.velocityY * dtSeconds
            petal.angle += petal.angularVelocity * dtSeconds
            val fade = sin((1f - progress) * 1.5707964f).coerceIn(0f, 1f)
            emitParticleBlobAt(
                x = petal.x,
                y = petal.y,
                radiusPx = (7.6f + petal.scale * 3.8f) * params.mistScale,
                color = petal.color,
                alpha = profile.coreAlpha * fade * petal.color.alpha,
                directionX = cos(petal.angle),
                directionY = sin(petal.angle),
                stretch = 2.4f + petal.scale * 1.6f,
                noiseScale = 42f,
                profile = profile,
                seed = petal.seed
            )
        }
    }

    private fun emitSoftSmoke(
        centerX: Float,
        centerY: Float,
        color: SprayPaintColor,
        style: SprayPaintPaletteStyle,
        count: Int,
        params: SprayPaintStepParams
    ) {
        repeat(count) {
            emitParticle(
                centerX = centerX,
                centerY = centerY,
                sigmaAlong = 66f * params.mistScale,
                sigmaSide = 54f * params.mistScale,
                directionX = 1f,
                directionY = 0f,
                color = mistColorFor(color, style),
                radiusPx = logNormal(median = 10.2f, sigma = 0.4f),
                alpha = 0.0068f * color.alpha,
                stretch = 1.2f + random.nextFloat() * 0.8f,
                noiseScale = noiseScaleFor(style, 52f),
                profile = styleProfile(style)
            )
        }
    }

    private fun noiseScaleFor(style: SprayPaintPaletteStyle, base: Float): Float {
        return when (style) {
            SprayPaintPaletteStyle.Spray -> base * 0.86f
            SprayPaintPaletteStyle.PaintSplash -> base * 1.42f
            SprayPaintPaletteStyle.Graffiti -> base * 1.55f
            SprayPaintPaletteStyle.LiquidPaint -> base * 0.72f
            SprayPaintPaletteStyle.FlowerPetals -> base * 0.58f
        }
    }

    private fun graffitiColorFor(color: SprayPaintColor): SprayPaintColor {
        return when {
            random.nextFloat() < 0.35f -> darken(color, 0.35f)
            random.nextFloat() < 0.77f -> saturate(color, 1.35f)
            else -> lighten(color, 0.25f)
        }
    }

    private fun gammaRadius(scalePx: Float): Float {
        val u1 = (1f - random.nextFloat()).coerceAtLeast(0.0001f)
        val u2 = (1f - random.nextFloat()).coerceAtLeast(0.0001f)
        return (-ln(u1 * u2) * scalePx * 0.42f).coerceAtMost(scalePx * 2.8f)
    }

    private fun lighten(color: SprayPaintColor, amount: Float): SprayPaintColor {
        return mixToward(color, 1f, 1f, 1f, amount)
    }

    private fun darken(color: SprayPaintColor, amount: Float): SprayPaintColor {
        return SprayPaintColor(
            red = (color.red * (1f - amount)).coerceIn(0f, 1f),
            green = (color.green * (1f - amount)).coerceIn(0f, 1f),
            blue = (color.blue * (1f - amount)).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }

    private fun saturate(color: SprayPaintColor, amount: Float): SprayPaintColor {
        val luma = color.red * 0.2126f + color.green * 0.7152f + color.blue * 0.0722f
        return SprayPaintColor(
            red = (luma + (color.red - luma) * amount).coerceIn(0f, 1f),
            green = (luma + (color.green - luma) * amount).coerceIn(0f, 1f),
            blue = (luma + (color.blue - luma) * amount).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }

    private fun mixToward(
        color: SprayPaintColor,
        targetRed: Float,
        targetGreen: Float,
        targetBlue: Float,
        amount: Float
    ): SprayPaintColor {
        val clamped = amount.coerceIn(0f, 1f)
        return SprayPaintColor(
            red = (color.red + (targetRed - color.red) * clamped).coerceIn(0f, 1f),
            green = (color.green + (targetGreen - color.green) * clamped).coerceIn(0f, 1f),
            blue = (color.blue + (targetBlue - color.blue) * clamped).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }

    private fun emitParticle(
        centerX: Float,
        centerY: Float,
        sigmaAlong: Float,
        sigmaSide: Float,
        directionX: Float,
        directionY: Float,
        color: SprayPaintColor,
        radiusPx: Float,
        alpha: Float,
        stretch: Float,
        noiseScale: Float,
        profile: StyleProfile
    ) {
        if (particlesEmittedThisFrame >= maxParticlesThisFrame) return
        val (offsetAlong, offsetSide) = gaussianPair()
        val perpX = -directionY
        val perpY = directionX
        val x = centerX + directionX * offsetAlong * sigmaAlong + perpX * offsetSide * sigmaSide
        val y = centerY + directionY * offsetAlong * sigmaAlong + perpY * offsetSide * sigmaSide
        emitParticleBlobAt(
            x = x,
            y = y,
            radiusPx = radiusPx,
            color = color,
            alpha = alpha,
            directionX = directionX,
            directionY = directionY,
            stretch = stretch,
            noiseScale = noiseScale,
            profile = profile
        )
    }

    private fun emitParticleBlobAt(
        x: Float,
        y: Float,
        radiusPx: Float,
        color: SprayPaintColor,
        alpha: Float,
        directionX: Float,
        directionY: Float,
        stretch: Float,
        noiseScale: Float,
        profile: StyleProfile,
        seed: Float = random.nextFloat() * 1000f
    ) {
        emitParticleAt(
            Particle(
                x = x,
                y = y,
                radiusPx = radiusPx,
                red = color.red,
                green = color.green,
                blue = color.blue,
                alpha = alpha,
                directionX = directionX,
                directionY = directionY,
                stretch = stretch,
                noiseScale = noiseScale,
                seed = seed,
                depositionModel = profile.depositionModel,
                compositeModel = profile.compositeModel,
                maxParticleAlpha = profile.maxParticleAlpha
            )
        )
    }

    private fun emitParticleAt(particle: Particle) {
        val target = paintAccumulationTexture ?: return
        if (particlesEmittedThisFrame >= maxParticlesThisFrame) return
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return
        if (
            particle.x < -MAX_OFFSCREEN_PARTICLE_PX ||
            particle.y < -MAX_OFFSCREEN_PARTICLE_PX ||
            particle.x > surfaceWidth + MAX_OFFSCREEN_PARTICLE_PX ||
            particle.y > surfaceHeight + MAX_OFFSCREEN_PARTICLE_PX
        ) {
            return
        }

        val centerX = (particle.x / surfaceWidth).coerceIn(-0.15f, 1.15f)
        val centerY = (1f - particle.y / surfaceHeight).coerceIn(-0.15f, 1.15f)
        val longSide = max(surfaceWidth, surfaceHeight).toFloat()
        val radius = (particle.radiusPx / longSide).coerceIn(0.0012f, 0.08f)
        val stretch = particle.stretch.coerceIn(1f, 7.2f)
        val quadRadiusX = (radius * stretch * surfaceWidth.coerceAtLeast(surfaceHeight) / surfaceWidth)
            .coerceAtMost(0.32f)
        val quadRadiusY = (radius * stretch * surfaceWidth.coerceAtLeast(surfaceHeight) / surfaceHeight)
            .coerceAtMost(0.32f)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebuffer)
        GLES30.glViewport(0, 0, target.width, target.height)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        applyBlendMode(particle.compositeModel)
        GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
        applyParticleScissor(centerX, centerY, quadRadiusX, quadRadiusY, target)

        GLES30.glUseProgram(depositionProgram)
        bindTexture(0, noiseTexture, depositionProgram, "uNoise")
        uniform2f(depositionProgram, "uCenter", centerX, centerY)
        uniform2f(depositionProgram, "uQuadRadius", quadRadiusX, quadRadiusY)
        uniform1f(depositionProgram, "uRadius", radius)
        uniform1f(depositionProgram, "uAspect", target.width.toFloat() / target.height.toFloat())
        uniform2f(depositionProgram, "uDirection", particle.directionX, -particle.directionY)
        uniform1f(depositionProgram, "uStretch", stretch)
        uniform4f(
            depositionProgram,
            "uColor",
            particle.red,
            particle.green,
            particle.blue,
            particle.alpha.coerceIn(0f, particle.maxParticleAlpha)
        )
        uniform1f(depositionProgram, "uShapeMode", shapeModeFor(particle.depositionModel))
        uniform1f(depositionProgram, "uMaxParticleAlpha", particle.maxParticleAlpha)
        uniform1f(depositionProgram, "uNoiseScale", particle.noiseScale)
        uniform1f(depositionProgram, "uSeed", particle.seed)
        drawQuad()

        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        particlesEmittedThisFrame += 1
        energyEstimate = (energyEstimate + particle.alpha * 0.95f).coerceAtMost(MAX_ENERGY_ESTIMATE)
    }

    private fun applyBlendMode(model: SprayPaintCompositeModel) {
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
        when (model) {
            SprayPaintCompositeModel.AdditiveMist -> {
                GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
            }

            SprayPaintCompositeModel.AlphaPaint,
            SprayPaintCompositeModel.WetHeightPaint,
            SprayPaintCompositeModel.PetalAlpha -> {
                GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            }
        }
    }

    private fun shapeModeFor(model: SprayPaintDepositionModel): Float {
        return when (model) {
            SprayPaintDepositionModel.AerosolGaussian -> 0f
            SprayPaintDepositionModel.NoisyPaintSplat -> 1f
            SprayPaintDepositionModel.DirectionalGraffiti -> 2f
            SprayPaintDepositionModel.ViscousLiquid -> 3f
            SprayPaintDepositionModel.PetalPrimitive -> 4f
        }
    }

    private fun applyParticleScissor(
        centerX: Float,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
        target: RenderTarget
    ) {
        val left = ((centerX - radiusX) * target.width).toInt().coerceIn(0, target.width)
        val right = ((centerX + radiusX) * target.width).toInt().coerceIn(0, target.width)
        val bottom = ((centerY - radiusY) * target.height).toInt().coerceIn(0, target.height)
        val top = ((centerY + radiusY) * target.height).toInt().coerceIn(0, target.height)
        GLES30.glScissor(
            left,
            bottom,
            (right - left).coerceAtLeast(1),
            (top - bottom).coerceAtLeast(1)
        )
    }

    private fun decayPaint(
        params: SprayPaintStepParams,
        dtSeconds: Float,
        profile: StyleProfile
    ) {
        val source = paintAccumulationTexture ?: return
        val destination = temporaryTexture ?: return
        val factor = effectiveDecayPerSecond(params, profile).pow(dtSeconds).coerceIn(0.70f, 0.999f)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, destination.framebuffer)
        GLES30.glViewport(0, 0, destination.width, destination.height)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(decayProgram)
        bindTexture(0, source.texture, decayProgram, "uPaint")
        uniform1f(decayProgram, "uDecay", factor)
        drawQuad()
        paintAccumulationTexture = destination
        temporaryTexture = source
    }

    private fun composite(params: SprayPaintStepParams, profile: StyleProfile) {
        val paint = paintAccumulationTexture ?: return
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(compositeProgram)
        bindTexture(0, paint.texture, compositeProgram, "uPaint")
        uniform2f(compositeProgram, "uTexelSize", 1f / paint.width, 1f / paint.height)
        uniform1f(compositeProgram, "uTime", timeSeconds)
        uniform1f(compositeProgram, "uShineMode", params.shineMode.toFloat())
        uniform1f(compositeProgram, "uShineStrength", params.shineStrength * profile.wetShine)
        uniform1f(compositeProgram, "uMaxDisplayAlpha", profile.maxDisplayAlpha)
        uniform1f(compositeProgram, "uEdgeDarkening", profile.edgeDarkening)
        uniform1f(compositeProgram, "uCompositeModel", compositeModeFor(profile.compositeModel))
        drawQuad()
    }

    private fun compositeModeFor(model: SprayPaintCompositeModel): Float {
        return when (model) {
            SprayPaintCompositeModel.AdditiveMist -> 0f
            SprayPaintCompositeModel.AlphaPaint -> 1f
            SprayPaintCompositeModel.WetHeightPaint -> 2f
            SprayPaintCompositeModel.PetalAlpha -> 3f
        }
    }

    private fun copyTextureToTarget(texture: Int, target: RenderTarget?) {
        if (target == null) return
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebuffer)
        GLES30.glViewport(0, 0, target.width, target.height)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glUseProgram(copyProgram)
        bindTexture(0, texture, copyProgram, "uTexture")
        drawQuad()
    }

    private fun clearTarget(target: RenderTarget?) {
        if (target == null) return
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebuffer)
        GLES30.glViewport(0, 0, target.width, target.height)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    private fun createTarget(width: Int, height: Int): RenderTarget {
        val textures = IntArray(1)
        val framebuffers = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA8,
            width,
            height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null
        )

        GLES30.glGenFramebuffers(1, framebuffers, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffers[0])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            textures[0],
            0
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            releaseTextureAndFramebuffer(textures[0], framebuffers[0])
            error("Spray paint framebuffer incomplete: 0x${status.toString(16)}")
        }
        return RenderTarget(
            texture = textures[0],
            framebuffer = framebuffers[0],
            width = width,
            height = height
        )
    }

    private fun createNoiseTexture(): Int {
        val size = NOISE_SIZE
        val bytes = ByteArray(size * size)
        random.nextBytes(bytes)
        val buffer = ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .put(bytes)
            .apply { position(0) }

        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_REPEAT
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_REPEAT
        )
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_R8,
            size,
            size,
            0,
            GLES30.GL_RED,
            GLES30.GL_UNSIGNED_BYTE,
            buffer
        )
        return textures[0]
    }

    private fun releaseTarget(target: RenderTarget?) {
        if (target == null) return
        releaseTextureAndFramebuffer(target.texture, target.framebuffer)
    }

    private fun releaseTextureAndFramebuffer(texture: Int, framebuffer: Int) {
        GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
        GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
    }

    private fun bindTexture(unit: Int, texture: Int, program: Int, uniform: String) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, uniform), unit)
    }

    private fun uniform1f(program: Int, name: String, value: Float) {
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, name), value)
    }

    private fun uniform2f(program: Int, name: String, x: Float, y: Float) {
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program, name), x, y)
    }

    private fun uniform4f(
        program: Int,
        name: String,
        x: Float,
        y: Float,
        z: Float,
        w: Float
    ) {
        GLES30.glUniform4f(GLES30.glGetUniformLocation(program, name), x, y, z, w)
    }

    private fun drawQuad() {
        quadVertices.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(
            0,
            2,
            GLES30.GL_FLOAT,
            false,
            0,
            quadVertices
        )
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(0)
    }

    private fun createPrograms() {
        depositionProgram = createProgram(DEPOSITION_VERTEX_SHADER, DEPOSITION_FRAGMENT_SHADER)
        decayProgram = createProgram(FULLSCREEN_VERTEX_SHADER, DECAY_FRAGMENT_SHADER)
        compositeProgram = createProgram(FULLSCREEN_VERTEX_SHADER, COMPOSITE_FRAGMENT_SHADER)
        copyProgram = createProgram(FULLSCREEN_VERTEX_SHADER, COPY_FRAGMENT_SHADER)
    }

    private fun createProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderSource)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glBindAttribLocation(program, 0, "aPosition")
        GLES30.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            error("Spray paint shader program link failed: $log")
        }
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source.trimIndent())
        GLES30.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            error("Spray paint shader compile failed: $log")
        }
        return shader
    }

    private fun poissonLike(mean: Float): Int {
        if (mean <= 0f) return 0
        val jitter = gaussian() * sqrt(mean.coerceAtLeast(1f)) * 0.72f
        return (mean + jitter).toInt().coerceAtLeast(1)
    }

    private fun logNormal(median: Float, sigma: Float): Float {
        return exp(ln(median.coerceAtLeast(0.1f)) + sigma * gaussian())
            .coerceIn(1.2f, 42f)
    }

    private fun gaussianPair(): Pair<Float, Float> {
        val angle = random.nextFloat() * TWO_PI
        val radius = sqrt(-2f * ln((1f - random.nextFloat()).coerceAtLeast(0.0001f)))
        return Pair(cos(angle) * radius, sin(angle) * radius)
    }

    private fun gaussian(): Float {
        return random.nextGaussian().toFloat().coerceIn(-3.2f, 3.2f)
    }

    companion object {
        private const val DEFAULT_IDLE_ENERGY_THRESHOLD = 0.012f
        private const val MAX_ENERGY_ESTIMATE = 2.4f
        private const val MIN_DT_SECONDS = 1f / 120f
        private const val MAX_DT_SECONDS = 1f / 20f
        private const val TIME_WRAP_SECONDS = 240f
        private const val TWO_PI = 6.2831855f
        private const val MOVE_EMITTER_SPACING_PX = 17f
        private const val MAX_MOVE_EMITTERS = 9
        private const val MAX_MOVE_SPEED_PX_PER_MS = 2.2f
        private const val MOVE_SPEED_SOFTNESS_PX_PER_MS = 0.72f
        private const val POP_LIFE_SECONDS = 0.15f
        private const val LONG_PRESS_START_MILLIS = 220L
        private const val DRIP_START_MILLIS = 650L
        private const val MAX_OFFSCREEN_PARTICLE_PX = 140f
        private const val MAX_HARD_PARTICLES_PER_FRAME = 900
        private const val MAX_PETAL_SPRITES = 96
        private const val PETAL_GRAVITY_PX_PER_SECOND = 58f
        private const val NOISE_SIZE = 64

        private val QUAD = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
        )

        private const val DEPOSITION_VERTEX_SHADER = """
            #version 300 es
            precision highp float;
            layout(location = 0) in vec2 aPosition;
            uniform vec2 uCenter;
            uniform vec2 uQuadRadius;
            out vec2 vUv;
            out vec2 vLocal;
            void main() {
                vLocal = aPosition;
                vUv = uCenter + aPosition * uQuadRadius;
                gl_Position = vec4(vUv * 2.0 - 1.0, 0.0, 1.0);
            }
        """

        private const val DEPOSITION_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            in vec2 vLocal;
            uniform sampler2D uNoise;
            uniform vec2 uCenter;
            uniform float uRadius;
            uniform float uAspect;
            uniform vec2 uDirection;
            uniform float uStretch;
            uniform vec4 uColor;
            uniform float uShapeMode;
            uniform float uMaxParticleAlpha;
            uniform float uNoiseScale;
            uniform float uSeed;
            out vec4 fragColor;
            void main() {
                vec2 direction = normalize(uDirection + vec2(0.0001, 0.0));
                vec2 perpendicular = vec2(-direction.y, direction.x);
                vec2 delta = vUv - uCenter;
                delta.x *= uAspect;
                float along = dot(delta, direction) / max(uRadius * uStretch, 0.0001);
                float side = dot(delta, perpendicular) / max(uRadius, 0.0001);
                float dist2 = along * along + side * side;
                float gaussian = exp(-dist2 * 0.5);
                float radial = sqrt(dist2);
                float noise = texture(
                    uNoise,
                    vUv * uNoiseScale + vec2(uSeed * 0.031, uSeed * 0.017)
                ).r;
                float shape = gaussian;
                if (uShapeMode > 0.5 && uShapeMode < 1.5) {
                    float irregularRadius = 0.92 + (noise - 0.5) * 0.46;
                    float core = 1.0 - smoothstep(0.08, irregularRadius, radial);
                    float ragged = mix(0.76, 1.28, noise);
                    shape = max(core * ragged, gaussian * 0.28);
                } else if (uShapeMode > 1.5 && uShapeMode < 2.5) {
                    float streak = exp(-(along * along * 0.34 + side * side * 1.35));
                    float grit = step(0.58, noise) * smoothstep(0.38, 1.6, radial);
                    shape = max(streak * mix(0.78, 1.18, noise), grit * 0.72);
                } else if (uShapeMode > 2.5 && uShapeMode < 3.5) {
                    float film = 1.0 - smoothstep(0.0, 1.16 + (noise - 0.5) * 0.18, radial);
                    shape = max(film, gaussian * 0.36);
                } else if (uShapeMode > 3.5) {
                    float petal = exp(-(
                        pow(abs(along) * 0.78, 2.6) +
                        pow(abs(side) * 1.72, 2.0)
                    ));
                    float taper = smoothstep(-1.25, -0.35, along) *
                        (1.0 - smoothstep(0.72, 1.48, along));
                    float vein = 1.0 - smoothstep(0.0, 0.09, abs(side)) * 0.22;
                    shape = petal * max(taper, 0.16) * vein;
                }
                float edgeMask = smoothstep(0.32, 1.15, radial);
                float edge = mix(1.0, mix(0.72, 1.28, noise), edgeMask);
                float alpha = clamp(uColor.a * shape * edge, 0.0, uMaxParticleAlpha);
                if (alpha < 0.001) {
                    discard;
                }
                fragColor = vec4(uColor.rgb * alpha, alpha);
            }
        """

        private const val FULLSCREEN_VERTEX_SHADER = """
            #version 300 es
            precision highp float;
            layout(location = 0) in vec2 aPosition;
            out vec2 vUv;
            void main() {
                vUv = aPosition * 0.5 + 0.5;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        private const val DECAY_FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            in vec2 vUv;
            uniform sampler2D uPaint;
            uniform float uDecay;
            out vec4 fragColor;
            void main() {
                vec4 paint = texture(uPaint, vUv);
                float alpha = max(paint.a * uDecay - 0.00012, 0.0);
                vec3 color = paint.rgb * uDecay;
                fragColor = vec4(min(color, vec3(alpha + 0.18)), alpha);
            }
        """

        private const val COPY_FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            uniform sampler2D uTexture;
            in vec2 vUv;
            out vec4 fragColor;
            void main() {
                fragColor = texture(uTexture, vUv);
            }
        """

        private const val COMPOSITE_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uPaint;
            uniform vec2 uTexelSize;
            uniform float uTime;
            uniform float uShineMode;
            uniform float uShineStrength;
            uniform float uMaxDisplayAlpha;
            uniform float uEdgeDarkening;
            uniform float uCompositeModel;
            out vec4 fragColor;
            void main() {
                vec4 paint = texture(uPaint, vUv);
                float a = clamp(paint.a, 0.0, 1.0);
                if (a <= 0.001) {
                    fragColor = vec4(0.0);
                    return;
                }
                vec3 color = paint.rgb / max(a, 0.001);
                float response = 0.92;
                if (uCompositeModel > 1.5 && uCompositeModel < 2.5) {
                    response = 0.68;
                } else if (uCompositeModel > 2.5) {
                    response = 0.58;
                }
                float displayAlpha = min(
                    uMaxDisplayAlpha,
                    smoothstep(0.0, response, a) * uMaxDisplayAlpha
                );
                if (uCompositeModel < 0.5) {
                    float aerosolAlpha = (1.0 - exp(-a * 7.5)) * uMaxDisplayAlpha;
                    displayAlpha = min(uMaxDisplayAlpha, aerosolAlpha);
                }

                float aL = texture(uPaint, vUv - vec2(uTexelSize.x, 0.0)).a;
                float aR = texture(uPaint, vUv + vec2(uTexelSize.x, 0.0)).a;
                float aD = texture(uPaint, vUv - vec2(0.0, uTexelSize.y)).a;
                float aU = texture(uPaint, vUv + vec2(0.0, uTexelSize.y)).a;
                vec2 gradient = vec2(aL - aR, aD - aU);
                if (uShineMode > 1.5) {
                    float aL2 = texture(uPaint, vUv - vec2(uTexelSize.x * 2.0, 0.0)).a;
                    float aR2 = texture(uPaint, vUv + vec2(uTexelSize.x * 2.0, 0.0)).a;
                    float aD2 = texture(uPaint, vUv - vec2(0.0, uTexelSize.y * 2.0)).a;
                    float aU2 = texture(uPaint, vUv + vec2(0.0, uTexelSize.y * 2.0)).a;
                    gradient = gradient * 0.76 + vec2(aL2 - aR2, aD2 - aU2) * 0.16;
                }
                if (uShineMode > 2.5) {
                    float aLD = texture(uPaint, vUv + vec2(-uTexelSize.x, -uTexelSize.y)).a;
                    float aLU = texture(uPaint, vUv + vec2(-uTexelSize.x, uTexelSize.y)).a;
                    float aRD = texture(uPaint, vUv + vec2(uTexelSize.x, -uTexelSize.y)).a;
                    float aRU = texture(uPaint, vUv + vec2(uTexelSize.x, uTexelSize.y)).a;
                    vec2 sobel = vec2(
                        (aLD + 2.0 * aL + aLU) - (aRD + 2.0 * aR + aRU),
                        (aLD + 2.0 * aD + aRD) - (aLU + 2.0 * aU + aRU)
                    ) * 0.25;
                    gradient = mix(gradient, sobel, 0.48);
                }
                vec3 normal = normalize(vec3(-gradient * 18.0, 1.0));
                vec3 light = normalize(vec3(-0.32, 0.44, 0.84));
                vec3 reflected = reflect(-light, normal);
                float wet = smoothstep(0.18, 0.88, a);
                float specular = pow(max(dot(reflected, vec3(0.0, 0.0, 1.0)), 0.0), 28.0);
                float shimmer = 0.9 + 0.1 * sin(uTime * 4.0 + vUv.x * 33.0 + vUv.y * 19.0);
                color += vec3(1.0, 0.96, 0.86) * specular * uShineStrength * wet * shimmer;
                float edge = smoothstep(0.18, 0.42, length(gradient));
                color = mix(color, color * 0.72, edge * uEdgeDarkening);
                fragColor = vec4(clamp(color, 0.0, 1.0) * displayAlpha, displayAlpha);
            }
        """
    }
}
