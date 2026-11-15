package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_selection

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyboardSelectionBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.KeyboardPreviewSelection
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.KeyboardPreviewTarget
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.isAvailable
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.keyboardSettingsDestination
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.CommonPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.navigateSafely
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

@AndroidEntryPoint
class KeyboardSelectionFragment : Fragment() {
    private var _binding: FragmentKeyboardSelectionBinding? = null
    private val binding get() = _binding!!
    @Inject lateinit var appPreferences: AppPreference
    @Inject lateinit var repository: KeyboardRepository
    private val viewModel: KeyboardSelectionViewModel by viewModels()
    private lateinit var adapter: KeyboardSelectionAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var layouts = emptyList<com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout>()
    private var lastUsed: KeyboardPreviewTarget? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentKeyboardSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = KeyboardSelectionAdapter(
            onStartDrag = { itemTouchHelper.startDrag(it) },
            onToggle = { type, enabled ->
                val order = viewModel.uiState.value.keyboards.toMutableList()
                if (enabled) order.add(type) else if (order.size > 1) order.remove(type) else
                    Toast.makeText(requireContext(), R.string.settings_keyboard_last_required, Toast.LENGTH_SHORT).show()
                updateOrder(order.distinct())
                render()
            },
            onSettings = { navigateSafely(if (it == KeyboardType.CUSTOM) R.id.keyboardListFragment else keyboardSettingsDestination(it)) },
        )
        binding.keyboardSelectionView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@KeyboardSelectionFragment.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, holder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val order = viewModel.uiState.value.keyboards.toMutableList()
                val from = holder.bindingAdapterPosition; val to = target.bindingAdapterPosition
                if (!viewModel.uiState.value.isEditing || from !in order.indices || to !in order.indices) return false
                Collections.swap(order, from, to); updateOrder(order); return true
            }
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
                if (viewModel.uiState.value.isEditing && viewHolder.bindingAdapterPosition in viewModel.uiState.value.keyboards.indices)
                    makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) else 0
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun isLongPressDragEnabled() = false
        })
        itemTouchHelper.attachToRecyclerView(binding.keyboardSelectionView)
        if (viewModel.uiState.value.keyboards.isEmpty()) viewModel.setInitialKeyboards(appPreferences.keyboard_order.ifEmpty { listOf(KeyboardType.TENKEY) })
        setupMenu()
        binding.keyboardStartupSettings.setOnClickListener {
            navigateSafely(R.id.legacyCommonSectionFragment, bundleOf(CommonPreferenceFragment.ARG_HIGHLIGHT_PREFERENCE_KEY to "settings_common_selection"))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            lastUsed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { KeyboardPreviewSelection.lastUsed(requireContext()) }
            render()
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(); requireActivity().invalidateOptionsMenu() } }
                launch { KeyboardPreviewSelection.current.collect {
                    lastUsed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { KeyboardPreviewSelection.lastUsed(requireContext()) }
                    render()
                } }
                launch { repository.getLayouts().collect { layouts = it; render() } }
            }
        }
    }
    private fun updateOrder(order: List<KeyboardType>) {
        val previous = appPreferences.keyboard_order.getOrNull(appPreferences.save_last_used_keyboard_position_preference)
        appPreferences.save_last_used_keyboard_position_preference = order.indexOf(previous).coerceAtLeast(0)
        appPreferences.keyboard_order = order
        viewModel.updateKeyboardOrder(order)
    }
    private fun render() {
        if (_binding == null) return
        val state = viewModel.uiState.value
        val live = KeyboardPreviewSelection.current.value?.takeIf { it.isAvailable(state.keyboards, layouts) }
        val selected = live ?: lastUsed?.takeIf { it.isAvailable(state.keyboards, layouts) }
        adapter.render(state.keyboards, state.isEditing, selected?.type,
            if (live != null) R.string.settings_keyboard_selected else R.string.settings_keyboard_last_used, layouts.size)
        binding.keyboardStartupSettings.text = getString(R.string.settings_keyboard_startup_settings) + " · " +
            getString(if (appPreferences.save_last_used_keyboard_enable_preference == true) R.string.settings_keyboard_startup_remember else R.string.settings_keyboard_startup_first)
    }
    private fun setupMenu() {
        (requireActivity() as MenuHost).addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) { inflater.inflate(R.menu.keyboard_selection_menu, menu) }
            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.action_edit)?.setTitle(if (viewModel.uiState.value.isEditing) com.kazumaproject.core.R.string.done_text else R.string.settings_keyboard_reorder)
            }
            override fun onMenuItemSelected(item: MenuItem): Boolean = if (item.itemId == R.id.action_edit) {
                viewModel.toggleEditMode(); true
            } else false
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
    override fun onDestroyView() { itemTouchHelper.attachToRecyclerView(null); _binding = null; super.onDestroyView() }
}

fun Context.getKeyboardDisplayName(keyboardType: KeyboardType): String = getString(when (keyboardType) {
    KeyboardType.TENKEY -> R.string.settings_keyboard_tenkey
    KeyboardType.MORSE -> R.string.morse_title
    KeyboardType.GOJUON -> R.string.keyboard_type_gojuon
    KeyboardType.QWERTY -> R.string.settings_keyboard_qwerty
    KeyboardType.ROMAJI -> R.string.settings_keyboard_romaji
    KeyboardType.SUMIRE -> R.string.settings_keyboard_sumire
    KeyboardType.CUSTOM -> R.string.category_custom_keyboard_title
    KeyboardType.SPLIT -> R.string.split_keyboard_title
})
