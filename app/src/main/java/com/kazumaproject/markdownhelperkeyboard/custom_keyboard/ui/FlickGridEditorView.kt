package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import com.google.android.material.R as MaterialR
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.DisplayActionUi
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.FlickMappingItem
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.SpecialFlickMappingItem
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepMappingItem

/**
 * セル選択状態
 */
sealed class CellMode {
    data class Petal(val direction: FlickDirection) : CellMode()
    data class SpecialFlick(val direction: FlickDirection) : CellMode()
    data class TwoStepFirst(val first: TfbiFlickDirection) : CellMode()
    data class TwoStepSecond(val first: TfbiFlickDirection, val second: TfbiFlickDirection) : CellMode()
}

/**
 * グリッドモード: どの種類のフリックを表示するか
 */
enum class GridMode {
    CROSS,          // 通常キーの1段フリック
    TWO_STEP,       // 通常キーの2段フリック
    SPECIAL_FLICK   // 特殊キーの1段フリック
}

/**
 * 3×3グリッドのフリック設定UIカスタムView
 */
@SuppressLint("ClickableViewAccessibility")
class FlickGridEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        val ALLOWED_TWO_STEP_PAIRS: Set<Pair<TfbiFlickDirection, TfbiFlickDirection>> = TwoStepMappingItem.ALLOWED_TWO_STEP_PAIRS
        val CARDINAL_DIRECTIONS = TwoStepMappingItem.CARDINAL_DIRECTIONS
    }

    // [0,0]=UP_LEFT  [0,1]=UP       [0,2]=UP_RIGHT
    // [1,0]=LEFT     [1,1]=TAP      [1,2]=RIGHT
    // [2,0]=DOWN_LEFT [2,1]=DOWN     [2,2]=DOWN_RIGHT
    private data class CellPos(val row: Int, val col: Int)

    private val tfbiDirectionGrid: Map<CellPos, TfbiFlickDirection> = mapOf(
        CellPos(0, 0) to TfbiFlickDirection.UP_LEFT,
        CellPos(0, 1) to TfbiFlickDirection.UP,
        CellPos(0, 2) to TfbiFlickDirection.UP_RIGHT,
        CellPos(1, 0) to TfbiFlickDirection.LEFT,
        CellPos(1, 1) to TfbiFlickDirection.TAP,
        CellPos(1, 2) to TfbiFlickDirection.RIGHT,
        CellPos(2, 0) to TfbiFlickDirection.DOWN_LEFT,
        CellPos(2, 1) to TfbiFlickDirection.DOWN,
        CellPos(2, 2) to TfbiFlickDirection.DOWN_RIGHT
    )

    // 通常キーの1段フリックの対応関係
    private val petalDirectionGrid: Map<FlickDirection, CellPos> = mapOf(
        FlickDirection.TAP to CellPos(1, 1),
        FlickDirection.UP_LEFT_FAR to CellPos(1, 0),
        FlickDirection.UP to CellPos(0, 1),
        FlickDirection.UP_RIGHT_FAR to CellPos(1, 2),
        FlickDirection.DOWN to CellPos(2, 1)
    )

    // 特殊キーの1段フリックの対応関係
    private val specialFlickDirectionGrid: Map<FlickDirection, CellPos> = mapOf(
        FlickDirection.TAP to CellPos(1, 1),
        FlickDirection.UP to CellPos(0, 1),
        FlickDirection.DOWN to CellPos(2, 1),
        FlickDirection.UP_LEFT to CellPos(1, 0),
        FlickDirection.UP_RIGHT to CellPos(1, 2)
    )

    private val density = context.resources.displayMetrics.density
    private val cellSizePx = (56f * density).toInt()
    private val gapPx = (18f * density).toInt()
    private val arrowSizePx = 10f * density
    private val cornerRadiusPx = 8f * density
    private val borderWidthPx = 2f * density
    private val extraPaddingPx = (4f * density).toInt()  // border見切れ防止の余白

    private val totalGridPx = cellSizePx * 3 + gapPx * 2

    private var gridMode: GridMode = GridMode.CROSS
    private var currentCellMode: CellMode? = null
    private val cellLabels: MutableMap<CellPos, String> = mutableMapOf()
    private val cellEnabled: MutableMap<CellPos, Boolean> = mutableMapOf()
    // 特殊フリック用: セルに紐付くアイコン
    private val cellIconResId: MutableMap<CellPos, Int?> = mutableMapOf()
    // 2段フリック用: アイテムを保持してセル選択時にラベルを再計算
    private var storedTwoStepItems: List<TwoStepMappingItem> = emptyList()

    var onCellSelected: ((CellMode) -> Unit)? = null

    // Paints（色はonDraw時にMaterial Themeから取得）
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidthPx
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f * density
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    init {
        isClickable = true
        isFocusable = true
    }

    private fun Context.getColorFromAttr(@AttrRes attrRes: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attrRes, tv, true)
        return ContextCompat.getColor(this, tv.resourceId)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = totalGridPx + extraPaddingPx * 2
        setMeasuredDimension(size, size)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            handleTap(event.x - extraPaddingPx, event.y - extraPaddingPx)
            performClick()
            return true
        }
        if (event.action == MotionEvent.ACTION_DOWN) return true
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Material Theme カラーを取得
        val isDark = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val colorSurface = context.getColorFromAttr(MaterialR.attr.colorSurfaceContainerHighest)
        val colorSpecialKey = context.getColorFromAttr(MaterialR.attr.colorSecondaryContainer)
        val colorSelected = context.getColorFromAttr(android.R.attr.colorPrimary)
        val colorOnSelected = context.getColorFromAttr(MaterialR.attr.colorOnPrimary)
        val colorDisabled = if (isDark) Color.parseColor("#1C1C1E") else Color.parseColor("#E5E5EA")
        val colorBorder = context.getColorFromAttr(MaterialR.attr.colorOutline)
        val colorOnSurface = context.getColorFromAttr(MaterialR.attr.colorOnSurface)
        val colorOnSurfaceVariant = context.getColorFromAttr(MaterialR.attr.colorOnSurfaceVariant)
        val colorArrow = context.getColorFromAttr(MaterialR.attr.colorOnSurfaceVariant)

        canvas.save()
        canvas.translate(extraPaddingPx.toFloat(), extraPaddingPx.toFloat())

        val activeArrows = getActiveArrows()
        val activeCellsOnPath = getActiveCellsOnPath()
        val twoStepDisabledCells = if (gridMode == GridMode.TWO_STEP) getDisabledCellsForTwoStep() else emptySet()

        for (row in 0..2) {
            for (col in 0..2) {
                val pos = CellPos(row, col)
                val enabled = cellEnabled[pos] ?: false
                val left = (col * (cellSizePx + gapPx)).toFloat()
                val top = (row * (cellSizePx + gapPx)).toFloat()
                val right = left + cellSizePx
                val bottom = top + cellSizePx

                val isSelected = isCellSelected(pos)
                val isOnPath = activeCellsOnPath.contains(pos)
                val isTwoStepDisabled = twoStepDisabledCells.contains(pos)  // フリックの2段目としての候補

                // --- 背景色 ---
                val bgColor = when {
                    !enabled -> colorDisabled
                    isTwoStepDisabled -> colorDisabled
                    gridMode == GridMode.SPECIAL_FLICK -> colorSpecialKey
                    else -> colorSurface
                }
                fillPaint.color = bgColor
                canvas.drawRoundRect(left, top, right, bottom, cornerRadiusPx, cornerRadiusPx, fillPaint)

                // --- Border: 選択セル or フリック経路上のセル ---
                if (enabled && (isSelected || isOnPath)) {
                    borderPaint.color = if (isSelected) colorSelected else colorBorder
                    borderPaint.strokeWidth = if (isSelected) borderWidthPx * 1.5f else borderWidthPx
                    canvas.drawRoundRect(left, top, right, bottom, cornerRadiusPx, cornerRadiusPx, borderPaint)
                }

                if (!enabled) continue

                // --- アイコン or テキスト ---
                val iconRes = cellIconResId[pos]
                if (iconRes != null) {
                    // アイコン描画（グレーアウトでも表示、ただし選択不可セルはアイコン色を薄く）
                    val effectiveIsSelected = isSelected && !isTwoStepDisabled
                    drawIconInCell(canvas, iconRes, left, top, right, bottom, effectiveIsSelected, colorOnSelected, colorOnSurface)
                } else {
                    val label = cellLabels[pos] ?: ""
                    if (label.isNotEmpty()) {
                        val effectiveIsSelected = isSelected && !isTwoStepDisabled
                        // グレーアウトセルはテキスト色を薄く
                        textPaint.color = when {
                            effectiveIsSelected -> colorOnSelected
                            isTwoStepDisabled -> colorOnSurfaceVariant
                            else -> colorOnSurface
                        }
                        val cx = left + cellSizePx / 2f
                        val cy = top + cellSizePx / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
                        // 選択セルはハイライト
                        if (effectiveIsSelected) {
                            fillPaint.color = colorSelected
                            canvas.drawRoundRect(left, top, right, bottom, cornerRadiusPx, cornerRadiusPx, fillPaint)
                        }
                        canvas.drawText(label, cx, cy, textPaint)
                    } else if (isSelected && !isTwoStepDisabled) {
                        // ラベルなし・選択中は青塗り
                        fillPaint.color = colorSelected
                        canvas.drawRoundRect(left, top, right, bottom, cornerRadiusPx, cornerRadiusPx, fillPaint)
                    }
                }
            }
        }

        // 矢印
        arrowPaint.color = colorArrow
        for ((from, to) in activeArrows) {
            drawArrowBetween(canvas, from, to, arrowPaint)
        }

        canvas.restore()
    }

    private fun drawIconInCell(
        canvas: Canvas,
        iconRes: Int,
        left: Float, top: Float, right: Float, bottom: Float,
        isSelected: Boolean,
        colorOnSelected: Int,
        colorOnSurface: Int
    ) {
        val fillPaintLocal = Paint(fillPaint)
        if (isSelected) {
            fillPaintLocal.color = context.getColorFromAttr(android.R.attr.colorPrimary)
            canvas.drawRoundRect(left, top, right, bottom, cornerRadiusPx, cornerRadiusPx, fillPaintLocal)
        }

        val iconColor = if (isSelected) colorOnSelected else colorOnSurface
        val drawable: Drawable = ContextCompat.getDrawable(context, iconRes) ?: return
        drawable.mutate()
        drawable.setTint(iconColor)

        val padding = (cellSizePx * 0.25f).toInt()
        val iLeft = (left + padding).toInt()
        val iTop = (top + padding).toInt()
        val iRight = (right - padding).toInt()
        val iBottom = (bottom - padding).toInt()
        drawable.setBounds(iLeft, iTop, iRight, iBottom)
        drawable.draw(canvas)
    }

    private fun isCellSelected(pos: CellPos): Boolean {
        val mode = currentCellMode ?: return false
        return when (mode) {
            is CellMode.Petal -> petalDirectionGrid[mode.direction] == pos
            is CellMode.SpecialFlick -> specialFlickDirectionGrid[mode.direction] == pos
            is CellMode.TwoStepFirst -> tfbiDirectionGrid[pos] == mode.first
            is CellMode.TwoStepSecond -> tfbiDirectionGrid[pos] == mode.second
        }
    }

    /**
     * borderを表示するフリック経路上のセル（選択されているセルを除く）
     */
    private fun getActiveCellsOnPath(): Set<CellPos> {
        val tapPos = CellPos(1, 1)
        val mode = currentCellMode ?: return emptySet()

        return when {
            gridMode == GridMode.CROSS || gridMode == GridMode.SPECIAL_FLICK -> {
                // TAPセル（常に経路の起点）はborder対象
                setOf(tapPos)
            }
            gridMode == GridMode.TWO_STEP -> {
                when (mode) {
                    is CellMode.TwoStepFirst -> {
                        // TAPとfirstの間の経路（TAP自身も含む）
                        val firstPos = tfbiDirectionGrid.entries.firstOrNull { it.value == mode.first }?.key
                            ?: return emptySet()
                        if (firstPos == tapPos) emptySet() else setOf(tapPos)
                    }
                    is CellMode.TwoStepSecond -> {
                        val firstPos = tfbiDirectionGrid.entries.firstOrNull { it.value == mode.first }?.key
                            ?: return emptySet()
                        if (firstPos == tapPos) emptySet() else setOf(tapPos, firstPos)
                    }
                    else -> emptySet()
                }
            }
            else -> emptySet()
        }
    }

    /**
     * 矢印描画対象セットを返す。
     * 矢印方向: from=起点セル, to=終点セル（中央→外側の向き = フリック方向）
     */
    private fun getActiveArrows(): Set<Pair<CellPos, CellPos>> {
        val tapPos = CellPos(1, 1)
        val mode = currentCellMode

        return when {
            gridMode == GridMode.CROSS || gridMode == GridMode.SPECIAL_FLICK -> {
                val enabledCells = if (gridMode == GridMode.CROSS) {
                    petalDirectionGrid.values.toSet()
                } else {
                    specialFlickDirectionGrid.values.toSet()
                }
                // TAP → 外側セル
                enabledCells.filter { it != tapPos }.map { tapPos to it }.toSet()
            }

            gridMode == GridMode.TWO_STEP -> {
                when {
                    mode == null || (mode is CellMode.TwoStepFirst && mode.first == TfbiFlickDirection.TAP) -> {
                        // 未選択 or TAP選択中: 「1段目を選ぶ状態」なので8方向全て表示
                        tfbiDirectionGrid.entries
                            .filter { it.key != tapPos }
                            .map { tapPos to it.key }
                            .toSet()
                    }
                    mode is CellMode.TwoStepFirst -> {
                        val firstPos = tfbiDirectionGrid.entries.firstOrNull { it.value == mode.first }?.key
                            ?: return emptySet()
                        val result = mutableSetOf<Pair<CellPos, CellPos>>()
                        // TAP → 1段目セル
                        result.add(tapPos to firstPos)
                        // 1段目セル → 許可された2段目セル
                        for ((pos, dir) in tfbiDirectionGrid) {
                            if (pos == firstPos || pos == tapPos) continue
                            if (ALLOWED_TWO_STEP_PAIRS.contains(Pair(mode.first, dir))) {
                                result.add(firstPos to pos)
                            }
                        }
                        result
                    }
                    mode is CellMode.TwoStepSecond -> {
                        val firstPos = tfbiDirectionGrid.entries.firstOrNull { it.value == mode.first }?.key
                            ?: return emptySet()
                        val result = mutableSetOf<Pair<CellPos, CellPos>>()
                        // TAP → 1段目セル
                        result.add(tapPos to firstPos)
                        // 1段目セル → 全許可2段目セル
                        for ((pos, dir) in tfbiDirectionGrid) {
                            if (pos == firstPos || pos == tapPos) continue
                            if (ALLOWED_TWO_STEP_PAIRS.contains(Pair(mode.first, dir))) {
                                result.add(firstPos to pos)
                            }
                        }
                        result
                    }
                    else -> emptySet()
                }
            }

            else -> emptySet()
        }
    }

    /**
     * 2段フリック時に無効（グレーアウト）すべきセルを返す
     */
    private fun getDisabledCellsForTwoStep(): Set<CellPos> {
        if (gridMode != GridMode.TWO_STEP) return emptySet()
        val tapPos = CellPos(1, 1)

        return when (val mode = currentCellMode) {
            is CellMode.TwoStepFirst -> {
                // TAP選択中は「1段目を選ぶ状態」なのでグレーアウトなし
                if (mode.first == TfbiFlickDirection.TAP) return emptySet()

                val firstPos = tfbiDirectionGrid.entries.firstOrNull { it.value == mode.first }?.key
                    ?: return emptySet()

                // firstPos以外で、ALLOWED_TWO_STEP_PAIRSに含まれない2段目はグレーアウト
                // tapPos（中央）はグレーアウトしない（1段目に戻れる）
                tfbiDirectionGrid.entries
                    .filter { (pos, dir) ->
                        pos != firstPos &&
                        pos != tapPos &&
                        !ALLOWED_TWO_STEP_PAIRS.contains(Pair(mode.first, dir))
                    }
                    .map { it.key }
                    .toSet()
            }
            is CellMode.TwoStepSecond -> {
                val firstPos = tfbiDirectionGrid.entries.firstOrNull { it.value == mode.first }?.key
                    ?: return emptySet()

                // タップ・1段目・2段目として有効なセル以外はグレーアウト
                tfbiDirectionGrid.entries
                    .filter { (pos, dir) ->
                        pos != firstPos &&
                        pos != tapPos &&
                        !ALLOWED_TWO_STEP_PAIRS.contains(Pair(mode.first, dir))
                    }
                    .map { it.key }
                    .toSet()
            }
            else -> emptySet()
        }
    }

    private fun drawArrowBetween(canvas: Canvas, from: CellPos, to: CellPos, paint: Paint) {
        val fromCx = from.col * (cellSizePx + gapPx).toFloat() + cellSizePx / 2f
        val fromCy = from.row * (cellSizePx + gapPx).toFloat() + cellSizePx / 2f
        val toCx = to.col * (cellSizePx + gapPx).toFloat() + cellSizePx / 2f
        val toCy = to.row * (cellSizePx + gapPx).toFloat() + cellSizePx / 2f

        val midX = (fromCx + toCx) / 2f
        val midY = (fromCy + toCy) / 2f

        val dx = toCx - fromCx
        val dy = toCy - fromCy
        val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        if (len == 0f) return
        val nx = dx / len
        val ny = dy / len

        val halfBase = arrowSizePx / 2f
        val arrowLen = arrowSizePx

        val tipX = midX + nx * arrowLen / 2f
        val tipY = midY + ny * arrowLen / 2f
        val baseX = midX - nx * arrowLen / 2f
        val baseY = midY - ny * arrowLen / 2f

        val path = Path()
        path.moveTo(tipX, tipY)
        path.lineTo(baseX - ny * halfBase, baseY + nx * halfBase)
        path.lineTo(baseX + ny * halfBase, baseY - nx * halfBase)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun handleTap(x: Float, y: Float) {
        val col = columnAtX(x) ?: return
        val row = rowAtY(y) ?: return
        val pos = CellPos(row, col)
        val enabled = cellEnabled[pos] ?: false
        if (!enabled) return

        when (gridMode) {
            GridMode.CROSS -> {
                val dir = petalDirectionGrid.entries.firstOrNull { it.value == pos }?.key ?: return
                val newMode = CellMode.Petal(dir)
                currentCellMode = newMode
                invalidate()
                onCellSelected?.invoke(newMode)
            }
            GridMode.SPECIAL_FLICK -> {
                val dir = specialFlickDirectionGrid.entries.firstOrNull { it.value == pos }?.key ?: return
                val newMode = CellMode.SpecialFlick(dir)
                currentCellMode = newMode
                invalidate()
                onCellSelected?.invoke(newMode)
            }
            GridMode.TWO_STEP -> {
                val tappedDir = tfbiDirectionGrid[pos] ?: return
                val newMode = computeNewTwoStepMode(tappedDir)
                currentCellMode = newMode
                updateTwoStepLabels(newMode)
                invalidate()
                onCellSelected?.invoke(newMode)
            }
        }
    }

    private fun computeNewTwoStepMode(tapped: TfbiFlickDirection): CellMode {
        val current = currentCellMode
        return when {
            current == null -> CellMode.TwoStepFirst(tapped)
            current is CellMode.TwoStepFirst -> {
                val first = current.first
                when {
                    tapped == first -> CellMode.TwoStepFirst(first)
                    !CARDINAL_DIRECTIONS.contains(tapped) -> {
                        if (ALLOWED_TWO_STEP_PAIRS.contains(Pair(first, tapped))) {
                            CellMode.TwoStepSecond(first, tapped)
                        } else {
                            CellMode.TwoStepFirst(tapped)
                        }
                    }
                    else -> CellMode.TwoStepFirst(tapped)
                }
            }
            current is CellMode.TwoStepSecond -> {
                val first = current.first
                when {
                    tapped == first -> CellMode.TwoStepFirst(first)
                    !CARDINAL_DIRECTIONS.contains(tapped) -> {
                        if (ALLOWED_TWO_STEP_PAIRS.contains(Pair(first, tapped))) {
                            CellMode.TwoStepSecond(first, tapped)
                        } else {
                            CellMode.TwoStepFirst(tapped)
                        }
                    }
                    else -> CellMode.TwoStepFirst(tapped)
                }
            }
            else -> CellMode.TwoStepFirst(tapped)
        }
    }

    private fun columnAtX(x: Float): Int? {
        for (col in 0..2) {
            val left = col * (cellSizePx + gapPx).toFloat()
            if (x >= left && x <= left + cellSizePx) return col
        }
        return null
    }

    private fun rowAtY(y: Float): Int? {
        for (row in 0..2) {
            val top = row * (cellSizePx + gapPx).toFloat()
            if (y >= top && y <= top + cellSizePx) return row
        }
        return null
    }

    // ---- 公開API ----

    fun setPetalContent(items: List<FlickMappingItem>, displayActions: List<DisplayActionUi>, centerLabel: String = "") {
        gridMode = GridMode.CROSS
        currentCellMode = null
        cellLabels.clear()
        cellEnabled.clear()
        cellIconResId.clear()

        for ((dir, pos) in petalDirectionGrid) {
            cellEnabled[pos] = true
            val item = items.firstOrNull { it.direction == dir }
            cellLabels[pos] = if (dir == FlickDirection.TAP) {
                centerLabel.ifEmpty { item?.output ?: "" }
            } else {
                item?.output ?: ""
            }
        }
        invalidate()
    }

    fun setTwoStepContent(items: List<TwoStepMappingItem>, displayActions: List<DisplayActionUi>) {
        gridMode = GridMode.TWO_STEP
        currentCellMode = null
        storedTwoStepItems = items
        cellLabels.clear()
        cellEnabled.clear()
        cellIconResId.clear()

        for ((pos, _) in tfbiDirectionGrid) {
            cellEnabled[pos] = true
        }
        // 初期表示は全セル1段目ラベル
        for ((pos, dir) in tfbiDirectionGrid) {
            val singleItem = items.firstOrNull { it.first == dir && it.second == dir }
            cellLabels[pos] = singleItem?.output ?: ""
        }
        invalidate()
    }

    fun setSpecialFlickContent(items: List<SpecialFlickMappingItem>, displayActions: List<DisplayActionUi>) {
        gridMode = GridMode.SPECIAL_FLICK
        currentCellMode = null
        cellLabels.clear()
        cellEnabled.clear()
        cellIconResId.clear()

        for ((dir, pos) in specialFlickDirectionGrid) {
            cellEnabled[pos] = true
            val item = items.firstOrNull { it.direction == dir }
            val action = item?.action
            val displayAction = action?.let { a -> displayActions.firstOrNull { it.action == a } }
            // アイコンがあればアイコン、なければdisplayName
            if (displayAction?.iconResId != null) {
                cellIconResId[pos] = displayAction.iconResId
                cellLabels[pos] = ""
            } else {
                cellIconResId[pos] = null
                cellLabels[pos] = displayAction?.displayName ?: ""
            }
        }
        invalidate()
    }

    /**
     * セルのラベルを更新（文字入力欄変更時）
     */
    fun updateCellLabel(mode: CellMode, label: String) {
        val pos: CellPos? = when (mode) {
            is CellMode.Petal -> petalDirectionGrid[mode.direction]
            is CellMode.SpecialFlick -> specialFlickDirectionGrid[mode.direction]
            is CellMode.TwoStepFirst -> tfbiDirectionGrid.entries.firstOrNull { it.value == mode.first }?.key
            is CellMode.TwoStepSecond -> tfbiDirectionGrid.entries.firstOrNull { it.value == mode.second }?.key
        }
        if (pos != null) {
            cellLabels[pos] = label
            invalidate()
        }
    }

    /**
     * 特殊フリックのセルをアイコンで更新
     */
    fun updateCellIcon(mode: CellMode.SpecialFlick, iconResId: Int?, displayName: String) {
        val pos = specialFlickDirectionGrid[mode.direction] ?: return
        cellIconResId[pos] = iconResId
        cellLabels[pos] = if (iconResId != null) "" else displayName
        invalidate()
    }

    fun setSelectedMode(mode: CellMode?) {
        currentCellMode = mode
        invalidate()
    }

    fun getCurrentMode(): CellMode? = currentCellMode

    /**
     * 初期セル（TAP）を選択してコールバックを呼ぶ
     */
    fun selectInitialCell() {
        val initialMode: CellMode = when (gridMode) {
            GridMode.CROSS -> CellMode.Petal(FlickDirection.TAP)
            GridMode.SPECIAL_FLICK -> CellMode.SpecialFlick(FlickDirection.TAP)
            GridMode.TWO_STEP -> CellMode.TwoStepFirst(TfbiFlickDirection.TAP)
        }
        currentCellMode = initialMode
        if (gridMode == GridMode.TWO_STEP) updateTwoStepLabels(initialMode)
        invalidate()
        onCellSelected?.invoke(initialMode)
    }

    /**
     * 2段フリックのアイテム更新後に現在のモードでラベルを再計算して再描画する。
     * KeyEditorFragment から currentTwoStepItems 更新後に呼び出すこと。
     */
    fun refreshTwoStepLabels(updatedItems: List<TwoStepMappingItem>) {
        storedTwoStepItems = updatedItems
        val mode = currentCellMode ?: return
        updateTwoStepLabels(mode)
        invalidate()
    }

    /**
     * 2段フリックのセル選択状態に応じてラベルを再計算する。
     *
     * - TAP または 斜め方向（1段目）を編集中: 全セルに1段目ラベルを表示
     * - 上下左右（1段目）または 斜め方向（2段目）を編集中:
     *     - 編集中の1段目に連結する2段目セル → その2段目ラベルを表示
     *     - それ以外 → 1段目ラベルを表示
     */
    private fun updateTwoStepLabels(mode: CellMode) {
        val items = storedTwoStepItems
        // 全セルを1段目ラベルで初期化
        for ((pos, dir) in tfbiDirectionGrid) {
            val singleItem = items.firstOrNull { it.first == dir && it.second == dir }
            cellLabels[pos] = singleItem?.output ?: ""
        }

        val firstDir: TfbiFlickDirection? = when (mode) {
            is CellMode.TwoStepFirst -> {
                // TAP または 斜め方向（1段目）: 2段目ラベルの表示は不要
                if (!CARDINAL_DIRECTIONS.contains(mode.first) || mode.first == TfbiFlickDirection.TAP) null
                else mode.first
            }
            is CellMode.TwoStepSecond -> mode.first
            else -> null
        }

        if (firstDir != null) {
            // firstDir に連結する2段目（斜め方向）のラベルを上書き
            for ((pos, dir) in tfbiDirectionGrid) {
                if (!CARDINAL_DIRECTIONS.contains(dir) && ALLOWED_TWO_STEP_PAIRS.contains(Pair(firstDir, dir))) {
                    val secondItem = items.firstOrNull { it.first == firstDir && it.second == dir }
                    cellLabels[pos] = secondItem?.output ?: ""
                }
            }
        }
    }
}
