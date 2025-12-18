package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.sumire_custom_angle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentCircularFlickSettingsBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CircularFlickSettingsFragment : Fragment() {

    @Inject
    lateinit var appPreference: AppPreference

    private var _binding: FragmentCircularFlickSettingsBinding? = null
    private val binding get() = _binding!!

    private var directionOrder = mutableListOf<FlickDirection>()
    private val angleData = mutableMapOf<FlickDirection, Pair<Float, Float>>()

    private var currentEditingDirection: FlickDirection = FlickDirection.UP
    private var isUpdatingUi = false
    private val minSweepAngle = 15f

    // 新規作成: カウンター変数
    private var resetClickCount = 0
    private var chipDownClickCount = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCircularFlickSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val is5Way = appPreference.circularFlick5DirectionsEnable

        // 初期状態ではスイッチを非表示にする（ただし、既に設定が有効な場合は表示したままにする等の調整が必要な場合はここを変更してください）
        // 要件に従い、隠しコマンド未達成なら非表示、既にONなら表示などのロジックを入れるのが親切ですが、
        // 今回は「条件達成時に表示」という要件のため、初期状態は、設定がOFFならGONEにします。
        binding.switchFiveDirections.isVisible = is5Way
        binding.switchFiveDirections.isChecked = is5Way

        updateDirectionOrder(is5Way)

        val ranges = appPreference.getCircularFlickRanges()
        angleData.putAll(ranges)

        binding.sliderSweepAngle.apply {
            valueFrom = minSweepAngle
            valueTo = 360f
        }

        binding.sliderWindowScale.apply {
            valueFrom = 0.5f
            valueTo = 2.0f
            stepSize = 0.1f
        }

        setupMenu()
        setupFiveWaySwitch()
        setupChipGroup()
        setupSliders()
        setupResetButton()

        updateChipVisibility(is5Way)
        refreshAll()
    }

    // 新規作成: 隠し機能解除チェック
    private fun checkSecretUnlock() {
        if (resetClickCount >= 16 && chipDownClickCount >= 16) {
            if (!binding.switchFiveDirections.isVisible) {
                binding.switchFiveDirections.isVisible = true
            }
        }
    }

    private fun updateDirectionOrder(is5Way: Boolean) {
        directionOrder.clear()
        if (is5Way) {
            directionOrder.add(FlickDirection.UP)
            directionOrder.add(FlickDirection.UP_RIGHT_FAR)
            directionOrder.add(FlickDirection.DOWN)
            directionOrder.add(FlickDirection.UP_RIGHT)
            directionOrder.add(FlickDirection.UP_LEFT_FAR)
        } else {
            directionOrder.add(FlickDirection.UP)
            directionOrder.add(FlickDirection.UP_RIGHT_FAR)
            directionOrder.add(FlickDirection.DOWN)
            directionOrder.add(FlickDirection.UP_LEFT_FAR)
        }
    }

    private fun setupFiveWaySwitch() {
        binding.switchFiveDirections.setOnCheckedChangeListener { _, isChecked ->
            appPreference.circularFlick5DirectionsEnable = isChecked
            updateDirectionOrder(isChecked)
            updateChipVisibility(isChecked)
            resetAnglesToDefault(isChecked)

            binding.chipGroupDirection.check(binding.chipUp.id)
            currentEditingDirection = FlickDirection.UP
            refreshAll()
        }
    }

    private fun updateChipVisibility(is5Way: Boolean) {
        binding.chipUpRight.isVisible = is5Way
    }

    private fun setupResetButton() {
        binding.btnReset.setOnClickListener {
            // 変更点: カウントアップとチェック
            resetClickCount++
            checkSecretUnlock()

            val is5Way = appPreference.circularFlick5DirectionsEnable
            resetAnglesToDefault(is5Way)
            appPreference.circular_flickWindow_scale = 1.0f
            binding.chipGroupDirection.check(binding.chipUp.id)
            currentEditingDirection = FlickDirection.UP
            refreshAll()
        }
    }

    private fun resetAnglesToDefault(is5Way: Boolean) {
        if (is5Way) {
            saveData(FlickDirection.UP, 234f, 72f)
            saveData(FlickDirection.UP_RIGHT_FAR, 306f, 72f)
            saveData(FlickDirection.DOWN, 18f, 72f)
            saveData(FlickDirection.UP_RIGHT, 90f, 72f)
            saveData(FlickDirection.UP_LEFT_FAR, 162f, 72f)
        } else {
            saveData(FlickDirection.UP, 225f, 90f)
            saveData(FlickDirection.UP_RIGHT_FAR, 315f, 90f)
            saveData(FlickDirection.DOWN, 45f, 90f)
            saveData(FlickDirection.UP_LEFT_FAR, 135f, 90f)

            angleData.remove(FlickDirection.UP_RIGHT)
        }
    }

    private fun saveData(direction: FlickDirection, start: Float, sweep: Float) {
        angleData[direction] = Pair(start, sweep)

        when (direction) {
            FlickDirection.UP -> {
                appPreference.circularFlickUpStart = start
                appPreference.circularFlickUpSweep = sweep
            }
            FlickDirection.UP_RIGHT -> {
                appPreference.circularFlickUpRightStart = start
                appPreference.circularFlickUpRightSweep = sweep
            }
            FlickDirection.UP_RIGHT_FAR -> {
                appPreference.circularFlickRightStart = start
                appPreference.circularFlickRightSweep = sweep
            }
            FlickDirection.DOWN -> {
                appPreference.circularFlickDownStart = start
                appPreference.circularFlickDownSweep = sweep
            }
            FlickDirection.UP_LEFT_FAR -> {
                appPreference.circularFlickLeftStart = start
                appPreference.circularFlickLeftSweep = sweep
            }
            else -> {}
        }
    }

    private fun setupChipGroup() {
        binding.chipGroupDirection.setOnCheckedChangeListener { _, checkedId ->
            // 変更点: DOWNチップ選択時のカウントとチェック
            if (checkedId == binding.chipDown.id) {
                chipDownClickCount++
                checkSecretUnlock()
            }

            val direction = when (checkedId) {
                binding.chipUp.id -> FlickDirection.UP
                binding.chipUpRight.id -> FlickDirection.UP_RIGHT
                binding.chipRight.id -> FlickDirection.UP_RIGHT_FAR
                binding.chipDown.id -> FlickDirection.DOWN
                binding.chipLeft.id -> FlickDirection.UP_LEFT_FAR
                else -> FlickDirection.UP
            }
            currentEditingDirection = direction
            updateSlidersFromData()
        }
    }

    private fun setupSliders() {
        binding.sliderWindowScale.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || isUpdatingUi) return@addOnChangeListener
            appPreference.circular_flickWindow_scale = value
            binding.tvScaleValue.text = String.format("%.1f", value)
        }

        binding.sliderStartAngle.addOnChangeListener { _, newStartValue, fromUser ->
            if (!fromUser || isUpdatingUi) return@addOnChangeListener

            val (prevDirection, nextDirection) = getNeighbors(currentEditingDirection)
            val (prevStart, _) = angleData[prevDirection] ?: Pair(0f, 90f)

            val newPrevSweep = normalizeAngle(newStartValue - prevStart)
            if (newPrevSweep < minSweepAngle) return@addOnChangeListener

            val (nextStart, _) = angleData[nextDirection] ?: Pair(0f, 90f)

            val newCurrentSweep = normalizeAngle(nextStart - newStartValue)
            if (newCurrentSweep < minSweepAngle) return@addOnChangeListener

            saveData(prevDirection, prevStart, newPrevSweep)
            saveData(currentEditingDirection, newStartValue, newCurrentSweep)

            refreshAll()
        }

        binding.sliderSweepAngle.addOnChangeListener { _, newSweepValue, fromUser ->
            if (!fromUser || isUpdatingUi) return@addOnChangeListener

            val (currentStart, _) = angleData[currentEditingDirection] ?: Pair(0f, 90f)
            val nextDirection = getNextDirection(currentEditingDirection)

            val newNextStart = normalizeAngle(currentStart + newSweepValue)
            val (oldNextStart, oldNextSweep) = angleData[nextDirection] ?: Pair(0f, 90f)
            val nextEnd = normalizeAngle(oldNextStart + oldNextSweep)

            val newNextSweep = normalizeAngle(nextEnd - newNextStart)

            if (newNextSweep < minSweepAngle) return@addOnChangeListener

            saveData(currentEditingDirection, currentStart, newSweepValue)
            saveData(nextDirection, newNextStart, newNextSweep)

            refreshAll()
        }
    }

    private fun refreshAll() {
        binding.previewView.setRanges(angleData)
        updateSlidersFromData()
    }

    private fun updateSlidersFromData() {
        isUpdatingUi = true
        val (start, sweep) = angleData[currentEditingDirection] ?: Pair(0f, 90f)

        binding.sliderStartAngle.value =
            start.coerceIn(binding.sliderStartAngle.valueFrom, binding.sliderStartAngle.valueTo)
        binding.sliderSweepAngle.value =
            sweep.coerceIn(binding.sliderSweepAngle.valueFrom, binding.sliderSweepAngle.valueTo)

        binding.tvStartValue.text = "${start.toInt()}°"
        binding.tvSweepValue.text = "${sweep.toInt()}°"

        val scale = appPreference.circular_flickWindow_scale
        binding.sliderWindowScale.value =
            scale.coerceIn(binding.sliderWindowScale.valueFrom, binding.sliderWindowScale.valueTo)
        binding.tvScaleValue.text = String.format("%.1f", scale)

        isUpdatingUi = false
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle % 360
        if (a < 0) a += 360
        if (a == 0f && angle > 0) return 360f
        return if (a == 0f) 0f else a
    }

    private fun getNextDirection(current: FlickDirection): FlickDirection {
        val index = directionOrder.indexOf(current)
        if (index == -1) return directionOrder[0]
        return directionOrder[(index + 1) % directionOrder.size]
    }

    private fun getNeighbors(current: FlickDirection): Pair<FlickDirection, FlickDirection> {
        val index = directionOrder.indexOf(current)
        if (index == -1) return Pair(directionOrder[0], directionOrder[0])
        val prev = directionOrder[(index - 1 + directionOrder.size) % directionOrder.size]
        val next = directionOrder[(index + 1) % directionOrder.size]
        return Pair(prev, next)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}
            override fun onPrepareMenu(menu: Menu) {}
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
}
