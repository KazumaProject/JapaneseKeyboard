package com.kazumaproject.core.ui.skin

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.view.View
import com.kazumaproject.core.domain.skin.KeyboardSkinId

/** The skin owns presentation only. Input geometry and gesture decisions stay in the keyboard. */
interface KeyboardSkin {
    val id: KeyboardSkinId
    val palette: SkinPalette
    fun keyboardDrawable(resources: Resources, floating: Boolean = false): Drawable
    fun keyDrawable(resources: Resources, qwerty: Boolean = false): Drawable
    fun popupDrawable(resources: Resources, direction: PopupDirection, selected: Boolean = false): Drawable
    fun configurePopupText(view: android.widget.TextView, flick: Boolean) {}
    fun configurePreviewText(view: android.widget.TextView) {}
    fun keyPreview(resources: Resources, keyWidth: Int, keyHeight: Int, keyLeft: Int,
                   screenWidth: Int, lowerRow: Boolean): SkinKeyPreview? = null
    fun guideDrawable(resources: Resources, direction: PopupDirection, selected: Boolean): Drawable =
        popupDrawable(resources, PopupDirection.CENTER, selected)
    fun variationDrawable(resources: Resources): Drawable = popupDrawable(resources, PopupDirection.CENTER)
    val popupReleaseDelayMillis: Long get() = 0L
    val longPressLabelColor: Int? get() = null
    val longPressLabelFadeMillis: Long get() = 0L
    val longPressLabelRestoreMillis: Long get() = 0L
    val longPressLabelInterpolator: android.animation.TimeInterpolator
        get() = android.view.animation.LinearInterpolator()
    val longPressLabelRestoreInterpolator: android.animation.TimeInterpolator
        get() = longPressLabelInterpolator
    fun showPopup(view: View)
    fun clearPopup(view: View)
}

/** Presentation bounds only; the anchor key remains the input target. */
data class SkinKeyPreview(val width: Int, val height: Int, val xOffset: Int, val yOffset: Int,
                          val background: Drawable)

data class SkinPalette(
    val background: Int,
    val key: Int,
    val text: Int,
    val pressed: Int,
    val selection: Int,
    val selectionText: Int,
)

enum class PopupDirection { CENTER, LEFT, TOP, RIGHT, BOTTOM, PREVIEW }

/** Null means delegate to the existing renderer, including custom colors and image themes. */
object KeyboardSkinRegistry {
    private val skins: Map<KeyboardSkinId, KeyboardSkin> = listOf(
        CupertinoSkin(KeyboardSkinId.CUPERTINO_LIGHT),
        CupertinoSkin(KeyboardSkinId.CUPERTINO_DARK),
    ).associateBy { it.id }

    @JvmStatic fun find(id: KeyboardSkinId): KeyboardSkin? = skins[id]
}
