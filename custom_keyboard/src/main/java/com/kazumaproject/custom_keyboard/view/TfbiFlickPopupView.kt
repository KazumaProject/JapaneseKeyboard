package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.ui.font.KeyboardFontAware
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.core.domain.extensions.getThemeColor
import com.kazumaproject.core.domain.extensions.isDarkThemeOn

class TfbiFlickPopupView(context: Context) : View(context), KeyboardFontAware {

    enum class PresentationMode {
        FLICK,
        LONG_PRESS
    }

    // ===== 描画関連のプロパティ =====

    private var tapCharacter: String = ""
    private var petalCharacters = mapOf<TfbiFlickDirection, String>()
    private var highlightedDirection: TfbiFlickDirection = TfbiFlickDirection.TAP

    // 各パーツの描画設定 (初期値はデフォルトテーマから取得)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (context.isDarkThemeOn()) {
            context.getThemeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest)
        } else {
            context.getThemeColor(com.google.android.material.R.attr.colorSurface)
        }
    }
    private val highlightBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color =
            context.getThemeColor(com.google.android.material.R.attr.colorSecondaryContainer)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_icon_color)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_icon_color)
        textAlign = Paint.Align.CENTER
        textSize = spToPx(20f)
    }
    private var popupBackgroundColor: Int? = null
    private var popupTextColor: Int? = null
    private var inputTextTransform: (String) -> String = { it }
    private var presentationMode = PresentationMode.FLICK

    init {
        setKeyboardFont(KeyboardFontApplicator.processSnapshot)
    }

    override fun setKeyboardFont(snapshot: KeyboardFontSnapshot) {
        KeyboardFontApplicator.track(this)
        KeyboardFontApplicator.apply(textPaint, snapshot)
        invalidate()
    }

    private val rects = mutableMapOf<TfbiFlickDirection, RectF>()
    private var cornerRadius = 20f

    // レイアウト定義
    private val directionLayout = listOf(
        // 0行目 (上段)
        listOf(TfbiFlickDirection.UP_LEFT, TfbiFlickDirection.UP, TfbiFlickDirection.UP_RIGHT),
        // 1行目 (中段)
        listOf(TfbiFlickDirection.LEFT, TfbiFlickDirection.TAP, TfbiFlickDirection.RIGHT),
        // 2行目 (下段)
        listOf(TfbiFlickDirection.DOWN_LEFT, TfbiFlickDirection.DOWN, TfbiFlickDirection.DOWN_RIGHT)
    )

    // ===== 公開メソッド =====

    /**
     * 動的に色を設定する
     * @param backgroundColor 通常時の背景色
     * @param highlightedBackgroundColor ハイライト時の背景色
     * @param textColor テキストおよび枠線の色
     */
    fun setColors(backgroundColor: Int, highlightedBackgroundColor: Int, textColor: Int) {
        bgPaint.color = backgroundColor
        highlightBgPaint.color = highlightedBackgroundColor
        strokePaint.color = textColor
        textPaint.color = textColor
        applyPopupColorOverrides()
        invalidate()
    }

    fun setCharacters(tapChar: String, petalChars: Map<TfbiFlickDirection, String>) {
        this.tapCharacter = tapChar
        this.petalCharacters = petalChars
        invalidate()
    }

    fun setInputTextTransform(transform: (String) -> String) {
        inputTextTransform = transform
        invalidate()
    }

    fun highlightDirection(direction: TfbiFlickDirection) {
        if (this.highlightedDirection != direction) {
            this.highlightedDirection = direction
            invalidate()
        }
    }

    fun setPresentationMode(mode: PresentationMode) {
        if (presentationMode != mode) {
            presentationMode = mode
            invalidate()
        }
    }

    private var skinId = KeyboardSkinId.DEFAULT

    fun applyPopupViewStyle(style: PopupViewStyle) {
        skinId = style.skinId
        popupBackgroundColor = style.backgroundColor
        popupTextColor = style.textColor
        textPaint.textSize = spToPx(style.textSizeSp.coerceIn(8f, 48f))
        applyPopupColorOverrides()
        invalidate()
    }

    private fun applyPopupColorOverrides() {
        popupBackgroundColor?.let { bgPaint.color = it }
        popupTextColor?.let { textColor ->
            textPaint.color = textColor
            strokePaint.color = textColor
        }
    }

    // ===== Viewのライフサイクル =====

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateRects(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        KeyboardSkinRegistry.find(skinId)?.let { skin ->
            val characters = petalCharacters + (TfbiFlickDirection.TAP to tapCharacter)
            if (presentationMode == PresentationMode.LONG_PRESS) {
                drawCupertinoLongPressPanel(canvas, skin.palette, characters)
                return
            }
            characters.forEach { (direction, label) ->
                rects[direction]?.let { rect ->
                    val selected = direction == highlightedDirection
                    skin.popupDrawable(resources, com.kazumaproject.core.ui.skin.PopupDirection.CENTER, selected).apply {
                        setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
                        draw(canvas)
                    }
                    textPaint.color = if (selected) skin.palette.selectionText else skin.palette.text
                    drawTextCentered(canvas, inputTextTransform(label), rect)
                }
            }
            return
        }

        rects[TfbiFlickDirection.TAP]?.let { rect ->
            val paint =
                if (highlightedDirection == TfbiFlickDirection.TAP) highlightBgPaint else bgPaint
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
            drawTextCentered(canvas, inputTextTransform(tapCharacter), rect)
        }

        for ((direction, char) in petalCharacters) {
            rects[direction]?.let { rect ->
                val paint = if (highlightedDirection == direction) highlightBgPaint else bgPaint
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
                drawTextCentered(canvas, inputTextTransform(char), rect)
            }
        }
    }

    /**
     * Long-press choices are shown in one continuous Cupertino panel. The radial, individually
     * raised petals remain reserved for active flicking; only the currently held direction is
     * selected inside this grid.
     */
    private fun drawCupertinoLongPressPanel(
        canvas: Canvas,
        palette: com.kazumaproject.core.ui.skin.SkinPalette,
        characters: Map<TfbiFlickDirection, String>
    ) {
        val density = resources.displayMetrics.density
        val visibleRects = characters.filterValues { it.isNotEmpty() }.keys.mapNotNull { direction ->
            rects[direction]?.let { direction to it }
        }
        if (visibleRects.isEmpty()) return

        // Build a continuous silhouette from the actual choices. A full 3x3 plate leaves
        // conspicuous empty corners for the common five-way (center + four directions) map.
        val panelShape = Path()
        visibleRects.forEachIndexed { index, (_, rect) ->
            val cellPath = Path().apply {
                addRect(rect, Path.Direction.CW)
            }
            if (index == 0) {
                panelShape.set(cellPath)
            } else {
                panelShape.op(cellPath, Path.Op.UNION)
            }
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.key }
        canvas.drawPath(panelShape, fill)

        val selectedRect = rects[highlightedDirection]
            ?.takeIf { highlightedDirection in characters }
            ?.let { RectF(it.left + density, it.top + density, it.right - density, it.bottom - density) }
        if (selectedRect != null) {
            fill.color = palette.selection
            canvas.save()
            canvas.clipPath(panelShape)
            canvas.drawRoundRect(selectedRect, 4f * density, 4f * density, fill)
            canvas.restore()
        }

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(palette.text, 50)
            strokeWidth = density.coerceAtLeast(1f)
        }
        visibleRects.forEachIndexed { index, (_, first) ->
            visibleRects.drop(index + 1).forEach { (_, second) ->
                when {
                    first.top == second.top && first.right == second.left ->
                        canvas.drawLine(first.right, first.top, first.right, first.bottom, dividerPaint)
                    first.top == second.top && second.right == first.left ->
                        canvas.drawLine(first.left, first.top, first.left, first.bottom, dividerPaint)
                    first.left == second.left && first.bottom == second.top ->
                        canvas.drawLine(first.left, first.bottom, first.right, first.bottom, dividerPaint)
                    first.left == second.left && second.bottom == first.top ->
                        canvas.drawLine(first.left, first.top, first.right, first.top, dividerPaint)
                }
            }
        }

        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density.coerceAtLeast(1f)
            color = ColorUtils.setAlphaComponent(palette.text, 54)
        }
        canvas.drawPath(panelShape, outline)

        visibleRects.forEach { (direction, rect) ->
            val label = characters[direction].orEmpty()
            if (label.isNotEmpty()) {
                textPaint.color = if (direction == highlightedDirection) {
                    palette.selectionText
                } else {
                    palette.text
                }
                drawTextCentered(canvas, inputTextTransform(label), rect)
            }
        }
    }

    // ===== 内部ヘルパーメソッド =====

    private fun calculateRects(width: Int, height: Int) {
        rects.clear()
        val cellWidth = width / 3f
        val cellHeight = height / 3f

        for (row in 0..2) {
            for (col in 0..2) {
                val left = col * cellWidth
                val top = row * cellHeight
                val right = (col + 1) * cellWidth
                val bottom = (row + 1) * cellHeight
                val rect = RectF(left, top, right, bottom)

                val direction = directionLayout[row][col]
                rects[direction] = rect
            }
        }
    }

    private fun drawTextCentered(canvas: Canvas, text: String, rect: RectF) {
        if (text.isEmpty()) return
        val textY = rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(text, rect.centerX(), textY, textPaint)
    }

    private fun spToPx(sp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
    }
}
