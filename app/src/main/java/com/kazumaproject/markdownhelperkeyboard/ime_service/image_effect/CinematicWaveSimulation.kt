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
    private var uWaveType = -1
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
        GLES30.glUniform1i(uWaveType, waveTypeUniformValue())
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

    private fun waveTypeUniformValue(): Int {
        return when (settings.normalizedWaveType) {
            CinematicWaveSettings.WAVE_TYPE_SILK_SINE -> 1
            CinematicWaveSettings.WAVE_TYPE_PRISMATIC_SINE -> 2
            CinematicWaveSettings.WAVE_TYPE_SPECTRUM_SINE -> 3
            CinematicWaveSettings.WAVE_TYPE_CHROMA_FOLD -> 4
            else -> 0
        }
    }

    private fun createProgram() {
        program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uResolution = GLES30.glGetUniformLocation(program, "uResolution")
        uTime = GLES30.glGetUniformLocation(program, "uTime")
        uWaveType = GLES30.glGetUniformLocation(program, "uWaveType")
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
            uniform int uWaveType;
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

            float ribbon(float y, float center, float width) {
                float d = (y - center) / max(width, 0.0001);
                return exp(-d * d);
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

                if (uWaveType == 2) {
                    vec2 sineUv = mix(uv, warpedUv, 0.42);
                    float x = sineUv.x;
                    float y = sineUv.y;
                    float centerA = 0.54 + sin(x * 5.35 - slowTime * 0.32 + 0.20) * 0.245;
                    float centerB = 0.47 + sin(x * 4.68 + slowTime * 0.26 + 2.12) * 0.220;
                    float centerC = 0.61 + sin(x * 6.10 - slowTime * 0.21 + 3.78) * 0.175;
                    float centerD = 0.38 + sin(x * 3.92 + slowTime * 0.18 + 5.18) * 0.205;

                    float bandA = ribbon(y, centerA, 0.135);
                    float bandB = ribbon(y, centerB, 0.122);
                    float bandC = ribbon(y, centerC, 0.108);
                    float bandD = ribbon(y, centerD, 0.116);
                    float coreA = ribbon(y, centerA, 0.025);
                    float coreB = ribbon(y, centerB, 0.023);
                    float coreC = ribbon(y, centerC, 0.020);
                    float coreD = ribbon(y, centerD, 0.022);

                    float prismaticCrossing = smoothstep(
                        0.035,
                        0.62,
                        bandA * bandB + bandA * bandC + bandB * bandD + bandC * bandD
                    );
                    float satinBody = clamp(
                        bandA * 0.46 + bandB * 0.42 + bandC * 0.36 + bandD * 0.34,
                        0.0,
                        1.0
                    );
                    float brightCore = clamp(
                        coreA * 0.74 + coreB * 0.68 + coreC * 0.56 + coreD * 0.50,
                        0.0,
                        1.0
                    );

                    vec3 blueRibbon = mix(uPrimaryColor, vec3(0.30, 0.66, 1.00), 0.58);
                    vec3 greenRibbon = mix(uSecondaryColor, vec3(0.24, 0.96, 0.48), 0.46);
                    vec3 roseRibbon = mix(uSecondaryColor, vec3(1.00, 0.24, 0.42), 0.55);
                    vec3 goldRibbon = mix(uHighlightColor, vec3(1.00, 0.70, 0.20), 0.28);
                    float vignette = 1.0 - smoothstep(0.26, 1.06, length(centered));
                    vec3 base = uBaseColor * (0.32 + 0.34 * vignette);

                    vec3 color = base;
                    color += blueRibbon * bandA * 0.54 * uIntensity;
                    color += greenRibbon * bandB * 0.42 * uIntensity;
                    color += roseRibbon * bandC * 0.48 * uIntensity;
                    color += goldRibbon * bandD * 0.25 * uIntensity;
                    color += uHighlightColor * pow(brightCore, 1.85) * 0.72 * uGlowStrength;
                    color += uHighlightColor * prismaticCrossing * 0.78 * uGlowStrength;
                    color += uHighlightColor * touchGlow * 0.13 * uIntensity;
                    color -= uBaseColor * touchShadow * 0.030;

                    float glass = 0.055 + vignette * 0.040;
                    float alpha = glass + satinBody * 0.33 * uIntensity + brightCore * 0.28 * uGlowStrength;
                    alpha += prismaticCrossing * 0.18 + touchGlow * 0.075;
                    alpha = clamp(alpha * uOpacity, 0.0, 0.66);
                    color = clamp(color, vec3(0.0), vec3(1.0));
                    fragColor = vec4(color * alpha, alpha);
                    return;
                }

                if (uWaveType == 3) {
                    vec2 spectrumSheetUv = uv + (warpedUv - uv) * 0.58;
                    float x = spectrumSheetUv.x;
                    float y = spectrumSheetUv.y;
                    float horizon = smoothstep(0.06, 0.92, x) * (1.0 - smoothstep(0.82, 1.04, x));

                    float blueAxis = 0.62 + sin(x * 5.02 - slowTime * 0.20 + 0.05) * 0.255;
                    float cyanAxis = 0.55 + sin(x * 5.82 + slowTime * 0.16 + 1.16) * 0.225;
                    float greenAxis = 0.36 + sin(x * 4.72 - slowTime * 0.13 + 2.54) * 0.238;
                    float roseAxis = 0.46 + sin(x * 5.30 + slowTime * 0.18 + 4.04) * 0.205;
                    float goldAxis = 0.29 + sin(x * 6.18 - slowTime * 0.11 + 5.64) * 0.112;

                    float blueD = y - blueAxis;
                    float cyanD = y - cyanAxis;
                    float greenD = y - greenAxis;
                    float roseD = y - roseAxis;
                    float goldD = y - goldAxis;
                    float blueAbs = abs(blueD);
                    float cyanAbs = abs(cyanD);
                    float greenAbs = abs(greenD);
                    float roseAbs = abs(roseD);
                    float goldAbs = abs(goldD);

                    float blueWidth = 0.136 + 0.026 * sin(x * 3.4 + slowTime * 0.08);
                    float cyanWidth = 0.118 + 0.018 * sin(x * 3.8 - slowTime * 0.07 + 1.2);
                    float greenWidth = 0.132 + 0.020 * sin(x * 3.1 + slowTime * 0.05 + 2.1);
                    float roseWidth = 0.113 + 0.016 * sin(x * 4.0 - slowTime * 0.06 + 3.7);
                    float goldWidth = 0.054 + 0.010 * sin(x * 4.6 + slowTime * 0.05 + 1.7);

                    float blueBody = (1.0 - smoothstep(blueWidth, blueWidth + 0.082, blueAbs)) * horizon;
                    float cyanBody = (1.0 - smoothstep(cyanWidth, cyanWidth + 0.072, cyanAbs)) * horizon;
                    float greenBody = (1.0 - smoothstep(greenWidth, greenWidth + 0.075, greenAbs)) * horizon;
                    float roseBody = (1.0 - smoothstep(roseWidth, roseWidth + 0.070, roseAbs)) * horizon;
                    float goldBody = (1.0 - smoothstep(goldWidth, goldWidth + 0.040, goldAbs)) * horizon;

                    float blueEdgeD = (blueAbs - blueWidth) * 28.0;
                    float cyanEdgeD = (cyanAbs - cyanWidth) * 31.0;
                    float greenEdgeD = (greenAbs - greenWidth) * 29.0;
                    float roseEdgeD = (roseAbs - roseWidth) * 32.0;
                    float goldEdgeD = (goldAbs - goldWidth) * 42.0;
                    float spectrumFilmEdge = clamp(
                        exp(-(blueEdgeD * blueEdgeD)) * 0.50 +
                            exp(-(cyanEdgeD * cyanEdgeD)) * 0.56 +
                            exp(-(greenEdgeD * greenEdgeD)) * 0.42 +
                            exp(-(roseEdgeD * roseEdgeD)) * 0.46 +
                            exp(-(goldEdgeD * goldEdgeD)) * 0.44,
                        0.0,
                        1.0
                    );
                    float spectrumSheetField = clamp(
                        blueBody * 0.44 + cyanBody * 0.42 + greenBody * 0.40 +
                            roseBody * 0.38 + goldBody * 0.26,
                        0.0,
                        1.0
                    );
                    float spectrumIntersectionGlow = smoothstep(
                        0.020,
                        0.46,
                        blueBody * cyanBody + cyanBody * greenBody +
                            greenBody * roseBody + roseBody * blueBody +
                            goldBody * roseBody
                    );
                    float spectrumWhiteWindow = smoothstep(
                        0.08,
                        0.66,
                        cyanBody * greenBody + blueBody * roseBody
                    ) * smoothstep(0.16, 0.86, y);
                    float upperLight = clamp(
                        smoothstep(-0.02, blueWidth, blueD) * blueBody * 0.25 +
                            smoothstep(-0.02, cyanWidth, cyanD) * cyanBody * 0.30 +
                            smoothstep(-0.02, greenWidth, greenD) * greenBody * 0.22 +
                            smoothstep(-0.02, roseWidth, roseD) * roseBody * 0.20,
                        0.0,
                        1.0
                    );

                    vec3 glassBase = mix(
                        uBaseColor * 0.22,
                        vec3(0.18, 0.18, 0.17),
                        smoothstep(0.34, 1.0, uv.y)
                    );
                    glassBase *= 0.72 + 0.20 * (1.0 - smoothstep(0.20, 1.12, length(centered)));

                    vec3 color = glassBase;
                    color += mix(uPrimaryColor, vec3(0.22, 0.50, 1.00), 0.72) * blueBody * 0.48 * uIntensity;
                    color += mix(uHighlightColor, vec3(0.42, 1.00, 0.94), 0.68) * cyanBody * 0.43 * uIntensity;
                    color += mix(uSecondaryColor, vec3(0.20, 0.93, 0.28), 0.66) * greenBody * 0.40 * uIntensity;
                    color += mix(uSecondaryColor, vec3(1.00, 0.24, 0.46), 0.62) * roseBody * 0.42 * uIntensity;
                    color += mix(uHighlightColor, vec3(1.00, 0.58, 0.20), 0.46) * goldBody * 0.28 * uIntensity;
                    color += vec3(0.88, 0.98, 1.00) * spectrumWhiteWindow * 0.50 * uGlowStrength;
                    color += uHighlightColor * spectrumFilmEdge * 0.30 * uGlowStrength;
                    color += vec3(0.96, 0.99, 1.00) * spectrumIntersectionGlow * 0.42 * uGlowStrength;
                    color += vec3(0.90, 0.96, 1.00) * upperLight * 0.18 * uGlowStrength;
                    color += uHighlightColor * touchGlow * 0.12 * uIntensity;
                    color -= uBaseColor * touchShadow * 0.032;

                    float alpha = 0.044 + spectrumSheetField * 0.31 * uIntensity +
                        spectrumFilmEdge * 0.15 * uGlowStrength +
                        spectrumIntersectionGlow * 0.15 + spectrumWhiteWindow * 0.11 +
                        touchGlow * 0.070;
                    alpha = clamp(alpha * uOpacity, 0.0, 0.64);
                    color = clamp(color, vec3(0.0), vec3(1.0));
                    fragColor = vec4(color * alpha, alpha);
                    return;
                }

                if (uWaveType == 4) {
                    vec2 vividUv = uv + (warpedUv - uv) * 0.66;
                    float x = vividUv.x;
                    float y = vividUv.y;
                    float vividTime = slowTime * 0.17;
                    float visibleSpan = smoothstep(0.03, 0.13, x) *
                        (1.0 - smoothstep(0.88, 1.04, x));

                    float cobaltAxis = 0.66 +
                        sin(x * 4.72 - vividTime * 1.08 + 0.18) * 0.244 +
                        sin(x * 9.15 + vividTime * 0.36 + 1.42) * 0.026;
                    float cyanAxis = 0.57 +
                        sin(x * 5.44 + vividTime * 0.92 + 1.02) * 0.218 +
                        sin(x * 10.20 - vividTime * 0.31 + 2.70) * 0.024;
                    float emeraldAxis = 0.43 +
                        sin(x * 4.98 - vividTime * 0.72 + 2.28) * 0.230 +
                        sin(x * 8.70 + vividTime * 0.28 + 0.64) * 0.028;
                    float limeAxis = 0.35 +
                        sin(x * 6.22 + vividTime * 0.64 + 3.35) * 0.172;
                    float coralAxis = 0.48 +
                        sin(x * 5.88 + vividTime * 1.02 + 4.18) * 0.198 +
                        sin(x * 11.35 - vividTime * 0.25 + 1.80) * 0.023;
                    float magentaAxis = 0.54 +
                        sin(x * 6.64 - vividTime * 0.86 + 5.05) * 0.177;
                    float amberAxis = 0.30 +
                        sin(x * 7.18 - vividTime * 0.54 + 5.82) * 0.118;

                    float cobaltD = y - cobaltAxis;
                    float cyanD = y - cyanAxis;
                    float emeraldD = y - emeraldAxis;
                    float limeD = y - limeAxis;
                    float coralD = y - coralAxis;
                    float magentaD = y - magentaAxis;
                    float amberD = y - amberAxis;

                    float cobaltW = 0.118 + sin(x * 3.7 + vividTime * 0.32) * 0.020;
                    float cyanW = 0.106 + sin(x * 4.0 - vividTime * 0.28 + 1.1) * 0.017;
                    float emeraldW = 0.111 + sin(x * 3.3 + vividTime * 0.24 + 2.0) * 0.018;
                    float limeW = 0.074 + sin(x * 4.9 - vividTime * 0.22 + 0.8) * 0.012;
                    float coralW = 0.102 + sin(x * 4.2 + vividTime * 0.25 + 3.3) * 0.016;
                    float magentaW = 0.082 + sin(x * 5.0 - vividTime * 0.21 + 2.4) * 0.014;
                    float amberW = 0.055 + sin(x * 5.6 + vividTime * 0.18 + 1.9) * 0.010;

                    float cobaltBody = (1.0 - smoothstep(cobaltW, cobaltW + 0.058, abs(cobaltD))) * visibleSpan;
                    float cyanBody = (1.0 - smoothstep(cyanW, cyanW + 0.052, abs(cyanD))) * visibleSpan;
                    float emeraldBody = (1.0 - smoothstep(emeraldW, emeraldW + 0.054, abs(emeraldD))) * visibleSpan;
                    float limeBody = (1.0 - smoothstep(limeW, limeW + 0.038, abs(limeD))) * visibleSpan;
                    float coralBody = (1.0 - smoothstep(coralW, coralW + 0.050, abs(coralD))) * visibleSpan;
                    float magentaBody = (1.0 - smoothstep(magentaW, magentaW + 0.042, abs(magentaD))) * visibleSpan;
                    float amberBody = (1.0 - smoothstep(amberW, amberW + 0.034, abs(amberD))) * visibleSpan;

                    float vividCobaltEdgeD = (abs(cobaltD) - cobaltW) * 34.0;
                    float vividCyanEdgeD = (abs(cyanD) - cyanW) * 36.0;
                    float vividEmeraldEdgeD = (abs(emeraldD) - emeraldW) * 35.0;
                    float vividLimeEdgeD = (abs(limeD) - limeW) * 44.0;
                    float vividCoralEdgeD = (abs(coralD) - coralW) * 37.0;
                    float vividMagentaEdgeD = (abs(magentaD) - magentaW) * 42.0;
                    float vividAmberEdgeD = (abs(amberD) - amberW) * 48.0;
                    float vividSpectrumEdge = clamp(
                        exp(-(vividCobaltEdgeD * vividCobaltEdgeD)) * 0.48 +
                            exp(-(vividCyanEdgeD * vividCyanEdgeD)) * 0.54 +
                            exp(-(vividEmeraldEdgeD * vividEmeraldEdgeD)) * 0.48 +
                            exp(-(vividLimeEdgeD * vividLimeEdgeD)) * 0.36 +
                            exp(-(vividCoralEdgeD * vividCoralEdgeD)) * 0.46 +
                            exp(-(vividMagentaEdgeD * vividMagentaEdgeD)) * 0.38 +
                            exp(-(vividAmberEdgeD * vividAmberEdgeD)) * 0.30,
                        0.0,
                        1.0
                    );
                    float vividSpectrumField = clamp(
                        cobaltBody * 0.36 + cyanBody * 0.36 + emeraldBody * 0.35 +
                            limeBody * 0.24 + coralBody * 0.35 + magentaBody * 0.27 +
                            amberBody * 0.20,
                        0.0,
                        1.0
                    );
                    float vividSpectrumCrossGlow = smoothstep(
                        0.030,
                        0.58,
                        cobaltBody * cyanBody + cyanBody * emeraldBody +
                            emeraldBody * coralBody + coralBody * magentaBody +
                            cobaltBody * coralBody + limeBody * amberBody
                    );
                    float vividSpectrumWhite = smoothstep(
                        0.06,
                        0.48,
                        cyanBody * emeraldBody + cobaltBody * coralBody + magentaBody * cyanBody
                    ) * smoothstep(0.14, 0.82, y);
                    float vividSpectrumClarity = clamp(
                        smoothstep(-0.01, cobaltW, cobaltD) * cobaltBody * 0.20 +
                            smoothstep(-0.01, cyanW, cyanD) * cyanBody * 0.26 +
                            smoothstep(-0.01, emeraldW, emeraldD) * emeraldBody * 0.22 +
                            smoothstep(-0.01, coralW, coralD) * coralBody * 0.22 +
                            vividSpectrumEdge * 0.34,
                        0.0,
                        1.0
                    );

                    vec3 base = mix(
                        uBaseColor * 0.18,
                        vec3(0.12, 0.12, 0.125),
                        smoothstep(0.24, 1.0, uv.y)
                    );
                    base *= 0.70 + 0.24 * (1.0 - smoothstep(0.18, 1.08, length(centered)));

                    vec3 color = base;
                    color += mix(uPrimaryColor, vec3(0.12, 0.38, 1.00), 0.76) * cobaltBody * 0.52 * uIntensity;
                    color += mix(uHighlightColor, vec3(0.20, 0.96, 1.00), 0.76) * cyanBody * 0.50 * uIntensity;
                    color += mix(uSecondaryColor, vec3(0.10, 0.95, 0.38), 0.72) * emeraldBody * 0.46 * uIntensity;
                    color += vec3(0.72, 1.00, 0.24) * limeBody * 0.30 * uIntensity;
                    color += vec3(1.00, 0.23, 0.24) * coralBody * 0.48 * uIntensity;
                    color += vec3(1.00, 0.16, 0.82) * magentaBody * 0.36 * uIntensity;
                    color += vec3(1.00, 0.66, 0.16) * amberBody * 0.28 * uIntensity;
                    color += vec3(0.96, 1.00, 1.00) * vividSpectrumWhite * 0.54 * uGlowStrength;
                    color += uHighlightColor * vividSpectrumEdge * 0.34 * uGlowStrength;
                    color += vec3(0.92, 0.98, 1.00) * vividSpectrumCrossGlow * 0.44 * uGlowStrength;
                    color += vec3(0.90, 0.98, 1.00) * vividSpectrumClarity * 0.14 * uGlowStrength;
                    color += uHighlightColor * touchGlow * 0.12 * uIntensity;
                    color -= uBaseColor * touchShadow * 0.030;

                    float alpha = 0.046 + vividSpectrumField * 0.34 * uIntensity +
                        vividSpectrumEdge * 0.17 * uGlowStrength +
                        vividSpectrumCrossGlow * 0.15 + vividSpectrumWhite * 0.11 +
                        touchGlow * 0.070;
                    alpha = clamp(alpha * uOpacity, 0.0, 0.68);
                    color = clamp(color, vec3(0.0), vec3(1.0));
                    fragColor = vec4(color * alpha, alpha);
                    return;
                }

                if (uWaveType == 1) {
                    vec2 ribbonUv = drift;
                    float aspect = uResolution.x / max(uResolution.y, 1.0);
                    float silkNoise = fbm(
                        ribbonUv * vec2(2.2, 3.4) + vec2(slowTime * 0.035, -slowTime * 0.021)
                    );
                    float phase = ribbonUv.x * (2.35 + aspect * 0.16) + silkNoise * 1.8;
                    float centerA = 0.56 + sin(phase * 3.15 + slowTime * 0.20) * 0.20;
                    float centerB = 0.45 + sin(phase * 2.52 - slowTime * 0.17 + 1.72) * 0.17;
                    float centerC = 0.64 + sin(phase * 3.86 + slowTime * 0.13 + 3.38) * 0.14;
                    float centerD = 0.36 + sin(phase * 2.04 + slowTime * 0.11 + 4.95) * 0.18;

                    float bandA = ribbon(uv.y, centerA, 0.115);
                    float bandB = ribbon(uv.y, centerB, 0.092);
                    float bandC = ribbon(uv.y, centerC, 0.076);
                    float bandD = ribbon(uv.y, centerD, 0.104);
                    float coreA = ribbon(uv.y, centerA, 0.030);
                    float coreB = ribbon(uv.y, centerB, 0.026);
                    float coreC = ribbon(uv.y, centerC, 0.022);

                    float crossing = smoothstep(0.05, 0.92, bandA * bandB + bandA * bandC + bandB * bandD);
                    float haze = clamp(bandA * 0.48 + bandB * 0.42 + bandC * 0.34 + bandD * 0.30, 0.0, 1.0);
                    float core = clamp(coreA * 0.70 + coreB * 0.58 + coreC * 0.46, 0.0, 1.0);
                    float depth = smoothstep(0.0, 1.0, uv.y);
                    vec3 base = mix(uBaseColor * 0.46, uBaseColor * 1.22, depth);
                    base += vec3(0.018, 0.020, 0.026) * (1.0 - smoothstep(0.08, 0.92, length(centered)));

                    vec3 tertiary = clamp(
                        vec3(
                            uSecondaryColor.b * 0.72 + uHighlightColor.r * 0.28,
                            uPrimaryColor.g * 0.58 + uSecondaryColor.g * 0.24 + 0.10,
                            uPrimaryColor.r * 0.34 + uHighlightColor.b * 0.48
                        ),
                        vec3(0.0),
                        vec3(1.0)
                    );
                    vec3 color = base;
                    color += uPrimaryColor * bandA * 0.44 * uIntensity;
                    color += uSecondaryColor * bandB * 0.38 * uIntensity;
                    color += tertiary * bandD * 0.32 * uIntensity;
                    color += mix(uPrimaryColor, uHighlightColor, 0.68) * bandC * 0.28 * uIntensity;
                    color += uHighlightColor * pow(core, 2.0) * 0.62 * uGlowStrength;
                    color += uHighlightColor * crossing * 0.20 * uGlowStrength;
                    color += uHighlightColor * touchGlow * 0.15 * uIntensity;
                    color -= uBaseColor * touchShadow * 0.035;

                    float glass = 0.075 + (1.0 - smoothstep(0.0, 0.96, length(centered))) * 0.050;
                    float alpha = glass + haze * 0.25 * uIntensity + core * 0.30 * uGlowStrength + crossing * 0.11 + touchGlow * 0.085;
                    alpha = clamp(alpha * uOpacity, 0.0, 0.64);
                    color = clamp(color, vec3(0.0), vec3(1.0));
                    fragColor = vec4(color * alpha, alpha);
                    return;
                }

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
