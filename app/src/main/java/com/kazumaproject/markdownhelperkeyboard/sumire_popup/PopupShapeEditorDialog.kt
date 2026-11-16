package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.kazumaproject.custom_keyboard.data.PopupOutline
import com.kazumaproject.custom_keyboard.data.PopupRegion
import com.kazumaproject.markdownhelperkeyboard.R

class PopupShapeEditorDialog : DialogFragment() {
    private val gson = Gson()
    private lateinit var editor: PopupShapeEditorView
    private val undo = arrayListOf<String>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val original = gson.fromJson(requireArguments().getString("region"), PopupRegion::class.java)
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        fun text(resource: Int) = TextView(context).apply { setText(resource); setPadding(0, dp(6), 0, dp(6)); content.addView(this) }
        text(R.string.free_flick_shape_hint)
        val count = text(R.string.free_flick_shape_rectangle)
        val status = TextView(context)
        lateinit var add: MaterialButton
        lateinit var remove: MaterialButton
        lateinit var undoButton: MaterialButton
        fun refresh() {
            count.text = when {
                editor.region.outline == null -> getString(R.string.free_flick_shape_rectangle)
                editor.vertices.isEmpty() -> getString(R.string.free_flick_shape_circle)
                else -> getString(R.string.free_flick_shape_polygon, editor.vertices.size)
            }
            add.isEnabled = editor.vertices.size < PopupOutline.MAX_VERTICES
            remove.isEnabled = editor.vertices.isNotEmpty()
            undoButton.isEnabled = undo.isNotEmpty()
        }
        editor = PopupShapeEditorView(context).apply {
            region = original
            sourceAspect = requireArguments().getFloat("aspect", 1f)
            maximumCanvasHeight = minOf(dp(320), (resources.displayMetrics.heightPixels * .40f).toInt())
            contentDescription = getString(R.string.free_flick_shape_canvas)
        }
        content.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        fun row(vararg buttons: MaterialButton) {
            content.addView(LinearLayout(context).apply {
                buttons.forEach { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
            })
        }
        fun button(resource: Int, action: () -> Unit) = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setText(resource); setOnClickListener { status.text = ""; action(); refresh() }
        }
        add = button(R.string.free_flick_shape_add) { editor.addVertex() }
        remove = button(R.string.free_flick_shape_remove) { editor.removeVertex() }
        row(add, remove)
        val reset = button(R.string.free_flick_shape_reset) { editor.resetRectangle() }
        undoButton = button(R.string.popup_undo) {
            undo.removeLastOrNull()?.let { editor.showOutline(gson.fromJson(it, PopupOutline::class.java)) }
        }
        row(reset, undoButton)
        content.addView(status)
        text(R.string.free_flick_shape_bounds_hint)
        undo.addAll(savedInstanceState?.getStringArrayList("undo").orEmpty())
        editor.onChanged = { refresh() }
        editor.onUndoPoint = { before ->
            undo += gson.toJson(before)
            if (undo.size > 50) undo.removeAt(0)
            undoButton.isEnabled = true
        }
        editor.onInvalid = { status.setText(R.string.free_flick_shape_invalid) }
        savedInstanceState?.getString("outline")?.let { editor.showOutline(gson.fromJson(it, PopupOutline::class.java)) }
        editor.selectVertex(savedInstanceState?.getInt("selected") ?: 0)
        refresh()
        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.free_flick_shape_customize)
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton(R.string.popup_cancel, null)
            .setPositiveButton(R.string.popup_apply) { _, _ ->
                parentFragmentManager.setFragmentResult(RESULT, bundleOf("map" to requireArguments().getString("map"),
                    "region" to original.id, "outline" to gson.toJson(editor.region.outline)))
            }.create()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("outline", gson.toJson(editor.region.outline))
        outState.putStringArrayList("undo", ArrayList(undo))
        outState.putInt("selected", editor.selectedVertex)
    }

    companion object {
        const val RESULT = "popup_shape_result"
        const val TAG = "popup_shape_editor"
        fun create(mapId: String, region: PopupRegion, aspect: Float) = PopupShapeEditorDialog().apply {
            arguments = bundleOf("map" to mapId, "region" to Gson().toJson(region), "aspect" to aspect)
        }
    }
}
