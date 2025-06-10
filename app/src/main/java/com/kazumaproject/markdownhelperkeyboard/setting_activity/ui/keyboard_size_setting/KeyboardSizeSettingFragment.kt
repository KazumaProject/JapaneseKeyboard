package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.window.layout.WindowMetricsCalculator
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyboardSettingBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KeyboardSettingFragment : Fragment() {

    @Inject
    lateinit var appPreference: AppPreference

    private var _binding: FragmentKeyboardSettingBinding? = null
    private val binding get() = _binding!!
    private var isRightAligned = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeyboardSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // The modern, lifecycle-aware way to handle the "Up" button
        setupMenu()

        // Load initial state
        isRightAligned = appPreference.keyboard_position ?: true

        // Setup UI components
        setupKeyboardHeightSeekBar()
        setupKeyboardWidthSeekBar()
        setupKeyboardPositionButton()

        // Set initial view state from preferences
        setInitialKeyboardView()

        updateKeyboardAlignment()
    }

    private fun setupMenu() {
        // Show the "Up" button
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val menuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // No new menu items to add
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the "Up" button click
                if (menuItem.itemId == android.R.id.home) {
                    parentFragmentManager.popBackStack()
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setInitialKeyboardView() {
        val heightFromPreference = appPreference.keyboard_height ?: 280
        val widthFromPreference = appPreference.keyboard_width ?: 100
        val density = resources.displayMetrics.density
        val heightInPx = (heightFromPreference * density).toInt()

        val screenWidth = WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(requireActivity()).bounds.width()

        binding.keyboardView.layoutParams = binding.keyboardView.layoutParams.apply {
            height = heightInPx
            width = if (widthFromPreference == 100) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                (screenWidth * (widthFromPreference / 100f)).toInt()
            }
        }
    }

    private fun setupKeyboardHeightSeekBar() {
        binding.keyboardHeightSeekbar.apply {
            max = 100
            progress = (appPreference.keyboard_height ?: 280) - 180

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    val actualProgress = progress + 180
                    appPreference.keyboard_height = actualProgress

                    val density = resources.displayMetrics.density
                    val heightInPx = (actualProgress * density).toInt()

                    binding.keyboardView.layoutParams = binding.keyboardView.layoutParams.apply {
                        height = heightInPx
                    }

                    val padding = calculateSideKeyPadding(actualProgress, density)
                    binding.keyboardView.setPaddingToSideKeySymbol(padding.toInt())
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

    private fun calculateSideKeyPadding(progress: Int, density: Float): Float {
        return when {
            progress in 210..280 -> {
                val startPadding = 12 * density
                val endPadding = 21 * density
                val normalizedProgress = (progress - 210) / 70f
                startPadding + (endPadding - startPadding) * normalizedProgress
            }

            progress < 210 -> 12 * density
            else -> 21 * density
        }
    }

    private fun setupKeyboardWidthSeekBar() {
        binding.keyboardWidthSeekbar.apply {
            max = 30
            progress = (appPreference.keyboard_width ?: 100) - 70

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    val actualProgress = progress + 70
                    appPreference.keyboard_width = actualProgress

                    val screenWidth = WindowMetricsCalculator.getOrCreate()
                        .computeCurrentWindowMetrics(requireActivity()).bounds.width()

                    binding.keyboardView.layoutParams = binding.keyboardView.layoutParams.apply {
                        width = if (actualProgress == 100) {
                            ViewGroup.LayoutParams.MATCH_PARENT
                        } else {
                            (screenWidth * (actualProgress / 100f)).toInt()
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

    private fun setupKeyboardPositionButton() {
        binding.keyboardPositionButton.setOnClickListener {
            isRightAligned = !isRightAligned
            updateKeyboardAlignment()
        }
    }

    private fun updateKeyboardAlignment() {
        appPreference.keyboard_position = isRightAligned

        val constraintLayout = binding.keyboardSettingConstraint
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)

        if (isRightAligned) {
            constraintSet.connect(
                binding.keyboardView.id,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            constraintSet.clear(binding.keyboardView.id, ConstraintSet.START)

            binding.keyboardPositionButton.setBackgroundColor(
                ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.blue)
            )
            binding.keyboardPositionButton.text = "右寄せ"
        } else {
            constraintSet.connect(
                binding.keyboardView.id,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            constraintSet.clear(binding.keyboardView.id, ConstraintSet.END)

            binding.keyboardPositionButton.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    com.kazumaproject.core.R.color.qwety_key_bg_color
                )
            )
            binding.keyboardPositionButton.text = "左寄せ"
        }
        constraintSet.applyTo(constraintLayout)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Hide the "Up" button when the fragment is destroyed
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
        _binding = null
    }
}
