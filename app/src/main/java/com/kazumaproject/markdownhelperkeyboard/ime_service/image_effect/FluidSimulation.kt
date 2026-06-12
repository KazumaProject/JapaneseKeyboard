package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

internal class FluidSimulation(
    private val gridResolver: FluidSimulationGridResolver = FluidSimulationGridResolver()
) {
    internal data class FluidRenderTargetFormat(
        val internalFormat: Int,
        val type: Int,
        val encodesSignedFields: Boolean
    ) {
        companion object {
            val HalfFloat = FluidRenderTargetFormat(
                internalFormat = GLES30.GL_RGBA16F,
                type = GLES30.GL_HALF_FLOAT,
                encodesSignedFields = false
            )

            val EncodedRgba8 = FluidRenderTargetFormat(
                internalFormat = GLES30.GL_RGBA8,
                type = GLES30.GL_UNSIGNED_BYTE,
                encodesSignedFields = true
            )

            fun resolveFromCurrentContext(): FluidRenderTargetFormat {
                return resolve(
                    version = GLES30.glGetString(GLES30.GL_VERSION).orEmpty(),
                    extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS).orEmpty()
                )
            }

            fun resolve(version: String, extensions: String): FluidRenderTargetFormat {
                if (!version.contains("OpenGL ES 3")) {
                    error("OpenGL ES 3 is unavailable.")
                }
                val supportsHalfFloatFramebuffer =
                    extensions.contains("EXT_color_buffer_half_float") ||
                        extensions.contains("EXT_color_buffer_float")
                return if (supportsHalfFloatFramebuffer) {
                    HalfFloat
                } else {
                    EncodedRgba8
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

    private data class DoubleTarget(
        var read: RenderTarget,
        var write: RenderTarget
    ) {
        fun swap() {
            val nextRead = write
            write = read
            read = nextRead
        }
    }

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var grid = FluidSimulationGrid(32, 32)
    private var velocity: DoubleTarget? = null
    private var dye: DoubleTarget? = null
    private var pressure: DoubleTarget? = null
    private var divergence: RenderTarget? = null
    private var curl: RenderTarget? = null
    private var renderTargetFormat = FluidRenderTargetFormat.HalfFloat
    private var waterPhaseSeconds = 0f

    private var copyProgram = 0
    private var splatProgram = 0
    private var advectProgram = 0
    private var divergenceProgram = 0
    private var curlProgram = 0
    private var vorticityProgram = 0
    private var pressureProgram = 0
    private var gradientSubtractProgram = 0
    private var compositeProgram = 0

    private val quadVertices: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD)
            position(0)
        }

    fun initialize(surfaceWidth: Int, surfaceHeight: Int, qualityLevel: Int) {
        require(surfaceWidth > 0 && surfaceHeight > 0) {
            "Invalid fluid surface size ${surfaceWidth}x$surfaceHeight"
        }
        renderTargetFormat = FluidRenderTargetFormat.resolveFromCurrentContext()
        if (copyProgram == 0) {
            createPrograms()
        }
        this.surfaceWidth = surfaceWidth
        this.surfaceHeight = surfaceHeight
        val nextGrid = gridResolver.resolve(surfaceWidth, surfaceHeight, qualityLevel)
        runCatching {
            resizeTargets(
                nextGrid = nextGrid,
                preserveExistingDye = false
            )
        }.recoverCatching { throwable ->
            if (renderTargetFormat.encodesSignedFields) throw throwable
            releaseTargets()
            renderTargetFormat = FluidRenderTargetFormat.EncodedRgba8
            resizeTargets(
                nextGrid = nextGrid,
                preserveExistingDye = false
            )
        }.getOrThrow()
        clear()
    }

    fun resizeSurface(surfaceWidth: Int, surfaceHeight: Int, qualityLevel: Int) {
        if (copyProgram == 0) {
            initialize(surfaceWidth, surfaceHeight, qualityLevel)
            return
        }
        this.surfaceWidth = surfaceWidth
        this.surfaceHeight = surfaceHeight
        val nextGrid = gridResolver.resolve(surfaceWidth, surfaceHeight, qualityLevel)
        if (nextGrid == grid) return
        resizeTargets(nextGrid = nextGrid, preserveExistingDye = true)
    }

    fun render(
        inputCommands: List<FluidInputCommand>,
        dtSeconds: Float,
        params: FluidStepParams
    ) {
        val velocityTarget = velocity ?: return
        val dyeTarget = dye ?: return
        val pressureTarget = pressure ?: return
        val divergenceTarget = divergence ?: return
        val curlTarget = curl ?: return

        val clampedDt = dtSeconds.coerceIn(MIN_DT_SECONDS, MAX_DT_SECONDS)
        waterPhaseSeconds = (waterPhaseSeconds + clampedDt).let {
            if (it > WATER_PHASE_WRAP_SECONDS) it - WATER_PHASE_WRAP_SECONDS else it
        }
        applySplatCommands(inputCommands, velocityTarget, dyeTarget)

        advect(
            velocity = velocityTarget.read,
            source = velocityTarget.read,
            destination = velocityTarget.write,
            dtSeconds = clampedDt,
            dissipation = params.velocityDissipation,
            waterDrift = params.waterDrift * VELOCITY_WATER_DRIFT_SCALE
        )
        velocityTarget.swap()

        computeCurl(velocityTarget.read, curlTarget)
        applyVorticity(
            velocity = velocityTarget.read,
            curl = curlTarget,
            destination = velocityTarget.write,
            dtSeconds = clampedDt,
            vorticity = params.vorticity
        )
        velocityTarget.swap()

        computeDivergence(velocityTarget.read, divergenceTarget)
        clearTarget(pressureTarget.read)
        clearTarget(pressureTarget.write)
        repeat(params.pressureIterations.coerceAtLeast(1)) {
            solvePressure(
                pressure = pressureTarget.read,
                divergence = divergenceTarget,
                destination = pressureTarget.write
            )
            pressureTarget.swap()
        }

        subtractPressureGradient(
            velocity = velocityTarget.read,
            pressure = pressureTarget.read,
            destination = velocityTarget.write
        )
        velocityTarget.swap()

        advect(
            velocity = velocityTarget.read,
            source = dyeTarget.read,
            destination = dyeTarget.write,
            dtSeconds = clampedDt,
            dissipation = params.dyeDissipation,
            waterDrift = params.waterDrift
        )
        dyeTarget.swap()

        composite(dyeTarget.read)
    }

    fun clear() {
        listOfNotNull(
            velocity?.read,
            velocity?.write,
            dye?.read,
            dye?.write,
            pressure?.read,
            pressure?.write,
            divergence,
            curl
        ).forEach(::clearTarget)
        waterPhaseSeconds = 0f
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun release() {
        releaseTargets()

        val programs = intArrayOf(
            copyProgram,
            splatProgram,
            advectProgram,
            divergenceProgram,
            curlProgram,
            vorticityProgram,
            pressureProgram,
            gradientSubtractProgram,
            compositeProgram
        ).filter { it != 0 }.toIntArray()
        programs.forEach(GLES30::glDeleteProgram)
        copyProgram = 0
        splatProgram = 0
        advectProgram = 0
        divergenceProgram = 0
        curlProgram = 0
        vorticityProgram = 0
        pressureProgram = 0
        gradientSubtractProgram = 0
        compositeProgram = 0
    }

    private fun releaseTargets() {
        releaseDoubleTarget(velocity)
        releaseDoubleTarget(dye)
        releaseDoubleTarget(pressure)
        releaseTarget(divergence)
        releaseTarget(curl)
        velocity = null
        dye = null
        pressure = null
        divergence = null
        curl = null
    }

    private fun resizeTargets(
        nextGrid: FluidSimulationGrid,
        preserveExistingDye: Boolean
    ) {
        val oldVelocity = velocity
        val oldDye = dye
        val oldPressure = pressure
        val oldDivergence = divergence
        val oldCurl = curl

        grid = nextGrid
        velocity = createDoubleTarget(grid.width, grid.height, signedField = true)
        dye = createDoubleTarget(grid.width, grid.height, signedField = false)
        pressure = createDoubleTarget(grid.width, grid.height, signedField = true)
        divergence = createTarget(grid.width, grid.height, signedField = true)
        curl = createTarget(grid.width, grid.height, signedField = true)

        if (preserveExistingDye) {
            oldVelocity?.read?.let { copyTextureToTarget(it.texture, velocity?.read) }
            oldDye?.read?.let { copyTextureToTarget(it.texture, dye?.read) }
        }

        releaseDoubleTarget(oldVelocity)
        releaseDoubleTarget(oldDye)
        releaseDoubleTarget(oldPressure)
        releaseTarget(oldDivergence)
        releaseTarget(oldCurl)
    }

    private fun applySplatCommands(
        inputCommands: List<FluidInputCommand>,
        velocityTarget: DoubleTarget,
        dyeTarget: DoubleTarget
    ) {
        inputCommands.forEach { command ->
            if (command !is FluidInputCommand.Splat) return@forEach
            val pointX = if (surfaceWidth > 0) command.x / surfaceWidth else 0f
            val pointY = if (surfaceHeight > 0) 1f - (command.y / surfaceHeight) else 0f
            val radius = (command.radiusPx / max(surfaceWidth, surfaceHeight).toFloat())
                .coerceIn(0.008f, 0.08f)
            val velocityScale = if (command.kind == FluidSplatKind.Down) 0f else VELOCITY_SPLAT_SCALE

            splat(
                source = velocityTarget.read,
                destination = velocityTarget.write,
                pointX = pointX,
                pointY = pointY,
                valueX = command.velocityX * velocityScale,
                valueY = -command.velocityY * velocityScale,
                valueZ = 0f,
                alpha = 1f,
                radius = radius * 1.3f,
                isDye = false
            )
            velocityTarget.swap()

            splat(
                source = dyeTarget.read,
                destination = dyeTarget.write,
                pointX = pointX,
                pointY = pointY,
                valueX = command.color.red,
                valueY = command.color.green,
                valueZ = command.color.blue,
                alpha = command.color.alpha,
                radius = radius,
                isDye = true
            )
            dyeTarget.swap()
        }
    }

    private fun splat(
        source: RenderTarget,
        destination: RenderTarget,
        pointX: Float,
        pointY: Float,
        valueX: Float,
        valueY: Float,
        valueZ: Float,
        alpha: Float,
        radius: Float,
        isDye: Boolean
    ) {
        useProgram(splatProgram, destination)
        bindTexture(0, source.texture, splatProgram, "uTarget")
        uniform2f(splatProgram, "uPoint", pointX.coerceIn(0f, 1f), pointY.coerceIn(0f, 1f))
        uniform3f(splatProgram, "uValue", valueX, valueY, valueZ)
        uniform1f(splatProgram, "uAlpha", alpha)
        uniform1f(splatProgram, "uRadius", radius)
        uniform1f(splatProgram, "uAspect", grid.width.toFloat() / grid.height.toFloat())
        uniform1f(splatProgram, "uIsDye", if (isDye) 1f else 0f)
        uniformEncoding(splatProgram, "uTargetEncoded", source)
        uniformEncoding(splatProgram, "uOutputEncoded", destination)
        uniform1f(splatProgram, "uSignedRange", SIGNED_FIELD_RANGE)
        drawQuad()
    }

    private fun advect(
        velocity: RenderTarget,
        source: RenderTarget,
        destination: RenderTarget,
        dtSeconds: Float,
        dissipation: Float,
        waterDrift: Float
    ) {
        useProgram(advectProgram, destination)
        bindTexture(0, velocity.texture, advectProgram, "uVelocity")
        bindTexture(1, source.texture, advectProgram, "uSource")
        uniform2f(advectProgram, "uTexelSize", 1f / grid.width, 1f / grid.height)
        uniform1f(advectProgram, "uDt", dtSeconds)
        uniform1f(advectProgram, "uDissipation", dissipation)
        uniform1f(advectProgram, "uWaterPhase", waterPhaseSeconds)
        uniform1f(advectProgram, "uWaterDrift", waterDrift)
        uniformEncoding(advectProgram, "uVelocityEncoded", velocity)
        uniformEncoding(advectProgram, "uSourceEncoded", source)
        uniformEncoding(advectProgram, "uOutputEncoded", destination)
        uniform1f(advectProgram, "uSignedRange", SIGNED_FIELD_RANGE)
        drawQuad()
    }

    private fun computeCurl(velocity: RenderTarget, destination: RenderTarget) {
        useProgram(curlProgram, destination)
        bindTexture(0, velocity.texture, curlProgram, "uVelocity")
        uniform2f(curlProgram, "uTexelSize", 1f / grid.width, 1f / grid.height)
        uniformEncoding(curlProgram, "uVelocityEncoded", velocity)
        uniformEncoding(curlProgram, "uOutputEncoded", destination)
        uniform1f(curlProgram, "uSignedRange", SIGNED_FIELD_RANGE)
        drawQuad()
    }

    private fun applyVorticity(
        velocity: RenderTarget,
        curl: RenderTarget,
        destination: RenderTarget,
        dtSeconds: Float,
        vorticity: Float
    ) {
        useProgram(vorticityProgram, destination)
        bindTexture(0, velocity.texture, vorticityProgram, "uVelocity")
        bindTexture(1, curl.texture, vorticityProgram, "uCurl")
        uniform2f(vorticityProgram, "uTexelSize", 1f / grid.width, 1f / grid.height)
        uniform1f(vorticityProgram, "uDt", dtSeconds)
        uniform1f(vorticityProgram, "uVorticity", vorticity)
        uniformEncoding(vorticityProgram, "uVelocityEncoded", velocity)
        uniformEncoding(vorticityProgram, "uCurlEncoded", curl)
        uniformEncoding(vorticityProgram, "uOutputEncoded", destination)
        uniform1f(vorticityProgram, "uSignedRange", SIGNED_FIELD_RANGE)
        drawQuad()
    }

    private fun computeDivergence(velocity: RenderTarget, destination: RenderTarget) {
        useProgram(divergenceProgram, destination)
        bindTexture(0, velocity.texture, divergenceProgram, "uVelocity")
        uniform2f(divergenceProgram, "uTexelSize", 1f / grid.width, 1f / grid.height)
        uniformEncoding(divergenceProgram, "uVelocityEncoded", velocity)
        uniformEncoding(divergenceProgram, "uOutputEncoded", destination)
        uniform1f(divergenceProgram, "uSignedRange", SIGNED_FIELD_RANGE)
        drawQuad()
    }

    private fun solvePressure(
        pressure: RenderTarget,
        divergence: RenderTarget,
        destination: RenderTarget
    ) {
        useProgram(pressureProgram, destination)
        bindTexture(0, pressure.texture, pressureProgram, "uPressure")
        bindTexture(1, divergence.texture, pressureProgram, "uDivergence")
        uniform2f(pressureProgram, "uTexelSize", 1f / grid.width, 1f / grid.height)
        uniformEncoding(pressureProgram, "uPressureEncoded", pressure)
        uniformEncoding(pressureProgram, "uDivergenceEncoded", divergence)
        uniformEncoding(pressureProgram, "uOutputEncoded", destination)
        uniform1f(pressureProgram, "uSignedRange", SIGNED_FIELD_RANGE)
        drawQuad()
    }

    private fun subtractPressureGradient(
        velocity: RenderTarget,
        pressure: RenderTarget,
        destination: RenderTarget
    ) {
        useProgram(gradientSubtractProgram, destination)
        bindTexture(0, velocity.texture, gradientSubtractProgram, "uVelocity")
        bindTexture(1, pressure.texture, gradientSubtractProgram, "uPressure")
        uniform2f(gradientSubtractProgram, "uTexelSize", 1f / grid.width, 1f / grid.height)
        uniformEncoding(gradientSubtractProgram, "uVelocityEncoded", velocity)
        uniformEncoding(gradientSubtractProgram, "uPressureEncoded", pressure)
        uniformEncoding(gradientSubtractProgram, "uOutputEncoded", destination)
        uniform1f(gradientSubtractProgram, "uSignedRange", SIGNED_FIELD_RANGE)
        drawQuad()
    }

    private fun composite(dye: RenderTarget) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(compositeProgram)
        bindTexture(0, dye.texture, compositeProgram, "uDye")
        drawQuad()
    }

    private fun copyTextureToTarget(texture: Int, target: RenderTarget?) {
        if (target == null) return
        useProgram(copyProgram, target)
        bindTexture(0, texture, copyProgram, "uTexture")
        drawQuad()
    }

    private fun clearTarget(target: RenderTarget) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebuffer)
        GLES30.glViewport(0, 0, target.width, target.height)
        val clearValue = if (target.isSignedFieldEncoded()) 0.5f else 0f
        GLES30.glClearColor(clearValue, clearValue, clearValue, clearValue)
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

    private fun uniform3f(program: Int, name: String, x: Float, y: Float, z: Float) {
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, name), x, y, z)
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

    private fun createDoubleTarget(width: Int, height: Int, signedField: Boolean): DoubleTarget {
        return DoubleTarget(
            read = createTarget(width, height, signedField),
            write = createTarget(width, height, signedField)
        )
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
            GLES30.GL_RGBA,
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
            error("Fluid framebuffer incomplete: 0x${status.toString(16)}")
        }
        return RenderTarget(
            texture = textures[0],
            framebuffer = framebuffers[0],
            width = width,
            height = height,
            signedField = signedField
        )
    }

    private fun releaseDoubleTarget(target: DoubleTarget?) {
        if (target == null) return
        releaseTarget(target.read)
        releaseTarget(target.write)
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
        copyProgram = createProgram(COPY_FRAGMENT_SHADER)
        splatProgram = createProgram(SPLAT_FRAGMENT_SHADER)
        advectProgram = createProgram(ADVECT_FRAGMENT_SHADER)
        divergenceProgram = createProgram(DIVERGENCE_FRAGMENT_SHADER)
        curlProgram = createProgram(CURL_FRAGMENT_SHADER)
        vorticityProgram = createProgram(VORTICITY_FRAGMENT_SHADER)
        pressureProgram = createProgram(PRESSURE_FRAGMENT_SHADER)
        gradientSubtractProgram = createProgram(GRADIENT_SUBTRACT_FRAGMENT_SHADER)
        compositeProgram = createProgram(COMPOSITE_FRAGMENT_SHADER)
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
            error("Fluid shader program link failed: $log")
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
            error("Fluid shader compile failed: $log")
        }
        return shader
    }

    companion object {
        private const val VELOCITY_SPLAT_SCALE = 4.2f
        private const val VELOCITY_WATER_DRIFT_SCALE = 0.32f
        private const val WATER_PHASE_WRAP_SECONDS = 240f
        private const val SIGNED_FIELD_RANGE = 4f
        private const val MIN_DT_SECONDS = 1f / 120f
        private const val MAX_DT_SECONDS = 1f / 24f

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

        private const val COPY_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uTexture;
            out vec4 fragColor;
            void main() {
                fragColor = texture(uTexture, vUv);
            }
        """

        private const val SPLAT_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uTarget;
            uniform vec2 uPoint;
            uniform vec3 uValue;
            uniform float uAlpha;
            uniform float uRadius;
            uniform float uAspect;
            uniform float uIsDye;
            uniform float uTargetEncoded;
            uniform float uOutputEncoded;
            uniform float uSignedRange;
            out vec4 fragColor;
            vec4 decodeField(vec4 value, float encoded) {
                return encoded > 0.5 ? (value * 2.0 - 1.0) * uSignedRange : value;
            }
            vec4 encodeField(vec4 value, float encoded) {
                return encoded > 0.5 ? clamp(value / uSignedRange * 0.5 + 0.5, 0.0, 1.0) : value;
            }
            void main() {
                vec2 delta = vUv - uPoint;
                delta.x *= uAspect;
                float radius = max(uRadius, 0.0001);
                float falloff = exp(-dot(delta, delta) / radius);
                if (uIsDye > 0.5) {
                    const float MAX_DYE_DENSITY = 0.84;
                    const float INK_DEPOSIT_STRENGTH = 0.52;
                    vec4 base = texture(uTarget, vUv);
                    vec3 baseColor = base.a > 0.0001 ? base.rgb / base.a : uValue;
                    float deposit = clamp(uAlpha * falloff * INK_DEPOSIT_STRENGTH, 0.0, 0.42);
                    float remainingCapacity = max(MAX_DYE_DENSITY - base.a, 0.0);
                    float nextAlpha = base.a + min(deposit, remainingCapacity);
                    float colorWeight = clamp(deposit / (base.a + deposit + 0.0001), 0.0, 0.24);
                    vec3 mixedColor = mix(baseColor, uValue, colorWeight);
                    fragColor = vec4(mixedColor * nextAlpha, nextAlpha);
                } else {
                    vec4 base = decodeField(texture(uTarget, vUv), uTargetEncoded);
                    vec2 nextVelocity = clamp(base.xy + uValue.xy * falloff, vec2(-3.0), vec2(3.0));
                    fragColor = encodeField(vec4(nextVelocity, 0.0, 0.0), uOutputEncoded);
                }
            }
        """

        private const val ADVECT_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uVelocity;
            uniform sampler2D uSource;
            uniform vec2 uTexelSize;
            uniform float uDt;
            uniform float uDissipation;
            uniform float uWaterPhase;
            uniform float uWaterDrift;
            uniform float uVelocityEncoded;
            uniform float uSourceEncoded;
            uniform float uOutputEncoded;
            uniform float uSignedRange;
            out vec4 fragColor;
            vec4 decodeField(vec4 value, float encoded) {
                return encoded > 0.5 ? (value * 2.0 - 1.0) * uSignedRange : value;
            }
            vec4 encodeField(vec4 value, float encoded) {
                return encoded > 0.5 ? clamp(value / uSignedRange * 0.5 + 0.5, 0.0, 1.0) : value;
            }
            vec4 sampleVelocity(vec2 uv) {
                vec2 position = uv / uTexelSize - vec2(0.5);
                vec2 cell = floor(position);
                vec2 f = fract(position);
                vec2 uv00 = (cell + vec2(0.5, 0.5)) * uTexelSize;
                vec2 uv10 = (cell + vec2(1.5, 0.5)) * uTexelSize;
                vec2 uv01 = (cell + vec2(0.5, 1.5)) * uTexelSize;
                vec2 uv11 = (cell + vec2(1.5, 1.5)) * uTexelSize;
                uv00 = clamp(uv00, uTexelSize, 1.0 - uTexelSize);
                uv10 = clamp(uv10, uTexelSize, 1.0 - uTexelSize);
                uv01 = clamp(uv01, uTexelSize, 1.0 - uTexelSize);
                uv11 = clamp(uv11, uTexelSize, 1.0 - uTexelSize);
                vec4 c00 = decodeField(texture(uVelocity, uv00), uVelocityEncoded);
                vec4 c10 = decodeField(texture(uVelocity, uv10), uVelocityEncoded);
                vec4 c01 = decodeField(texture(uVelocity, uv01), uVelocityEncoded);
                vec4 c11 = decodeField(texture(uVelocity, uv11), uVelocityEncoded);
                return mix(mix(c00, c10, f.x), mix(c01, c11, f.x), f.y);
            }
            vec4 sampleSource(vec2 uv) {
                vec2 position = uv / uTexelSize - vec2(0.5);
                vec2 cell = floor(position);
                vec2 f = fract(position);
                vec2 uv00 = (cell + vec2(0.5, 0.5)) * uTexelSize;
                vec2 uv10 = (cell + vec2(1.5, 0.5)) * uTexelSize;
                vec2 uv01 = (cell + vec2(0.5, 1.5)) * uTexelSize;
                vec2 uv11 = (cell + vec2(1.5, 1.5)) * uTexelSize;
                uv00 = clamp(uv00, uTexelSize, 1.0 - uTexelSize);
                uv10 = clamp(uv10, uTexelSize, 1.0 - uTexelSize);
                uv01 = clamp(uv01, uTexelSize, 1.0 - uTexelSize);
                uv11 = clamp(uv11, uTexelSize, 1.0 - uTexelSize);
                vec4 c00 = decodeField(texture(uSource, uv00), uSourceEncoded);
                vec4 c10 = decodeField(texture(uSource, uv10), uSourceEncoded);
                vec4 c01 = decodeField(texture(uSource, uv01), uSourceEncoded);
                vec4 c11 = decodeField(texture(uSource, uv11), uSourceEncoded);
                return mix(mix(c00, c10, f.x), mix(c01, c11, f.x), f.y);
            }
            void main() {
                vec2 velocity = sampleVelocity(vUv).xy;
                float phase = uWaterPhase * 0.42;
                float folded = sin((vUv.x * 2.4 + vUv.y * 9.5) + phase * 0.8);
                float stream = sin(vUv.y * 7.0 + folded * 0.65 - phase * 1.15);
                float braid = sin((vUv.y * 15.0 - vUv.x * 3.2) + phase * 0.7);
                vec2 water = vec2(
                    stream * 0.019 + braid * 0.007,
                    cos(vUv.x * 6.2 - phase * 0.9 + folded * 0.35) * 0.011 +
                        sin(vUv.y * 10.0 + phase * 0.6) * 0.006
                ) * uWaterDrift;
                vec2 coord = clamp(vUv - (velocity + water) * uDt, uTexelSize, 1.0 - uTexelSize);
                vec4 value = sampleSource(coord) * uDissipation;
                fragColor = encodeField(value, uOutputEncoded);
            }
        """

        private const val CURL_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uVelocity;
            uniform vec2 uTexelSize;
            uniform float uVelocityEncoded;
            uniform float uOutputEncoded;
            uniform float uSignedRange;
            out vec4 fragColor;
            vec4 decodeField(vec4 value, float encoded) {
                return encoded > 0.5 ? (value * 2.0 - 1.0) * uSignedRange : value;
            }
            vec4 encodeField(vec4 value, float encoded) {
                return encoded > 0.5 ? clamp(value / uSignedRange * 0.5 + 0.5, 0.0, 1.0) : value;
            }
            void main() {
                float left = decodeField(texture(uVelocity, vUv - vec2(uTexelSize.x, 0.0)), uVelocityEncoded).y;
                float right = decodeField(texture(uVelocity, vUv + vec2(uTexelSize.x, 0.0)), uVelocityEncoded).y;
                float bottom = decodeField(texture(uVelocity, vUv - vec2(0.0, uTexelSize.y)), uVelocityEncoded).x;
                float top = decodeField(texture(uVelocity, vUv + vec2(0.0, uTexelSize.y)), uVelocityEncoded).x;
                float curl = right - left - top + bottom;
                fragColor = encodeField(vec4(curl * 0.5, 0.0, 0.0, 0.0), uOutputEncoded);
            }
        """

        private const val VORTICITY_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uVelocity;
            uniform sampler2D uCurl;
            uniform vec2 uTexelSize;
            uniform float uDt;
            uniform float uVorticity;
            uniform float uVelocityEncoded;
            uniform float uCurlEncoded;
            uniform float uOutputEncoded;
            uniform float uSignedRange;
            out vec4 fragColor;
            vec4 decodeField(vec4 value, float encoded) {
                return encoded > 0.5 ? (value * 2.0 - 1.0) * uSignedRange : value;
            }
            vec4 encodeField(vec4 value, float encoded) {
                return encoded > 0.5 ? clamp(value / uSignedRange * 0.5 + 0.5, 0.0, 1.0) : value;
            }
            void main() {
                float left = abs(decodeField(texture(uCurl, vUv - vec2(uTexelSize.x, 0.0)), uCurlEncoded).x);
                float right = abs(decodeField(texture(uCurl, vUv + vec2(uTexelSize.x, 0.0)), uCurlEncoded).x);
                float bottom = abs(decodeField(texture(uCurl, vUv - vec2(0.0, uTexelSize.y)), uCurlEncoded).x);
                float top = abs(decodeField(texture(uCurl, vUv + vec2(0.0, uTexelSize.y)), uCurlEncoded).x);
                float center = decodeField(texture(uCurl, vUv), uCurlEncoded).x;
                vec2 force = vec2(right - left, top - bottom) * 0.5;
                force = normalize(force + vec2(0.00001)) * center * uVorticity;
                force.y *= -1.0;
                vec2 velocity = decodeField(texture(uVelocity, vUv), uVelocityEncoded).xy + force * uDt;
                fragColor = encodeField(vec4(clamp(velocity, vec2(-3.0), vec2(3.0)), 0.0, 0.0), uOutputEncoded);
            }
        """

        private const val DIVERGENCE_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uVelocity;
            uniform vec2 uTexelSize;
            uniform float uVelocityEncoded;
            uniform float uOutputEncoded;
            uniform float uSignedRange;
            out vec4 fragColor;
            vec4 decodeField(vec4 value, float encoded) {
                return encoded > 0.5 ? (value * 2.0 - 1.0) * uSignedRange : value;
            }
            vec4 encodeField(vec4 value, float encoded) {
                return encoded > 0.5 ? clamp(value / uSignedRange * 0.5 + 0.5, 0.0, 1.0) : value;
            }
            void main() {
                float left = decodeField(texture(uVelocity, vUv - vec2(uTexelSize.x, 0.0)), uVelocityEncoded).x;
                float right = decodeField(texture(uVelocity, vUv + vec2(uTexelSize.x, 0.0)), uVelocityEncoded).x;
                float bottom = decodeField(texture(uVelocity, vUv - vec2(0.0, uTexelSize.y)), uVelocityEncoded).y;
                float top = decodeField(texture(uVelocity, vUv + vec2(0.0, uTexelSize.y)), uVelocityEncoded).y;
                float divergence = (right - left + top - bottom) * 0.5;
                fragColor = encodeField(vec4(divergence, 0.0, 0.0, 0.0), uOutputEncoded);
            }
        """

        private const val PRESSURE_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uPressure;
            uniform sampler2D uDivergence;
            uniform vec2 uTexelSize;
            uniform float uPressureEncoded;
            uniform float uDivergenceEncoded;
            uniform float uOutputEncoded;
            uniform float uSignedRange;
            out vec4 fragColor;
            vec4 decodeField(vec4 value, float encoded) {
                return encoded > 0.5 ? (value * 2.0 - 1.0) * uSignedRange : value;
            }
            vec4 encodeField(vec4 value, float encoded) {
                return encoded > 0.5 ? clamp(value / uSignedRange * 0.5 + 0.5, 0.0, 1.0) : value;
            }
            void main() {
                float left = decodeField(texture(uPressure, vUv - vec2(uTexelSize.x, 0.0)), uPressureEncoded).x;
                float right = decodeField(texture(uPressure, vUv + vec2(uTexelSize.x, 0.0)), uPressureEncoded).x;
                float bottom = decodeField(texture(uPressure, vUv - vec2(0.0, uTexelSize.y)), uPressureEncoded).x;
                float top = decodeField(texture(uPressure, vUv + vec2(0.0, uTexelSize.y)), uPressureEncoded).x;
                float divergence = decodeField(texture(uDivergence, vUv), uDivergenceEncoded).x;
                float pressure = (left + right + bottom + top - divergence) * 0.25;
                fragColor = encodeField(vec4(pressure, 0.0, 0.0, 0.0), uOutputEncoded);
            }
        """

        private const val GRADIENT_SUBTRACT_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uVelocity;
            uniform sampler2D uPressure;
            uniform vec2 uTexelSize;
            uniform float uVelocityEncoded;
            uniform float uPressureEncoded;
            uniform float uOutputEncoded;
            uniform float uSignedRange;
            out vec4 fragColor;
            vec4 decodeField(vec4 value, float encoded) {
                return encoded > 0.5 ? (value * 2.0 - 1.0) * uSignedRange : value;
            }
            vec4 encodeField(vec4 value, float encoded) {
                return encoded > 0.5 ? clamp(value / uSignedRange * 0.5 + 0.5, 0.0, 1.0) : value;
            }
            void main() {
                float left = decodeField(texture(uPressure, vUv - vec2(uTexelSize.x, 0.0)), uPressureEncoded).x;
                float right = decodeField(texture(uPressure, vUv + vec2(uTexelSize.x, 0.0)), uPressureEncoded).x;
                float bottom = decodeField(texture(uPressure, vUv - vec2(0.0, uTexelSize.y)), uPressureEncoded).x;
                float top = decodeField(texture(uPressure, vUv + vec2(0.0, uTexelSize.y)), uPressureEncoded).x;
                vec2 velocity = decodeField(texture(uVelocity, vUv), uVelocityEncoded).xy;
                velocity -= vec2(right - left, top - bottom) * 0.5;
                fragColor = encodeField(vec4(clamp(velocity, vec2(-3.0), vec2(3.0)), 0.0, 0.0), uOutputEncoded);
            }
        """

        private const val COMPOSITE_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uDye;
            out vec4 fragColor;
            void main() {
                vec4 dye = texture(uDye, vUv);
                float alpha = clamp(pow(dye.a, 0.86) * 1.08, 0.0, 0.86);
                vec3 color = dye.a > 0.0001 ? dye.rgb / dye.a : vec3(0.0);
                float maxChannel = max(max(color.r, color.g), color.b);
                if (maxChannel > 0.72) {
                    color *= 0.72 / maxChannel;
                }
                fragColor = vec4(color * alpha, alpha);
            }
        """
    }
}
