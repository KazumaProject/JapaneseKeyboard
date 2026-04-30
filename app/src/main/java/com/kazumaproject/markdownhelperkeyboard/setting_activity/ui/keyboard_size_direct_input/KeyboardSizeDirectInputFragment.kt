package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyboardSizeDirectInputBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KeyboardSizeDirectInputFragment : Fragment() {

    @Inject
    lateinit var appPreference: AppPreference

    private var _binding: FragmentKeyboardSizeDirectInputBinding? = null
    private val binding get() = _binding!!

    private val preferenceAccessor: KeyboardSizePreferenceAccessor by lazy {
        KeyboardSizePreferenceAccessor(appPreference)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentKeyboardSizeDirectInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenu()
        setupSelectionGroups()
        setupButtons()
        loadSelectedValues()
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupMenu() {
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = Unit

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        parentFragmentManager.popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupSelectionGroups() {
        val initialOrientation = arguments?.getString(InitialOrientationArgument)
        val initialOrientationButtonId = if (initialOrientation == InitialOrientationLandscape) {
            binding.orientationLandscapeButton.id
        } else {
            binding.orientationPortraitButton.id
        }
        binding.orientationToggleGroup.check(initialOrientationButtonId)
        binding.keyboardTypeToggleGroup.check(binding.keyboardTypeTenKeyButton.id)

        binding.orientationToggleGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) loadSelectedValues()
        }
        binding.keyboardTypeToggleGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) loadSelectedValues()
        }
    }

    private fun setupButtons() {
        binding.saveButton.setOnClickListener {
            saveSelectedValues()
        }
        binding.resetButton.setOnClickListener {
            preferenceAccessor.reset(selectedOrientation(), selectedKeyboardType())
            loadSelectedValues()
            Toast.makeText(
                requireContext(),
                R.string.keyboard_size_direct_input_reset_to_default,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun loadSelectedValues() {
        clearErrors()
        val values = preferenceAccessor.load(selectedOrientation(), selectedKeyboardType())
        binding.heightEditText.setText(values.heightDp.toString())
        binding.widthEditText.setText(values.widthPercent.toString())
        binding.bottomMarginEditText.setText(values.bottomMarginDp.toString())
        binding.marginStartEditText.setText(values.marginStartDp.toString())
        binding.marginEndEditText.setText(values.marginEndDp.toString())
    }

    private fun saveSelectedValues() {
        val values = readInputValues() ?: run {
            Toast.makeText(
                requireContext(),
                R.string.keyboard_size_direct_input_invalid_value,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        preferenceAccessor.save(selectedOrientation(), selectedKeyboardType(), values)
        loadSelectedValues()
        Toast.makeText(requireContext(), R.string.saved, Toast.LENGTH_SHORT).show()
    }

    private fun readInputValues(): KeyboardSizeValues? {
        clearErrors()

        val height = readRequiredInt(binding.heightInputLayout, binding.heightEditText)
        val width = readRequiredInt(binding.widthInputLayout, binding.widthEditText)
        val bottomMargin =
            readRequiredInt(binding.bottomMarginInputLayout, binding.bottomMarginEditText)
        val marginStart =
            readRequiredInt(binding.marginStartInputLayout, binding.marginStartEditText)
        val marginEnd = readRequiredInt(binding.marginEndInputLayout, binding.marginEndEditText)

        if (height == null || width == null || bottomMargin == null ||
            marginStart == null || marginEnd == null
        ) {
            return null
        }

        return KeyboardSizeValues(
            heightDp = height,
            widthPercent = width,
            bottomMarginDp = bottomMargin,
            marginStartDp = marginStart,
            marginEndDp = marginEnd,
        )
    }

    private fun readRequiredInt(
        inputLayout: TextInputLayout,
        editText: TextInputEditText,
    ): Int? {
        val value = editText.text?.toString()?.trim()?.toIntOrNull()
        if (value == null) {
            inputLayout.error = getString(R.string.keyboard_size_direct_input_invalid_value)
        }
        return value
    }

    private fun clearErrors() {
        binding.heightInputLayout.error = null
        binding.widthInputLayout.error = null
        binding.bottomMarginInputLayout.error = null
        binding.marginStartInputLayout.error = null
        binding.marginEndInputLayout.error = null
    }

    private fun selectedOrientation(): KeyboardSizeOrientation {
        return when (binding.orientationToggleGroup.checkedButtonId) {
            binding.orientationLandscapeButton.id -> KeyboardSizeOrientation.Landscape
            else -> KeyboardSizeOrientation.Portrait
        }
    }

    private fun selectedKeyboardType(): KeyboardSizeKeyboardType {
        return when (binding.keyboardTypeToggleGroup.checkedButtonId) {
            binding.keyboardTypeQwertyButton.id -> KeyboardSizeKeyboardType.Qwerty
            else -> KeyboardSizeKeyboardType.TenKey
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val InitialOrientationArgument = "initialOrientation"
        private const val InitialOrientationLandscape = "landscape"
    }
}
