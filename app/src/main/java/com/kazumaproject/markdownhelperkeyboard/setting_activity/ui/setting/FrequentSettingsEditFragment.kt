package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.R as MaterialR
import com.google.android.material.card.MaterialCardView
import androidx.appcompat.R as AppCompatR
import com.kazumaproject.core.R as CoreR
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSettingFrequentEditBinding
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
        super.onDestroyView()
        _binding = null
    }

    private fun renderRows() {
        val candidates = SettingDestinations.frequentCandidates(requireContext())
        val byKey = candidates.associateBy { it.key }
        val orderedRows = selectedKeys.mapNotNull { byKey[it] } +
            candidates.filterNot { it.key in selectedKeys }

        binding.settingFrequentEditList.removeAllViews()
        orderedRows.forEach { destination ->
            binding.settingFrequentEditList.addView(createRow(destination))
        }
        binding.settingFrequentEditEmptyText.isVisible = selectedKeys.isEmpty()
    }

    private fun createRow(destination: SettingDestination): View {
        val context = requireContext()
        val isSelected = destination.key in selectedKeys
        val selectedIndex = selectedKeys.indexOf(destination.key)

        val card = MaterialCardView(context).apply {
            radius = dp(8).toFloat()
            strokeWidth = dp(1)
            setStrokeColor(resolveColor(MaterialR.attr.colorOutline))
            setCardBackgroundColor(resolveColor(MaterialR.attr.colorSurface))
            isClickable = true
            isFocusable = true
            foreground = selectableItemBackground(context)
            contentDescription = destination.title
            setOnClickListener { toggle(destination.key, !isSelected) }
        }

        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(10), dp(8), dp(10))
            minimumHeight = dp(78)
        }

        row.addView(
            CheckBox(context).apply {
                isChecked = isSelected
                contentDescription = getString(
                    R.string.setting_frequent_select_content_description,
                    destination.title,
                )
                setOnCheckedChangeListener { _, checked ->
                    if ((destination.key in selectedKeys) != checked) {
                        toggle(destination.key, checked)
                    }
                }
            },
            LinearLayout.LayoutParams(dp(48), dp(48))
        )

        row.addView(
            ImageView(context).apply {
                setImageResource(destination.iconRes)
                imageTintList = ColorStateList.valueOf(resolveColor(AppCompatR.attr.colorPrimary))
            },
            LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                marginStart = dp(2)
            }
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
                text = destination.summary
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

        row.addView(
            textColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            }
        )

        row.addView(
            createMoveButton(
                iconRes = CoreR.drawable.outline_arrow_upward_alt_24,
                enabled = isSelected && selectedIndex > 0,
                contentDescription = getString(R.string.setting_frequent_move_up, destination.title),
            ) {
                move(destination.key, -1)
            }
        )
        row.addView(
            createMoveButton(
                iconRes = CoreR.drawable.outline_arrow_downward_alt_24,
                enabled = isSelected && selectedIndex in 0 until selectedKeys.lastIndex,
                contentDescription = getString(R.string.setting_frequent_move_down, destination.title),
            ) {
                move(destination.key, 1)
            }
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

    private fun createMoveButton(
        iconRes: Int,
        enabled: Boolean,
        contentDescription: String,
        onClick: () -> Unit,
    ): View =
        ImageButton(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(
                resolveColor(
                    if (enabled) {
                        MaterialR.attr.colorOnSurfaceVariant
                    } else {
                        MaterialR.attr.colorOutline
                    }
                )
            )
            background = selectableItemBackground(requireContext())
            isEnabled = enabled
            isFocusable = true
            this.contentDescription = contentDescription
            setOnClickListener { onClick() }
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

    private fun move(key: String, direction: Int) {
        val currentIndex = selectedKeys.indexOf(key)
        val targetIndex = currentIndex + direction
        if (currentIndex !in selectedKeys.indices || targetIndex !in selectedKeys.indices) return
        selectedKeys = selectedKeys.toMutableList().apply {
            val item = removeAt(currentIndex)
            add(targetIndex, item)
        }
        saveSelection()
        renderRows()
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
        appPreference.setting_home_frequent_keys =
            selectedKeys.filter { it in allowedKeys }.distinct()
    }

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
