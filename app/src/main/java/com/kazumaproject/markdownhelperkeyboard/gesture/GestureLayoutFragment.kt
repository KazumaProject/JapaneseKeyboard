package com.kazumaproject.markdownhelperkeyboard.gesture

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.snackbar.Snackbar
import com.kazumaproject.gesture_keyboard.*
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import java.util.UUID

class GestureLayoutFragment : GestureScreenFragment() {
    private lateinit var countSelector: Spinner
    private lateinit var preview: GestureKeyboardView
    private lateinit var selectedText: android.widget.TextView
    private lateinit var edit: View
    private lateinit var remove: View
    private var selected: String? = null
    private var undo: Pair<GestureControlLayout, GestureSessionState>? = null
    private var draftId: String? = null
    private var draftSide = GestureSide.LEFT
    private var draftAction: KeyAction = KeyAction.Delete
    private var draftLabel = ""
    private var dialog: AlertDialog? = null
    override fun build(state: Bundle?) {
        selected = state?.getString("selected")
        undo = state?.getString("undo")?.let { layout -> runCatching { GestureCodec.decodeLayout(layout) to GestureCodec.decodeSession(state.getString("undoSession")!!) }.getOrNull() }
        text(t("タップで選択、長押ししてドラッグで移動。線の位置へ挿入します。", "Tap to select. Hold and drag to insert a key at the highlighted position."))
        text(t("領域をドラッグして移動、右下の矢印でサイズ変更。重なる配置は保存されません。", "Drag an area to move; drag its bottom-right handle to resize. Overlapping areas cannot be saved."))
        countSelector = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, (1..4).map { t("入力領域: ", "Drawing areas: ") + it })
            setSelection(store.session().pads.size - 1)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (store.session().pads.size != position + 1) change(store.layout(), store.session().withCount(position + 1))
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        }
        content.addView(countSelector)
        preview = GestureKeyboardView(requireContext()).apply {
            inputAllowed = { false }
            onStateChanged = { next -> change(store.layout(), next) }
            onPadBoundsChanged = { id, bounds -> change(store.layout(), store.session().update(id) { it.copy(bounds = bounds) }) }
            onControlSelected = { selected = it; updateSelection() }
            onControlMove = { id, side, index ->
                val before = store.layout()
                val next = before.move(id, side, index)
                if (next != before) change(next)
                selected = id; render()
            }
            onControlAdd = { side -> showEditor(null, side) }
        }
        content.addView(preview, LinearLayout.LayoutParams(-1, dp(320)))
        selectedText = text("")
        edit = button(t("編集", "Edit")) { selectedKey()?.let { showEditor(it, selectedSide()) } }
        remove = button(t("削除", "Delete")) {
            val id = selected ?: return@button
            val before = store.layout()
            if (change(before.copy(leftKeys = before.leftKeys.filterNot { it.id == id }, rightKeys = before.rightKeys.filterNot { it.id == id }))) {
                selected = null; render()
            }
        }
        button(t("元に戻す", "Undo")) { undo?.let { previous ->
            if (attempt { store.saveConfiguration(previous.first, previous.second) }) { undo = null; render() }
        } }
        button(t("配置を初期状態に戻す", "Reset layout")) { change(GestureControlLayout(), GestureSessionState()) }
        render()
        if (state?.containsKey("draftAction") == true) {
            draftId = state.getString("draftId")
            draftSide = GestureSide.valueOf(state.getString("draftSide")!!)
            draftAction = requireNotNull(KeyActionMapper.toKeyAction(state.getString("draftAction")))
            draftLabel = state.getString("draftLabel").orEmpty()
            content.post { if (view != null) showEditor(null, draftSide, restore = true) }
        }
    }
    private fun selectedKey() = (store.layout().leftKeys + store.layout().rightKeys).firstOrNull { it.id == selected }
    private fun selectedSide() = if (store.layout().leftKeys.any { it.id == selected }) GestureSide.LEFT else GestureSide.RIGHT
    private fun updateSelection() {
        preview.selectedControlId = selected
        val key = selectedKey()
        selectedText.text = key?.let { t("選択: ", "Selected: ") + (it.labelOverride ?: GestureLabels.command(it.action, requireContext())) } ?: t("キーを選択してください", "Select a key")
        edit.isEnabled = key != null; remove.isEnabled = key != null
    }
    private fun render() { countSelector.setSelection(store.session().pads.size - 1); preview.configure(store.sets(), store.session(), store.layout()); updateSelection() }
    private fun change(next: GestureControlLayout, session: GestureSessionState = store.session()): Boolean {
        val before = store.layout() to store.session()
        if (!attempt { store.saveConfiguration(next, session) }) return false
        undo = before; render()
        if (view != null) Snackbar.make(requireView(), t("配置を保存しました", "Layout saved"), Snackbar.LENGTH_LONG)
            .setAction(t("元に戻す", "Undo")) { if (attempt { store.saveConfiguration(before.first, before.second) }) { undo = null; render() } }.show()
        return true
    }
    private fun showEditor(key: GestureControlKey?, side: GestureSide, restore: Boolean = false) {
        if (!restore) { draftId = key?.id; draftSide = side; draftAction = key?.action ?: KeyAction.Delete; draftLabel = key?.labelOverride.orEmpty() }
        val fields = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        val actionButton = android.widget.Button(requireContext()).apply {
            isAllCaps = false
            text = GestureLabels.command(draftAction, requireContext())
            setOnClickListener { showGestureActionPicker { action ->
                draftAction = action; text = GestureLabels.command(action, requireContext())
            } }
        }
        val label = EditText(requireContext()).apply {
            hint = t("表示名（空欄で既定のアイコン）", "Label (blank = default icon)")
            filters = arrayOf(android.text.InputFilter.LengthFilter(40)); setText(draftLabel)
            doAfterTextChanged { draftLabel = it.toString() }
        }
        fields.addView(actionButton); fields.addView(label)
        dialog = AlertDialog.Builder(requireContext()).setTitle(t("特殊キー", "Special key")).setView(fields)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create()
        dialog!!.setOnShowListener {
            dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val id = draftId ?: UUID.randomUUID().toString()
                val replacement = GestureControlKey(id, draftAction, draftLabel.trim().ifEmpty { null })
                val before = store.layout()
                val next = if (draftId == null) before.withKeys(draftSide, before.keys(draftSide) + replacement)
                    else before.copy(leftKeys = before.leftKeys.map { if (it.id == id) replacement else it },
                        rightKeys = before.rightKeys.map { if (it.id == id) replacement else it })
                if (change(next)) { selected = id; updateSelection(); dialog?.dismiss() }
            }
        }
        dialog!!.show()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); outState.putString("selected", selected)
        undo?.let { outState.putString("undo", GestureCodec.encodeLayout(it.first)); outState.putString("undoSession", GestureCodec.encodeSession(it.second)) }
        if (dialog?.isShowing == true) {
            outState.putString("draftId", draftId); outState.putString("draftSide", draftSide.name)
            outState.putString("draftAction", KeyActionMapper.fromKeyAction(draftAction)); outState.putString("draftLabel", draftLabel)
        }
    }
    override fun onDestroyView() { dialog?.dismiss(); dialog = null; preview.cancelInput(); super.onDestroyView() }
}
