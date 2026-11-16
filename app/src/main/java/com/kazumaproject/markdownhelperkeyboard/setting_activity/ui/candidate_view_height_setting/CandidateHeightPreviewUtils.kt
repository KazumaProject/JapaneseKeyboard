package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.tenkey.TenKey
import com.kazumaproject.gojuon_keyboard.GojuonKeyboardView
import com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyRepository
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.KeyboardPreviewView
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.currentKeyboardPreviewTarget
import kotlinx.coroutines.launch

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


internal data class CandidateKeyboardPreviewViews(
    val container: FrameLayout, val tenKey: TenKey, val gojuon: GojuonKeyboardView,
    val morse: com.kazumaproject.morse_keyboard.MorseKeyboardView,
    val qwerty: QWERTYKeyboardView, val flick: FlickKeyboardView,
)

internal fun renderCandidateKeyboardPreview(
    fragment: Fragment, appPreference: AppPreference, keyboardRepository: KeyboardRepository,
    sumireSpecialKeyRepository: SumireSpecialKeyRepository, views: CandidateKeyboardPreviewViews,
    isLandscape: Boolean, onPreviewLayoutChanged: () -> Unit,
) {
    views.container.removeAllViews()
    fragment.viewLifecycleOwner.lifecycleScope.launch {
        val target = currentKeyboardPreviewTarget(fragment.requireContext())
        val preview = KeyboardPreviewView(fragment.requireContext(), target).apply {
            landscape = isLandscape
            includeCandidates = false
            onReady = {
                views.container.post {
                    val height = displayHeightForWidth(views.container.width.coerceAtLeast(1))
                    views.container.layoutParams = views.container.layoutParams.apply { this.height = height }
                    onPreviewLayoutChanged()
                }
            }
        }
        views.container.addView(preview, FrameLayout.LayoutParams(-1, -1))
    }
}

internal fun candidateKeyboardPreviewHeightPx(container: FrameLayout): Int =
    container.layoutParams.height.takeIf { it > 0 } ?: (220 * container.resources.displayMetrics.density).toInt()
