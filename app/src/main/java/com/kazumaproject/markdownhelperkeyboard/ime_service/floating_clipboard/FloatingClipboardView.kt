package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_clipboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.view.WindowManager
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.listeners.ClipboardItemAction
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.FloatingPanelFrame

/** A history list that never takes the application's input focus. */
internal class FloatingClipboardView(
    context: Context,
    onEdit: () -> Unit,
    onHandle: (MotionEvent) -> Unit,
    onHide: () -> Unit,
    private val onHistoryEnabled: (Boolean) -> Unit,
    private val onAction: (ClipboardItem, ClipboardItemAction) -> Unit,
) : FloatingPanelFrame(context, onEdit, onHandle, onHide = onHide) {
    private var font = KeyboardFontApplicator.processSnapshot
    private var menu: PopupWindow? = null
    private var busy = false
    private var changingSwitch = false
    private val history = SwitchMaterial(context).apply {
        setText(R.string.floating_clipboard_history)
        minHeight = dp(48)
        isFocusable = false
        setOnCheckedChangeListener { _, checked -> if (!changingSwitch) onHistoryEnabled(checked) }
    }
    private val empty = TextView(context).apply {
        setText(R.string.floating_clipboard_empty)
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    private val error = TextView(context).apply {
        setText(R.string.update_failed)
        visibility = GONE
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val adapter = Items()
    private val recycler = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        adapter = this@FloatingClipboardView.adapter
    }

    init {
        contentDescription = context.getString(R.string.floating_clipboard_title)
        contentContainer.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(history, LinearLayout.LayoutParams(-1, dp(48)))
            addView(error, LinearLayout.LayoutParams(-1, -2))
            addView(empty, LinearLayout.LayoutParams(-1, -2))
            addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        paintContent()
        setKeyboardFont(font)
    }

    fun updateItems(items: List<ClipboardItem>) {
        val visible = items.filter { it !== ClipboardItem.Empty }
        val rows = buildList {
            val pinned = visible.filter { it.pinned() }
            val unpinned = visible.filterNot { it.pinned() }
            if (pinned.isNotEmpty()) {
                add(Row.Header(com.kazumaproject.symbol_keyboard.R.string.symbol_clipboard_section_pinned))
                addAll(pinned.map { Row.Content(it) })
            }
            if (unpinned.isNotEmpty()) {
                add(Row.Header(com.kazumaproject.symbol_keyboard.R.string.symbol_clipboard_section_unpinned))
                addAll(unpinned.map { Row.Content(it) })
            }
        }
        empty.visibility = if (visible.isEmpty()) VISIBLE else GONE
        adapter.submitList(rows)
    }

    fun updateHistoryEnabled(enabled: Boolean) {
        changingSwitch = true
        try { history.isChecked = enabled } finally { changingSwitch = false }
    }

    fun setBusy(value: Boolean) {
        busy = value
        history.isEnabled = !value && !editing
        recycler.isEnabled = !value && !editing
        if (value) error.visibility = GONE
    }

    fun showError() { error.visibility = VISIBLE }
    fun dismissMenu() { menu?.dismiss(); menu = null }

    private fun dispatch(item: ClipboardItem, action: ClipboardItemAction) {
        if (!busy && !editing) onAction(item, action)
    }

    private fun showMenu(anchor: View, item: ClipboardItem) {
        dismissMenu()
        val actions = listOf(
            ClipboardItemAction.PASTE to com.kazumaproject.symbol_keyboard.R.string.symbol_clipboard_action_paste,
            (if (item.pinned()) ClipboardItemAction.UNPIN else ClipboardItemAction.PIN) to
                (if (item.pinned()) com.kazumaproject.symbol_keyboard.R.string.symbol_clipboard_action_unpin
                else com.kazumaproject.symbol_keyboard.R.string.symbol_clipboard_action_pin),
            ClipboardItemAction.DELETE to com.kazumaproject.symbol_keyboard.R.string.symbol_clipboard_action_delete,
        )
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        // A focusable menu can restart the application's editor when dismissed.
        // Keep the IME and its target connected while offering the same long-press actions.
        val popup = PopupWindow(content, dp(200), dp(actions.size * 48 + 8), false).apply {
            isOutsideTouchable = true
            if (android.os.Build.VERSION.SDK_INT >= 29) setIsClippedToScreen(true)
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
            elevation = dp(8).toFloat()
            setBackgroundDrawable(GradientDrawable().apply {
                cornerRadius = dp(12).toFloat(); setColor(this@FloatingClipboardView.colors.background)
            })
        }
        actions.forEach { (action, resource) ->
            content.addView(TextView(context).apply {
                setText(resource); textSize = 16f; setTextColor(inkColor)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                tag = action
                isFocusable = false
                background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(inkColor, 32)), null,
                    GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(android.graphics.Color.WHITE) })
                KeyboardFontApplicator.apply(this, font)
                setOnClickListener { popup.dismiss(); dispatch(item, action) }
            }, LinearLayout.LayoutParams(-1, dp(48)))
        }
        popup.setOnDismissListener { if (menu === popup) menu = null }
        menu = popup
        popup.showAsDropDown(anchor, 0, 0, Gravity.END)
    }

    override fun setColors(value: CandidatePanelColors) {
        if (colors == value) return
        super.setColors(value)
        paintContent()
    }

    private fun paintContent() {
        history.setTextColor(inkColor)
        val tint = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, colors.icon))
        history.thumbTintList = tint
        history.trackTintList = tint
        empty.setTextColor(inkColor)
        error.setTextColor(inkColor)
        adapter.notifyDataSetChanged()
    }

    override fun setKeyboardFont(snapshot: KeyboardFontSnapshot) {
        super.setKeyboardFont(snapshot)
        font = snapshot
        listOf(history, empty, error).forEach { KeyboardFontApplicator.apply(it, snapshot) }
        dismissMenu()
        adapter.notifyDataSetChanged()
    }

    override fun onDetachedFromWindow() { dismissMenu(); super.onDetachedFromWindow() }

    private sealed interface Row {
        data class Header(val resource: Int) : Row
        data class Content(val item: ClipboardItem) : Row
    }

    private inner class Items : ListAdapter<Row, Holder>(object : DiffUtil.ItemCallback<Row>() {
        override fun areItemsTheSame(a: Row, b: Row) = when {
            a is Row.Header && b is Row.Header -> a.resource == b.resource
            a is Row.Content && b is Row.Content -> a.item.id() == b.item.id()
            else -> false
        }
        override fun areContentsTheSame(a: Row, b: Row) = a == b
    }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                isFocusable = false
                layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                    setMargins(dp(2), dp(3), dp(2), dp(3))
                }
            }
            val text = TextView(context).apply { maxLines = 3; ellipsize = TextUtils.TruncateAt.END }
            val image = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            row.addView(text, LinearLayout.LayoutParams(-1, -2))
            row.addView(image, LinearLayout.LayoutParams(-1, dp(120)))
            return Holder(row, text, image)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.text.setTextColor(inkColor)
            KeyboardFontApplicator.apply(holder.text, font)
            holder.image.setImageDrawable(null)
            holder.image.visibility = GONE
            holder.itemView.setOnClickListener(null)
            holder.itemView.setOnLongClickListener(null)
            when (val row = getItem(position)) {
                is Row.Header -> {
                    holder.text.setText(row.resource)
                    holder.text.textSize = 12f
                    holder.itemView.minimumHeight = dp(32)
                    holder.itemView.background = null
                    holder.itemView.isClickable = false
                    holder.itemView.contentDescription = null
                }
                is Row.Content -> {
                    val item = row.item
                    holder.text.textSize = 16f
                    holder.itemView.minimumHeight = dp(64)
                    holder.text.text = if (item is ClipboardItem.Text) item.text else context.getString(R.string.floating_clipboard_image)
                    if (item is ClipboardItem.Image) {
                        holder.image.visibility = VISIBLE
                        holder.image.setImageBitmap(item.bitmap)
                    }
                    holder.itemView.contentDescription = holder.text.text
                    holder.itemView.background = RippleDrawable(
                        ColorStateList.valueOf(ColorUtils.setAlphaComponent(inkColor, 32)),
                        GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(this@FloatingClipboardView.colors.candidate) }, null)
                    holder.itemView.setOnClickListener { dispatch(item, ClipboardItemAction.PASTE) }
                    holder.itemView.setOnLongClickListener {
                        if (!busy && !editing) showMenu(it, item)
                        true
                    }
                }
            }
        }
    }

    private class Holder(view: View, val text: TextView, val image: ImageView) : RecyclerView.ViewHolder(view)
}

internal fun ClipboardItem.pinned(): Boolean = when (this) {
    is ClipboardItem.Text -> isPinned
    is ClipboardItem.Image -> isPinned
    ClipboardItem.Empty -> false
}

internal fun ClipboardItem.id(): Long = when (this) {
    is ClipboardItem.Text -> id
    is ClipboardItem.Image -> id
    ClipboardItem.Empty -> 0
}
