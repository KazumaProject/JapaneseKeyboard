package com.kazumaproject.markdownhelperkeyboard.gesture

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import com.kazumaproject.gesture_keyboard.*
import com.kazumaproject.markdownhelperkeyboard.R

class GestureSetEditorFragment : GestureScreenFragment() {
    override fun build(state: Bundle?) { render() }
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        if (view == null && content.childCount > 0) return
        content.removeAllViews()
        val set = store.sets().firstOrNull { it.id == arguments?.getString("setId") } ?: run {
            text(t("文字セットが見つかりません", "Gesture set not found")); return
        }
        text(set.name)
        val romaji = androidx.appcompat.widget.SwitchCompat(requireContext()).apply {
            text = t("ローマ字入力", "Romaji input")
            isChecked = set.isRomaji; isEnabled = !set.builtIn
            setOnCheckedChangeListener { _, checked ->
                val current = store.sets().firstOrNull { it.id == set.id } ?: return@setOnCheckedChangeListener
                if (!attempt { store.saveSet(current.copy(isRomaji = checked)) }) isChecked = current.isRomaji
            }
        }
        content.addView(romaji)
        content.addView(androidx.appcompat.widget.SwitchCompat(requireContext()).apply {
            text = t("Palm式のタップ記号・Shift動作", "Palm tap punctuation and Shift")
            isChecked = set.palm; isEnabled = !set.builtIn
            setOnCheckedChangeListener { _, checked ->
                val current = store.sets().firstOrNull { it.id == set.id } ?: return@setOnCheckedChangeListener
                if (!attempt { store.saveSet(current.copy(palm = checked)) }) isChecked = current.palm
            }
        })
        if (set.builtIn) text(t("標準セットは一覧画面から複製すると編集できます。●は始点、矢印は描画方向です。", "Duplicate the standard set to edit it. Dot: start. Arrow: direction."))
        else button(t("ジェスチャーを追加", "Add gesture")) {
            findNavController().navigate(R.id.gestureEntryEditorFragment, Bundle().apply { putString("setId", set.id) })
        }
        content.addView(GestureGuideView(requireContext(), set, store.maxDistance(), onRecorded = record@ { entry, points ->
            val editable = if (set.builtIn) set.copy(id = java.util.UUID.randomUUID().toString(),
                name = set.name + t(" のコピー", " copy"), builtIn = false) else store.sets().first { it.id == set.id }
            val target = editable.entries.firstOrNull { it.output == entry.output }
                ?: entry.copy(id = java.util.UUID.randomUUID().toString())
            if (target.samples.size >= 16) {
                attempt { error(t("見本は16個までです。編集画面で既存の見本を削除してください。", "Up to 16 samples. Remove an existing sample in the editor first.")) }
                return@record
            }
            val next = if (target in editable.entries) editable else editable.copy(entries = editable.entries + target)
            if (attempt { store.saveSet(next) }) {
                findNavController().navigate(R.id.gestureEntryEditorFragment, Bundle().apply {
                    putString("setId", editable.id); putString("entryId", target.id)
                    putFloatArray("practiceSample", points.flatMap { listOf(it.x, it.y) }.toFloatArray())
                })
            }
        }, onEdit = if (set.builtIn) null else { entry ->
            findNavController().navigate(R.id.gestureEntryEditorFragment, Bundle().apply {
                putString("setId", set.id); putString("entryId", entry.id)
            })
        }, onDelete = if (set.builtIn) null else { entry ->
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setMessage(t("このジェスチャーを削除しますか？", "Delete this gesture?"))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    if (attempt { store.saveSet(store.sets().first { it.id == set.id }.let { current ->
                        current.copy(entries = current.entries.filterNot { it.id == entry.id })
                    }) }) render()
                }.show()
        }))
    }
}
