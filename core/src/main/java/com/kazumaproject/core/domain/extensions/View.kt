package com.kazumaproject.core.domain.extensions

import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ScaleDrawable
import android.view.View
import androidx.annotation.ColorInt

/**
 * 既存の背景Drawableの形状(Shape)を維持したまま、ボーダー(枠線)を適用する拡張関数
 * <shape> (GradientDrawable) を再帰的に探索して setStroke を適用します。
 */
fun View.setBorder(@ColorInt color: Int, widthPx: Int) {
    val background = this.background ?: return
    // mutate() してこのView専用のインスタンスにしてから変更を適用
    background.mutate().applyBorderToDrawable(color, widthPx)
}

/**
 * 再帰的に Drawable を探索し、GradientDrawable (Shape) があれば枠線を設定する
 */
private fun Drawable.applyBorderToDrawable(@ColorInt color: Int, widthPx: Int) {
    when (this) {
        // 1. <shape> (GradientDrawable) の場合 -> ここで枠線を設定
        is GradientDrawable -> {
            setStroke(widthPx, color)
        }

        // 2. <layer-list> (LayerDrawable) の場合 -> 全てのレイヤーに再帰適用
        is LayerDrawable -> {
            for (i in 0 until numberOfLayers) {
                getDrawable(i).applyBorderToDrawable(color, widthPx)
            }
        }

        // 3. <selector> (StateListDrawable) の場合 -> 全ての状態の子に再帰適用
        is DrawableContainer -> {
            val containerState = constantState as? DrawableContainer.DrawableContainerState
            val children = containerState?.children
            children?.filterNotNull()?.forEach { child ->
                child.applyBorderToDrawable(color, widthPx)
            }
        }

        // 4. <inset> の場合 -> 中身に再帰適用
        is InsetDrawable -> {
            drawable?.applyBorderToDrawable(color, widthPx)
        }

        // 5. <scale> の場合 -> 中身に再帰適用
        is ScaleDrawable -> {
            drawable?.applyBorderToDrawable(color, widthPx)
        }

        // 注意: VectorDrawable には setStroke がないため、ここでは無視されます。
        // key_preview_bubble が <shape> で作られているなら問題ありません。
    }
}

private fun RippleDrawable.safeGetDrawable(index: Int): Drawable? =
    runCatching { getDrawable(index) }.getOrNull()
