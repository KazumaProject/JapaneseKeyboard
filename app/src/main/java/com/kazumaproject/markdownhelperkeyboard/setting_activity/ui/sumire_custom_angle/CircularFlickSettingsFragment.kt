package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.sumire_custom_angle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.google.android.material.slider.Slider
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.buildEvenCircularRanges
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentCircularFlickSettingsBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class CircularFlickSettingsFragment : Fragment() {

    @Inject
    lateinit var appPreference: AppPreference

    private var _binding: FragmentCircularFlickSettingsBinding? = null
    private val binding get() = _binding!!

    private var directionOrder = mutableListOf<CircularFlickDirection>()
    private val angleData = mutableMapOf<CircularFlickDirection, Pair<Float, Float>>()

    private var currentEditingDirection: CircularFlickDirection = CircularFlickDirection.SLOT_0
    private var isUpdatingUi = false
    private val minSweepAngle = 15f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCircularFlickSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val directionCount = appPreference.circularFlickDirectionCount

        setupDirectionCountSpinner(directionCount)
        setupMapSwitchDirectionSpinner()
        updateDirectionOrder(directionCount)

        val ranges = appPreference.getCircularFlickRanges()
        angleData.clear()
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
        setupChipGroup()
        setupSliders()
        setupResetButton()

        updateChipVisibility(directionCount)
        refreshAll()
    }

    private fun setupDirectionCountSpinner(initialCount: Int) {
        val items = listOf(
            "4方向" to 4,
            "5方向" to 5,
            "6方向" to 6,
            "7方向" to 7,
        )

        binding.spinnerDirectionCount.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            items.map { it.first },
        )

        binding.spinnerDirectionCount.setSelection(
            items.indexOfFirst { it.second == initialCount }.coerceAtLeast(0),
        )

        binding.spinnerDirectionCount.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    if (isUpdatingUi) return

                    val count = items[position].second

                    appPreference.circularFlickDirectionCount = count
                    appPreference.circularFlick5DirectionsEnable = count == 5

                    updateDirectionOrder(count)
                    updateChipVisibility(count)
                    resetAnglesToDefault(count)

                    binding.chipGroupDirection.check(binding.chipUp.id)
                    currentEditingDirection = CircularFlickDirection.SLOT_0

                    refreshAll()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
    }

    private fun setupMapSwitchDirectionSpinner() {
        val allowedDirections = listOf(
            CircularFlickDirection.SLOT_4,
            CircularFlickDirection.SLOT_5,
            CircularFlickDirection.SLOT_6,
        )
        val items = listOf("なし" to null) +
            allowedDirections.map { it.name to it }

        binding.spinnerMapSwitchDirection.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            items.map { it.first },
        )

        val rawCurrent = appPreference.circularFlickMapSwitchDirection
        val current = rawCurrent?.takeIf { allowedDirections.contains(it) }
        if (rawCurrent != null && current == null) {
            appPreference.circularFlickMapSwitchDirection = null
        }

        binding.spinnerMapSwitchDirection.setSelection(
            items.indexOfFirst { it.second == current }.coerceAtLeast(0),
        )

        binding.spinnerMapSwitchDirection.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    if (isUpdatingUi) return
                    appPreference.circularFlickMapSwitchDirection = items[position].second
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
    }

    private fun updateDirectionOrder(directionCount: Int) {
        directionOrder.clear()
        directionOrder.addAll(CircularFlickDirection.slots(directionCount))
    }

    private fun updateChipVisibility(directionCount: Int) {
        binding.chipUpRight.isVisible = directionCount >= 5
        binding.chipSlot5.isVisible = directionCount >= 6
        binding.chipSlot6.isVisible = directionCount >= 7
    }

    private fun setupResetButton() {
        binding.btnReset.setOnClickListener {
            val count = appPreference.circularFlickDirectionCount

            resetAnglesToDefault(count)
            appPreference.circular_flickWindow_scale = 1.0f

            binding.chipGroupDirection.check(binding.chipUp.id)
            currentEditingDirection = CircularFlickDirection.SLOT_0

            refreshAll()
        }
    }

    private fun resetAnglesToDefault(directionCount: Int) {
        angleData.clear()

        if (directionCount >= 6) {
            val defaultRanges = buildEvenCircularRanges(directionCount)
            angleData.putAll(defaultRanges)

            defaultRanges.forEach { (direction, range) ->
                saveData(direction, range.first, range.second)
            }
        } else if (directionCount == 5) {
            saveData(CircularFlickDirection.SLOT_0, 234f, 72f)
            saveData(CircularFlickDirection.SLOT_1, 306f, 72f)
            saveData(CircularFlickDirection.SLOT_2, 18f, 72f)
            saveData(CircularFlickDirection.SLOT_4, 90f, 72f)
            saveData(CircularFlickDirection.SLOT_3, 162f, 72f)
        } else {
            saveData(CircularFlickDirection.SLOT_0, 225f, 90f)
            saveData(CircularFlickDirection.SLOT_1, 315f, 90f)
            saveData(CircularFlickDirection.SLOT_2, 45f, 90f)
            saveData(CircularFlickDirection.SLOT_3, 135f, 90f)
        }
    }

    private fun saveData(direction: CircularFlickDirection, start: Float, sweep: Float) {
        angleData[direction] = start to sweep

        when (direction) {
            CircularFlickDirection.SLOT_0 -> {
                appPreference.circularFlickUpStart = start
                appPreference.circularFlickUpSweep = sweep
            }

            CircularFlickDirection.SLOT_4 -> {
                appPreference.circularFlickUpRightStart = start
                appPreference.circularFlickUpRightSweep = sweep
            }

            CircularFlickDirection.SLOT_1 -> {
                appPreference.circularFlickRightStart = start
                appPreference.circularFlickRightSweep = sweep
            }

            CircularFlickDirection.SLOT_2 -> {
                appPreference.circularFlickDownStart = start
                appPreference.circularFlickDownSweep = sweep
            }

            CircularFlickDirection.SLOT_3 -> {
                appPreference.circularFlickLeftStart = start
                appPreference.circularFlickLeftSweep = sweep
            }

            else -> Unit
        }
    }

    private fun setupChipGroup() {
        binding.chipGroupDirection.setOnCheckedChangeListener { _, checkedId ->
            val direction = when (checkedId) {
                binding.chipUp.id -> CircularFlickDirection.SLOT_0
                binding.chipUpRight.id -> CircularFlickDirection.SLOT_4
                binding.chipRight.id -> CircularFlickDirection.SLOT_1
                binding.chipDown.id -> CircularFlickDirection.SLOT_2
                binding.chipLeft.id -> CircularFlickDirection.SLOT_3
                binding.chipSlot5.id -> CircularFlickDirection.SLOT_5
                binding.chipSlot6.id -> CircularFlickDirection.SLOT_6
                else -> CircularFlickDirection.SLOT_0
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
            if (directionOrder.isEmpty()) return@addOnChangeListener

            rebuildDirectionOrderFromAngles()

            val (prevDirection, nextDirection) = getNeighbors(currentEditingDirection)
            val (prevStart, _) = angleData[prevDirection] ?: (0f to 90f)
            val (nextStart, _) = angleData[nextDirection] ?: (0f to 90f)

            val totalArc = normalizeAngle(nextStart - prevStart)
            if (totalArc <= minSweepAngle * 2f) return@addOnChangeListener

            val rawPrevSweep = normalizeAngle(newStartValue - prevStart)
            val newPrevSweep = rawPrevSweep.coerceIn(minSweepAngle, totalArc - minSweepAngle)
            val newStart = normalizeAngle(prevStart + newPrevSweep)
            val newCurrentSweep = totalArc - newPrevSweep

            saveData(prevDirection, prevStart, newPrevSweep)
            saveData(currentEditingDirection, newStart, newCurrentSweep)

            refreshAll()
        }

        binding.sliderSweepAngle.addOnChangeListener { _, newSweepValue, fromUser ->
            if (!fromUser || isUpdatingUi) return@addOnChangeListener
            if (directionOrder.isEmpty()) return@addOnChangeListener

            rebuildDirectionOrderFromAngles()

            val (currentStart, _) = angleData[currentEditingDirection] ?: (0f to 90f)
            val nextDirection = getNextDirection(currentEditingDirection)

            val newNextStart = normalizeAngle(currentStart + newSweepValue)
            val (oldNextStart, oldNextSweep) = angleData[nextDirection] ?: (0f to 90f)
            val nextEnd = normalizeAngle(oldNextStart + oldNextSweep)

            val totalArc = normalizeAngle(nextEnd - currentStart)
            if (totalArc <= minSweepAngle * 2f) return@addOnChangeListener

            val clampedCurrentSweep = newSweepValue.coerceIn(minSweepAngle, totalArc - minSweepAngle)
            val clampedNextStart = normalizeAngle(currentStart + clampedCurrentSweep)
            val newNextSweep = totalArc - clampedCurrentSweep

            saveData(currentEditingDirection, currentStart, clampedCurrentSweep)
            saveData(nextDirection, clampedNextStart, newNextSweep)

            refreshAll()
        }
    }

    private fun refreshAll() {
        rebuildDirectionOrderFromAngles()
        binding.previewView.setRanges(angleData)
        updateSlidersFromData()
    }

    private fun rebuildDirectionOrderFromAngles() {
        val count = appPreference.circularFlickDirectionCount
        val targets = CircularFlickDirection.slots(count)
        if (targets.isEmpty()) return

        val sorted = targets.sortedBy { direction ->
            val start = angleData[direction]?.first ?: 0f
            normalizeAngle(start)
        }

        directionOrder.clear()
        directionOrder.addAll(sorted)
    }

    private fun updateSlidersFromData() {
        isUpdatingUi = true

        val (start, sweep) = angleData[currentEditingDirection] ?: (0f to 90f)

        binding.sliderStartAngle.value = coerceSliderValue(
            slider = binding.sliderStartAngle,
            value = start,
        )

        binding.sliderSweepAngle.value = coerceSliderValue(
            slider = binding.sliderSweepAngle,
            value = sweep,
        )

        binding.tvStartValue.text = "${start.toInt()}°"
        binding.tvSweepValue.text = "${sweep.toInt()}°"

        val scale = appPreference.circular_flickWindow_scale

        binding.sliderWindowScale.value = coerceSliderValue(
            slider = binding.sliderWindowScale,
            value = scale,
        )

        binding.tvScaleValue.text = String.format("%.1f", scale)

        isUpdatingUi = false
    }

    private fun coerceSliderValue(slider: Slider, value: Float): Float {
        val coerced = value.coerceIn(slider.valueFrom, slider.valueTo)

        if (slider.stepSize <= 0f) {
            return coerced
        }

        val steps = ((coerced - slider.valueFrom) / slider.stepSize).roundToInt()
        return slider.valueFrom + steps * slider.stepSize
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle % 360f
        if (a < 0f) a += 360f
        if (a == 0f && angle > 0f) return 360f
        return if (a == 0f) 0f else a
    }

    private fun getNextDirection(current: CircularFlickDirection): CircularFlickDirection {
        if (directionOrder.isEmpty()) {
            return CircularFlickDirection.SLOT_0
        }

        val index = directionOrder.indexOf(current)
        if (index == -1) {
            return directionOrder[0]
        }

        return directionOrder[(index + 1) % directionOrder.size]
    }

    private fun getNeighbors(
        current: CircularFlickDirection,
    ): Pair<CircularFlickDirection, CircularFlickDirection> {
        if (directionOrder.isEmpty()) {
            return CircularFlickDirection.SLOT_0 to CircularFlickDirection.SLOT_0
        }

        val index = directionOrder.indexOf(current)
        if (index == -1) {
            return directionOrder[0] to directionOrder[0]
        }

        val prev = directionOrder[(index - 1 + directionOrder.size) % directionOrder.size]
        val next = directionOrder[(index + 1) % directionOrder.size]

        return prev to next
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = Unit

                override fun onPrepareMenu(menu: Menu) = Unit

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        android.R.id.home -> {
                            parentFragmentManager.popBackStack()
                            true
                        }

                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }
}
