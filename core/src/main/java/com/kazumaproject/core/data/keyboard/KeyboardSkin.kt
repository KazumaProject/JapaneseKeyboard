package com.kazumaproject.core.data.keyboard

import android.content.Context
import androidx.annotation.ColorInt

/** Stable preference values for the built-in keyboard appearances. */
enum class KeyboardSkinId(val preferenceValue: String) {
    DEFAULT("default"),
    FLAT("flat"),
    GLASS("glass"),
    NEUMORPHISM("neumorphism"),
    MECHANICAL("mechanical"),
    WASHI("washi"),
    NEON("neon"),
    TERMINAL("terminal"),
    CUPERTINO("cupertino"),
    CUPERTINO_DARK("cupertino_dark");

    companion object {
        fun fromPreference(value: String?): KeyboardSkinId =
            entries.firstOrNull { it.preferenceValue == value } ?: DEFAULT
    }
}

enum class KeyboardSkinMotionMode(val preferenceValue: String) {
    FULL("full"),
    REDUCED("reduced"),
    OFF("off");

    companion object {
        fun fromPreference(value: String?): KeyboardSkinMotionMode =
            entries.firstOrNull { it.preferenceValue == value } ?: FULL
    }
}

/** Semantic roles allow every keyboard implementation to share one visual language. */
enum class KeyboardElementRole {
    CHARACTER,
    MODIFIER,
    ACTION,
    SPACE,
    CANDIDATE,
    TOOLBAR,
    POPUP,
}

enum class KeyboardSurfaceRole {
    DECK,
    CANDIDATE_STRIP,
    CANDIDATE_PANEL,
    TOOLBAR,
    POPUP,
}

enum class KeyboardSkinMaterial {
    DEFAULT,
    FLAT,
    GLASS,
    SOFT_EXTRUSION,
    MECHANICAL,
    WASHI,
    NEON,
    TERMINAL,
    CUPERTINO,
}

enum class KeyboardSkinDepthModel {
    LEGACY,
    NONE,
    REFRACTIVE,
    DUAL_SHADOW,
    KEYCAP_SIDEWALL,
    PAPER,
    EMISSIVE,
    GRID,
    SHORT_SHADOW,
}

data class KeyboardSkinPalette(
    @ColorInt val backgroundColor: Int,
    @ColorInt val normalKeyColor: Int,
    @ColorInt val specialKeyColor: Int,
    @ColorInt val actionKeyColor: Int,
    @ColorInt val normalKeyTextColor: Int,
    @ColorInt val specialKeyTextColor: Int,
    @ColorInt val actionKeyTextColor: Int,
    @ColorInt val accentColor: Int,
    @ColorInt val secondaryAccentColor: Int,
    @ColorInt val candidateSurfaceColor: Int,
    @ColorInt val candidateTextColor: Int,
) {
    @ColorInt
    fun keyColor(role: KeyboardElementRole): Int = when (role) {
        KeyboardElementRole.CHARACTER,
        KeyboardElementRole.SPACE -> normalKeyColor

        KeyboardElementRole.ACTION -> actionKeyColor
        KeyboardElementRole.CANDIDATE -> candidateSurfaceColor
        KeyboardElementRole.MODIFIER,
        KeyboardElementRole.TOOLBAR,
        KeyboardElementRole.POPUP -> specialKeyColor
    }

    @ColorInt
    fun textColor(role: KeyboardElementRole): Int = when (role) {
        KeyboardElementRole.CHARACTER,
        KeyboardElementRole.SPACE -> normalKeyTextColor

        KeyboardElementRole.ACTION -> actionKeyTextColor
        KeyboardElementRole.CANDIDATE -> candidateTextColor
        KeyboardElementRole.MODIFIER,
        KeyboardElementRole.TOOLBAR,
        KeyboardElementRole.POPUP -> specialKeyTextColor
    }
}

data class KeyboardSkinGeometry(
    val cornerRadiusDp: Float,
    val visualInsetDp: Float,
    val strokeWidthDp: Float,
    val depthDp: Float,
    val irregularityDp: Float = 0f,
)

data class KeyboardSkinTypography(
    val familyName: String,
    val bold: Boolean,
    val letterSpacing: Float = 0f,
)

data class KeyboardSkinMotionSpec(
    val pressScale: Float,
    val pressTranslationYDp: Float,
    val pressTranslationXDp: Float = 0f,
    val pressDurationMs: Long,
    val releaseDurationMs: Long,
    /** Zero means that the skin has no continuously animated backdrop. */
    val continuousPeriodMs: Long,
)

data class KeyboardSkinSpec(
    val id: KeyboardSkinId,
    val palette: KeyboardSkinPalette,
    val geometry: KeyboardSkinGeometry,
    val typography: KeyboardSkinTypography,
    val material: KeyboardSkinMaterial,
    val depthModel: KeyboardSkinDepthModel,
    val motion: KeyboardSkinMotionSpec,
)

/**
 * Authoritative built-in skin catalog. Non-default palettes are deliberately independent from
 * keyboard theme colors so skins remain recognizable on a physical device.
 */
object KeyboardSkinCatalog {
    private val specs: Map<KeyboardSkinId, KeyboardSkinSpec> = listOf(
        spec(
            KeyboardSkinId.DEFAULT,
            palette(
                0xFFF1F2F5, 0xFFFFFFFF, 0xFFD8DADE, 0xFF2864DC,
                0xFF16181C, 0xFF16181C, 0xFFFFFFFF,
                0xFF2864DC, 0xFF7A7F89, 0xFFF5F6F8, 0xFF16181C,
            ),
            KeyboardSkinGeometry(8f, 2f, 1f, 2f),
            KeyboardSkinTypography("sans-serif", false),
            KeyboardSkinMaterial.DEFAULT,
            KeyboardSkinDepthModel.LEGACY,
            KeyboardSkinMotionSpec(0.98f, 1f, pressDurationMs = 80, releaseDurationMs = 110, continuousPeriodMs = 0),
        ),
        spec(
            KeyboardSkinId.FLAT,
            palette(
                0xFF1E4ED8, 0xFFFFF4DA, 0xFFFFD166, 0xFFC9343A,
                0xFF17213B, 0xFF17213B, 0xFFFFFFFF,
                0xFFFFFFFF, 0xFFFF5A4F, 0xFF163FAF, 0xFFFFFFFF,
            ),
            KeyboardSkinGeometry(2f, 1f, 0f, 0f),
            KeyboardSkinTypography("sans-serif", true, 0.015f),
            KeyboardSkinMaterial.FLAT,
            KeyboardSkinDepthModel.NONE,
            KeyboardSkinMotionSpec(0.96f, 0f, pressDurationMs = 70, releaseDurationMs = 90, continuousPeriodMs = 0),
        ),
        spec(
            KeyboardSkinId.GLASS,
            palette(
                0xFF06152F, 0x52213F67, 0x6611325C, 0x7000D9FF,
                0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
                0xFF00D9FF, 0xFFFF3CAC, 0xB3142346, 0xFFFFFFFF,
            ),
            KeyboardSkinGeometry(12f, 2f, 1.5f, 0f),
            KeyboardSkinTypography("sans-serif-medium", false, 0.01f),
            KeyboardSkinMaterial.GLASS,
            KeyboardSkinDepthModel.REFRACTIVE,
            KeyboardSkinMotionSpec(0.97f, 1f, pressDurationMs = 120, releaseDurationMs = 150, continuousPeriodMs = 8_000),
        ),
        spec(
            KeyboardSkinId.NEUMORPHISM,
            palette(
                0xFFE9E4D9, 0xFFE9E4D9, 0xFFDAD3C5, 0xFF9E4937,
                0xFF26231F, 0xFF26231F, 0xFFFFFFFF,
                0xFFFFFFFF, 0xFF9D9689, 0xFFE4DED2, 0xFF26231F,
            ),
            KeyboardSkinGeometry(14f, 3f, 0f, 5f),
            KeyboardSkinTypography("sans-serif-medium", false),
            KeyboardSkinMaterial.SOFT_EXTRUSION,
            KeyboardSkinDepthModel.DUAL_SHADOW,
            KeyboardSkinMotionSpec(0.985f, 1.5f, pressDurationMs = 110, releaseDurationMs = 140, continuousPeriodMs = 0),
        ),
        spec(
            KeyboardSkinId.MECHANICAL,
            palette(
                0xFF16181D, 0xFF2A2D34, 0xFF3B3F48, 0xFFB72C36,
                0xFFF5E9D8, 0xFFF5E9D8, 0xFFFFFFFF,
                0xFF00E5FF, 0xFFFF3D71, 0xFF202229, 0xFFF5E9D8,
            ),
            KeyboardSkinGeometry(4f, 2f, 1f, 3f),
            KeyboardSkinTypography("sans-serif-condensed", true, 0.025f),
            KeyboardSkinMaterial.MECHANICAL,
            KeyboardSkinDepthModel.KEYCAP_SIDEWALL,
            KeyboardSkinMotionSpec(1f, 3f, pressDurationMs = 70, releaseDurationMs = 105, continuousPeriodMs = 9_000),
        ),
        spec(
            KeyboardSkinId.WASHI,
            palette(
                0xFF162A4C, 0xFFF2E3C1, 0xFFD8C69F, 0xFFA9362D,
                0xFF1D3763, 0xFF1D3763, 0xFFFFF7E7,
                0xFFB6402B, 0xFFF2E3C1, 0xFF21395D, 0xFFF2E3C1,
            ),
            KeyboardSkinGeometry(5f, 2f, 1f, 1f, irregularityDp = 1.5f),
            KeyboardSkinTypography("serif", true, 0.015f),
            KeyboardSkinMaterial.WASHI,
            KeyboardSkinDepthModel.PAPER,
            KeyboardSkinMotionSpec(0.975f, 0.5f, pressDurationMs = 160, releaseDurationMs = 180, continuousPeriodMs = 14_000),
        ),
        spec(
            KeyboardSkinId.NEON,
            palette(
                0xFF090018, 0xFF120626, 0xFF1A0A34, 0xFF32102D,
                0xFFF7FBFF, 0xFFF7FBFF, 0xFFFFFFFF,
                0xFF00EFFF, 0xFFFF2BD6, 0xFF100622, 0xFFF7FBFF,
            ),
            KeyboardSkinGeometry(4f, 2f, 1.5f, 0f),
            KeyboardSkinTypography("sans-serif-medium", false, 0.02f),
            KeyboardSkinMaterial.NEON,
            KeyboardSkinDepthModel.EMISSIVE,
            KeyboardSkinMotionSpec(0.98f, 0f, pressDurationMs = 90, releaseDurationMs = 125, continuousPeriodMs = 6_000),
        ),
        spec(
            KeyboardSkinId.TERMINAL,
            palette(
                0xFF020806, 0xFF03140C, 0xFF082A18, 0xFF39FF88,
                0xFF5CFF9C, 0xFF5CFF9C, 0xFF001A0D,
                0xFF39FF88, 0xFF0B7A3B, 0xFF04150D, 0xFF5CFF9C,
            ),
            KeyboardSkinGeometry(0f, 1f, 1f, 0f),
            KeyboardSkinTypography("monospace", true, 0.035f),
            KeyboardSkinMaterial.TERMINAL,
            KeyboardSkinDepthModel.GRID,
            KeyboardSkinMotionSpec(1f, 0f, pressTranslationXDp = 1.5f, pressDurationMs = 80, releaseDurationMs = 70, continuousPeriodMs = 4_000),
        ),
        spec(
            KeyboardSkinId.CUPERTINO,
            // Measured from the light keyboard on an iPhone 17 Pro Max,
            // iOS 26.4 Simulator. Keep this palette independent from the app theme.
            palette(
                0xFFE8E9ED, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF0091FF,
                0xFF000000, 0xFF000000, 0xFFFFFFFF,
                0xFF0091FF, 0xFF636366, 0xFFE8E9ED, 0xFF000000,
            ),
            KeyboardSkinGeometry(7f, 1.5f, 0f, 0f),
            KeyboardSkinTypography("sans-serif", false),
            KeyboardSkinMaterial.CUPERTINO,
            KeyboardSkinDepthModel.NONE,
            KeyboardSkinMotionSpec(1f, 0f, pressDurationMs = 60, releaseDurationMs = 80, continuousPeriodMs = 0),
        ),
        spec(
            KeyboardSkinId.CUPERTINO_DARK,
            // Measured from the dark keyboard on an iPhone 17 Pro Max,
            // iOS 26.4 Simulator. This is a separate skin, not a theme-dependent variant.
            palette(
                0xFF171717, 0xFF3D3D3D, 0xFF3D3D3D, 0xFF007AFF,
                0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
                0xFF007AFF, 0xFF8E8E93, 0xFF171717, 0xFFFFFFFF,
            ),
            KeyboardSkinGeometry(7f, 1.5f, 0f, 0f),
            KeyboardSkinTypography("sans-serif", false),
            KeyboardSkinMaterial.CUPERTINO,
            KeyboardSkinDepthModel.NONE,
            KeyboardSkinMotionSpec(1f, 0f, pressDurationMs = 60, releaseDurationMs = 80, continuousPeriodMs = 0),
        ),
    ).associateBy(KeyboardSkinSpec::id)

    fun specFor(id: KeyboardSkinId): KeyboardSkinSpec =
        checkNotNull(specs[id]) { "Missing keyboard skin specification for $id" }

    fun all(): List<KeyboardSkinSpec> = KeyboardSkinId.entries.map(::specFor)

    private fun palette(
        background: Long,
        normal: Long,
        special: Long,
        action: Long,
        normalText: Long,
        specialText: Long,
        actionText: Long,
        accent: Long,
        secondaryAccent: Long,
        candidateSurface: Long,
        candidateText: Long,
    ) = KeyboardSkinPalette(
        background.toInt(), normal.toInt(), special.toInt(), action.toInt(),
        normalText.toInt(), specialText.toInt(), actionText.toInt(),
        accent.toInt(), secondaryAccent.toInt(), candidateSurface.toInt(), candidateText.toInt(),
    )

    private fun spec(
        id: KeyboardSkinId,
        palette: KeyboardSkinPalette,
        geometry: KeyboardSkinGeometry,
        typography: KeyboardSkinTypography,
        material: KeyboardSkinMaterial,
        depthModel: KeyboardSkinDepthModel,
        motion: KeyboardSkinMotionSpec,
    ) = KeyboardSkinSpec(id, palette, geometry, typography, material, depthModel, motion)
}

sealed interface KeyboardAppearance {
    data class Legacy(val palette: KeyboardSkinPalette) : KeyboardAppearance

    data class BuiltIn(
        val spec: KeyboardSkinSpec,
        val motionMode: KeyboardSkinMotionMode,
    ) : KeyboardAppearance
}

object KeyboardAppearanceResolver {
    fun resolve(
        context: Context,
        skinValue: String?,
        motionValue: String?,
        themeMode: String,
        customBackgroundColor: Int,
        customKeyColor: Int,
        customSpecialKeyColor: Int,
        customKeyTextColor: Int,
        customSpecialKeyTextColor: Int,
    ): KeyboardAppearance {
        val skinId = KeyboardSkinId.fromPreference(skinValue)
        if (skinId != KeyboardSkinId.DEFAULT) {
            return KeyboardAppearance.BuiltIn(
                spec = KeyboardSkinCatalog.specFor(skinId),
                motionMode = KeyboardSkinMotionMode.fromPreference(motionValue),
            )
        }
        return KeyboardAppearance.Legacy(
            resolveKeyboardSkinPalette(
                context = context,
                themeMode = themeMode,
                customBackgroundColor = customBackgroundColor,
                customKeyColor = customKeyColor,
                customSpecialKeyColor = customSpecialKeyColor,
                customKeyTextColor = customKeyTextColor,
                customSpecialKeyTextColor = customSpecialKeyTextColor,
            )
        )
    }
}

/** Legacy/default palette resolution. Built-in non-default skins never consume these colors. */
fun resolveKeyboardSkinPalette(
    context: Context,
    themeMode: String,
    customBackgroundColor: Int,
    customKeyColor: Int,
    customSpecialKeyColor: Int,
    customKeyTextColor: Int,
    customSpecialKeyTextColor: Int,
    skinId: KeyboardSkinId = KeyboardSkinId.DEFAULT,
): KeyboardSkinPalette {
    if (skinId != KeyboardSkinId.DEFAULT) return KeyboardSkinCatalog.specFor(skinId).palette

    if (themeMode == "custom") {
        return KeyboardSkinPalette(
            backgroundColor = customBackgroundColor,
            normalKeyColor = customKeyColor,
            specialKeyColor = customSpecialKeyColor,
            actionKeyColor = customSpecialKeyColor,
            normalKeyTextColor = customKeyTextColor,
            specialKeyTextColor = customSpecialKeyTextColor,
            actionKeyTextColor = customSpecialKeyTextColor,
            accentColor = customSpecialKeyColor,
            secondaryAccentColor = customKeyColor,
            candidateSurfaceColor = customBackgroundColor,
            candidateTextColor = customKeyTextColor,
        )
    }

    val background = context.getColor(com.kazumaproject.core.R.color.qwety_bg_color)
    val normal = context.getColor(com.kazumaproject.core.R.color.qwety_key_bg_color)
    val special = context.getColor(com.kazumaproject.core.R.color.qwety_key_bg_color_2)
    val text = context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color)
    return KeyboardSkinPalette(
        backgroundColor = background,
        normalKeyColor = normal,
        specialKeyColor = special,
        actionKeyColor = special,
        normalKeyTextColor = text,
        specialKeyTextColor = text,
        actionKeyTextColor = text,
        accentColor = special,
        secondaryAccentColor = normal,
        candidateSurfaceColor = background,
        candidateTextColor = text,
    )
}
