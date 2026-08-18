package com.kazumaproject.qwerty_keyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.kazumaproject.core.data.keyboard.KeyboardElementRole
import com.kazumaproject.core.data.keyboard.KeyboardSkinCatalog
import com.kazumaproject.core.data.keyboard.KeyboardSkinId
import com.kazumaproject.core.data.keyboard.KeyboardSkinPopupKind
import com.kazumaproject.core.data.keyboard.KeyboardSkinPopupRenderer
import com.kazumaproject.core.data.keyboard.KeyboardSkinRendererRegistry
import com.kazumaproject.core.data.keyboard.KeyboardSurfaceRole
import com.kazumaproject.core.data.popup.PopupViewStyle
import kotlin.math.ceil
import kotlin.math.min

class VariationsPopupView(context: Context) : View(context) {

    // ★ デザインモードの定義
    enum class PopupStyle {
        FLAT,           // 元のフラットデザイン
        NEUMORPHISM     // ニューモーフィズム
    }

    var currentStyle: PopupStyle = PopupStyle.FLAT
    var maxColumns = 3 // デフォルトは3 (元のコードに合わせる)

    // ■■■ 共通設定 ■■■
    private val cornerRadius = 30f
    private val clipPath = Path()
    private var chars: List<Char> = emptyList()
    private var selectedIndex = -1
    private var itemWidth = 0f
    private var itemHeight = 0f
    private var numColumns = 1
    private var numRows = 1
    private var popupBackgroundColor: Int? = null
    private var popupTextColor: Int? = null
    private var popupTextSizeSp: Float = 28f
    private var keyboardSkinId: KeyboardSkinId = KeyboardSkinId.DEFAULT
    private var skinSurfaceDrawable: Drawable? = null
    private var skinItemDrawable: Drawable? = null
    private var skinSelectedItemDrawable: Drawable? = null
    private val skinTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    // ■■■ FLATモード用 (元のコードの変数) ■■■
    private val flatTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_icon_color)
        textSize = 60f
        textAlign = Paint.Align.CENTER
    }
    private val flatSelectionPaint = Paint().apply {
        color = ContextCompat.getColor(context, com.kazumaproject.core.R.color.enter_key_bg)
    }
    private val flatBackgroundPaint = Paint().apply {
        color = ContextCompat.getColor(context, com.kazumaproject.core.R.color.candidate_background)
    }

    // ■■■ NEUMORPHISMモード用 ■■■
    private val neuTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 60f
        color = Color.BLACK
    }
    private val neuBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val neuSelectedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val neuPressedShadowDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val neuPressedShadowLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val itemCornerRadius = 15f

    fun applyPopupViewStyle(style: PopupViewStyle) {
        if (KeyboardSkinPopupRenderer.isFixedCupertino(keyboardSkinId)) {
            val popup = checkNotNull(KeyboardSkinPopupRenderer.specFor(keyboardSkinId))
            popupTextSizeSp = popup.variationTextSizeSp
            popupBackgroundColor = null
            popupTextColor = popup.textColor
            skinTextPaint.textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                popup.variationTextSizeSp,
                resources.displayMetrics,
            )
            invalidate()
            return
        }
        popupBackgroundColor = style.backgroundColor
        popupTextColor = style.textColor
        popupTextSizeSp = style.textSizeSp.coerceIn(8f, 48f)
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            style.textSizeSp.coerceIn(8f, 48f),
            resources.displayMetrics
        )
        flatTextPaint.textSize = textSizePx
        neuTextPaint.textSize = textSizePx
        style.backgroundColor?.let { backgroundColor ->
            flatBackgroundPaint.color = backgroundColor
            neuBackgroundPaint.color = backgroundColor
        }
        style.textColor?.let { textColor ->
            flatTextPaint.color = textColor
            neuTextPaint.color = textColor
        }
        invalidate()
    }

    fun setKeyboardSkin(skinId: KeyboardSkinId) {
        keyboardSkinId = skinId
        if (skinId == KeyboardSkinId.DEFAULT) {
            skinSurfaceDrawable = null
            skinItemDrawable = null
            skinSelectedItemDrawable = null
            invalidate()
            return
        }
        val renderer = KeyboardSkinRendererRegistry.rendererFor(skinId)
        val spec = KeyboardSkinCatalog.specFor(skinId)
        skinSurfaceDrawable = renderer.createPopupDrawable(
            context,
            KeyboardSkinPopupKind.VARIATION,
        ) ?: renderer.createSurfaceDrawable(context, KeyboardSurfaceRole.POPUP)
        skinItemDrawable = renderer.createPopupDrawable(
            context,
            KeyboardSkinPopupKind.VARIATION,
        ) ?: renderer.createKeyDrawable(context, KeyboardElementRole.CANDIDATE, 1)
        skinSelectedItemDrawable = renderer.createPopupDrawable(
            context,
            KeyboardSkinPopupKind.VARIATION,
            selected = true,
        ) ?: renderer.createKeyDrawable(context, KeyboardElementRole.CANDIDATE, 2).apply {
            state = intArrayOf(android.R.attr.state_pressed, android.R.attr.state_enabled)
        }
        val popup = KeyboardSkinPopupRenderer.specFor(skinId)
        skinTextPaint.color = popup?.textColor ?: spec.palette.candidateTextColor
        skinTextPaint.typeface = Typeface.create(
            spec.typography.familyName,
            if (spec.typography.bold) Typeface.BOLD else Typeface.NORMAL,
        )
        skinTextPaint.textSize = popup?.let {
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                it.variationTextSizeSp,
                resources.displayMetrics,
            )
        } ?: flatTextPaint.textSize
        invalidate()
    }

    // ニューモーフィズム用メソッド
    fun setNeumorphicColors(
        @ColorInt bgColor: Int,
        @ColorInt selectedColor: Int,
        @ColorInt textColor: Int
    ) {
        // モードを自動的にニューモーフィズムに切り替え
        currentStyle = PopupStyle.NEUMORPHISM

        neuBackgroundPaint.color = popupBackgroundColor ?: bgColor
        neuTextPaint.color = popupTextColor ?: textColor
        neuSelectedFillPaint.color = selectedColor

        // 影の色を計算
        val shadowLight = manipulateColor(selectedColor, 1.15f)
        val shadowDark = manipulateColor(selectedColor, 0.85f)

        neuPressedShadowLightPaint.color = shadowLight
        neuPressedShadowDarkPaint.color = shadowDark

        invalidate()
    }

    // 元のメソッド（Flatモード用）
    fun setChars(charList: List<Char>) {
        this.chars = charList
        if (chars.isNotEmpty()) {
            numColumns = if (chars.size < maxColumns) chars.size else maxColumns
            numRows = ceil(chars.size.toFloat() / maxColumns).toInt()
        }
        selectedIndex = if (charList.isNotEmpty()) 0 else -1
        requestLayout()
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (numColumns > 0 && numRows > 0) {
            itemWidth = width / numColumns.toFloat()
            itemHeight = height / numRows.toFloat()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (chars.isEmpty()) return

        if (keyboardSkinId != KeyboardSkinId.DEFAULT) {
            drawKeyboardSkinPopup(canvas)
            return
        }

        // 共通：描画領域のクリップ
        clipPath.reset()
        clipPath.addRoundRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            cornerRadius, cornerRadius, Path.Direction.CW
        )
        canvas.clipPath(clipPath)

        // 背景描画
        if (currentStyle == PopupStyle.NEUMORPHISM) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), neuBackgroundPaint)
        } else {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flatBackgroundPaint)
        }

        // 各文字の描画
        chars.forEachIndexed { index, char ->
            val col = index % maxColumns
            val row = index / maxColumns
            val left = col * itemWidth
            val top = row * itemHeight
            val right = left + itemWidth
            val bottom = top + itemHeight

            // ■ 選択時のハイライト描画
            if (index == selectedIndex) {
                if (currentStyle == PopupStyle.NEUMORPHISM) {
                    drawNeumorphicSelection(canvas, left, top, right, bottom)
                } else {
                    // 元のロジック（単純な矩形塗りつぶし）
                    canvas.drawRect(left, top, right, bottom, flatSelectionPaint)
                }
            }

            // ■ 文字描画
            val targetPaint =
                if (currentStyle == PopupStyle.NEUMORPHISM) neuTextPaint else flatTextPaint
            val cx = left + itemWidth / 2f
            val cy = top + (itemHeight / 2f) - ((targetPaint.descent() + targetPaint.ascent()) / 2f)
            canvas.drawText(char.toString(), cx, cy, targetPaint)
        }
    }

    private fun drawKeyboardSkinPopup(canvas: Canvas) {
        skinSurfaceDrawable?.apply {
            setBounds(0, 0, width, height)
            draw(canvas)
        }
        val spec = KeyboardSkinCatalog.specFor(keyboardSkinId)
        chars.forEachIndexed { index, char ->
            val col = index % maxColumns
            val row = index / maxColumns
            val left = col * itemWidth
            val top = row * itemHeight
            val right = left + itemWidth
            val bottom = top + itemHeight
            val drawable = if (index == selectedIndex) {
                skinSelectedItemDrawable
            } else {
                skinItemDrawable
            }
            val gap = KeyboardSkinPopupRenderer.specFor(keyboardSkinId)?.itemGapDp?.let {
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    it / 2f,
                    resources.displayMetrics,
                )
            } ?: 0f
            drawable?.apply {
                setBounds(
                    (left + gap).toInt(),
                    (top + gap).toInt(),
                    (right - gap).toInt(),
                    (bottom - gap).toInt(),
                )
                draw(canvas)
            }
            val popup = KeyboardSkinPopupRenderer.specFor(keyboardSkinId)
            skinTextPaint.color = when {
                popup != null && index == selectedIndex -> popup.selectedTextColor
                popup != null -> popup.textColor
                index != selectedIndex -> spec.palette.candidateTextColor
                keyboardSkinId == KeyboardSkinId.FLAT ||
                    keyboardSkinId == KeyboardSkinId.TERMINAL -> spec.palette.backgroundColor
                else -> spec.palette.candidateTextColor
            }
            skinTextPaint.textSize = popup?.let {
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    it.variationTextSizeSp,
                    resources.displayMetrics,
                )
            } ?: flatTextPaint.textSize
            val cx = left + itemWidth / 2f
            val cy = top + itemHeight / 2f -
                (skinTextPaint.descent() + skinTextPaint.ascent()) / 2f
            canvas.drawText(char.toString(), cx, cy, skinTextPaint)
        }
    }

    // ニューモーフィズムの凹み描画ロジックを分離
    private fun drawNeumorphicSelection(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        val padding = 12f
        val rect = RectF(left + padding, top + padding, right - padding, bottom - padding)

        // 1. ベース塗りつぶし
        canvas.drawRoundRect(rect, itemCornerRadius, itemCornerRadius, neuSelectedFillPaint)

        // 2. 左上の暗い影
        canvas.save()
        canvas.clipRect(rect)
        canvas.drawRoundRect(
            rect.left, rect.top, rect.right + 10f, rect.bottom + 10f,
            itemCornerRadius, itemCornerRadius, neuPressedShadowDarkPaint
        )
        canvas.restore()

        // 3. 右下の明るいハイライト
        canvas.save()
        canvas.clipRect(rect)
        canvas.drawRoundRect(
            rect.left - 10f, rect.top - 10f, rect.right, rect.bottom,
            itemCornerRadius, itemCornerRadius, neuPressedShadowLightPaint
        )
        canvas.restore()
    }

    private fun manipulateColor(@ColorInt color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = min(255, (Color.red(color) * factor).toInt())
        val g = min(255, (Color.green(color) * factor).toInt())
        val b = min(255, (Color.blue(color) * factor).toInt())
        return Color.argb(a, r, g, b)
    }

    // 元のロジックを維持
    fun updateSelection(x: Float, y: Float): Boolean {
        if (chars.isEmpty()) return false
        if (x < 0 || x > width || y < 0 || y > height) return false

        val col = (x / itemWidth).toInt()
        val row = (y / itemHeight).toInt()

        if (col in 0 until numColumns && row in 0 until numRows) {
            val newIndex = row * maxColumns + col
            if (newIndex in chars.indices && newIndex != selectedIndex) {
                selectedIndex = newIndex
                invalidate()
                return true
            }
        }
        return false
    }

    fun getSelectedChar(): Char? {
        return if (selectedIndex != -1 && selectedIndex < chars.size) chars[selectedIndex] else null
    }
}
