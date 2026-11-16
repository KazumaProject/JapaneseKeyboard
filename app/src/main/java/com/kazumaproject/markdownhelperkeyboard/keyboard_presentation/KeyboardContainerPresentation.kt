package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import com.google.android.material.color.DynamicColors
import com.google.android.material.R as MaterialR
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.core.domain.extensions.getThemeColorOrFallback
import com.kazumaproject.core.domain.extensions.setDrawableSolidColor
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.applyClassicKeyboardCornerRounding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

internal class KeyboardContainerPresentation(private val context: Context, private val keyboardSkinId: KeyboardSkinId) {
    private val resources get() = context.resources
    private val isKeyboardRounded = AppPreference.keyboard_corner_round_preference
    private val keyboardCornerRadiusDp = AppPreference.keyboard_corner_radius_dp_preference
    private val keyboardCornerTopLeft = AppPreference.keyboard_corner_top_left_preference
    private val keyboardCornerTopRight = AppPreference.keyboard_corner_top_right_preference
    private val keyboardCornerBottomRight = AppPreference.keyboard_corner_bottom_right_preference
    private val keyboardCornerBottomLeft = AppPreference.keyboard_corner_bottom_left_preference
    private val keyboardThemeMode = AppPreference.theme_mode
    private val customThemeBgColor = AppPreference.custom_theme_bg_color
    private fun getColor(id: Int) = androidx.core.content.ContextCompat.getColor(context, id)
    fun apply(mainView: MainLayoutBinding) {
        KeyboardSkinRegistry.find(keyboardSkinId)?.let { skin ->
            val keyboardBackground = skin.keyboardDrawable(resources)
            if (keyboardSkinId == KeyboardSkinId.CUPERTINO_CLASSIC) {
                applyClassicKeyboardCornerRounding(
                    drawable = keyboardBackground,
                    rounded = isKeyboardRounded == true,
                    radiusDp = keyboardCornerRadiusDp,
                    density = resources.displayMetrics.density,
                    topLeft = keyboardCornerTopLeft,
                    topRight = keyboardCornerTopRight,
                    bottomRight = keyboardCornerBottomRight,
                    bottomLeft = keyboardCornerBottomLeft,
                )
                val chrome = com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CupertinoClassicCandidateChrome
                mainView.suggestionViewParent.background = chrome.panelBackground()
                mainView.candidateTabLayout.background = chrome.tabsBackground()
                mainView.shortcutToolbarRecyclerview.background = chrome.toolbarBackground(resources)
            } else {
                mainView.suggestionViewParent.background = skin.keyboardDrawable(resources)
                mainView.candidateTabLayout.setBackgroundColor(skin.palette.background)
                mainView.shortcutToolbarRecyclerview.background = null
            }
            mainView.root.background = keyboardBackground
            return
        }
        mainView.shortcutToolbarRecyclerview.background = null
        val isDynamic = DynamicColors.isDynamicColorAvailable()
        if (isKeyboardRounded == true) {
            val fallbackColor = getColor(com.kazumaproject.core.R.color.keyboard_bg)
            val defaultColor = if (isDynamic) {
                mainView.root.context.getThemeColorOrFallback(
                    attrRes = MaterialR.attr.colorSurfaceContainer,
                    fallbackColor = fallbackColor
                )
            } else {
                fallbackColor
            }
            val customColor = customThemeBgColor ?: Color.WHITE
            val backgroundColor = when (keyboardThemeMode) {
                "custom" -> customColor
                else -> defaultColor
            }
            mainView.root.background = createKeyboardBackgroundDrawable(
                color = backgroundColor,
                radiusDp = keyboardCornerRadiusDp,
                topLeft = keyboardCornerTopLeft,
                topRight = keyboardCornerTopRight,
                bottomRight = keyboardCornerBottomRight,
                bottomLeft = keyboardCornerBottomLeft
            )
            mainView.suggestionViewParent.background = createKeyboardBackgroundDrawable(
                color = backgroundColor,
                radiusDp = keyboardCornerRadiusDp,
                topLeft = keyboardCornerTopLeft,
                topRight = keyboardCornerTopRight,
                bottomRight = keyboardCornerBottomRight,
                bottomLeft = keyboardCornerBottomLeft
            )
            mainView.candidateTabLayout.background = createKeyboardBackgroundDrawable(
                color = backgroundColor,
                radiusDp = keyboardCornerRadiusDp,
                topLeft = keyboardCornerTopLeft,
                topRight = keyboardCornerTopRight,
                bottomRight = keyboardCornerBottomRight,
                bottomLeft = keyboardCornerBottomLeft
            )
            return
        }

        when (keyboardThemeMode) {
            "default" -> {
                if (isDynamic) {
                    mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                    mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                    mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                } else {
                    mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                    mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                    mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                }
            }

            "custom" -> {
                mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)

                mainView.root.setDrawableSolidColor(customThemeBgColor ?: Color.WHITE)
                mainView.suggestionViewParent.setDrawableSolidColor(
                    customThemeBgColor ?: Color.WHITE
                )
                mainView.candidateTabLayout.setDrawableSolidColor(
                    customThemeBgColor ?: Color.WHITE
                )
            }

            else -> {
                if (isDynamic) {
                    mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                    mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                    mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                } else {
                    mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                    mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                    mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                }
            }
        }
    }

    private fun createKeyboardBackgroundDrawable(
        @ColorInt color: Int,
        radiusDp: Int,
        topLeft: Boolean,
        topRight: Boolean,
        bottomRight: Boolean,
        bottomLeft: Boolean
    ): GradientDrawable {
        val radiusPx = radiusDp * resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadii = floatArrayOf(
                if (topLeft) radiusPx else 0f,
                if (topLeft) radiusPx else 0f,
                if (topRight) radiusPx else 0f,
                if (topRight) radiusPx else 0f,
                if (bottomRight) radiusPx else 0f,
                if (bottomRight) radiusPx else 0f,
                if (bottomLeft) radiusPx else 0f,
                if (bottomLeft) radiusPx else 0f
            )
        }
    }

}
