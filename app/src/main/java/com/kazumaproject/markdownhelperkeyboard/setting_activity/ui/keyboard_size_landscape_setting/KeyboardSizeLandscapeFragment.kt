package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_landscape_setting

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.widget.ViewPager2
import androidx.window.layout.WindowMetricsCalculator
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyboardsizeLandscapeBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.adapter.KeyboardViewPagerAdapter
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class KeyboardSizeLandscapeFragment : Fragment() {

    @Inject
    lateinit var appPreference: AppPreference

    private var _binding: FragmentKeyboardsizeLandscapeBinding? = null
    private val binding get() = _binding!!

    private var isRightAligned = true
    private var areControlsVisible = true

    private val minHeightDp = 100
    private val maxHeightDp = 420
    private val minWidthPercent = 32
    private val maxWidthPercent = 100

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeyboardsizeLandscapeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenu()
        isRightAligned = appPreference.keyboard_position_landscape ?: true

        setupViewPager()
        applyCurrentPageDimensions()
        setupKeyboardPositionButton()
        setupResetButton()
        updateKeyboardAlignment()
        setupResizeHandles()
        setupMoveHandle()

        updateControlsVisibility()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    override fun onPause() {
        super.onPause()
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun setupViewPager() {
        val adapter = KeyboardViewPagerAdapter()
        binding.keyboardViewPager.adapter = adapter
        binding.keyboardViewPager.isUserInputEnabled = false

        binding.keyboardViewPager.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                applyCurrentPageDimensions()
                updateTooltipUI(position)

                isRightAligned = if (position == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
                    appPreference.keyboard_position_landscape ?: true
                } else {
                    appPreference.qwerty_keyboard_position_landscape ?: true
                }
                updateKeyboardAlignment()
            }
        })

        binding.tenkeyTooltipButton.setOnClickListener {
            binding.keyboardViewPager.setCurrentItem(
                KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION,
                true
            )
        }
        binding.qwertyTooltipButton.setOnClickListener {
            binding.keyboardViewPager.setCurrentItem(
                KeyboardViewPagerAdapter.QWERTY_PAGE_POSITION,
                true
            )
        }
        updateTooltipUI(binding.keyboardViewPager.currentItem)
    }

    private fun applyCurrentPageDimensions() {
        val position = binding.keyboardViewPager.currentItem
        val heightPref: Int
        val widthPref: Int
        val marginBottomPref: Int
        val positionPref: Boolean

        if (position == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
            heightPref = appPreference.keyboard_height_landscape ?: 220
            widthPref = appPreference.keyboard_width_landscape ?: 100
            marginBottomPref = appPreference.keyboard_vertical_margin_bottom_landscape ?: 0
            positionPref = appPreference.keyboard_position_landscape ?: true
        } else { // QWERTY
            heightPref = appPreference.qwerty_keyboard_height_landscape ?: 220
            widthPref = appPreference.qwerty_keyboard_width_landscape ?: 100
            marginBottomPref = appPreference.qwerty_keyboard_vertical_margin_bottom_landscape ?: 0
            positionPref = appPreference.qwerty_keyboard_position_landscape ?: true
        }
        isRightAligned = positionPref

        val density = resources.displayMetrics.density
        val heightInPx = (heightPref * density).toInt()
        val marginBottomInPx = (marginBottomPref * density).toInt()

        val screenWidth = WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(requireActivity()).bounds.width()
        val widthInPx = if (widthPref >= 98) {
            ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            (screenWidth * (widthPref / 100f)).toInt()
        }

        val layoutParams = binding.keyboardContainer.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.height = heightInPx
        layoutParams.width = widthInPx
        layoutParams.bottomMargin = marginBottomInPx
        binding.keyboardContainer.layoutParams = layoutParams
    }

    private fun setupMenu() {
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val menuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_keyboard_settings, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        parentFragmentManager.popBackStack()
                        true
                    }

                    R.id.action_toggle_visibility -> {
                        areControlsVisible = !areControlsVisible
                        updateControlsVisibility()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMoveHandle() {
        var initialY = 0f
        var initialBottomMargin = 0
        val density = resources.displayMetrics.density

        binding.handleMove.setOnTouchListener { _, event ->
            val layoutParams =
                binding.keyboardContainer.layoutParams as ConstraintLayout.LayoutParams
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    initialBottomMargin = layoutParams.bottomMargin
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - initialY
                    val newBottomMargin = initialBottomMargin - deltaY
                    layoutParams.bottomMargin = newBottomMargin.toInt().coerceAtLeast(0)
                    binding.keyboardContainer.requestLayout()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val finalMarginDp = (layoutParams.bottomMargin / density).roundToInt()
                    val currentPage = binding.keyboardViewPager.currentItem

                    if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
                        appPreference.keyboard_vertical_margin_bottom_landscape = finalMarginDp
                    } else { // QWERTY
                        appPreference.qwerty_keyboard_vertical_margin_bottom_landscape =
                            finalMarginDp
                    }
                    Timber.d("Saved landscape vertical margin for page $currentPage: $finalMarginDp dp")
                    true
                }

                else -> false
            }
        }
    }

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

        fun saveHeightPreference() {
            val finalHeightDp = (binding.keyboardContainer.height / density).roundToInt()
            val currentPage = binding.keyboardViewPager.currentItem
            if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
                appPreference.keyboard_height_landscape = finalHeightDp
            } else { // QWERTY
                appPreference.qwerty_keyboard_height_landscape = finalHeightDp
            }
            Timber.d("Saved landscape Height for page $currentPage: $finalHeightDp dp")
        }

        fun saveWidthPreference() {
            val parentView = binding.keyboardSettingConstraint
            val availableWidth =
                (parentView.width - parentView.paddingLeft - parentView.paddingRight).toFloat()
            if (availableWidth <= 0) return
            val currentWidth = binding.keyboardContainer.width.toFloat()
            val finalWidthPercent = ((currentWidth / availableWidth) * 100).roundToInt()
            val finalWidthValue = if (finalWidthPercent >= 98) 100 else finalWidthPercent
            val currentPage = binding.keyboardViewPager.currentItem
            if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
                appPreference.keyboard_width_landscape = finalWidthValue
            } else { // QWERTY
                appPreference.qwerty_keyboard_width_landscape = finalWidthValue
            }
            Timber.d("Saved landscape Width for page $currentPage: $finalWidthValue %")
        }

        binding.handleTop.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY; initialHeight = binding.keyboardContainer.height
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - initialY
                    val newHeight = (initialHeight - deltaY).coerceIn(minHeightPx, maxHeightPx)
                    binding.keyboardContainer.layoutParams.height = newHeight.toInt()
                    binding.keyboardContainer.requestLayout()
                }

                MotionEvent.ACTION_UP -> saveHeightPreference()
            }
            true
        }

        binding.handleBottom.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY; initialHeight = binding.keyboardContainer.height
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - initialY
                    val newHeight = (initialHeight + deltaY).coerceIn(minHeightPx, maxHeightPx)
                    binding.keyboardContainer.layoutParams.height = newHeight.toInt()
                    binding.keyboardContainer.requestLayout()
                }

                MotionEvent.ACTION_UP -> saveHeightPreference()
            }
            true
        }

        binding.handleLeft.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = event.rawX; initialWidth = binding.keyboardContainer.width
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialX
                    val newWidth =
                        (initialWidth - deltaX).coerceIn(minWidthPx, screenWidth.toFloat())
                    binding.keyboardContainer.layoutParams.width = newWidth.toInt()
                    binding.keyboardContainer.requestLayout()
                }

                MotionEvent.ACTION_UP -> saveWidthPreference()
            }
            true
        }

        binding.handleRight.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = event.rawX; initialWidth = binding.keyboardContainer.width
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialX
                    val newWidth =
                        (initialWidth + deltaX).coerceIn(minWidthPx, screenWidth.toFloat())
                    binding.keyboardContainer.layoutParams.width = newWidth.toInt()
                    binding.keyboardContainer.requestLayout()
                }

                MotionEvent.ACTION_UP -> saveWidthPreference()
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

    private fun setupResetButton() {
        binding.resetLayoutButton.setOnClickListener {
            val currentPage = binding.keyboardViewPager.currentItem
            if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
                appPreference.keyboard_height_landscape = 220
                appPreference.keyboard_width_landscape = 100
                appPreference.keyboard_vertical_margin_bottom_landscape = 0
                appPreference.keyboard_position_landscape = true
            } else {
                appPreference.qwerty_keyboard_height_landscape = 220
                appPreference.qwerty_keyboard_width_landscape = 100
                appPreference.qwerty_keyboard_vertical_margin_bottom_landscape = 0
                appPreference.qwerty_keyboard_position_landscape = true
            }
            applyCurrentPageDimensions()
            updateKeyboardAlignment()
        }
    }

    private fun updateKeyboardAlignment() {
        val currentPage = binding.keyboardViewPager.currentItem
        if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
            appPreference.keyboard_position_landscape = isRightAligned
        } else {
            appPreference.qwerty_keyboard_position_landscape = isRightAligned
        }

        val constraintLayout = binding.keyboardSettingConstraint
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)

        if (isRightAligned) {
            constraintSet.connect(
                binding.keyboardContainer.id,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            constraintSet.clear(binding.keyboardContainer.id, ConstraintSet.START)
            binding.keyboardPositionButton.setBackgroundColor(
                ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.blue)
            )
            binding.keyboardPositionButton.text =
                getString(R.string.key_size_position_button_text_right)
        } else {
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
            binding.keyboardPositionButton.text =
                getString(R.string.key_size_position_button_text_left)
        }
        constraintSet.applyTo(constraintLayout)
    }

    private fun updateTooltipUI(selectedPosition: Int) {
        val selectedColor =
            ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.blue)
        val defaultColor = ContextCompat.getColor(
            requireContext(),
            com.kazumaproject.core.R.color.qwety_key_bg_color
        )

        binding.tenkeyTooltipButton.setBackgroundColor(
            if (selectedPosition == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) selectedColor else defaultColor
        )
        binding.qwertyTooltipButton.setBackgroundColor(
            if (selectedPosition == KeyboardViewPagerAdapter.QWERTY_PAGE_POSITION) selectedColor else defaultColor
        )
    }

    private fun updateControlsVisibility() {
        val visibility = if (areControlsVisible) View.VISIBLE else View.GONE
        binding.keyboardPositionTitle.visibility = visibility
        binding.keyboardPositionButton.visibility = visibility
        binding.resetLayoutButton.visibility = visibility
        binding.tenkeyTooltipButton.visibility = visibility
        binding.qwertyTooltipButton.visibility = visibility
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
        _binding = null
    }
}
