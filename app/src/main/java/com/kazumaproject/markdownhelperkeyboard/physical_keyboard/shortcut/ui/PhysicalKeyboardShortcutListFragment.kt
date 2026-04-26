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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kazumaproject.markdownhelperkeyboard.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PhysicalKeyboardShortcutListFragment : Fragment() {

    private val viewModel: PhysicalKeyboardShortcutViewModel by viewModels()

    private lateinit var adapter: PhysicalKeyboardShortcutAdapter
    private lateinit var emptyText: TextView
    private lateinit var recyclerView: RecyclerView

    private var systemBottomInset: Int = 0
    private var navViewLayoutChangeListener: View.OnLayoutChangeListener? = null

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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
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
            setPadding(
                dp(32),
                dp(32),
                dp(32),
                dp(32)
            )
        }

        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(
                DividerItemDecoration(
                    context,
                    DividerItemDecoration.VERTICAL
                )
            )

            clipToPadding = false
            overScrollMode = RecyclerView.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

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

        root.addView(
            addButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            emptyText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            recyclerView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupMenu()
        applyRecyclerViewBottomSafeArea(view)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.shortcuts.collect { shortcuts ->
                    adapter.submitList(shortcuts)
                    emptyText.visibility = if (shortcuts.isEmpty()) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }

                    recyclerView.post {
                        updateRecyclerViewBottomPadding()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        val navView = activity?.findViewById<BottomNavigationView>(R.id.nav_view)
        val listener = navViewLayoutChangeListener

        if (navView != null && listener != null) {
            navView.removeOnLayoutChangeListener(listener)
        }

        navViewLayoutChangeListener = null

        super.onDestroyView()
    }

    private fun applyRecyclerViewBottomSafeArea(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            systemBottomInset = insets
                .getInsets(WindowInsetsCompat.Type.systemBars())
                .bottom

            updateRecyclerViewBottomPadding()

            insets
        }

        root.doOnLayout {
            ViewCompat.requestApplyInsets(root)
            updateRecyclerViewBottomPadding()
        }

        val navView = activity?.findViewById<BottomNavigationView>(R.id.nav_view)

        navView?.doOnLayout {
            updateRecyclerViewBottomPadding()
        }

        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateRecyclerViewBottomPadding()
        }

        navView?.addOnLayoutChangeListener(listener)
        navViewLayoutChangeListener = listener

        recyclerView.post {
            updateRecyclerViewBottomPadding()
        }
    }

    private fun updateRecyclerViewBottomPadding() {
        if (!::recyclerView.isInitialized) {
            return
        }

        val navView = activity?.findViewById<BottomNavigationView>(R.id.nav_view)

        val navBottom = if (navView != null && navView.isVisible && navView.height > 0) {
            navView.height
        } else {
            0
        }

        val bottomSafeArea = maxOf(systemBottomInset, navBottom) + dp(24)

        recyclerView.clipToPadding = false

        recyclerView.updatePadding(
            left = recyclerView.paddingLeft,
            top = recyclerView.paddingTop,
            right = recyclerView.paddingRight,
            bottom = bottomSafeArea
        )
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = Unit

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return if (menuItem.itemId == android.R.id.home) {
                        findNavController().popBackStack()
                        true
                    } else {
                        false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
