package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.google.android.material.R as MaterialR
import com.google.android.material.card.MaterialCardView
import androidx.appcompat.R as AppCompatR
import com.kazumaproject.core.R as CoreR
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSettingSearchBinding

class SettingSearchFragment : Fragment() {

    private var _binding: FragmentSettingSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var searchableDestinations: List<SettingDestination>

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
        searchableDestinations = SettingSearchIndex.searchable(requireContext())
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
        super.onDestroyView()
        _binding = null
    }

    private fun renderResults(query: String) {
        val normalizedQuery = query.normalizeForSearch()
        val results = if (normalizedQuery.isBlank()) {
            searchableDestinations
        } else {
            searchableDestinations.filter { destination ->
                destination.matches(normalizedQuery)
            }
        }

        binding.settingSearchResultLabel.text = getString(
            R.string.setting_search_result_count,
            results.size,
        )
        binding.settingSearchResultList.removeAllViews()

        if (results.isEmpty()) {
            binding.settingSearchResultList.addView(createEmptyView())
            return
        }

        results
            .sortedWith(compareBy<SettingDestination> { it.category.ordinal }.thenBy { it.title })
            .forEach { destination ->
                binding.settingSearchResultList.addView(createResultRow(destination))
            }
    }

    private fun SettingDestination.matches(normalizedQuery: String): Boolean {
        val targetText = buildString {
            append(key).append(' ')
            append(title).append(' ')
            append(summary).append(' ')
            append(SettingDestinations.categoryTitle(requireContext(), category)).append(' ')
            keywords.forEach { append(it).append(' ') }
        }.normalizeForSearch()
        return normalizedQuery
            .split(' ')
            .filter { it.isNotBlank() }
            .all { token -> targetText.contains(token) }
    }

    private fun createResultRow(destination: SettingDestination): View {
        val context = requireContext()
        val categoryTitle = SettingDestinations.categoryTitle(context, destination.category)
        val card = MaterialCardView(context).apply {
            radius = dp(8).toFloat()
            strokeWidth = dp(1)
            setStrokeColor(resolveColor(MaterialR.attr.colorOutline))
            setCardBackgroundColor(resolveColor(MaterialR.attr.colorSurface))
            isClickable = true
            isFocusable = true
            foreground = selectableItemBackground(context)
            contentDescription = getString(
                R.string.setting_search_result_content_description,
                destination.title,
                destination.summary,
                categoryTitle,
            )
            setOnClickListener { navigateTo(destination) }
        }

        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(12), dp(12), dp(12))
            minimumHeight = dp(78)
        }

        row.addView(
            ImageView(context).apply {
                setImageResource(destination.iconRes)
                imageTintList = ColorStateList.valueOf(resolveColor(AppCompatR.attr.colorPrimary))
            },
            LinearLayout.LayoutParams(dp(28), dp(28))
        )

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        textColumn.addView(
            TextView(context).apply {
                text = destination.title
                setTextColor(resolveColor(MaterialR.attr.colorOnSurface))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
            }
        )
        textColumn.addView(
            TextView(context).apply {
                text = destination.summary.ifBlank { categoryTitle }
                setTextColor(resolveColor(MaterialR.attr.colorOnSurfaceVariant))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 2
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(3)
            }
        )
        textColumn.addView(
            TextView(context).apply {
                text = categoryTitle
                setTextColor(resolveColor(AppCompatR.attr.colorPrimary))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 1
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
        )

        row.addView(
            textColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            }
        )
        row.addView(
            ImageView(context).apply {
                setImageResource(CoreR.drawable.baseline_arrow_right_24)
                imageTintList =
                    ColorStateList.valueOf(resolveColor(MaterialR.attr.colorOnSurfaceVariant))
            },
            LinearLayout.LayoutParams(dp(24), dp(24))
        )

        card.addView(row)
        card.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(8)
        }
        return card
    }

    private fun createEmptyView(): View =
        TextView(requireContext()).apply {
            text = getString(R.string.setting_search_empty)
            setTextColor(resolveColor(MaterialR.attr.colorOnSurfaceVariant))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(32), dp(16), dp(32))
        }

    private fun navigateTo(destination: SettingDestination) {
        when (val target = destination.destination) {
            is SettingDestinationType.NavDestination -> {
                val args = target.highlightPreferenceKey?.let { key ->
                    bundleOf(CommonPreferenceFragment.ARG_HIGHLIGHT_PREFERENCE_KEY to key)
                }
                navigateSafely(target.destinationId, args)
            }
        }
    }

    private fun String.normalizeForSearch(): String =
        lowercase()
            .replace(Regex("[_\\-.]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun selectableItemBackground(context: Context) =
        TypedValue().let { value ->
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
            AppCompatResources.getDrawable(context, value.resourceId)
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
}
