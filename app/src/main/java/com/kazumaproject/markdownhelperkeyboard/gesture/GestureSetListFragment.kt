package com.kazumaproject.markdownhelperkeyboard.gesture

import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.findNavController
import com.kazumaproject.gesture_keyboard.GestureSet
import com.kazumaproject.markdownhelperkeyboard.R
import java.util.UUID

class GestureSetListFragment : GestureScreenFragment() {
    override fun build(state: Bundle?) { render() }
    override fun onResume() { super.onResume(); if (view != null) render() }
    private fun render() {
        content.removeAllViews()
        if (store.invalidData()) text(getString(R.string.gesture_invalid_data))
        button(t("新しい文字セット", "New gesture set")) { nameDialog(null) }
        store.sets().forEach { set ->
            text(set.name + if (set.builtIn) t("（標準）", " (standard)") else "")
            button(t("開く · ", "Open · ") + set.entries.size) {
                findNavController().navigate(R.id.gestureSetEditorFragment, Bundle().apply { putString("setId", set.id) })
            }
            button(t("複製して編集", "Duplicate to edit")) { nameDialog(set) }
            if (!set.builtIn) {
                button(t("名前を変更", "Rename")) { nameDialog(set, rename = true) }
                button(t("削除", "Delete")) {
                    AlertDialog.Builder(requireContext()).setMessage(t("この文字セットを削除しますか？", "Delete this gesture set?"))
                        .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok) { _, _ ->
                            if (attempt { store.deleteSet(set.id) }) render()
                        }.show()
                }
            }
        }
    }
    private fun nameDialog(source: GestureSet?, rename: Boolean = false) {
        val field = EditText(requireContext()).apply {
            setSingleLine(); filters = arrayOf(android.text.InputFilter.LengthFilter(100))
            setText(if (rename) source?.name else source?.name?.plus(t(" のコピー", " copy")).orEmpty())
        }
        val dialog = AlertDialog.Builder(requireContext()).setTitle(t("文字セット名", "Set name")).setView(field)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val set = if (rename) source!!.copy(name = field.text.toString().trim())
                else GestureSet(UUID.randomUUID().toString(), field.text.toString().trim(), source?.entries.orEmpty(), isRomaji = source?.isRomaji ?: false)
            if (attempt { store.saveSet(set) }) { dialog.dismiss(); render() }
        } }
        dialog.show()
    }
}
