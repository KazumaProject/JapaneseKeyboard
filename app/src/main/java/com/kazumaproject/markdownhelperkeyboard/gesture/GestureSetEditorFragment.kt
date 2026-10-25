package com.kazumaproject.markdownhelperkeyboard.gesture

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.kazumaproject.gesture_keyboard.*
import com.kazumaproject.markdownhelperkeyboard.R

class GestureSetEditorFragment : GestureScreenFragment() {
    private var testPad: GesturePadView? = null
    override fun build(state: Bundle?) { render() }
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        if (view == null && content.childCount > 0) return
        content.removeAllViews()
        val set = store.sets().firstOrNull { it.id == arguments?.getString("setId") } ?: run {
            text(t("文字セットが見つかりません", "Gesture set not found")); return
        }
        text(set.name)
        if (set.builtIn) text(t("標準セットは一覧画面から複製すると編集できます。●は始点、矢印は描画方向です。", "Duplicate the standard set to edit it. Dot: start. Arrow: direction."))
        else button(t("ジェスチャーを追加", "Add gesture")) {
            findNavController().navigate(R.id.gestureEntryEditorFragment, Bundle().apply { putString("setId", set.id) })
        }
        text(t("試し書き（文字は入力しません）", "Try recognition (no text is entered)"))
        val matcher = GestureRecognizer.Prepared(set)
        testPad = GesturePadView(requireContext()).apply {
            setBackgroundColor(0xffe8edf6.toInt())
            onStroke = { points ->
                val match = matcher.recognize(points, store.maxDistance())
                Toast.makeText(context, match?.let { GestureLabels.output(it.entry.output, ja) } ?: t("認識できません", "Not recognized"), Toast.LENGTH_SHORT).show()
            }
        }
        content.addView(testPad, LinearLayout.LayoutParams(-1, dp(180)))
        set.entries.forEach { entry ->
            text(GestureLabels.output(entry.output, ja) + " · " + entry.samples.size + t(" 見本", " samples"))
            entry.samples.forEach { sample ->
                content.addView(GesturePadView(requireContext()).apply {
                    guide = sample; captureAllowed = { false }; inkColor = android.graphics.Color.GRAY
                }, LinearLayout.LayoutParams(-1, dp(110)))
            }
            if (!set.builtIn) {
                button(t("編集・見本を追加／再録", "Edit / add or replace samples")) {
                    findNavController().navigate(R.id.gestureEntryEditorFragment, Bundle().apply { putString("setId", set.id); putString("entryId", entry.id) })
                }
                button(t("ジェスチャーを削除", "Delete gesture")) {
                    if (attempt { store.saveSet(set.copy(entries = set.entries.filterNot { it.id == entry.id })) }) render()
                }
            }
        }
    }
    override fun onDestroyView() { testPad?.cancelStroke(); testPad = null; super.onDestroyView() }
}
