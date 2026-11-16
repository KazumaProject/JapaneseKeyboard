package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentClassifiedDictionarySettingsBinding
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCompatibilityProblem
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCompatibilityResult
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCompatibilityValidator
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpecs
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.android.material.R as MaterialR

@AndroidEntryPoint
class ClassifiedDictionarySettingsFragment : Fragment() {
    @Inject lateinit var storeProvider: javax.inject.Provider<DictionaryOverrideStore>
    @Inject lateinit var validatorProvider: javax.inject.Provider<DictionaryCompatibilityValidator>
    private var _binding: FragmentClassifiedDictionarySettingsBinding? = null
    private val binding get() = _binding!!
    private var loadingUi: SettingsLoadingUi? = null
    private var loadJob: Job? = null
    private var changeJob: Job? = null
    private lateinit var tree: ClassifiedDictionaryNode
    private lateinit var group: ClassifiedDictionaryNode
    private lateinit var store: DictionaryOverrideStore
    private lateinit var adapter: CategoryAdapter
    private var problems: Map<DictionaryCategory, List<DictionaryCompatibilityProblem>> = emptyMap()
    private var filter = DictionarySelectionFilter.ALL
    private var query = ""
    private var visible = emptyList<ClassifiedDictionaryNode>()
    private var snackbar: Snackbar? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentClassifiedDictionarySettingsBinding.inflate(inflater, container, false)
        return SettingsLoadingUi(requireContext(), ::load).also { loadingUi = it }.wrap(binding.root)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        query = savedInstanceState?.getString("query").orEmpty()
        filter = savedInstanceState?.getString("filter")?.let { name -> DictionarySelectionFilter.entries.find { it.name == name } }
            ?: DictionarySelectionFilter.ALL
        adapter = CategoryAdapter(::openGroup, ::toggle)
        binding.classifiedList.layoutManager = LinearLayoutManager(requireContext())
        binding.classifiedList.adapter = adapter
        binding.classifiedSearch.setText(query)
        binding.classifiedFilters.check(when (filter) {
            DictionarySelectionFilter.ALL -> R.id.classified_filter_all
            DictionarySelectionFilter.ENABLED -> R.id.classified_filter_enabled
            DictionarySelectionFilter.DISABLED -> R.id.classified_filter_disabled
        })
        binding.classifiedSearch.doAfterTextChanged { query = it?.toString().orEmpty(); refresh() }
        binding.classifiedFilters.setOnCheckedStateChangeListener { _, ids ->
            filter = when (ids.firstOrNull()) {
                R.id.classified_filter_enabled -> DictionarySelectionFilter.ENABLED
                R.id.classified_filter_disabled -> DictionarySelectionFilter.DISABLED
                else -> DictionarySelectionFilter.ALL
            }
            refresh()
        }
        binding.classifiedEnableAll.setOnClickListener { update(visible.flatMap { it.leaves() }, true) }
        binding.classifiedDisableAll.setOnClickListener { update(visible.flatMap { it.leaves() }, false) }
        load()
    }

    private fun load() {
        if (loadJob?.isActive == true) return
        val context = requireContext()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            loadingUi?.load {
                val result = settingsIo(SettingsLoadStage.DATABASE) {
                    val nextStore = storeProvider.get()
                    val nextTree = ClassifiedDictionaryNode.load(context)
                    val validator = validatorProvider.get()
                    val nextProblems = nextTree.leaves().associate { node ->
                        val category = requireNotNull(node.category)
                        val result = if (DictionaryFileSpecs.forCategory(category).all { nextStore.isValidOverride(it.key) }) {
                            validator.validateCategoryReplacement(category)
                        } else DictionaryCompatibilityResult.Compatible
                        category to (result as? DictionaryCompatibilityResult.Incompatible)?.problems.orEmpty()
                    }
                    Triple(nextTree, nextStore, nextProblems)
                }
                tree = result.first
                store = result.second
                problems = result.third
                val highlight = arguments?.getString(CommonPreferenceFragment.ARG_HIGHLIGHT_PREFERENCE_KEY)
                group = arguments?.getString(ARG_GROUP)?.let(tree::find)
                    ?: highlight?.let { tree.pathTo(it).dropLast(1).lastOrNull() }
                    ?: tree
                refresh(highlight)
            }
        }
    }

    private fun refresh(highlight: String? = null) {
        if (_binding == null || !::group.isInitialized) return
        visible = group.visible(query, filter, store::isOptionalBundledEnabled)
        val all = group.leaves()
        val enabled = all.count { store.isOptionalBundledEnabled(requireNotNull(it.category)) }
        binding.classifiedBreadcrumb.text = tree.pathTo(group.key).joinToString(" › ") { it.title }
        binding.classifiedCount.text = getString(R.string.classified_group_enabled_count, enabled, all.size)
        val scope = visible.flatMap { it.leaves() }.distinctBy { it.key }
        binding.classifiedBulkScope.text = getString(R.string.classified_bulk_scope, scope.size)
        binding.classifiedEnableAll.isEnabled = scope.any { !store.isOptionalBundledEnabled(requireNotNull(it.category)) }
        binding.classifiedDisableAll.isEnabled = scope.any { store.isOptionalBundledEnabled(requireNotNull(it.category)) }
        binding.classifiedEmpty.isVisible = visible.isEmpty()
        adapter.submitList(visible.map { node ->
            val leaves = node.leaves()
            CategoryRow(node, leaves.count { store.isOptionalBundledEnabled(requireNotNull(it.category)) }, leaves.size,
                if (node.category != null && (query.isNotBlank() || filter != DictionarySelectionFilter.ALL))
                    tree.pathTo(node.key).drop(1).dropLast(1).joinToString(" › ") { it.title } else "")
        }) {
            if (_binding != null && highlight != null) {
                val position = adapter.currentList.indexOfFirst { it.node.key == highlight }
                if (position >= 0) binding.classifiedList.scrollToPosition(position)
            }
        }
    }

    private fun openGroup(node: ClassifiedDictionaryNode) {
        navigateSafely(R.id.action_classified_dictionary_group, bundleOf(ARG_GROUP to node.key))
    }

    private fun toggle(node: ClassifiedDictionaryNode, enabled: Boolean) = update(node.leaves(), enabled)

    private data class PreviousSelection(val category: DictionaryCategory, val bundled: Boolean, val external: Boolean)

    private fun update(nodes: List<ClassifiedDictionaryNode>, enabled: Boolean) {
        if (changeJob?.isActive == true || nodes.isEmpty()) return
        snackbar?.dismiss()
        changeJob = viewLifecycleOwner.lifecycleScope.launch {
            loadingUi?.load {
                val previous = settingsIo(SettingsLoadStage.DATABASE) {
                    nodes.mapNotNull { it.category }.distinct().filter { store.isOptionalBundledEnabled(it) != enabled }.map { category ->
                        PreviousSelection(category, store.isOptionalBundledEnabled(category), store.isExternalEnabledForCategory(category))
                    }.also { selections ->
                        selections.forEach { store.setDisableableCategoryEnabled(it.category, enabled, problems[it.category].isNullOrEmpty()) }
                    }
                }
                refresh()
                if (previous.isEmpty()) return@load
                val revision = store.currentRevision
                snackbar = Snackbar.make(binding.root, getString(R.string.classified_change_message, previous.size), Snackbar.LENGTH_LONG)
                    .setAction(R.string.classified_undo) { undo(previous, revision) }.also { it.show() }
                val incompatible = if (enabled) previous.flatMap { problems[it.category].orEmpty() }.distinct() else emptyList()
                if (incompatible.isNotEmpty()) showCompatibilityProblems(incompatible)
            }
        }
    }

    private fun undo(previous: List<PreviousSelection>, revision: Long) {
        if (changeJob?.isActive == true) return
        changeJob = viewLifecycleOwner.lifecycleScope.launch {
            loadingUi?.load {
                settingsIo(SettingsLoadStage.DATABASE) {
                    // Do not undo over changes made by another settings surface.
                    if (store.currentRevision == revision) previous.forEach {
                        store.setOptionalBundledEnabled(it.category, it.bundled)
                        store.setExternalEnabledForCategory(it.category, it.external)
                    }
                }
                refresh()
            }
        }
    }

    private fun showCompatibilityProblems(problems: List<DictionaryCompatibilityProblem>) {
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.external_dictionary_compat_dialog_title)
            .setMessage((listOf(getString(R.string.external_dictionary_compat_imported_but_not_usable),
                getString(R.string.external_dictionary_compat_import_matching_common_files)) +
                problems.map { getString(it.messageResId, *it.messageArgs.toTypedArray()) }).joinToString("\n"))
            .setPositiveButton(android.R.string.ok, null).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("query", query)
        outState.putString("filter", filter.name)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        snackbar?.dismiss()
        snackbar = null
        loadJob?.cancel()
        changeJob?.cancel()
        binding.classifiedList.adapter = null
        loadingUi = null
        super.onDestroyView()
        _binding = null
    }

    private data class CategoryRow(val node: ClassifiedDictionaryNode, val enabled: Int, val total: Int, val path: String)

    private class CategoryAdapter(
        val open: (ClassifiedDictionaryNode) -> Unit,
        val toggle: (ClassifiedDictionaryNode, Boolean) -> Unit,
    ) : ListAdapter<CategoryRow, CategoryAdapter.Holder>(object : DiffUtil.ItemCallback<CategoryRow>() {
        override fun areItemsTheSame(old: CategoryRow, new: CategoryRow) = old.node.key == new.node.key
        override fun areContentsTheSame(old: CategoryRow, new: CategoryRow) = old == new
    }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(parent.context)
        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

        inner class Holder(context: Context) : RecyclerView.ViewHolder(MaterialCardView(context)) {
            private fun dp(value: Int) = (value * itemView.resources.displayMetrics.density).toInt()
            private val card = itemView as MaterialCardView
            private val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)); minimumHeight = dp(72) }
            private val check = MaterialCheckBox(context)
            private val title = TextView(context).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); setTextColor(MaterialColors.getColor(this, MaterialR.attr.colorOnSurface)) }
            private val summary = TextView(context).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(MaterialColors.getColor(this, MaterialR.attr.colorOnSurfaceVariant)) }
            private val switch = MaterialSwitch(context)
            init {
                card.radius = dp(18).toFloat()
                card.cardElevation = 0f
                card.strokeWidth = dp(1).coerceAtLeast(1)
                card.setStrokeColor(MaterialColors.getColor(card, MaterialR.attr.colorOutlineVariant))
                card.setCardBackgroundColor(MaterialColors.getColor(card, MaterialR.attr.colorSurfaceContainerLow))
                card.layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
                row.addView(check, LinearLayout.LayoutParams(dp(48), dp(48)))
                val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(title); addView(summary) }
                row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
                row.addView(switch, LinearLayout.LayoutParams(-2, dp(48)))
                card.addView(row)
            }
            fun bind(value: CategoryRow) {
                val node = value.node
                val group = node.category == null
                title.text = node.title
                summary.text = if (group) itemView.context.getString(R.string.classified_group_enabled_count, value.enabled, value.total) else value.path
                summary.isVisible = summary.text.isNotBlank()
                check.isVisible = group
                switch.isVisible = !group
                check.setOnCheckedChangeListener(null)
                check.checkedState = when (value.enabled) { 0 -> MaterialCheckBox.STATE_UNCHECKED; value.total -> MaterialCheckBox.STATE_CHECKED; else -> MaterialCheckBox.STATE_INDETERMINATE }
                check.contentDescription = itemView.context.getString(R.string.classified_group_toggle, node.title)
                check.setOnCheckedChangeListener { _, checked -> toggle(node, checked) }
                switch.setOnCheckedChangeListener(null)
                switch.isChecked = value.enabled == value.total
                switch.contentDescription = node.title
                switch.setOnCheckedChangeListener { _, checked -> toggle(node, checked) }
                card.isClickable = true
                card.isFocusable = true
                card.contentDescription = if (group) itemView.context.getString(R.string.classified_group_open, node.title) else null
                card.setOnClickListener { if (group) open(node) else toggle(node, !switch.isChecked) }
            }
        }
    }

    companion object { const val ARG_GROUP = "dictionaryGroup" }
}
