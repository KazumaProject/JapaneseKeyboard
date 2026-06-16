package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal class CinematicWaveSimulation {
    private var program = 0
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var timeSeconds = 0f
    private var settings = CinematicWaveSettings.Disabled

    private var uResolution = -1
    private var uTime = -1
    private var uOpacity = -1
    private var uIntensity = -1
    private var uMotion = -1
    private var uNoiseOctaves = -1
    private var uGlowStrength = -1
    private var uWarpStrength = -1
    private var uBaseColor = -1
    private var uPrimaryColor = -1
    private var uSecondaryColor = -1
    private var uHighlightColor = -1
    private var uTouchCount = -1
    private var uTouchPositions = -1
    private var uTouchAges = -1
    private var uTouchStrengths = -1

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
        settings: CinematicWaveSettings
    ) {
        require(surfaceWidth > 0 && surfaceHeight > 0) {
            "Invalid cinematic wave surface size ${surfaceWidth}x$surfaceHeight"
        }
        if (program == 0) {
            createProgram()
        }
        this.surfaceWidth = surfaceWidth
        this.surfaceHeight = surfaceHeight
        configure(settings)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        clear()
    }

    fun configure(settings: CinematicWaveSettings) {
        this.settings = settings
    }

    fun resizeSurface(surfaceWidth: Int, surfaceHeight: Int) {
        if (program == 0) {
            initialize(surfaceWidth, surfaceHeight, settings)
            return
        }
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return
        this.surfaceWidth = surfaceWidth
        this.surfaceHeight = surfaceHeight
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
    }

    fun render(
        dtSeconds: Float,
        params: CinematicWaveStepParams,
        palette: CinematicWavePalette,
        touches: List<CinematicWaveTouchSnapshot>
    ) {
        if (program == 0 || surfaceWidth <= 0 || surfaceHeight <= 0) return
        val clampedDt = dtSeconds.coerceIn(MIN_DT_SECONDS, MAX_DT_SECONDS)
        timeSeconds = (timeSeconds + clampedDt * params.motionSpeed).let {
            if (it > TIME_WRAP_SECONDS) it - TIME_WRAP_SECONDS else it
        }

        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)

        GLES30.glUniform2f(uResolution, surfaceWidth.toFloat(), surfaceHeight.toFloat())
        GLES30.glUniform1f(uTime, timeSeconds)
        GLES30.glUniform1f(uOpacity, settings.opacity)
        GLES30.glUniform1f(uIntensity, settings.intensity)
        GLES30.glUniform1f(uMotion, params.motionSpeed)
        GLES30.glUniform1i(uNoiseOctaves, params.noiseOctaves)
        GLES30.glUniform1f(uGlowStrength, params.glowStrength)
        GLES30.glUniform1f(uWarpStrength, params.warpStrength)
        palette.base.uniform(uBaseColor)
        palette.primary.uniform(uPrimaryColor)
        palette.secondary.uniform(uSecondaryColor)
        palette.highlight.uniform(uHighlightColor)
        uploadTouches(touches, params)

        quadVertices.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(
            0,
            2,
            GLES30.GL_FLOAT,
            false,
            2 * Float.SIZE_BYTES,
            quadVertices
        )
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glUseProgram(0)
    }

    fun clear() {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    fun release() {
        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }
        surfaceWidth = 0
        surfaceHeight = 0
    }

    private fun uploadTouches(
        touches: List<CinematicWaveTouchSnapshot>,
        params: CinematicWaveStepParams
    ) {
        val count = minOf(MAX_TOUCHES, touches.size)
        GLES30.glUniform1i(uTouchCount, count)
        val positions = FloatArray(MAX_TOUCHES * 2)
        val ages = FloatArray(MAX_TOUCHES)
        val strengths = FloatArray(MAX_TOUCHES)
        for (index in 0 until count) {
            val touch = touches[index]
            positions[index * 2] = touch.x
            positions[index * 2 + 1] = touch.y
            ages[index] = touch.ageSeconds
            strengths[index] = touch.strength * params.touchResponse
        }
        GLES30.glUniform2fv(uTouchPositions, MAX_TOUCHES, positions, 0)
        GLES30.glUniform1fv(uTouchAges, MAX_TOUCHES, ages, 0)
        GLES30.glUniform1fv(uTouchStrengths, MAX_TOUCHES, strengths, 0)
    }

    private fun CinematicWaveColor.uniform(location: Int) {
        GLES30.glUniform3f(location, red, green, blue)
    }

    private fun createProgram() {
        program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uResolution = GLES30.glGetUniformLocation(program, "uResolution")
        uTime = GLES30.glGetUniformLocation(program, "uTime")
        uOpacity = GLES30.glGetUniformLocation(program, "uOpacity")
        uIntensity = GLES30.glGetUniformLocation(program, "uIntensity")
        uMotion = GLES30.glGetUniformLocation(program, "uMotion")
        uNoiseOctaves = GLES30.glGetUniformLocation(program, "uNoiseOctaves")
        uGlowStrength = GLES30.glGetUniformLocation(program, "uGlowStrength")
        uWarpStrength = GLES30.glGetUniformLocation(program, "uWarpStrength")
        uBaseColor = GLES30.glGetUniformLocation(program, "uBaseColor")
        uPrimaryColor = GLES30.glGetUniformLocation(program, "uPrimaryColor")
        uSecondaryColor = GLES30.glGetUniformLocation(program, "uSecondaryColor")
        uHighlightColor = GLES30.glGetUniformLocation(program, "uHighlightColor")
        uTouchCount = GLES30.glGetUniformLocation(program, "uTouchCount")
        uTouchPositions = GLES30.glGetUniformLocation(program, "uTouchPositions[0]")
        uTouchAges = GLES30.glGetUniformLocation(program, "uTouchAges[0]")
        uTouchStrengths = GLES30.glGetUniformLocation(program, "uTouchStrengths[0]")
    }

    private fun linkProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val nextProgram = GLES30.glCreateProgram()
        GLES30.glAttachShader(nextProgram, vertexShader)
        GLES30.glAttachShader(nextProgram, fragmentShader)
        GLES30.glBindAttribLocation(nextProgram, 0, "aPosition")
        GLES30.glLinkProgram(nextProgram)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(nextProgram, GLES30.GL_LINK_STATUS, linkStatus, 0)
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(nextProgram)
            GLES30.glDeleteProgram(nextProgram)
            error("Cinematic wave shader program link failed: $log")
        }
        return nextProgram
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
            error("Cinematic wave shader compile failed: $log")
        }
        return shader
    }

    companion object {
        private const val MAX_TOUCHES = 5
        private const val MIN_DT_SECONDS = 1f / 120f
        private const val MAX_DT_SECONDS = 1f / 18f
        private const val TIME_WRAP_SECONDS = 600f

        private val QUAD = floatArrayOf(
            -1f,
            -1f,
            1f,
            -1f,
            -1f,
            1f,
            1f,
            1f
        )

        private const val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec2 aPosition;
            out vec2 vUv;

            void main() {
                vUv = aPosition * 0.5 + 0.5;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;

            in vec2 vUv;
            out vec4 fragColor;

            uniform vec2 uResolution;
            uniform float uTime;
            uniform float uOpacity;
            uniform float uIntensity;
            uniform float uMotion;
            uniform int uNoiseOctaves;
            uniform float uGlowStrength;
            uniform float uWarpStrength;
            uniform vec3 uBaseColor;
            uniform vec3 uPrimaryColor;
            uniform vec3 uSecondaryColor;
            uniform vec3 uHighlightColor;
            uniform int uTouchCount;
            uniform vec2 uTouchPositions[5];
            uniform float uTouchAges[5];
            uniform float uTouchStrengths[5];

            float hash(vec2 p) {
                vec3 p3 = fract(vec3(p.xyx) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }

            float noise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                vec2 u = f * f * (3.0 - 2.0 * f);
                float a = hash(i);
                float b = hash(i + vec2(1.0, 0.0));
                float c = hash(i + vec2(0.0, 1.0));
                float d = hash(i + vec2(1.0, 1.0));
                return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
            }

            float fbm(vec2 p) {
                float value = 0.0;
                float amplitude = 0.5;
                mat2 rotate = mat2(0.82, -0.58, 0.58, 0.82);
                for (int i = 0; i < 5; i++) {
                    if (i >= uNoiseOctaves) {
                        break;
                    }
                    value += amplitude * noise(p);
                    p = rotate * p * 2.04 + vec2(7.1, 3.4);
                    amplitude *= 0.52;
                }
                return value;
            }

            float ridge(float x) {
                float v = 1.0 - abs(x);
                return smoothstep(0.08, 0.96, v);
            }

            void main() {
                vec2 uv = gl_FragCoord.xy / max(uResolution, vec2(1.0));
                vec2 centered = uv - 0.5;
                centered.x *= uResolution.x / max(uResolution.y, 1.0);

                float slowTime = uTime * uMotion;
                vec2 warp = vec2(
                    fbm(uv * 2.0 + vec2(slowTime * 0.050, -slowTime * 0.018)),
                    fbm(uv * 2.5 - vec2(slowTime * 0.034, slowTime * 0.043))
                ) - 0.5;
                vec2 warpedUv = uv + warp * 0.072 * uWarpStrength;

                float touchGlow = 0.0;
                float touchShadow = 0.0;
                for (int i = 0; i < 5; i++) {
                    if (i >= uTouchCount) {
                        break;
                    }
                    vec2 touchUv = uTouchPositions[i];
                    vec2 delta = warpedUv - touchUv;
                    delta.x *= uResolution.x / max(uResolution.y, 1.0);
                    float d = length(delta);
                    vec2 dir = normalize(delta + vec2(0.0001));
                    float ageSoftness = 1.0 / (1.0 + uTouchAges[i] * 0.18);
                    float strength = clamp(uTouchStrengths[i], 0.0, 1.8) * ageSoftness;
                    float lens = exp(-d * d * 42.0) * strength;
                    warpedUv += dir * lens * 0.040;
                    warpedUv += vec2(-dir.y, dir.x) * lens * 0.018;
                    touchGlow += exp(-d * d * 52.0) * strength;
                    touchShadow += exp(-d * d * 18.0) * strength;
                }

                vec2 drift = warpedUv;
                drift += vec2(
                    fbm(warpedUv * 4.2 + slowTime * 0.045),
                    fbm(warpedUv * 3.6 - slowTime * 0.052)
                ) * 0.060 * uWarpStrength;

                float w1 = sin(drift.x * 8.0 + drift.y * 3.0 + slowTime * 0.35);
                float w2 = sin(drift.x * 14.0 - drift.y * 2.0 - slowTime * 0.22);
                float w3 = sin((drift.x + drift.y) * 10.5 + fbm(drift * 2.7) * 3.8 + slowTime * 0.18);
                float field = w1 * 0.50 + w2 * 0.31 + w3 * 0.19 + (fbm(drift * 4.0) - 0.5) * 0.54;
                float ridgeA = ridge(field);
                float ridgeB = ridge(field * 0.74 + fbm(drift * 5.8 + slowTime * 0.055) * 0.72 - 0.18);
                float glow = pow(clamp(ridgeA * 0.76 + ridgeB * 0.42, 0.0, 1.0), 2.12);

                float depth = smoothstep(0.0, 1.0, uv.y);
                vec3 base = mix(uBaseColor * 0.50, uBaseColor * 1.34, depth);
                base += vec3(0.018, 0.022, 0.030) * (1.0 - length(centered) * 0.36);

                float veil = smoothstep(-0.34, 0.72, field) * (1.0 - smoothstep(0.10, 1.22, abs(centered.y)));
                vec3 color = base;
                color += uPrimaryColor * glow * 0.58 * uGlowStrength * uIntensity;
                color += uSecondaryColor * ridgeB * 0.30 * uIntensity;
                color += mix(uPrimaryColor, uSecondaryColor, fbm(drift * 2.1)) * veil * 0.16;
                color += uHighlightColor * pow(glow, 3.0) * 0.30 * uGlowStrength;
                color += uHighlightColor * touchGlow * 0.16 * uIntensity;
                color -= uBaseColor * touchShadow * 0.045;

                float glass = 0.085 + (1.0 - smoothstep(0.0, 0.92, length(centered))) * 0.055;
                float alpha = glass + glow * 0.42 * uIntensity + veil * 0.10 + touchGlow * 0.10;
                alpha = clamp(alpha * uOpacity, 0.0, 0.62);
                color = clamp(color, vec3(0.0), vec3(1.0));
                fragColor = vec4(color * alpha, alpha);
            }
        """
    }
}
