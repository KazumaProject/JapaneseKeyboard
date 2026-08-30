package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.utility.Precision
import com.kazumaproject.markdownhelperkeyboard.converter.utility.UnitCategory
import com.kazumaproject.markdownhelperkeyboard.converter.utility.UnitRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.utility.UnitTargetSetting
import com.kazumaproject.markdownhelperkeyboard.converter.utility.UtilityCandidateConfig
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import java.util.Collections

@AndroidEntryPoint
class UnitTargetSettingsFragment : Fragment() {
    private val registry = UnitRegistry.Default
    private lateinit var category: UnitCategory
    private lateinit var adapter: TargetAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        category = arguments?.getString(ARG_CATEGORY)?.let {
            runCatching { UnitCategory.valueOf(it) }.getOrNull()
        } ?: UnitCategory.LENGTH
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 12.dp, 16.dp, 12.dp)
        }
        root.addView(TextView(context).apply {
            text = getString(R.string.utility_drag_hint)
            setPadding(8.dp, 4.dp, 8.dp, 12.dp)
        })
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        adapter = TargetAdapter(
            items = AppPreference.utility_candidate_config.unitTargets[category]
                .orEmpty().toMutableList(),
            onPrecision = ::showPrecisionDialog,
            onRemove = { position ->
                adapter.remove(position)
                saveTargets()
            },
        )
        recyclerView.adapter = adapter
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0,
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                saveTargets()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        }).attachToRecyclerView(recyclerView)
        root.addView(recyclerView)
        root.addView(Button(context).apply {
            text = getString(R.string.utility_add_target)
            setOnClickListener { showAddDialog() }
        })
        return root
    }

    private fun showAddDialog() {
        if (adapter.items.size >= UtilityCandidateConfig.MAX_TARGETS_PER_CATEGORY) {
            Toast.makeText(
                requireContext(),
                getString(
                    R.string.utility_target_limit,
                    UtilityCandidateConfig.MAX_TARGETS_PER_CATEGORY,
                ),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val selectedIds = adapter.items.mapTo(mutableSetOf()) { it.unitId }
        val available = registry.units.filter { it.category == category && it.id !in selectedIds }
            .sortedBy { it.symbol }
        if (available.isEmpty()) {
            Toast.makeText(requireContext(), R.string.utility_no_more_targets, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.utility_add_target)
            .setItems(available.map { it.symbol }.toTypedArray()) { _, index ->
                adapter.add(UnitTargetSetting(available[index].id, Precision.Auto))
                saveTargets()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPrecisionDialog(position: Int) {
        val current = adapter.items.getOrNull(position) ?: return
        val values: List<Precision> = listOf(Precision.Auto, Precision.Integer) +
            (Precision.MIN_DIGITS..Precision.MAX_DIGITS).map(Precision::SignificantDigits)
        val labels = values.map { precisionLabel(it) }.toTypedArray()
        val selected = values.indexOf(current.precision).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.utility_calculation_precision_title)
            .setSingleChoiceItems(labels, selected) { dialog, index ->
                adapter.updatePrecision(position, values[index])
                saveTargets()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveTargets() {
        val current = AppPreference.utility_candidate_config
        AppPreference.utility_candidate_config = current.copy(
            unitTargets = current.unitTargets + (category to adapter.items.toList()),
        )
    }

    private fun precisionLabel(precision: Precision): String = when (precision) {
        Precision.Auto -> getString(R.string.utility_precision_auto)
        Precision.Integer -> getString(R.string.utility_precision_integer)
        is Precision.SignificantDigits -> getString(
            R.string.utility_precision_digits,
            precision.digits,
        )
    }

    private inner class TargetAdapter(
        val items: MutableList<UnitTargetSetting>,
        private val onPrecision: (Int) -> Unit,
        private val onRemove: (Int) -> Unit,
    ) : RecyclerView.Adapter<TargetViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TargetViewHolder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8.dp, 6.dp, 8.dp, 6.dp)
            }
            val symbol = TextView(parent.context).apply {
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val precision = Button(parent.context)
            val remove = Button(parent.context).apply {
                text = "×"
                contentDescription = getString(R.string.utility_remove_target)
            }
            row.addView(symbol)
            row.addView(precision)
            row.addView(remove)
            return TargetViewHolder(row, symbol, precision, remove)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: TargetViewHolder, position: Int) {
            val item = items[position]
            holder.symbol.text = registry.findById(item.unitId)?.symbol ?: item.unitId.value
            holder.precision.text = precisionLabel(item.precision)
            holder.precision.setOnClickListener { onPrecision(holder.bindingAdapterPosition) }
            holder.remove.setOnClickListener { onRemove(holder.bindingAdapterPosition) }
        }

        fun add(item: UnitTargetSetting) {
            items += item
            notifyItemInserted(items.lastIndex)
        }

        fun remove(position: Int) {
            if (position !in items.indices) return
            items.removeAt(position)
            notifyItemRemoved(position)
        }

        fun move(from: Int, to: Int) {
            if (from !in items.indices || to !in items.indices || from == to) return
            Collections.swap(items, from, to)
            notifyItemMoved(from, to)
        }

        fun updatePrecision(position: Int, precision: Precision) {
            if (position !in items.indices) return
            items[position] = items[position].copy(precision = precision)
            notifyItemChanged(position)
        }
    }

    private class TargetViewHolder(
        itemView: View,
        val symbol: TextView,
        val precision: Button,
        val remove: Button,
    ) : RecyclerView.ViewHolder(itemView)

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val ARG_CATEGORY = "category"
    }
}
