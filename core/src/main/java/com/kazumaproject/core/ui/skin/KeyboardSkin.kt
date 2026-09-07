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
    fun showPopup(view: View)
    fun clearPopup(view: View)
}

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
