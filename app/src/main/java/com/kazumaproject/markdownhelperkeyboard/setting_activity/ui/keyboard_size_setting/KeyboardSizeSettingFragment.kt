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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.widget.ViewPager2
import androidx.window.layout.WindowMetricsCalculator
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyboardSettingBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.adapter.KeyboardViewPagerAdapter
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
    private var isFloatingMode = false
    private var areControlsVisible = true

    private val minHeightDp = 100
    private val maxHeightDp = 420
    private val minWidthPercent = 32
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
        isFloatingMode = appPreference.is_floating_mode ?: false

        setupViewPager()
        applyCurrentPageDimensions()
        setupKeyboardPositionButton()
        setupFloatingButton()
        setupResetButton()
        updateKeyboardAlignment()
        updateFloatingModeUI()
        // ▼▼▼ 正しい関数呼び出しに修正 ▼▼▼
        setupResizeHandles()
        setupMoveHandle()
        // ▲▲▲ 正しい関数呼び出しに修正 ▲▲▲

        updateControlsVisibility()
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
                    appPreference.keyboard_position ?: true
                } else {
                    appPreference.qwerty_keyboard_position ?: true
                }
                updateKeyboardAlignment()

                binding.floatingKeyboardSettingBtn.visibility =
                    if (position == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) View.VISIBLE else View.GONE
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
        binding.floatingKeyboardSettingBtn.visibility =
            if (binding.keyboardViewPager.currentItem == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) View.VISIBLE else View.GONE
    }

    private fun applyCurrentPageDimensions() {
        val position = binding.keyboardViewPager.currentItem
        val heightPref: Int
        val widthPref: Int
        val marginBottomPref: Int
        val positionPref: Boolean

        if (position == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
            heightPref = appPreference.keyboard_height ?: 220
            widthPref = appPreference.keyboard_width ?: 100
            marginBottomPref = appPreference.keyboard_vertical_margin_bottom ?: 0
            positionPref = appPreference.keyboard_position ?: true
        } else { // QWERTY
            heightPref = appPreference.qwerty_keyboard_height ?: 220
            widthPref = appPreference.qwerty_keyboard_width ?: 100
            marginBottomPref = appPreference.qwerty_keyboard_vertical_margin_bottom ?: 0
            positionPref = appPreference.qwerty_keyboard_position ?: true
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

    // ▼▼▼ 移動専用の関数を復活させ、中央の `handle_move` を対象にするように修正 ▼▼▼
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
                        appPreference.keyboard_vertical_margin_bottom = finalMarginDp
                    } else { // QWERTY
                        appPreference.qwerty_keyboard_vertical_margin_bottom = finalMarginDp
                    }
                    Timber.d("Saved vertical margin for page $currentPage: $finalMarginDp dp")
                    true
                }

                else -> false
            }
        }
    }
    // ▲▲▲ 移動専用の関数を復活 ▲▲▲

    // ▼▼▼ リサイズ専用の関数に修正 (`handle_top`の機能をリサイズに戻しました) ▼▼▼
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

        fun savePreferences() {
            val finalHeightDp = (binding.keyboardContainer.height / density).roundToInt()
            val finalWidthPercent =
                ((binding.keyboardContainer.width.toFloat() / screenWidth) * 100).roundToInt()
            val finalWidthValue = if (finalWidthPercent >= 98) 100 else finalWidthPercent

            val currentPage = binding.keyboardViewPager.currentItem
            if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
                appPreference.keyboard_height = finalHeightDp
                appPreference.keyboard_width = finalWidthValue
            } else { // QWERTY
                appPreference.qwerty_keyboard_height = finalHeightDp
                appPreference.qwerty_keyboard_width = finalWidthValue
            }
            Timber.d("Saved dimensions for page $currentPage: H=$finalHeightDp dp, W=$finalWidthValue %")
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

                MotionEvent.ACTION_UP -> savePreferences()
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

                MotionEvent.ACTION_UP -> savePreferences()
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

                MotionEvent.ACTION_UP -> savePreferences()
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

                MotionEvent.ACTION_UP -> savePreferences()
            }
            true
        }
    }
    // ▲▲▲ リサイズ専用の関数に修正 ▲▲▲

    private fun setupKeyboardPositionButton() {
        binding.keyboardPositionButton.setOnClickListener {
            isRightAligned = !isRightAligned
            updateKeyboardAlignment()
        }
    }

    private fun setupFloatingButton() {
        binding.floatingKeyboardSettingBtn.setOnClickListener {
            isFloatingMode = !isFloatingMode
            appPreference.is_floating_mode = isFloatingMode
            updateFloatingModeUI()
        }
    }

    private fun setupResetButton() {
        binding.resetLayoutButton.setOnClickListener {
            val currentPage = binding.keyboardViewPager.currentItem

            if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
                appPreference.keyboard_height = 220
                appPreference.keyboard_width = 100
                appPreference.keyboard_vertical_margin_bottom = 0
                appPreference.keyboard_position = true
            } else {
                appPreference.qwerty_keyboard_height = 220
                appPreference.qwerty_keyboard_width = 100
                appPreference.qwerty_keyboard_vertical_margin_bottom = 0
                appPreference.qwerty_keyboard_position = true
            }

            applyCurrentPageDimensions()
            updateKeyboardAlignment()
        }
    }

    private fun updateKeyboardAlignment() {
        val currentPage = binding.keyboardViewPager.currentItem
        if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
            appPreference.keyboard_position = isRightAligned
        } else {
            appPreference.qwerty_keyboard_position = isRightAligned
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

    private fun updateFloatingModeUI() {
        if (isFloatingMode) {
            binding.floatingKeyboardSettingBtn.text =
                getString(R.string.key_size_floating_button_text_on)
            binding.floatingKeyboardSettingBtn.setBackgroundColor(
                ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.blue)
            )
        } else {
            binding.floatingKeyboardSettingBtn.text =
                getString(R.string.key_size_floating_button_text_off)
            binding.floatingKeyboardSettingBtn.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    com.kazumaproject.core.R.color.qwety_key_bg_color
                )
            )
        }
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

        if (areControlsVisible && binding.keyboardViewPager.currentItem == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
            binding.floatingKeyboardSettingBtn.visibility = View.VISIBLE
        } else {
            binding.floatingKeyboardSettingBtn.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
        _binding = null
    }
}
