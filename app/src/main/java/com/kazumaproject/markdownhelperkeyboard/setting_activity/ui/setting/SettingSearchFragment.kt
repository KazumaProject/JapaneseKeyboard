package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSettingSearchBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

class SettingSearchFragment : Fragment() {

    companion object {
        const val ARG_SEARCH_SCOPE = "setting_search_scope"
    }

    private var _binding: FragmentSettingSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var searchableDestinations: List<SettingDestination>
    private lateinit var searchAdapter: SettingSearchAdapter
    private lateinit var settingCardEditorController: SettingCardEditorController
    private val searchScope: SettingSearchScope by lazy {
        arguments
            ?.getString(ARG_SEARCH_SCOPE)
            ?.let { runCatching { SettingSearchScope.valueOf(it) }.getOrNull() }
            ?: SettingSearchScope.NEW_HOME
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingCardEditorController = SettingCardEditorController(requireContext())
        searchableDestinations = SettingSearchIndex.searchable(requireContext(), searchScope)
        searchAdapter = SettingSearchAdapter(
            editorController = settingCardEditorController,
            onClick = ::handleSearchResultClick,
        )

        binding.settingSearchResultRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }
        binding.settingSearchInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderResults(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            }
        )
        renderResults("")
    }

    override fun onDestroyView() {
        binding.settingSearchResultRecyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private fun renderResults(query: String) {
        val normalizedQuery = SettingSearchIndex.normalizeForSearch(query)
        val touchEffectType = AppPreference.keyboard_touch_effect_type_preference
        val visibleDestinations = searchableDestinations.filter { destination ->
            KeyboardTouchEffectSettingVisibility.isVisibleForEffect(
                destination = destination,
                effectType = touchEffectType,
            )
        }
        val results = SettingSearchIndex.search(
            context = requireContext(),
            destinations = visibleDestinations,
            query = query,
        )
        searchAdapter.submitList(results)

        when {
            normalizedQuery.isBlank() -> {
                binding.settingSearchResultLabel.isVisible = false
                showEmptyState(getString(R.string.setting_search_initial_empty))
            }

            results.isEmpty() -> {
                updateResultCount(0)
                showEmptyState(getString(R.string.setting_search_empty))
            }

            else -> {
                updateResultCount(results.size)
                binding.settingSearchEmptyText.isVisible = false
                binding.settingSearchResultRecyclerView.isVisible = true
            }
        }
    }

    private fun updateResultCount(count: Int) {
        val text = getString(R.string.setting_search_result_count, count)
        binding.settingSearchResultLabel.apply {
            isVisible = true
            this.text = text
            contentDescription = text
        }
    }

    private fun showEmptyState(message: String) {
        binding.settingSearchResultRecyclerView.isVisible = false
        binding.settingSearchEmptyText.apply {
            isVisible = true
            text = message
            contentDescription = message
        }
    }

    private fun navigateTo(destination: SettingDestination) {
        val legacyTarget = destination.legacyTarget
        if (legacyTarget != null) {
            navigateToLegacyPreference(legacyTarget)
            return
        }
        val destinationId = SettingDestinations.destinationId(destination.destination) ?: return
        val args = SettingDestinations.highlightPreferenceKey(destination.destination)?.let { key ->
            bundleOf(CommonPreferenceFragment.ARG_HIGHLIGHT_PREFERENCE_KEY to key)
        }
        navigateSafely(destinationId, args)
    }

    private fun navigateToLegacyPreference(target: LegacySettingTarget) {
        val args = if (target.filterResultMode) {
            bundleOf(
                LegacyPreferenceResultFilter.ARG_LEGACY_SEARCH_RESULT_MODE to true,
                LegacyPreferenceResultFilter.ARG_LEGACY_SEARCH_TARGET_KEY to target.preferenceKey,
                LegacyPreferenceResultFilter.ARG_LEGACY_SEARCH_RELATED_KEYS to
                    target.relatedPreferenceKeys.toTypedArray(),
                CommonPreferenceFragment.ARG_HIGHLIGHT_PREFERENCE_KEY to target.preferenceKey,
            )
        } else {
            null
        }
        navigateSafely(target.destinationId, args)
    }

    private fun handleSearchResultClick(destination: SettingDestination) {
        if (destination.legacyTarget != null) {
            navigateTo(destination)
            return
        }
        val handled = settingCardEditorController.handleClick(destination) {
            searchAdapter.notifyDestinationChanged(destination)
        }
        if (!handled) {
            navigateTo(destination)
        }
    }
}
