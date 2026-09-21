package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.R as AppCompatR
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentCandidateHeightDefaultsBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CandidateHeightDefaultsFragment : Fragment() {

    @Inject
    lateinit var appPreference: AppPreference

    private var _binding: FragmentCandidateHeightDefaultsBinding? = null
    private val binding get() = _binding!!

    private var previousNavigationContainerVisibility: Int? = null

    private val isLandscape: Boolean
        get() = requireArguments().getBoolean(ARG_IS_LANDSCAPE)

    private val minHeightDp = 30
    private val maxHeightDp = 300

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCandidateHeightDefaultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideNavigationContainer()
        appPreference.migrateCandidateHeightPerColumnPreferencesIfNeeded()
        binding.toolbar.title = getString(
            if (isLandscape) {
                R.string.candidate_default_height_screen_title_landscape
            } else {
                R.string.candidate_default_height_screen_title_portrait
            }
        )
        binding.toolbar.setNavigationIcon(AppCompatR.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.saveDefaultsButton.setOnClickListener {
            saveDefaultHeightsFromInputs()
        }
        binding.useCurrentDefaultsButton.setOnClickListener {
            appPreference.copyCandidateHeightSettingsToUserDefaults(isLandscape = isLandscape)
            syncDefaultHeightControls()
        }
        binding.restoreFactoryDefaultsButton.setOnClickListener {
            appPreference.resetCandidateHeightDefaultsToFactoryDefaults(isLandscape = isLandscape)
            syncDefaultHeightControls()
        }
        listOf(
            binding.defaultHeightOneEditText,
            binding.defaultHeightTwoEditText,
            binding.defaultHeightThreeEditText,
            binding.defaultEmptyHeightEditText
        ).forEach { editText ->
            editText.setOnEditorActionListener { _, _, _ ->
                saveDefaultHeightsFromInputs()
                false
            }
        }
        syncDefaultHeightControls()
    }

    override fun onDestroyView() {
        restoreNavigationContainer()
        super.onDestroyView()
        _binding = null
    }

    private fun hideNavigationContainer() {
        val navigationContainer = activity?.findViewById<View>(R.id.nav_view_container) ?: return
        if (previousNavigationContainerVisibility == null) {
            previousNavigationContainerVisibility = navigationContainer.visibility
        }
        navigationContainer.visibility = View.GONE
    }

    private fun restoreNavigationContainer() {
        val visibility = previousNavigationContainerVisibility ?: return
        activity?.findViewById<View>(R.id.nav_view_container)?.visibility = visibility
        previousNavigationContainerVisibility = null
    }

    private fun saveDefaultHeightsFromInputs(): Boolean {
        val one = readDefaultHeightInput(
            binding.defaultHeightOneInputLayout,
            binding.defaultHeightOneEditText
        ) ?: return false
        val two = readDefaultHeightInput(
            binding.defaultHeightTwoInputLayout,
            binding.defaultHeightTwoEditText
        ) ?: return false
        val three = readDefaultHeightInput(
            binding.defaultHeightThreeInputLayout,
            binding.defaultHeightThreeEditText
        ) ?: return false
        val empty = readDefaultHeightInput(
            binding.defaultEmptyHeightInputLayout,
            binding.defaultEmptyHeightEditText
        ) ?: return false

        appPreference.setCandidateDefaultVisibleHeightDp(
            isLandscape = isLandscape,
            column = "1",
            heightDp = one
        )
        appPreference.setCandidateDefaultVisibleHeightDp(
            isLandscape = isLandscape,
            column = "2",
            heightDp = two
        )
        appPreference.setCandidateDefaultVisibleHeightDp(
            isLandscape = isLandscape,
            column = "3",
            heightDp = three
        )
        appPreference.setCandidateDefaultEmptyHeightDp(
            isLandscape = isLandscape,
            heightDp = empty
        )
        syncDefaultHeightControls()
        return true
    }

    private fun readDefaultHeightInput(
        inputLayout: TextInputLayout,
        editText: TextInputEditText
    ): Int? {
        val value = editText.text?.toString()?.trim()?.toIntOrNull()
        if (value == null) {
            inputLayout.error = getString(R.string.candidate_height_invalid_value)
            return null
        }
        inputLayout.error = null
        return value.coerceIn(minHeightDp, maxHeightDp)
    }

    private fun syncDefaultHeightControls() {
        setDefaultHeightText(
            binding.defaultHeightOneInputLayout,
            binding.defaultHeightOneEditText,
            appPreference.getCandidateDefaultVisibleHeightDp(isLandscape, "1")
        )
        setDefaultHeightText(
            binding.defaultHeightTwoInputLayout,
            binding.defaultHeightTwoEditText,
            appPreference.getCandidateDefaultVisibleHeightDp(isLandscape, "2")
        )
        setDefaultHeightText(
            binding.defaultHeightThreeInputLayout,
            binding.defaultHeightThreeEditText,
            appPreference.getCandidateDefaultVisibleHeightDp(isLandscape, "3")
        )
        setDefaultHeightText(
            binding.defaultEmptyHeightInputLayout,
            binding.defaultEmptyHeightEditText,
            appPreference.getCandidateDefaultEmptyHeightDp(isLandscape)
        )
    }

    private fun setDefaultHeightText(
        inputLayout: TextInputLayout,
        editText: TextInputEditText,
        heightDp: Int
    ) {
        val text = heightDp.coerceIn(minHeightDp, maxHeightDp).toString()
        if (editText.text?.toString() != text) {
            editText.setText(text)
            editText.setSelection(text.length)
        }
        inputLayout.error = null
    }

    companion object {
        const val ARG_IS_LANDSCAPE = "isLandscape"
    }
}
