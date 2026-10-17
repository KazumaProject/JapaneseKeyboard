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
import com.kazumaproject.markdownhelperkeyboard.converter.date.DateCandidateConfig
import com.kazumaproject.markdownhelperkeyboard.converter.date.DateCandidateFormat
import com.kazumaproject.markdownhelperkeyboard.converter.date.DateCandidateProvider
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentDateCandidateSettingsBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemDateCandidateFormatBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

@AndroidEntryPoint
class DateCandidateSettingsFragment : Fragment() {
    private var binding: FragmentDateCandidateSettingsBinding? = null
    private var touchHelper: ItemTouchHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val viewBinding = FragmentDateCandidateSettingsBinding.inflate(inflater, container, false)
        binding = viewBinding
        val adapter = FormatAdapter(AppPreference.date_candidate_config)
        viewBinding.dateFormats.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.dateFormats.adapter = adapter
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
        }).also { it.attachToRecyclerView(viewBinding.dateFormats) }
        viewBinding.resetFormats.setOnClickListener {
            val defaults = DateCandidateConfig()
            AppPreference.date_candidate_config = defaults
            adapter.replace(defaults)
        }
        return viewBinding.root
    }

    override fun onDestroyView() {
        touchHelper?.attachToRecyclerView(null)
        touchHelper = null
        binding?.dateFormats?.adapter = null
        binding = null
        super.onDestroyView()
    }

    private inner class FormatAdapter(config: DateCandidateConfig) : RecyclerView.Adapter<FormatHolder>() {
        private val formats = config.normalizedOrder.toMutableList()
        private val enabledFormats = config.enabledFormats.toMutableSet()
        // Single-digit month and day make zero padding visible regardless of today's date.
        private val previewDate = GregorianCalendar(Locale.ROOT).apply {
            clear()
            set(2026, Calendar.JANUARY, 4, 12, 0, 0)
        }

        init { setHasStableIds(true) }

        override fun getItemCount(): Int = formats.size
        override fun getItemId(position: Int): Long = formats[position].ordinal.toLong()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FormatHolder = FormatHolder(
            ListItemDateCandidateFormatBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: FormatHolder, position: Int) {
            val format = formats[position]
            val name = getString(format.titleResource())
            with(holder.row) {
                formatName.text = name
                formatExample.text = DateCandidateProvider.format(format, previewDate)
                formatEnabled.contentDescription = name
                formatEnabled.setOnCheckedChangeListener(null)
                formatEnabled.isChecked = format in enabledFormats
                formatEnabled.setOnCheckedChangeListener { _, checked ->
                    if (checked) enabledFormats.add(format) else enabledFormats.remove(format)
                    save()
                }
                dragHandle.contentDescription = getString(R.string.date_candidate_drag_handle, name)
                ViewCompat.setAccessibilityDelegate(dragHandle, object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        val currentPosition = holder.bindingAdapterPosition
                        if (currentPosition > 0) {
                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.date_candidate_action_move_up,
                                getString(R.string.date_candidate_move_up, name),
                            ))
                        }
                        if (currentPosition in 0 until formats.lastIndex) {
                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.date_candidate_action_move_down,
                                getString(R.string.date_candidate_move_down, name),
                            ))
                        }
                    }

                    override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                        val from = holder.bindingAdapterPosition
                        return when (action) {
                            R.id.date_candidate_action_move_up -> move(from, from - 1)
                            R.id.date_candidate_action_move_down -> move(from, from + 1)
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
        fun replace(config: DateCandidateConfig) {
            formats.clear()
            formats.addAll(config.normalizedOrder)
            enabledFormats.clear()
            enabledFormats.addAll(config.enabledFormats)
            notifyDataSetChanged()
        }

        private fun save() {
            AppPreference.date_candidate_config = DateCandidateConfig(
                order = formats.toList(),
                enabledFormats = enabledFormats.toSet(),
            )
        }
    }

    private class FormatHolder(val row: ListItemDateCandidateFormatBinding) : RecyclerView.ViewHolder(row.root)

    @StringRes
    private fun DateCandidateFormat.titleResource(): Int = when (this) {
        DateCandidateFormat.YEAR_MONTH_DAY -> R.string.date_candidate_format_year_month_day
        DateCandidateFormat.MONTH_DAY -> R.string.date_candidate_format_month_day
        DateCandidateFormat.MONTH_DAY_WEEKDAY -> R.string.date_candidate_format_month_day_weekday
        DateCandidateFormat.REIWA_DATE -> R.string.date_candidate_format_reiwa_date
        DateCandidateFormat.REIWA_SHORT_DATE -> R.string.date_candidate_format_reiwa_short_date
        DateCandidateFormat.WEEKDAY_SHORT -> R.string.date_candidate_format_weekday_short
        DateCandidateFormat.WEEKDAY_LONG -> R.string.date_candidate_format_weekday_long
    }
}
