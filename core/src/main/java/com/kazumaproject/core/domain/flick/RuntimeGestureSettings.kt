package com.kazumaproject.core.domain.flick

/**
 * Runtime-only projection of the existing input preferences.
 *
 * This is deliberately not a persistence layer. SharedPreferences remains the single source of
 * persisted values; this snapshot gives every keyboard surface one typed, atomically replaceable
 * configuration source.
 */
data class RuntimeGestureSettings(
    val flickSensitivity: Int = DEFAULT_FLICK_SENSITIVITY,
    val longPressTimeoutMillis: Long = DEFAULT_LONG_PRESS_TIMEOUT_MILLIS,
    val revision: Long = 0L
) {
    fun normalized(): RuntimeGestureSettings = copy(
        flickSensitivity = flickSensitivity.coerceIn(
            MIN_FLICK_SENSITIVITY,
            MAX_FLICK_SENSITIVITY
        ),
        longPressTimeoutMillis = longPressTimeoutMillis.coerceIn(
            MIN_LONG_PRESS_TIMEOUT_MILLIS,
            MAX_LONG_PRESS_TIMEOUT_MILLIS
        )
    )

    companion object {
        const val MIN_FLICK_SENSITIVITY = 1
        const val MAX_FLICK_SENSITIVITY = 200
        const val DEFAULT_FLICK_SENSITIVITY = 100

        const val MIN_LONG_PRESS_TIMEOUT_MILLIS = 100L
        const val MAX_LONG_PRESS_TIMEOUT_MILLIS = 2_000L
        const val DEFAULT_LONG_PRESS_TIMEOUT_MILLIS = 300L
    }
}

fun interface RuntimeGestureSettingsSource {
    fun snapshot(): RuntimeGestureSettings
}

/**
 * Atomically publishes recognition settings to all keyboard surfaces.
 *
 * Updates are expected on the main thread, while [snapshot] is safe to read from any thread.
 */
class MutableRuntimeGestureSettingsSource(
    initial: RuntimeGestureSettings = RuntimeGestureSettings()
) : RuntimeGestureSettingsSource {

    @Volatile
    private var current = initial.normalized()

    override fun snapshot(): RuntimeGestureSettings = current

    fun update(
        flickSensitivity: Int = current.flickSensitivity,
        longPressTimeoutMillis: Long = current.longPressTimeoutMillis
    ): RuntimeGestureSettings {
        val previous = current
        val normalizedSensitivity = flickSensitivity.coerceIn(
            RuntimeGestureSettings.MIN_FLICK_SENSITIVITY,
            RuntimeGestureSettings.MAX_FLICK_SENSITIVITY
        )
        val normalizedLongPress = longPressTimeoutMillis.coerceIn(
            RuntimeGestureSettings.MIN_LONG_PRESS_TIMEOUT_MILLIS,
            RuntimeGestureSettings.MAX_LONG_PRESS_TIMEOUT_MILLIS
        )
        if (
            previous.flickSensitivity == normalizedSensitivity &&
            previous.longPressTimeoutMillis == normalizedLongPress
        ) {
            return previous
        }

        return RuntimeGestureSettings(
            flickSensitivity = normalizedSensitivity,
            longPressTimeoutMillis = normalizedLongPress,
            revision = previous.revision + 1L
        ).also { current = it }
    }
}

/**
 * Keeps controllers bound to one stable source object while allowing a View to switch from its
 * local preview settings to the IME-wide runtime source without rebuilding keys or controllers.
 */
class DelegatingRuntimeGestureSettingsSource(
    private val fallback: RuntimeGestureSettingsSource
) : RuntimeGestureSettingsSource {

    @Volatile
    private var delegate: RuntimeGestureSettingsSource = fallback

    override fun snapshot(): RuntimeGestureSettings = delegate.snapshot()

    fun bind(source: RuntimeGestureSettingsSource?) {
        delegate = source ?: fallback
    }
}

/**
 * Recognition values frozen for exactly one pointer gesture.
 *
 * Controllers must obtain this on ACTION_DOWN and keep it until ACTION_UP/ACTION_CANCEL so a
 * preference change cannot reinterpret a gesture halfway through.
 */
data class GestureSessionConfig(
    val settingsRevision: Long,
    val flickSensitivity: Int,
    val flickThresholdPx: Float,
    val longPressTimeoutMillis: Long
) {
    init {
        require(flickThresholdPx > 0f) { "flickThresholdPx must be positive" }
        require(
            longPressTimeoutMillis in
                RuntimeGestureSettings.MIN_LONG_PRESS_TIMEOUT_MILLIS..
                    RuntimeGestureSettings.MAX_LONG_PRESS_TIMEOUT_MILLIS
        ) {
            "longPressTimeoutMillis is outside the supported range"
        }
    }
}

fun interface GestureSessionConfigSource {
    fun snapshot(): GestureSessionConfig
}

class FixedGestureSessionConfigSource(
    config: GestureSessionConfig
) : GestureSessionConfigSource {
    private val fixed = config

    override fun snapshot(): GestureSessionConfig = fixed
}
