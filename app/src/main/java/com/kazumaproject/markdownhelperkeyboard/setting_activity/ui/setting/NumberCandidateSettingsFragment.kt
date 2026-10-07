package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.number.NumberCandidateConfig
import com.kazumaproject.markdownhelperkeyboard.converter.number.NumberCandidateFormat
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentNumberCandidateSettingsBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemNumberCandidateFormatBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NumberCandidateSettingsFragment : Fragment() {
    private var binding: FragmentNumberCandidateSettingsBinding? = null
    private var touchHelper: ItemTouchHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val viewBinding = FragmentNumberCandidateSettingsBinding.inflate(inflater, container, false)
        binding = viewBinding
        val adapter = FormatAdapter(AppPreference.number_candidate_config)
        viewBinding.numberFormats.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.numberFormats.adapter = adapter
        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0,
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean = adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun isLongPressDragEnabled(): Boolean = false
        }).also { it.attachToRecyclerView(viewBinding.numberFormats) }
        viewBinding.resetFormats.setOnClickListener {
            val defaults = NumberCandidateConfig()
            AppPreference.number_candidate_config = AppPreference.number_candidate_config.copy(order = defaults.normalizedOrder)
            adapter.replace(defaults)
        }
        return viewBinding.root
    }

    override fun onDestroyView() {
        touchHelper?.attachToRecyclerView(null)
        touchHelper = null
        binding?.numberFormats?.adapter = null
        binding = null
        super.onDestroyView()
    }

    private inner class FormatAdapter(config: NumberCandidateConfig) : RecyclerView.Adapter<FormatHolder>() {
        private val formats = config.normalizedOrder.toMutableList()
        init { setHasStableIds(true) }

        override fun getItemCount(): Int = formats.size
        override fun getItemId(position: Int): Long = formats[position].ordinal.toLong()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FormatHolder = FormatHolder(
            ListItemNumberCandidateFormatBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: FormatHolder, position: Int) {
            val format = formats[position]
            val name = getString(format.numberTitleResource())
            with(holder.row) {
                formatName.text = name
                formatExample.text = when (format) {
                    NumberCandidateFormat.HALF_WIDTH -> "10000円 / 10,000円 / 1万円"
                    NumberCandidateFormat.FULL_WIDTH -> "１００００円"
                    NumberCandidateFormat.KANJI -> "一万円"
                }
                dragHandle.contentDescription = getString(R.string.number_candidate_drag_handle, name)
                ViewCompat.setAccessibilityDelegate(dragHandle, object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        val currentPosition = holder.bindingAdapterPosition
                        if (currentPosition > 0) {
                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.number_candidate_action_move_up,
                                getString(R.string.number_candidate_move_up, name),
                            ))
                        }
                        if (currentPosition in 0 until formats.lastIndex) {
                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.number_candidate_action_move_down,
                                getString(R.string.number_candidate_move_down, name),
                            ))
                        }
                    }

                    override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                        val from = holder.bindingAdapterPosition
                        return when (action) {
                            R.id.number_candidate_action_move_up -> move(from, from - 1)
                            R.id.number_candidate_action_move_down -> move(from, from + 1)
                            else -> super.performAccessibilityAction(host, action, args)
                        }
                    }
                })
                dragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        touchHelper?.startDrag(holder)
                        true
                    } else {
                        false
                    }
                }
            }
        }

        fun move(from: Int, to: Int): Boolean {
            if (from !in formats.indices || to !in formats.indices || from == to) return false
            formats.add(to, formats.removeAt(from))
            notifyItemMoved(from, to)
            val first = minOf(from, to)
            notifyItemRangeChanged(first, maxOf(from, to) - first + 1)
            save()
            return true
        }

        @SuppressLint("NotifyDataSetChanged")
        fun replace(config: NumberCandidateConfig) {
            formats.clear()
            formats.addAll(config.normalizedOrder)
            notifyDataSetChanged()
        }

        private fun save() {
            AppPreference.number_candidate_config = AppPreference.number_candidate_config.copy(order = formats.toList())
        }
    }

    private class FormatHolder(val row: ListItemNumberCandidateFormatBinding) : RecyclerView.ViewHolder(row.root)

}

@StringRes
internal fun NumberCandidateFormat.numberTitleResource(): Int = when (this) {
    NumberCandidateFormat.HALF_WIDTH -> R.string.number_candidate_half_width
    NumberCandidateFormat.FULL_WIDTH -> R.string.number_candidate_full_width
    NumberCandidateFormat.KANJI -> R.string.number_candidate_kanji
}
