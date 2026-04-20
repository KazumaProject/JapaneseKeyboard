package com.kazumaproject.markdownhelperkeyboard.delete_key_flick.ui

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
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentDeleteKeyFlickTargetsBinding
import com.kazumaproject.markdownhelperkeyboard.delete_key_flick.database.DeleteKeyFlickDeleteTarget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DeleteKeyFlickTargetsFragment : Fragment() {

    private val viewModel: DeleteKeyFlickTargetsViewModel by viewModels()
    private var _binding: FragmentDeleteKeyFlickTargetsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: DeleteKeyFlickTargetsAdapter

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
        _binding = FragmentDeleteKeyFlickTargetsBinding.inflate(inflater, container, false)
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
        adapter = DeleteKeyFlickTargetsAdapter(
            onItemClick = { target -> showSymbolDialog(target) },
            onDeleteClick = { target -> showDeleteDialog(target) }
        )
        binding.deleteKeyFlickTargetsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@DeleteKeyFlickTargetsFragment.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupButtons() {
        binding.addDeleteKeyFlickTargetButton.setOnClickListener {
            showSymbolDialog(target = null)
        }
        binding.resetDeleteKeyFlickTargetsButton.setOnClickListener {
            showResetDialog()
        }
    }

    private fun observeTargets() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.targets.collect { targets ->
                    adapter.submitList(targets)
                    binding.emptyTargetsText.visibility =
                        if (targets.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun showSymbolDialog(target: DeleteKeyFlickDeleteTarget?) {
        val editText = EditText(requireContext()).apply {
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setText(target?.symbol.orEmpty())
            setSelection(text.length)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(
                if (target == null) {
                    R.string.delete_key_flick_target_add_title
                } else {
                    R.string.delete_key_flick_target_edit_title
                }
            )
            .setMessage(R.string.delete_key_flick_target_dialog_message)
            .setView(editText)
            .setPositiveButton(R.string.save_string, null)
            .setNegativeButton(R.string.cancel_string, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val symbol = editText.text?.toString().orEmpty()
                if (!isOneCharacter(symbol)) {
                    toast(getString(R.string.delete_key_flick_target_invalid))
                    return@setOnClickListener
                }

                val onResult: (Boolean) -> Unit = { success ->
                    if (success) {
                        dialog.dismiss()
                    } else {
                        toast(getString(R.string.delete_key_flick_target_duplicate))
                    }
                }

                if (target == null) {
                    viewModel.addSymbol(symbol, onResult)
                } else {
                    viewModel.updateSymbol(target, symbol, onResult)
                }
            }
        }
        dialog.show()
    }

    private fun showDeleteDialog(target: DeleteKeyFlickDeleteTarget) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_delete_title)
            .setMessage(getString(R.string.delete_key_flick_target_delete_message, target.symbol))
            .setPositiveButton(R.string.delete_string) { _, _ ->
                viewModel.delete(target) { success ->
                    if (!success) {
                        toast(getString(R.string.delete_key_flick_target_delete_last_error))
                    }
                }
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun showResetDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.reset_to_default)
            .setMessage(R.string.delete_key_flick_target_reset_message)
            .setPositiveButton(R.string.reset_to_default) { _, _ ->
                viewModel.resetToDefault()
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun isOneCharacter(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.length == 1
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
