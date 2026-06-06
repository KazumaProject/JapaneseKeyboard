package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR
import androidx.appcompat.R as AppCompatR
import com.kazumaproject.core.R as CoreR
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSettingFrequentEditBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemSettingFrequentEditBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemSettingFrequentHeaderBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FrequentSettingsEditFragment : Fragment() {

    private var _binding: FragmentSettingFrequentEditBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var appPreference: AppPreference

    private var selectedKeys: MutableList<String> = mutableListOf()
    private var frequentAdapter: FrequentSettingsAdapter? = null
    private var itemTouchHelper: ItemTouchHelper? = null
    private var pendingDragSave = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingFrequentEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectedKeys = normalizedSelectedKeys().toMutableList()

        frequentAdapter = FrequentSettingsAdapter()
        binding.settingFrequentEditRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = frequentAdapter
        }
        setupDragAndDrop()

        binding.settingFrequentEditResetButton.setOnClickListener {
            selectedKeys = SettingDestinations.defaultFrequent(requireContext())
                .map { it.key }
                .toMutableList()
            saveSelection()
            renderRows()
        }
        renderRows()
    }

    override fun onDestroyView() {
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = null
        binding.settingFrequentEditRecyclerView.adapter = null
        frequentAdapter = null
        super.onDestroyView()
        _binding = null
    }

    private fun setupDragAndDrop() {
        itemTouchHelper = ItemTouchHelper(
            object : ItemTouchHelper.Callback() {
                override fun getMovementFlags(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ): Int {
                    val row = frequentAdapter?.destinationRowAt(viewHolder.bindingAdapterPosition)
                    val dragFlags = if (row?.selected == true) {
                        ItemTouchHelper.UP or ItemTouchHelper.DOWN
                    } else {
                        0
                    }
                    return makeMovementFlags(dragFlags, 0)
                }

                override fun isLongPressDragEnabled(): Boolean = false

                override fun isItemViewSwipeEnabled(): Boolean = false

                override fun onMove(
                    recyclerView: RecyclerView,
                    source: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean {
                    val fromRow =
                        frequentAdapter?.destinationRowAt(source.bindingAdapterPosition)
                            ?: return false
                    val toRow =
                        frequentAdapter?.destinationRowAt(target.bindingAdapterPosition)
                            ?: return false
                    if (!fromRow.selected || !toRow.selected) return false

                    val moved = moveSelectedKeyToTarget(
                        fromKey = fromRow.destination.key,
                        toKey = toRow.destination.key,
                    )
                    if (moved) {
                        pendingDragSave = true
                        frequentAdapter?.moveRow(
                            source.bindingAdapterPosition,
                            target.bindingAdapterPosition,
                        )
                    }
                    return moved
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.alpha = 1f
                    if (pendingDragSave) {
                        pendingDragSave = false
                        saveSelection()
                        renderRows()
                    }
                }

                override fun onSelectedChanged(
                    viewHolder: RecyclerView.ViewHolder?,
                    actionState: Int,
                ) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                        viewHolder?.itemView?.alpha = 0.92f
                    }
                }
            }
        ).also { helper ->
            helper.attachToRecyclerView(binding.settingFrequentEditRecyclerView)
        }
    }

    private fun renderRows() {
        val candidates = SettingDestinations.frequentCandidates(requireContext())
        val byKey = candidates.associateBy { it.key }
        val selectedDestinations = selectedKeys.mapNotNull { byKey[it] }
        val unselectedDestinations = candidates.filterNot { it.key in selectedKeys }
        val rows = buildList {
            add(
                FrequentSettingsRow.Header(
                    id = "selected",
                    title = getString(
                        R.string.setting_frequent_selected_count,
                        selectedDestinations.size,
                    ),
                )
            )
            selectedDestinations.forEachIndexed { index, destination ->
                add(
                    FrequentSettingsRow.DestinationRow(
                        destination = destination,
                        selected = true,
                        selectedIndex = index,
                    )
                )
            }
            add(
                FrequentSettingsRow.Header(
                    id = "unselected",
                    title = getString(R.string.setting_frequent_unselected),
                )
            )
            unselectedDestinations.forEach { destination ->
                add(
                    FrequentSettingsRow.DestinationRow(
                        destination = destination,
                        selected = false,
                        selectedIndex = RecyclerView.NO_POSITION,
                    )
                )
            }
        }

        frequentAdapter?.submitRows(rows)
        binding.settingFrequentEditEmptyText.isVisible = selectedKeys.isEmpty()
    }

    private fun toggle(key: String, checked: Boolean) {
        selectedKeys = if (checked) {
            (selectedKeys + key).distinct().toMutableList()
        } else {
            selectedKeys.filterNot { it == key }.toMutableList()
        }
        saveSelection()
        renderRows()
    }

    private fun move(key: String, direction: Int): Boolean {
        val currentIndex = selectedKeys.indexOf(key)
        val targetIndex = currentIndex + direction
        if (currentIndex !in selectedKeys.indices || targetIndex !in selectedKeys.indices) {
            return false
        }
        selectedKeys = selectedKeys.toMutableList().apply {
            val item = removeAt(currentIndex)
            add(targetIndex, item)
        }
        saveSelection()
        renderRows()
        return true
    }

    private fun moveSelectedKeyToTarget(fromKey: String, toKey: String): Boolean {
        val fromIndex = selectedKeys.indexOf(fromKey)
        val toIndex = selectedKeys.indexOf(toKey)
        if (fromIndex !in selectedKeys.indices || toIndex !in selectedKeys.indices) {
            return false
        }
        selectedKeys = selectedKeys.toMutableList().apply {
            val item = removeAt(fromIndex)
            add(toIndex, item)
        }
        return true
    }

    private fun normalizedSelectedKeys(): List<String> {
        val candidates = SettingDestinations.frequentCandidates(requireContext())
        val allowedKeys = candidates.map { it.key }.toSet()
        val saved = appPreference.setting_home_frequent_keys
            .filter { it in allowedKeys }
            .distinct()
        val normalized = if (appPreference.has_setting_home_frequent_keys) {
            saved
        } else {
            SettingDestinations.defaultFrequent(requireContext()).map { it.key }
        }
        appPreference.setting_home_frequent_keys = normalized
        return normalized
    }

    private fun saveSelection() {
        val allowedKeys = SettingDestinations.frequentCandidates(requireContext())
            .map { it.key }
            .toSet()
        val normalized = selectedKeys.filter { it in allowedKeys }.distinct()
        selectedKeys = normalized.toMutableList()
        appPreference.setting_home_frequent_keys = normalized
    }

    private fun resolveColor(@AttrRes attr: Int): Int {
        val value = TypedValue()
        requireContext().theme.resolveAttribute(attr, value, true)
        return if (value.resourceId != 0) {
            ContextCompat.getColor(requireContext(), value.resourceId)
        } else {
            value.data
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private sealed class FrequentSettingsRow {
        data class Header(
            val id: String,
            val title: String,
        ) : FrequentSettingsRow()

        data class DestinationRow(
            val destination: SettingDestination,
            val selected: Boolean,
            val selectedIndex: Int,
        ) : FrequentSettingsRow()
    }

    private inner class FrequentSettingsAdapter :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var rows: List<FrequentSettingsRow> = emptyList()

        fun submitRows(nextRows: List<FrequentSettingsRow>) {
            rows = nextRows
            notifyDataSetChanged()
        }

        fun destinationRowAt(position: Int): FrequentSettingsRow.DestinationRow? =
            rows.getOrNull(position) as? FrequentSettingsRow.DestinationRow

        fun moveRow(fromPosition: Int, toPosition: Int) {
            if (fromPosition !in rows.indices || toPosition !in rows.indices) return
            rows = rows.toMutableList().apply {
                val item = removeAt(fromPosition)
                add(toPosition, item)
            }
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun getItemViewType(position: Int): Int =
            when (rows[position]) {
                is FrequentSettingsRow.Header -> VIEW_TYPE_HEADER
                is FrequentSettingsRow.DestinationRow -> VIEW_TYPE_DESTINATION
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                VIEW_TYPE_HEADER -> HeaderViewHolder(
                    ItemSettingFrequentHeaderBinding.inflate(inflater, parent, false)
                )

                else -> DestinationViewHolder(
                    ItemSettingFrequentEditBinding.inflate(inflater, parent, false)
                )
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is FrequentSettingsRow.Header -> (holder as HeaderViewHolder).bind(row)
                is FrequentSettingsRow.DestinationRow -> (holder as DestinationViewHolder).bind(row)
            }
        }

        override fun getItemCount(): Int = rows.size

        private inner class HeaderViewHolder(
            private val binding: ItemSettingFrequentHeaderBinding,
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(row: FrequentSettingsRow.Header) {
                binding.settingFrequentHeaderText.text = row.title
            }
        }

        private inner class DestinationViewHolder(
            private val binding: ItemSettingFrequentEditBinding,
        ) : RecyclerView.ViewHolder(binding.root) {

            @SuppressLint("ClickableViewAccessibility")
            fun bind(row: FrequentSettingsRow.DestinationRow) {
                val destination = row.destination
                val selected = row.selected
                val actionDescription = getString(
                    if (selected) {
                        R.string.setting_frequent_remove
                    } else {
                        R.string.setting_frequent_add
                    }
                )
                val statusDescription = getString(
                    if (selected) {
                        R.string.setting_frequent_selected
                    } else {
                        R.string.setting_frequent_unselected
                    }
                )

                binding.settingFrequentCard.apply {
                    setCardBackgroundColor(
                        resolveColor(
                            if (selected) {
                                MaterialR.attr.colorSurfaceVariant
                            } else {
                                MaterialR.attr.colorSurface
                            }
                        )
                    )
                    strokeWidth = dp(1)
                    setStrokeColor(
                        resolveColor(
                            if (selected) {
                                AppCompatR.attr.colorPrimary
                            } else {
                                MaterialR.attr.colorOutline
                            }
                        )
                    )
                    contentDescription = listOf(
                        destination.title,
                        destination.summary,
                        statusDescription,
                        actionDescription,
                    ).filter { it.isNotBlank() }.joinToString(". ")
                    setOnClickListener { toggle(destination.key, !selected) }
                }

                binding.settingFrequentDragHandle.apply {
                    alpha = if (selected) 1f else 0.34f
                    isEnabled = selected
                    imageTintList = ColorStateList.valueOf(
                        resolveColor(
                            if (selected) {
                                MaterialR.attr.colorOnSurfaceVariant
                            } else {
                                MaterialR.attr.colorOutline
                            }
                        )
                    )
                    contentDescription = getString(R.string.setting_frequent_drag_reorder)
                    setOnTouchListener { _, event ->
                        if (selected && event.actionMasked == MotionEvent.ACTION_DOWN) {
                            itemTouchHelper?.startDrag(this@DestinationViewHolder)
                            true
                        } else {
                            false
                        }
                    }
                }

                binding.settingFrequentIcon.apply {
                    setImageResource(destination.iconRes)
                    imageTintList =
                        ColorStateList.valueOf(resolveColor(AppCompatR.attr.colorPrimary))
                }

                binding.settingFrequentTitle.text = destination.title
                binding.settingFrequentSummary.text = destination.summary

                binding.settingFrequentActionIcon.apply {
                    setImageResource(
                        if (selected) {
                            CoreR.drawable.baseline_check_24
                        } else {
                            CoreR.drawable.baseline_add_circle_outline_24
                        }
                    )
                    imageTintList = ColorStateList.valueOf(
                        resolveColor(
                            if (selected) {
                                AppCompatR.attr.colorPrimary
                            } else {
                                MaterialR.attr.colorOnSurfaceVariant
                            }
                        )
                    )
                    contentDescription = actionDescription
                }

                setAccessibilityMoveActions(row)
            }

            private fun setAccessibilityMoveActions(row: FrequentSettingsRow.DestinationRow) {
                ViewCompat.setAccessibilityDelegate(
                    binding.settingFrequentCard,
                    object : AccessibilityDelegateCompat() {
                        override fun onInitializeAccessibilityNodeInfo(
                            host: View,
                            info: AccessibilityNodeInfoCompat,
                        ) {
                            super.onInitializeAccessibilityNodeInfo(host, info)
                            if (!row.selected) return

                            if (row.selectedIndex > 0) {
                                info.addAction(
                                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                        R.id.setting_frequent_action_move_up,
                                        getString(
                                            R.string.setting_frequent_move_up,
                                            row.destination.title,
                                        ),
                                    )
                                )
                            }
                            if (row.selectedIndex in 0 until selectedKeys.lastIndex) {
                                info.addAction(
                                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                        R.id.setting_frequent_action_move_down,
                                        getString(
                                            R.string.setting_frequent_move_down,
                                            row.destination.title,
                                        ),
                                    )
                                )
                            }
                        }

                        override fun performAccessibilityAction(
                            host: View,
                            action: Int,
                            args: Bundle?,
                        ): Boolean {
                            if (row.selected) {
                                when (action) {
                                    R.id.setting_frequent_action_move_up ->
                                        return move(row.destination.key, -1)

                                    R.id.setting_frequent_action_move_down ->
                                        return move(row.destination.key, 1)
                                }
                            }
                            return super.performAccessibilityAction(host, action, args)
                        }
                    }
                )
            }
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_DESTINATION = 1
    }
}
