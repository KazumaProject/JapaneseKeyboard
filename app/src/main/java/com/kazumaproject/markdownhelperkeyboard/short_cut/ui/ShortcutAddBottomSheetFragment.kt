package com.kazumaproject.markdownhelperkeyboard.short_cut.ui

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentShortcutAddBottomSheetBinding
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutToolbarAddAdapter
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import com.kazumaproject.markdownhelperkeyboard.short_cut.summary

class ShortcutAddBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentShortcutAddBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ShortcutToolbarAddAdapter
    private var selectedTypeIds: MutableSet<String> = mutableSetOf()
    private var pendingVoiceShortcutType: ShortcutType? = null

    private val requestRecordAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            val type = pendingVoiceShortcutType
            pendingVoiceShortcutType = null
            if (type == null) return@registerForActivityResult
            if (isGranted) {
                add(type)
            } else {
                Toast.makeText(
                    requireContext(),
                    R.string.shortcut_toolbar_voice_permission_denied,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentShortcutAddBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectedTypeIds = savedInstanceState
            ?.getStringArrayList(ARG_SELECTED_TYPE_IDS)
            ?.toMutableSet()
            ?: arguments
                ?.getStringArrayList(ARG_SELECTED_TYPE_IDS)
                ?.toMutableSet()
            ?: mutableSetOf()

        setupAdapter()
        setupSearch()
        renderCandidates()
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = (dialog as? BottomSheetDialog)
            ?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        val targetHeight = (resources.displayMetrics.heightPixels * SHEET_HEIGHT_RATIO).toInt()
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = targetHeight
        }
        BottomSheetBehavior.from(bottomSheet).apply {
            skipCollapsed = true
            peekHeight = targetHeight
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(ARG_SELECTED_TYPE_IDS, ArrayList(selectedTypeIds))
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding.shortcutAddRecyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private fun setupAdapter() {
        adapter = ShortcutToolbarAddAdapter(
            onAdd = { type -> addWithPermissionIfNeeded(type) },
        )
        binding.shortcutAddRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ShortcutAddBottomSheetFragment.adapter
        }
    }

    private fun setupSearch() {
        binding.shortcutAddSearchInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderCandidates()
                }

                override fun afterTextChanged(s: Editable?) = Unit
            }
        )
    }

    private fun addWithPermissionIfNeeded(type: ShortcutType) {
        if (type != ShortcutType.VOICE_INPUT) {
            add(type)
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            add(type)
        } else {
            pendingVoiceShortcutType = type
            requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun add(type: ShortcutType) {
        if (!selectedTypeIds.add(type.id)) return
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY_ADD_SHORTCUT,
            bundleOf(KEY_SHORTCUT_TYPE_ID to type.id),
        )
        renderCandidates()
    }

    private fun renderCandidates() {
        val query = binding.shortcutAddSearchInput.text?.toString().orEmpty().trim()
        val available = ShortcutType.entries
            .filterNot { it.id in selectedTypeIds }
            .filter { type ->
                query.isBlank() ||
                    type.description.contains(query, ignoreCase = true) ||
                    type.summary(requireContext()).contains(query, ignoreCase = true)
            }

        binding.shortcutAddEmptyText.isVisible = available.isEmpty()
        binding.shortcutAddRecyclerView.isVisible = available.isNotEmpty()
        adapter.submitList(available)
    }

    companion object {
        const val REQUEST_KEY_ADD_SHORTCUT = "request_key_add_shortcut"
        const val KEY_SHORTCUT_TYPE_ID = "key_shortcut_type_id"

        private const val ARG_SELECTED_TYPE_IDS = "arg_selected_type_ids"
        private const val SHEET_HEIGHT_RATIO = 0.88f

        fun newInstance(selectedTypeIds: List<String>): ShortcutAddBottomSheetFragment {
            return ShortcutAddBottomSheetFragment().apply {
                arguments = bundleOf(ARG_SELECTED_TYPE_IDS to ArrayList(selectedTypeIds))
            }
        }
    }
}

