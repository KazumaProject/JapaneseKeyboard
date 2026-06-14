package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max

internal class LiquidRippleSimulation(
    private val gridResolver: LiquidRippleSimulationGridResolver =
        LiquidRippleSimulationGridResolver()
) {
    internal data class LiquidRippleRenderTargetFormat(
        val internalFormat: Int,
        val format: Int,
        val type: Int,
        val encodesSignedFields: Boolean,
        val label: String
    ) {
        companion object {
            val R16F = LiquidRippleRenderTargetFormat(
                internalFormat = GLES30.GL_R16F,
                format = GLES30.GL_RED,
                type = GLES30.GL_HALF_FLOAT,
                encodesSignedFields = false,
                label = "R16F"
            )

            val Rgba16F = LiquidRippleRenderTargetFormat(
                internalFormat = GLES30.GL_RGBA16F,
                format = GLES30.GL_RGBA,
                type = GLES30.GL_HALF_FLOAT,
                encodesSignedFields = false,
                label = "RGBA16F"
            )

            val EncodedRgba8 = LiquidRippleRenderTargetFormat(
                internalFormat = GLES30.GL_RGBA8,
                format = GLES30.GL_RGBA,
                type = GLES30.GL_UNSIGNED_BYTE,
                encodesSignedFields = true,
                label = "RGBA8 encoded"
            )

            fun candidatesFromCurrentContext(): List<LiquidRippleRenderTargetFormat> {
                return candidates(
                    version = GLES30.glGetString(GLES30.GL_VERSION).orEmpty(),
                    extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS).orEmpty()
                )
            }

            fun candidates(
                version: String,
                extensions: String
            ): List<LiquidRippleRenderTargetFormat> {
                if (!version.contains("OpenGL ES 3")) {
                    error("OpenGL ES 3 is unavailable.")
                }
                val supportsHalfFloatFramebuffer =
                    extensions.contains("EXT_color_buffer_half_float") ||
                        extensions.contains("EXT_color_buffer_float")
                return if (supportsHalfFloatFramebuffer) {
                    listOf(R16F, Rgba16F, EncodedRgba8)
                } else {
                    listOf(EncodedRgba8)
                }
            }
        }
    }

    private data class RenderTarget(
        val texture: Int,
        val framebuffer: Int,
        val width: Int,
        val height: Int,
        val signedField: Boolean
    )

    private data class RippleTargets(
        var previous: RenderTarget,
        var current: RenderTarget,
        var next: RenderTarget
    ) {
        fun swapCurrentWithNext() {
            val oldCurrent = current
            current = next
            next = oldCurrent
        }

        fun advanceWave() {
            val oldPrevious = previous
            previous = current
            current = next
            next = oldPrevious
        }
    }

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var grid = LiquidRippleSimulationGrid(32, 32)
    private var rippleTargets: RippleTargets? = null
    private var renderTargetFormat = LiquidRippleRenderTargetFormat.R16F
    private var energyEstimate = 0f
    private var timeSeconds = 0f
    private var accumulatedWaveTimeSeconds = 0f

    private var impulseProgram = 0
    private var waveProgram = 0
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
            "Invalid liquid ripple surface size ${surfaceWidth}x$surfaceHeight"
        }
        if (impulseProgram == 0) {
            createPrograms()
        }
        this.surfaceWidth = surfaceWidth
        this.surfaceHeight = surfaceHeight
        val nextGrid = gridResolver.resolve(surfaceWidth, surfaceHeight, qualityLevel, userQuality)
        val candidates = LiquidRippleRenderTargetFormat.candidatesFromCurrentContext()
        var lastFailure: Throwable? = null
        for (format in candidates) {
            runCatching {
                releaseTargets()
                renderTargetFormat = format
                resizeTargets(
                    nextGrid = nextGrid,
                    preserveExistingRipple = false
                )
                clear()
                return
            }.onFailure {
                lastFailure = it
                releaseTargets()
            }
        }
        throw IllegalStateException("Liquid ripple framebuffer creation failed", lastFailure)
    }

    fun resizeSurface(
        surfaceWidth: Int,
        surfaceHeight: Int,
        qualityLevel: Int,
        userQuality: String = KeyboardTouchEffectQuality.HIGH
    ) {
        if (impulseProgram == 0) {
            initialize(surfaceWidth, surfaceHeight, qualityLevel, userQuality)
            return
        }
        this.surfaceWidth = surfaceWidth
        this.surfaceHeight = surfaceHeight
        val nextGrid = gridResolver.resolve(surfaceWidth, surfaceHeight, qualityLevel, userQuality)
        if (nextGrid == grid) return
        resizeTargets(
            nextGrid = nextGrid,
            preserveExistingRipple = true
        )
    }

    fun render(
        inputCommands: List<LiquidRippleInputCommand>,
        dtSeconds: Float,
        params: LiquidRippleStepParams
    ): Boolean {
        val targets = rippleTargets ?: return false
        val clampedDt = dtSeconds.coerceIn(MIN_DT_SECONDS, MAX_DT_SECONDS)
        timeSeconds = (timeSeconds + clampedDt).let {
            if (it > TIME_WRAP_SECONDS) it - TIME_WRAP_SECONDS else it
        }

        // Damped height-field wave step: impulses modify height, fixed-rate Laplacian steps propagate it.
        applyImpulseCommands(inputCommands, targets)
        val waveSteps = stepWaveAtFixedRate(targets, clampedDt, params)
        // Composite derives normals, highlights, and caustic-like accents from the height field.
        composite(targets.current, params)

        repeat(waveSteps) {
            energyEstimate *= params.energyDamping
        }
        if (energyEstimate < params.idleEnergyThreshold && inputCommands.isEmpty()) {
            clear()
            return false
        }
        return energyEstimate >= params.idleEnergyThreshold || inputCommands.isNotEmpty()
    }

    fun hasVisibleEnergy(): Boolean {
        return energyEstimate >= DEFAULT_IDLE_ENERGY_THRESHOLD
    }

    /**
     * Clears visible simulation state.
     *
     * Do not call this from resizeSurface().
     * resizeSurface() must preserve user-visible effect state.
     */
    fun clear() {
        rippleTargets?.let { targets ->
            clearTarget(targets.previous)
            clearTarget(targets.current)
            clearTarget(targets.next)
        }
        energyEstimate = 0f
        accumulatedWaveTimeSeconds = 0f
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        }
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    fun release() {
        releaseTargets()
        val programs = intArrayOf(
            impulseProgram,
            waveProgram,
            compositeProgram,
            copyProgram
        ).filter { it != 0 }.toIntArray()
        programs.forEach(GLES30::glDeleteProgram)
        impulseProgram = 0
        waveProgram = 0
        compositeProgram = 0
        copyProgram = 0
    }

    private fun releaseTargets() {
        rippleTargets?.let { targets ->
            releaseTarget(targets.previous)
            releaseTarget(targets.current)
            releaseTarget(targets.next)
        }
        rippleTargets = null
    }

    private fun resizeTargets(
        nextGrid: LiquidRippleSimulationGrid,
        preserveExistingRipple: Boolean
    ) {
        val oldTargets = rippleTargets
        var nextPrevious: RenderTarget? = null
        var nextCurrent: RenderTarget? = null
        var nextNext: RenderTarget? = null

        try {
            val createdPrevious = createTarget(nextGrid.width, nextGrid.height, signedField = true)
                .also { nextPrevious = it }
            val createdCurrent = createTarget(nextGrid.width, nextGrid.height, signedField = true)
                .also { nextCurrent = it }
            val createdNext = createTarget(nextGrid.width, nextGrid.height, signedField = true)
                .also { nextNext = it }

            if (preserveExistingRipple && oldTargets != null) {
                copyTextureToTarget(oldTargets.previous.texture, createdPrevious)
                copyTextureToTarget(oldTargets.current.texture, createdCurrent)
                clearTarget(createdNext)
            } else {
                clearTarget(createdPrevious)
                clearTarget(createdCurrent)
                clearTarget(createdNext)
                energyEstimate = 0f
                accumulatedWaveTimeSeconds = 0f
            }

            grid = nextGrid
            rippleTargets = RippleTargets(
                previous = createdPrevious,
                current = createdCurrent,
                next = createdNext
            )
            releaseTarget(oldTargets?.previous)
            releaseTarget(oldTargets?.current)
            releaseTarget(oldTargets?.next)
        } catch (throwable: Throwable) {
            releaseTarget(nextPrevious)
            releaseTarget(nextCurrent)
            releaseTarget(nextNext)
            throw throwable
        }
    }

    private fun applyImpulseCommands(
        inputCommands: List<LiquidRippleInputCommand>,
        targets: RippleTargets
    ) {
        inputCommands.forEach { command ->
            if (command !is LiquidRippleInputCommand.Impulse) return@forEach
            val pointX = if (surfaceWidth > 0) command.x / surfaceWidth else 0f
            val pointY = if (surfaceHeight > 0) 1f - (command.y / surfaceHeight) else 0f
            val radius = (command.radiusPx / max(surfaceWidth, surfaceHeight).toFloat())
                .coerceIn(0.006f, 0.07f)
            val strength = when (command.kind) {
                LiquidRippleImpulseKind.Down -> command.strength
                LiquidRippleImpulseKind.Move -> command.strength
                LiquidRippleImpulseKind.Up -> -command.strength * 0.45f
            }

            impulse(
                source = targets.current,
                destination = targets.next,
                pointX = pointX,
                pointY = pointY,
                strength = strength,
                radius = radius,
                ringRadius = radius * 2.1f,
                ringWidth = radius * 0.48f,
                directionX = command.directionX,
                directionY = command.directionY,
                impulseKind = command.kind,
                phase = (command.eventTimeMillis % 10_000L) / 1000f
            )
            targets.swapCurrentWithNext()
            val energyWeight = when (command.kind) {
                LiquidRippleImpulseKind.Down -> 1f
                LiquidRippleImpulseKind.Move -> 0.62f
                LiquidRippleImpulseKind.Up -> 0.24f
            }
            energyEstimate = (energyEstimate + abs(command.strength) * energyWeight)
                .coerceAtMost(MAX_ENERGY_ESTIMATE)
        }
    }

    private fun impulse(
        source: RenderTarget,
        destination: RenderTarget,
        pointX: Float,
        pointY: Float,
        strength: Float,
        radius: Float,
        ringRadius: Float,
        ringWidth: Float,
        directionX: Float,
        directionY: Float,
        impulseKind: LiquidRippleImpulseKind,
        phase: Float
    ) {
        useProgram(impulseProgram, destination)
        bindTexture(0, source.texture, impulseProgram, "uHeight")
        uniform2f(impulseProgram, "uPoint", pointX.coerceIn(0f, 1f), pointY.coerceIn(0f, 1f))
        uniform1f(impulseProgram, "uStrength", strength)
        uniform1f(impulseProgram, "uRadius", radius)
        uniform1f(impulseProgram, "uRingRadius", ringRadius)
        uniform1f(impulseProgram, "uRingWidth", ringWidth)
        uniform2f(impulseProgram, "uDirection", directionX, directionY)
        uniform1f(
            impulseProgram,
            "uImpulseType",
            when (impulseKind) {
                LiquidRippleImpulseKind.Down -> 0f
                LiquidRippleImpulseKind.Move -> 1f
                LiquidRippleImpulseKind.Up -> 2f
            }
        )
        uniform1f(impulseProgram, "uAspect", grid.width.toFloat() / grid.height.toFloat())
        uniform1f(impulseProgram, "uPhase", phase)
        uniformEncoding(impulseProgram, "uHeightEncoded", source)
        uniformEncoding(impulseProgram, "uOutputEncoded", destination)
        uniform1f(impulseProgram, "uSignedRange", SIGNED_FIELD_RANGE)
        drawQuad()
    }

    private fun stepWaveAtFixedRate(
        targets: RippleTargets,
        dtSeconds: Float,
        params: LiquidRippleStepParams
    ): Int {
        accumulatedWaveTimeSeconds = minOf(
            accumulatedWaveTimeSeconds + dtSeconds,
            FIXED_WAVE_STEP_SECONDS * MAX_WAVE_STEPS_PER_FRAME
        )
        var waveSteps = 0
        while (
            accumulatedWaveTimeSeconds >= FIXED_WAVE_STEP_SECONDS &&
            waveSteps < MAX_WAVE_STEPS_PER_FRAME
        ) {
            stepWave(targets, params)
            targets.advanceWave()
            accumulatedWaveTimeSeconds -= FIXED_WAVE_STEP_SECONDS
            waveSteps += 1
        }
        return waveSteps
    }

    private fun stepWave(targets: RippleTargets, params: LiquidRippleStepParams) {
        useProgram(waveProgram, targets.next)
        bindTexture(0, targets.previous.texture, waveProgram, "uPrevious")
        bindTexture(1, targets.current.texture, waveProgram, "uCurrent")
        uniform2f(waveProgram, "uTexelSize", 1f / grid.width, 1f / grid.height)
        uniform1f(waveProgram, "uWaveSpeed", params.waveSpeed)
        uniform1f(waveProgram, "uDamping", params.damping)
        uniform1f(waveProgram, "uBoundaryAbsorptionWidth", params.boundaryAbsorptionWidth)
        uniformEncoding(waveProgram, "uPreviousEncoded", targets.previous)
        uniformEncoding(waveProgram, "uCurrentEncoded", targets.current)
        uniformEncoding(waveProgram, "uOutputEncoded", targets.next)
        uniform1f(waveProgram, "uSignedRange", SIGNED_FIELD_RANGE)
        drawQuad()
    }

    private fun composite(height: RenderTarget, params: LiquidRippleStepParams) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(compositeProgram)
        bindTexture(0, height.texture, compositeProgram, "uHeight")
        uniform2f(compositeProgram, "uTexelSize", 1f / grid.width, 1f / grid.height)
        uniform1f(compositeProgram, "uTime", timeSeconds)
        uniform1f(compositeProgram, "uNormalSampleMode", params.normalSampleMode.toFloat())
        uniformEncoding(compositeProgram, "uHeightEncoded", height)
        uniform1f(compositeProgram, "uSignedRange", SIGNED_FIELD_RANGE)
        drawQuad()
    }

    private fun copyTextureToTarget(texture: Int, target: RenderTarget?) {
        if (target == null) return
        useProgram(copyProgram, target)
        GLES30.glDisable(GLES30.GL_BLEND)
        bindTexture(0, texture, copyProgram, "uTexture")
        drawQuad()
    }

    private fun clearTarget(target: RenderTarget) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebuffer)
        GLES30.glViewport(0, 0, target.width, target.height)
        val clearValue = if (target.isSignedFieldEncoded()) 0.5f else 0f
        GLES30.glClearColor(clearValue, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    private fun useProgram(program: Int, target: RenderTarget) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebuffer)
        GLES30.glViewport(0, 0, target.width, target.height)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glUseProgram(program)
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

    private fun uniformEncoding(program: Int, name: String, target: RenderTarget) {
        uniform1f(program, name, if (target.isSignedFieldEncoded()) 1f else 0f)
    }

    private fun RenderTarget.isSignedFieldEncoded(): Boolean {
        return signedField && renderTargetFormat.encodesSignedFields
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

    private fun createTarget(width: Int, height: Int, signedField: Boolean): RenderTarget {
        val textures = IntArray(1)
        val framebuffers = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_NEAREST
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_NEAREST
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
            renderTargetFormat.internalFormat,
            width,
            height,
            0,
            renderTargetFormat.format,
            renderTargetFormat.type,
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
            error(
                "Liquid ripple framebuffer incomplete for ${renderTargetFormat.label}: " +
                    "0x${status.toString(16)}"
            )
        }
        return RenderTarget(
            texture = textures[0],
            framebuffer = framebuffers[0],
            width = width,
            height = height,
            signedField = signedField
        )
    }

    private fun releaseTarget(target: RenderTarget?) {
        if (target == null) return
        releaseTextureAndFramebuffer(target.texture, target.framebuffer)
    }

    private fun releaseTextureAndFramebuffer(texture: Int, framebuffer: Int) {
        GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
        GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
    }

    private fun createPrograms() {
        impulseProgram = createProgram(IMPULSE_FRAGMENT_SHADER)
        waveProgram = createProgram(WAVE_FRAGMENT_SHADER)
        compositeProgram = createProgram(COMPOSITE_FRAGMENT_SHADER)
        copyProgram = createProgram(COPY_FRAGMENT_SHADER)
    }

    private fun createProgram(fragmentShaderSource: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
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
            error("Liquid ripple shader program link failed: $log")
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
            error("Liquid ripple shader compile failed: $log")
        }
        return shader
    }

    companion object {
        private const val SIGNED_FIELD_RANGE = 2.5f
        private const val MAX_ENERGY_ESTIMATE = 1.8f
        private const val DEFAULT_IDLE_ENERGY_THRESHOLD = 0.012f
        private const val MIN_DT_SECONDS = 1f / 120f
        private const val MAX_DT_SECONDS = 1f / 20f
        private const val FIXED_WAVE_STEP_SECONDS = 1f / 60f
        private const val MAX_WAVE_STEPS_PER_FRAME = 3
        private const val TIME_WRAP_SECONDS = 240f

        private val QUAD = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
        )

        private const val VERTEX_SHADER = """
            #version 300 es
            precision highp float;
            layout(location = 0) in vec2 aPosition;
            out vec2 vUv;
            void main() {
                vUv = aPosition * 0.5 + 0.5;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        private const val IMPULSE_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uHeight;
            uniform vec2 uPoint;
            uniform float uStrength;
            uniform float uRadius;
            uniform float uRingRadius;
            uniform float uRingWidth;
            uniform vec2 uDirection;
            uniform float uImpulseType;
            uniform float uAspect;
            uniform float uPhase;
            uniform float uHeightEncoded;
            uniform float uOutputEncoded;
            uniform float uSignedRange;
            out vec4 fragColor;
            float decodeHeight(vec4 value, float encoded) {
                return encoded > 0.5 ? (value.r * 2.0 - 1.0) * uSignedRange : value.r;
            }
            vec4 encodeHeight(float value, float encoded) {
                if (encoded > 0.5) {
                    float packed = clamp(value / uSignedRange * 0.5 + 0.5, 0.0, 1.0);
                    return vec4(packed, packed, packed, 1.0);
                }
                return vec4(value, 0.0, 0.0, 1.0);
            }
            void main() {
                float base = decodeHeight(texture(uHeight, vUv), uHeightEncoded);
                vec2 delta = vUv - uPoint;
                delta.x *= uAspect;
                float d = length(delta);
                float radius = max(uRadius, 0.0001);
                float gaussian = exp(-(d * d) / (radius * radius));
                float ring = exp(-pow((d - uRingRadius) / max(uRingWidth, 0.0001), 2.0));
                vec2 direction = normalize(uDirection + vec2(0.00001, 0.0));
                float along = dot(delta, direction);
                float side = delta.x * direction.y - delta.y * direction.x;
                float trailing = smoothstep(radius * 1.8, -radius * 0.15, along);
                float directionalWake =
                    exp(-(side * side) / (radius * radius * 0.28)) *
                    exp(-(along * along) / (radius * radius * 5.6)) *
                    trailing;
                float circularImpulse = gaussian * 0.56 + ring * 0.32;
                float moveImpulse = directionalWake * 0.42 + gaussian * 0.12;
                float upImpulse = gaussian * 0.38 + ring * 0.16;
                float impulseShape = circularImpulse;
                if (uImpulseType > 1.5) {
                    impulseShape = upImpulse;
                } else if (uImpulseType > 0.5) {
                    impulseShape = moveImpulse;
                }
                float impulse = uStrength * impulseShape;
                fragColor = encodeHeight(clamp(base + impulse, -1.6, 1.6), uOutputEncoded);
            }
        """

        private const val WAVE_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uPrevious;
            uniform sampler2D uCurrent;
            uniform vec2 uTexelSize;
            uniform float uWaveSpeed;
            uniform float uDamping;
            uniform float uBoundaryAbsorptionWidth;
            uniform float uPreviousEncoded;
            uniform float uCurrentEncoded;
            uniform float uOutputEncoded;
            uniform float uSignedRange;
            out vec4 fragColor;
            float decodeHeight(vec4 value, float encoded) {
                return encoded > 0.5 ? (value.r * 2.0 - 1.0) * uSignedRange : value.r;
            }
            vec4 encodeHeight(float value, float encoded) {
                if (encoded > 0.5) {
                    float packed = clamp(value / uSignedRange * 0.5 + 0.5, 0.0, 1.0);
                    return vec4(packed, packed, packed, 1.0);
                }
                return vec4(value, 0.0, 0.0, 1.0);
            }
            void main() {
                vec2 uv = clamp(vUv, uTexelSize, 1.0 - uTexelSize);
                float previous = decodeHeight(texture(uPrevious, uv), uPreviousEncoded);
                float current = decodeHeight(texture(uCurrent, uv), uCurrentEncoded);
                float hL = decodeHeight(texture(uCurrent, uv - vec2(uTexelSize.x, 0.0)), uCurrentEncoded);
                float hR = decodeHeight(texture(uCurrent, uv + vec2(uTexelSize.x, 0.0)), uCurrentEncoded);
                float hD = decodeHeight(texture(uCurrent, uv - vec2(0.0, uTexelSize.y)), uCurrentEncoded);
                float hU = decodeHeight(texture(uCurrent, uv + vec2(0.0, uTexelSize.y)), uCurrentEncoded);
                float laplacian = hL + hR + hD + hU - current * 4.0;
                float nextHeight = (current * 2.0 - previous + laplacian * uWaveSpeed) * uDamping;
                float edge = min(min(vUv.x, 1.0 - vUv.x), min(vUv.y, 1.0 - vUv.y));
                float absorptionWidth = max(
                    uBoundaryAbsorptionWidth,
                    max(uTexelSize.x, uTexelSize.y) * 2.0
                );
                float edgeMask = smoothstep(0.0, absorptionWidth, edge);
                float boundaryDamping = mix(0.88, 1.0, edgeMask);
                nextHeight *= boundaryDamping;
                fragColor = encodeHeight(clamp(nextHeight, -1.6, 1.6), uOutputEncoded);
            }
        """

        private const val COMPOSITE_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uHeight;
            uniform vec2 uTexelSize;
            uniform float uTime;
            uniform float uNormalSampleMode;
            uniform float uHeightEncoded;
            uniform float uSignedRange;
            out vec4 fragColor;
            float decodeHeight(vec4 value, float encoded) {
                return encoded > 0.5 ? (value.r * 2.0 - 1.0) * uSignedRange : value.r;
            }
            float sampleHeight(vec2 uv) {
                return decodeHeight(texture(uHeight, clamp(uv, uTexelSize, 1.0 - uTexelSize)), uHeightEncoded);
            }
            void main() {
                float hC = sampleHeight(vUv);
                float hL = sampleHeight(vUv - vec2(uTexelSize.x, 0.0));
                float hR = sampleHeight(vUv + vec2(uTexelSize.x, 0.0));
                float hD = sampleHeight(vUv - vec2(0.0, uTexelSize.y));
                float hU = sampleHeight(vUv + vec2(0.0, uTexelSize.y));
                vec2 gradient = vec2(hL - hR, hD - hU);
                float curvature = abs(hL + hR + hD + hU - hC * 4.0);
                if (uNormalSampleMode > 0.5) {
                    float hL2 = sampleHeight(vUv - vec2(uTexelSize.x * 2.0, 0.0));
                    float hR2 = sampleHeight(vUv + vec2(uTexelSize.x * 2.0, 0.0));
                    float hD2 = sampleHeight(vUv - vec2(0.0, uTexelSize.y * 2.0));
                    float hU2 = sampleHeight(vUv + vec2(0.0, uTexelSize.y * 2.0));
                    gradient = gradient * 0.72 + vec2(hL2 - hR2, hD2 - hU2) * 0.18;
                    curvature = max(
                        curvature,
                        abs(hL2 + hR2 + hD2 + hU2 - hC * 4.0) * 0.55
                    );
                }
                if (uNormalSampleMode > 1.5) {
                    float hLD = sampleHeight(vUv + vec2(-uTexelSize.x, -uTexelSize.y));
                    float hLU = sampleHeight(vUv + vec2(-uTexelSize.x, uTexelSize.y));
                    float hRD = sampleHeight(vUv + vec2(uTexelSize.x, -uTexelSize.y));
                    float hRU = sampleHeight(vUv + vec2(uTexelSize.x, uTexelSize.y));
                    vec2 sobel = vec2(
                        (hLD + 2.0 * hL + hLU) - (hRD + 2.0 * hR + hRU),
                        (hLD + 2.0 * hD + hRD) - (hLU + 2.0 * hU + hRU)
                    ) * 0.25;
                    gradient = mix(gradient, sobel, 0.55);
                    curvature = max(curvature, abs(hLD + hLU + hRD + hRU - hC * 4.0) * 0.42);
                }
                if (uNormalSampleMode > 2.5) {
                    float hL3 = sampleHeight(vUv - vec2(uTexelSize.x * 3.0, 0.0));
                    float hR3 = sampleHeight(vUv + vec2(uTexelSize.x * 3.0, 0.0));
                    float hD3 = sampleHeight(vUv - vec2(0.0, uTexelSize.y * 3.0));
                    float hU3 = sampleHeight(vUv + vec2(0.0, uTexelSize.y * 3.0));
                    gradient = gradient * 0.82 + vec2(hL3 - hR3, hD3 - hU3) * 0.08;
                    curvature = max(
                        curvature,
                        abs(hL3 + hR3 + hD3 + hU3 - hC * 4.0) * 0.32
                    );
                }
                vec3 normal = normalize(vec3(gradient * 12.0, 1.0));
                vec3 light = normalize(vec3(-0.28, -0.42, 0.86));
                float diffuse = dot(normal, light);
                float ridge = clamp(length(gradient) * 13.0 + curvature * 5.4, 0.0, 1.0);
                float slowPulse = 0.86 + 0.14 * sin(uTime * 0.72);
                float softHighlight = smoothstep(0.05, 0.62, diffuse) * ridge;
                float subtleShadow = smoothstep(0.0, 0.42, -diffuse) * ridge;
                vec3 reflected = reflect(-light, normal);
                float specular = pow(max(dot(reflected, vec3(0.0, 0.0, 1.0)), 0.0), 44.0) * ridge;
                float caustic = pow(ridge, 1.7) *
                    (0.58 + 0.42 * sin((vUv.x * 38.0 + vUv.y * 44.0) + uTime * 1.18 + hC * 12.0)) *
                    slowPulse;
                float alpha = clamp(
                    ridge * 0.072 + softHighlight * 0.052 + specular * 0.16 +
                        subtleShadow * 0.035 + caustic * 0.028,
                    0.0,
                    0.20
                );
                vec3 highlightColor = vec3(0.74, 0.89, 1.0);
                vec3 shadowColor = vec3(0.18, 0.26, 0.33);
                float lightMix = clamp(softHighlight + specular * 1.2 + caustic * 0.32, 0.0, 1.0);
                vec3 color = mix(shadowColor, highlightColor, lightMix);
                color = mix(color, vec3(0.90, 0.97, 1.0), specular * 0.48);
                fragColor = vec4(color * alpha, alpha);
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
    }
}
