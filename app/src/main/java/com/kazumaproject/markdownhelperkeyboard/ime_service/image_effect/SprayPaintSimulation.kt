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
        val seed: Float
    )

    private data class StyleProfile(
        val tapCountScale: Float,
        val coreShare: Float,
        val mistShare: Float,
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
        val dripChance: Float,
        val dripLengthScale: Float,
        val dripIntervalScale: Float,
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
    private val longPressResidualByPointer = HashMap<Int, Float>()
    private val nextDripMillisByPointer = HashMap<Int, Long>()
    private val liquidSlimeProfile = StyleProfile(
        tapCountScale = 1.08f,
        coreShare = 0.62f,
        mistShare = 0.25f,
        coreSigmaPx = 10f,
        mistSigmaPx = 30f,
        speckleSigmaPx = 44f,
        coreMedianRadiusPx = 18.5f,
        mistMedianRadiusPx = 7.2f,
        speckleMedianRadiusPx = 3.3f,
        coreAlpha = 0.035f,
        mistAlpha = 0.012f,
        speckleAlpha = 0.058f,
        moveCountScale = 1.14f,
        moveAlongBasePx = 25f,
        moveAlongSpeedPx = 52f,
        moveMistSidePx = 20f,
        moveCoreSidePx = 11f,
        moveStretchBoost = 2.35f,
        popCountScale = 1.14f,
        popStartSpreadPx = 20f,
        popEndSpreadPx = 72f,
        popAlphaScale = 1.18f,
        longPressScale = 1.18f,
        dripChance = 1f,
        dripLengthScale = 1.12f,
        dripIntervalScale = 0.74f,
        graffitiAccentScale = 0f,
        liquidGlobScale = 1f,
        petalScale = 0f,
        smokeScale = 0.18f
    )
    private val neonGraffitiProfile = StyleProfile(
        tapCountScale = 1.18f,
        coreShare = 0.36f,
        mistShare = 0.44f,
        coreSigmaPx = 15f,
        mistSigmaPx = 46f,
        speckleSigmaPx = 72f,
        coreMedianRadiusPx = 10.5f,
        mistMedianRadiusPx = 5.2f,
        speckleMedianRadiusPx = 2.2f,
        coreAlpha = 0.028f,
        mistAlpha = 0.018f,
        speckleAlpha = 0.092f,
        moveCountScale = 1.28f,
        moveAlongBasePx = 34f,
        moveAlongSpeedPx = 72f,
        moveMistSidePx = 27f,
        moveCoreSidePx = 7f,
        moveStretchBoost = 3.25f,
        popCountScale = 1.24f,
        popStartSpreadPx = 30f,
        popEndSpreadPx = 92f,
        popAlphaScale = 1.16f,
        longPressScale = 1.02f,
        dripChance = 0.42f,
        dripLengthScale = 0.82f,
        dripIntervalScale = 1.22f,
        graffitiAccentScale = 1f,
        liquidGlobScale = 0f,
        petalScale = 0f,
        smokeScale = 0.1f
    )
    private val softPastelProfile = StyleProfile(
        tapCountScale = 0.92f,
        coreShare = 0.39f,
        mistShare = 0.49f,
        coreSigmaPx = 14f,
        mistSigmaPx = 42f,
        speckleSigmaPx = 56f,
        coreMedianRadiusPx = 12.4f,
        mistMedianRadiusPx = 8.6f,
        speckleMedianRadiusPx = 2.5f,
        coreAlpha = 0.021f,
        mistAlpha = 0.012f,
        speckleAlpha = 0.046f,
        moveCountScale = 0.94f,
        moveAlongBasePx = 25f,
        moveAlongSpeedPx = 40f,
        moveMistSidePx = 24f,
        moveCoreSidePx = 10f,
        moveStretchBoost = 1.3f,
        popCountScale = 0.96f,
        popStartSpreadPx = 28f,
        popEndSpreadPx = 78f,
        popAlphaScale = 0.82f,
        longPressScale = 0.82f,
        dripChance = 0.16f,
        dripLengthScale = 0.62f,
        dripIntervalScale = 1.8f,
        graffitiAccentScale = 0f,
        liquidGlobScale = 0f,
        petalScale = 0.18f,
        smokeScale = 0.72f
    )
    private val sumireSmokeProfile = StyleProfile(
        tapCountScale = 0.96f,
        coreShare = 0.24f,
        mistShare = 0.62f,
        coreSigmaPx = 16f,
        mistSigmaPx = 54f,
        speckleSigmaPx = 76f,
        coreMedianRadiusPx = 10.4f,
        mistMedianRadiusPx = 9.4f,
        speckleMedianRadiusPx = 2.3f,
        coreAlpha = 0.019f,
        mistAlpha = 0.01f,
        speckleAlpha = 0.042f,
        moveCountScale = 0.9f,
        moveAlongBasePx = 28f,
        moveAlongSpeedPx = 34f,
        moveMistSidePx = 30f,
        moveCoreSidePx = 12f,
        moveStretchBoost = 1.05f,
        popCountScale = 1.08f,
        popStartSpreadPx = 38f,
        popEndSpreadPx = 104f,
        popAlphaScale = 0.78f,
        longPressScale = 0.72f,
        dripChance = 0f,
        dripLengthScale = 0f,
        dripIntervalScale = 1f,
        graffitiAccentScale = 0f,
        liquidGlobScale = 0f,
        petalScale = 1f,
        smokeScale = 1f
    )

    private var depositionProgram = 0
    private var decayProgram = 0
    private var compositeProgram = 0

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
        resizeTargets(nextGrid)
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
        resizeTargets(nextGrid)
        clear()
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

        decayPaint(params, clampedDt)
        applyInputCommands(inputCommands, params)
        emitPopBursts(params, clampedDt)
        emitLongPress(activePointers, nowMillis, clampedDt, params)
        composite(params)

        energyEstimate *= params.decayPerSecond.pow(clampedDt).coerceIn(0.74f, 0.998f)
        if (
            energyEstimate < params.idleEnergyThreshold &&
            inputCommands.isEmpty() &&
            activePointers.isEmpty() &&
            popBursts.isEmpty()
        ) {
            clear()
            return false
        }
        return energyEstimate >= params.idleEnergyThreshold ||
            inputCommands.isNotEmpty() ||
            activePointers.isNotEmpty() ||
            popBursts.isNotEmpty()
    }

    fun hasVisiblePaint(): Boolean {
        return energyEstimate >= DEFAULT_IDLE_ENERGY_THRESHOLD || popBursts.isNotEmpty()
    }

    fun clear() {
        clearTarget(paintAccumulationTexture)
        clearTarget(temporaryTexture)
        popBursts.clear()
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
            compositeProgram
        ).filter { it != 0 }.toIntArray()
        programs.forEach(GLES30::glDeleteProgram)
        depositionProgram = 0
        decayProgram = 0
        compositeProgram = 0
    }

    private fun resizeTargets(nextGrid: SprayPaintSimulationGrid) {
        releaseTarget(paintAccumulationTexture)
        releaseTarget(temporaryTexture)
        grid = nextGrid
        paintAccumulationTexture = createTarget(grid.width, grid.height)
        temporaryTexture = createTarget(grid.width, grid.height)
    }

    private fun applyInputCommands(
        inputCommands: List<SprayPaintInputCommand>,
        params: SprayPaintStepParams
    ) {
        inputCommands.forEach { command ->
            when (command) {
                is SprayPaintInputCommand.Spray -> {
                    when (command.kind) {
                        SprayPaintEmissionKind.Down -> {
                            emitDown(command, params)
                            popBursts.add(
                                PopBurst(
                                    x = command.x,
                                    y = command.y,
                                    color = command.color,
                                    style = command.style
                                )
                            )
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
        val profile = styleProfile(command.style)
        val count = poissonLike(params.tapParticleCount.toFloat() * profile.tapCountScale)
        val coreCount = (count * profile.coreShare).toInt().coerceAtLeast(1)
        val mistCount = (count * profile.mistShare).toInt().coerceAtLeast(1)
        val speckleCount = (count - coreCount - mistCount).coerceAtLeast(2)

        repeat(coreCount) {
            emitParticle(
                centerX = command.x,
                centerY = command.y,
                sigmaAlong = profile.coreSigmaPx,
                sigmaSide = profile.coreSigmaPx,
                directionX = 1f,
                directionY = 0f,
                color = command.color,
                radiusPx = logNormal(median = profile.coreMedianRadiusPx, sigma = 0.34f),
                alpha = profile.coreAlpha * command.color.alpha,
                stretch = 1f,
                noiseScale = noiseScaleFor(command.style, 42f)
            )
        }
        repeat(mistCount) {
            emitParticle(
                centerX = command.x,
                centerY = command.y,
                sigmaAlong = profile.mistSigmaPx * params.mistScale,
                sigmaSide = profile.mistSigmaPx * params.mistScale,
                directionX = 1f,
                directionY = 0f,
                color = mistColorFor(command.color, command.style),
                radiusPx = logNormal(median = profile.mistMedianRadiusPx, sigma = 0.48f),
                alpha = profile.mistAlpha * command.color.alpha,
                stretch = 1f,
                noiseScale = noiseScaleFor(command.style, 68f)
            )
        }
        repeat(speckleCount) {
            emitParticle(
                centerX = command.x,
                centerY = command.y,
                sigmaAlong = profile.speckleSigmaPx * params.mistScale,
                sigmaSide = profile.speckleSigmaPx * params.mistScale,
                directionX = 1f,
                directionY = 0f,
                color = speckleColorFor(command.color, command.style),
                radiusPx = logNormal(median = profile.speckleMedianRadiusPx, sigma = 0.55f),
                alpha = profile.speckleAlpha * command.color.alpha,
                stretch = 1f,
                noiseScale = noiseScaleFor(command.style, 96f)
            )
        }

        emitPaletteAccents(
            centerX = command.x,
            centerY = command.y,
            directionX = 1f,
            directionY = 0f,
            color = command.color,
            style = command.style,
            profile = profile,
            sourceCount = count,
            speedFactor = 0f,
            params = params
        )
    }

    private fun emitMove(command: SprayPaintInputCommand.Spray, params: SprayPaintStepParams) {
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
        val emitterSpacing = (MOVE_EMITTER_SPACING_PX / profile.moveCountScale)
            .coerceIn(10f, 28f)
        val emitterCount = ceil(distance / emitterSpacing)
            .toInt()
            .coerceIn(1, MAX_MOVE_EMITTERS)
        val totalCount = poissonLike(
            params.moveParticleCount *
                profile.moveCountScale *
                (0.72f + speedFactor * 1.25f)
        )
        val countPerEmitter = (totalCount / emitterCount).coerceAtLeast(2)

        repeat(emitterCount) { index ->
            val t = (index + random.nextFloat()) / emitterCount.toFloat()
            val centerX = command.previousX + dx * t
            val centerY = command.previousY + dy * t
            repeat(countPerEmitter) {
                val mist = random.nextFloat() < profile.mistShare
                emitParticle(
                    centerX = centerX,
                    centerY = centerY,
                    sigmaAlong = (profile.moveAlongBasePx + speedFactor * profile.moveAlongSpeedPx) *
                        params.mistScale,
                    sigmaSide = if (mist) {
                        profile.moveMistSidePx * params.mistScale
                    } else {
                        profile.moveCoreSidePx
                    },
                    directionX = directionX,
                    directionY = directionY,
                    color = if (mist) {
                        mistColorFor(command.color, command.style)
                    } else {
                        command.color
                    },
                    radiusPx = if (mist) {
                        logNormal(median = profile.mistMedianRadiusPx, sigma = 0.42f)
                    } else {
                        logNormal(median = profile.coreMedianRadiusPx * 0.74f, sigma = 0.36f)
                    },
                    alpha = (if (mist) profile.mistAlpha else profile.coreAlpha * 0.72f) *
                        command.color.alpha,
                    stretch = 1f + speedFactor * profile.moveStretchBoost,
                    noiseScale = noiseScaleFor(command.style, if (mist) 72f else 50f)
                )
            }
            emitPaletteAccents(
                centerX = centerX,
                centerY = centerY,
                directionX = directionX,
                directionY = directionY,
                color = command.color,
                style = command.style,
                profile = profile,
                sourceCount = countPerEmitter,
                speedFactor = speedFactor,
                params = params
            )
        }
    }

    private fun emitUp(command: SprayPaintInputCommand.Spray, params: SprayPaintStepParams) {
        val profile = styleProfile(command.style)
        val count = poissonLike(params.upParticleCount.toFloat() * profile.tapCountScale)
        repeat(count) {
            emitParticle(
                centerX = command.x,
                centerY = command.y,
                sigmaAlong = profile.speckleSigmaPx * 0.62f * params.mistScale,
                sigmaSide = profile.speckleSigmaPx * 0.62f * params.mistScale,
                directionX = 1f,
                directionY = 0f,
                color = speckleColorFor(command.color, command.style),
                radiusPx = logNormal(median = profile.speckleMedianRadiusPx * 1.32f, sigma = 0.56f),
                alpha = profile.speckleAlpha * 0.68f * command.color.alpha,
                stretch = 1f,
                noiseScale = noiseScaleFor(command.style, 96f)
            )
        }
        emitPaletteAccents(
            centerX = command.x,
            centerY = command.y,
            directionX = 1f,
            directionY = 0f,
            color = command.color,
            style = command.style,
            profile = profile,
            sourceCount = count,
            speedFactor = 0f,
            params = params
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
            repeat(count) {
                emitParticle(
                    centerX = burst.x,
                    centerY = burst.y,
                    sigmaAlong = spread * params.mistScale,
                    sigmaSide = spread * params.mistScale,
                    directionX = 1f,
                    directionY = 0f,
                    color = mistColorFor(burst.color, burst.style),
                    radiusPx = logNormal(
                        median = profile.mistMedianRadiusPx + progress * profile.coreMedianRadiusPx * 0.54f,
                        sigma = 0.44f
                    ),
                    alpha = (0.012f + 0.01f * (1f - progress)) *
                        profile.popAlphaScale *
                        burst.color.alpha,
                    stretch = if (burst.style == SprayPaintPaletteStyle.SumireSmoke) {
                        1.4f + random.nextFloat() * 1.2f
                    } else {
                        1f
                    },
                    noiseScale = noiseScaleFor(burst.style, 80f)
                )
            }
            emitPaletteAccents(
                centerX = burst.x,
                centerY = burst.y,
                directionX = 1f,
                directionY = 0f,
                color = burst.color,
                style = burst.style,
                profile = profile,
                sourceCount = (count * (1f - progress)).toInt().coerceAtLeast(1),
                speedFactor = progress,
                params = params
            )
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
            val residual = longPressResidualByPointer[pointer.pointerId] ?: 0f
            val target = residual + params.longPressParticlesPerSecond * profile.longPressScale * dtSeconds
            val count = target.toInt()
            longPressResidualByPointer[pointer.pointerId] = target - count
            repeat(count.coerceAtMost(24)) {
                val mist = random.nextFloat() < profile.mistShare
                emitParticle(
                    centerX = pointer.x,
                    centerY = pointer.y,
                    sigmaAlong = if (mist) {
                        profile.mistSigmaPx * 0.82f * params.mistScale
                    } else {
                        profile.coreSigmaPx * 0.92f
                    },
                    sigmaSide = if (mist) {
                        profile.mistSigmaPx * 0.82f * params.mistScale
                    } else {
                        profile.coreSigmaPx * 0.92f
                    },
                    directionX = 1f,
                    directionY = 0f,
                    color = if (mist) {
                        mistColorFor(pointer.color, pointer.style)
                    } else {
                        pointer.color
                    },
                    radiusPx = if (mist) {
                        logNormal(median = profile.mistMedianRadiusPx, sigma = 0.42f)
                    } else {
                        logNormal(median = profile.coreMedianRadiusPx * 0.92f, sigma = 0.34f)
                    },
                    alpha = (if (mist) profile.mistAlpha else profile.coreAlpha * 0.62f) *
                        pointer.color.alpha,
                    stretch = if (pointer.style == SprayPaintPaletteStyle.SumireSmoke && mist) {
                        1.3f + random.nextFloat()
                    } else {
                        1f
                    },
                    noiseScale = noiseScaleFor(pointer.style, if (mist) 76f else 48f)
                )
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
            emitParticleAt(
                Particle(
                    x = x,
                    y = y,
                    radiusPx = 5.6f + random.nextFloat() * 2.2f,
                    red = pointer.color.red,
                    green = pointer.color.green,
                    blue = pointer.color.blue,
                    alpha = profile.speckleAlpha * 0.74f * pointer.color.alpha,
                    directionX = 0f,
                    directionY = 1f,
                    stretch = 3.2f + length / 26f,
                    noiseScale = noiseScaleFor(pointer.style, 92f),
                    seed = random.nextFloat() * 1000f
                )
            )
        }
        nextDripMillisByPointer[pointer.pointerId] =
            nowMillis + ((360L + random.nextInt(420)) * profile.dripIntervalScale).toLong()
    }

    private fun styleProfile(style: SprayPaintPaletteStyle): StyleProfile {
        return when (style) {
            SprayPaintPaletteStyle.NeonGraffiti -> neonGraffitiProfile
            SprayPaintPaletteStyle.SoftPastel -> softPastelProfile
            SprayPaintPaletteStyle.SumireSmoke -> sumireSmokeProfile
            SprayPaintPaletteStyle.LiquidSlime -> liquidSlimeProfile
        }
    }

    private fun mistColorFor(
        color: SprayPaintColor,
        style: SprayPaintPaletteStyle
    ): SprayPaintColor {
        return when (style) {
            SprayPaintPaletteStyle.NeonGraffiti -> lighten(color, 0.16f)
            SprayPaintPaletteStyle.SoftPastel -> lighten(color, 0.28f)
            SprayPaintPaletteStyle.SumireSmoke -> mixToward(color, 0.92f, 0.82f, 1f, 0.34f)
            SprayPaintPaletteStyle.LiquidSlime -> saturate(color, 1.12f)
        }
    }

    private fun speckleColorFor(
        color: SprayPaintColor,
        style: SprayPaintPaletteStyle
    ): SprayPaintColor {
        return when (style) {
            SprayPaintPaletteStyle.NeonGraffiti -> {
                if (random.nextFloat() < 0.38f) darken(color, 0.76f) else saturate(color, 1.22f)
            }

            SprayPaintPaletteStyle.SoftPastel -> lighten(color, 0.2f)
            SprayPaintPaletteStyle.SumireSmoke -> {
                if (random.nextFloat() < 0.46f) {
                    mixToward(color, 1f, 0.78f, 0.92f, 0.46f)
                } else {
                    lighten(color, 0.28f)
                }
            }

            SprayPaintPaletteStyle.LiquidSlime -> saturate(color, 1.08f)
        }
    }

    private fun emitPaletteAccents(
        centerX: Float,
        centerY: Float,
        directionX: Float,
        directionY: Float,
        color: SprayPaintColor,
        style: SprayPaintPaletteStyle,
        profile: StyleProfile,
        sourceCount: Int,
        speedFactor: Float,
        params: SprayPaintStepParams
    ) {
        if (profile.graffitiAccentScale > 0f) {
            emitGraffitiAccents(
                centerX = centerX,
                centerY = centerY,
                directionX = directionX,
                directionY = directionY,
                color = color,
                count = (sourceCount * 0.34f * profile.graffitiAccentScale)
                    .toInt()
                    .coerceAtLeast(1),
                speedFactor = speedFactor,
                params = params
            )
        }
        if (profile.liquidGlobScale > 0f) {
            emitLiquidGlobules(
                centerX = centerX,
                centerY = centerY,
                directionX = directionX,
                directionY = directionY,
                color = color,
                count = (sourceCount * 0.14f * profile.liquidGlobScale)
                    .toInt()
                    .coerceAtLeast(1),
                speedFactor = speedFactor,
                params = params
            )
        }
        if (profile.petalScale > 0f) {
            emitSumirePetals(
                centerX = centerX,
                centerY = centerY,
                color = color,
                count = (sourceCount * 0.24f * profile.petalScale)
                    .toInt()
                    .coerceAtLeast(1),
                params = params
            )
        }
        if (profile.smokeScale > 0.45f) {
            emitSoftSmoke(
                centerX = centerX,
                centerY = centerY,
                color = color,
                style = style,
                count = (sourceCount * 0.16f * profile.smokeScale)
                    .toInt()
                    .coerceAtLeast(1),
                params = params
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
                noiseScale = 142f
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
                    noiseScale = 126f
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
        repeat(count) {
            emitParticle(
                centerX = centerX,
                centerY = centerY,
                sigmaAlong = (22f + speedFactor * 28f) * params.mistScale,
                sigmaSide = (18f + speedFactor * 10f) * params.mistScale,
                directionX = directionX,
                directionY = directionY,
                color = if (random.nextFloat() < 0.44f) lighten(color, 0.18f) else saturate(color, 1.16f),
                radiusPx = logNormal(median = 12.8f, sigma = 0.32f),
                alpha = 0.025f * color.alpha,
                stretch = 1.1f + speedFactor * 1.8f,
                noiseScale = 46f
            )
        }
    }

    private fun emitSumirePetals(
        centerX: Float,
        centerY: Float,
        color: SprayPaintColor,
        count: Int,
        params: SprayPaintStepParams
    ) {
        repeat(count) {
            val angle = random.nextFloat() * TWO_PI
            val petalColor = if (random.nextFloat() < 0.5f) {
                mixToward(color, 1f, 0.72f, 0.9f, 0.42f)
            } else {
                mixToward(color, 0.72f, 0.68f, 1f, 0.38f)
            }
            emitParticle(
                centerX = centerX,
                centerY = centerY,
                sigmaAlong = 48f * params.mistScale,
                sigmaSide = 34f * params.mistScale,
                directionX = cos(angle),
                directionY = sin(angle),
                color = petalColor,
                radiusPx = logNormal(median = 4.6f + random.nextFloat() * 2.2f, sigma = 0.28f),
                alpha = 0.028f * color.alpha,
                stretch = 2.2f + random.nextFloat() * 2.4f,
                noiseScale = 58f
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
                noiseScale = noiseScaleFor(style, 52f)
            )
        }
    }

    private fun noiseScaleFor(style: SprayPaintPaletteStyle, base: Float): Float {
        return when (style) {
            SprayPaintPaletteStyle.NeonGraffiti -> base * 1.38f
            SprayPaintPaletteStyle.SoftPastel -> base * 0.82f
            SprayPaintPaletteStyle.SumireSmoke -> base * 0.72f
            SprayPaintPaletteStyle.LiquidSlime -> base * 0.94f
        }
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
        noiseScale: Float
    ) {
        if (particlesEmittedThisFrame >= maxParticlesThisFrame) return
        val (offsetAlong, offsetSide) = gaussianPair()
        val perpX = -directionY
        val perpY = directionX
        val x = centerX + directionX * offsetAlong * sigmaAlong + perpX * offsetSide * sigmaSide
        val y = centerY + directionY * offsetAlong * sigmaAlong + perpY * offsetSide * sigmaSide
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
                seed = random.nextFloat() * 1000f
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
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
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
            particle.alpha.coerceIn(0f, 0.16f)
        )
        uniform1f(depositionProgram, "uNoiseScale", particle.noiseScale)
        uniform1f(depositionProgram, "uSeed", particle.seed)
        drawQuad()

        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        particlesEmittedThisFrame += 1
        energyEstimate = (energyEstimate + particle.alpha * 0.95f).coerceAtMost(MAX_ENERGY_ESTIMATE)
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

    private fun decayPaint(params: SprayPaintStepParams, dtSeconds: Float) {
        val source = paintAccumulationTexture ?: return
        val destination = temporaryTexture ?: return
        val factor = params.decayPerSecond.pow(dtSeconds).coerceIn(0.72f, 0.999f)
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

    private fun composite(params: SprayPaintStepParams) {
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
        uniform1f(compositeProgram, "uShineStrength", params.shineStrength)
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
                float edgeMask = smoothstep(0.32, 1.15, radial);
                float edge = mix(1.0, mix(0.72, 1.28, noise), edgeMask);
                float alpha = clamp(uColor.a * gaussian * edge, 0.0, 0.18);
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

        private const val COMPOSITE_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uPaint;
            uniform vec2 uTexelSize;
            uniform float uTime;
            uniform float uShineMode;
            uniform float uShineStrength;
            out vec4 fragColor;
            void main() {
                vec4 paint = texture(uPaint, vUv);
                float a = clamp(paint.a, 0.0, 1.0);
                if (a <= 0.001) {
                    fragColor = vec4(0.0);
                    return;
                }
                vec3 color = paint.rgb / max(a, 0.001);
                float displayAlpha = min(0.58, smoothstep(0.0, 0.92, a) * 0.58);

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
                fragColor = vec4(clamp(color, 0.0, 1.0) * displayAlpha, displayAlpha);
            }
        """
    }
}
