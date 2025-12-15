package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickDirectionMapper
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.FlickMappingAdapter
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.FlickMappingItem
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyEditorBinding
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class KeyEditorFragment : Fragment(R.layout.fragment_key_editor) {

    @Inject
    lateinit var keyboardRepository: KeyboardRepository

    private val viewModel: KeyboardEditorViewModel by hiltNavGraphViewModels(R.id.mobile_navigation)

    private var _binding: FragmentKeyEditorBinding? = null
    private val binding get() = _binding!!

    private var flickAdapter: FlickMappingAdapter? = null
    private var currentKeyData: KeyData? = null
    private var currentFlickItems = mutableListOf<FlickMappingItem>()
    private lateinit var keyActionAdapter: ArrayAdapter<String>

    // サイズ設定用の変数
    private var currentColSpan: Int = 1
    private var currentRowSpan: Int = 1
    private var maxColSpan: Int = 1
    private var maxRowSpan: Int = 1

    // The ActionBar setup is now fully handled in onViewCreated.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentKeyEditorBinding.bind(view)

        binding.buttonDone.isEnabled = false
        setupToolbarAndMenu()
        setupAdapters()
        setupUIListeners()
        setupInitialState()
    }

    private fun setupAdapters() {
        flickAdapter = FlickMappingAdapter(onItemUpdated = { updatedItem ->
            val index = currentFlickItems.indexOfFirst { it.id == updatedItem.id }
            if (index != -1) {
                currentFlickItems[index] = updatedItem
            }
        }, context = requireContext())
        binding.flickMappingsRecyclerView.adapter = flickAdapter
        binding.flickMappingsRecyclerView.layoutManager = LinearLayoutManager(context)

        val actionDisplayNames =
            KeyActionMapper.getDisplayActions(requireContext()).map { it.displayName }
        keyActionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            actionDisplayNames
        )
        binding.keyActionSpinner.setAdapter(keyActionAdapter)
    }

    private fun setupUIListeners() {
        binding.buttonDone.setOnClickListener { onDone() }

        binding.keyTypeChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val selectedChipId = checkedIds.first()
            val isSpecialKey = selectedChipId == R.id.chip_special

            binding.keyLabelLayout.isVisible = !isSpecialKey
            binding.keyActionLayout.isVisible = isSpecialKey
            binding.flickEditorGroup.isVisible = !isSpecialKey

            // If switching to a normal key, ensure the flick list is initialized and displayed.
            if (!isSpecialKey) {
                // If the list is empty, it means we are coming from a "special key" state.
                if (currentFlickItems.isEmpty()) {
                    currentFlickItems = FlickDirectionMapper.allowedDirections.map { direction ->
                        FlickMappingItem(direction = direction, output = "")
                    }.toMutableList()
                }
                // Submit the list to the adapter to display the flick settings.
                flickAdapter?.submitList(currentFlickItems)
            }

            updateDoneButtonState()
        }

        binding.keyLabelEdittext.doAfterTextChanged {
            updateDoneButtonState()
        }
        binding.keyActionSpinner.doAfterTextChanged {
            updateDoneButtonState()
        }

        // ▼▼▼ サイズ変更ボタンのリスナー設定 ▼▼▼
        binding.btnColPlus.setOnClickListener {
            if (currentColSpan < maxColSpan) {
                currentColSpan++
                updateSizeDisplay()
            }
        }
        binding.btnColMinus.setOnClickListener {
            if (currentColSpan > 1) {
                currentColSpan--
                updateSizeDisplay()
            }
        }
        binding.btnRowPlus.setOnClickListener {
            if (currentRowSpan < maxRowSpan) {
                currentRowSpan++
                updateSizeDisplay()
            }
        }
        binding.btnRowMinus.setOnClickListener {
            if (currentRowSpan > 1) {
                currentRowSpan--
                updateSizeDisplay()
            }
        }
    }

    private fun updateSizeDisplay() {
        binding.textColSpan.text = currentColSpan.toString()
        binding.textRowSpan.text = currentRowSpan.toString()

        // ボタンの有効/無効状態を更新
        binding.btnColPlus.isEnabled = currentColSpan < maxColSpan
        binding.btnColMinus.isEnabled = currentColSpan > 1
        binding.btnRowPlus.isEnabled = currentRowSpan < maxRowSpan
        binding.btnRowMinus.isEnabled = currentRowSpan > 1
    }

    private fun setupToolbarAndMenu() {
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = getString(R.string.edit_key)
            // This line shows the back arrow in the ActionBar
            setDisplayHomeAsUpEnabled(true)
        }

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // No menu items to inflate for this fragment
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // This handles the click on the back arrow
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        findNavController().popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun updateDoneButtonState() {
        val isEnabled = when (binding.keyTypeChipGroup.checkedChipId) {
            R.id.chip_special -> {
                binding.keyActionSpinner.text.isNotEmpty()
            }

            R.id.chip_normal -> {
                binding.keyLabelEdittext.text.toString().isNotEmpty()
            }

            else -> false
        }
        binding.buttonDone.isEnabled = isEnabled
    }

    private fun setupInitialState() {
        viewLifecycleOwner.lifecycleScope.launch {
            val state = viewModel.uiState.filterNotNull().first()
            currentKeyData =
                state.layout.keys.firstOrNull { it.keyId == state.selectedKeyIdentifier }

            if (currentKeyData == null) {
                findNavController().popBackStack()
                return@launch
            }

            val key = currentKeyData!!

            // ▼▼▼ サイズ情報の初期化と最大値の計算 ▼▼▼
            currentColSpan = key.colSpan
            currentRowSpan = key.rowSpan
            // 現在のキー位置に基づいて、グリッドからはみ出さない最大サイズを計算
            maxColSpan = state.layout.columnCount - key.column
            maxRowSpan = state.layout.rowCount - key.row

            updateSizeDisplay()
            // ▲▲▲ サイズ情報の初期化ここまで ▲▲▲

            binding.keyLabelEdittext.setText(key.label)

            if (key.isSpecialKey) {
                binding.keyTypeChipGroup.check(R.id.chip_special)
                key.action?.let { currentAction ->
                    val displayAction =
                        KeyActionMapper.getDisplayActions(requireContext())
                            .find { it.action == currentAction }
                    if (displayAction != null) {
                        binding.keyActionSpinner.setText(displayAction.displayName, false)
                    }
                }
            } else {
                binding.keyTypeChipGroup.check(R.id.chip_normal)

                val flickMap = state.layout.flickKeyMaps[key.keyId]?.firstOrNull() ?: emptyMap()
                currentFlickItems = FlickDirectionMapper.allowedDirections.map { direction ->
                    val savedAction = flickMap[direction]
                    val output = if (savedAction is FlickAction.Input) savedAction.char else ""
                    FlickMappingItem(direction = direction, output = output)
                }.toMutableList()
                flickAdapter?.submitList(currentFlickItems)
            }

            updateDoneButtonState()
        }
    }

    private fun onDone() {
        val originalKey = currentKeyData ?: return

        val newLabel: String
        val newKeyType: KeyType
        val isSpecial: Boolean
        val newAction: KeyAction?
        var newFlickMap: Map<FlickDirection, FlickAction> = emptyMap()
        val newDrawableResId: Int?

        when (binding.keyTypeChipGroup.checkedChipId) {
            R.id.chip_special -> {
                isSpecial = true
                newKeyType = KeyType.NORMAL

                val selectedText = binding.keyActionSpinner.text.toString()
                val selectedDisplayAction =
                    KeyActionMapper.getDisplayActions(requireContext())
                        .firstOrNull { it.displayName == selectedText }

                Timber.d("KeyEditor: Spinner text is '$selectedText', Found action object is '$selectedDisplayAction'")

                newAction = selectedDisplayAction?.action
                newDrawableResId = selectedDisplayAction?.iconResId

                newLabel = if (newDrawableResId != null) "" else selectedDisplayAction?.displayName
                    ?: "ACTION"
            }

            else -> { // chip_normal
                isSpecial = false
                newAction = null
                newKeyType = KeyType.GRID_FLICK
                newDrawableResId = null
                newLabel = binding.keyLabelEdittext.text.toString()
                newFlickMap = currentFlickItems.filter { it.output.isNotEmpty() }
                    .associate { it.direction to FlickAction.Input(it.output) }
            }
        }

        val updatedKey = originalKey.copy(
            label = newLabel,
            keyType = newKeyType,
            isSpecialKey = isSpecial,
            action = newAction,
            isFlickable = !isSpecial && newFlickMap.isNotEmpty(),
            drawableResId = newDrawableResId,
            // ▼▼▼ 変更されたサイズを適用 ▼▼▼
            rowSpan = currentRowSpan,
            colSpan = currentColSpan
        )

        viewModel.updateKeyAndFlicks(updatedKey, newFlickMap)
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up the action bar when the view is destroyed.
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = null
            setDisplayHomeAsUpEnabled(false)
        }
        viewModel.doneNavigatingToKeyEditor()
        flickAdapter = null
        _binding = null
    }
}
