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

    private val directionOrder = listOf(
        FlickDirection.UP,
        FlickDirection.UP_RIGHT_FAR,
        FlickDirection.DOWN,
        FlickDirection.UP_LEFT_FAR
    )

    // 開始角度と範囲(Sweep)の両方を保持するMap
    // Pair(StartAngle, SweepAngle)
    private val angleData = mutableMapOf<FlickDirection, Pair<Float, Float>>()

    private var currentEditingDirection: FlickDirection = FlickDirection.UP
    private var isUpdatingUi = false
    private val minSweepAngle = 15f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCircularFlickSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Preferenceからデータをロード
        val ranges = appPreference.getCircularFlickRanges()
        angleData.putAll(ranges)

        binding.sliderSweepAngle.apply {
            valueFrom = minSweepAngle
            valueTo = 360f
        }

        setupMenu()
        setupChipGroup()
        setupSliders()
        setupResetButton()

        refreshAll()
    }

    private fun setupResetButton() {
        binding.btnReset.setOnClickListener {
            // デフォルト値に戻して保存
            saveData(FlickDirection.UP, 225f, 90f)
            saveData(FlickDirection.UP_RIGHT_FAR, 315f, 90f)
            saveData(FlickDirection.DOWN, 45f, 90f)
            saveData(FlickDirection.UP_LEFT_FAR, 135f, 90f)

            binding.chipGroupDirection.check(binding.chipUp.id)
            currentEditingDirection = FlickDirection.UP

            refreshAll()
        }
    }

    // 特定の方向のデータ(Start/Sweep)をメモリとPreferenceの両方に保存する
    private fun saveData(direction: FlickDirection, start: Float, sweep: Float) {
        // メモリ上のMap更新
        angleData[direction] = Pair(start, sweep)

        // Preferenceへの保存
        when (direction) {
            FlickDirection.UP -> {
                appPreference.circularFlickUpStart = start
                appPreference.circularFlickUpSweep = sweep
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
            val direction = when (checkedId) {
                binding.chipUp.id -> FlickDirection.UP
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
        // --- 開始角度(Start)のスライダー操作 ---
        binding.sliderStartAngle.addOnChangeListener { _, newStartValue, fromUser ->
            if (!fromUser || isUpdatingUi) return@addOnChangeListener

            // 現在の方向のSweepを取得
            val currentSweep = angleData[currentEditingDirection]?.second ?: 90f

            // 1. 前の方向への影響チェック
            val prevDirection = getPrevDirection(currentEditingDirection)
            val (prevStart, _) = angleData[prevDirection] ?: Pair(0f, 90f)

            // 前の方向の新しいSweepを計算 (自分のStartが変わる＝前のSweepが伸び縮みする)
            val newPrevSweep = normalizeAngle(newStartValue - prevStart)
            if (newPrevSweep < minSweepAngle) return@addOnChangeListener

            val nextDirection = getNextDirection(currentEditingDirection)
            val (nextStart, _) = angleData[nextDirection] ?: Pair(0f, 90f)

            val newCurrentSweep = normalizeAngle(nextStart - newStartValue)
            if (newCurrentSweep < minSweepAngle) return@addOnChangeListener

            // 値の更新と保存
            // 前の方向: Startはそのまま、Sweep更新
            saveData(prevDirection, prevStart, newPrevSweep)
            // 現在の方向: Start更新、Sweep更新
            saveData(currentEditingDirection, newStartValue, newCurrentSweep)

            refreshAll()
        }

        // --- 範囲(Sweep)のスライダー操作 ---
        binding.sliderSweepAngle.addOnChangeListener { _, newSweepValue, fromUser ->
            if (!fromUser || isUpdatingUi) return@addOnChangeListener

            val (currentStart, _) = angleData[currentEditingDirection] ?: Pair(0f, 90f)

            // Sweepが変わると、次の方向のStartが変わる
            val newNextStart = normalizeAngle(currentStart + newSweepValue)
            val nextDirection = getNextDirection(currentEditingDirection)

            // 次の方向のSweepチェック
            // 次の方向の終了位置(Start + Sweep)を取得
            val (oldNextStart, oldNextSweep) = angleData[nextDirection] ?: Pair(0f, 90f)
            val nextEnd = normalizeAngle(oldNextStart + oldNextSweep)

            // 次の方向の新しいSweep = (元の終了位置 - 新しい開始位置)
            val newNextSweep = normalizeAngle(nextEnd - newNextStart)

            if (newNextSweep < minSweepAngle) return@addOnChangeListener

            // 値の更新と保存
            // 現在の方向: Startそのまま、Sweep更新
            saveData(currentEditingDirection, currentStart, newSweepValue)
            // 次の方向: Start更新、Sweep更新
            saveData(nextDirection, newNextStart, newNextSweep)

            refreshAll()
        }
    }

    private fun refreshAll() {
        // PreviewView には Map<FlickDirection, Pair<Start, Sweep>> をそのまま渡せる想定
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
        return directionOrder[(index + 1) % directionOrder.size]
    }

    private fun getPrevDirection(current: FlickDirection): FlickDirection {
        val index = directionOrder.indexOf(current)
        return directionOrder[(index - 1 + directionOrder.size) % directionOrder.size]
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {

            }

            override fun onPrepareMenu(menu: Menu) {

            }

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
