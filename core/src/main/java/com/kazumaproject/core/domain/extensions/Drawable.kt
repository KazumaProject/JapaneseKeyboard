package com.kazumaproject.core.domain.extensions

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ScaleDrawable
import android.graphics.drawable.VectorDrawable
import android.util.Log
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.core.graphics.drawable.DrawableCompat

/**
 * 背景がLayerDrawableの場合、指定したインデックスのShape(GradientDrawable)の色を変更する拡張関数
 * @param color 変更したい色 (Int)
 * @param index layer-list内のアイテムのインデックス (デフォルトは0)
 */
fun View.setLayerTypeSolidColor(@ColorInt color: Int, index: Int = 0) {
    // 背景がない場合は何もしない
    val background = this.background ?: return

    // mutate()をして他のViewへの影響を防ぐ
    val mutatedDrawable = background.mutate()

    if (mutatedDrawable is LayerDrawable) {
        // インデックスが範囲内かチェック
        if (index in 0 until mutatedDrawable.numberOfLayers) {
            // 指定したインデックスのDrawableを取得
            val child = mutatedDrawable.getDrawable(index)

            // それがGradientDrawable(Shape)なら色を変更
            if (child is GradientDrawable) {
                child.setColor(color)
            }
        }
    }
}

/**
 * (オプション) layer-listのitemにandroid:idが付いている場合用
 */
fun View.setLayerTypeSolidColorById(@ColorInt color: Int, @IdRes layerId: Int) {
    val background = this.background ?: return
    val mutatedDrawable = background.mutate()

    if (mutatedDrawable is LayerDrawable) {
        val child = mutatedDrawable.findDrawableByLayerId(layerId)
        if (child is GradientDrawable) {
            child.setColor(color)
        }
    }
}

fun View.debugDrawableType(index: Int = 0) {
    val bg = this.background
    if (bg == null) {
        Log.d("DEBUG_COLOR", "Background is null")
        return
    }
    Log.d("DEBUG_COLOR", "Root Drawable: ${bg::class.java.simpleName}")

    if (bg is LayerDrawable) {
        if (index < bg.numberOfLayers) {
            val child = bg.getDrawable(index)
            Log.d("DEBUG_COLOR", "Child($index) Drawable: ${child::class.java.simpleName}")
        } else {
            Log.d("DEBUG_COLOR", "Index out of bounds")
        }
    }
}

/**
 * Viewの背景Drawableに対して、再帰的に色変更（Solid Color）を適用する拡張関数
 * Shape, LayerList, StateList, Inset, Scale などに対応
 */
fun View.setDrawableSolidColor(@ColorInt color: Int) {
    val background = this.background ?: return
    // mutate() してこのView専用のインスタンスにする
    background.mutate().applyColorToDrawable(color)
}

/**
 * Drawableの種類を判別して色を適用する再帰関数（内部利用）
 */
private fun Drawable.applyColorToDrawable(@ColorInt color: Int) {
    when (this) {
        // 1. <shape> (GradientDrawable) -> setColor
        is GradientDrawable -> {
            setColor(color)
        }

        // 2. <layer-list> (LayerDrawable) -> 全レイヤーに再帰適用
        is LayerDrawable -> {
            for (i in 0 until numberOfLayers) {
                getDrawable(i).applyColorToDrawable(color)
            }
        }

        // 3. <selector> (DrawableContainer/StateListDrawable) -> 子要素に再帰適用
        is DrawableContainer -> {
            val containerState = constantState as? DrawableContainer.DrawableContainerState
            val children = containerState?.children
            children?.filterNotNull()?.forEach { child ->
                child.applyColorToDrawable(color)
            }
        }

        // 4. <inset> -> 中身に再帰適用
        is InsetDrawable -> {
            drawable?.applyColorToDrawable(color)
        }

        // 5. <scale> -> 中身に再帰適用
        is ScaleDrawable -> {
            drawable?.applyColorToDrawable(color)
        }

        // 6. 単色 (ColorDrawable) -> colorプロパティ変更
        is ColorDrawable -> {
            this.color = color
        }

        // 【追加】 7. <vector> (VectorDrawable) -> setTint
        is VectorDrawable -> {
            setTint(color)
        }

        // 【追加】 その他のDrawable (VectorDrawableCompatなど) -> DrawableCompatでTint適用を試みる
        else -> {
            DrawableCompat.setTint(this, color)
        }
    }
}

/**
 * 背景が Shape(GradientDrawable) または LayerDrawable の場合に色を変更する拡張関数
 * @param color 変更したい色 (Int)
 * @param index LayerDrawableの場合の対象インデックス (デフォルトは0)
 */
fun View.setDrawableSolidColor(@ColorInt color: Int, index: Int = 0) {
    val background = this.background ?: return

    // mutate()をして他のViewへの影響を防ぐ
    when (val mutatedDrawable = background.mutate()) {
        // ケース1: XMLが <shape> で始まっている場合
        is GradientDrawable -> {
            mutatedDrawable.setColor(color)
        }

        // ケース2: XMLが <layer-list> の場合
        is LayerDrawable -> {
            if (index in 0 until mutatedDrawable.numberOfLayers) {
                val child = mutatedDrawable.getDrawable(index)
                if (child is GradientDrawable) {
                    child.setColor(color)
                }
            }
        }

        // ケース3: 単色のColorDrawableの場合 (念のため)
        is ColorDrawable -> {
            mutatedDrawable.color = color
        }
    }
}

/**
 * Viewの背景Drawableの透明度(Alpha)を変更する拡張関数
 * すでにある色や形状（Shape, Layer, Borderなど）を維持したまま、透け感のみを変更します。
 *
 * @param alpha 透明度 (0:完全透明 〜 255:完全不透明)
 */
fun View.setDrawableAlpha(alpha: Int) {
    val background = this.background ?: return
    // mutate() してこのView専用のインスタンスにする（他のViewへの影響を防ぐ）
    background.mutate().alpha = alpha
}

/**
 * 色指定と透明度指定を同時に行う便利関数
 * 既存の setDrawableSolidColor を内部で利用します。
 *
 * @param color ベースとなる色 (Color.WHITE など)
 * @param alpha 透明度 (0:完全透明 〜 255:完全不透明)
 */
fun View.setDrawableSolidColorWithAlpha(@ColorInt color: Int, alpha: Int) {
    // ColorUtilsを使って、指定した色のAlpha値を上書きする
    val colorWithAlpha = androidx.core.graphics.ColorUtils.setAlphaComponent(color, alpha)
    // 既存の再帰的カラー適用関数を呼び出す
    this.setDrawableSolidColor(colorWithAlpha)
}
