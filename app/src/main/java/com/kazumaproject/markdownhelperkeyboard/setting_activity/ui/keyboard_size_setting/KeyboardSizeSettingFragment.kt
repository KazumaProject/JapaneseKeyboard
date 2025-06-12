package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class KeyboardSettingFragment : Fragment() {

    @Inject
    lateinit var appPreference: AppPreference

    private var _binding: FragmentKeyboardSettingBinding? = null
    private val binding get() = _binding!!

    private var isRightAligned = true

    // Define min/max dimensions for the keyboard
    private val minHeightDp = 170
    private val maxHeightDp = 280
    private val minWidthPercent = 74
    private val maxWidthPercent = 100

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeyboardSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenu()
        isRightAligned = appPreference.keyboard_position ?: true

        // Set initial state and setup listeners
        setInitialKeyboardView()
        setupKeyboardPositionButton()
        setupResetButton() // Call the new setup function here
        updateKeyboardAlignment()
        setupResizeHandles()
    }

    /**
     * Sets up the modern, lifecycle-aware MenuProvider to handle the "Up" button.
     */
    private fun setupMenu() {
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val menuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == android.R.id.home) {
                    parentFragmentManager.popBackStack()
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    /**
     * Applies the saved dimensions from preferences to the keyboard container on startup.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setInitialKeyboardView() {
        val heightFromPreference = appPreference.keyboard_height ?: maxHeightDp
        val widthFromPreference = appPreference.keyboard_width ?: maxWidthPercent
        val density = resources.displayMetrics.density
        val heightInPx = (heightFromPreference * density).toInt()

        val screenWidth = WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(requireActivity()).bounds.width()

        binding.keyboardContainer.layoutParams = binding.keyboardContainer.layoutParams.apply {
            height = heightInPx
            width = if (widthFromPreference == maxWidthPercent) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                (screenWidth * (widthFromPreference / 100f)).toInt()
            }
        }
        binding.keyboardContainer.requestLayout()


        binding.keyboardView.setOnTouchListener { _, _ ->
            true
        }
    }

    /**
     * Initializes touch listeners for all four resize handles.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupResizeHandles() {
        var initialY = 0f
        var initialHeight = 0
        var initialX = 0f
        var initialWidth = 0

        val density = resources.displayMetrics.density
        val screenWidth = WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(requireActivity()).bounds.width()
        val minHeightPx = minHeightDp * density
        val maxHeightPx = maxHeightDp * density
        val minWidthPx = screenWidth * (minWidthPercent / 100f)

        // Common function to save preferences on ACTION_UP
        fun savePreferences() {
            val finalHeightPx = (binding.keyboardContainer.height / density).roundToInt()
            val finalWidthPx =
                ((binding.keyboardContainer.width.toFloat() / screenWidth) * 100).roundToInt()

            appPreference.keyboard_height = finalHeightPx
            appPreference.keyboard_width = if (finalWidthPx >= 90) {
                100
            } else {
                finalWidthPx
            }

            Timber.d("savePreferences: $finalWidthPx $finalHeightPx")
        }

        // Top Handle
        binding.handleTop.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    initialHeight = binding.keyboardContainer.height
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - initialY
                    val newHeight = (initialHeight - deltaY).coerceIn(minHeightPx, maxHeightPx)
                    binding.keyboardContainer.layoutParams.height = newHeight.toInt()
                    binding.keyboardContainer.requestLayout()
                }

                MotionEvent.ACTION_UP -> savePreferences()
            }
            true
        }

        // Bottom Handle
        binding.handleBottom.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    initialHeight = binding.keyboardContainer.height
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - initialY
                    val newHeight = (initialHeight + deltaY).coerceIn(minHeightPx, maxHeightPx)
                    binding.keyboardContainer.layoutParams.height = newHeight.toInt()
                    binding.keyboardContainer.requestLayout()
                }

                MotionEvent.ACTION_UP -> savePreferences()
            }
            true
        }

        // Left Handle
        binding.handleLeft.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = event.rawX
                    initialWidth = binding.keyboardContainer.width
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialX
                    val newWidth =
                        (initialWidth - deltaX).coerceIn(minWidthPx, screenWidth.toFloat())
                    binding.keyboardContainer.layoutParams.width = newWidth.toInt()
                    binding.keyboardContainer.requestLayout()
                }

                MotionEvent.ACTION_UP -> savePreferences()
            }
            true
        }

        // Right Handle
        binding.handleRight.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = event.rawX
                    initialWidth = binding.keyboardContainer.width
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialX
                    val newWidth =
                        (initialWidth + deltaX).coerceIn(minWidthPx, screenWidth.toFloat())
                    binding.keyboardContainer.layoutParams.width = newWidth.toInt()
                    binding.keyboardContainer.requestLayout()
                }

                MotionEvent.ACTION_UP -> savePreferences()
            }
            true
        }
    }

    private fun setupKeyboardPositionButton() {
        binding.keyboardPositionButton.setOnClickListener {
            isRightAligned = !isRightAligned
            updateKeyboardAlignment()
        }
    }

    /**
     * Sets up the listener for the new reset button.
     */
    private fun setupResetButton() {
        binding.resetLayoutButton.setOnClickListener {
            // Set preferences to default values
            appPreference.keyboard_height = 220
            appPreference.keyboard_width = maxWidthPercent
            appPreference.keyboard_position = true // Default to right-aligned

            // Update local state and UI
            isRightAligned = true
            setInitialKeyboardView()
            updateKeyboardAlignment()
        }
    }

    private fun updateKeyboardAlignment() {
        appPreference.keyboard_position = isRightAligned

        val constraintLayout = binding.keyboardSettingConstraint
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)

        if (isRightAligned) {
            // Align container to the right
            constraintSet.connect(
                binding.keyboardContainer.id,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            constraintSet.clear(binding.keyboardContainer.id, ConstraintSet.START)
            binding.keyboardPositionButton.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    com.kazumaproject.core.R.color.blue
                )
            )
            binding.keyboardPositionButton.text = "右寄せ"
        } else {
            // Align container to the left
            constraintSet.connect(
                binding.keyboardContainer.id,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            constraintSet.clear(binding.keyboardContainer.id, ConstraintSet.END)
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
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
        _binding = null
    }
}
