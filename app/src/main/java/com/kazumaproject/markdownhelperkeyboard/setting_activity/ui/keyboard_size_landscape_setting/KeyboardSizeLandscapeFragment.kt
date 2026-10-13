package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_landscape_setting

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
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyboardsizeLandscapeBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.adapter.KeyboardViewPagerAdapter
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.preview.KeyboardPreviewGeometry
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.navigateSafely
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
    private var targetScreen = KeyboardPreviewGeometry.Size(1, 1)
    private var previewGeneration = 0

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
        refreshPreviewGeometry()
        setupPreviewResizeObservation()
        applyCurrentPageDimensions()
        setupKeyboardPositionButton()
        setupResetButton()

        updateKeyboardAlignment()      // constraints + horizontal margin apply
        setupResizeHandles()
        setupMoveHandle()             // vertical + horizontal move

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
                previewGeneration++
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
        val marginStartDpPref: Int
        val marginEndDpPref: Int

        if (position == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
            heightPref = appPreference.keyboard_height_landscape ?: 220
            widthPref = appPreference.keyboard_width_landscape ?: 100
            marginBottomPref = appPreference.keyboard_vertical_margin_bottom_landscape ?: 0
            positionPref = appPreference.keyboard_position_landscape ?: true
            marginStartDpPref = appPreference.keyboard_margin_start_dp_landscape ?: 0
            marginEndDpPref = appPreference.keyboard_margin_end_dp_landscape ?: 0
        } else {
            heightPref = appPreference.qwerty_keyboard_height_landscape ?: 220
            widthPref = appPreference.qwerty_keyboard_width_landscape ?: 100
            marginBottomPref = appPreference.qwerty_keyboard_vertical_margin_bottom_landscape ?: 0
            positionPref = appPreference.qwerty_keyboard_position_landscape ?: true
            marginStartDpPref = appPreference.qwerty_keyboard_margin_start_dp_landscape ?: 0
            marginEndDpPref = appPreference.qwerty_keyboard_margin_end_dp_landscape ?: 0
        }

        isRightAligned = positionPref

        val density = resources.displayMetrics.density
        val screenWidth = targetScreen.widthPx
        val screenHeight = targetScreen.heightPx
        val projected = KeyboardPreviewGeometry.projectHeightAndBottomMargin(
            logicalHeightPx = screenHeight,
            savedHeightPx = (heightPref * density).roundToInt(),
            savedBottomMarginPx = (marginBottomPref * density).roundToInt(),
            minimumHeightPx = (minHeightDp * density).roundToInt()
        )
        val widthInPx = KeyboardPreviewGeometry.widthForPercent(screenWidth, widthPref)
        val layoutParams = binding.keyboardContainer.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.height = projected.heightPx
        layoutParams.width = widthInPx
        layoutParams.bottomMargin = projected.bottomMarginPx
        val marginStartPx = (marginStartDpPref * density).toInt()
        val marginEndPx = (marginEndDpPref * density).toInt()
        layoutParams.marginStart = 0
        layoutParams.marginEnd = 0
        val maxMargin = (screenWidth - widthInPx).coerceAtLeast(0)
        if (isRightAligned) {
            layoutParams.marginEnd = marginEndPx.coerceIn(0, maxMargin)
        } else {
            layoutParams.marginStart = marginStartPx.coerceIn(0, maxMargin)
        }
        binding.keyboardContainer.layoutParams = layoutParams
    }

    private fun clampHorizontalMarginToBounds() {
        val lp = binding.keyboardContainer.layoutParams as ConstraintLayout.LayoutParams
        val maxMargin = (targetScreen.widthPx - lp.width).coerceAtLeast(0)
        if (isRightAligned) {
            val activeMargin = lp.marginEnd
            lp.marginStart = 0
            lp.marginEnd = activeMargin.coerceIn(0, maxMargin)
        } else {
            val activeMargin = lp.marginStart
            lp.marginEnd = 0
            lp.marginStart = activeMargin.coerceIn(0, maxMargin)
        }
        binding.keyboardContainer.layoutParams = lp
    }

    private fun refreshPreviewGeometry(): Boolean {
        val bounds = WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(requireActivity()).bounds
        val updated = KeyboardPreviewGeometry.targetSize(bounds.width(), bounds.height(), true)
        binding.keyboardPreviewViewport.setLogicalCanvasSize(updated.widthPx, updated.heightPx)
        if (updated == targetScreen) return false
        targetScreen = updated
        previewGeneration++
        return true
    }

    private fun setupPreviewResizeObservation() {
        binding.root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val viewportChanged = right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop
            if (viewportChanged) {
                previewGeneration++
                if (refreshPreviewGeometry()) {
                    applyCurrentPageDimensions()
                    updateKeyboardAlignment()
                }
            }
        }
        binding.keyboardPreviewViewport.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                previewGeneration++
            }
        }
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

                    R.id.action_keyboard_size_direct_input -> {
                        navigateSafely(
                            R.id.action_keyboardSizeLandscapeFragment_to_keyboardSizeDirectInputFragment
                        )
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    /**
     * handle_move で上下 + 左右に動かす。
     * - 上下: bottomMargin (dp 保存)
     * - 左右: 「現在の alignment 側」の marginStart / marginEnd を dp 保存
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupMoveHandle() {
        var initialX = 0f
        var initialY = 0f
        var initialBottomMarginPx = 0
        var initialMarginStartPx = 0
        var initialMarginEndPx = 0
        var gestureScale = 1f
        var gestureGeneration = 0
        var gestureStarted = false
        val density = resources.displayMetrics.density

        binding.handleMove.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    refreshPreviewGeometry()
                    applyCurrentPageDimensions()
                    updateKeyboardAlignment()
                    val lp = binding.keyboardContainer.layoutParams as ConstraintLayout.LayoutParams
                    initialX = event.rawX
                    initialY = event.rawY
                    gestureScale = binding.keyboardPreviewViewport.scale
                    gestureGeneration = previewGeneration
                    gestureStarted = true
                    initialBottomMarginPx = lp.bottomMargin
                    initialMarginStartPx = lp.marginStart
                    initialMarginEndPx = lp.marginEnd
                    binding.handleMove.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!gestureStarted) return@setOnTouchListener true
                    if (gestureGeneration != previewGeneration) {
                        applyCurrentPageDimensions()
                        updateKeyboardAlignment()
                        gestureStarted = false
                        return@setOnTouchListener true
                    }
                    val lp = binding.keyboardContainer.layoutParams as ConstraintLayout.LayoutParams
                    val deltaX = KeyboardPreviewGeometry.logicalDelta(event.rawX - initialX, gestureScale)
                    val deltaY = KeyboardPreviewGeometry.logicalDelta(event.rawY - initialY, gestureScale)
                    val maxHorizontalMarginPx = (targetScreen.widthPx - lp.width).coerceAtLeast(0)
                    val maxBottomMarginPx = (targetScreen.heightPx - lp.height)
                        .coerceAtLeast(0)
                    val newBottomMargin = (initialBottomMarginPx - deltaY.roundToInt())
                        .coerceIn(0, maxBottomMarginPx)
                    lp.bottomMargin = newBottomMargin
                    if (isRightAligned) {
                        val newEnd = (initialMarginEndPx - deltaX.roundToInt())
                            .coerceIn(0, maxHorizontalMarginPx)
                        lp.marginEnd = newEnd
                        lp.marginStart = 0
                    } else {
                        val newStart = (initialMarginStartPx + deltaX.roundToInt())
                            .coerceIn(0, maxHorizontalMarginPx)
                        lp.marginStart = newStart
                        lp.marginEnd = 0
                    }
                    binding.keyboardContainer.layoutParams = lp
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (gestureStarted && gestureGeneration == previewGeneration) {
                        val lp = binding.keyboardContainer.layoutParams as ConstraintLayout.LayoutParams
                        val currentPage = binding.keyboardViewPager.currentItem
                        if (lp.bottomMargin != initialBottomMarginPx) {
                            val value = (lp.bottomMargin / density).roundToInt()
                            if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
                                appPreference.keyboard_vertical_margin_bottom_landscape = value
                            } else {
                                appPreference.qwerty_keyboard_vertical_margin_bottom_landscape = value
                            }
                        }
                        if (isRightAligned && lp.marginEnd != initialMarginEndPx) {
                            val value = (lp.marginEnd / density).roundToInt()
                            if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
                                appPreference.keyboard_margin_end_dp_landscape = value
                            } else {
                                appPreference.qwerty_keyboard_margin_end_dp_landscape = value
                            }
                        } else if (!isRightAligned && lp.marginStart != initialMarginStartPx) {
                            val value = (lp.marginStart / density).roundToInt()
                            if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
                                appPreference.keyboard_margin_start_dp_landscape = value
                            } else {
                                appPreference.qwerty_keyboard_margin_start_dp_landscape = value
                            }
                        }
                    } else if (gestureStarted) {
                        applyCurrentPageDimensions()
                        updateKeyboardAlignment()
                    }
                    gestureStarted = false
                    binding.handleMove.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (gestureStarted) {
                        applyCurrentPageDimensions()
                        updateKeyboardAlignment()
                    }
                    gestureStarted = false
                    binding.handleMove.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }

                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupResizeHandles() {
        val density = resources.displayMetrics.density
        bindResizeHandle(binding.handleTop, ResizeEdge.TOP, density)
        bindResizeHandle(binding.handleBottom, ResizeEdge.BOTTOM, density)
        bindResizeHandle(binding.handleLeft, ResizeEdge.LEFT, density)
        bindResizeHandle(binding.handleRight, ResizeEdge.RIGHT, density)
    }

    private enum class ResizeEdge { TOP, BOTTOM, LEFT, RIGHT }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindResizeHandle(handle: View, edge: ResizeEdge, density: Float) {
        var initialX = 0f
        var initialY = 0f
        var initialWidth = 0
        var initialHeight = 0
        var initialBottomMargin = 0
        var gestureScale = 1f
        var gestureGeneration = 0
        var gestureStarted = false

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    refreshPreviewGeometry()
                    applyCurrentPageDimensions()
                    updateKeyboardAlignment()
                    val lp = binding.keyboardContainer.layoutParams as ConstraintLayout.LayoutParams
                    initialX = event.rawX
                    initialY = event.rawY
                    initialWidth = lp.width
                    initialHeight = lp.height
                    initialBottomMargin = lp.bottomMargin
                    gestureScale = binding.keyboardPreviewViewport.scale
                    gestureGeneration = previewGeneration
                    gestureStarted = true
                    handle.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!gestureStarted) return@setOnTouchListener true
                    if (gestureGeneration != previewGeneration) {
                        applyCurrentPageDimensions()
                        updateKeyboardAlignment()
                        gestureStarted = false
                        return@setOnTouchListener true
                    }
                    val lp = binding.keyboardContainer.layoutParams as ConstraintLayout.LayoutParams
                    val deltaX = KeyboardPreviewGeometry.logicalDelta(event.rawX - initialX, gestureScale)
                    val deltaY = KeyboardPreviewGeometry.logicalDelta(event.rawY - initialY, gestureScale)
                    val minimumHeight = minOf(
                        (minHeightDp * density).roundToInt(), targetScreen.heightPx
                    )
                    val maximumHeight = minOf(
                        (maxHeightDp * density).roundToInt(), targetScreen.heightPx
                    )
                    val minimumWidth = (targetScreen.widthPx * (minWidthPercent / 100f)).roundToInt()

                    when (edge) {
                        ResizeEdge.TOP -> {
                            val maxHeight = minOf(maximumHeight, targetScreen.heightPx - initialBottomMargin)
                            lp.height = (initialHeight - deltaY.roundToInt())
                                .coerceIn(minimumHeight.coerceAtMost(maxHeight), maxHeight)
                            lp.bottomMargin = initialBottomMargin
                        }
                        ResizeEdge.BOTTOM -> {
                            val topEdge = targetScreen.heightPx - initialBottomMargin - initialHeight
                            val maxHeight = minOf(maximumHeight, targetScreen.heightPx - topEdge)
                            lp.height = (initialHeight + deltaY.roundToInt())
                                .coerceIn(minimumHeight.coerceAtMost(maxHeight), maxHeight)
                            lp.bottomMargin = (targetScreen.heightPx - topEdge - lp.height).coerceAtLeast(0)
                        }
                        ResizeEdge.LEFT -> {
                            val activeMargin = if (isRightAligned) lp.marginEnd else lp.marginStart
                            val maxWidth = (targetScreen.widthPx - activeMargin).coerceAtLeast(minimumWidth)
                            lp.width = (initialWidth - deltaX.roundToInt())
                                .coerceIn(minimumWidth.coerceAtMost(maxWidth), maxWidth)
                        }
                        ResizeEdge.RIGHT -> {
                            val activeMargin = if (isRightAligned) lp.marginEnd else lp.marginStart
                            val maxWidth = (targetScreen.widthPx - activeMargin).coerceAtLeast(minimumWidth)
                            lp.width = (initialWidth + deltaX.roundToInt())
                                .coerceIn(minimumWidth.coerceAtMost(maxWidth), maxWidth)
                        }
                    }
                    binding.keyboardContainer.layoutParams = lp
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (gestureStarted && gestureGeneration == previewGeneration) {
                        val lp = binding.keyboardContainer.layoutParams as ConstraintLayout.LayoutParams
                        val currentPage = binding.keyboardViewPager.currentItem
                        if ((edge == ResizeEdge.TOP || edge == ResizeEdge.BOTTOM) && lp.height != initialHeight) {
                            val value = (lp.height / density).roundToInt()
                            if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
                                appPreference.keyboard_height_landscape = value
                            } else {
                                appPreference.qwerty_keyboard_height_landscape = value
                            }
                        }
                        if (edge == ResizeEdge.BOTTOM && lp.bottomMargin != initialBottomMargin) {
                            val value = (lp.bottomMargin / density).roundToInt()
                            if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
                                appPreference.keyboard_vertical_margin_bottom_landscape = value
                            } else {
                                appPreference.qwerty_keyboard_vertical_margin_bottom_landscape = value
                            }
                        }
                        if ((edge == ResizeEdge.LEFT || edge == ResizeEdge.RIGHT) && lp.width != initialWidth) {
                            val value = KeyboardPreviewGeometry.percentForWidth(lp.width, targetScreen.widthPx)
                            if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
                                appPreference.keyboard_width_landscape = value
                            } else {
                                appPreference.qwerty_keyboard_width_landscape = value
                            }
                        }
                    } else if (gestureStarted) {
                        applyCurrentPageDimensions()
                        updateKeyboardAlignment()
                    }
                    gestureStarted = false
                    handle.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (gestureStarted) {
                        applyCurrentPageDimensions()
                        updateKeyboardAlignment()
                    }
                    gestureStarted = false
                    handle.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }

                else -> false
            }
        }
    }
    private fun setupKeyboardPositionButton() {
        binding.keyboardPositionButton.setOnClickListener {
            isRightAligned = !isRightAligned
            val currentPage = binding.keyboardViewPager.currentItem
            previewGeneration++
            if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
                appPreference.keyboard_position_landscape = isRightAligned
            } else {
                appPreference.qwerty_keyboard_position_landscape = isRightAligned
            }
            updateKeyboardAlignment()
        }
    }

    private fun setupResetButton() {
        binding.resetLayoutButton.setOnClickListener {
            val currentPage = binding.keyboardViewPager.currentItem
            previewGeneration++

            if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
                appPreference.keyboard_height_landscape = 220
                appPreference.keyboard_width_landscape = 100
                appPreference.keyboard_vertical_margin_bottom_landscape = 0
                appPreference.keyboard_position_landscape = true

                // 追加: 左右 margin も reset
                appPreference.keyboard_margin_start_dp_landscape = 0
                appPreference.keyboard_margin_end_dp_landscape = 0
            } else {
                appPreference.qwerty_keyboard_height_landscape = 220
                appPreference.qwerty_keyboard_width_landscape = 100
                appPreference.qwerty_keyboard_vertical_margin_bottom_landscape = 0
                appPreference.qwerty_keyboard_position_landscape = true

                // 追加: 左右 margin も reset
                appPreference.qwerty_keyboard_margin_start_dp_landscape = 0
                appPreference.qwerty_keyboard_margin_end_dp_landscape = 0
            }

            applyCurrentPageDimensions()
            updateKeyboardAlignment()
        }
    }

    /**
     * alignment は ConstraintSet で START/END の constraint を切り替える。
     * ただし保存するのは bias ではなく margin。
     */
    private fun updateKeyboardAlignment() {
        val currentPage = binding.keyboardViewPager.currentItem
        val constraintLayout = binding.keyboardPreviewCanvas
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
            binding.keyboardPositionButton.text = getString(R.string.key_size_position_button_text_right)
        } else {
            constraintSet.connect(
                binding.keyboardContainer.id,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            constraintSet.clear(binding.keyboardContainer.id, ConstraintSet.END)
            binding.keyboardPositionButton.setBackgroundColor(
                ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.qwety_key_bg_color)
            )
            binding.keyboardPositionButton.text = getString(R.string.key_size_position_button_text_left)
        }

        constraintSet.applyTo(constraintLayout)

        val marginDp = if (isRightAligned) {
            if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
                appPreference.keyboard_margin_end_dp_landscape ?: 0
            } else {
                appPreference.qwerty_keyboard_margin_end_dp_landscape ?: 0
            }
        } else {
            if (currentPage == KeyboardViewPagerAdapter.TEN_KEY_PAGE_POSITION) {
                appPreference.keyboard_margin_start_dp_landscape ?: 0
            } else {
                appPreference.qwerty_keyboard_margin_start_dp_landscape ?: 0
            }
        }
        val lp = binding.keyboardContainer.layoutParams as ConstraintLayout.LayoutParams
        val maxMargin = (targetScreen.widthPx - lp.width).coerceAtLeast(0)
        val marginPx = (marginDp * resources.displayMetrics.density).roundToInt().coerceIn(0, maxMargin)
        lp.marginStart = if (isRightAligned) 0 else marginPx
        lp.marginEnd = if (isRightAligned) marginPx else 0
        binding.keyboardContainer.layoutParams = lp
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
        _binding = null
    }
}
