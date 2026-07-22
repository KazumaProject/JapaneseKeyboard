package com.kazumaproject.markdownhelperkeyboard.short_cut

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.appcompat.R as AppCompatR
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR
import com.kazumaproject.core.R as CoreR
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemShortcutToolbarAddCardBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemShortcutToolbarEditCardBinding

class ShortcutToolbarSelectedAdapter(
    private val onRemove: (ShortcutType) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onMove: (type: ShortcutType, direction: Int) -> Boolean,
) : ListAdapter<ShortcutType, ShortcutToolbarSelectedAdapter.ViewHolder>(DiffCallback) {

    private var canRemove: Boolean = false

    fun setCanRemove(canRemove: Boolean) {
        if (this.canRemove == canRemove) return
        this.canRemove = canRemove
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemShortcutToolbarEditCardBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position, itemCount, canRemove)
    }

    inner class ViewHolder(
        private val binding: ItemShortcutToolbarEditCardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(type: ShortcutType, position: Int, count: Int, canRemove: Boolean) {
            val context = binding.root.context
            val summary = type.summary(context)

            binding.shortcutToolbarEditCard.apply {
                isClickable = false
                isFocusable = true
                setCardBackgroundColor(resolveColor(MaterialR.attr.colorSurface))
                strokeWidth = dp(1)
                setStrokeColor(resolveColor(MaterialR.attr.colorOutline))
                contentDescription = listOf(
                    type.description,
                    summary,
                    context.getString(R.string.shortcut_toolbar_selected),
                    context.getString(R.string.shortcut_toolbar_drag_reorder),
                ).joinToString(". ")
            }

            binding.shortcutToolbarDragHandle.apply {
                isEnabled = true
                imageTintList = ColorStateList.valueOf(resolveColor(MaterialR.attr.colorOnSurfaceVariant))
                contentDescription = context.getString(R.string.shortcut_toolbar_drag_reorder)
                setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onStartDrag(this@ViewHolder)
                        true
                    } else {
                        false
                    }
                }
            }

            binding.shortcutToolbarIcon.apply {
                setImageResource(type.iconResId)
                imageTintList = ColorStateList.valueOf(resolveColor(AppCompatR.attr.colorPrimary))
            }
            binding.shortcutToolbarTitle.text = type.description
            binding.shortcutToolbarSummary.text = summary
            binding.shortcutToolbarRemoveButton.apply {
                isEnabled = canRemove
                alpha = if (canRemove) 1f else 0.38f
                imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, CoreR.color.red)
                )
                contentDescription = context.getString(
                    R.string.shortcut_toolbar_remove_content_description,
                    type.description,
                )
                setOnClickListener {
                    onRemove(type)
                }
            }

            setAccessibilityMoveActions(type, position, count)
        }

        private fun setAccessibilityMoveActions(type: ShortcutType, position: Int, count: Int) {
            ViewCompat.setAccessibilityDelegate(
                binding.shortcutToolbarEditCard,
                object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        if (position > 0) {
                            info.addAction(
                                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    R.id.shortcut_toolbar_action_move_up,
                                    binding.root.context.getString(
                                        R.string.shortcut_toolbar_move_up,
                                        type.description,
                                    ),
                                )
                            )
                        }
                        if (position in 0 until count - 1) {
                            info.addAction(
                                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    R.id.shortcut_toolbar_action_move_down,
                                    binding.root.context.getString(
                                        R.string.shortcut_toolbar_move_down,
                                        type.description,
                                    ),
                                )
                            )
                        }
                    }

                    override fun performAccessibilityAction(
                        host: View,
                        action: Int,
                        args: Bundle?,
                    ): Boolean =
                        when (action) {
                            R.id.shortcut_toolbar_action_move_up -> onMove(type, -1)
                            R.id.shortcut_toolbar_action_move_down -> onMove(type, 1)
                            else -> super.performAccessibilityAction(host, action, args)
                        }
                }
            )
        }

        private fun resolveColor(@AttrRes attr: Int): Int =
            binding.root.context.resolveThemeColor(attr)

        private fun dp(value: Int): Int =
            (value * binding.root.resources.displayMetrics.density).toInt()
    }
}

class ShortcutToolbarAddAdapter(
    private val onAdd: (ShortcutType) -> Unit,
) : ListAdapter<ShortcutType, ShortcutToolbarAddAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemShortcutToolbarAddCardBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemShortcutToolbarAddCardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(type: ShortcutType) {
            val context = binding.root.context
            val summary = type.summary(context)

            binding.shortcutToolbarAddCard.apply {
                isClickable = true
                isFocusable = true
                setCardBackgroundColor(resolveColor(MaterialR.attr.colorSurface))
                strokeWidth = dp(1)
                setStrokeColor(resolveColor(MaterialR.attr.colorOutline))
                contentDescription = listOf(
                    type.description,
                    summary,
                    context.getString(R.string.shortcut_toolbar_available),
                ).joinToString(". ")
                setOnClickListener { onAdd(type) }
            }
            binding.shortcutToolbarAddIcon.apply {
                setImageResource(type.iconResId)
                imageTintList = ColorStateList.valueOf(resolveColor(AppCompatR.attr.colorPrimary))
            }
            binding.shortcutToolbarAddTitle.text = type.description
            binding.shortcutToolbarAddSummary.text = summary
            binding.shortcutToolbarAddButton.apply {
                imageTintList = ColorStateList.valueOf(resolveColor(AppCompatR.attr.colorPrimary))
                contentDescription = context.getString(
                    R.string.shortcut_toolbar_add_content_description,
                    type.description,
                )
                setOnClickListener { onAdd(type) }
            }
        }

        private fun resolveColor(@AttrRes attr: Int): Int =
            binding.root.context.resolveThemeColor(attr)

        private fun dp(value: Int): Int =
            (value * binding.root.resources.displayMetrics.density).toInt()
    }
}

private object DiffCallback : DiffUtil.ItemCallback<ShortcutType>() {
    override fun areItemsTheSame(oldItem: ShortcutType, newItem: ShortcutType): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: ShortcutType, newItem: ShortcutType): Boolean =
        oldItem == newItem
}

fun ShortcutType.summary(context: android.content.Context): String =
    when (this) {
        ShortcutType.SETTINGS -> context.getString(R.string.shortcut_toolbar_summary_settings)
        ShortcutType.EMOJI -> context.getString(R.string.shortcut_toolbar_summary_emoji)
        ShortcutType.TEMPLATE -> context.getString(R.string.shortcut_toolbar_summary_template)
        ShortcutType.KEYBOARD_PICKER -> context.getString(R.string.shortcut_toolbar_summary_keyboard_picker)
        ShortcutType.KEYBOARD_LAYOUT_EDIT -> context.getString(R.string.shortcut_toolbar_summary_keyboard_layout_edit)
        ShortcutType.KEYBOARD_FLOATING_TOGGLE -> context.getString(R.string.shortcut_toolbar_summary_keyboard_floating_toggle)
        ShortcutType.INPUT_BEHAVIOR_TOGGLE -> context.getString(R.string.shortcut_toolbar_summary_input_behavior_toggle)
        ShortcutType.LIVE_CONVERSION_TOGGLE -> context.getString(R.string.shortcut_toolbar_summary_live_conversion_toggle)
        ShortcutType.LEARNING_PAUSE -> context.getString(R.string.shortcut_toolbar_summary_learning_pause)
        ShortcutType.SELECT_ALL -> context.getString(R.string.shortcut_toolbar_summary_select_all)
        ShortcutType.COPY -> context.getString(R.string.shortcut_toolbar_summary_copy)
        ShortcutType.PASTE -> context.getString(R.string.shortcut_toolbar_summary_paste)
        ShortcutType.DATE_PICKER -> context.getString(R.string.shortcut_toolbar_summary_date_picker)
        ShortcutType.VOICE_INPUT -> context.getString(R.string.shortcut_toolbar_summary_voice_input)
        ShortcutType.CLIP_BOARD -> context.getString(R.string.shortcut_toolbar_summary_clip_board)
    }

private fun android.content.Context.resolveThemeColor(@AttrRes attr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return if (value.resourceId != 0) {
        ContextCompat.getColor(this, value.resourceId)
    } else {
        value.data
    }
}
