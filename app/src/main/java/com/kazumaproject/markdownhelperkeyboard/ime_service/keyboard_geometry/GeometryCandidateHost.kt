package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry

import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Matrix
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.constraintlayout.widget.ConstraintLayout
import com.kazumaproject.android.flexbox.AlignItems
import com.kazumaproject.android.flexbox.FlexDirection
import com.kazumaproject.android.flexbox.FlexWrap
import com.kazumaproject.android.flexbox.FlexboxLayoutManager
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import kotlin.math.roundToInt

/** Maps a keyboard-local rectangle through floating-panel scaling into its host. */
internal fun geometryBoundsInHost(body: View, host: View, bounds: RectF): Rect? {
    val bodyToScreen = Matrix()
    val hostToScreen = Matrix()
    body.transformMatrixToGlobal(bodyToScreen)
    host.transformMatrixToGlobal(hostToScreen)
    val screenToHost = Matrix()
    if (!hostToScreen.invert(screenToHost)) return null
    val mapped = RectF(bounds)
    bodyToScreen.mapRect(mapped)
    screenToHost.mapRect(mapped)
    return Rect(mapped.left.roundToInt(), mapped.top.roundToInt(), mapped.right.roundToInt(), mapped.bottom.roundToInt())
}

/** Keep the segment editor over the actual keys, leaving room for controls in the same row. */
internal fun alignSegmentBarToKeyboardBody(bar: View, body: View, overlays: List<View>) {
    val row = bar.parent as? ConstraintLayout ?: return
    if (row.width <= 0 || body.width <= 0) return
    val bodyBounds = geometryBoundsInHost(body, row, RectF(0f, 0f, body.width.toFloat(), body.height.toFloat())) ?: return
    var left = bodyBounds.left.coerceIn(0, row.width)
    var right = bodyBounds.right.coerceIn(left, row.width)
    for (overlay in overlays) {
        if (!overlay.isShown || overlay.width <= 0 || overlay.height <= 0) continue
        val bounds = geometryBoundsInHost(overlay, row, RectF(0f, 0f, overlay.width.toFloat(), overlay.height.toFloat())) ?: continue
        if (bounds.bottom <= 0 || bounds.top >= row.height || bounds.right <= left || bounds.left >= right) continue
        if (bounds.centerX() < (left + right) / 2) left = maxOf(left, bounds.right)
        else right = minOf(right, bounds.left)
    }
    right = right.coerceAtLeast(left)
    val params = bar.layoutParams as? ConstraintLayout.LayoutParams ?: return
    val rightMargin = row.width - right
    if (params.leftToLeft == ConstraintLayout.LayoutParams.PARENT_ID &&
        params.rightToRight == ConstraintLayout.LayoutParams.PARENT_ID &&
        params.startToStart == ConstraintLayout.LayoutParams.UNSET && params.endToStart == ConstraintLayout.LayoutParams.UNSET &&
        params.leftMargin == left && params.rightMargin == rightMargin) return
    params.startToStart = ConstraintLayout.LayoutParams.UNSET
    params.endToStart = ConstraintLayout.LayoutParams.UNSET
    params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
    params.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
    params.leftMargin = left
    params.rightMargin = rightMargin
    bar.layoutParams = params
}

/** Flows candidates down each column, then scrolls between columns horizontally. */
internal class SideCandidateFlowLayoutManager(context: Context, var rightToLeft: Boolean) :
    FlexboxLayoutManager(context, FlexDirection.COLUMN, FlexWrap.WRAP) {
    init { alignItems = AlignItems.FLEX_START }
    // Affect column order only; candidate text views keep their own layout direction.
    override fun getLayoutDirection(): Int = if (rightToLeft) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    override fun generateLayoutParams(params: ViewGroup.LayoutParams): RecyclerView.LayoutParams =
        FlexboxLayoutManager.LayoutParams(params)

    // The bundled Flexbox manager reports LTR scroll offsets for RTL columns. Estimate
    // a consistent range from visible items so accessibility and nested scrolling can
    // discover the correct direction as well as ordinary touch gestures.
    private fun scrollMetrics(state: RecyclerView.State): Triple<Int, Int, Int> {
        val extent = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        if (extent == 0 || childCount == 0 || state.itemCount == 0) return Triple(0, extent, extent)
        val first = findFirstVisibleItemPosition()
        val last = findLastVisibleItemPosition()
        if (first < 0 || last < first) return Triple(0, extent, extent)
        val firstView = findViewByPosition(first)
        val lastView = findViewByPosition(last)
        val earlier = first > 0 || (firstView != null && if (rightToLeft)
            getDecoratedRight(firstView) > width - paddingRight else getDecoratedLeft(firstView) < paddingLeft)
        val later = last < state.itemCount - 1 || (lastView != null && if (rightToLeft)
            getDecoratedLeft(lastView) < paddingLeft else getDecoratedRight(lastView) > width - paddingRight)
        val visibleItems = (last - first + 1).coerceAtLeast(1)
        val estimated = (extent.toLong() * state.itemCount / visibleItems).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val extra = maxOf(estimated - extent, if (earlier && later) 2 else if (earlier || later) 1 else 0)
        var progress = (extra.toLong() * first / (state.itemCount - visibleItems).coerceAtLeast(1)).toInt()
        progress = if (earlier) progress.coerceAtLeast(1) else 0
        progress = if (later) progress.coerceAtMost(extra - 1) else extra
        return Triple(if (rightToLeft) extra - progress else progress, extent, extent + extra)
    }

    override fun computeHorizontalScrollOffset(state: RecyclerView.State): Int = scrollMetrics(state).first
    override fun computeHorizontalScrollExtent(state: RecyclerView.State): Int = scrollMetrics(state).second
    override fun computeHorizontalScrollRange(state: RecyclerView.State): Int = scrollMetrics(state).third
}

/** Moves the existing candidate list into the keyboard's reserved space and restores it exactly. */
internal class GeometryCandidateHost(
    private val root: FrameLayout,
    private val recycler: RecyclerView,
    private val onRestore: () -> Unit = {},
) {
    private var parent: ViewGroup? = null
    private var index = -1
    private var originalParams: ViewGroup.LayoutParams? = null
    private var originalManager: RecyclerView.LayoutManager? = null
    private var originalClipToPadding = false
    private var originalPadding: Rect? = null
    private var panel: FrameLayout? = null
    private var verticalSpacingApplied = false
    private var verticalSpacingPx = 0
    private var verticalSpacingRightToLeft = false
    private val verticalSpacing = object : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            outRect.bottom = verticalSpacingPx
            if (verticalSpacingRightToLeft) outRect.left = verticalSpacingPx else outRect.right = verticalSpacingPx
        }
    }
    val attached: Boolean get() = panel != null

    fun show(
        bounds: Rect, verticalText: Boolean = false, rightToLeft: Boolean = false, wrapLongText: Boolean = false,
        spacingDp: Int = SideCandidateSizing.DEFAULT_SPACING_DP,
    ) {
        if (bounds.width() <= 0 || bounds.height() <= 0) { detach(); return }
        if (!attached) {
            val originalParent = recycler.parent as? ViewGroup ?: return
            parent = originalParent
            index = originalParent.indexOfChild(recycler)
            originalParams = recycler.layoutParams
            originalManager = recycler.layoutManager
            originalClipToPadding = recycler.clipToPadding
            originalPadding = Rect(recycler.paddingLeft, recycler.paddingTop, recycler.paddingRight, recycler.paddingBottom)
            originalParent.removeView(recycler)
            val container = FrameLayout(root.context)
            container.addView(recycler, FrameLayout.LayoutParams(-1, -1))
            recycler.layoutManager = LinearLayoutManager(root.context, RecyclerView.VERTICAL, false)
            recycler.clipToPadding = true
            recycler.setPadding(0, 0, 0, 0)
            root.addView(container)
            panel = container
        }
        val adapter = recycler.adapter as? SuggestionAdapter
        var columnWidth = 0
        if (verticalText) {
            val spacing = (spacingDp.coerceIn(0, SideCandidateSizing.MAX_SPACING_DP) * root.resources.displayMetrics.density).roundToInt()
            if (verticalSpacingPx != spacing || verticalSpacingRightToLeft != rightToLeft) {
                verticalSpacingPx = spacing
                verticalSpacingRightToLeft = rightToLeft
                if (verticalSpacingApplied) {
                    if (recycler.isComputingLayout) recycler.post { if (verticalSpacingApplied) recycler.invalidateItemDecorations() }
                    else recycler.invalidateItemDecorations()
                }
            }
            if (!verticalSpacingApplied) {
                recycler.addItemDecoration(verticalSpacing)
                verticalSpacingApplied = true
            }
            val metrics = root.resources.displayMetrics
            val inset = SideCandidateSizing.horizontalInset(bounds.width(), metrics.density)
            if (recycler.paddingLeft != inset || recycler.paddingRight != inset) recycler.setPadding(inset, 0, inset, 0)
            val textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, adapter?.sideCandidateTextSizeSp() ?: 14f, metrics)
            columnWidth = SideCandidateSizing.columnWidth(bounds.width(), metrics.density, textSize)
            val current = recycler.layoutManager as? SideCandidateFlowLayoutManager
            if (current == null) {
                recycler.layoutManager = SideCandidateFlowLayoutManager(root.context, rightToLeft)
            } else {
                if (current.rightToLeft != rightToLeft) {
                    current.rightToLeft = rightToLeft
                    current.scrollToPosition(0)
                    current.requestLayout()
                }
            }
        } else {
            if (recycler.paddingLeft != 0 || recycler.paddingRight != 0) recycler.setPadding(0, 0, 0, 0)
            if (verticalSpacingApplied) {
                recycler.removeItemDecoration(verticalSpacing)
                verticalSpacingApplied = false
            }
            if (recycler.layoutManager is SideCandidateFlowLayoutManager) {
                recycler.layoutManager = LinearLayoutManager(root.context, RecyclerView.VERTICAL, false)
            }
        }
        adapter?.setSideCandidateVerticalText(verticalText, columnWidth, bounds.height(), bounds.width() - 2 * recycler.paddingLeft,
            wrapLongText = wrapLongText, rightToLeft = rightToLeft, spacingDp = spacingDp)
        val container = panel ?: return
        val params = (container.layoutParams as? FrameLayout.LayoutParams) ?: FrameLayout.LayoutParams(bounds.width(), bounds.height())
        if (params.width != bounds.width() || params.height != bounds.height() || params.leftMargin != bounds.left || params.topMargin != bounds.top || params.gravity != (Gravity.TOP or Gravity.LEFT)) {
            params.width = bounds.width(); params.height = bounds.height()
            params.leftMargin = bounds.left; params.topMargin = bounds.top
            params.gravity = Gravity.TOP or Gravity.LEFT
            container.layoutParams = params
        }
        container.visibility = View.VISIBLE
    }

    fun containsScreenPoint(x: Float, y: Float): Boolean {
        val view = panel ?: return false
        if (!view.isShown) return false
        val location = IntArray(2).also(view::getLocationOnScreen)
        val localToGlobal = Matrix().also(view::transformMatrixToGlobal)
        val globalOrigin = floatArrayOf(0f, 0f).also(localToGlobal::mapPoints)
        val globalToLocal = Matrix()
        if (!localToGlobal.invert(globalToLocal)) return false
        // getLocationOnScreen includes the window offset, whereas the matrix is in
        // global view coordinates. Translate the event into that coordinate space first.
        val point = floatArrayOf(x - location[0] + globalOrigin[0], y - location[1] + globalOrigin[1])
        globalToLocal.mapPoints(point)
        return point[0] >= 0f && point[0] < view.width && point[1] >= 0f && point[1] < view.height
    }

    fun detach(): Boolean {
        val container = panel ?: return false
        if (verticalSpacingApplied) {
            recycler.removeItemDecoration(verticalSpacing)
            verticalSpacingApplied = false
        }
        container.removeView(recycler)
        root.removeView(container)
        recycler.layoutManager = originalManager
        recycler.recycledViewPool.clear()
        recycler.clipToPadding = originalClipToPadding
        originalPadding?.let { recycler.setPadding(it.left, it.top, it.right, it.bottom) }
        parent?.addView(recycler, index.coerceIn(0, parent!!.childCount), originalParams)
        panel = null; parent = null; originalParams = null; originalManager = null; originalPadding = null
        (recycler.adapter as? SuggestionAdapter)?.setSideCandidateVerticalText(false)
        onRestore()
        return true
    }
}
