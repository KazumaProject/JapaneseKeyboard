package com.kazumaproject.markdownhelperkeyboard.cursor_move_targets.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentCursorMoveTargetPairsBinding
import kotlinx.coroutines.launch

class CursorMoveTargetPairsFragment : Fragment() {

    private val viewModel: CursorMoveTargetPairsViewModel by viewModels()
    private var _binding: FragmentCursorMoveTargetPairsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CursorMoveTargetPairsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCursorMoveTargetPairsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupRecyclerView()
        setupButtons()
        observeTargets()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = Unit

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
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

    private fun setupRecyclerView() {
        adapter = CursorMoveTargetPairsAdapter(
            onItemClick = { target -> showTargetPairDialog(target) },
            onDeleteClick = { target -> showDeleteDialog(target) }
        )
        binding.cursorMoveTargetPairsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CursorMoveTargetPairsFragment.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupButtons() {
        binding.addCursorMoveTargetPairButton.setOnClickListener {
            showTargetPairDialog(target = null)
        }
        binding.resetCursorMoveTargetPairsButton.setOnClickListener {
            showResetDialog()
        }
    }

    private fun observeTargets() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.targets.collect { targets ->
                    adapter.submitList(targets)
                    binding.emptyTargetPairsText.visibility =
                        if (targets.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun showTargetPairDialog(target: String?) {
        val editText = EditText(requireContext()).apply {
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setText(target.orEmpty())
            setSelection(text.length)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(
                if (target == null) {
                    R.string.cursor_move_target_pair_add_title
                } else {
                    R.string.cursor_move_target_pair_edit_title
                }
            )
            .setMessage(R.string.cursor_move_target_pair_dialog_message)
            .setView(editText)
            .setPositiveButton(R.string.save_string, null)
            .setNegativeButton(R.string.cancel_string, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = editText.text?.toString().orEmpty()
                if (!isTwoCharacters(value)) {
                    toast(getString(R.string.cursor_move_target_pair_invalid))
                    return@setOnClickListener
                }

                val onResult: (Boolean) -> Unit = { success ->
                    if (success) {
                        dialog.dismiss()
                    } else {
                        toast(getString(R.string.cursor_move_target_pair_duplicate))
                    }
                }

                if (target == null) {
                    viewModel.addTargetPair(value, onResult)
                } else {
                    viewModel.updateTargetPair(target, value, onResult)
                }
            }
        }
        dialog.show()
    }

    private fun showDeleteDialog(target: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_delete_title)
            .setMessage(getString(R.string.cursor_move_target_pair_delete_message, target))
            .setPositiveButton(R.string.delete_string) { _, _ ->
                viewModel.deleteTargetPair(target)
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun showResetDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.reset_to_default)
            .setMessage(R.string.cursor_move_target_pair_reset_message)
            .setPositiveButton(R.string.reset_to_default) { _, _ ->
                viewModel.resetToDefault()
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun isTwoCharacters(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.length == 2
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
