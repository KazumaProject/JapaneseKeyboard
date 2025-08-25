package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GridSpacingItemDecoration(
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
        val lp = view.layoutParams as GridLayoutManager.LayoutParams

        if (layoutManager.orientation == GridLayoutManager.HORIZONTAL) {
            // 水平方向グリッドのロジック
            val row = lp.spanIndex // 行インデックス (0 から spanCount-1)
            val column = position / spanCount // 列インデックス

            if (includeEdge) {
                // 上下のスペーシング
                outRect.top = spacing - row * spacing / spanCount
                outRect.bottom = (row + 1) * spacing / spanCount

                // 左右のスペーシング
                if (column == 0) { // 左端
                    outRect.left = spacing
                }
                outRect.right = spacing // 各アイテムの右側
            } else {
                // 上下のスペーシング
                outRect.top = row * spacing / spanCount
                outRect.bottom = spacing - (row + 1) * spacing / spanCount

                // 左右のスペーシング
                if (column > 0) {
                    outRect.left = spacing // 先頭列以外の左側
                }
            }
        } else {
            // 垂直方向グリッドのロジック（元のコード）
            val column = lp.spanIndex // 列インデックス (0 から spanCount-1)

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount
                outRect.right = (column + 1) * spacing / spanCount
                if (position < spanCount) { // 上端
                    outRect.top = spacing
                }
                outRect.bottom = spacing // 各アイテムの下側
            } else {
                outRect.left = column * spacing / spanCount
                outRect.right = spacing - (column + 1) * spacing / spanCount
                if (position >= spanCount) {
                    outRect.top = spacing // 先頭行以外の上側
                }
            }
        }
    }
}
