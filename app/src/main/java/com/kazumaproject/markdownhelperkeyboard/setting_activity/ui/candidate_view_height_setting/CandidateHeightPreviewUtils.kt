package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

internal fun createCandidateHeightPreviewCandidates(): List<Candidate> {
    return listOf(
        "へんかん",
        "変換",
        "変換候補",
        "変換する",
        "日本語",
        "入力",
        "候補欄",
        "設定",
        "予測",
        "学習なし",
        "オフライン",
        "キーボード"
    ).mapIndexed { index, text ->
        Candidate(
            string = text,
            type = 1.toByte(),
            length = text.length.toUByte(),
            score = 4000 - index,
            leftId = 0.toShort(),
            rightId = 0.toShort()
        )
    }
}

internal fun clearItemDecorations(recyclerView: RecyclerView) {
    while (recyclerView.itemDecorationCount > 0) {
        recyclerView.removeItemDecorationAt(0)
    }
}

internal class CandidateHeightPreviewGridSpacingDecoration(
    private val spanCount: Int,
    private val spacing: Int,
    private val includeEdge: Boolean
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position < 0) {
            outRect.set(0, 0, 0, 0)
            return
        }

        val layoutManager = parent.layoutManager as? GridLayoutManager ?: return
        val layoutParams = view.layoutParams as GridLayoutManager.LayoutParams

        if (layoutManager.orientation == GridLayoutManager.HORIZONTAL) {
            val row = layoutParams.spanIndex
            val column = position / spanCount
            if (includeEdge) {
                outRect.top = spacing - row * spacing / spanCount
                outRect.bottom = (row + 1) * spacing / spanCount
                if (column == 0) {
                    outRect.left = spacing
                }
                outRect.right = spacing
            } else {
                outRect.top = row * spacing / spanCount
                outRect.bottom = spacing - (row + 1) * spacing / spanCount
                if (column > 0) {
                    outRect.left = spacing
                }
            }
        }
    }
}
