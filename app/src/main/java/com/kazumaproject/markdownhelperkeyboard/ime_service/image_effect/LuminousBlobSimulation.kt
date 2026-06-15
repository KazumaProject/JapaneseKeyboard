package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

internal class LuminousBlobSimulation {
    private data class RenderTarget(
        val texture: Int,
        val framebuffer: Int,
        val width: Int,
        val height: Int
    )

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var renderTarget: RenderTarget? = null
    private var timeSeconds = 0f
    private var pointerX = 0.5f
    private var pointerY = 0.5f
    private var pointerStrength = 0f
    private var pointerInitialized = false
    private var stableBaseRadiusSurfacePx = 0f
    private var colorSet = LuminousBlobColorSet.Default

    private var blobProgram = 0
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
        params: LuminousBlobStepParams,
        settings: LuminousBlobSettings
    ) {
        require(surfaceWidth > 0 && surfaceHeight > 0) {
            "Invalid luminous blob surface size ${surfaceWidth}x$surfaceHeight"
        }
        if (blobProgram == 0) {
            createPrograms()
        }
        configure(settings)
        this.surfaceWidth = surfaceWidth
        this.surfaceHeight = surfaceHeight
        stableBaseRadiusSurfacePx = calculateContainedBaseRadius(surfaceWidth, surfaceHeight)
        resizeTarget(params.renderScale)
        clearSurface()
    }

    fun configure(settings: LuminousBlobSettings) {
        colorSet = defaultColorSetFor(settings)
    }

    fun resizeSurface(
        surfaceWidth: Int,
        surfaceHeight: Int,
        params: LuminousBlobStepParams
    ) {
        if (blobProgram == 0) return
        val previousWidth = this.surfaceWidth
        val previousHeight = this.surfaceHeight
        this.surfaceWidth = surfaceWidth
        this.surfaceHeight = surfaceHeight
        stableBaseRadiusSurfacePx = resolveStableBaseRadiusAfterResize(
            currentRadius = stableBaseRadiusSurfacePx,
            previousWidth = previousWidth,
            previousHeight = previousHeight,
            newWidth = surfaceWidth,
            newHeight = surfaceHeight
        )
        resizeTarget(params.renderScale)
    }

    fun render(
        pointer: LuminousBlobPointerSnapshot?,
        dtSeconds: Float,
        params: LuminousBlobStepParams
    ): Boolean {
        if (surfaceWidth <= 0 || surfaceHeight <= 0 || blobProgram == 0) return false
        resizeTarget(params.renderScale)
        val target = renderTarget ?: return false
        val clampedDt = dtSeconds.coerceIn(MIN_DT_SECONDS, MAX_DT_SECONDS)
        timeSeconds = (timeSeconds + clampedDt).let {
            if (it > TIME_WRAP_SECONDS) it - TIME_WRAP_SECONDS else it
        }
        updatePointer(pointer, clampedDt, params)
        drawBlob(target, params)
        blitToSurface(target)
        return true
    }

    fun hasResidualTouch(threshold: Float): Boolean {
        return pointerStrength > threshold
    }

    fun cancelTouch() {
        pointerStrength = 0f
        pointerInitialized = false
    }

    fun clear() {
        pointerStrength = 0f
        pointerInitialized = false
        clearRenderTarget()
        clearSurface()
    }

    fun release() {
        releaseTarget(renderTarget)
        renderTarget = null
        stableBaseRadiusSurfacePx = 0f
        val programs = intArrayOf(blobProgram, copyProgram).filter { it != 0 }.toIntArray()
        programs.forEach(GLES30::glDeleteProgram)
        blobProgram = 0
        copyProgram = 0
    }

    private fun updatePointer(
        pointer: LuminousBlobPointerSnapshot?,
        dtSeconds: Float,
        params: LuminousBlobStepParams
    ) {
        if (pointer != null) {
            val targetX = (pointer.x / surfaceWidth).coerceIn(0f, 1f)
            val targetY = (1f - pointer.y / surfaceHeight).coerceIn(0f, 1f)
            val speedX = pointer.velocityX * POINTER_VELOCITY_LEAD_MS / surfaceWidth
            val speedY = -pointer.velocityY * POINTER_VELOCITY_LEAD_MS / surfaceHeight
            val ledX = (targetX + speedX).coerceIn(0f, 1f)
            val ledY = (targetY + speedY).coerceIn(0f, 1f)
            val follow = exponentialApproach(params.pointerRisePerSecond, dtSeconds)
            if (!pointerInitialized) {
                pointerX = ledX
                pointerY = ledY
                pointerInitialized = true
            } else {
                pointerX += (ledX - pointerX) * follow
                pointerY += (ledY - pointerY) * follow
            }
            pointerStrength += (1f - pointerStrength) * follow
            colorSet = pointer.colorSet
        } else {
            val decay = exp(-params.pointerDecayPerSecond * dtSeconds)
            pointerStrength *= decay
            if (pointerStrength < MIN_POINTER_STRENGTH) {
                pointerStrength = 0f
                pointerInitialized = false
            }
        }
    }

    private fun exponentialApproach(ratePerSecond: Float, dtSeconds: Float): Float {
        return (1f - exp(-ratePerSecond * dtSeconds)).coerceIn(0f, 1f)
    }

    private fun drawBlob(target: RenderTarget, params: LuminousBlobStepParams) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebuffer)
        GLES30.glViewport(0, 0, target.width, target.height)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(blobProgram)

        val baseRadius = baseRadiusForTarget(target)
        val centerX = target.width * 0.5f
        val centerY = target.height * 0.5f
        val pointerPx = pointerX * target.width
        val pointerPy = pointerY * target.height
        val shadow1X = 0.22f + sin(timeSeconds * 0.23f) * 0.08f
        val shadow1Y = -0.16f + cos(timeSeconds * 0.19f) * 0.07f
        val shadow2X = -0.28f + cos(timeSeconds * 0.17f) * 0.08f
        val shadow2Y = 0.20f + sin(timeSeconds * 0.29f) * 0.06f

        uniform2f(blobProgram, "uResolution", target.width.toFloat(), target.height.toFloat())
        uniform2f(blobProgram, "uCenter", centerX, centerY)
        uniform2f(blobProgram, "uPointer", pointerPx, pointerPy)
        uniform2f(blobProgram, "uShadow1", shadow1X, shadow1Y)
        uniform2f(blobProgram, "uShadow2", shadow2X, shadow2Y)
        uniform3f(blobProgram, "uBaseColor", colorSet.base)
        uniform3f(blobProgram, "uEdgeColor", colorSet.edge)
        uniform3f(blobProgram, "uDeepColor", colorSet.deep)
        uniform1f(blobProgram, "uTime", timeSeconds)
        uniform1f(blobProgram, "uBaseRadius", baseRadius)
        uniform1f(blobProgram, "uPointerStrength", pointerStrength)
        uniform1f(
            blobProgram,
            "uPointerGlowRadius",
            minOf(target.width, target.height) * params.pointerGlowRadiusScale
        )
        uniform1f(blobProgram, "uOuterGlowReach", OUTER_GLOW_REACH)
        uniform1f(blobProgram, "uEdgeSharpness", params.edgeSharpness)
        uniform1f(blobProgram, "uInnerStrength", params.innerStrength)
        uniform1f(blobProgram, "uShadowStrength", params.shadowStrength)
        uniform1f(blobProgram, "uDriftSpeedScale", params.driftSpeedScale)
        uniform1f(blobProgram, "uMaxAlpha", params.maxAlpha * colorSet.base.alpha)
        drawQuad()
    }

    private fun baseRadiusForTarget(target: RenderTarget): Float {
        val radiusSurfacePx = stableBaseRadiusSurfacePx.takeIf { it > 0f }
            ?: calculateContainedBaseRadius(surfaceWidth, surfaceHeight)
        val scaleX = if (surfaceWidth > 0) {
            target.width / surfaceWidth.toFloat()
        } else {
            1f
        }
        val scaleY = if (surfaceHeight > 0) {
            target.height / surfaceHeight.toFloat()
        } else {
            1f
        }
        return radiusSurfacePx * minOf(scaleX, scaleY)
    }

    private fun blitToSurface(target: RenderTarget) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(copyProgram)
        bindTexture(0, target.texture, copyProgram, "uTexture")
        drawQuad()
    }

    private fun resizeTarget(renderScale: Float) {
        val targetWidth = (surfaceWidth * renderScale).roundToInt().coerceAtLeast(MIN_TARGET_SIDE)
        val targetHeight = (surfaceHeight * renderScale).roundToInt().coerceAtLeast(MIN_TARGET_SIDE)
        val current = renderTarget
        if (current != null && current.width == targetWidth && current.height == targetHeight) {
            return
        }
        releaseTarget(current)
        renderTarget = createTarget(targetWidth, targetHeight)
    }

    private fun clearRenderTarget() {
        val target = renderTarget ?: return
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebuffer)
        GLES30.glViewport(0, 0, target.width, target.height)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    private fun clearSurface() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        }
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
            error("Luminous blob framebuffer incomplete: 0x${status.toString(16)}")
        }
        return RenderTarget(
            texture = textures[0],
            framebuffer = framebuffers[0],
            width = width,
            height = height
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

    private fun uniform3f(program: Int, name: String, color: LuminousBlobColor) {
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, name), color.red, color.green, color.blue)
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
        blobProgram = createProgram(BLOB_FRAGMENT_SHADER)
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
            error("Luminous blob shader program link failed: $log")
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
            error("Luminous blob shader compile failed: $log")
        }
        return shader
    }

    private fun defaultColorSetFor(settings: LuminousBlobSettings): LuminousBlobColorSet {
        return when (settings.normalizedColorMode) {
            LuminousBlobSettings.COLOR_MODE_FIXED,
            LuminousBlobSettings.COLOR_MODE_THEME -> LuminousBlobColorSet.fromBase(
                LuminousBlobColor.fromColorInt(settings.fixedColor)
            )

            else -> LuminousBlobColorSet.Default
        }
    }

    companion object {
        private const val PREFERRED_BASE_RADIUS_SCALE = 0.34f
        private const val MAX_BOUNDARY_WOBBLE = 0.23f
        private const val MAX_POINTER_PULL = 0.28f
        private const val OUTER_GLOW_REACH = 0.22f
        private const val CENTER_SAFETY_SCALE = 0.94f
        private const val STRUCTURAL_WIDTH_CHANGE_RATIO = 0.08f
        private const val MIN_STRUCTURAL_WIDTH_CHANGE_PX = 12
        private const val MIN_DT_SECONDS = 1f / 120f
        private const val MAX_DT_SECONDS = 1f / 18f
        private const val TIME_WRAP_SECONDS = 600f
        private const val MIN_TARGET_SIDE = 16
        private const val MIN_POINTER_STRENGTH = 0.002f
        private const val POINTER_VELOCITY_LEAD_MS = 72f

        internal fun calculateContainedBaseRadius(width: Int, height: Int): Float {
            if (width <= 0 || height <= 0) return 0f
            val shortSide = minOf(width, height).toFloat()
            val preferredRadius = shortSide * PREFERRED_BASE_RADIUS_SCALE
            val maxVisualExtent =
                1f + MAX_BOUNDARY_WOBBLE + MAX_POINTER_PULL + OUTER_GLOW_REACH
            val containedRadius = (shortSide * 0.5f * CENTER_SAFETY_SCALE) / maxVisualExtent
            return minOf(preferredRadius, containedRadius).coerceAtLeast(1f)
        }

        internal fun resolveStableBaseRadiusAfterResize(
            currentRadius: Float,
            previousWidth: Int,
            previousHeight: Int,
            newWidth: Int,
            newHeight: Int
        ): Float {
            return if (
                currentRadius <= 0f ||
                shouldRecalculateStableBaseRadius(previousWidth, previousHeight, newWidth, newHeight)
            ) {
                calculateContainedBaseRadius(newWidth, newHeight)
            } else {
                currentRadius
            }
        }

        internal fun shouldRecalculateStableBaseRadius(
            previousWidth: Int,
            previousHeight: Int,
            newWidth: Int,
            newHeight: Int
        ): Boolean {
            if (previousWidth <= 0 || previousHeight <= 0 || newWidth <= 0 || newHeight <= 0) {
                return true
            }
            val orientationChanged = (previousWidth >= previousHeight) != (newWidth >= newHeight)
            if (orientationChanged) return true

            val widthDelta = abs(newWidth - previousWidth)
            val structuralWidthDelta = maxOf(
                MIN_STRUCTURAL_WIDTH_CHANGE_PX,
                (previousWidth * STRUCTURAL_WIDTH_CHANGE_RATIO).roundToInt()
            )
            return widthDelta >= structuralWidthDelta
        }

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

        private const val BLOB_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            uniform vec2 uResolution;
            uniform vec2 uCenter;
            uniform vec2 uPointer;
            uniform vec2 uShadow1;
            uniform vec2 uShadow2;
            uniform vec3 uBaseColor;
            uniform vec3 uEdgeColor;
            uniform vec3 uDeepColor;
            uniform float uTime;
            uniform float uBaseRadius;
            uniform float uPointerStrength;
            uniform float uPointerGlowRadius;
            uniform float uOuterGlowReach;
            uniform float uEdgeSharpness;
            uniform float uInnerStrength;
            uniform float uShadowStrength;
            uniform float uDriftSpeedScale;
            uniform float uMaxAlpha;
            out vec4 fragColor;

            void main() {
                vec2 fragCoord = vUv * uResolution;
                vec2 center = uCenter;
                vec2 p = (fragCoord - center) / max(uBaseRadius, 1.0);
                float angle = atan(p.y, p.x);
                float radius = length(p);

                vec2 pointerDir = normalize(uPointer - center + vec2(0.0001, 0.0));
                vec2 dir = normalize(fragCoord - center + vec2(0.0001, 0.0));
                float alignment = max(dot(pointerDir, dir), 0.0);
                float pull = alignment * uPointerStrength * 0.28;

                float boundary =
                    1.0
                    + 0.12 * sin(angle * 2.0 + uTime * 0.7 * uDriftSpeedScale)
                    + 0.07 * sin(angle * 3.0 - uTime * 0.45 * uDriftSpeedScale)
                    + 0.04 * sin(angle * 5.0 + uTime * 0.25 * uDriftSpeedScale);
                boundary += pull;

                float sdf = radius - boundary;
                float edgeGlow = exp(-abs(sdf) * uEdgeSharpness);
                float outerGlowMask = 1.0 - smoothstep(0.0, uOuterGlowReach, abs(sdf));
                float outerGlow =
                    exp(-max(sdf, 0.0) * 5.8) *
                    outerGlowMask;

                float inside = smoothstep(0.03, -0.18, sdf);
                float cloud =
                    0.55
                    + 0.25 * sin(p.x * 4.0 + uTime * 0.8 * uDriftSpeedScale)
                    + 0.20 * sin(p.y * 3.0 - uTime * 0.6 * uDriftSpeedScale)
                    + 0.15 * sin((p.x + p.y) * 5.0 + uTime * 0.35 * uDriftSpeedScale);
                float innerGlow = inside * clamp(cloud, 0.0, 1.0) * uInnerStrength;

                float shadow1 = exp(-length(p - uShadow1) * 4.0);
                float shadow2 = exp(-length(p - uShadow2) * 4.5);
                float shadow = clamp(shadow1 + shadow2, 0.0, 1.0) * uShadowStrength;
                innerGlow *= 1.0 - shadow * 0.75;

                float pointerGlow =
                    exp(-distance(fragCoord, uPointer) / max(uPointerGlowRadius, 1.0)) *
                    uPointerStrength;
                float pointerMembraneMask = 1.0 - smoothstep(-0.04, uOuterGlowReach, sdf);
                pointerGlow *= pointerMembraneMask;
                innerGlow += pointerGlow * 0.35;

                float membrane = clamp(innerGlow, 0.0, 1.0);
                float rim = pow(clamp(edgeGlow, 0.0, 1.0), 1.12);
                float shadowAlpha = inside * shadow * 0.10;
                vec3 color =
                    uDeepColor * membrane * 0.35
                    + uBaseColor * membrane * 0.65
                    + uEdgeColor * rim * 0.92
                    + uEdgeColor * pointerGlow * 0.28
                    + vec3(0.012, 0.010, 0.0) * shadowAlpha;
                float alpha = clamp(
                    membrane * 0.32
                    + rim * 0.34
                    + outerGlow * 0.08
                    + pointerGlow * 0.12
                    + shadowAlpha,
                    0.0,
                    uMaxAlpha
                );
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
