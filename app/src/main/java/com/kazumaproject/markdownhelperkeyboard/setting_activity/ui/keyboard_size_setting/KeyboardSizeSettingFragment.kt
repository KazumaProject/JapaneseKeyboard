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
    private var isFloatingMode = false // フローティングモードの状態を管理する変数

    // Define min/max dimensions for the keyboard
    private val minHeightDp = 170
    private val maxHeightDp = 420
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
        // SharedPreferencesから状態を読み込む
        isRightAligned = appPreference.keyboard_position ?: true
        isFloatingMode = appPreference.is_floating_mode ?: false

        // Set initial state and setup listeners
        setInitialKeyboardView()
        setupKeyboardPositionButton()
        setupFloatingButton() // フローティングボタンのリスナーをセットアップ
        setupResetButton()
        updateKeyboardAlignment()
        updateFloatingModeUI() // UIの初期状態をセットアップ
        setupResizeHandles()
        setupMoveHandle()
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
        val marginBottomFromPreference = appPreference.keyboard_vertical_margin_bottom ?: 0

        val density = resources.displayMetrics.density
        val heightInPx = (heightFromPreference * density).toInt()
        val marginBottomInPx = (marginBottomFromPreference * density).toInt()

        val screenWidth = WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(requireActivity()).bounds.width()

        val layoutParams = binding.keyboardContainer.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.height = heightInPx
        layoutParams.width = if (widthFromPreference == maxWidthPercent) {
            ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            (screenWidth * (widthFromPreference / 100f)).toInt()
        }
        layoutParams.bottomMargin = marginBottomInPx
        binding.keyboardContainer.layoutParams = layoutParams
        binding.keyboardView.setOnTouchListener { _, _ ->
            true
        }
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
                    // Yが小さい（上方向）にドラッグするとdeltaYは負になるため、マージンは増加する
                    val newBottomMargin = initialBottomMargin - deltaY
                    // 画面外にドラッグできないようにマージンを制限（例: 0以上）
                    layoutParams.bottomMargin = newBottomMargin.toInt().coerceAtLeast(0)
                    binding.keyboardContainer.requestLayout()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // dpに変換して設定を保存
                    val finalMarginDp = (layoutParams.bottomMargin / density).roundToInt()
                    appPreference.keyboard_vertical_margin_bottom = finalMarginDp
                    Timber.d("savePreferences: vertical margin bottom = $finalMarginDp dp")
                    true
                }

                else -> false
            }
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
     * フローティングボタンのクリックリスナーをセットアップ
     */
    private fun setupFloatingButton() {
        binding.floatingKeyboardSettingBtn.setOnClickListener {
            isFloatingMode = !isFloatingMode
            appPreference.is_floating_mode = isFloatingMode // 設定を保存
            updateFloatingModeUI()
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
            appPreference.keyboard_vertical_margin_bottom = 0
            appPreference.is_floating_mode = false // フローティングモードをOFFにリセット

            // Update local state and UI
            isRightAligned = true
            isFloatingMode = false // ローカルの状態もリセット
            setInitialKeyboardView()
            updateKeyboardAlignment()
            updateFloatingModeUI() // フローティングボタンのUIを更新
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

    /**
     * フローティングモードの状態に基づいてUIを更新
     */
    private fun updateFloatingModeUI() {
        if (isFloatingMode) {
            binding.floatingKeyboardSettingBtn.text = "フローティング ON"
            binding.floatingKeyboardSettingBtn.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    com.kazumaproject.core.R.color.blue
                )
            )
        } else {
            binding.floatingKeyboardSettingBtn.text = "フローティング OFF"
            binding.floatingKeyboardSettingBtn.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    com.kazumaproject.core.R.color.qwety_key_bg_color
                )
            )
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
        _binding = null
    }
}
