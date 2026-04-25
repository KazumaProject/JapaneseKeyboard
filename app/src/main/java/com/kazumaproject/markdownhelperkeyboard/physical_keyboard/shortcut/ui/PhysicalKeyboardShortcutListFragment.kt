package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
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
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PhysicalKeyboardShortcutListFragment : Fragment() {
    private val viewModel: PhysicalKeyboardShortcutViewModel by viewModels()
    private lateinit var adapter: PhysicalKeyboardShortcutAdapter
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = getString(R.string.physical_keyboard_shortcut_list_title)
            setDisplayHomeAsUpEnabled(true)
            show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val addButton = Button(context).apply {
            text = getString(R.string.physical_keyboard_shortcut_add)
            setOnClickListener {
                findNavController().navigate(
                    R.id.physicalKeyboardShortcutEditFragment,
                    bundleOf("shortcutId" to 0L)
                )
            }
        }
        emptyText = TextView(context).apply {
            text = getString(R.string.physical_keyboard_shortcut_empty)
            visibility = View.GONE
            setPadding(32, 32, 32, 32)
        }
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
        root.addView(addButton)
        root.addView(emptyText)
        root.addView(recyclerView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        adapter = PhysicalKeyboardShortcutAdapter(
            onClick = { item ->
                findNavController().navigate(
                    R.id.physicalKeyboardShortcutEditFragment,
                    bundleOf("shortcutId" to item.id)
                )
            },
            onEnabledChange = viewModel::toggle
        )
        recyclerView.adapter = adapter
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupMenu()
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.shortcuts.collect {
                    adapter.submitList(it)
                    emptyText.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = Unit
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return if (menuItem.itemId == android.R.id.home) {
                    findNavController().popBackStack()
                    true
                } else {
                    false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
}
