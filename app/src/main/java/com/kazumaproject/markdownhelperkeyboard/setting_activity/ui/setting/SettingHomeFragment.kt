package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.AttrRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.R as MaterialR
import androidx.appcompat.R as AppCompatR
import com.kazumaproject.core.R as CoreR
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSettingHomeBinding
import com.kazumaproject.markdownhelperkeyboard.repository.RomajiMapRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SettingHomeFragment : Fragment() {

    private var _binding: FragmentSettingHomeBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var userDictionaryRepository: UserDictionaryRepository

    @Inject
    lateinit var romajiMapRepository: RomajiMapRepository

    private lateinit var settingCardEditorController: SettingCardEditorController

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingCardEditorController = SettingCardEditorController(requireContext(), appPreference)
        initializeRomajiDataIfNeeded()
        renderFrequentCards()
        renderCategoryRows()
        renderManagementRows()

        binding.settingHomeSearchButton.setOnClickListener {
            navigateSafely(R.id.settingSearchFragment)
        }
        binding.settingHomeFrequentEditButton.setOnClickListener {
            navigateSafely(R.id.frequentSettingsEditFragment)
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    requireActivity().finish()
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            renderFrequentCards()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            binding.settingHomeProgressBar.isVisible = true
            val enabled = withContext(Dispatchers.IO) {
                isKeyboardBoardEnabled()
            }
            binding.settingHomeProgressBar.isVisible = false
            if (enabled == false) {
                navigateSafely(R.id.enableKeyboardFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun renderFrequentCards() {
        val columnCount = if (resources.configuration.screenWidthDp >= 600) 3 else 2
        binding.settingHomeFrequentGrid.columnCount = columnCount
        binding.settingHomeFrequentGrid.removeAllViews()
        homeFrequentDestinations().forEach { destination ->
            binding.settingHomeFrequentGrid.addView(
                createFrequentCard(destination),
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
            )
        }
    }

    private fun renderCategoryRows() {
        binding.settingHomeCategoryList.removeAllViews()
        SettingDestinations.categories(requireContext()).forEach { destination ->
            binding.settingHomeCategoryList.addView(createCategoryRow(destination))
        }
    }

    private fun renderManagementRows() {
        binding.settingHomeManagementList.removeAllViews()
        SettingDestinations.management(requireContext()).forEach { destination ->
            binding.settingHomeManagementList.addView(createCategoryRow(destination))
        }
    }

    private fun createFrequentCard(destination: SettingDestination): View {
        val context = requireContext()
        val title = destination.title
        val summary = currentSummary(destination)
        val valueText = currentValueText(destination)
        val switchTarget = destination.destination as? SettingDestinationType.SwitchPreference
        val switchValue = switchTarget?.let { settingCardEditorController.readSwitchPreference(it) }
        val card = MaterialCardView(context).apply {
            radius = dp(8).toFloat()
            strokeWidth = dp(1)
            setStrokeColor(resolveColor(MaterialR.attr.colorOutline))
            setCardBackgroundColor(resolveColor(MaterialR.attr.colorSurfaceVariant))
            isClickable = true
            isFocusable = true
            foreground = selectableItemBackground(context)
            contentDescription = listOf(title, summary, valueText)
                .filter { !it.isNullOrBlank() }
                .joinToString(". ")
            valueText?.let { ViewCompat.setStateDescription(this, it) }
            setOnClickListener { handleFrequentCardClick(destination) }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            minimumHeight = dp(120)
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(
            ImageView(context).apply {
                setImageResource(destination.iconRes)
                imageTintList = ColorStateList.valueOf(resolveColor(AppCompatR.attr.colorPrimary))
            },
            LinearLayout.LayoutParams(dp(28), dp(28))
        )
        if (switchTarget != null && switchValue != null) {
            headerRow.addView(
                View(context),
                LinearLayout.LayoutParams(0, 1, 1f)
            )
            headerRow.addView(
                SwitchCompat(context).apply {
                    isChecked = switchValue
                    isClickable = false
                    isFocusable = false
                    contentDescription = title
                    ViewCompat.setStateDescription(this, statusLabel(switchValue))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            )
        } else if (!settingCardEditorController.isDirectEditable(destination)) {
            headerRow.addView(
                View(context),
                LinearLayout.LayoutParams(0, 1, 1f)
            )
            headerRow.addView(
                ImageView(context).apply {
                    setImageResource(CoreR.drawable.baseline_arrow_right_24)
                    imageTintList =
                        ColorStateList.valueOf(resolveColor(MaterialR.attr.colorOnSurfaceVariant))
                },
                LinearLayout.LayoutParams(dp(24), dp(24))
            )
        }
        content.addView(headerRow)
        content.addView(
            TextView(context).apply {
                text = title
                setTextColor(resolveColor(MaterialR.attr.colorOnSurface))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        )
        content.addView(
            TextView(context).apply {
                text = summary
                setTextColor(resolveColor(MaterialR.attr.colorOnSurfaceVariant))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 2
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
        )
        valueText?.let { currentValue ->
            content.addView(
                TextView(context).apply {
                    text = currentValue
                    setTextColor(resolveColor(AppCompatR.attr.colorPrimary))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    maxLines = 1
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
            )
        }

        card.addView(content)
        return card
    }

    private fun createCategoryRow(destination: SettingDestination): View {
        val context = requireContext()
        val title = destination.title
        val summary = currentSummary(destination)
        val card = MaterialCardView(context).apply {
            radius = dp(8).toFloat()
            strokeWidth = dp(1)
            setStrokeColor(resolveColor(MaterialR.attr.colorOutline))
            setCardBackgroundColor(resolveColor(MaterialR.attr.colorSurface))
            isClickable = true
            isFocusable = true
            foreground = selectableItemBackground(context)
            contentDescription = "$title. $summary"
            setOnClickListener { navigateTo(destination) }
        }

        val row = LinearLayout(context).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(12), dp(12), dp(12))
            minimumHeight = dp(76)
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
                text = title
                setTextColor(resolveColor(MaterialR.attr.colorOnSurface))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
            }
        )
        textColumn.addView(
            TextView(context).apply {
                text = summary
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

    private fun navigateTo(destination: SettingDestination) {
        val destinationId = SettingDestinations.destinationId(destination.destination) ?: return
        val args = SettingDestinations.highlightPreferenceKey(destination.destination)?.let { key ->
            bundleOf(CommonPreferenceFragment.ARG_HIGHLIGHT_PREFERENCE_KEY to key)
        }
        navigateSafely(destinationId, args)
    }

    private fun handleFrequentCardClick(destination: SettingDestination) {
        val handled = settingCardEditorController.handleClick(destination) {
            renderFrequentCards()
        }
        if (!handled) {
            navigateTo(destination)
        }
    }

    private fun homeFrequentDestinations(): List<SettingDestination> {
        val context = requireContext()
        val candidates = SettingDestinations.frequentCandidates(context).associateBy { it.key }
        val savedKeys = appPreference.setting_home_frequent_keys
            .filter { it in candidates }
            .distinct()
        val selected = if (appPreference.has_setting_home_frequent_keys) {
            savedKeys
        } else {
            SettingDestinations.defaultFrequent(context).map { it.key }
        }
        return selected.mapNotNull { candidates[it] }
    }

    private fun currentSummary(destination: SettingDestination): String {
        return when (destination.key) {
            "keyboard_screen_preference" -> getString(
                R.string.setting_home_keyboard_size_summary,
                appPreference.keyboard_height ?: 220,
                appPreference.keyboard_width ?: 100,
            )

            "keyboard_screen_landscape_preference" -> getString(
                R.string.setting_home_keyboard_size_summary,
                appPreference.keyboard_height_landscape ?: 220,
                appPreference.keyboard_width_landscape ?: 100,
            )

            "candidate_view_height_setting_fragment_preference" -> getString(
                R.string.setting_home_dp_summary,
                appPreference.candidate_view_height_dp ?: 110,
            )

            "candidate_view_height_landscape_setting_fragment_preference" -> getString(
                R.string.setting_home_dp_summary,
                appPreference.candidate_view_height_dp_landscape ?: 110,
            )

            "setting_route_keyboard_theme" -> getString(
                R.string.setting_home_current_value,
                themeModeLabel(appPreference.theme_mode),
            )

            "setting_route_input_method" -> getString(
                R.string.setting_home_keyboard_order_summary,
                appPreference.keyboard_order.size,
            )

            "setting_route_dictionary" -> getString(
                R.string.setting_home_dictionary_status_summary,
                statusLabel(appPreference.user_dictionary_preference == true),
                statusLabel(appPreference.learn_dictionary_preference == true),
            )

            "setting_route_zenz_preferences" -> getString(
                R.string.setting_home_status_summary,
                statusLabel(appPreference.enable_zenz_preference),
            )

            "setting_route_ai_conversion" -> getString(
                R.string.setting_home_ai_status_summary,
                statusLabel(appPreference.enable_zenz_preference),
                statusLabel(appPreference.enable_gemma_translation_preference),
            )

            "setting_route_clipboard_shortcut" -> getString(
                R.string.setting_home_clipboard_shortcut_status_summary,
                statusLabel(appPreference.clipboard_history_enable == true),
                statusLabel(appPreference.shortcut_toolbar_visibility_preference),
            )

            "setting_route_operation_feedback" -> getString(
                R.string.setting_home_operation_status_summary,
                statusLabel(appPreference.vibration_preference == true),
                statusLabel(appPreference.key_sound_preference == true),
            )

            else -> destination.summary
        }
    }

    private fun currentValueText(destination: SettingDestination): String? {
        return settingCardEditorController.currentValueLabel(destination)
            ?.let { getString(R.string.setting_home_current_value, it) }
    }

    private fun themeModeLabel(mode: String): String {
        return when (mode) {
            "light" -> getString(R.string.theme_light)
            "dark" -> getString(R.string.theme_dark)
            "custom" -> getString(R.string.theme_custom)
            else -> getString(R.string.theme_default)
        }
    }

    private fun statusLabel(enabled: Boolean): String =
        if (enabled) {
            getString(R.string.setting_status_enabled)
        } else {
            getString(R.string.setting_status_disabled)
        }

    private fun initializeRomajiDataIfNeeded() {
        val romajiMapUpdated = appPreference.romaji_map_data_version
        lifecycleScope.launch(Dispatchers.IO) {
            if (romajiMapUpdated == 0) {
                romajiMapRepository.updateDefaultMap()

                userDictionaryRepository.apply {
                    if (searchByReadingExactMatchSuspend("びゃんびゃんめん").isEmpty()) {
                        insert(
                            UserWord(
                                reading = "びゃんびゃんめん",
                                word = "\uD883\uDEDE\uD883\uDEDE麺",
                                posIndex = 0,
                                posScore = 4000
                            )
                        )
                    }
                    if (searchByReadingExactMatchSuspend("びゃん").isEmpty()) {
                        insert(
                            UserWord(
                                reading = "びゃん",
                                word = "\uD883\uDEDE",
                                posIndex = 0,
                                posScore = 3000
                            )
                        )
                    }
                }

                appPreference.romaji_map_data_version = 1
            }
        }
    }

    private fun isKeyboardBoardEnabled(): Boolean? {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        return imm?.enabledInputMethodList?.any { it.packageName == requireContext().packageName }
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
