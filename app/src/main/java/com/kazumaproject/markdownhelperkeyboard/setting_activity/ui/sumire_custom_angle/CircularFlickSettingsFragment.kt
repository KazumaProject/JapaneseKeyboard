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
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.buildEvenCircularRanges
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

    private var directionOrder = mutableListOf<CircularFlickDirection>()
    private val angleData = mutableMapOf<CircularFlickDirection, Pair<Float, Float>>()

    private var currentEditingDirection: CircularFlickDirection = CircularFlickDirection.SLOT_0
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

        val directionCount = appPreference.circularFlickDirectionCount

        setupDirectionCountSpinner(directionCount)
        setupMapSwitchDirectionSpinner()
        updateDirectionOrder(directionCount)

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
        setupChipGroup()
        setupSliders()
        setupResetButton()

        updateChipVisibility(directionCount)
        refreshAll()
    }

    private fun setupDirectionCountSpinner(initialCount: Int) {
        val items = listOf("4方向" to 4, "5方向" to 5, "6方向" to 6, "7方向" to 7)
        binding.spinnerDirectionCount.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            items.map { it.first }
        )
        binding.spinnerDirectionCount.setSelection(items.indexOfFirst { it.second == initialCount }.coerceAtLeast(0))
        binding.spinnerDirectionCount.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
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
        val items = listOf("なし" to null) +
            CircularFlickDirection.slots(7).map { it.name to it }
        binding.spinnerMapSwitchDirection.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            items.map { it.first }
        )
        val current = appPreference.circularFlickMapSwitchDirection
        binding.spinnerMapSwitchDirection.setSelection(items.indexOfFirst { it.second == current }.coerceAtLeast(0))
        binding.spinnerMapSwitchDirection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
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
        if (directionCount >= 6) {
            angleData.clear()
            angleData.putAll(buildEvenCircularRanges(directionCount))
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

            angleData.remove(CircularFlickDirection.SLOT_4)
        }
    }

    private fun saveData(direction: CircularFlickDirection, start: Float, sweep: Float) {
        angleData[direction] = Pair(start, sweep)

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
            else -> {}
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

    private fun getNextDirection(current: CircularFlickDirection): CircularFlickDirection {
        val index = directionOrder.indexOf(current)
        if (index == -1) return directionOrder[0]
        return directionOrder[(index + 1) % directionOrder.size]
    }

    private fun getNeighbors(current: CircularFlickDirection): Pair<CircularFlickDirection, CircularFlickDirection> {
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
