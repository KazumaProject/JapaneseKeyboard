package com.kazumaproject.core.data.keyboard

import androidx.annotation.ColorInt

/**
 * A persisted keyboard skin reference. Built-in preference values are intentionally unchanged;
 * imported values are namespaced so a user file can never shadow a built-in skin.
 */
sealed interface KeyboardSkinRef {
    val preferenceValue: String

    data class BuiltIn(val id: KeyboardSkinId) : KeyboardSkinRef {
        override val preferenceValue: String = id.preferenceValue
    }

    data class Imported(val id: String) : KeyboardSkinRef {
        override val preferenceValue: String = "$PREFERENCE_PREFIX$id"
    }

    companion object {
        const val PREFERENCE_PREFIX = "imported:"
        val DEFAULT: KeyboardSkinRef = BuiltIn(KeyboardSkinId.DEFAULT)

        fun fromPreference(value: String?): KeyboardSkinRef {
            val builtIn = KeyboardSkinId.entries.firstOrNull { it.preferenceValue == value }
            if (builtIn != null) return BuiltIn(builtIn)
            val importedId = value?.removePrefix(PREFERENCE_PREFIX)
            return if (value?.startsWith(PREFERENCE_PREFIX) == true &&
                importedId != null &&
                KeyboardSkinIdPattern.matches(importedId)
            ) {
                Imported(importedId)
            } else {
                DEFAULT
            }
        }
    }
}

private val KeyboardSkinIdPattern = Regex("[a-z][a-z0-9._-]{2,63}")

fun KeyboardSkinRef.isBuiltIn(id: KeyboardSkinId): Boolean =
    this is KeyboardSkinRef.BuiltIn && this.id == id

fun KeyboardSkinRef.isDefault(): Boolean = isBuiltIn(KeyboardSkinId.DEFAULT)

fun KeyboardSkinRef.importedIdOrNull(): String? =
    (this as? KeyboardSkinRef.Imported)?.id

/** Resolves a persisted reference against the in-memory store without touching disk. */
fun KeyboardSkinRef.resolvedOrDefault(): KeyboardSkinRef = when (this) {
    is KeyboardSkinRef.BuiltIn -> this
    is KeyboardSkinRef.Imported -> if (KeyboardSkinRuntime.definitionFor(id) == null) {
        KeyboardSkinRef.DEFAULT
    } else {
        this
    }
}

enum class KeyboardSkinShape {
    ROUNDED_RECT,
    CAPSULE,
    CUT_CORNER,
    HEXAGON,
    PIXEL_NOTCHED,
    ROUGH_RECT,
}

sealed interface KeyboardSkinFill {
    data class Solid(@ColorInt val color: Int) : KeyboardSkinFill

    data class LinearGradient(
        val colors: List<Int>,
        val stops: List<Float>,
        val angleDegrees: Float,
    ) : KeyboardSkinFill

    data class RadialGradient(
        val colors: List<Int>,
        val stops: List<Float>,
        val centerX: Float,
        val centerY: Float,
        val radius: Float,
    ) : KeyboardSkinFill
}

data class KeyboardSkinStroke(
    @ColorInt val color: Int,
    val widthDp: Float,
)

data class KeyboardSkinShadow(
    @ColorInt val color: Int,
    val offsetXDp: Float,
    val offsetYDp: Float,
    val blurDp: Float,
)

enum class KeyboardSkinDecorationType {
    NONE,
    DOTS,
    GRID,
    STRIPES,
    SCANLINES,
    SPECKLES,
    WEAVE,
}

enum class KeyboardSkinBackgroundAnimation {
    NONE,
    PULSE,
    SWEEP,
    SHIFT,
}

data class KeyboardSkinDecoration(
    val type: KeyboardSkinDecorationType,
    @ColorInt val color: Int,
    val opacity: Float,
    val sizeDp: Float,
    val spacingDp: Float,
    val angleDegrees: Float,
)

/** Immutable, already validated style consumed by the generic Canvas renderer. */
data class KeyboardSkinShapeStyle(
    val shape: KeyboardSkinShape = KeyboardSkinShape.ROUNDED_RECT,
    val fill: KeyboardSkinFill = KeyboardSkinFill.Solid(0x00000000),
    val cornerRadiusDp: Float = 0f,
    val insetDp: Float = 0f,
    val roughnessDp: Float = 0f,
    val cutSizeDp: Float = 0f,
    val stroke: KeyboardSkinStroke? = null,
    val shadows: List<KeyboardSkinShadow> = emptyList(),
    val decoration: KeyboardSkinDecoration? = null,
) {
    fun merge(override: KeyboardSkinShapeStyleOverride): KeyboardSkinShapeStyle = copy(
        shape = override.shape ?: shape,
        fill = override.fill ?: fill,
        cornerRadiusDp = override.cornerRadiusDp ?: cornerRadiusDp,
        insetDp = override.insetDp ?: insetDp,
        roughnessDp = override.roughnessDp ?: roughnessDp,
        cutSizeDp = override.cutSizeDp ?: cutSizeDp,
        stroke = override.stroke ?: stroke,
        shadows = override.shadows ?: shadows,
        decoration = override.decoration ?: decoration,
    )
}

data class KeyboardSkinShapeStyleOverride(
    val shape: KeyboardSkinShape? = null,
    val fill: KeyboardSkinFill? = null,
    val cornerRadiusDp: Float? = null,
    val insetDp: Float? = null,
    val roughnessDp: Float? = null,
    val cutSizeDp: Float? = null,
    val stroke: KeyboardSkinStroke? = null,
    val shadows: List<KeyboardSkinShadow>? = null,
    val decoration: KeyboardSkinDecoration? = null,
)

data class ImportedKeyboardSkinDefinition(
    val id: String,
    val name: String,
    val author: String?,
    val description: String?,
    val spec: KeyboardSkinSpec,
    val warnings: List<KeyboardSkinValidationWarning>,
    val normalizedJson: String,
) {
    val reference: KeyboardSkinRef.Imported = KeyboardSkinRef.Imported(id)
}

data class KeyboardSkinValidationWarning(
    val path: String,
    val message: String,
)

data class KeyboardSkinValidationError(
    val path: String,
    val message: String,
) {
    override fun toString(): String = "$path: $message"
}

sealed interface KeyboardSkinParseResult {
    data class Success(val definition: ImportedKeyboardSkinDefinition) : KeyboardSkinParseResult

    data class Failure(val errors: List<KeyboardSkinValidationError>) : KeyboardSkinParseResult {
        init {
            require(errors.isNotEmpty())
        }

        val summary: String get() = errors.joinToString("\n")
    }
}

data class StoredImportedKeyboardSkin(
    val definition: ImportedKeyboardSkinDefinition,
    val file: java.io.File,
)
