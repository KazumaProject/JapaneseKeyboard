package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input

import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

data class KeyboardSizeValues(
    val heightDp: Int,
    val widthPercent: Int,
    val bottomMarginDp: Int,
    val marginStartDp: Int,
    val marginEndDp: Int,
)

enum class KeyboardSizeOrientation {
    Portrait,
    Landscape,
}

enum class KeyboardSizeKeyboardType {
    TenKey,
    Qwerty,
}

class KeyboardSizePreferenceAccessor(
    private val appPreference: AppPreference,
) {
    fun load(
        orientation: KeyboardSizeOrientation,
        keyboardType: KeyboardSizeKeyboardType,
    ): KeyboardSizeValues {
        return when (orientation) {
            KeyboardSizeOrientation.Portrait -> loadPortrait(keyboardType)
            KeyboardSizeOrientation.Landscape -> loadLandscape(keyboardType)
        }
    }

    fun save(
        orientation: KeyboardSizeOrientation,
        keyboardType: KeyboardSizeKeyboardType,
        values: KeyboardSizeValues,
    ) {
        val normalized = normalize(values)
        when (orientation) {
            KeyboardSizeOrientation.Portrait -> savePortrait(keyboardType, normalized)
            KeyboardSizeOrientation.Landscape -> saveLandscape(keyboardType, normalized)
        }
    }

    fun reset(
        orientation: KeyboardSizeOrientation,
        keyboardType: KeyboardSizeKeyboardType,
    ) {
        save(orientation, keyboardType, DefaultValues)
        when (orientation) {
            KeyboardSizeOrientation.Portrait -> {
                when (keyboardType) {
                    KeyboardSizeKeyboardType.TenKey -> appPreference.keyboard_position = true
                    KeyboardSizeKeyboardType.Qwerty -> appPreference.qwerty_keyboard_position = true
                }
            }

            KeyboardSizeOrientation.Landscape -> {
                when (keyboardType) {
                    KeyboardSizeKeyboardType.TenKey -> appPreference.keyboard_position_landscape = true
                    KeyboardSizeKeyboardType.Qwerty -> {
                        appPreference.qwerty_keyboard_position_landscape = true
                    }
                }
            }
        }
    }

    private fun loadPortrait(keyboardType: KeyboardSizeKeyboardType): KeyboardSizeValues {
        return when (keyboardType) {
            KeyboardSizeKeyboardType.TenKey -> KeyboardSizeValues(
                heightDp = appPreference.keyboard_height ?: DefaultHeightDp,
                widthPercent = appPreference.keyboard_width ?: DefaultWidthPercent,
                bottomMarginDp = appPreference.keyboard_vertical_margin_bottom ?: DefaultMarginDp,
                marginStartDp = appPreference.keyboard_margin_start_dp ?: DefaultMarginDp,
                marginEndDp = appPreference.keyboard_margin_end_dp ?: DefaultMarginDp,
            )

            KeyboardSizeKeyboardType.Qwerty -> KeyboardSizeValues(
                heightDp = appPreference.qwerty_keyboard_height ?: DefaultHeightDp,
                widthPercent = appPreference.qwerty_keyboard_width ?: DefaultWidthPercent,
                bottomMarginDp = appPreference.qwerty_keyboard_vertical_margin_bottom
                    ?: DefaultMarginDp,
                marginStartDp = appPreference.qwerty_keyboard_margin_start_dp ?: DefaultMarginDp,
                marginEndDp = appPreference.qwerty_keyboard_margin_end_dp ?: DefaultMarginDp,
            )
        }
    }

    private fun loadLandscape(keyboardType: KeyboardSizeKeyboardType): KeyboardSizeValues {
        return when (keyboardType) {
            KeyboardSizeKeyboardType.TenKey -> KeyboardSizeValues(
                heightDp = appPreference.keyboard_height_landscape ?: DefaultHeightDp,
                widthPercent = appPreference.keyboard_width_landscape ?: DefaultWidthPercent,
                bottomMarginDp = appPreference.keyboard_vertical_margin_bottom_landscape
                    ?: DefaultMarginDp,
                marginStartDp = appPreference.keyboard_margin_start_dp_landscape
                    ?: DefaultMarginDp,
                marginEndDp = appPreference.keyboard_margin_end_dp_landscape ?: DefaultMarginDp,
            )

            KeyboardSizeKeyboardType.Qwerty -> KeyboardSizeValues(
                heightDp = appPreference.qwerty_keyboard_height_landscape ?: DefaultHeightDp,
                widthPercent = appPreference.qwerty_keyboard_width_landscape
                    ?: DefaultWidthPercent,
                bottomMarginDp = appPreference.qwerty_keyboard_vertical_margin_bottom_landscape
                    ?: DefaultMarginDp,
                marginStartDp = appPreference.qwerty_keyboard_margin_start_dp_landscape
                    ?: DefaultMarginDp,
                marginEndDp = appPreference.qwerty_keyboard_margin_end_dp_landscape
                    ?: DefaultMarginDp,
            )
        }
    }

    private fun savePortrait(
        keyboardType: KeyboardSizeKeyboardType,
        values: KeyboardSizeValues,
    ) {
        when (keyboardType) {
            KeyboardSizeKeyboardType.TenKey -> {
                appPreference.keyboard_height = values.heightDp
                appPreference.keyboard_width = values.widthPercent
                appPreference.keyboard_vertical_margin_bottom = values.bottomMarginDp
                appPreference.keyboard_margin_start_dp = values.marginStartDp
                appPreference.keyboard_margin_end_dp = values.marginEndDp
            }

            KeyboardSizeKeyboardType.Qwerty -> {
                appPreference.qwerty_keyboard_height = values.heightDp
                appPreference.qwerty_keyboard_width = values.widthPercent
                appPreference.qwerty_keyboard_vertical_margin_bottom = values.bottomMarginDp
                appPreference.qwerty_keyboard_margin_start_dp = values.marginStartDp
                appPreference.qwerty_keyboard_margin_end_dp = values.marginEndDp
            }
        }
    }

    private fun saveLandscape(
        keyboardType: KeyboardSizeKeyboardType,
        values: KeyboardSizeValues,
    ) {
        when (keyboardType) {
            KeyboardSizeKeyboardType.TenKey -> {
                appPreference.keyboard_height_landscape = values.heightDp
                appPreference.keyboard_width_landscape = values.widthPercent
                appPreference.keyboard_vertical_margin_bottom_landscape = values.bottomMarginDp
                appPreference.keyboard_margin_start_dp_landscape = values.marginStartDp
                appPreference.keyboard_margin_end_dp_landscape = values.marginEndDp
            }

            KeyboardSizeKeyboardType.Qwerty -> {
                appPreference.qwerty_keyboard_height_landscape = values.heightDp
                appPreference.qwerty_keyboard_width_landscape = values.widthPercent
                appPreference.qwerty_keyboard_vertical_margin_bottom_landscape =
                    values.bottomMarginDp
                appPreference.qwerty_keyboard_margin_start_dp_landscape = values.marginStartDp
                appPreference.qwerty_keyboard_margin_end_dp_landscape = values.marginEndDp
            }
        }
    }

    private fun normalize(values: KeyboardSizeValues): KeyboardSizeValues {
        return values.copy(
            heightDp = values.heightDp.coerceIn(MinHeightDp, MaxHeightDp),
            widthPercent = values.widthPercent.coerceIn(MinWidthPercent, MaxWidthPercent),
            bottomMarginDp = values.bottomMarginDp.coerceAtLeast(MinMarginDp),
            marginStartDp = values.marginStartDp.coerceAtLeast(MinMarginDp),
            marginEndDp = values.marginEndDp.coerceAtLeast(MinMarginDp),
        )
    }

    companion object {
        const val MinHeightDp = 100
        const val MaxHeightDp = 420
        const val MinWidthPercent = 32
        const val MaxWidthPercent = 100
        private const val MinMarginDp = 0
        private const val DefaultHeightDp = 220
        private const val DefaultWidthPercent = 100
        private const val DefaultMarginDp = 0

        val DefaultValues = KeyboardSizeValues(
            heightDp = DefaultHeightDp,
            widthPercent = DefaultWidthPercent,
            bottomMarginDp = DefaultMarginDp,
            marginStartDp = DefaultMarginDp,
            marginEndDp = DefaultMarginDp,
        )
    }
}
